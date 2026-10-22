package com.renotube.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = YouTubeRed,
    onPrimary = YouTubeWhite,
    background = YouTubeBlack,
    onBackground = YouTubeWhite,
    surface = YouTubeSurface,
    onSurface = YouTubeWhite,
    surfaceVariant = YouTubeSurfaceVariant,
    onSurfaceVariant = YouTubeSecondaryText,
    outline = YouTubeDivider
)

@Composable
fun RenoTubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = TVTypography,
        content = content
    )
}
