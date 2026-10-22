package com.renotube.tv.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem as Media3MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.mediacodec.DefaultMediaCodecAdapterFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import com.liskovsoft.mediaserviceinterfaces.data.MediaSubtitle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.media3.common.TrackSelectionOverride
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RenoTubePlayer"

private val KNOWN_LANG_CODES = mapOf(
    "en" to "English",
    "hi" to "Hindi",
    "es" to "Spanish",
    "fr" to "French",
    "de" to "German",
    "ja" to "Japanese",
    "ko" to "Korean",
    "pt" to "Portuguese",
    "ru" to "Russian",
    "it" to "Italian",
    "ar" to "Arabic",
    "id" to "Indonesian",
    "te" to "Telugu",
    "ta" to "Tamil",
    "bn" to "Bengali",
    "mr" to "Marathi",
    "pa" to "Punjabi",
    "gu" to "Gujarati",
    "kn" to "Kannada",
    "ml" to "Malayalam",
    "ur" to "Urdu",
    "th" to "Thai",
    "vi" to "Vietnamese",
    "tr" to "Turkish",
    "pl" to "Polish",
    "nl" to "Dutch",
    "uk" to "Ukrainian"
)

data class AudioTrackOption(
    val id: String,
    val name: String,
    val language: String?,
    val isSelected: Boolean,
    val trackGroupIndex: Int,
    val trackIndex: Int
)

data class StatsForNerdsData(
    val videoId: String = "",
    val resolution: String = "",
    val codecs: String = "",
    val bufferHealthMs: Long = 0L,
    val bitrateKbps: Int = 0,
    val speed: Float = 1.0f,
    val droppedFrames: Int = 0,
    val totalFrames: Int = 0,
    val metadataFps: Float = 0f,
    val actualFps: Float = 0f,
    val audioSessionId: Int = 0
)

