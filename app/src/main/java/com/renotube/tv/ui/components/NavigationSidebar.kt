package com.renotube.tv.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
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
import com.renotube.tv.ui.theme.YouTubeBlack
import com.renotube.tv.ui.theme.YouTubeFocusedBorder
import com.renotube.tv.ui.theme.YouTubeRed
import com.renotube.tv.ui.theme.YouTubeSecondaryText
import com.renotube.tv.ui.theme.YouTubeSurface
import com.renotube.tv.ui.theme.YouTubeWhite

data class TVNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NavigationSidebar(
    selectedRoute: String,
    onRouteSelected: (String) -> Unit,
    isExpandedState: androidx.compose.runtime.MutableState<Boolean>? = null,
    onNavigateToContent: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    activeItemFocusRequester: FocusRequester = remember { FocusRequester() }
) {
    var internalFocused by remember { mutableStateOf(false) }
    val isSidebarExpanded = (isExpandedState?.value == true) || internalFocused

    // Official YouTube TV Navigation Hierarchy:
    // Account / Sign-In at top, then Search, Home, Subscriptions, You, then Settings pinned to bottom
    val mainNavItems = listOf(
        TVNavItem("search", "Search", Icons.Default.Search),
        TVNavItem("home", "Home", Icons.Default.Home),
        TVNavItem("subscriptions", "Subscriptions", Icons.Default.Subscriptions),
        TVNavItem("library", "You", Icons.Default.VideoLibrary)
    )

    val sidebarWidth = if (isSidebarExpanded) 240.dp else 72.dp

    val isUserSigned = try {
        com.liskovsoft.youtubeapi.service.YouTubeSignInService.instance().isSigned
    } catch (_: Exception) { false }

    val accountName = try {
        if (isUserSigned) {
            com.liskovsoft.youtubeapi.service.YouTubeSignInService.instance().selectedAccount?.name ?: "Account"
        } else "Sign in"
    } catch (_: Exception) { "Sign in" }

    LaunchedEffect(Unit) {
        try {
            activeItemFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(isSidebarExpanded) {
        if (isSidebarExpanded && !internalFocused) {
            try {
                activeItemFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(sidebarWidth)
            .animateContentSize()
            .focusGroup()
            .background(YouTubeBlack.copy(alpha = if (isSidebarExpanded) 0.98f else 0.88f))
            .onFocusChanged { focusState: FocusState ->
                internalFocused = focusState.hasFocus
                if (isExpandedState != null) {
                    isExpandedState.value = focusState.hasFocus
                }
            }
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Top Block: Account Profile + Main Rails (Search, Home, Subscriptions, You) ──
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // User Account / Sign-In Circle (Official YouTube TV Header)
                AccountSidebarRow(
                    isSigned = isUserSigned,
                    accountName = accountName,
                    isExpanded = isSidebarExpanded,
                    onNavigateToContent = onNavigateToContent,
                    onClick = { onRouteSelected("settings") }
                )

                Spacer(modifier = Modifier.height(6.dp))

                mainNavItems.forEachIndexed { index, item ->
                    val isSelected = selectedRoute == item.route
                    SidebarItemRow(
                        item = item,
                        isSelected = isSelected,
                        isExpanded = isSidebarExpanded,
                        focusRequester = if (isSelected) activeItemFocusRequester else null,
                        isFirst = false,
                        isLast = false,
                        onNavigateToContent = onNavigateToContent,
                        onClick = { onRouteSelected(item.route) }
                    )
                }
            }

            // ── Bottom Block: Settings (Incorporating Ambilight & Web Remote) ──
            Column {
                SidebarItemRow(
                    item = TVNavItem("settings", "Settings", Icons.Default.Settings),
                    isSelected = selectedRoute == "settings",
                    isExpanded = isSidebarExpanded,
                    focusRequester = if (selectedRoute == "settings") activeItemFocusRequester else null,
                    isFirst = false,
                    isLast = true,
                    onNavigateToContent = onNavigateToContent,
                    onClick = { onRouteSelected("settings") }
                )
            }
        }
    }
}

@Composable
private fun AccountSidebarRow(
    isSigned: Boolean,
    accountName: String,
    isExpanded: Boolean,
    onNavigateToContent: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(vertical = 2.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) YouTubeFocusedBorder else Color.Transparent,
                shape = RoundedCornerShape(24.dp)
            )
            .background(
                color = when {
                    isFocused -> YouTubeSurface
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(24.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> return@onKeyEvent true // Clamped at left edge
                        Key.DirectionRight -> {
                            onNavigateToContent?.invoke()
                            return@onKeyEvent true
                        }
                        Key.DirectionUp -> return@onKeyEvent true // Clamped at top
                    }
                }
                if (keyEvent.type == KeyEventType.KeyUp) {
                    val key = keyEvent.key
                    if (key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter) {
                        onClick()
                        return@onKeyEvent true
                    }
                }
                false
            }
            .padding(horizontal = 12.dp)
    ) {
        // User Profile Avatar Circle
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isSigned) YouTubeRed else Color(0xFF383838)),
            contentAlignment = Alignment.Center
        ) {
            if (isSigned) {
                Text(
                    text = accountName.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "U",
                    color = YouTubeWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "Sign in",
                    tint = YouTubeWhite,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = accountName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = YouTubeWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isSigned) "Switch account" else "Sign in to YouTube",
                    fontSize = 11.sp,
                    color = YouTubeSecondaryText,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SidebarItemRow(
    item: TVNavItem,
    isSelected: Boolean,
    isExpanded: Boolean,
    focusRequester: FocusRequester? = null,
    isFirst: Boolean = false,
    isLast: Boolean = false,
    onNavigateToContent: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    var rowModifier = Modifier
        .fillMaxWidth()
        .height(44.dp)
        .padding(vertical = 2.dp)

    if (focusRequester != null) {
        rowModifier = rowModifier.focusRequester(focusRequester)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = rowModifier
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) YouTubeFocusedBorder else Color.Transparent,
                shape = RoundedCornerShape(22.dp)
            )
            .background(
                color = when {
                    isFocused -> YouTubeSurface
                    isSelected -> YouTubeSurface.copy(alpha = 0.55f)
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> return@onKeyEvent true // Clamped at left edge: stay in sidebar
                        Key.DirectionRight -> {
                            // Official YouTube TV: DPAD_RIGHT from sidebar collapses and returns focus to content!
                            onNavigateToContent?.invoke()
                            return@onKeyEvent true
                        }
                        Key.DirectionUp -> if (isFirst) return@onKeyEvent true // Clamped at top of sidebar
                        Key.DirectionDown -> if (isLast) return@onKeyEvent true // Clamped at bottom of sidebar
                    }
                }
                if (keyEvent.type == KeyEventType.KeyUp) {
                    val key = keyEvent.key
                    if (key == Key.DirectionCenter ||
                        key == Key.Enter ||
                        key == Key.NumPadEnter
                    ) {
                        onClick()
                        return@onKeyEvent true
                    }
                }
                false
            }
            .padding(horizontal = 14.dp)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = when {
                isSelected -> YouTubeRed
                isFocused -> YouTubeWhite
                else -> YouTubeSecondaryText
            },
            modifier = Modifier.size(24.dp)
        )

        if (isExpanded) {
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = item.label,
                fontSize = 14.5.sp,
                fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
                color = if (isFocused || isSelected) YouTubeWhite else YouTubeSecondaryText,
                maxLines = 1
            )
        }
    }
}
