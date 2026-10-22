package com.renotube.tv.ui.screens.player

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.PixelCopy
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.renotube.tv.R
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import com.renotube.tv.ui.components.QrCodeView
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material.icons.filled.Translate
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import android.text.Layout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import coil.compose.AsyncImage
import com.renotube.tv.data.model.SectionRowModel
import com.renotube.tv.data.model.VideoCardModel
import com.renotube.tv.player.StatsForNerdsData
import com.renotube.tv.ui.components.VideoCard
import com.renotube.tv.ui.theme.YouTubeBadgeBackground
import com.renotube.tv.ui.theme.YouTubeBlack
import com.renotube.tv.ui.theme.YouTubeFocusedBorder
import com.renotube.tv.ui.theme.YouTubeRed
import com.renotube.tv.ui.theme.YouTubeSecondaryText
import com.renotube.tv.ui.theme.YouTubeSponsorBlock
import com.renotube.tv.ui.theme.YouTubeSurface
import com.renotube.tv.ui.theme.YouTubeWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean


enum class QuickSettingsTab {
    MAIN,
    QUALITY,
    SPEED,
    AUDIO_LANGUAGE,
    SUBTITLES,
    CAPTION_STYLE,
    DESCRIPTION
}

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onVideoSelected: (VideoCardModel) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onViewChannel: ((channelName: String, channelId: String?) -> Unit)? = null
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var selectedVideoForAction by remember { mutableStateOf<VideoCardModel?>(null) }
    val ambilightConfig by viewModel.ambilightConfig.collectAsState()
    val autoplayEnabled by viewModel.autoplayEnabled.collectAsState()
    val subtitlesEnabled by viewModel.subtitlesEnabled.collectAsState()
    val voiceClarityEnabled by viewModel.voiceClarityEnabled.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val afrEnabled by viewModel.afrEnabled.collectAsState()
    val loopEnabled by viewModel.loopEnabled.collectAsState()
    val statsForNerdsEnabled by viewModel.statsForNerdsEnabled.collectAsState()

    var statsData by remember { mutableStateOf<StatsForNerdsData>(viewModel.getStatsData()) }

    LaunchedEffect(statsForNerdsEnabled, uiState.isPlaying) {
        if (statsForNerdsEnabled) {
            while (isActive) {
                statsData = viewModel.getStatsData()
                delay(1000)
            }
        }
    }

    var surfaceViewRef by remember { mutableStateOf<View?>(null) }
    var subtitleViewRef by remember { mutableStateOf<SubtitleView?>(null) }
    var currentVideoFps by remember { mutableStateOf(60f) }
    var is4k60Stream by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // Caption Styling Preferences
    val captionPrefs = remember { context.getSharedPreferences("renotube_caption_prefs", Context.MODE_PRIVATE) }
    var captionTextSizeSp by remember { mutableStateOf(captionPrefs.getFloat("caption_size_sp", 18f)) }
    var captionTextColorInt by remember { mutableStateOf(captionPrefs.getInt("caption_color_int", android.graphics.Color.WHITE)) }
    var captionBgColorInt by remember { mutableStateOf(captionPrefs.getInt("caption_bg_int", android.graphics.Color.argb(200, 0, 0, 0))) }

    fun applySubtitleStyle(subView: SubtitleView?, sizeSp: Float, textCol: Int, bgCol: Int) {
        if (subView == null) return
        try {
            subView.setApplyEmbeddedStyles(false)
            subView.setApplyEmbeddedFontSizes(false)
            subView.setBottomPaddingFraction(0.08f)
            subView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            val style = CaptionStyleCompat(
                textCol,
                bgCol,
                android.graphics.Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                android.graphics.Color.BLACK,
                null
            )
            subView.setStyle(style)
        } catch (_: Exception) {}
    }

    var lastLockedFps by remember { mutableStateOf(0f) }

    fun lockSurfaceVsyncCadence(view: View? = surfaceViewRef, fps: Float = currentVideoFps, force: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val targetFps = if (fps > 0f) fps else 60f
            if (!force && lastLockedFps == targetFps) return
            val sv = view as? SurfaceView
            val surface = sv?.holder?.surface
            if (surface != null && surface.isValid) {
                try {
                    surface.setFrameRate(targetFps, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
                    lastLockedFps = targetFps
                    Log.i("PlayerScreen", "Locked SurfaceView VSYNC cadence to $targetFps fps (FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)")
                } catch (e: Exception) {
                    Log.e("PlayerScreen", "Failed to setFrameRate: ${e.message}")
                }
            }
        }
    }

    // Centered Subtitle Dispatcher & Video Format Cadence Lock
    DisposableEffect(viewModel.exoPlayer) {
        val playerListener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                val centeredCues = cueGroup.cues.map { cue ->
                    cue.buildUpon()
                        .setTextAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setPosition(0.5f)
                        .setPositionAnchor(Cue.ANCHOR_TYPE_MIDDLE)
                        .build()
                }
                subtitleViewRef?.setCues(centeredCues)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val vf = viewModel.exoPlayer.videoFormat
                if (vf != null) {
                    if (vf.frameRate > 0f) currentVideoFps = vf.frameRate
                    is4k60Stream = (vf.height >= 2160 && vf.frameRate >= 50f)
                }
                lockSurfaceVsyncCadence(surfaceViewRef, currentVideoFps)
            }

            override fun onTracksChanged(tracks: Tracks) {
                val vf = viewModel.exoPlayer.videoFormat
                if (vf != null) {
                    if (vf.frameRate > 0f) currentVideoFps = vf.frameRate
                    is4k60Stream = (vf.height >= 2160 && vf.frameRate >= 50f)
                }
                lockSurfaceVsyncCadence(surfaceViewRef, currentVideoFps)
            }
        }
        viewModel.exoPlayer.addListener(playerListener)
        onDispose {
            viewModel.exoPlayer.removeListener(playerListener)
        }
    }

    // OSD Navigation Level:
    // 0 = Hidden (clean video view)
    // 1 = Controls (Scrubber, Play/Pause, Rewind, FastForward, Captions, Autoplay, Settings)
    // 2 = Up Next shelf (Horizontal suggestions carousel)
    var osdLevel by remember { mutableStateOf(0) }
    var showQuickSettings by remember { mutableStateOf(false) }
    var currentSettingsTab by remember { mutableStateOf(QuickSettingsTab.MAIN) }
    var feedbackToast by remember { mutableStateOf<String?>(null) }
    var selectedResolutionLabel by remember { mutableStateOf("Auto (4K UHD)") }

    var showWebRemoteQr by remember { mutableStateOf(false) }

    // Timestamp of last remote/user interaction for 5-second timeout
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }

    // D-Pad seeking accumulator state
    var seekAccumulatorMs by remember { mutableStateOf(0L) }
    var lastSeekTime by remember { mutableStateOf(0L) }

    // Focus Requesters
    val playerRootRequester = remember { FocusRequester() }
    val playPauseRequester = remember { FocusRequester() }
    val scrubberRequester = remember { FocusRequester() }
    val upNextFirstCardRequester = remember { FocusRequester() }
    val settingsFirstItemRequester = remember { FocusRequester() }
    val webRemoteCloseRequester = remember { FocusRequester() }

    // Scrubber Dragging & Fast Seeking State
    var isScrubberFocused by remember { mutableStateOf(false) }
    var isDraggingScrubber by remember { mutableStateOf(false) }
    var scrubberDragFraction by remember { mutableStateOf(0f) }
    var lastLiveSeekTime by remember { mutableStateOf(0L) }
    var scrubberAccelerationFactor by remember { mutableStateOf(1f) }
    var lastScrubberDpadTime by remember { mutableStateOf(0L) }

    // Clear feedback toast after 1.5 seconds and reset seek accumulator
    LaunchedEffect(feedbackToast) {
        if (feedbackToast != null) {
            delay(1500)
            feedbackToast = null
            seekAccumulatorMs = 0L
        }
    }

    // Explicit instantaneous focus management when osdLevel, settings modal or web remote modal changes
    LaunchedEffect(osdLevel, showQuickSettings, currentSettingsTab, showWebRemoteQr) {
        if (showWebRemoteQr) {
            try { webRemoteCloseRequester.requestFocus() } catch (_: Exception) {}
        } else if (showQuickSettings) {
            try { settingsFirstItemRequester.requestFocus() } catch (_: Exception) {}
        } else if (osdLevel == 0) {
            try { playerRootRequester.requestFocus() } catch (_: Exception) {}
        } else if (osdLevel == 1) {
            try { playPauseRequester.requestFocus() } catch (_: Exception) {}
        } else if (osdLevel == 2) {
            try { upNextFirstCardRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        delay(100)
        try { playerRootRequester.requestFocus() } catch (_: Exception) {}
    }

    // 5-Second Inactivity Auto-Timeout (Reset on any user key interaction)
    LaunchedEffect(osdLevel, showQuickSettings, lastInteractionTime) {
        if (osdLevel > 0 && !showQuickSettings) {
            delay(5000)
            if (osdLevel > 0 && !showQuickSettings) {
                osdLevel = 0
                viewModel.setOsdVisible(false)
                try { playerRootRequester.requestFocus() } catch (_: Exception) {}
            }
        }
    }

    // Auto-exit when playback ends and Autoplay is disabled
    LaunchedEffect(Unit) {
        viewModel.playbackEndedExitFlow.collect {
            viewModel.stopPlayback()
            onBack()
        }
    }

    // Handle direct commands sent from Web Remote (Settings, Quality, Speed)
    LaunchedEffect(Unit) {
        viewModel.remoteActionFlow.collect { action ->
            lastInteractionTime = System.currentTimeMillis()
            when (action) {
                "OPEN_SETTINGS" -> {
                    osdLevel = 1
                    currentSettingsTab = QuickSettingsTab.MAIN
                    showQuickSettings = true
                }
                "QUALITY" -> {
                    osdLevel = 1
                    currentSettingsTab = QuickSettingsTab.QUALITY
                    showQuickSettings = true
                }
                "SPEED" -> {
                    osdLevel = 1
                    currentSettingsTab = QuickSettingsTab.SPEED
                    showQuickSettings = true
                }
                "CLOSE_SETTINGS" -> {
                    showQuickSettings = false
                    try { playPauseRequester.requestFocus() } catch (_: Exception) {}
                }
            }
        }
    }

    // Ambilight Dual-Pipeline GPU + CPU Architecture Lifecycle
    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetAmbilightToWarmWhite()
            viewModel.stopCpuAmbilight()
        }
    }

    LaunchedEffect(ambilightConfig.enabled, uiState.isPlaying, uiState.isPlayWhenReady) {
        if (ambilightConfig.enabled && uiState.isPlaying) {
            viewModel.ensureAmbilightAwake()
            viewModel.startCpuAmbilightIfEnabled()
        } else if (!ambilightConfig.enabled) {
            viewModel.stopCpuAmbilight()
        } else if (ambilightConfig.enabled && !uiState.isPlayWhenReady) {
            // User explicitly paused playback! Give 2.5s before transitioning to warm white
            delay(2500)
            if (!uiState.isPlayWhenReady) {
                viewModel.resetAmbilightToWarmWhite()
            }
        }
    }

    // Prevent TV screensaver and ambient daydream during playback
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Hardware Back Button handling:
    BackHandler {
        lastInteractionTime = System.currentTimeMillis()
        if (showWebRemoteQr) {
            showWebRemoteQr = false
            try { playPauseRequester.requestFocus() } catch (_: Exception) {}
            return@BackHandler
        }
        if (showQuickSettings) {
            if (currentSettingsTab != QuickSettingsTab.MAIN) {
                currentSettingsTab = QuickSettingsTab.MAIN
            } else {
                showQuickSettings = false
                try { playPauseRequester.requestFocus() } catch (_: Exception) {}
            }
            return@BackHandler
        }
        when (osdLevel) {
            2 -> {
                // Return from Up Next shelf back to controls row
                osdLevel = 1
                try { playPauseRequester.requestFocus() } catch (_: Exception) {}
            }
            1 -> {
                // Close OSD overlay back to clean video view
                osdLevel = 0
                viewModel.setOsdVisible(false)
                try { playerRootRequester.requestFocus() } catch (_: Exception) {}
            }
            else -> {
                // Stop audio & ExoPlayer immediately and exit
                viewModel.stopPlayback()
                onBack()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .focusRequester(playerRootRequester)
            .focusable(true)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    lastInteractionTime = System.currentTimeMillis()
                    when (event.key) {
                        Key.DirectionDown -> {
                            if (showQuickSettings || showWebRemoteQr) {
                                false // Pass down so QuickSettings LazyColumn can be navigated
                            } else if (osdLevel == 0) {
                                // 1st DOWN press: Open playback controls ONLY and focus Play/Pause directly
                                osdLevel = 1
                                viewModel.setOsdVisible(true)
                                try { playPauseRequester.requestFocus() } catch (_: Exception) {}
                                true
                            } else if (osdLevel == 1) {
                                // 2nd DOWN press: Open Up Next shelf directly
                                if (uiState.metadata?.suggestions?.isNotEmpty() == true) {
                                    osdLevel = 2
                                    try { upNextFirstCardRequester.requestFocus() } catch (_: Exception) {}
                                    true
                                } else false
                            } else false
                        }
                        Key.DirectionUp -> {
                            if (showQuickSettings || showWebRemoteQr) {
                                false // Pass up so QuickSettings LazyColumn can be navigated
                            } else if (osdLevel == 0) {
                                // 1st UP press on clean screen: Open playback controls and focus scrubber
                                osdLevel = 1
                                viewModel.setOsdVisible(true)
                                try { scrubberRequester.requestFocus() } catch (_: Exception) {}
                                true
                            } else if (osdLevel == 1) {
                                // UP from controls row: focus scrubber directly
                                try { scrubberRequester.requestFocus() } catch (_: Exception) {}
                                true
                            } else if (osdLevel == 2) {
                                // UP from Up Next shelf: return to controls row
                                osdLevel = 1
                                try { playPauseRequester.requestFocus() } catch (_: Exception) {}
                                true
                            } else false
                        }
                        Key.MediaRewind -> {
                            val now = System.currentTimeMillis()
                            if (now - lastSeekTime < 1200L && seekAccumulatorMs <= 0L) {
                                seekAccumulatorMs -= 10_000L
                            } else {
                                seekAccumulatorMs = -10_000L
                            }
                            lastSeekTime = now
                            viewModel.seekRelative(-10_000L)
                            feedbackToast = "${seekAccumulatorMs / 1000L}s"
                            true
                        }
                        Key.MediaFastForward -> {
                            val now = System.currentTimeMillis()
                            if (now - lastSeekTime < 1200L && seekAccumulatorMs >= 0L) {
                                seekAccumulatorMs += 10_000L
                            } else {
                                seekAccumulatorMs = 10_000L
                            }
                            lastSeekTime = now
                            viewModel.seekRelative(10_000L)
                            feedbackToast = "+${seekAccumulatorMs / 1000L}s"
                            true
                        }
                        Key.DirectionLeft -> {
                            if (showQuickSettings || showWebRemoteQr) {
                                false
                            } else if (osdLevel == 0) {
                                val now = System.currentTimeMillis()
                                if (now - lastSeekTime < 1200L && seekAccumulatorMs <= 0L) {
                                    seekAccumulatorMs -= 10_000L
                                } else {
                                    seekAccumulatorMs = -10_000L
                                }
                                lastSeekTime = now
                                viewModel.seekRelative(-10_000L)
                                feedbackToast = "${seekAccumulatorMs / 1000L}s"
                                true
                            } else false
                        }
                        Key.DirectionRight -> {
                            if (showQuickSettings || showWebRemoteQr) {
                                false
                            } else if (osdLevel == 0) {
                                val now = System.currentTimeMillis()
                                if (now - lastSeekTime < 1200L && seekAccumulatorMs >= 0L) {
                                    seekAccumulatorMs += 10_000L
                                } else {
                                    seekAccumulatorMs = 10_000L
                                }
                                lastSeekTime = now
                                viewModel.seekRelative(10_000L)
                                feedbackToast = "+${seekAccumulatorMs / 1000L}s"
                                true
                            } else false
                        }
                        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                            lastInteractionTime = System.currentTimeMillis()
                            viewModel.togglePlayPause()
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                            if (showQuickSettings || showWebRemoteQr || selectedVideoForAction != null) {
                                false // Pass through so drawer / action dialog items can be clicked with D-pad Center / Enter
                            } else if (osdLevel == 0) {
                                // Direct play/pause toggle when OSD is hidden
                                lastInteractionTime = System.currentTimeMillis()
                                viewModel.togglePlayPause()
                                osdLevel = 1
                                viewModel.setOsdVisible(true)
                                try { playPauseRequester.requestFocus() } catch (_: Exception) {}
                                true
                            } else {
                                // When OSD controls (osdLevel == 1) or Up Next shelf (osdLevel == 2) are active,
                                // pass through so the focused button or VideoCard child receives and handles the click!
                                false
                            }
                        }
                        Key.Escape, Key.Back -> {
                            if (showWebRemoteQr) {
                                showWebRemoteQr = false
                                try { playPauseRequester.requestFocus() } catch (_: Exception) {}
                                true
                            } else if (showQuickSettings) {
                                if (currentSettingsTab != QuickSettingsTab.MAIN) {
                                    currentSettingsTab = QuickSettingsTab.MAIN
                                } else {
                                    showQuickSettings = false
                                }
                                true
                            } else if (osdLevel == 2) {
                                osdLevel = 1
                                true
                            } else if (osdLevel == 1) {
                                osdLevel = 0
                                viewModel.setOsdVisible(false)
                                true
                            } else {
                                viewModel.stopPlayback()
                                onBack()
                                true
                            }
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(
                enabled = osdLevel == 0,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    lastInteractionTime = System.currentTimeMillis()
                    osdLevel = 1
                    viewModel.setOsdVisible(true)
                }
            )
    ) {
        // ── Real ExoPlayer AndroidView (Dedicated 100% Hardware 4K 60fps SurfaceView with Tunneling) ──
        AndroidView(
            factory = { ctx ->
                val layoutRes = R.layout.player_view_surface
                (LayoutInflater.from(ctx).inflate(layoutRes, null) as PlayerView).apply {
                    player = viewModel.exoPlayer
                    useController = false
                    keepScreenOn = true
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    subtitleView?.visibility = View.GONE // Hide internal uncentered subtitles
                    surfaceViewRef = videoSurfaceView
                    post {
                        if (surfaceViewRef == null) {
                            surfaceViewRef = videoSurfaceView
                        }
                        videoSurfaceView?.isFocusable = false
                        videoSurfaceView?.isFocusableInTouchMode = false
                    }
                }
            },
            update = { playerView ->
                playerView.player = viewModel.exoPlayer
                playerView.keepScreenOn = true
                playerView.isFocusable = false
                playerView.isFocusableInTouchMode = false
                playerView.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                playerView.videoSurfaceView?.isFocusable = false
                playerView.subtitleView?.visibility = View.GONE
                if (playerView.videoSurfaceView != null) {
                    surfaceViewRef = playerView.videoSurfaceView
                }
            },
            onRelease = { playerView ->
                playerView.player = null
                surfaceViewRef = null
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Centered Subtitle Layer (100% horizontally centered & custom styled) ──
        AndroidView(
            factory = { ctx ->
                SubtitleView(ctx).apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                    applySubtitleStyle(this, captionTextSizeSp, captionTextColorInt, captionBgColorInt)
                    visibility = if (subtitlesEnabled) View.VISIBLE else View.GONE
                    subtitleViewRef = this
                }
            },
            update = { subView ->
                subtitleViewRef = subView
                subView.visibility = if (subtitlesEnabled) View.VISIBLE else View.GONE
                applySubtitleStyle(subView, captionTextSizeSp, captionTextColorInt, captionBgColorInt)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Instant Video Modal Backdrop: High resolution preview backdrop crossfaded until first frame renders
        if (!uiState.isFirstFrameRendered && uiState.errorMessage == null) {
            val previewThumb = uiState.initialThumbnail.takeIf { it.isNotBlank() }
                ?: (if (uiState.videoId.isNotBlank()) "https://i.ytimg.com/vi/${uiState.videoId}/hqdefault.jpg" else "")

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                if (previewThumb.isNotBlank()) {
                    val previewImageRequest = remember(previewThumb) {
                        coil.request.ImageRequest.Builder(context)
                            .data(previewThumb)
                            .crossfade(150)
                            .build()
                    }
                    AsyncImage(
                        model = previewImageRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Cinematic dark vignette overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xCC000000),
                                    Color(0x66000000),
                                    Color(0xEE000000)
                                )
                            )
                        )
                )

                // Bottom title & subtle loading spinner
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 56.dp, bottom = 56.dp)
                ) {
                    val dispTitle = uiState.metadata?.title?.takeIf { it.isNotBlank() } ?: uiState.initialTitle
                    val dispAuthor = uiState.metadata?.author?.takeIf { it.isNotBlank() } ?: uiState.initialAuthor

                    if (dispTitle.isNotBlank()) {
                        Text(
                            text = dispTitle,
                            color = YouTubeWhite,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth(0.75f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (dispAuthor.isNotBlank()) {
                        Text(
                            text = dispAuthor,
                            color = YouTubeSecondaryText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = YouTubeRed,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Loading video...",
                            color = YouTubeWhite.copy(alpha = 0.85f),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Error message banner
        if (uiState.errorMessage != null && !uiState.isLoading) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(YouTubeSurface, RoundedCornerShape(12.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "⚠️ Playback Notice",
                        color = YouTubeRed,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = YouTubeWhite,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // ── SponsorBlock Skip Toast ───────────────────────────────────
        if (uiState.lastSkippedSegment != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(32.dp)
                    .background(YouTubeBadgeBackground, RoundedCornerShape(20.dp))
                    .border(1.dp, YouTubeSponsorBlock, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "⚡ Skipped Sponsor: ${uiState.lastSkippedSegment?.category ?: "Segment"}",
                    color = YouTubeSponsorBlock,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ── Action Feedback Toast (Captions / Autoplay / Resolution) ──
        if (feedbackToast != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp)
                    .background(Color(0xDD202020), RoundedCornerShape(24.dp))
                    .border(1.dp, YouTubeFocusedBorder, RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = feedbackToast ?: "",
                    color = YouTubeWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ── Stats for Nerds HUD Overlay ──────────────────────────────
        if (statsForNerdsEnabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 32.dp, top = 80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(1.dp, YouTubeFocusedBorder.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Text("Stats for Nerds", color = YouTubeRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Video ID: ${statsData.videoId}", color = YouTubeWhite, fontSize = 11.sp)
                    Text("Dimensions: ${statsData.resolution}", color = YouTubeWhite, fontSize = 11.sp)
                    Text("Codecs: ${statsData.codecs}", color = YouTubeWhite, fontSize = 11.sp)
                    Text("Buffer Health: ${statsData.bufferHealthMs / 1000}s", color = Color(0xFF4CAF50), fontSize = 11.sp)
                    Text("FPS: %.1f / %.1f (Rendered / Meta)".format(statsData.actualFps, statsData.metadataFps), color = if (statsData.actualFps < statsData.metadataFps * 0.9f && statsData.metadataFps > 0f) YouTubeRed else Color(0xFF4CAF50), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("Dropped Frames: ${statsData.droppedFrames} / ${statsData.totalFrames}", color = if (statsData.droppedFrames > 0) Color(0xFFFF9800) else YouTubeWhite, fontSize = 11.sp)
                    Text("Speed: ${statsData.speed}x", color = YouTubeWhite, fontSize = 11.sp)
                    Text("Voice Clarity: ${if (voiceClarityEnabled) "ON (+6dB)" else "OFF"}", color = YouTubeWhite, fontSize = 11.sp)
                    Text("AFR (Match Frame Rate): ${if (afrEnabled) "ON (24p)" else "OFF"}", color = YouTubeWhite, fontSize = 11.sp)
                }
            }
        }

        // ── OSD Controls & Up Next Shelf Overlay ───────────────────────
        val osdAlpha by animateFloatAsState(
            targetValue = if (osdLevel > 0 || uiState.isLoading) 1f else 0f,
            animationSpec = tween(durationMillis = 100),
            label = "osdAlpha"
        )

        if (osdLevel > 0 || uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(osdAlpha)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                YouTubeBlack.copy(alpha = 0.88f),
                                Color.Transparent,
                                YouTubeBlack.copy(alpha = 0.96f)
                            )
                        )
                    )
                    .padding(horizontal = 32.dp, vertical = 24.dp)
            ) {
                // Top Bar: Back Button, Channel Avatar, Title, Info
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                ) {
                    PlayerIconButton(
                        icon = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        onClick = {
                            viewModel.stopPlayback()
                            onBack()
                        }
                    )
                    Spacer(modifier = Modifier.width(16.dp))

                    AsyncImage(
                        model = uiState.metadata?.authorAvatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(YouTubeSurface)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = uiState.metadata?.title ?: uiState.videoId,
                            color = YouTubeWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${uiState.metadata?.author ?: ""} • ${uiState.metadata?.viewCount ?: ""}",
                            color = YouTubeSecondaryText,
                            fontSize = 12.sp
                        )
                    }
                }

                // Bottom Area: Timers + Scrubber + Symmetrical Controls Row + Up Next List
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    val chapters = uiState.metadata?.chapters ?: emptyList()
                    val activeChapter = chapters.lastOrNull { it.startTimeMs <= uiState.currentPositionMs }

                    val effectivePositionMs = if (isDraggingScrubber) {
                        (scrubberDragFraction * uiState.durationMs).toLong().coerceIn(0L, uiState.durationMs)
                    } else {
                        uiState.currentPositionMs
                    }

                    // 1. Playback Timers ABOVE the Red Line: Current Time on LEFT, Total Duration on RIGHT
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Current playing time & chapter label (Left side above red line)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = formatMs(effectivePositionMs),
                                color = if (isDraggingScrubber || isScrubberFocused) YouTubeRed else YouTubeWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (activeChapter != null && activeChapter.title.isNotBlank()) {
                                Text(
                                    text = " • ${activeChapter.title}",
                                    color = YouTubeSecondaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                        }

                        // Total video length (Right side above red line)
                        Text(
                            text = formatMs(uiState.durationMs),
                            color = YouTubeSecondaryText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // 2. Interactive Scrubber (The Draggable Red Line) with chapter notches & touch/pointer drag
                    val baseProgressFraction = if (uiState.durationMs > 0)
                        (uiState.currentPositionMs.toFloat() / uiState.durationMs.toFloat()).coerceIn(0f, 1f)
                    else 0f

                    val progressFraction = if (isDraggingScrubber) scrubberDragFraction else baseProgressFraction

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .focusRequester(scrubberRequester)
                            .onFocusChanged { isScrubberFocused = it.isFocused }
                            .focusable(true)
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    lastInteractionTime = System.currentTimeMillis()
                                    when (keyEvent.key) {
                                        Key.DirectionDown -> {
                                            try { playPauseRequester.requestFocus() } catch (_: Exception) {}
                                            true
                                        }
                                        Key.DirectionLeft -> {
                                            val now = System.currentTimeMillis()
                                            val dt = now - lastScrubberDpadTime
                                            lastScrubberDpadTime = now
                                            val delta = if (dt < 400L) {
                                                scrubberAccelerationFactor = (scrubberAccelerationFactor + 0.5f).coerceAtMost(6f)
                                                (-10_000L * scrubberAccelerationFactor).toLong()
                                            } else {
                                                scrubberAccelerationFactor = 1f
                                                -10_000L
                                            }
                                            viewModel.seekRelative(delta)
                                            feedbackToast = "${delta / 1000L}s"
                                            true
                                        }
                                        Key.DirectionRight -> {
                                            val now = System.currentTimeMillis()
                                            val dt = now - lastScrubberDpadTime
                                            lastScrubberDpadTime = now
                                            val delta = if (dt < 400L) {
                                                scrubberAccelerationFactor = (scrubberAccelerationFactor + 0.5f).coerceAtMost(6f)
                                                (10_000L * scrubberAccelerationFactor).toLong()
                                            } else {
                                                scrubberAccelerationFactor = 1f
                                                10_000L
                                            }
                                            viewModel.seekRelative(delta)
                                            feedbackToast = "+${delta / 1000L}s"
                                            true
                                        }
                                        Key.DirectionCenter, Key.Enter, Key.Spacebar -> {
                                            viewModel.togglePlayPause()
                                            true
                                        }
                                        Key.Back -> {
                                            try { playPauseRequester.requestFocus() } catch (_: Exception) {}
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .pointerInput(uiState.durationMs) {
                                detectTapGestures { offset ->
                                    lastInteractionTime = System.currentTimeMillis()
                                    val fullWidth = size.width.toFloat()
                                    if (fullWidth > 0f && uiState.durationMs > 0L) {
                                        val frac = (offset.x / fullWidth).coerceIn(0f, 1f)
                                        val targetMs = (frac * uiState.durationMs).toLong()
                                        viewModel.seekTo(targetMs)
                                    }
                                }
                            }
                            .pointerInput(uiState.durationMs) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        lastInteractionTime = System.currentTimeMillis()
                                        isDraggingScrubber = true
                                        val fullWidth = size.width.toFloat()
                                        if (fullWidth > 0f && uiState.durationMs > 0L) {
                                            scrubberDragFraction = (offset.x / fullWidth).coerceIn(0f, 1f)
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        lastInteractionTime = System.currentTimeMillis()
                                        val fullWidth = size.width.toFloat()
                                        if (fullWidth > 0f && uiState.durationMs > 0L) {
                                            val frac = (change.position.x / fullWidth).coerceIn(0f, 1f)
                                            scrubberDragFraction = frac
                                            val targetMs = (frac * uiState.durationMs).toLong()
                                            val now = System.currentTimeMillis()
                                            if (now - lastLiveSeekTime > 90L) {
                                                lastLiveSeekTime = now
                                                viewModel.seekTo(targetMs)
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (uiState.durationMs > 0L) {
                                            val targetMs = (scrubberDragFraction * uiState.durationMs).toLong()
                                            viewModel.seekTo(targetMs)
                                        }
                                        isDraggingScrubber = false
                                        lastInteractionTime = System.currentTimeMillis()
                                    },
                                    onDragCancel = {
                                        isDraggingScrubber = false
                                    }
                                )
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                        ) {
                            val isHighlighted = isDraggingScrubber || isScrubberFocused
                            val barHeight = if (isHighlighted) 7.dp.toPx() else 4.dp.toPx()
                            val yCenter = size.height / 2f
                            val fullWidth = size.width

                            // Base grey track
                            drawLine(
                                color = Color(0x55FFFFFF),
                                start = Offset(0f, yCenter),
                                end = Offset(fullWidth, yCenter),
                                strokeWidth = barHeight,
                                cap = StrokeCap.Round
                            )

                            // Red played progress
                            val playedX = (progressFraction * fullWidth).coerceIn(0f, fullWidth)
                            if (playedX > 0f) {
                                drawLine(
                                    color = YouTubeRed,
                                    start = Offset(0f, yCenter),
                                    end = Offset(playedX, yCenter),
                                    strokeWidth = barHeight,
                                    cap = StrokeCap.Round
                                )
                            }

                            // Chapter Marker notches
                            if (chapters.isNotEmpty() && uiState.durationMs > 0) {
                                for (ch in chapters) {
                                    val chFraction = ch.startTimeMs.toFloat() / uiState.durationMs.toFloat()
                                    if (chFraction in 0.005f..0.995f) {
                                        val notchX = chFraction * fullWidth
                                        drawLine(
                                            color = Color.Black.copy(alpha = 0.9f),
                                            start = Offset(notchX, yCenter - barHeight),
                                            end = Offset(notchX, yCenter + barHeight),
                                            strokeWidth = 3.dp.toPx()
                                        )
                                        drawLine(
                                            color = Color.White.copy(alpha = 0.95f),
                                            start = Offset(notchX + 0.5f, yCenter - barHeight),
                                            end = Offset(notchX + 0.5f, yCenter + barHeight),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                                }
                            }

                            // Draggable Scrubber Thumb (knob at progress head)
                            val thumbRadius = if (isDraggingScrubber) 9.dp.toPx() else if (isScrubberFocused) 7.dp.toPx() else 5.dp.toPx()
                            val thumbX = playedX.coerceIn(thumbRadius, fullWidth - thumbRadius)

                            // Outer halo / glow when focused or dragging
                            if (isHighlighted) {
                                drawCircle(
                                    color = YouTubeRed.copy(alpha = 0.35f),
                                    radius = thumbRadius + 5.dp.toPx(),
                                    center = Offset(thumbX, yCenter)
                                )
                            }

                            // Outer white ring
                            drawCircle(
                                color = Color.White,
                                radius = thumbRadius + 1.5.dp.toPx(),
                                center = Offset(thumbX, yCenter)
                            )

                            // Inner solid YouTube Red core
                            drawCircle(
                                color = YouTubeRed,
                                radius = thumbRadius,
                                center = Offset(thumbX, yCenter)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Symmetrical Playback Controls Row (Below the Red Line)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Cluster: Autoplay toggle & Mobile remote QR
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerIconButton(
                                icon = Icons.Default.Autorenew,
                                contentDescription = if (autoplayEnabled) "Autoplay: ON" else "Autoplay: OFF",
                                tint = if (autoplayEnabled) Color(0xFF4CAF50) else YouTubeSecondaryText,
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    val newState = !autoplayEnabled
                                    viewModel.setAutoplayEnabled(newState)
                                    feedbackToast = if (newState) "Autoplay Next: ON" else "Autoplay Next: OFF"
                                },
                                onDownPress = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    if (uiState.metadata?.suggestions?.isNotEmpty() == true) {
                                        osdLevel = 2
                                    }
                                },
                                onUpPress = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    try { scrubberRequester.requestFocus() } catch (_: Exception) {}
                                }
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            PlayerIconButton(
                                icon = Icons.Default.PhoneAndroid,
                                contentDescription = "Mobile Remote QR",
                                tint = YouTubeWhite,
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    showWebRemoteQr = true
                                },
                                onDownPress = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    if (uiState.metadata?.suggestions?.isNotEmpty() == true) {
                                        osdLevel = 2
                                    }
                                },
                                onUpPress = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    try { scrubberRequester.requestFocus() } catch (_: Exception) {}
                                }
                            )
                        }

                        // Center Cluster: Rewind 10s + Play/Pause + Fast Forward 10s
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerIconButton(
                                icon = Icons.Default.FastRewind,
                                contentDescription = "Rewind 10s",
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    viewModel.seekRelative(-10_000L)
                                },
                                onDownPress = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    if (uiState.metadata?.suggestions?.isNotEmpty() == true) {
                                        osdLevel = 2
                                    }
                                },
                                onUpPress = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    try { scrubberRequester.requestFocus() } catch (_: Exception) {}
                                }
                            )

                            PlayerIconButton(
                                icon = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                                focusRequester = playPauseRequester,
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    viewModel.togglePlayPause()
                                },
                                onDownPress = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    if (uiState.metadata?.suggestions?.isNotEmpty() == true) {
                                        osdLevel = 2
                                    }
                                },
                                onUpPress = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    try { scrubberRequester.requestFocus() } catch (_: Exception) {}
                                }
                            )

                            PlayerIconButton(
                                icon = Icons.Default.FastForward,
                                contentDescription = "Fast Forward 10s",
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    viewModel.seekRelative(10_000L)
                                },
                                onDownPress = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    if (uiState.metadata?.suggestions?.isNotEmpty() == true) {
                                        osdLevel = 2
                                    }
                                },
                                onUpPress = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    try { scrubberRequester.requestFocus() } catch (_: Exception) {}
                                }
                            )
                        }

                        // Right Cluster: Captions (CC) & Settings Drawer
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerIconButton(
                                icon = Icons.Default.ClosedCaption,
                                contentDescription = "Captions",
                                tint = if (subtitlesEnabled) YouTubeWhite else YouTubeSecondaryText.copy(alpha = 0.5f),
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    val enabled = viewModel.toggleSubtitles()
                                    feedbackToast = if (enabled) "Captions: ON" else "Captions: OFF"
                                },
                                onDownPress = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    if (uiState.metadata?.suggestions?.isNotEmpty() == true) {
                                        osdLevel = 2
                                    }
                                },
                                onUpPress = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    try { scrubberRequester.requestFocus() } catch (_: Exception) {}
                                }
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            // Ambient Mode (Ambilight) toggle & quick status
                            PlayerIconButton(
                                icon = Icons.Default.Palette,
                                contentDescription = if (ambilightConfig.enabled) "Ambient Lighting: ON" else "Ambient Lighting: OFF",
                                tint = if (ambilightConfig.enabled) Color(0xFFFFB300) else YouTubeSecondaryText.copy(alpha = 0.5f),
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    val newEnabled = viewModel.toggleAmbilight()
                                    feedbackToast = if (newEnabled) "Ambient Lighting: ON" else "Ambient Lighting: OFF"
                                },
                                onDownPress = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    if (uiState.metadata?.suggestions?.isNotEmpty() == true) {
                                        osdLevel = 2
                                    }
                                },
                                onUpPress = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    try { scrubberRequester.requestFocus() } catch (_: Exception) {}
                                }
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            PlayerIconButton(
                                icon = Icons.Default.Settings,
                                contentDescription = "Settings",
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    currentSettingsTab = QuickSettingsTab.MAIN
                                    showQuickSettings = true
                                },
                                onDownPress = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    if (uiState.metadata?.suggestions?.isNotEmpty() == true) {
                                        osdLevel = 2
                                    }
                                },
                                onUpPress = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    try { scrubberRequester.requestFocus() } catch (_: Exception) {}
                                }
                            )
                        }
                    }

                    // 4. Tri-Row Up Next Shelf (Visible on osdLevel 2 - Second DOWN press)
                    if (osdLevel == 2 && uiState.metadata?.suggestions?.isNotEmpty() == true) {
                        val suggestionSections = uiState.metadata?.suggestionSections ?: emptyList()
                        val seenShelfVideoIds = HashSet<String>()
                        val currentPlayingId = uiState.metadata?.videoId ?: ""
                        if (currentPlayingId.isNotBlank()) {
                            seenShelfVideoIds.add(currentPlayingId)
                        }

                        val row1Section = suggestionSections.getOrNull(0) ?: SectionRowModel(
                            title = "Up Next",
                            items = uiState.metadata?.suggestions?.take(10) ?: emptyList()
                        )
                        val row1Filtered = row1Section.items.filter { it.videoId.isNotBlank() && seenShelfVideoIds.add(it.videoId) }.take(10)
                        val row1 = row1Section.copy(items = row1Filtered)

                        val row2Section = suggestionSections.getOrNull(1)
                        val row2Filtered = (row2Section?.items ?: uiState.metadata?.suggestions?.drop(10) ?: emptyList())
                            .filter { it.videoId.isNotBlank() && seenShelfVideoIds.add(it.videoId) }.take(10)
                        val row2 = SectionRowModel(
                            title = row2Section?.title ?: if (!uiState.metadata?.author.isNullOrBlank()) "More from ${uiState.metadata?.author}" else "Related Videos",
                            items = row2Filtered
                        )

                        val row3Section = suggestionSections.getOrNull(2)
                        val row3Filtered = (row3Section?.items ?: uiState.metadata?.suggestions?.drop(20) ?: emptyList())
                            .filter { it.videoId.isNotBlank() && seenShelfVideoIds.add(it.videoId) }.take(10)
                        val row3 = SectionRowModel(
                            title = row3Section?.title ?: "Recommended for You",
                            items = row3Filtered
                        )

                        val allRows = listOf(row1, row2, row3).filter { it.items.isNotEmpty() }
                        val upNextScrollState = rememberScrollState()
                        var focusedRowIndex by remember { mutableStateOf(0) }
                        var focusedColIndex by remember { mutableStateOf(0) }
                        val rowRequesters = remember { List(3) { List(10) { FocusRequester() } } }

                        LaunchedEffect(focusedRowIndex) {
                            upNextScrollState.animateScrollTo(focusedRowIndex * 220)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            // Calculate exact card width so 4 columns fit evenly across the screen width
                            val totalWidth = maxWidth
                            val calculatedCardWidth = ((totalWidth - 36.dp - 8.dp) / 4).coerceAtLeast(180.dp)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 380.dp)
                                    .verticalScroll(upNextScrollState)
                            ) {
                                allRows.forEachIndexed { rIndex, rowSection ->
                                    if (rIndex > 0) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }

                                    // Shelf Header
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = rowSection.title,
                                            color = YouTubeWhite,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (rIndex == 0 && autoplayEnabled) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "• Auto-playing next",
                                                color = Color(0xFF4CAF50),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        itemsIndexed(
                                            items = rowSection.items,
                                            key = { cIndex, suggestion -> "upnext_r${rIndex}_${suggestion.videoId}_$cIndex" }
                                        ) { cIndex, suggestion ->
                                            val itemRequester = if (rIndex == 0 && cIndex == 0) upNextFirstCardRequester else rowRequesters[rIndex][cIndex]
                                            VideoCard(
                                                video = suggestion,
                                                onClick = {
                                                    osdLevel = 0
                                                    viewModel.setOsdVisible(false)
                                                    try { playerRootRequester.requestFocus() } catch (_: Exception) {}
                                                    onVideoSelected(suggestion)
                                                },
                                                onLongClick = {
                                                    selectedVideoForAction = suggestion
                                                },
                                                cardWidth = calculatedCardWidth,
                                                modifier = Modifier.focusRequester(itemRequester),
                                                isFirstInRow = (cIndex == 0),
                                                isLastInRow = (cIndex == rowSection.items.lastIndex),
                                                onFocused = {
                                                    lastInteractionTime = System.currentTimeMillis()
                                                    focusedRowIndex = rIndex
                                                    focusedColIndex = cIndex
                                                    viewModel.prefetchVideo(suggestion.videoId)
                                                },
                                                onLeftBoundaryPress = {
                                                    lastInteractionTime = System.currentTimeMillis()
                                                    if (cIndex == 0) {
                                                        true // Stay on first card of row, do not lose cursor
                                                    } else {
                                                        try {
                                                            if (rIndex == 0 && cIndex - 1 == 0) {
                                                                upNextFirstCardRequester.requestFocus()
                                                            } else {
                                                                rowRequesters[rIndex][cIndex - 1].requestFocus()
                                                            }
                                                        } catch (_: Exception) {}
                                                        true
                                                    }
                                                },
                                                onRightBoundaryPress = {
                                                    lastInteractionTime = System.currentTimeMillis()
                                                    if (cIndex == rowSection.items.lastIndex) {
                                                        true // Stay on last card of row
                                                    } else {
                                                        try {
                                                            rowRequesters[rIndex][cIndex + 1].requestFocus()
                                                        } catch (_: Exception) {}
                                                        true
                                                    }
                                                },
                                                onUpBoundaryPress = {
                                                    lastInteractionTime = System.currentTimeMillis()
                                                    if (rIndex == 0) {
                                                        osdLevel = 1
                                                        try { playPauseRequester.requestFocus() } catch (_: Exception) {}
                                                        true
                                                    } else {
                                                        val targetCol = minOf(cIndex, allRows[rIndex - 1].items.lastIndex)
                                                        try {
                                                            if (rIndex - 1 == 0 && targetCol == 0) {
                                                                upNextFirstCardRequester.requestFocus()
                                                            } else {
                                                                rowRequesters[rIndex - 1][targetCol].requestFocus()
                                                            }
                                                        } catch (_: Exception) {}
                                                        true
                                                    }
                                                },
                                                onDownBoundaryPress = {
                                                    lastInteractionTime = System.currentTimeMillis()
                                                    if (rIndex >= allRows.lastIndex) {
                                                        true // Clamp on bottom row: do not lose cursor
                                                    } else {
                                                        val targetCol = minOf(cIndex, allRows[rIndex + 1].items.lastIndex)
                                                        try {
                                                            rowRequesters[rIndex + 1][targetCol].requestFocus()
                                                        } catch (_: Exception) {}
                                                        true
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // ── YouTube TV Leanback Settings Drawer ───────────────────────
        if (showQuickSettings) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterEnd
            ) {
                // Dimmed scrim overlay behind drawer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.72f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                showQuickSettings = false
                                try { playPauseRequester.requestFocus() } catch (_: Exception) {}
                            }
                        )
                )

                // Drawer panel
                Column(
                    modifier = Modifier
                        .width(420.dp)
                        .fillMaxHeight()
                        .background(YouTubeSurface)
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    // Header with Title / Back
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (currentSettingsTab != QuickSettingsTab.MAIN) {
                                PlayerIconButton(
                                    icon = Icons.Default.ArrowBack,
                                    contentDescription = "Back",
                                    onClick = { currentSettingsTab = QuickSettingsTab.MAIN }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Text(
                                text = when (currentSettingsTab) {
                                    QuickSettingsTab.MAIN -> "Playback Settings"
                                    QuickSettingsTab.QUALITY -> "Video Quality"
                                    QuickSettingsTab.SPEED -> "Playback Speed"
                                    QuickSettingsTab.AUDIO_LANGUAGE -> "Audio Language"
                                    QuickSettingsTab.SUBTITLES -> "Subtitles & CC"
                                    QuickSettingsTab.CAPTION_STYLE -> "Caption Style"
                                    QuickSettingsTab.DESCRIPTION -> "Video Details"
                                },
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = YouTubeWhite
                            )
                        }
                        PlayerIconButton(
                            icon = Icons.Default.Close,
                            contentDescription = "Close",
                            onClick = {
                                showQuickSettings = false
                                try { playPauseRequester.requestFocus() } catch (_: Exception) {}
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    val audioTracks = remember(currentSettingsTab, showQuickSettings) { viewModel.getAvailableAudioTracks() }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when (currentSettingsTab) {
                            QuickSettingsTab.MAIN -> {
                                item {
                                    QuickSettingsRow(
                                        title = "Quality",
                                        value = selectedResolutionLabel,
                                        icon = Icons.Default.HighQuality,
                                        hasSubmenu = true,
                                        focusRequester = settingsFirstItemRequester,
                                        onClick = { currentSettingsTab = QuickSettingsTab.QUALITY }
                                    )
                                }
                                item {
                                    val currentSelectedAudio = audioTracks.find { it.isSelected }?.name
                                        ?: "English (Default)"
                                    QuickSettingsRow(
                                        title = "Audio Language",
                                        value = currentSelectedAudio,
                                        icon = Icons.Default.Translate,
                                        hasSubmenu = true,
                                        onClick = { currentSettingsTab = QuickSettingsTab.AUDIO_LANGUAGE }
                                    )
                                }
                                item {
                                    QuickSettingsRow(
                                        title = "Subtitles / CC",
                                        value = if (subtitlesEnabled) "English" else "Off",
                                        icon = Icons.Default.ClosedCaption,
                                        hasSubmenu = true,
                                        onClick = { currentSettingsTab = QuickSettingsTab.SUBTITLES }
                                    )
                                }
                                item {
                                    QuickSettingsRow(
                                        title = "Caption Style",
                                        value = "${captionTextSizeSp.toInt()}sp",
                                        icon = Icons.Default.Palette,
                                        hasSubmenu = true,
                                        onClick = { currentSettingsTab = QuickSettingsTab.CAPTION_STYLE }
                                    )
                                }
                                item {
                                    QuickSettingsRow(
                                        title = "Playback Speed",
                                        value = "${playbackSpeed}x",
                                        icon = Icons.Default.Speed,
                                        hasSubmenu = true,
                                        onClick = { currentSettingsTab = QuickSettingsTab.SPEED }
                                    )
                                }
                                item {
                                    QuickSettingsRow(
                                        title = "Voice Clarity (Dialogue Boost)",
                                        value = if (voiceClarityEnabled) "ON (+6dB)" else "OFF",
                                        icon = Icons.Default.GraphicEq,
                                        isSelected = voiceClarityEnabled,
                                        onClick = {
                                            val state = viewModel.toggleVoiceClarity()
                                            feedbackToast = if (state) "Voice Clarity: ON (+6dB)" else "Voice Clarity: OFF"
                                        }
                                    )
                                }
                                item {
                                    QuickSettingsRow(
                                        title = "Match Frame Rate (AFR)",
                                        value = if (afrEnabled) "ON (24p)" else "OFF",
                                        icon = Icons.Default.Tv,
                                        isSelected = afrEnabled,
                                        onClick = {
                                            val state = viewModel.toggleAfr()
                                            feedbackToast = if (state) "Match Frame Rate: ON (24p)" else "Match Frame Rate: OFF"
                                        }
                                    )
                                }
                                item {
                                    QuickSettingsRow(
                                        title = "Autoplay Next",
                                        value = if (autoplayEnabled) "ON" else "OFF",
                                        icon = Icons.Default.Autorenew,
                                        isSelected = autoplayEnabled,
                                        onClick = {
                                            val state = !autoplayEnabled
                                            viewModel.setAutoplayEnabled(state)
                                            feedbackToast = if (state) "Autoplay: ON" else "Autoplay: OFF"
                                        }
                                    )
                                }
                                item {
                                    QuickSettingsRow(
                                        title = "Loop / Repeat Video",
                                        value = if (loopEnabled) "ON" else "OFF",
                                        icon = Icons.Default.Repeat,
                                        isSelected = loopEnabled,
                                        onClick = {
                                            val state = viewModel.toggleLoop()
                                            feedbackToast = if (state) "Loop Video: ON" else "Loop Video: OFF"
                                        }
                                    )
                                }
                                item {
                                    QuickSettingsRow(
                                        title = "Mobile Web Remote",
                                        value = "QR Code",
                                        icon = Icons.Default.PhoneAndroid,
                                        onClick = {
                                            showQuickSettings = false
                                            showWebRemoteQr = true
                                        }
                                    )
                                }
                                item {
                                    val sponsorBlockEnabled by viewModel.sponsorBlockEnabled.collectAsState()
                                    QuickSettingsRow(
                                        title = "SponsorBlock Auto-Skip",
                                        value = if (sponsorBlockEnabled) "ON" else "OFF",
                                        icon = Icons.Default.Block,
                                        isSelected = sponsorBlockEnabled,
                                        onClick = {
                                            val state = viewModel.toggleSponsorBlock()
                                            feedbackToast = if (state) "SponsorBlock: ON" else "SponsorBlock: OFF"
                                        }
                                    )
                                }
                                item {
                                    val adBlockEnabled by viewModel.adBlockEnabled.collectAsState()
                                    QuickSettingsRow(
                                        title = "Ad-Free Streaming (AdBlock)",
                                        value = if (adBlockEnabled) "ON" else "OFF",
                                        icon = Icons.Default.Security,
                                        isSelected = adBlockEnabled,
                                        onClick = {
                                            val state = viewModel.toggleAdBlock()
                                            feedbackToast = if (state) "AdBlock: ON" else "AdBlock: OFF"
                                        }
                                    )
                                }
                                item {
                                    QuickSettingsRow(
                                        title = "Video Details & Description",
                                        value = "View Info",
                                        icon = Icons.Default.Info,
                                        hasSubmenu = true,
                                        onClick = { currentSettingsTab = QuickSettingsTab.DESCRIPTION }
                                    )
                                }
                                item {
                                    QuickSettingsRow(
                                        title = "Stats for Nerds",
                                        value = if (statsForNerdsEnabled) "ON" else "OFF",
                                        icon = Icons.Default.Info,
                                        isSelected = statsForNerdsEnabled,
                                        onClick = {
                                            val state = viewModel.toggleStatsForNerds()
                                            feedbackToast = if (state) "Stats for Nerds: ON" else "Stats for Nerds: OFF"
                                        }
                                    )
                                }
                            }

                            QuickSettingsTab.QUALITY -> {
                                val resolutions = listOf(
                                    "Auto (4K UHD)" to 2160,
                                    "1440p (QHD)" to 1440,
                                    "1080p (Full HD)" to 1080,
                                    "720p (HD)" to 720,
                                    "480p (SD)" to 480
                                )
                                itemsIndexed(resolutions) { index, (label, res) ->
                                    val isSelected = selectedResolutionLabel == label
                                    QuickSettingsRow(
                                        title = label,
                                        isSelected = isSelected,
                                        focusRequester = if (index == 0) settingsFirstItemRequester else null,
                                        onClick = {
                                            selectedResolutionLabel = label
                                            viewModel.setMaxVideoResolution(res)
                                            feedbackToast = "Resolution: $label"
                                            showQuickSettings = false
                                            try { playPauseRequester.requestFocus() } catch (_: Exception) {}
                                        }
                                    )
                                }
                            }

                            QuickSettingsTab.SPEED -> {
                                val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                                itemsIndexed(speeds) { index, speed ->
                                    val isSelected = (playbackSpeed == speed)
                                    val speedLabel = if (speed == 1.0f) "Normal (1.0x)" else "${speed}x"
                                    QuickSettingsRow(
                                        title = speedLabel,
                                        isSelected = isSelected,
                                        focusRequester = if (index == 0) settingsFirstItemRequester else null,
                                        onClick = {
                                            viewModel.setPlaybackSpeed(speed)
                                            feedbackToast = "Playback Speed: ${speed}x"
                                            currentSettingsTab = QuickSettingsTab.MAIN
                                        }
                                    )
                                }
                            }

                            QuickSettingsTab.AUDIO_LANGUAGE -> {
                                if (audioTracks.isEmpty()) {
                                    item {
                                        QuickSettingsRow(
                                            title = "English (Default)",
                                            isSelected = true,
                                            focusRequester = settingsFirstItemRequester,
                                            onClick = {
                                                viewModel.resetAudioTrackToDefault()
                                                currentSettingsTab = QuickSettingsTab.MAIN
                                            }
                                        )
                                    }
                                } else {
                                    itemsIndexed(audioTracks) { index, trackOption ->
                                        QuickSettingsRow(
                                            title = trackOption.name,
                                            isSelected = trackOption.isSelected,
                                            focusRequester = if (index == 0) settingsFirstItemRequester else null,
                                            onClick = {
                                                viewModel.selectAudioTrack(trackOption)
                                                feedbackToast = "Audio: ${trackOption.name}"
                                                currentSettingsTab = QuickSettingsTab.MAIN
                                            }
                                        )
                                    }
                                }
                            }

                            QuickSettingsTab.SUBTITLES -> {
                                item {
                                    QuickSettingsRow(
                                        title = "Off",
                                        isSelected = !subtitlesEnabled,
                                        focusRequester = settingsFirstItemRequester,
                                        onClick = {
                                            if (subtitlesEnabled) {
                                                viewModel.toggleSubtitles()
                                            }
                                            feedbackToast = "Captions: OFF"
                                            currentSettingsTab = QuickSettingsTab.MAIN
                                        }
                                    )
                                }
                                item {
                                    QuickSettingsRow(
                                        title = "English (auto-generated)",
                                        isSelected = subtitlesEnabled,
                                        onClick = {
                                            if (!subtitlesEnabled) {
                                                viewModel.toggleSubtitles()
                                            }
                                            feedbackToast = "Captions: English (auto-generated)"
                                            currentSettingsTab = QuickSettingsTab.MAIN
                                        }
                                    )
                                }
                                item {
                                    QuickSettingsRow(
                                        title = "Customize Caption Style",
                                        hasSubmenu = true,
                                        icon = Icons.Default.Palette,
                                        onClick = { currentSettingsTab = QuickSettingsTab.CAPTION_STYLE }
                                    )
                                }
                            }

                            QuickSettingsTab.CAPTION_STYLE -> {
                                item {
                                    // Live Subtitle Preview Box
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.8f))
                                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Sample Subtitle Text Preview",
                                            fontSize = captionTextSizeSp.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(captionTextColorInt),
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier
                                                .background(
                                                    color = Color(captionBgColorInt),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                item {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "TEXT SIZE",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = YouTubeSecondaryText
                                    )
                                }
                                val sizes = listOf(
                                    "Small" to 14f,
                                    "Normal" to 18f,
                                    "Large" to 24f,
                                    "Extra Large" to 32f
                                )
                                itemsIndexed(sizes) { index, (label, size) ->
                                    QuickSettingsRow(
                                        title = label,
                                        value = "${size.toInt()}sp",
                                        isSelected = (captionTextSizeSp == size),
                                        focusRequester = if (index == 0) settingsFirstItemRequester else null,
                                        onClick = {
                                            captionTextSizeSp = size
                                            captionPrefs.edit().putFloat("caption_size_sp", size).apply()
                                            applySubtitleStyle(subtitleViewRef, size, captionTextColorInt, captionBgColorInt)
                                            feedbackToast = "Caption Size: $label"
                                        }
                                    )
                                }

                                item {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "TEXT COLOR",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = YouTubeSecondaryText
                                    )
                                }
                                val colors = listOf(
                                    "White" to android.graphics.Color.WHITE,
                                    "Yellow" to android.graphics.Color.YELLOW,
                                    "Cyan" to android.graphics.Color.CYAN,
                                    "Green" to android.graphics.Color.GREEN
                                )
                                itemsIndexed(colors) { _, (label, colorInt) ->
                                    QuickSettingsRow(
                                        title = label,
                                        isSelected = (captionTextColorInt == colorInt),
                                        onClick = {
                                            captionTextColorInt = colorInt
                                            captionPrefs.edit().putInt("caption_color_int", colorInt).apply()
                                            applySubtitleStyle(subtitleViewRef, captionTextSizeSp, colorInt, captionBgColorInt)
                                            feedbackToast = "Caption Color: $label"
                                        }
                                    )
                                }

                                item {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "BACKGROUND",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = YouTubeSecondaryText
                                    )
                                }
                                val backgrounds = listOf(
                                    "75% Black" to android.graphics.Color.argb(192, 0, 0, 0),
                                    "Solid Black" to android.graphics.Color.BLACK,
                                    "Transparent" to android.graphics.Color.TRANSPARENT,
                                    "Dark Blue" to android.graphics.Color.argb(200, 10, 25, 47)
                                )
                                itemsIndexed(backgrounds) { _, (label, bgInt) ->
                                    QuickSettingsRow(
                                        title = label,
                                        isSelected = (captionBgColorInt == bgInt),
                                        onClick = {
                                            captionBgColorInt = bgInt
                                            captionPrefs.edit().putInt("caption_bg_int", bgInt).apply()
                                            applySubtitleStyle(subtitleViewRef, captionTextSizeSp, captionTextColorInt, bgInt)
                                            feedbackToast = "Caption Background: $label"
                                        }
                                    )
                                }
                            }

                            QuickSettingsTab.DESCRIPTION -> {
                                item {
                                    val meta = uiState.metadata
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = meta?.title ?: uiState.videoId,
                                            color = YouTubeWhite,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "${meta?.author ?: ""} • ${meta?.subscriberCount ?: ""} subscribers",
                                            color = Color(0xFF42A5F5),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${meta?.viewCount ?: ""} views • ${meta?.publishedDate ?: ""}",
                                            color = YouTubeSecondaryText,
                                            fontSize = 12.sp
                                        )
                                        if (!meta?.likeCount.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "👍 ${meta?.likeCount} likes",
                                                color = YouTubeSecondaryText,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(14.dp))

                                        val descText = meta?.description?.takeIf { it.isNotBlank() } ?: "No description available for this video."
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF181818))
                                                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                                                .padding(14.dp)
                                                .focusRequester(settingsFirstItemRequester)
                                                .focusable(true)
                                        ) {
                                            Text(
                                                text = descText,
                                                color = YouTubeWhite,
                                                fontSize = 13.sp,
                                                lineHeight = 20.sp
                                            )
                                        }

                                        if (!meta?.chapters.isNullOrEmpty()) {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = "CHAPTERS",
                                                color = YouTubeSecondaryText,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            meta?.chapters?.forEach { chapter ->
                                                val sec = chapter.startTimeMs / 1000
                                                val timeStr = "%02d:%02d".format(sec / 60, sec % 60)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .clickable {
                                                            viewModel.seekTo(chapter.startTimeMs)
                                                            feedbackToast = "Chapter: ${chapter.title}"
                                                            showQuickSettings = false
                                                        }
                                                        .padding(vertical = 6.dp, horizontal = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(text = timeStr, color = YouTubeRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Text(text = chapter.title, color = YouTubeWhite, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Mobile Web Remote QR Code Modal Overlay ──────────────────
        if (showWebRemoteQr) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            showWebRemoteQr = false
                            try { playPauseRequester.requestFocus() } catch (_: Exception) {}
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(YouTubeSurface)
                        .border(2.dp, YouTubeFocusedBorder, RoundedCornerShape(20.dp))
                        .padding(32.dp)
                ) {
                    Text(
                        text = "Mobile Web Remote",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = YouTubeWhite
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Point your phone camera at this QR code to connect instantly",
                        fontSize = 14.sp,
                        color = YouTubeSecondaryText
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    QrCodeView(
                        content = viewModel.webRemoteUrl,
                        size = 200.dp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = viewModel.webRemoteUrl,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    PlayerIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Close",
                        focusRequester = webRemoteCloseRequester,
                        onClick = {
                            showWebRemoteQr = false
                            try { playPauseRequester.requestFocus() } catch (_: Exception) {}
                        }
                    )
                }
            }
        }

        // ── Long-Press Context Menu Modal ────────────────────────────
        if (selectedVideoForAction != null) {
            val targetVideo = selectedVideoForAction!!
            com.renotube.tv.ui.components.VideoActionModal(
                video = targetVideo,
                onViewChannel = { author, chId ->
                    onBack()
                    onViewChannel?.invoke(author, chId)
                },
                onPlayNext = { video ->
                    viewModel.queuePlayNext(video)
                    feedbackToast = "Added to play next: ${video.title}"
                },
                onAddToPlaylist = { video ->
                    viewModel.addToPlaylist(video) { added ->
                        feedbackToast = if (added) "Saved to Watch Later" else "Already in Watch Later"
                    }
                },
                onRemoveFromSuggestions = { videoId ->
                    viewModel.hideVideo(videoId)
                    feedbackToast = "Removed from suggestions"
                },
                onBlockChannel = { author ->
                    viewModel.blockChannel(author)
                    feedbackToast = "Won't recommend '$author'"
                },
                onDismiss = { selectedVideoForAction = null }
            )
        }
    }
}

@Composable
private fun QuickSettingsRow(
    title: String,
    value: String? = null,
    icon: ImageVector? = null,
    isSelected: Boolean = false,
    hasSubmenu: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(true)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) YouTubeWhite
                else if (isSelected) YouTubeSurface.copy(alpha = 0.5f)
                else Color.Transparent
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) YouTubeFocusedBorder else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isFocused) YouTubeBlack else YouTubeSecondaryText,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isFocused) YouTubeBlack else YouTubeWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(
                    text = value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (isFocused) Color(0xFF444444) else YouTubeSecondaryText
                )
            }
            if (hasSubmenu) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = if (isFocused) YouTubeBlack else YouTubeSecondaryText,
                    modifier = Modifier.size(18.dp)
                )
            } else if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = if (isFocused) YouTubeBlack else YouTubeRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String? = null,
    focusRequester: FocusRequester? = null,
    tint: Color? = null,
    onDownPress: (() -> Unit)? = null,
    onUpPress: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(50.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(true)
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) YouTubeFocusedBorder else Color.Transparent,
                shape = CircleShape
            )
            .background(
                color = if (isFocused) YouTubeWhite else YouTubeSurface,
                shape = CircleShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionDown -> {
                            if (onDownPress != null) {
                                onDownPress.invoke()
                                true
                            } else false
                        }
                        Key.DirectionUp -> {
                            if (onUpPress != null) {
                                onUpPress.invoke()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) YouTubeBlack else (tint ?: YouTubeWhite),
            modifier = Modifier.size(24.dp)
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
