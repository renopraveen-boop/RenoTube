package com.renotube.tv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renotube.tv.data.model.VideoCardModel
import com.renotube.tv.ui.theme.YouTubeBlack
import com.renotube.tv.ui.theme.YouTubeFocusedBorder
import com.renotube.tv.ui.theme.YouTubeSecondaryText
import com.renotube.tv.ui.theme.YouTubeSurface
import com.renotube.tv.ui.theme.YouTubeWhite
import kotlinx.coroutines.delay

@Composable
fun VideoActionModal(
    video: VideoCardModel,
    onViewChannel: (channelName: String, channelId: String?) -> Unit,
    onPlayNext: (video: VideoCardModel) -> Unit,
    onAddToPlaylist: (video: VideoCardModel) -> Unit,
    onRemoveFromSuggestions: (videoId: String) -> Unit,
    onBlockChannel: (author: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)

    val firstFocusRequester = remember { FocusRequester() }
    var isModalArmed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            firstFocusRequester.requestFocus()
        } catch (_: Exception) {}
        delay(350) // Settle time to guarantee initial hold release is finished before accepting input
        isModalArmed = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(YouTubeSurface)
                .border(1.dp, YouTubeFocusedBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {}) // Absorb taps inside modal card
                }
                .verticalScroll(scrollState)
                .padding(24.dp)
        ) {
            // Video Header info
            Text(
                text = video.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = YouTubeWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (video.author.isNotBlank()) {
                Text(
                    text = video.author,
                    fontSize = 13.sp,
                    color = YouTubeSecondaryText,
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(18.dp))
            }

            // 1. View Channel
            VideoActionModalOption(
                icon = Icons.Default.AccountCircle,
                text = if (video.author.isNotBlank()) "View channel (${video.author})" else "View channel",
                modifier = Modifier.focusRequester(firstFocusRequester),
                isArmed = isModalArmed,
                onClick = {
                    onViewChannel(video.author, video.channelId)
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Play Next
            VideoActionModalOption(
                icon = Icons.Default.QueueMusic,
                text = "Play next",
                isArmed = isModalArmed,
                onClick = {
                    onPlayNext(video)
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Add to Playlist
            VideoActionModalOption(
                icon = Icons.Default.PlaylistAdd,
                text = "Add to Watch Later",
                isArmed = isModalArmed,
                onClick = {
                    onAddToPlaylist(video)
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Remove from suggestions
            VideoActionModalOption(
                icon = Icons.Default.VisibilityOff,
                text = "Remove from suggestions",
                isArmed = isModalArmed,
                onClick = {
                    onRemoveFromSuggestions(video.videoId)
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 5. Block channel
            if (video.author.isNotBlank()) {
                VideoActionModalOption(
                    icon = Icons.Default.Block,
                    text = "Don't recommend channel (${video.author})",
                    isArmed = isModalArmed,
                    onClick = {
                        onBlockChannel(video.author)
                        onDismiss()
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            // 6. Cancel
            VideoActionModalOption(
                icon = Icons.Default.Close,
                text = "Cancel",
                isArmed = isModalArmed,
                onClick = onDismiss
            )
        }
    }
}

@Composable
private fun VideoActionModalOption(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    isArmed: Boolean = true,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    var isPressedDown by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { 
                isFocused = it.isFocused 
                if (!it.isFocused) {
                    isPressedDown = false
                }
            }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) YouTubeFocusedBorder else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .background(if (isFocused) YouTubeWhite else Color(0xFF282828))
            .focusable(true)
            .onKeyEvent { keyEvent ->
                val isConfirm = keyEvent.key == Key.DirectionCenter ||
                        keyEvent.key == Key.Enter ||
                        keyEvent.key == Key.NumPadEnter
                if (isConfirm) {
                    if (!isArmed) {
                        // Modal not yet armed: swallow any hold remnants completely
                        return@onKeyEvent true
                    }
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        // Must be a fresh KeyDown (repeatCount == 0) inside the armed modal!
                        if (keyEvent.nativeKeyEvent.repeatCount == 0) {
                            isPressedDown = true
                        }
                        return@onKeyEvent true
                    } else if (keyEvent.type == KeyEventType.KeyUp) {
                        if (isPressedDown) {
                            isPressedDown = false
                            onClick()
                            return@onKeyEvent true
                        } else {
                            // Trailing release from initial hold or unconfirmed press — safely consumed
                            return@onKeyEvent true
                        }
                    }
                }
                false
            }
            .pointerInput(isArmed) {
                detectTapGestures(onTap = { 
                    if (isArmed) onClick() 
                })
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isFocused) YouTubeBlack else YouTubeWhite,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            color = if (isFocused) YouTubeBlack else YouTubeWhite,
            fontSize = 14.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
