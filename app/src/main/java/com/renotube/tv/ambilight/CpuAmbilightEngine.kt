package com.renotube.tv.ambilight

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class CpuAmbilightEngine(
    private val context: Context,
    private val processor: AmbilightProcessor,
    private val configProvider: () -> AmbilightConfig
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        processor: AmbilightProcessor,
        ambilightRepository: AmbilightRepository
    ) : this(
        context = context,
        processor = processor,
        configProvider = { ambilightRepository.config.value }
    )

    companion object {
        // +30ms look-ahead lead cancels out Wi-Fi UDP + WLED interpolation latency
        // so the physical LEDs change at the exact same millisecond as the 4K TV panel!
        private const val WLED_LEAD_OFFSET_MS = 30L
    }

    private var cpuPlayer: ExoPlayer? = null
    private var attachedMainPlayer: ExoPlayer? = null
    private var imageReader: ImageReader? = null
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private val mainLooperHandler = Handler(Looper.getMainLooper())
    private val sampleBitmap = Bitmap.createBitmap(128, 72, Bitmap.Config.ARGB_8888)
    private val argbPixels = IntArray(128 * 72)
    @Volatile var currentVideoAspectRatio: Float = 16f / 9f
    private var lastProcessedMs = 0L
    private val isMainPlaying = java.util.concurrent.atomic.AtomicBoolean(false)
    private var currentUrl: String? = null

    // Force ONLY CPU Software Decoders so we never touch the MediaTek 4K Hardware Decoder
    private val cpuOnlyCodecSelector = MediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
        val all = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecure, requiresTunneling)
        val swOnly = all.filter { info ->
            val name = info.name.lowercase()
            name.startsWith("c2.android.") || name.startsWith("omx.google.") || name.contains(".sw.")
        }
        swOnly.ifEmpty { all }
    }

    // Event-driven 0ms listener attached directly to the main 4K ExoPlayer
    private val mainPlayerSyncListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            isMainPlaying.set(isPlaying)
            val main = attachedMainPlayer ?: return
            val cpu = cpuPlayer ?: return
            if (isPlaying) {
                val targetPos = (main.currentPosition + WLED_LEAD_OFFSET_MS).coerceAtLeast(0L)
                if (abs(cpu.currentPosition - targetPos) > 80L) {
                    cpu.seekTo(targetPos)
                }
                cpu.playWhenReady = true
            } else {
                // Immediately freeze WLED updates when 4K player pauses or buffers
                cpu.playWhenReady = false
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            // Instant 0ms sync when SponsorBlock skips a segment or user seeks with D-pad
            val cpu = cpuPlayer ?: return
            val targetPos = (newPosition.positionMs + WLED_LEAD_OFFSET_MS).coerceAtLeast(0L)
            cpu.seekTo(targetPos)
            cpu.playbackParameters = PlaybackParameters.DEFAULT
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            cpuPlayer?.playbackParameters = playbackParameters
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                currentVideoAspectRatio = (videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio) / videoSize.height.toFloat()
            }
        }
    }

    // Proportional micro-pacing loop (200ms interval saves 75% main-thread wakeups)
    private val syncTickRunnable = object : Runnable {
        override fun run() {
            val main = attachedMainPlayer
            val cpu = cpuPlayer
            if (main != null && cpu != null) {
                syncWithMainPlayer(main)
                mainLooperHandler.postDelayed(this, 200L)
            }
        }
    }

    fun start(mainPlayer: ExoPlayer, lowResVideoUrl: String) {
        if (cpuPlayer != null && currentUrl == lowResVideoUrl && attachedMainPlayer == mainPlayer) {
            return
        }
        stop()
        currentUrl = lowResVideoUrl
        attachedMainPlayer = mainPlayer
        isMainPlaying.set(mainPlayer.isPlaying)
        val vs = mainPlayer.videoSize
        if (vs.width > 0 && vs.height > 0) {
            currentVideoAspectRatio = (vs.width.toFloat() * vs.pixelWidthHeightRatio) / vs.height.toFloat()
        }

        workerThread = HandlerThread("CpuAmbilightWorker", Thread.NORM_PRIORITY - 1).apply { start() }
        workerHandler = Handler(workerThread!!.looper)

        imageReader = ImageReader.newInstance(256, 144, ImageFormat.YUV_420_888, 6).apply {
            setOnImageAvailableListener({ reader ->
                val image = try { reader.acquireLatestImage() } catch (_: Exception) { null }
                    ?: return@setOnImageAvailableListener
                try {
                    val now = SystemClock.elapsedRealtime()
                    // Only process frames while main 4K player is actively playing on screen
                    if (isMainPlaying.get() && now - lastProcessedMs >= 45L) {
                        lastProcessedMs = now
                        convertYuvImageTo128x72Bitmap(image)
                        processor.processFrame(sampleBitmap, configProvider(), currentVideoAspectRatio)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("CpuAmbilightEngine", "Frame conversion error: ${e.message}")
                } finally {
                    try {
                        image.close()
                    } catch (_: Exception) {}
                }
            }, workerHandler)
        }

        val renderersFactory = DefaultRenderersFactory(context)
            .setMediaCodecSelector(cpuOnlyCodecSelector)
            .setEnableDecoderFallback(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(10_000, 30_000, 200, 500)
            .setTargetBufferBytes(3 * 1024 * 1024) // 3 MB holds ~5 minutes of 144p video in RAM
            .setBackBuffer(10_000, false)
            .build()

        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        val httpFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(mapOf(
                "User-Agent" to userAgent,
                "Origin" to "https://www.youtube.com",
                "Referer" to "https://www.youtube.com/"
            ))
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(10_000)

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, httpFactory)
        val mediaSource = androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(lowResVideoUrl))

        cpuPlayer = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .build().apply {
                volume = 0f
                // Fast seeking so sync adjustments never stall the CPU software decoder
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .build()
                setVideoSurface(imageReader!!.surface)
                setMediaSource(mediaSource)
                prepare()
                val initialTarget = (mainPlayer.currentPosition + WLED_LEAD_OFFSET_MS).coerceAtLeast(0L)
                if (initialTarget > 0L) seekTo(initialTarget)
                // Only start decoding when the 4K player is actually playing (not buffering)
                playWhenReady = mainPlayer.isPlaying
            }

        mainPlayer.addListener(mainPlayerSyncListener)
        mainLooperHandler.post(syncTickRunnable)
    }

    private fun syncWithMainPlayer(mainPlayer: ExoPlayer) {
        val player = cpuPlayer ?: return
        val mainIsPlaying = mainPlayer.isPlaying
        if (player.playWhenReady != mainIsPlaying) {
            player.playWhenReady = mainIsPlaying
        }
        if (!mainIsPlaying || player.playbackState != Player.STATE_READY) return

        val baseSpeed = mainPlayer.playbackParameters.speed
        val targetPosMs = mainPlayer.currentPosition + WLED_LEAD_OFFSET_MS
        val cpuPosMs = player.currentPosition
        val driftMs = targetPosMs - cpuPosMs // Positive = CPU is behind; Negative = CPU is ahead

        when {
            abs(driftMs) > 350L -> {
                // Hard frame-exact seek if drift ever exceeds 350ms
                player.seekTo(targetPosMs.coerceAtLeast(0L))
                player.playbackParameters = PlaybackParameters(baseSpeed)
            }
            abs(driftMs) > 12L -> {
                // Smooth proportional micro-pacing (+/-1% to +/-6%) locks within 12ms (<1 frame)
                val correction = (driftMs / 1000f).coerceIn(-0.06f, 0.06f)
                val adjustedSpeed = (baseSpeed + correction).coerceIn(0.90f, 1.10f)
                if (abs(player.playbackParameters.speed - adjustedSpeed) > 0.005f) {
                    player.playbackParameters = PlaybackParameters(adjustedSpeed)
                }
            }
            else -> {
                // Locked within +/-12ms (<1 frame at 60fps) -> run at exact 1:1 speed
                if (abs(player.playbackParameters.speed - baseSpeed) > 0.002f) {
                    player.playbackParameters = PlaybackParameters(baseSpeed)
                }
            }
        }
    }

    private fun convertYuvImageTo128x72Bitmap(image: android.media.Image) {
        val imgW = image.width
        val imgH = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride
        val chromaW = (imgW shr 1).coerceAtLeast(1)
        val chromaH = (imgH shr 1).coerceAtLeast(1)

        var outIdx = 0
        for (ty in 0 until 72) {
            val sy = ((ty * imgH) / 72).coerceIn(0, imgH - 1)
            val cy = (sy shr 1).coerceIn(0, chromaH - 1)
            val yRowOffset = sy * yRowStride
            val uRowOffset = cy * uRowStride
            val vRowOffset = cy * vRowStride

            for (tx in 0 until 128) {
                val sx = ((tx * imgW) / 128).coerceIn(0, imgW - 1)
                val cx = (sx shr 1).coerceIn(0, chromaW - 1)
                val yVal = (yBuf.get(yRowOffset + sx).toInt() and 0xFF)

                // 1. Strict TV-Range Black Cutoff (Y <= 22 is black + H.264 dark noise floor).
                // Prevents V > 128 from creating pure RED and U/V < 128 from creating pure GREEN!
                if (yVal <= 22) {
                    argbPixels[outIdx++] = 0xFF000000.toInt()
                    continue
                }

                val rawU = (uBuf.get(uRowOffset + cx * uPixelStride).toInt() and 0xFF) - 128
                val rawV = (vBuf.get(vRowOffset + cx * vPixelStride).toInt() and 0xFF) - 128

                // 2. Deadband tiny +/-3 chroma quantization noise in dark scenes (yVal < 60)
                val cleanU = if (yVal < 60 && kotlin.math.abs(rawU) <= 3) 0 else rawU
                val cleanV = if (yVal < 60 && kotlin.math.abs(rawV) <= 3) 0 else rawV

                // 3. Smooth Shadow Chroma Gate: in dark shadows (yVal 23..54), scale U and V
                // smoothly from 0% to 100% so negative U/V cannot spike Green (-547*V - 218*U)
                // and positive V cannot spike Red (+1836*V) when the other channels clip to 0!
                val chromaScale = if (yVal < 54) {
                    val t = (yVal - 22).toFloat() / 32f
                    t * t
                } else {
                    1.0f
                }
                val uVal = (cleanU * chromaScale).toInt()
                val vVal = (cleanV * chromaScale).toInt()

                val y1192 = 1192 * (yVal - 16)
                var r = (y1192 + 1836 * vVal) shr 10
                var g = (y1192 - 547 * vVal - 218 * uVal) shr 10
                var b = (y1192 + 2166 * uVal) shr 10

                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)

                // 4. Block isolated single-channel Red or Green clipping artifacts in dark pixels
                if (yVal < 42) {
                    if (r > 0 && g <= 1 && b <= 1) r = 0
                    if (g > 0 && r <= 1 && b <= 1) g = 0
                }

                argbPixels[outIdx++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        sampleBitmap.setPixels(argbPixels, 0, 128, 0, 0, 128, 72)
    }

    fun stop() {
        currentUrl = null
        mainLooperHandler.removeCallbacks(syncTickRunnable)
        attachedMainPlayer?.removeListener(mainPlayerSyncListener)
        attachedMainPlayer = null
        isMainPlaying.set(false)
        try {
            cpuPlayer?.stop()
            cpuPlayer?.clearMediaItems()
            cpuPlayer?.release()
        } catch (_: Exception) {}
        cpuPlayer = null
        try {
            imageReader?.close()
        } catch (_: Exception) {}
        imageReader = null
        workerThread?.quitSafely()
        workerThread = null
        workerHandler = null
    }
}
