package com.renotube.tv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renotube.tv.data.model.SectionRowModel
import com.renotube.tv.data.model.VideoCardModel
import com.renotube.tv.data.repository.ContentRepository
import com.renotube.tv.data.repository.MediaRepository
import com.renotube.tv.player.PlaybackQueueManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

data class HomeUiState(
    val isLoading: Boolean = true,
    val featuredVideo: VideoCardModel? = null,
    val sections: List<SectionRowModel> = emptyList(),
    val errorMessage: String? = null,
    val currentCategory: String = "home"
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val mediaRepository: MediaRepository,
    private val userHistoryRepository: com.renotube.tv.data.repository.UserHistoryRepository,
    private val playbackQueueManager: PlaybackQueueManager
) : ViewModel() {

    val mediaRepositoryRef: MediaRepository get() = mediaRepository

    val homeListState = LazyListState()

    var activePreviewVideo by mutableStateOf<VideoCardModel?>(null)
        private set

    var lastFocusedVideoId by mutableStateOf<String?>(null)
        private set

    fun onCardFocused(video: VideoCardModel) {
        lastFocusedVideoId = video.videoId
        activePreviewVideo = video
        prefetchVideo(video.videoId)
    }

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var currentJob: kotlinx.coroutines.Job? = null
    private val categoryCache = ConcurrentHashMap<String, List<SectionRowModel>>()

    private var prefetchJob: kotlinx.coroutines.Job? = null

    /**
     * Ensures every video tile appears at most ONCE across all rows on screen.
     * Prevents duplicate video tiles between Home shelves (e.g. Jump Back In, Most Played, and Recommendations).
     */
    private fun deduplicateSections(sections: List<SectionRowModel>): List<SectionRowModel> {
        val seen = HashSet<String>()
        return sections.map { section ->
            val uniqueItems = section.items.filter { video ->
                if (video.videoId.isNotBlank()) seen.add(video.videoId) else true
            }
            section.copy(items = uniqueItems)
        }.filter { it.items.isNotEmpty() }
    }

    fun prefetchVideo(videoId: String) {
        if (videoId.isEmpty()) return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(300)
            try {
                mediaRepository.prefetchFormatInfo(videoId)
            } catch (t: Throwable) {
                // best effort prefetch
            }
        }
    }

    init {
        loadCategory("home")
    }

    fun loadHomeFeed() {
        loadCategory("home")
    }

    fun refreshPersonalizedFeed(force: Boolean = false) {
        if (force) {
            categoryCache.remove("home")
        }
        loadCategory("home")
    }

    fun loadCategory(category: String) {
        currentJob?.cancel()

        // Stale-While-Revalidate: If we already have cached sections for this category, display them INSTANTLY!
        val cached = categoryCache[category]
        if (!cached.isNullOrEmpty()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = null,
                    sections = cached,
                    featuredVideo = cached.firstOrNull()?.items?.firstOrNull(),
                    currentCategory = category
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    sections = emptyList(),
                    featuredVideo = null,
                    currentCategory = category
                )
            }
        }

        currentJob = viewModelScope.launch {
            // Personalized Viewing Algorithm: Prepend user history and favorite creator shelves on Home for guest sessions
            if (category == "home" && !contentRepository.isUserSignedIn) {
                val personalizedRows = mutableListOf<SectionRowModel>()
                val recent = userHistoryRepository.getRecentlyWatched(limit = 15)
                if (recent.isNotEmpty()) {
                    personalizedRows.add(SectionRowModel(title = "Jump Back In", items = recent))
                }

                val mostPlayed = userHistoryRepository.getMostPlayed(limit = 15)
                if (mostPlayed.isNotEmpty()) {
                    personalizedRows.add(SectionRowModel(title = "Most Played Songs & Videos", items = mostPlayed))
                }

                // Query recommendations for top watched channels/artists
                val topAuthors = userHistoryRepository.getTopAuthors(limit = 2)
                for (author in topAuthors) {
                    try {
                        val authorResults = contentRepository.search(author)
                        val authorItems = authorResults.flatMap { it.items }.distinctBy { it.videoId }.take(12)
                        if (authorItems.isNotEmpty()) {
                            personalizedRows.add(SectionRowModel(title = "More From $author", items = authorItems))
                        }
                    } catch (e: Exception) {
                        // ignore background search failure
                    }
                }

                if (personalizedRows.isNotEmpty()) {
                    _uiState.update { current ->
                        val merged = deduplicateSections((personalizedRows + current.sections).distinctBy { it.title })
                        current.copy(
                            isLoading = false,
                            featuredVideo = current.featuredVideo ?: merged.firstOrNull()?.items?.firstOrNull(),
                            sections = merged
                        )
                    }
                }
            }

            // Dedicated "Library / You" section: Watch Later, Watch History, Most Played, and Top Creators
            if (category == "library") {
                val libraryRows = mutableListOf<SectionRowModel>()
                if (contentRepository.isUserSignedIn) {
                    try {
                        contentRepository.getHistoryFlow().collect { historyRow ->
                            if (historyRow.items.isNotEmpty()) {
                                val deduped = deduplicateSections(listOf(historyRow.copy(title = "Watch History")))
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        featuredVideo = deduped.firstOrNull()?.items?.firstOrNull(),
                                        sections = deduped
                                    )
                                }
                                categoryCache[category] = deduped
                            }
                        }
                        return@launch
                    } catch (_: Exception) {}
                }
                val watchLater = userHistoryRepository.getPlaylist("Watch Later")
                if (watchLater.isNotEmpty()) {
                    libraryRows.add(SectionRowModel(title = "Watch Later", items = watchLater))
                }
                val recent = userHistoryRepository.getRecentlyWatched(limit = 25)
                if (recent.isNotEmpty()) {
                    libraryRows.add(SectionRowModel(title = "Watch History", items = recent))
                }
                val mostPlayed = userHistoryRepository.getMostPlayed(limit = 25)
                if (mostPlayed.isNotEmpty()) {
                    libraryRows.add(SectionRowModel(title = "Most Played Videos", items = mostPlayed))
                }
                val topAuthors = userHistoryRepository.getTopAuthors(limit = 4)
                for (author in topAuthors) {
                    try {
                        val authorResults = contentRepository.search(author)
                        val authorItems = authorResults.flatMap { it.items }.distinctBy { it.videoId }.take(12)
                        if (authorItems.isNotEmpty()) {
                            libraryRows.add(SectionRowModel(title = "Saved from $author", items = authorItems))
                        }
                    } catch (_: Exception) {}
                }

                val dedupedLibrary = deduplicateSections(libraryRows)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        featuredVideo = dedupedLibrary.firstOrNull()?.items?.firstOrNull(),
                        sections = dedupedLibrary
                    )
                }
                categoryCache[category] = dedupedLibrary
                return@launch
            }

            val flow = when (category) {
                "trending" -> contentRepository.getTrendingRowsFlow()
                "music" -> contentRepository.getMusicRowsFlow()
                "gaming" -> contentRepository.getGamingRowsFlow()
                "news" -> contentRepository.getNewsRowsFlow()
                "sports" -> contentRepository.getSportsRowsFlow()
                "live" -> contentRepository.getLiveRowsFlow()
                "subscriptions" -> contentRepository.getSubscriptionsRowsFlow()
                else -> contentRepository.getHomeRowsFlow()
            }

            flow
                .catch { exception ->
                    if (_uiState.value.sections.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = exception.localizedMessage ?: "Failed to load feed"
                            )
                        }
                    }
                }
                .collect { newRows ->
                    val filteredRows = newRows.map { section ->
                        section.copy(items = userHistoryRepository.filterBlocked(section.items))
                    }.filter { it.items.isNotEmpty() }

                    if (filteredRows.isNotEmpty()) {
                        _uiState.update { current ->
                            val combinedSections = deduplicateSections((current.sections + filteredRows).distinctBy { it.title })
                            val featured = current.featuredVideo
                                ?: combinedSections.firstOrNull()?.items?.firstOrNull()

                            if (featured != null) {
                                prefetchVideo(featured.videoId)
                            }

                            categoryCache[category] = combinedSections

                            current.copy(
                                isLoading = false,
                                featuredVideo = featured,
                                sections = combinedSections
                            )
                        }
                    } else if (_uiState.value.sections.isNotEmpty()) {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }

            _uiState.update { it.copy(isLoading = false) }
        }

        // Safety fallback timer: guarantee spinner dismisses within 3.5s
        viewModelScope.launch {
            kotlinx.coroutines.delay(3500)
            if (_uiState.value.isLoading) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun blockChannel(author: String) {
        viewModelScope.launch {
            userHistoryRepository.blockChannel(author)
            categoryCache.clear()
            _uiState.update { current ->
                val updatedSections = current.sections.map { section ->
                    section.copy(items = section.items.filter { it.author != author })
                }.filter { it.items.isNotEmpty() }
                current.copy(
                    sections = updatedSections,
                    featuredVideo = if (current.featuredVideo?.author == author)
                        updatedSections.firstOrNull()?.items?.firstOrNull()
                    else current.featuredVideo
                )
            }
        }
    }

    fun hideVideo(videoId: String) {
        viewModelScope.launch {
            userHistoryRepository.hideVideo(videoId)
            categoryCache.clear()
            _uiState.update { current ->
                val updatedSections = current.sections.map { section ->
                    section.copy(items = section.items.filter { it.videoId != videoId })
                }.filter { it.items.isNotEmpty() }
                current.copy(
                    sections = updatedSections,
                    featuredVideo = if (current.featuredVideo?.videoId == videoId)
                        updatedSections.firstOrNull()?.items?.firstOrNull()
                    else current.featuredVideo
                )
            }
        }
    }

    fun loadChannel(channelName: String, channelId: String? = null) {
        currentJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                sections = emptyList(),
                featuredVideo = null
            )
        }
        currentJob = viewModelScope.launch {
            if (!channelId.isNullOrBlank()) {
                try {
                    contentRepository.getChannelFlow(channelId).collect { rows ->
                        val filtered = rows.map { section ->
                            section.copy(items = userHistoryRepository.filterBlocked(section.items))
                        }.filter { it.items.isNotEmpty() }
                        val deduped = deduplicateSections(filtered)
                        if (deduped.isNotEmpty()) {
                            _uiState.update { current ->
                                current.copy(
                                    isLoading = false,
                                    sections = deduped,
                                    featuredVideo = deduped.firstOrNull()?.items?.firstOrNull()
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("HomeViewModel", "Channel flow error for $channelId: ${e.message}")
                }
            }

            if (_uiState.value.sections.isEmpty()) {
                try {
                    val searchRows = contentRepository.search(channelName)
                    val filtered = searchRows.map { section ->
                        section.copy(
                            title = if (section.title.isBlank() || section.title.equals("Search", ignoreCase = true)) channelName else section.title,
                            items = userHistoryRepository.filterBlocked(section.items)
                        )
                    }.filter { it.items.isNotEmpty() }

                    val deduped = deduplicateSections(filtered)
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            sections = deduped,
                            featuredVideo = deduped.firstOrNull()?.items?.firstOrNull()
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            errorMessage = "Unable to load channel for $channelName"
                        )
                    }
                }
            }
        }
    }

    fun queuePlayNext(video: VideoCardModel) {
        playbackQueueManager.playNext(video)
    }

    fun addToPlaylist(video: VideoCardModel, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val added = userHistoryRepository.addToPlaylist(video, "Watch Later")
            categoryCache.remove("library")
            if (_uiState.value.currentCategory == "library") {
                loadCategory("library")
            }
            onResult(added)
        }
    }
}
