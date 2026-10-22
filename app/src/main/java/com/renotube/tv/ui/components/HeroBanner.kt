package com.renotube.tv.ui.components

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.renotube.tv.data.model.VideoCardModel
import com.renotube.tv.data.repository.MediaRepository
import com.renotube.tv.ui.theme.YouTubeBadgeBackground
import com.renotube.tv.ui.theme.YouTubeBlack
import com.renotube.tv.ui.theme.YouTubeRed
import com.renotube.tv.ui.theme.YouTubeSecondaryText
import com.renotube.tv.ui.theme.YouTubeSurface
import com.renotube.tv.ui.theme.YouTubeWhite
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.media3.ui.AspectRatioFrameLayout
import com.renotube.tv.ui.theme.YouTubeFocusedBorder
import kotlinx.coroutines.delay

@Composable
fun HeroBanner(
    featuredVideo: VideoCardModel?,
    mediaRepository: MediaRepository,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    onNavigateToSidebar: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    if (featuredVideo == null) return

    val context = LocalContext.current
    var previewPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPreviewPlaying by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val player = ExoPlayer.Builder(context).build().apply {
            volume = 0f // Muted preview video
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = true
        }
        previewPlayer = player

        onDispose {
            try {
                player.stop()
                player.release()
            } catch (_: Exception) {}
            previewPlayer = null
            isPreviewPlaying = false
        }
    }

    // Debounced video stream preview loader when cursor moves across cards
    LaunchedEffect(featuredVideo.videoId, isEnabled) {
        if (!isEnabled) {
            isPreviewPlaying = false
            previewPlayer?.stop()
            previewPlayer?.clearMediaItems()
            return@LaunchedEffect
        }

        isPreviewPlaying = false
        previewPlayer?.stop()
        previewPlayer?.clearMediaItems()

        // 1200ms debounce: only load video preview if user rests on this card
        delay(1200)

        try {
            val formatInfo = mediaRepository.getFormatInfo(featuredVideo.videoId)
            val progressiveUrls = formatInfo?.createUrlList()
            val progressiveUrl = progressiveUrls?.firstOrNull { it.isNotEmpty() }
            val hlsUrl = if (formatInfo?.containsHlsUrl() == true) formatInfo.hlsManifestUrl else null
            val streamUrl = progressiveUrl ?: hlsUrl

            if (streamUrl != null && previewPlayer != null) {
                val mediaItem = MediaItem.fromUri(streamUrl)
                previewPlayer?.setMediaItem(mediaItem)
                previewPlayer?.prepare()
                previewPlayer?.play()
                isPreviewPlaying = true
            }
        } catch (_: Exception) {
            isPreviewPlaying = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) YouTubeFocusedBorder else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .onFocusChanged { 
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocused?.invoke()
                }
            }
            .focusable(true)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = onClick != null
            ) {
                onClick?.invoke()
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp -> true // Clamp at top: stay on HeroBanner!
                        Key.DirectionRight -> true // Clamp right: stay on HeroBanner!
                        Key.DirectionLeft -> {
                            if (onNavigateToSidebar != null) {
                                onNavigateToSidebar()
                                true
                            } else false
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onClick?.invoke()
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // 1. Live Preview Video Player (Muted)
        if (isPreviewPlaying && previewPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = previewPlayer
                        useController = false
                        isFocusable = false
                        isFocusableInTouchMode = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                },
                update = { view ->
                    view.player = previewPlayer
                    view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. High-res Backdrop Image (shown when preview is loading or not ready)
        AnimatedVisibility(
            visible = !isPreviewPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            val fallbackUrl = if (featuredVideo.videoId.isNotBlank()) "https://i.ytimg.com/vi/${featuredVideo.videoId}/hqdefault.jpg" else null
            val bannerUrl = featuredVideo.backdropUrl?.takeIf {
                !it.contains("yt3.ggpht.com") && !it.contains("yt4.ggpht.com") && !it.contains("/channel/") && !it.contains("/channels/")
            } ?: featuredVideo.thumbnailUrl.takeIf {
                !it.contains("yt3.ggpht.com") && !it.contains("yt4.ggpht.com") && !it.contains("/channel/") && !it.contains("/channels/")
            } ?: fallbackUrl

            val bannerTargetUrl = bannerUrl ?: fallbackUrl
            val heroImageRequest = remember(bannerTargetUrl) {
                ImageRequest.Builder(context)
                    .data(bannerTargetUrl)
                    .crossfade(true)
                    .build()
            }
            AsyncImage(
                model = heroImageRequest,
                contentDescription = featuredVideo.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. Shading Gradient for High-Contrast Text Legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            YouTubeBlack.copy(alpha = 0.95f),
                            YouTubeBlack.copy(alpha = 0.70f),
                            YouTubeBlack.copy(alpha = 0.25f),
                            Color.Transparent
                        ),
                        startX = 0f,
                        endX = 1400f
                    )
                )
        )

        // 4. Video Details (Dynamically follows cursor)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 32.dp, top = 24.dp, bottom = 24.dp, end = 260.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            val (cleanHeroAuthor, cleanHeroSecond) = remember(featuredVideo.author, featuredVideo.secondTitle) {
                VideoCardModel.cleanAuthorAndDetails(featuredVideo.author, featuredVideo.secondTitle)
            }

            Column {
                if (cleanHeroAuthor.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = cleanHeroAuthor.uppercase(),
                            color = YouTubeRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        if (isPreviewPlaying) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xDDCC0000), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "LIVE PREVIEW",
                                    color = YouTubeWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = featuredVideo.title,
                    color = YouTubeWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 28.sp
                )

                if (cleanHeroSecond.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = cleanHeroSecond,
                        color = YouTubeSecondaryText,
                        fontSize = 13.sp
                    )
                }
            }

            // Duration / Badge Info
            if (featuredVideo.durationText.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .background(YouTubeBadgeBackground, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Duration: ${featuredVideo.durationText}",
                        color = YouTubeWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
