package com.renotube.tv.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.renotube.tv.data.model.SectionRowModel
import com.renotube.tv.data.repository.ContentRepository
import com.renotube.tv.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val searchResults: List<SectionRowModel> = emptyList(),
    val isSearching: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val mediaRepository: MediaRepository,
    private val userHistoryRepository: com.renotube.tv.data.repository.UserHistoryRepository,
    private val playbackQueueManager: com.renotube.tv.player.PlaybackQueueManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var debounceJob: Job? = null

    init {
        loadPopularPredictions()
    }

    /**
     * Resets search query, results, and reloads trending suggestions.
     * Called whenever navigating away or returning to search from another screen.
     */
    fun resetSearch() {
        debounceJob?.cancel()
        _uiState.update {
            it.copy(
                query = "",
                searchResults = emptyList(),
                isSearching = false
            )
        }
        loadPopularPredictions()
    }

    private fun loadPopularPredictions() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tags = contentRepository.getSearchSuggestions("")
                val initialTags = if (tags.isNotEmpty()) {
                    tags
                } else {
                    listOf("Trending Movies", "Latest Songs", "New Trailers", "Live News", "Top Gaming", "Music Hits")
                }
                _uiState.update { it.copy(suggestions = initialTags) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(suggestions = listOf("Trending Movies", "Latest Songs", "New Trailers", "Live News"))
                }
            }
        }
    }

    private var prefetchJob: kotlinx.coroutines.Job? = null

    fun prefetchVideo(videoId: String) {
        if (videoId.isEmpty()) return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(300)
            try {
                mediaRepository.prefetchFormatInfo(videoId)
            } catch (t: Throwable) {
                // best effort
            }
        }
    }

    private fun deduplicateSections(sections: List<com.renotube.tv.data.model.SectionRowModel>): List<com.renotube.tv.data.model.SectionRowModel> {
        val seen = HashSet<String>()
        return sections.map { section ->
            val uniqueItems = section.items.filter { video ->
                if (video.videoId.isNotBlank()) seen.add(video.videoId) else true
            }
            section.copy(items = uniqueItems)
        }.filter { it.items.isNotEmpty() }
    }

    /**
     * Updates search predictions matching the original YouTube TV app:
     * - Immediate / 150ms debounce
     * - Updates predictions even with 1 character
     * - Reloads popular suggestions if cleared
     */
    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }

        debounceJob?.cancel()
        if (newQuery.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            loadPopularPredictions()
            return
        }

        debounceJob = viewModelScope.launch(Dispatchers.IO) {
            // Fast 150ms debounce for TV remote typing
            delay(150)
            val tags = contentRepository.getSearchSuggestions(newQuery)
            _uiState.update { it.copy(suggestions = tags) }
        }
    }

    fun executeSearch(searchQuery: String) {
        if (searchQuery.isBlank()) return
        _uiState.update { it.copy(query = searchQuery, isSearching = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val rawResults = contentRepository.search(searchQuery)
            val filteredCards = rawResults.flatMap { section ->
                userHistoryRepository.filterBlocked(section.items)
            }.filter { it.videoId.isNotBlank() }.distinctBy { it.videoId }.toMutableList()

            // If fewer than 50 results, fetch supplementary relevant results to aim for 50 top results
            if (filteredCards.size < 50) {
                try {
                    val fallbackSearch = contentRepository.search("$searchQuery full video")
                    val extra = fallbackSearch.flatMap { it.items }
                        .filter { it.videoId.isNotBlank() && filteredCards.none { existing -> existing.videoId == it.videoId } }
                    filteredCards.addAll(extra)
                } catch (_: Exception) {}
            }

            val top50 = filteredCards.take(50)
            val fiveRows = mutableListOf<SectionRowModel>()
            val r0 = top50.take(10)
            val r1 = top50.drop(10).take(10)
            val r2 = top50.drop(20).take(10)
            val r3 = top50.drop(30).take(10)
            val r4 = top50.drop(40).take(10)

            if (r0.isNotEmpty()) fiveRows.add(SectionRowModel(title = "Top Results", items = r0))
            if (r1.isNotEmpty()) fiveRows.add(SectionRowModel(title = "More Results", items = r1))
            if (r2.isNotEmpty()) fiveRows.add(SectionRowModel(title = "Related Videos", items = r2))
            if (r3.isNotEmpty()) fiveRows.add(SectionRowModel(title = "Popular for \"$searchQuery\"", items = r3))
            if (r4.isNotEmpty()) fiveRows.add(SectionRowModel(title = "Additional Matches", items = r4))

            _uiState.update {
                it.copy(
                    searchResults = fiveRows,
                    isSearching = false
                )
            }
            // Prewarm first result for instantaneous playback
            fiveRows.firstOrNull()?.items?.firstOrNull()?.videoId?.let { firstId ->
                prefetchVideo(firstId)
            }
        }
    }

    fun queuePlayNext(video: com.renotube.tv.data.model.VideoCardModel) {
        playbackQueueManager.playNext(video)
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
                val updatedResults = current.searchResults.map { section ->
                    section.copy(items = section.items.filter { it.author != author })
                }.filter { it.items.isNotEmpty() }
                current.copy(searchResults = updatedResults)
            }
        }
    }

    fun hideVideo(videoId: String) {
        viewModelScope.launch {
            userHistoryRepository.hideVideo(videoId)
            _uiState.update { current ->
                val updatedResults = current.searchResults.map { section ->
                    section.copy(items = section.items.filter { it.videoId != videoId })
                }.filter { it.items.isNotEmpty() }
                current.copy(searchResults = updatedResults)
            }
        }
    }
}
