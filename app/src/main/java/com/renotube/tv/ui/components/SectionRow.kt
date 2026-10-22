package com.renotube.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renotube.tv.data.model.SectionRowModel
import com.renotube.tv.data.model.VideoCardModel
import com.renotube.tv.ui.theme.YouTubeWhite

@Composable
fun SectionRow(
    section: SectionRowModel,
    onVideoClick: (VideoCardModel) -> Unit,
    modifier: Modifier = Modifier,
    onVideoLongClick: ((VideoCardModel) -> Unit)? = null,
    onVideoFocused: ((VideoCardModel) -> Unit)? = null,
    focusedVideoId: String? = null,
    onNavigateToSidebar: (() -> Unit)? = null,
    onUpPress: (() -> Boolean)? = null,
    onDownPress: (() -> Boolean)? = null,
    onLoadMore: () -> Unit = {}
) {
    if (section.items.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        // Row Section Header
        Text(
            text = section.title,
            color = YouTubeWhite,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, bottom = 6.dp)
        )

        // Horizontal Carousel
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = section.items,
                key = { index, item -> "${section.title}_${item.videoId}_$index" },
                contentType = { _, _ -> "video_card" }
            ) { index, video ->
                VideoCard(
                    video = video,
                    onClick = { onVideoClick(video) },
                    onLongClick = { onVideoLongClick?.invoke(video) },
                    onFocused = { onVideoFocused?.invoke(video) },
                    isLastFocused = (focusedVideoId != null && video.videoId == focusedVideoId),
                    isFirstInRow = (index == 0),
                    isLastInRow = (index == section.items.lastIndex),
                    onLeftBoundaryPress = {
                        if (index == 0 && onNavigateToSidebar != null) {
                            onNavigateToSidebar()
                            true
                        } else false
                    },
                    onRightBoundaryPress = {
                        if (index == section.items.lastIndex) {
                            true // Clamp at end of row: stay on this card
                        } else false
                    },
                    onUpBoundaryPress = onUpPress,
                    onDownBoundaryPress = onDownPress
                )

                // Trigger pagination when reaching last 3 items
                if (index >= section.items.size - 3) {
                    onLoadMore()
                }
            }
        }
    }
}
