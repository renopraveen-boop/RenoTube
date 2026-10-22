package com.renotube.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renotube.tv.ui.theme.YouTubeBlack
import com.renotube.tv.ui.theme.YouTubeFocusedBorder
import com.renotube.tv.ui.theme.YouTubeSurface
import com.renotube.tv.ui.theme.YouTubeWhite

data class FilterChipItem(
    val id: String,
    val label: String
)

val YOUTUBE_TV_FILTER_CHIPS = listOf(
    FilterChipItem("home", "All"),
    FilterChipItem("music", "Music"),
    FilterChipItem("gaming", "Gaming"),
    FilterChipItem("live", "Live"),
    FilterChipItem("trending", "Recently uploaded"),
    FilterChipItem("news", "News"),
    FilterChipItem("sports", "Sports"),
    FilterChipItem("podcasts", "Podcasts")
)

@Composable
fun FilterChipsBar(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSidebar: (() -> Unit)? = null,
    onDownPress: (() -> Unit)? = null,
    firstChipFocusRequester: FocusRequester? = null
) {
    val listState = rememberLazyListState()

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
    ) {
        itemsIndexed(
            items = YOUTUBE_TV_FILTER_CHIPS,
            key = { _, item -> item.id }
        ) { index, item ->
            val isSelected = selectedCategory == item.id || (selectedCategory.isBlank() && item.id == "home")
            var isFocused by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1.0f)

            var chipModifier = Modifier
                .scale(scale)
                .clip(RoundedCornerShape(18.dp))

            if (index == 0 && firstChipFocusRequester != null) {
                chipModifier = chipModifier.focusRequester(firstChipFocusRequester)
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = chipModifier
                    .onFocusChanged { isFocused = it.isFocused }
                    .border(
                        width = if (isFocused) 2.dp else 0.dp,
                        color = if (isFocused) YouTubeFocusedBorder else Color.Transparent,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .background(
                        color = when {
                            isSelected -> YouTubeWhite
                            isFocused -> YouTubeSurface.copy(alpha = 0.9f)
                            else -> Color(0xFF272727)
                        },
                        shape = RoundedCornerShape(18.dp)
                    )
                    .focusable(true)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onCategorySelected(item.id) }
                    )
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown) {
                            when (keyEvent.key) {
                                Key.DirectionLeft -> {
                                    if (index == 0 && onNavigateToSidebar != null) {
                                        onNavigateToSidebar()
                                        return@onKeyEvent true
                                    }
                                }
                                Key.DirectionDown -> {
                                    if (onDownPress != null) {
                                        onDownPress()
                                        return@onKeyEvent true
                                    }
                                }
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    onCategorySelected(item.id)
                                    return@onKeyEvent true
                                }
                            }
                        }
                        false
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = item.label,
                    fontSize = 13.5.sp,
                    fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        isSelected -> YouTubeBlack
                        isFocused -> YouTubeWhite
                        else -> YouTubeWhite.copy(alpha = 0.85f)
                    }
                )
            }
        }
    }
}
