package com.renotube.tv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import com.renotube.tv.data.model.VideoCardModel
import com.renotube.tv.ui.components.NavigationSidebar
import com.renotube.tv.ui.screens.home.HomeScreen
import com.renotube.tv.ui.screens.home.HomeViewModel
import com.renotube.tv.ui.screens.player.PlayerScreen
import com.renotube.tv.ui.screens.player.PlayerViewModel
import com.renotube.tv.ui.screens.search.SearchScreen
import com.renotube.tv.ui.screens.search.SearchViewModel
import com.renotube.tv.ui.screens.settings.SettingsScreen
import com.renotube.tv.ui.screens.settings.SettingsViewModel
import com.renotube.tv.ui.theme.RenoTubeTheme
import com.renotube.tv.ui.theme.YouTubeBlack
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.renotube.tv.perf.PerformanceHud
import com.renotube.tv.perf.PerformanceTracker
import dagger.hilt.android.AndroidEntryPoint

import androidx.lifecycle.lifecycleScope
import com.renotube.tv.remote.RemoteCommandDispatcher
import com.renotube.tv.remote.WebRemoteServer
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var webRemoteServer: WebRemoteServer

    private val homeViewModel: HomeViewModel by viewModels()
    private val searchViewModel: SearchViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    val activeVideoIdState = mutableStateOf<String?>(null)
    val isSidebarExpandedState = mutableStateOf(false)
    val currentRouteState = mutableStateOf("home")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep TV screen awake at all times during app usage
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        RemoteCommandDispatcher.registerActivity(this)
        lifecycleScope.launch {
            webRemoteServer.start(this)
        }

        setContent {
            RenoTubeTheme {
                MainTvShell(
                    homeViewModel = homeViewModel,
                    searchViewModel = searchViewModel,
                    playerViewModel = playerViewModel,
                    settingsViewModel = settingsViewModel,
                    activeVideoIdState = activeVideoIdState,
                    isSidebarExpandedState = isSidebarExpandedState,
                    currentRouteState = currentRouteState
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        RemoteCommandDispatcher.unregisterActivity(this)
        webRemoteServer.stop()
    }

    fun onRemoteHome() {
        if (activeVideoIdState.value != null) {
            playerViewModel.stopPlayback()
            activeVideoIdState.value = null
        }
        currentRouteState.value = "home"
        homeViewModel.loadCategory("home")
    }

    fun onRemoteSearch(query: String) {
        if (activeVideoIdState.value != null) {
            playerViewModel.stopPlayback()
            activeVideoIdState.value = null
        }
        currentRouteState.value = "search"
        searchViewModel.onQueryChange(query)
        searchViewModel.executeSearch(query)
    }

    fun onRemotePlay(videoId: String) {
        if (activeVideoIdState.value != null) {
            playerViewModel.stopPlayback()
        }
        com.renotube.tv.perf.PerformanceTracker.onStreamClicked(videoId)
        activeVideoIdState.value = videoId
        playerViewModel.loadVideo(videoId)
    }

    fun getCurrentPositionMs(): Long {
        return playerViewModel.uiState.value.currentPositionMs
    }

    fun getPlayerStatusJson(): String {
        val videoId = activeVideoIdState.value ?: return "{\"active\":false}"
        val ui = playerViewModel.uiState.value
        return "{\"active\":true,\"videoId\":\"$videoId\",\"positionMs\":${ui.currentPositionMs},\"durationMs\":${ui.durationMs},\"isPlaying\":${ui.isPlaying}}"
    }

    fun seekToMs(targetMs: Long) {
        runOnUiThread {
            playerViewModel.seekTo(targetMs)
        }
    }

    fun pausePlayback() {
        runOnUiThread {
            playerViewModel.exoPlayer.pause()
        }
    }

    fun resumePlayback() {
        runOnUiThread {
            playerViewModel.exoPlayer.play()
        }
    }

    fun onRemoteAction(action: String): Boolean {
        if (activeVideoIdState.value != null) {
            val upper = action.uppercase()
            if (upper.startsWith("SEEK:")) {
                val frac = upper.substringAfter("SEEK:").toFloatOrNull()
                if (frac != null) {
                    val dur = playerViewModel.uiState.value.durationMs
                    if (dur > 0) {
                        val targetMs = (frac.coerceIn(0f, 1f) * dur).toLong()
                        runOnUiThread { playerViewModel.seekTo(targetMs) }
                        return true
                    }
                }
            }
            if (upper.startsWith("SEEK_MS:")) {
                val ms = upper.substringAfter("SEEK_MS:").toLongOrNull()
                if (ms != null) {
                    runOnUiThread { playerViewModel.seekTo(ms) }
                    return true
                }
            }
            if (upper == "PAUSE") {
                runOnUiThread { playerViewModel.exoPlayer.pause() }
                return true
            }
            if (upper == "PLAY" || upper == "RESUME") {
                runOnUiThread { playerViewModel.exoPlayer.play() }
                return true
            }
            when (upper) {
                "SETTINGS", "OPEN_SETTINGS" -> {
                    runOnUiThread { playerViewModel.triggerAction("OPEN_SETTINGS") }
                    return true
                }
                "QUALITY" -> {
                    runOnUiThread { playerViewModel.triggerAction("QUALITY") }
                    return true
                }
                "SPEED" -> {
                    runOnUiThread { playerViewModel.triggerAction("SPEED") }
                    return true
                }
                "CLOSE_SETTINGS" -> {
                    runOnUiThread { playerViewModel.triggerAction("CLOSE_SETTINGS") }
                    return true
                }
                "SUBTITLES", "CC" -> {
                    runOnUiThread { playerViewModel.toggleSubtitles() }
                    return true
                }
                "VOICE_BOOST", "VOICE_CLARITY" -> {
                    runOnUiThread { playerViewModel.toggleVoiceClarity() }
                    return true
                }
                "STATS" -> {
                    runOnUiThread { playerViewModel.toggleStatsForNerds() }
                    return true
                }
                "NEXT", "NEXT_VIDEO" -> {
                    runOnUiThread { playerViewModel.playNextVideo() }
                    return true
                }
                "PREVIOUS", "PREV_VIDEO" -> {
                    runOnUiThread { playerViewModel.seekRelative(-10000) }
                    return true
                }
                "FORWARD", "FAST_FORWARD", "FF" -> {
                    runOnUiThread { playerViewModel.seekRelative(30000) }
                    return true
                }
                "SEEK_END" -> {
                    runOnUiThread {
                        val dur = playerViewModel.exoPlayer.duration
                        val cur = playerViewModel.exoPlayer.currentPosition
                        if (dur > 0 && cur < dur) {
                            playerViewModel.seekRelative(dur - cur - 2500)
                        }
                    }
                    return true
                }
            }
        }
        return false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_STOP -> {
                    if (activeVideoIdState.value != null) {
                        playerViewModel.stopPlayback()
                        activeVideoIdState.value = null
                        return true
                    }
                }
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                    // Always allow Compose BackHandler (e.g. VideoActionModal, dialogs) to process back first!
                    val handled = super.dispatchKeyEvent(event)
                    if (handled) {
                        return true
                    }

                    if (activeVideoIdState.value != null) {
                        // Failsafe: if nothing in Compose consumed BACK while player was open, close player cleanly
                        playerViewModel.stopPlayback()
                        activeVideoIdState.value = null
                        return true
                    } else {
                        // While surfing main screen: 1st back opens sidebar menu, 2nd back exits app
                        if (!isSidebarExpandedState.value) {
                            isSidebarExpandedState.value = true
                            return true
                        } else {
                            finish()
                            return true
                        }
                    }
                }
            }
        }
        return try {
            super.dispatchKeyEvent(event)
        } catch (e: Exception) {
            // Protect against Compose focus search bugs on Android TV (e.g. LayoutCoordinate / isAttached == false)
            android.util.Log.w("MainActivity", "Handled Compose focus traversal anomaly: ${e.message}")
            true
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MainTvShell(
    homeViewModel: HomeViewModel,
    searchViewModel: SearchViewModel,
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel,
    activeVideoIdState: MutableState<String?>,
    isSidebarExpandedState: MutableState<Boolean>,
    currentRouteState: MutableState<String>
) {
    val currentRoute = currentRouteState.value
    val activeVideoId = activeVideoIdState.value

    // Automatically change WLED Ambilight to warm white and refresh personalized feed whenever user returns to Home / browsing screen
    LaunchedEffect(activeVideoId) {
        if (activeVideoId == null) {
            settingsViewModel.setWarmWhite()
            homeViewModel.refreshPersonalizedFeed(force = false)
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1200)
        settingsViewModel.setWarmWhite()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (activeVideoId != null) Color.Transparent else YouTubeBlack)
    ) {
        if (activeVideoId != null) {
            // Fullscreen Player View - Only component mounted to eliminate GPU overdraw & allow pure hardware overlay promotion
            PlayerScreen(
                viewModel = playerViewModel,
                onVideoSelected = { video ->
                    activeVideoIdState.value = video.videoId
                    playerViewModel.loadVideo(
                        videoId = video.videoId,
                        title = video.title,
                        author = video.author,
                        thumbnailUrl = video.thumbnailUrl,
                        clickTrackingParams = video.clickTrackingParams
                    )
                },
                onBack = {
                    playerViewModel.stopPlayback()
                    activeVideoIdState.value = null
                },
                onViewChannel = { channelName, channelId ->
                    activeVideoIdState.value = null
                    currentRouteState.value = "home"
                    homeViewModel.loadChannel(channelName, channelId)
                }
            )
        } else {
            val contentFocusRequester = remember { FocusRequester() }
            val sidebarActiveItemRequester = remember { FocusRequester() }
            val navigateToSidebar: () -> Unit = {
                try {
                    sidebarActiveItemRequester.requestFocus()
                } catch (_: Exception) {}
            }

            // Standard Leanback 2-Column Shell (Sidebar + Content)
            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                NavigationSidebar(
                    selectedRoute = currentRoute,
                    onRouteSelected = { route ->
                        if (currentRoute != route) {
                            if (currentRoute == "search" || route == "search") {
                                searchViewModel.resetSearch()
                            }
                            currentRouteState.value = route
                            if (route != "search" && route != "settings") {
                                homeViewModel.loadCategory(route)
                            }
                        } else if (route == "home") {
                            // Re-visiting home refreshes recent history row
                            homeViewModel.loadCategory("home")
                        }
                    },
                    isExpandedState = isSidebarExpandedState,
                    onNavigateToContent = {
                        try { contentFocusRequester.requestFocus() } catch (_: Exception) {}
                    },
                    activeItemFocusRequester = sidebarActiveItemRequester
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .focusRequester(contentFocusRequester)
                        .focusGroup()
                ) {
                    when (currentRoute) {
                        "home" -> HomeScreen(
                            viewModel = homeViewModel,
                            isPlayerActive = false,
                            onNavigateToSidebar = navigateToSidebar,
                            onVideoSelected = { video ->
                                PerformanceTracker.onStreamClicked(video.videoId)
                                activeVideoIdState.value = video.videoId
                                playerViewModel.loadVideo(
                                    videoId = video.videoId,
                                    title = video.title,
                                    author = video.author,
                                    thumbnailUrl = video.thumbnailUrl,
                                    clickTrackingParams = video.clickTrackingParams
                                )
                            }
                        )
                        "search" -> SearchScreen(
                            viewModel = searchViewModel,
                            onVideoSelected = { video ->
                                PerformanceTracker.onStreamClicked(video.videoId)
                                activeVideoIdState.value = video.videoId
                                playerViewModel.loadVideo(
                                    videoId = video.videoId,
                                    title = video.title,
                                    author = video.author,
                                    thumbnailUrl = video.thumbnailUrl,
                                    clickTrackingParams = video.clickTrackingParams
                                )
                            },
                            onViewChannel = { channelName, channelId ->
                                currentRouteState.value = "home"
                                homeViewModel.loadChannel(channelName, channelId)
                            }
                        )
                        "settings" -> SettingsScreen(
                            viewModel = settingsViewModel,
                            onNavigateToSidebar = navigateToSidebar
                        )
                        else -> HomeScreen(
                            viewModel = homeViewModel,
                            isPlayerActive = false,
                            onNavigateToSidebar = navigateToSidebar,
                            onVideoSelected = { video ->
                                PerformanceTracker.onStreamClicked(video.videoId)
                                activeVideoIdState.value = video.videoId
                                playerViewModel.loadVideo(
                                    videoId = video.videoId,
                                    title = video.title,
                                    author = video.author,
                                    thumbnailUrl = video.thumbnailUrl,
                                    clickTrackingParams = video.clickTrackingParams
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