@Singleton
class RenoTubePlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
        .setInitialBitrateEstimate(50_000_000L) // 50 Mbps default so 4K starts immediately!
        .build()

    val hardware4kCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
        val allCodecs = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
        // Prefer hardware MediaTek decoders first and strip software decoders if hardware exists
        val hwCodecs = allCodecs.filter { info ->
            val name = info.name.lowercase()
            !name.startsWith("c2.android.") && !name.startsWith("omx.google.") && !name.contains(".sw.")
        }
        hwCodecs.ifEmpty { allCodecs }
    }

    private val asyncCodecAdapterFactory = DefaultMediaCodecAdapterFactory().apply {
        forceEnableAsynchronous()
    }

    private val renderersFactory = object : DefaultRenderersFactory(context) {
        override fun getCodecAdapterFactory(): MediaCodecAdapter.Factory {
            return asyncCodecAdapterFactory
        }

        override fun buildVideoRenderers(
            context: Context,
            extensionRendererMode: Int,
            mediaCodecSelector: MediaCodecSelector,
            enableDecoderFallback: Boolean,
            eventHandler: Handler,
            eventListener: VideoRendererEventListener,
            allowedVideoJoiningTimeMs: Long,
            out: ArrayList<Renderer>
        ) {
            val customVideoRenderer = object : MediaCodecVideoRenderer(
                context,
                asyncCodecAdapterFactory,
                hardware4kCodecSelector,
                allowedVideoJoiningTimeMs,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                50
            ) {
                override fun supportsFormat(
                    mediaCodecSelector: MediaCodecSelector,
                    format: Format
                ): Int {
                    // Clamp 4096x2160 DCI 4K to 3840x2160 during capability check so
                    // MediaTek hardware decoder is never rejected for width=4096
                    val clampedFormat = if (format.width in 3841..4096 || format.height in 2161..2304) {
                        format.buildUpon()
                            .setWidth(minOf(format.width, 3840))
                            .setHeight(minOf(format.height, 2160))
                            .build()
                    } else {
                        format
                    }
                    return super.supportsFormat(mediaCodecSelector, clampedFormat)
                }

                override fun getCodecMaxValues(
                    codecInfo: MediaCodecInfo,
                    format: Format,
                    streamFormats: Array<Format>
                ): CodecMaxValues {
                    val clampedFormat = if (format.width in 3841..4096 || format.height in 2161..2304) {
                        format.buildUpon()
                            .setWidth(minOf(format.width, 3840))
                            .setHeight(minOf(format.height, 2160))
                            .build()
                    } else {
                        format
                    }
                    val clampedStreams = streamFormats.map { sf ->
                        if (sf.width in 3841..4096 || sf.height in 2161..2304) {
                            sf.buildUpon()
                                .setWidth(minOf(sf.width, 3840))
                                .setHeight(minOf(sf.height, 2160))
                                .build()
                        } else sf
                    }.toTypedArray()
                    return super.getCodecMaxValues(codecInfo, clampedFormat, clampedStreams)
                }
            }
            out.add(customVideoRenderer)
        }

        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean
        ): AudioSink? {
            return DefaultAudioSink.Builder(context)
                .setAudioCapabilities(AudioCapabilities.getCapabilities(context))
                .setEnableFloatOutput(false)
                .setAudioTrackBufferSizeProvider { minBufferSize, _, _, pcmFrameSize, sampleRate, _, _ ->
                    val targetBytes = ((sampleRate.toLong() * pcmFrameSize * 300_000L) / 1_000_000L).toInt()
                    maxOf(minBufferSize * 2, targetBytes)
                }
                .build()
        }
    }.apply {
        setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        setEnableDecoderFallback(true)
    }

    private val loadControl = DefaultLoadControl.Builder()
        .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
        .setBufferDurationsMs(
            /* minBufferMs = */ 8_000,
            /* maxBufferMs = */ 20_000,
            /* bufferForPlaybackMs = */ 400,
            /* bufferForPlaybackAfterRebufferMs = */ 1_000
        )
        .setTargetBufferBytes(32 * 1024 * 1024)
        .setBackBuffer(1_500, false)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    val trackSelector = DefaultTrackSelector(context).apply {
        setParameters(
            buildUponParameters()
                .setMaxVideoSize(4096, 2160) // Unconstrained DCI 4K (4096x2160) & 4K UHD (3840x2160)
                .setMaxVideoFrameRate(60)    // 60 fps smooth playback
                .setPreferredVideoMimeTypes(
                    MimeTypes.VIDEO_VP9,     // Prefer VP9 over AV1 for 4K60 lower latency hardware decoding
                    MimeTypes.VIDEO_H265,
                    MimeTypes.VIDEO_AV1,
                    MimeTypes.VIDEO_H264
                )
                .setTunnelingEnabled(false)
                .setPreferredAudioLanguage("en") // English audio is always preferred by default
                .setPreferredTextLanguage("en")
                .setSelectUndeterminedTextLanguage(true)
        )
    }

    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .setUsage(C.USAGE_MEDIA)
        .build()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context, renderersFactory)
        .setLoadControl(loadControl)
        .setBandwidthMeter(bandwidthMeter)
        .setTrackSelector(trackSelector)
        .setVideoChangeFrameRateStrategy(C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS)
        .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
        .setHandleAudioBecomingNoisy(true)
        .build().apply {
            // Master gain unity level (1.0f). Prevents premature audio attenuation on Sony TV at volume level 1
            volume = 1.0f
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        com.renotube.tv.perf.PerformanceTracker.onStreamReady()
                    }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        com.renotube.tv.perf.PerformanceTracker.onStreamReady()
                    }
                }
            })
            addAnalyticsListener(EventLogger("RenoTubeEvents"))
        }

    private var preferredAudioLanguage: String = "en"

    fun getPreferredAudioLanguage(): String = preferredAudioLanguage

    fun setPreferredAudioLanguage(lang: String) {
        preferredAudioLanguage = lang
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setPreferredAudioLanguage(lang)
        )
        Log.i(TAG, "Preferred audio language set to $lang (default: English)")
    }

    fun getAvailableAudioTracks(): List<AudioTrackOption> {
        val options = mutableListOf<AudioTrackOption>()
        val tracks = exoPlayer.currentTracks
        var counter = 1
        for ((gIndex, group) in tracks.groups.withIndex()) {
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (tIndex in 0 until group.length) {
                    val format = group.getTrackFormat(tIndex)
                    val isSelected = group.isTrackSelected(tIndex)
                    val langCode = format.language ?: ""
                    val rawLabel = format.label?.takeIf { it.isNotBlank() }

                    // YouTube formats language as "en-us (original)" or "te (dubbed-auto)", or sets langCode to "default"
                    val rawCombined = when {
                        langCode.isNotBlank() && !langCode.equals("default", ignoreCase = true) -> langCode
                        !rawLabel.isNullOrBlank() -> rawLabel
                        else -> langCode
                    }
                    val tokenBeforeSpace = rawCombined.substringBefore(" ").substringBefore("(").trim()
                    val cleanCode = tokenBeforeSpace.substringBefore("-").substringBefore("_").lowercase()

                    val isDubbed = rawCombined.contains("dubbed", ignoreCase = true) || rawLabel?.contains("dubbed", ignoreCase = true) == true
                    val isOriginal = rawCombined.contains("original", ignoreCase = true) || rawLabel?.contains("original", ignoreCase = true) == true

                    val fullLangName = when {
                        cleanCode == "en" || rawCombined.contains("english", ignoreCase = true) || rawCombined.contains("en-", ignoreCase = true) || (isOriginal && (cleanCode.isBlank() || cleanCode == "default")) -> "English"
                        else -> KNOWN_LANG_CODES[cleanCode]
                            ?: try {
                                val loc = java.util.Locale.forLanguageTag(tokenBeforeSpace)
                                loc.getDisplayLanguage(java.util.Locale.ENGLISH).takeIf { it.isNotBlank() && !it.equals(tokenBeforeSpace, ignoreCase = true) }
                                    ?: java.util.Locale(cleanCode).getDisplayLanguage(java.util.Locale.ENGLISH).takeIf { it.isNotBlank() && !it.equals(cleanCode, ignoreCase = true) }
                            } catch (_: Exception) { null }
                    }

                    val audioQuality = when (format.channelCount) {
                        6 -> "5.1 Surround"
                        2 -> "Stereo"
                        1 -> "Mono"
                        else -> null
                    }

                    val baseName = when {
                        fullLangName != null && isOriginal -> "$fullLangName [Original]"
                        fullLangName != null && isDubbed -> "$fullLangName (Dubbed)"
                        fullLangName != null -> fullLangName
                        rawLabel != null -> rawLabel
                        langCode.isNotBlank() -> langCode.uppercase()
                        else -> "Audio Track $counter"
                    }

                    val finalLabel = if (audioQuality != null && !baseName.contains(audioQuality, ignoreCase = true)) {
                        "$baseName • $audioQuality"
                    } else {
                        baseName
                    }

                    options.add(
                        AudioTrackOption(
                            id = "${format.id ?: "audio"}_${gIndex}_$tIndex",
                            name = finalLabel,
                            language = format.language ?: cleanCode,
                            isSelected = isSelected,
                            trackGroupIndex = gIndex,
                            trackIndex = tIndex
                        )
                    )
                    counter++
                }
            }
        }
        // Deduplicate identical names and prioritize selected / English / Original at top
        val distinctOptions = options.distinctBy { it.name }
        return distinctOptions.sortedWith(
            compareByDescending<AudioTrackOption> { it.isSelected }
                .thenByDescending { it.name.contains("English", ignoreCase = true) || it.name.contains("Original", ignoreCase = true) }
        )
    }

    fun selectAudioTrack(option: AudioTrackOption) {
        try {
            val tracks = exoPlayer.currentTracks
            val group = tracks.groups.getOrNull(option.trackGroupIndex) ?: return
            val mediaTrackGroup = group.mediaTrackGroup
            val override = TrackSelectionOverride(mediaTrackGroup, listOf(option.trackIndex))
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .addOverride(override)
                    .apply {
                        if (!option.language.isNullOrBlank()) {
                            setPreferredAudioLanguage(option.language)
                        }
                    }
            )
            Log.i(TAG, "Selected audio track: ${option.name} (lang: ${option.language})")
        } catch (e: Exception) {
            Log.w(TAG, "Error selecting audio track: ${e.message}")
        }
    }

    fun resetAudioTrackToDefault() {
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .setPreferredAudioLanguage("en")
        )
        preferredAudioLanguage = "en"
        Log.i(TAG, "Reset audio track to default (English)")
    }

    private var subtitlesEnabled = true

    fun toggleSubtitles(): Boolean {
        subtitlesEnabled = !subtitlesEnabled
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setPreferredTextLanguage("en")
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
        )
        Log.i(TAG, "Subtitles toggled: $subtitlesEnabled")
        return subtitlesEnabled
    }

    fun isSubtitlesEnabled(): Boolean = subtitlesEnabled

    private var isAmbilightActive = false
    private var manualResolutionHeight: Int? = null

    fun setAmbilightActive(active: Boolean) {
        if (isAmbilightActive == active) return
        isAmbilightActive = active
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setTunnelingEnabled(false)
        )
        applyOptimalResolution()
    }

    private fun applyOptimalResolution() {
        val manual = manualResolutionHeight
        if (manual != null) {
            applyConstrainedResolution(manual)
            return
        }

        // Native Performance (SurfaceView): Unconstrained 4K UHD @ 60fps HDR
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .clearVideoSizeConstraints()
                .setMaxVideoSize(4096, 2160) // Unconstrained DCI 4K (4096x2160) & 4K UHD (3840x2160)
                .setMinVideoSize(1920, 1080)
                .setTunnelingEnabled(false)
                .setPreferredVideoMimeTypes(
                    MimeTypes.VIDEO_VP9,     // Prefer VP9 over AV1 for 4K60 lower latency hardware decoding
                    MimeTypes.VIDEO_H265,
                    MimeTypes.VIDEO_H264
                )
        )
        Log.i(TAG, "Native 4K Performance: Unconstrained 4K UHD @ 60fps (VP9 preferred) on SurfaceView (tunneling=false)")
    }

    fun setMaxVideoResolution(height: Int) {
        manualResolutionHeight = if (height >= 2160) null else height
        if (height >= 2160) {
            applyOptimalResolution()
        } else {
            applyConstrainedResolution(height)
        }
    }

    private fun applyConstrainedResolution(height: Int) {
        val width = when (height) {
            1440 -> 2560
            1080 -> 1920
            720 -> 1280
            480 -> 854
            else -> 1920
        }
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setMaxVideoSize(width, height)
                .setMinVideoSize(width, height)
                .setTunnelingEnabled(false)
        )
        Log.i(TAG, "Video resolution constrained and locked to ${width}x${height} (tunneling=false)")
    }

    // ── Voice Clarity / Dialogue Boost Mode (+6dB vocal compression) ──
    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null
    private var voiceBoostEnabled = false

    fun setVoiceBoostEnabled(enabled: Boolean): Boolean {
        voiceBoostEnabled = enabled
        try {
            val sessionId = exoPlayer.audioSessionId
            if (sessionId != C.AUDIO_SESSION_ID_UNSET) {
                if (enabled) {
                    if (loudnessEnhancer == null) {
                        loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(sessionId)
                    }
                    loudnessEnhancer?.setTargetGain(600) // +6 dB dialogue frequency enhancement
                    loudnessEnhancer?.enabled = true
                    Log.i(TAG, "Voice Clarity (Dialogue Boost) activated (+6dB vocal enhancement)")
                } else {
                    loudnessEnhancer?.enabled = false
                    Log.i(TAG, "Voice Clarity deactivated")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed applying Voice Clarity effect: ${e.message}")
        }
        return voiceBoostEnabled
    }

    fun isVoiceBoostEnabled(): Boolean = voiceBoostEnabled

    // ── Match Frame Rate (AFR - Auto Frame Rate Switching for Sony Cinema 24p) ──
    private var afrEnabled = true
    var currentVideoFps: Float = 0f

    fun setAfrEnabled(enabled: Boolean) {
        afrEnabled = enabled
        Log.i(TAG, "Match Frame Rate (AFR) set to $enabled")
    }

    fun isAfrEnabled(): Boolean = afrEnabled

    // ── Playback Speed Selector (0.5x – 2.0x) ──
    private var playbackSpeed: Float = 1.0f

    fun getPlaybackSpeed(): Float = playbackSpeed

    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(0.25f, 3.0f)
        exoPlayer.setPlaybackSpeed(playbackSpeed)
        Log.i(TAG, "Playback speed changed to ${playbackSpeed}x")
    }

    fun play() {
        exoPlayer.play()
        Log.d(TAG, "Playback resumed")
    }

    // ── Loop / Repeat Single Video ──
    private var loopEnabled = false

    fun setLoopEnabled(enabled: Boolean) {
        loopEnabled = enabled
        exoPlayer.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        Log.i(TAG, "Loop / Repeat Video set to $enabled")
    }

    fun isLoopEnabled(): Boolean = loopEnabled

    private var lastRenderedCount = 0
    private var lastFpsTimestampMs = System.currentTimeMillis()
    private var liveActualFps = 0.0f

    fun getStatsForNerds(videoId: String): StatsForNerdsData {
        val format = exoPlayer.videoFormat
        val audioFormat = exoPlayer.audioFormat
        val metaFps = format?.frameRate?.takeIf { it > 0f } ?: 60.0f
        val res = if (format != null) "${format.width}x${format.height}@${metaFps.toInt()}fps" else "Auto (4K UHD)"
        val videoCodec = format?.sampleMimeType?.substringAfterLast("/")?.uppercase() ?: "VP9"
        val audioCodec = audioFormat?.sampleMimeType?.substringAfterLast("/")?.uppercase() ?: "OPUS"
        val buffer = (exoPlayer.bufferedPosition - exoPlayer.currentPosition).coerceAtLeast(0)
        val bitrate = ((format?.bitrate ?: 0) + (audioFormat?.bitrate ?: 0)) / 1000

        val counters = exoPlayer.videoDecoderCounters
        val renderedFrames = counters?.renderedOutputBufferCount ?: 0
        val droppedFrames = counters?.droppedBufferCount ?: 0
        val totalFrames = renderedFrames + droppedFrames

        val now = System.currentTimeMillis()
        val dtMs = now - lastFpsTimestampMs
        if (dtMs >= 750L) {
            val deltaFrames = (renderedFrames - lastRenderedCount).coerceAtLeast(0)
            liveActualFps = if (exoPlayer.isPlaying) (deltaFrames * 1000f) / dtMs else 0f
            lastRenderedCount = renderedFrames
            lastFpsTimestampMs = now
        }

        return StatsForNerdsData(
            videoId = videoId,
            resolution = res,
            codecs = "$videoCodec / $audioCodec",
            bufferHealthMs = buffer,
            bitrateKbps = bitrate,
            speed = playbackSpeed,
            droppedFrames = droppedFrames,
            totalFrames = totalFrames,
            metadataFps = metaFps,
            actualFps = liveActualFps,
            audioSessionId = exoPlayer.audioSessionId
        )
    }

    var onErrorListener: ((PlaybackException) -> Unit)? = null
    var onPlaybackEndedListener: (() -> Unit)? = null
    var onFirstFrameRenderedListener: (() -> Unit)? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                super.onRenderedFirstFrame()
                Log.i(TAG, "ExoPlayer onRenderedFirstFrame: First video frame rendered!")
                onFirstFrameRenderedListener?.invoke()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer error: ${error.errorCodeName} (${error.errorCode})", error)
                onErrorListener?.invoke(error)
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_VIDEO && group.isSelected) {
                        for (i in 0 until group.length) {
                            if (group.isTrackSelected(i)) {
                                val format = group.getTrackFormat(i)
                                if (format.frameRate > 0) {
                                    currentVideoFps = format.frameRate
                                    Log.i(TAG, "Active Video Format: ${format.width}x${format.height} @ ${format.frameRate} fps (AFR=$afrEnabled)")
                                }
                            }
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> Log.d(TAG, "ExoPlayer: BUFFERING (pos=${exoPlayer.currentPosition}ms, buf=${exoPlayer.bufferedPosition}ms, totalBuf=${exoPlayer.totalBufferedDuration}ms, isPlaying=${exoPlayer.isPlaying}, playWhenReady=${exoPlayer.playWhenReady})")
                    Player.STATE_READY -> {
                        Log.d(TAG, "ExoPlayer: READY (duration=${exoPlayer.duration}ms)")
                        if (voiceBoostEnabled) {
                            setVoiceBoostEnabled(true)
                        }
                    }
                    Player.STATE_ENDED -> {
                        Log.d(TAG, "ExoPlayer: ENDED")
                        onPlaybackEndedListener?.invoke()
                    }
                    Player.STATE_IDLE -> Log.d(TAG, "ExoPlayer: IDLE")
                }
            }
        })
    }

    private fun createDataSourceFactory(formatInfo: MediaItemFormatInfo?): DefaultDataSource.Factory {
        val userAgent = formatInfo?.clientInfo?.userAgent?.takeIf { it.isNotEmpty() }
            ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        val headers = mutableMapOf(
            "User-Agent" to userAgent,
            "Origin" to "https://www.youtube.com",
            "Referer" to "https://www.youtube.com/"
        )

        formatInfo?.visitorCookie?.takeIf { it.isNotEmpty() }?.let {
            headers["Cookie"] = it
        }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
            .setTransferListener(bandwidthMeter)
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(10_000)

        return DefaultDataSource.Factory(context, httpFactory)
            .setTransferListener(bandwidthMeter)
    }

    private fun buildSubtitleConfigurations(formatInfo: MediaItemFormatInfo): List<Media3MediaItem.SubtitleConfiguration> {
        val configs = mutableListOf<Media3MediaItem.SubtitleConfiguration>()
        val subtitles = formatInfo.subtitles ?: return configs

        var hasEnglish = false
        var translatableSub: MediaSubtitle? = null

        for (sub in subtitles) {
            val url = sub.baseUrl ?: continue
            val lang = sub.languageCode ?: "en"
            val name = sub.name ?: "English"
            if (lang.startsWith("en", ignoreCase = true)) {
                hasEnglish = true
            }
            if (sub.isTranslatable && translatableSub == null) {
                translatableSub = sub
            }

            val isDefault = lang.startsWith("en", ignoreCase = true)
            try {
                val config = Media3MediaItem.SubtitleConfiguration.Builder(Uri.parse(url))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(lang)
                    .setLabel(name.ifEmpty { lang })
                    .setRoleFlags(C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_SUBTITLE)
                    .setSelectionFlags(if (isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                    .build()
                configs.add(config)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse subtitle track $lang: ${e.message}")
            }
        }

        // Auto-generate English captions if not present but video has translatable captions
        if (!hasEnglish && translatableSub != null && !translatableSub.baseUrl.isNullOrEmpty()) {
            val baseUrl = translatableSub.baseUrl
            val sep = if (baseUrl.contains("?")) "&" else "?"
            val autoEnUrl = "$baseUrl${sep}tlang=en"
            try {
                val autoEnConfig = Media3MediaItem.SubtitleConfiguration.Builder(Uri.parse(autoEnUrl))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage("en")
                    .setLabel("English (auto-generated)")
                    .setRoleFlags(C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_SUBTITLE)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                configs.add(0, autoEnConfig)
                Log.i(TAG, "Attached auto-generated English captions: $autoEnUrl")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to attach auto-translated English captions: ${e.message}")
            }
        }

        return configs
    }

    /**
     * Plays the given format info using the best available stream mechanism:
     * 1. DASH MPD (via formatInfo.createMpdStream() saved to local cache)
     * 2. HLS Manifest (formatInfo.hlsManifestUrl)
     * 3. Progressive URL (formatInfo.createUrlList())
     */
    suspend fun playFormatInfo(formatInfo: MediaItemFormatInfo) = withContext(Dispatchers.IO) {
        val dataSourceFactory = createDataSourceFactory(formatInfo)
        val subtitleConfigs = buildSubtitleConfigurations(formatInfo)

        // 1. Clean up older manifest files to prevent disk bloating & eliminate stale tokens
        try {
            val now = System.currentTimeMillis()
            val mpdFiles = context.cacheDir.listFiles { _, name -> name.startsWith("manifest_") && name.endsWith(".mpd") }
            mpdFiles?.forEach { file ->
                if (file.name != "manifest_${formatInfo.videoId}.mpd" || (now - file.lastModified()) > 15 * 60 * 1000L) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}

        // 2. Prepare DASH MPD from adaptive streams on IO thread
        val mpdFile = File(context.cacheDir, "manifest_${formatInfo.videoId}.mpd")
        val mpdReady = if (mpdFile.exists() && mpdFile.length() > 0 && (System.currentTimeMillis() - mpdFile.lastModified()) < 15 * 60 * 1000L) {
            true
        } else {
            val mpdStream = try {
                if (formatInfo.containsDashFormats() || formatInfo.containsSabrFormats()) {
                    formatInfo.createMpdStream()
                } else null
            } catch (e: Exception) {
                Log.w(TAG, "Failed to create MPD stream: ${e.message}")
                null
            }

            if (mpdStream != null) {
                try {
                    mpdStream.use { input ->
                        mpdFile.outputStream().buffered().use { output ->
                            input.copyTo(output)
                        }
                    }
                    mpdFile.length() > 0
                } catch (e: Exception) {
                    Log.w(TAG, "Error writing MPD to file: ${e.message}")
                    false
                }
            } else {
                false
            }
        }

        val hlsUrl = if (formatInfo.containsHlsUrl()) formatInfo.hlsManifestUrl else null
        val progressiveUrl = if (!mpdReady && hlsUrl.isNullOrEmpty()) {
            formatInfo.createUrlList()?.firstOrNull { it.isNotEmpty() }
        } else null

        // 3. Switch to Main thread to bind MediaSource to ExoPlayer
        withContext(Dispatchers.Main) {
            exoPlayer.stop()

            // Reset any previous per-video audio track overrides and guarantee default English audio
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .setPreferredAudioLanguage(preferredAudioLanguage.ifBlank { "en" })
            )

            if (mpdReady) {
                try {
                    Log.i(TAG, "Playing via local DASH MPD (${mpdFile.length()} bytes) with ${subtitleConfigs.size} caption track(s)")
                    val mediaItem = Media3MediaItem.Builder()
                        .setUri(Uri.fromFile(mpdFile))
                        .setMimeType(MimeTypes.APPLICATION_MPD)
                        .setSubtitleConfigurations(subtitleConfigs)
                        .build()
                    val mediaSource = DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
                    exoPlayer.setMediaSource(mediaSource)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    return@withContext
                } catch (e: Exception) {
                    Log.w(TAG, "Error preparing DASH MPD: ${e.message}")
                }
            }

            // Fallback to HLS Manifest
            if (!hlsUrl.isNullOrEmpty()) {
                Log.i(TAG, "Playing via HLS Manifest: $hlsUrl with ${subtitleConfigs.size} caption track(s)")
                val mediaItem = Media3MediaItem.Builder()
                    .setUri(hlsUrl)
                    .setSubtitleConfigurations(subtitleConfigs)
                    .build()
                val mediaSource = HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                return@withContext
            }

            // Fallback to Progressive MP4 URL
            if (!progressiveUrl.isNullOrEmpty()) {
                Log.i(TAG, "Playing via Progressive URL: $progressiveUrl with ${subtitleConfigs.size} caption track(s)")
                val mediaItem = Media3MediaItem.Builder()
                    .setUri(progressiveUrl)
                    .setSubtitleConfigurations(subtitleConfigs)
                    .build()
                val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
                return@withContext
            }

            Log.e(TAG, "No playable stream found in format info for video ${formatInfo.videoId}")
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.playWhenReady) {
            exoPlayer.pause()
            Log.d(TAG, "Playback paused at ${exoPlayer.currentPosition}ms")
        } else {
            exoPlayer.play()
            Log.d(TAG, "Playback resumed at ${exoPlayer.currentPosition}ms")
        }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceIn(0, exoPlayer.duration.coerceAtLeast(0))
        Log.d(TAG, "Seeking to ${target}ms")
        exoPlayer.seekTo(target)
    }

    fun seekRelative(deltaMs: Long) {
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0, exoPlayer.duration.coerceAtLeast(0))
        Log.d(TAG, "Seeking relative by ${deltaMs}ms -> target: ${target}ms")
        exoPlayer.seekTo(target)
    }

    fun stop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        Log.d(TAG, "Playback stopped and media items cleared")
    }

    fun pause() {
        exoPlayer.pause()
        Log.d(TAG, "Playback paused")
    }

    fun release() {
        exoPlayer.release()
    }

    val currentPositionMs: Long get() = exoPlayer.currentPosition.coerceAtLeast(0)
    val durationMs: Long get() = exoPlayer.duration.coerceAtLeast(0)
    val isPlaying: Boolean get() = exoPlayer.isPlaying
}
