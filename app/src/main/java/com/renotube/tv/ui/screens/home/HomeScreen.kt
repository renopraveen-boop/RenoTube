package com.renotube.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renotube.tv.data.model.VideoCardModel
import com.renotube.tv.ui.components.HeroBanner
import com.renotube.tv.ui.components.SectionRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import com.renotube.tv.ui.theme.YouTubeBlack
import com.renotube.tv.ui.theme.YouTubeRed
import com.renotube.tv.ui.theme.YouTubeSecondaryText
import com.renotube.tv.ui.theme.YouTubeWhite

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.renotube.tv.ui.theme.YouTubeFocusedBorder
import com.renotube.tv.ui.theme.YouTubeSurface
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onVideoSelected: (VideoCardModel) -> Unit,
    modifier: Modifier = Modifier,
    isPlayerActive: Boolean = false,
    onNavigateToSidebar: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var selectedVideoForAction by remember { mutableStateOf<VideoCardModel?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var currentFocusedRowIndex by remember { mutableStateOf(0) }
    val firstChipFocusRequester = remember { FocusRequester() }

    // Instant prefetch for first 3 cards in the top shelf to guarantee sub-500ms TTFB
    LaunchedEffect(uiState.sections) {
        val firstBatch = uiState.sections.firstOrNull()?.items?.take(3) ?: emptyList()
        firstBatch.forEach { video ->
            viewModel.prefetchVideo(video.videoId)
        }
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2500)
            toastMessage = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(YouTubeBlack)
    ) {
        if (uiState.isLoading && uiState.sections.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = YouTubeRed)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading YouTube for Android TV...",
                        color = YouTubeSecondaryText,
                        fontSize = 14.sp
                    )
                }
            }
        } else if (uiState.errorMessage != null && uiState.sections.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = uiState.errorMessage ?: "Error loading feed",
                        color = YouTubeSecondaryText,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.Button(
                        onClick = { viewModel.loadHomeFeed() },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                    ) {
                        Text(text = "Retry", color = YouTubeWhite)
                    }
                }
            }
        } else {
            val homeListState = viewModel.homeListState

            LaunchedEffect(currentFocusedRowIndex) {
                if (currentFocusedRowIndex == 0) {
                    homeListState.animateScrollToItem(0, 0)
                }
            }

            LazyColumn(
                state = homeListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // 1. Top Filter Chips Bar (Official YouTube TV category pills)
                item {
                    com.renotube.tv.ui.components.FilterChipsBar(
                        selectedCategory = uiState.currentCategory,
                        onCategorySelected = { categoryId ->
                            viewModel.loadCategory(categoryId)
                        },
                        onNavigateToSidebar = onNavigateToSidebar,
                        onDownPress = {
                            currentFocusedRowIndex = 0
                            coroutineScope.launch { homeListState.animateScrollToItem(1, 0) }
                        },
                        firstChipFocusRequester = firstChipFocusRequester
                    )
                }

                // 2. Top Hero Spotlight Item: Plays live preview video based on currently focused video
                item {
                    val previewVideo = viewModel.activePreviewVideo ?: uiState.featuredVideo
                    HeroBanner(
                        featuredVideo = previewVideo,
                        mediaRepository = viewModel.mediaRepositoryRef,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        isEnabled = !isPlayerActive,
                        onNavigateToSidebar = onNavigateToSidebar,
                        onFocused = {
                            currentFocusedRowIndex = 0
                            coroutineScope.launch { homeListState.animateScrollToItem(1, 0) }
                        },
                        onClick = {
                            previewVideo?.let { onVideoSelected(it) }
                        }
                    )
                }

                // Section Rows
                itemsIndexed(
                    items = uiState.sections,
                    key = { index, section -> "${section.title}_${section.type}_$index" },
                    contentType = { _, _ -> "section_row" }
                ) { rowIndex, section ->
                    SectionRow(
                        section = section,
                        onVideoClick = { video ->
                            viewModel.onCardFocused(video)
                            onVideoSelected(video)
                        },
                        onVideoLongClick = { selectedVideoForAction = it },
                        onVideoFocused = { video ->
                            currentFocusedRowIndex = rowIndex + 1
                            viewModel.onCardFocused(video)
                        },
                        onNavigateToSidebar = onNavigateToSidebar,
                        onUpPress = if (rowIndex == 0) {
                            {
                                currentFocusedRowIndex = 0
                                coroutineScope.launch { homeListState.animateScrollToItem(0, 0) }
                                false // Allow focus to transition naturally to HeroBanner above
                            }
                        } else null,
                        focusedVideoId = viewModel.lastFocusedVideoId
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
                    viewModel.loadChannel(author, chId)
                    toastMessage = "Browsing $author"
                },
                onPlayNext = { video ->
                    viewModel.queuePlayNext(video)
                    toastMessage = "Added to play next: ${video.title}"
                },
                onAddToPlaylist = { video ->
                    viewModel.addToPlaylist(video) { added ->
                        toastMessage = if (added) "Saved to Watch Later" else "Already in Watch Later"
                    }
                },
                onRemoveFromSuggestions = { videoId ->
                    viewModel.hideVideo(videoId)
                    toastMessage = "Removed from suggestions"
                },
                onBlockChannel = { author ->
                    viewModel.blockChannel(author)
                    toastMessage = "Won't recommend '$author'"
                },
                onDismiss = { selectedVideoForAction = null }
            )
        }

        // ── Feedback Toast ───────────────────────────────────────────
        if (toastMessage != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xDD202020))
                    .border(1.dp, YouTubeFocusedBorder, RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = toastMessage ?: "",
                    color = YouTubeWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
