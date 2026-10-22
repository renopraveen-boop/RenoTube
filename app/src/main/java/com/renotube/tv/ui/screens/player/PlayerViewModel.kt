package com.renotube.tv.ui.screens.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import android.graphics.Bitmap
import com.renotube.tv.ambilight.AmbilightConfig
import com.renotube.tv.ambilight.AmbilightProcessor
import com.renotube.tv.ambilight.AmbilightRepository
import com.renotube.tv.data.model.SponsorSegmentModel
import com.renotube.tv.data.model.VideoPlaybackMetadata
import com.renotube.tv.data.repository.MediaRepository
import com.renotube.tv.player.RenoTubePlayer
import com.renotube.tv.player.PlaybackQueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

import android.content.Context
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo
import com.liskovsoft.youtubeapi.track.TrackingService
import dagger.hilt.android.qualifiers.ApplicationContext

private const val TAG = "PlayerViewModel"

data class PlayerUiState(
    val videoId: String = "",
    val initialTitle: String = "",
    val initialAuthor: String = "",
    val initialThumbnail: String = "",
    val isFirstFrameRendered: Boolean = false,
    val isLoading: Boolean = true,
    val metadata: VideoPlaybackMetadata? = null,
    val sponsorSegments: List<SponsorSegmentModel> = emptyList(),
    val isLiveStream: Boolean = false,
    val isPlaying: Boolean = true,
    val isPlayWhenReady: Boolean = true,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val isOsdVisible: Boolean = true,
    val lastSkippedSegment: SponsorSegmentModel? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val contentRepository: com.renotube.tv.data.repository.ContentRepository,
    private val renoTubePlayer: RenoTubePlayer,
    private val ambilightRepository: AmbilightRepository,
    private val ambilightProcessor: AmbilightProcessor,
    private val userHistoryRepository: com.renotube.tv.data.repository.UserHistoryRepository,
    private val playbackQueueManager: PlaybackQueueManager,
    private val cpuAmbilightEngine: com.renotube.tv.ambilight.CpuAmbilightEngine
) : ViewModel() {
    private var currentFormatInfo: MediaItemFormatInfo? = null
    var currentLowResUrl: String? = null
        private set

    val remoteActionFlow = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)

    fun syncWatchHistoryToYouTube(posSec: Float, lengthSec: Float) {
        val format = currentFormatInfo ?: return
        val videoId = format.videoId ?: _uiState.value.videoId
        if (videoId.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!format.eventId.isNullOrEmpty() && !format.ofParam.isNullOrEmpty()) {
                    TrackingService.instance().updateWatchTime(
                        videoId,
                        posSec,
                        lengthSec,
                        format.eventId,
                        format.visitorMonitoringData,
                        format.ofParam
                    )
                    Log.d(TAG, "YouTube watch history synced directly via TrackingService: $videoId at ${posSec}s / ${lengthSec}s")
                } else {
                    mediaRepository.updateHistory(videoId, posSec)
                    Log.d(TAG, "YouTube watch history synced via MediaRepository: $videoId at ${posSec}s")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync YouTube watch history: ${e.message}")
            }
        }
    }

    fun triggerAction(action: String) {
        remoteActionFlow.tryEmit(action)
    }

    fun playNextVideo() {
        val queued = playbackQueueManager.pollNext()
        if (queued != null && queued.videoId.isNotEmpty()) {
            loadVideo(
                videoId = queued.videoId,
                title = queued.title,
                author = queued.author,
                thumbnailUrl = queued.thumbnailUrl,
                clickTrackingParams = queued.clickTrackingParams
            )
            return
        }
        val currentMeta = _uiState.value.metadata
        val nextVideo = currentMeta?.nextVideo ?: currentMeta?.suggestions?.firstOrNull()
        if (nextVideo != null && nextVideo.videoId.isNotEmpty()) {
            loadVideo(
                videoId = nextVideo.videoId,
                title = nextVideo.title,
                author = nextVideo.author,
                thumbnailUrl = nextVideo.thumbnailUrl,
                clickTrackingParams = nextVideo.clickTrackingParams
            )
        }
    }

    fun queuePlayNext(video: com.renotube.tv.data.model.VideoCardModel) {
        playbackQueueManager.playNext(video)
        _uiState.update { current ->
            val meta = current.metadata
            if (meta != null) {
                val updatedSuggestions = (listOf(video) + meta.suggestions).distinctBy { it.videoId }
                current.copy(metadata = meta.copy(suggestions = updatedSuggestions))
            } else current
        }
    }

    fun addToPlaylist(video: com.renotube.tv.data.model.VideoCardModel, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val added = userHistoryRepository.addToPlaylist(video, "Watch Later")
            onResult(added)
        }
    }

    fun blockChannel(author: String) {
        viewModelScope.launch {
            userHistoryRepository.blockChannel(author)
            _uiState.update { current ->
                val meta = current.metadata
                if (meta != null) {
                    val filtered = meta.suggestions.filter { it.author != author }
                    current.copy(metadata = meta.copy(suggestions = filtered))
                } else current
            }
        }
    }

    fun hideVideo(videoId: String) {
        viewModelScope.launch {
            userHistoryRepository.hideVideo(videoId)
            _uiState.update { current ->
                val meta = current.metadata
                if (meta != null) {
                    val filtered = meta.suggestions.filter { it.videoId != videoId }
                    current.copy(metadata = meta.copy(suggestions = filtered))
                } else current
            }
        }
    }

    private val playerPrefs = context.getSharedPreferences("renotube_player_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _autoplayEnabled = MutableStateFlow(playerPrefs.getBoolean("autoplay_next", true))
    val autoplayEnabled: StateFlow<Boolean> = _autoplayEnabled.asStateFlow()

    fun setAutoplayEnabled(enabled: Boolean) {
        playerPrefs.edit().putBoolean("autoplay_next", enabled).apply()
        _autoplayEnabled.value = enabled
    }

    private val _sponsorBlockEnabled = MutableStateFlow(playerPrefs.getBoolean("sponsorblock_enabled", true))
    val sponsorBlockEnabled: StateFlow<Boolean> = _sponsorBlockEnabled.asStateFlow()

    fun toggleSponsorBlock(): Boolean {
        val newState = !_sponsorBlockEnabled.value
        playerPrefs.edit().putBoolean("sponsorblock_enabled", newState).apply()
        _sponsorBlockEnabled.value = newState
        return newState
    }

    private val _adBlockEnabled = MutableStateFlow(playerPrefs.getBoolean("adblock_enabled", true))
    val adBlockEnabled: StateFlow<Boolean> = _adBlockEnabled.asStateFlow()

    fun toggleAdBlock(): Boolean {
        val newState = !_adBlockEnabled.value
        playerPrefs.edit().putBoolean("adblock_enabled", newState).apply()
        _adBlockEnabled.value = newState
        return newState
    }

    val webRemoteUrl: String
        get() = "http://${com.renotube.tv.remote.WebRemoteServer.getLocalIpAddress(context)}:${com.renotube.tv.remote.DEFAULT_WEB_REMOTE_PORT}"

    val ambilightConfig: StateFlow<AmbilightConfig> = ambilightRepository.config

    fun toggleAmbilight(): Boolean {
        val current = ambilightConfig.value.enabled
        val newState = !current
        ambilightRepository.updateConfig(ambilightConfig.value.copy(enabled = newState))
        if (!newState) {
            stopCpuAmbilight()
            resetAmbilightToWarmWhite()
        } else if (_uiState.value.isPlaying) {
            ensureAmbilightAwake()
            startCpuAmbilightIfEnabled()
        }
        return newState
    }

    private val _playbackEndedExitFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val playbackEndedExitFlow = _playbackEndedExitFlow.asSharedFlow()

    private val isAmbilightProcessing = java.util.concurrent.atomic.AtomicBoolean(false)
    private val isTargetUpdating = java.util.concurrent.atomic.AtomicBoolean(false)

    fun isAmbilightBusy(): Boolean = isAmbilightProcessing.get()

    fun updateAmbilightTargetDirect(bitmap: Bitmap) {
        val cfg = ambilightConfig.value
        if (cfg.enabled) {
            ambilightProcessor.updateTargetFromBitmap(bitmap, cfg)
        }
    }

    fun updateAmbilightTarget(bitmap: Bitmap) {
        val cfg = ambilightConfig.value
        if (cfg.enabled && isTargetUpdating.compareAndSet(false, true)) {
            viewModelScope.launch(Dispatchers.Default) {
                try {
                    ambilightProcessor.updateTargetFromBitmap(bitmap, cfg)
                } finally {
                    isTargetUpdating.set(false)
                }
            }
        }
    }

    suspend fun tickAmbilightPhysics() {
        val cfg = ambilightConfig.value
        if (cfg.enabled) {
            ambilightProcessor.tickPhysicsAndSend(cfg)
        }
    }

    fun processAmbilightFrame(bitmap: Bitmap) {
        val cfg = ambilightConfig.value
        if (cfg.enabled && isAmbilightProcessing.compareAndSet(false, true)) {
            viewModelScope.launch(Dispatchers.Default) {
                try {
                    ambilightProcessor.processFrame(bitmap, cfg)
                } finally {
                    isAmbilightProcessing.set(false)
                }
            }
        }
    }

    fun processAmbilightFallback() {
        val cfg = ambilightConfig.value
        if (cfg.enabled && isAmbilightProcessing.compareAndSet(false, true)) {
            viewModelScope.launch(Dispatchers.Default) {
                try {
                    ambilightProcessor.processFrame(null, cfg)
                } finally {
                    isAmbilightProcessing.set(false)
                }
            }
        }
    }

    fun resetAmbilightToWarmWhite() {
        val cfg = ambilightConfig.value
        if (cfg.enabled) {
            viewModelScope.launch(Dispatchers.IO) {
                ambilightRepository.setWarmWhite()
            }
        }
    }

    fun ensureAmbilightAwake() {
        val cfg = ambilightConfig.value
        if (cfg.enabled) {
            viewModelScope.launch(Dispatchers.IO) {
                ambilightRepository.ensureWledOn()
            }
        }
    }

    /** Expose the raw ExoPlayer for AndroidView binding in PlayerScreen */
    val exoPlayer: ExoPlayer get() = renoTubePlayer.exoPlayer

    private val skippedSegmentKeys = mutableSetOf<String>()

    fun startCpuAmbilightIfEnabled() {
        val cfg = ambilightConfig.value
        val url = currentLowResUrl ?: mediaRepository.ambilightLowResUrl
        if (cfg.enabled && !url.isNullOrBlank()) {
            cpuAmbilightEngine.start(renoTubePlayer.exoPlayer, url)
        } else {
            cpuAmbilightEngine.stop()
        }
    }

    fun stopCpuAmbilight() {
        cpuAmbilightEngine.stop()
    }

    init {
        // Fast time-to-first-frame: notify UI the exact millisecond video rendering begins
        renoTubePlayer.onFirstFrameRenderedListener = {
            _uiState.update { it.copy(isFirstFrameRendered = true) }
        }

        // Smart Dynamic Surface & Resolution Controller: optimizes resolution when Ambilight is active
        viewModelScope.launch {
            ambilightRepository.config.collect { cfg ->
                renoTubePlayer.setAmbilightActive(cfg.enabled)
                if (!cfg.enabled) {
                    cpuAmbilightEngine.stop()
                } else if (_uiState.value.isPlaying) {
                    val url = currentLowResUrl ?: mediaRepository.ambilightLowResUrl
                    if (!url.isNullOrBlank()) {
                        cpuAmbilightEngine.start(renoTubePlayer.exoPlayer, url)
                    }
                }
            }
        }

        // Player error listener with automatic format failover
        renoTubePlayer.onErrorListener = { error ->
            Log.w(TAG, "Playback error caught in ViewModel: ${error.message}. Switching format...")
            val currentId = _uiState.value.videoId
            if (currentId.isNotEmpty()) {
                viewModelScope.launch {
                    try {
                        java.io.File(context.cacheDir, "manifest_${currentId}.mpd").delete()
                    } catch (_: Exception) {}
                    mediaRepository.switchNextFormat(force = false)
                    delay(300)
                    loadVideo(currentId, retryCount = 1)
                }
            }
        }

        // Autoplay listener on video ended (with Loop support & Autoplay OFF Exit to Main Screen)
        renoTubePlayer.onPlaybackEndedListener = {
            val durSec = renoTubePlayer.durationMs / 1000f
            syncWatchHistoryToYouTube(durSec, durSec)
            if (renoTubePlayer.isLoopEnabled()) {
                Log.i(TAG, "Loop Video enabled: Replaying current video")
                renoTubePlayer.seekTo(0)
                renoTubePlayer.play()
            } else if (_autoplayEnabled.value) {
                val queued = playbackQueueManager.pollNext()
                if (queued != null && queued.videoId.isNotEmpty()) {
                    Log.i(TAG, "Autoplay: Playing queued next video '${queued.title}' (${queued.videoId})")
                    loadVideo(
                        videoId = queued.videoId,
                        title = queued.title,
                        author = queued.author,
                        thumbnailUrl = queued.thumbnailUrl,
                        clickTrackingParams = queued.clickTrackingParams
                    )
                } else {
                    val currentMeta = _uiState.value.metadata
                    val nextVideo = currentMeta?.nextVideo ?: currentMeta?.suggestions?.firstOrNull()
                    if (nextVideo != null && nextVideo.videoId.isNotEmpty()) {
                        Log.i(TAG, "Autoplay: Playing next video '${nextVideo.title}' (${nextVideo.videoId})")
                        loadVideo(
                            videoId = nextVideo.videoId,
                            title = nextVideo.title,
                            author = nextVideo.author,
                            thumbnailUrl = nextVideo.thumbnailUrl,
                            clickTrackingParams = nextVideo.clickTrackingParams
                        )
                    }
                }
            } else {
                Log.i(TAG, "Autoplay disabled: Video playback completed -> Exiting player back to main screen")
                viewModelScope.launch {
                    _playbackEndedExitFlow.emit(Unit)
                }
            }
        }

        // Continuously poll position every 250ms: SponsorBlock always runs in background,
        // but Compose UI state is ONLY updated when OSD is visible (or every 2000ms when hidden)
        // to prevent UI recomposition stalls during 4K 60fps playback.
        var lastWatchHistorySyncSec = 0f
        var watchHistorySyncCountdown = 20 // Trigger first sync after ~5 seconds
        var lastUiUpdateTime = 0L
        viewModelScope.launch {
            while (isActive) {
                delay(250)
                val pos = renoTubePlayer.currentPositionMs
                val dur = renoTubePlayer.durationMs
                val playing = renoTubePlayer.isPlaying
                val playWhenReady = renoTubePlayer.exoPlayer.playWhenReady
                val now = System.currentTimeMillis()
                val isControlsVisible = _uiState.value.isOsdVisible
                val playStateChanged = playing != _uiState.value.isPlaying || playWhenReady != _uiState.value.isPlayWhenReady

                if (isControlsVisible || (now - lastUiUpdateTime >= 2000L) || playStateChanged) {
                    lastUiUpdateTime = now
                    _uiState.update {
                        it.copy(
                            currentPositionMs = pos,
                            durationMs = dur,
                            isPlaying = playing,
                            isPlayWhenReady = playWhenReady
                        )
                    }
                }

                // Periodic YouTube watch history & recommendation synchronization
                if (playing && pos > 2000L) {
                    val posSec = pos / 1000f
                    val durSec = dur / 1000f
                    if (watchHistorySyncCountdown > 0) {
                        watchHistorySyncCountdown--
                        if (watchHistorySyncCountdown == 0) {
                            syncWatchHistoryToYouTube(posSec, durSec)
                            lastWatchHistorySyncSec = posSec
                        }
                    } else if (kotlin.math.abs(posSec - lastWatchHistorySyncSec) >= 15f) {
                        syncWatchHistoryToYouTube(posSec, durSec)
                        lastWatchHistorySyncSec = posSec
                    }
                }

                // SponsorBlock auto-skip check (only when user has enabled SponsorBlock)
                if (_sponsorBlockEnabled.value) {
                    val segments = _uiState.value.sponsorSegments
                    for (segment in segments) {
                        val key = "${segment.startMs}_${segment.endMs}"
                        if (!skippedSegmentKeys.contains(key) && pos in segment.startMs..segment.endMs) {
                            skippedSegmentKeys.add(key)
                            val targetMs = segment.endMs + 250L
                            Log.i(TAG, "SponsorBlock auto-skipping segment [${segment.startMs}..${segment.endMs}] -> seeking to ${targetMs}ms")
                            renoTubePlayer.seekTo(targetMs)
                            _uiState.update { it.copy(lastSkippedSegment = segment, currentPositionMs = targetMs) }
                            viewModelScope.launch {
                                delay(2500)
                                _uiState.update { it.copy(lastSkippedSegment = null) }
                            }
                            break
                        }
                    }
                }
            }
        }
    }

    fun loadVideo(
        videoId: String,
        title: String = "",
        author: String = "",
        thumbnailUrl: String = "",
        clickTrackingParams: String? = null,
        retryCount: Int = 0
    ) {
        skippedSegmentKeys.clear()
        _uiState.update {
            it.copy(
                videoId = videoId,
                initialTitle = title,
                initialAuthor = author,
                initialThumbnail = thumbnailUrl,
                isFirstFrameRendered = false,
                isLoading = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                // Parallel asynchronous dispatch: start metadata & sponsor fetch concurrently
                val metadataDeferred = async(Dispatchers.IO) {
                    try { mediaRepository.getMetadata(videoId) } catch (e: Exception) { null }
                }
                val sponsorDeferred = async(Dispatchers.IO) {
                    try { mediaRepository.getSponsorSegments(videoId) } catch (e: Exception) { emptyList() }
                }

                // 1. Resolve stream formats through SmartTube's MediaItemService
                val formatInfo = mediaRepository.getFormatInfo(videoId, clickTrackingParams)
                currentFormatInfo = formatInfo
                val lowResUrl = mediaRepository.ambilightLowResUrl ?: mediaRepository.extractLowResVideoStreamUrl(formatInfo)
                currentLowResUrl = lowResUrl

                if (formatInfo == null || formatInfo.isUnplayable) {
                    if (retryCount < 2) {
                        Log.w(TAG, "Format unplayable for $videoId. Switching format (attempt $retryCount)...")
                        mediaRepository.switchNextFormat(force = true)
                        loadVideo(
                            videoId = videoId,
                            title = title,
                            author = author,
                            thumbnailUrl = thumbnailUrl,
                            clickTrackingParams = clickTrackingParams,
                            retryCount = retryCount + 1
                        )
                        return@launch
                    } else {
                        mediaRepository.resetClientToDefault()
                    }
                }

                // 2. Start playback IMMEDIATELY without waiting for metadata
                if (formatInfo != null) {
                    renoTubePlayer.playFormatInfo(formatInfo)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLiveStream = formatInfo.isLive
                        )
                    }
                    if (ambilightConfig.value.enabled && !lowResUrl.isNullOrBlank()) {
                        cpuAmbilightEngine.start(renoTubePlayer.exoPlayer, lowResUrl)
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "No playable stream found for $videoId") }
                }

                // 3. Await metadata and SponsorBlock concurrently
                val rawMetadata = metadataDeferred.await()
                val sponsorSegments = sponsorDeferred.await()

                // Ensure relevant Up Next suggestions based on current video: minimum 30 cards for 3 rows x 10 cards
                val baseMetadata = if (rawMetadata != null && rawMetadata.suggestions.size < 30) {
                    try {
                        val cloudRecs: List<com.renotube.tv.data.model.VideoCardModel> = if (contentRepository.isUserSignedIn) {
                            try {
                                kotlinx.coroutines.withTimeoutOrNull(2500) {
                                    contentRepository.getRecommendedFlow().firstOrNull()?.items
                                } ?: emptyList()
                            } catch (_: Exception) { emptyList() }
                        } else emptyList()

                        val query = if (rawMetadata.author.isNotBlank()) "${rawMetadata.author} ${rawMetadata.title}" else rawMetadata.title
                        val relatedRows = if ((rawMetadata.suggestions + cloudRecs).size < 30) {
                            try { contentRepository.search(query) } catch (_: Exception) { emptyList() }
                        } else emptyList()

                        val extraCards: List<com.renotube.tv.data.model.VideoCardModel> = (cloudRecs + relatedRows.flatMap { it.items }).filter { it.videoId != videoId }
                        val merged = (rawMetadata.suggestions + extraCards).distinctBy { it.videoId }

                        val row1 = merged.take(10)
                        val row2 = merged.drop(10).take(10)
                        val row3 = merged.drop(20).take(10)
                        val newSections = mutableListOf<com.renotube.tv.data.model.SectionRowModel>()
                        if (row1.isNotEmpty()) newSections.add(com.renotube.tv.data.model.SectionRowModel(title = "Up Next", items = row1))
                        if (row2.isNotEmpty()) newSections.add(com.renotube.tv.data.model.SectionRowModel(title = if (rawMetadata.author.isNotBlank()) "More from ${rawMetadata.author}" else "Related Videos", items = row2))
                        if (row3.isNotEmpty()) newSections.add(com.renotube.tv.data.model.SectionRowModel(title = "Recommended for You", items = row3))

                        rawMetadata.copy(suggestions = merged, suggestionSections = newSections)
                    } catch (e: Exception) {
                        rawMetadata
                    }
                } else rawMetadata

                val queued = playbackQueueManager.getQueue()
                val metadata = if (baseMetadata != null && queued.isNotEmpty()) {
                    val combined = (queued + baseMetadata.suggestions).distinctBy { it.videoId }
                    val row1 = combined.take(10)
                    val row2 = combined.drop(10).take(10)
                    val row3 = combined.drop(20).take(10)
                    val newSections = mutableListOf<com.renotube.tv.data.model.SectionRowModel>()
                    if (row1.isNotEmpty()) newSections.add(com.renotube.tv.data.model.SectionRowModel(title = "Up Next", items = row1))
                    if (row2.isNotEmpty()) newSections.add(com.renotube.tv.data.model.SectionRowModel(title = if (baseMetadata.author.isNotBlank()) "More from ${baseMetadata.author}" else "Related Videos", items = row2))
                    if (row3.isNotEmpty()) newSections.add(com.renotube.tv.data.model.SectionRowModel(title = "Recommended for You", items = row3))

                    baseMetadata.copy(suggestions = combined, suggestionSections = newSections)
                } else baseMetadata

                _uiState.update {
                    it.copy(
                        metadata = metadata,
                        sponsorSegments = sponsorSegments
                    )
                }

                // Record in viewing algorithm history for personalized Home Screen
                if (metadata != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        userHistoryRepository.recordView(
                            videoId = videoId,
                            title = metadata.title,
                            author = metadata.author,
                            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                            durationText = ""
                        )
                    }
                }

                // 4. Background prefetch the next video's format after current video buffers
                val nextCandidate = metadata?.nextVideo ?: metadata?.suggestions?.firstOrNull()
                if (nextCandidate != null && nextCandidate.videoId.isNotEmpty()) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            delay(15000)
                            mediaRepository.prefetchFormatInfo(nextCandidate.videoId)
                            Log.d(TAG, "Prefetched next video format: ${nextCandidate.videoId}")
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to prefetch next format: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load video: ${e.message}", e)
                if (retryCount < 2) {
                    mediaRepository.switchNextFormat(force = true)
                    loadVideo(
                        videoId = videoId,
                        title = title,
                        author = author,
                        thumbnailUrl = thumbnailUrl,
                        clickTrackingParams = clickTrackingParams,
                        retryCount = retryCount + 1
                    )
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.localizedMessage ?: "Failed to resolve video stream"
                        )
                    }
                }
            }
        }
    }

    fun prefetchVideo(videoId: String) {
        if (videoId.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mediaRepository.prefetchFormatInfo(videoId)
            } catch (e: Exception) {
                // best effort
            }
        }
    }

    fun stopPlayback() {
        val posSec = renoTubePlayer.currentPositionMs / 1000f
        val durSec = renoTubePlayer.durationMs / 1000f
        syncWatchHistoryToYouTube(posSec, durSec)
        Log.i(TAG, "Stopping video playback and resetting UI state")
        skippedSegmentKeys.clear()
        currentFormatInfo = null
        currentLowResUrl = null
        cpuAmbilightEngine.stop()
        renoTubePlayer.stop()
        _uiState.update {
            it.copy(
                videoId = "",
                isPlaying = false,
                isLoading = false,
                isOsdVisible = false
            )
        }
        resetAmbilightToWarmWhite()
    }

    fun togglePlayPause() {
        renoTubePlayer.togglePlayPause()
    }

    fun seekRelative(deltaMs: Long) {
        renoTubePlayer.seekRelative(deltaMs)
    }

    fun seekTo(positionMs: Long) {
        renoTubePlayer.seekTo(positionMs)
    }

    fun setOsdVisible(visible: Boolean) {
        _uiState.update {
            it.copy(
                isOsdVisible = visible,
                currentPositionMs = if (visible) renoTubePlayer.currentPositionMs else it.currentPositionMs,
                durationMs = if (visible) renoTubePlayer.durationMs else it.durationMs
            )
        }
    }

    fun toggleSubtitles(): Boolean {
        val enabled = renoTubePlayer.toggleSubtitles()
        _subtitlesEnabled.value = enabled
        return enabled
    }

    private val _subtitlesEnabled = MutableStateFlow(renoTubePlayer.isSubtitlesEnabled())
    val subtitlesEnabled: StateFlow<Boolean> = _subtitlesEnabled.asStateFlow()

    fun setMaxVideoResolution(height: Int) {
        renoTubePlayer.setMaxVideoResolution(height)
    }

    private val _voiceClarityEnabled = MutableStateFlow(renoTubePlayer.isVoiceBoostEnabled())
    val voiceClarityEnabled: StateFlow<Boolean> = _voiceClarityEnabled.asStateFlow()

    fun toggleVoiceClarity(): Boolean {
        val newState = !renoTubePlayer.isVoiceBoostEnabled()
        renoTubePlayer.setVoiceBoostEnabled(newState)
        _voiceClarityEnabled.value = newState
        return newState
    }

    private val _playbackSpeed = MutableStateFlow(renoTubePlayer.getPlaybackSpeed())
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    fun setPlaybackSpeed(speed: Float) {
        renoTubePlayer.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
    }

    private val _afrEnabled = MutableStateFlow(renoTubePlayer.isAfrEnabled())
    val afrEnabled: StateFlow<Boolean> = _afrEnabled.asStateFlow()

    fun toggleAfr(): Boolean {
        val newState = !renoTubePlayer.isAfrEnabled()
        renoTubePlayer.setAfrEnabled(newState)
        _afrEnabled.value = newState
        return newState
    }

    private val _loopEnabled = MutableStateFlow(renoTubePlayer.isLoopEnabled())
    val loopEnabled: StateFlow<Boolean> = _loopEnabled.asStateFlow()

    fun toggleLoop(): Boolean {
        val newState = !renoTubePlayer.isLoopEnabled()
        renoTubePlayer.setLoopEnabled(newState)
        _loopEnabled.value = newState
        return newState
    }

    private val _statsForNerdsEnabled = MutableStateFlow(false)
    val statsForNerdsEnabled: StateFlow<Boolean> = _statsForNerdsEnabled.asStateFlow()

    fun toggleStatsForNerds(): Boolean {
        val newState = !_statsForNerdsEnabled.value
        _statsForNerdsEnabled.value = newState
        return newState
    }

    fun getStatsData(): com.renotube.tv.player.StatsForNerdsData {
        return renoTubePlayer.getStatsForNerds(_uiState.value.videoId)
    }

    fun getAvailableAudioTracks(): List<com.renotube.tv.player.AudioTrackOption> {
        return renoTubePlayer.getAvailableAudioTracks()
    }

    fun selectAudioTrack(option: com.renotube.tv.player.AudioTrackOption) {
        renoTubePlayer.selectAudioTrack(option)
    }

    fun getPreferredAudioLanguage(): String {
        return renoTubePlayer.getPreferredAudioLanguage()
    }

    fun setPreferredAudioLanguage(lang: String) {
        renoTubePlayer.setPreferredAudioLanguage(lang)
    }

    fun resetAudioTrackToDefault() {
        renoTubePlayer.resetAudioTrackToDefault()
    }

    override fun onCleared() {
        super.onCleared()
        cpuAmbilightEngine.stop()
        // Do NOT release the player here — it's a singleton managed by Hilt
    }
}
