package com.renotube.tv.ui.screens.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import com.renotube.tv.ui.components.QrCodeView
import com.renotube.tv.crash.CrashHandler
import com.renotube.tv.perf.PerformanceTracker
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.renotube.tv.ambilight.AmbilightConfig
import com.renotube.tv.ui.theme.YouTubeBlack
import com.renotube.tv.ui.theme.YouTubeFocusedBorder
import com.renotube.tv.ui.theme.YouTubeRed
import com.renotube.tv.ui.theme.YouTubeSecondaryText
import com.renotube.tv.ui.theme.YouTubeSurface
import com.renotube.tv.ui.theme.YouTubeWhite

import androidx.compose.ui.ExperimentalComposeUiApi

private enum class SettingsCategory(val title: String, val icon: ImageVector) {
    ACCOUNTS("Accounts", Icons.Default.AccountCircle),
    AUTOPLAY("Autoplay", Icons.Default.Autorenew),
    WEB_REMOTE("Web Remote", Icons.Default.PhoneAndroid),
    AMBILIGHT("Ambilight (WLED)", Icons.Default.Lightbulb),
    VIDEO_AUDIO("Video & Audio", Icons.Default.HighQuality),
    SPONSORBLOCK("SponsorBlock & Ads", Icons.Default.Block),
    RESTRICTED_MODE("Restricted Mode", Icons.Default.Security),
    ABOUT("About & Device", Icons.Default.Info)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    onNavigateToSidebar: (() -> Unit)? = null
) {
    val config by viewModel.config.collectAsState()
    val testStatus by viewModel.testStatus.collectAsState()
    val detectStatus by viewModel.detectStatus.collectAsState()
    val autoplayEnabled by viewModel.autoplayEnabled.collectAsState()
    val isEdgeTestActive by viewModel.isEdgeTestActive.collectAsState()
    val isCornerTestActive by viewModel.isCornerTestActive.collectAsState()

    var selectedCategory by remember { mutableStateOf(SettingsCategory.ACCOUNTS) }
    var isMenuFocused by remember { mutableStateOf(true) }

    val menuWidth by animateDpAsState(
        targetValue = if (isMenuFocused) 230.dp else 68.dp,
        animationSpec = tween(durationMillis = 200),
        label = "settingsMenuWidth"
    )

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(YouTubeBlack)
            .padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 20.dp)
            .focusGroup()
            .focusProperties {
                down = FocusRequester.Cancel
            }
    ) {
        val activeCategoryRequester = remember { FocusRequester() }

        // ── LEFT PANE: Vertical Category Navigation (Collapsible LazyColumn) ──
        LazyColumn(
            modifier = Modifier
                .width(menuWidth)
                .fillMaxHeight()
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                if (isMenuFocused) {
                    Text(
                        text = "Settings",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = YouTubeWhite,
                        modifier = Modifier.padding(bottom = 14.dp, start = 8.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            itemsIndexed(SettingsCategory.values()) { _, category ->
                val isSelected = selectedCategory == category
                CategoryNavItem(
                    category = category,
                    isSelected = isSelected,
                    isMenuExpanded = isMenuFocused,
                    focusRequester = if (isSelected) activeCategoryRequester else null,
                    onNavigateToSidebar = onNavigateToSidebar,
                    onSelect = { selectedCategory = category },
                    onFocusChanged = { focused ->
                        if (focused) isMenuFocused = true
                    }
                )
            }
        }

        Spacer(modifier = Modifier.width(if (isMenuFocused) 24.dp else 32.dp))

        // ── RIGHT PANE: Category Details Layout (Expands when menu collapses) ─
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusGroup()
                .onFocusChanged {
                    if (it.hasFocus) {
                        isMenuFocused = false
                    }
                }
                .focusProperties {
                    left = activeCategoryRequester
                }
                .verticalScroll(scrollState)
                .padding(bottom = 64.dp)
        ) {
            when (selectedCategory) {
                SettingsCategory.ACCOUNTS -> AccountsSettings(viewModel)
                SettingsCategory.AUTOPLAY -> AutoplaySettings(
                    autoplayEnabled = autoplayEnabled,
                    onToggle = { viewModel.toggleAutoplay(it) }
                )
                SettingsCategory.WEB_REMOTE -> WebRemoteSettings(viewModel)
                SettingsCategory.AMBILIGHT -> AmbilightSettings(
                    config = config,
                    testStatus = testStatus,
                    detectStatus = detectStatus,
                    isEdgeTestActive = isEdgeTestActive,
                    isCornerTestActive = isCornerTestActive,
                    viewModel = viewModel
                )
                SettingsCategory.VIDEO_AUDIO -> VideoAudioSettings(viewModel)
                SettingsCategory.SPONSORBLOCK -> SponsorBlockSettings(viewModel)
                SettingsCategory.RESTRICTED_MODE -> RestrictedModeSettings()
                SettingsCategory.ABOUT -> AboutDeviceSettings()
            }
        }
    }
}

@Composable
private fun CategoryNavItem(
    category: SettingsCategory,
    isSelected: Boolean,
    isMenuExpanded: Boolean,
    focusRequester: FocusRequester? = null,
    onNavigateToSidebar: (() -> Unit)? = null,
    onSelect: () -> Unit,
    onFocusChanged: (Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    var rowModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp)
        .clip(RoundedCornerShape(24.dp))

    if (focusRequester != null) {
        rowModifier = rowModifier.focusRequester(focusRequester)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isMenuExpanded) Arrangement.Start else Arrangement.Center,
        modifier = rowModifier
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChanged(it.isFocused)
                if (it.isFocused) {
                    onSelect()
                }
            }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) YouTubeFocusedBorder else Color.Transparent,
                shape = RoundedCornerShape(24.dp)
            )
            .background(
                when {
                    isFocused -> YouTubeWhite
                    isSelected -> YouTubeSurface
                    else -> Color.Transparent
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect
            )
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            if (onNavigateToSidebar != null) {
                                onNavigateToSidebar()
                                true
                            } else false
                        }
                        Key.DirectionDown -> category == SettingsCategory.ABOUT
                        Key.DirectionUp -> category == SettingsCategory.ACCOUNTS
                        else -> false
                    }
                } else false
            }
            .padding(
                horizontal = if (isMenuExpanded) 16.dp else 10.dp,
                vertical = 12.dp
            )
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = category.title,
            tint = when {
                isFocused -> YouTubeBlack
                isSelected -> YouTubeRed
                else -> YouTubeSecondaryText
            },
            modifier = Modifier.size(20.dp)
        )
        if (isMenuExpanded) {
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = category.title,
                fontSize = 15.sp,
                fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    isFocused -> YouTubeBlack
                    isSelected -> YouTubeWhite
                    else -> YouTubeSecondaryText
                },
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AutoplaySettings(
    autoplayEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    SettingsDetailHeader(
        title = "Autoplay",
        subtitle = "Choose whether recommended videos start automatically after current playback ends"
    )

    SettingsDetailCard {
        SettingsToggleRow(
            title = "Autoplay next video",
            subtitle = "When finished watching a video, another video will start playing automatically",
            checked = autoplayEnabled,
            onCheckedChange = onToggle
        )
    }
}

@Composable
private fun AmbilightSettings(
    config: AmbilightConfig,
    testStatus: TestConnectionState,
    detectStatus: WledDetectState,
    isEdgeTestActive: Boolean,
    isCornerTestActive: Boolean,
    viewModel: SettingsViewModel
) {
    var editingIp by remember { mutableStateOf(false) }
    var ipInput by remember(config.wledIp) { mutableStateOf(config.wledIp) }

    SettingsDetailHeader(
        title = "Ambilight & HyperLED Setup",
        subtitle = "Real-time edge color sync to WLED ESP32 with HyperHDR screen geometry"
    )

    // Master Toggle
    SettingsDetailCard {
        SettingsToggleRow(
            title = "Enable Ambilight Sync",
            subtitle = "Streams real-time edge colors over UDP to your ESP32 running WLED",
            checked = config.enabled,
            onCheckedChange = { viewModel.toggleAmbilight(it) }
        )
    }

    if (config.enabled) {
        Spacer(modifier = Modifier.height(20.dp))

        // ── 1. WLED CONNECTION & AUTO-DISCOVERY ──
        Text(
            text = "1. WLED APP & ESP32 CONNECTION",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFB300),
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        SettingsDetailCard {
            // IP Input & Auto-Detect
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "WLED Device IP Address", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = YouTubeWhite)
                    Text(text = "Target: ${config.wledIp} (${config.wledDeviceName})", fontSize = 13.sp, color = YouTubeSecondaryText)
                }

                if (editingIp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = ipInput,
                            onValueChange = { ipInput = it },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = YouTubeBlack,
                                unfocusedContainerColor = YouTubeBlack,
                                focusedTextColor = YouTubeWhite,
                                unfocusedTextColor = YouTubeWhite
                            ),
                            modifier = Modifier.width(160.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TvActionButton(text = "Save", onClick = {
                            viewModel.setWledIp(ipInput)
                            editingIp = false
                        })
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TvActionButton(text = config.wledIp, onClick = { editingIp = true })
                        TvActionButton(
                            text = "Auto-Detect WLED",
                            icon = Icons.Default.Sync,
                            onClick = { viewModel.detectWledDevice() }
                        )
                    }
                }
            }

            // Auto-detect status display
            when (detectStatus) {
                is WledDetectState.Detecting -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Color(0xFFFFB300), modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connecting to WLED HTTP API...", color = Color(0xFFFFB300), fontSize = 13.sp)
                    }
                }
                is WledDetectState.Discovered -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    val info = detectStatus.info
                    Text(
                        text = "✓ Connected to '${info.name}' (${info.totalLeds} LEDs, WLED v${info.version}, ${info.fps} FPS)",
                        color = Color(0xFF4CAF50),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                is WledDetectState.Failed -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = "⚠️ ${detectStatus.message}", color = YouTubeRed, fontSize = 13.sp)
                }
                else -> Unit
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Protocol Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Realtime Protocol", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = YouTubeWhite)
                    Text(text = if (config.wledPort == 4048) "DDP (Port 4048 - Recommended for WLED & HyperHDR)" else "DRGB (Port 21324)", fontSize = 13.sp, color = YouTubeSecondaryText)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvSelectablePill(label = "DDP (4048)", isSelected = config.wledPort == 4048, onClick = { viewModel.updateConfig(config.copy(wledPort = 4048)) })
                    TvSelectablePill(label = "DRGB (21324)", isSelected = config.wledPort == 21324, onClick = { viewModel.updateConfig(config.copy(wledPort = 21324)) })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Test Connection
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TvActionButton(
                    text = "Test Connection (Flash LEDs)",
                    icon = Icons.Default.FlashOn,
                    onClick = { viewModel.testWledConnection() }
                )

                when (testStatus) {
                    is TestConnectionState.Testing -> {
                        CircularProgressIndicator(color = YouTubeRed, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    is TestConnectionState.Success -> {
                        Text("Packet sent to ${config.wledIp}!", color = Color(0xFF4CAF50), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    is TestConnectionState.Error -> {
                        Text((testStatus as TestConnectionState.Error).message, color = YouTubeRed, fontSize = 13.sp)
                    }
                    else -> Unit
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Auto-Disable on 3-Min Disconnect Setting
            SettingsToggleRow(
                title = "Auto-Turn Off If Disconnected (3 Mins)",
                subtitle = "Automatically disables WLED option if the lightstrip is unavailable or unreachable on the network for 3 minutes",
                checked = config.autoTurnOffOnDisconnect,
                onCheckedChange = {
                    viewModel.updateConfig(config.copy(autoTurnOffOnDisconnect = it))
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── 2. SCREEN LAYOUT SETUP (HYPERHDR / HYPERION GEOMETRY) ──
        Text(
            text = "2. SCREEN LAYOUT & LED GEOMETRY",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF42A5F5),
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        SettingsDetailCard {
            Text(
                text = "LED Counts per Edge (Total: ${config.totalLeds} LEDs)",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = YouTubeWhite
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LedCountStepper(
                    label = "Top Edge",
                    count = config.topLeds,
                    onCountChange = { viewModel.setLedCounts(it, config.rightLeds, config.bottomLeds, config.leftLeds) },
                    modifier = Modifier.weight(1f)
                )
                LedCountStepper(
                    label = "Right Edge",
                    count = config.rightLeds,
                    onCountChange = { viewModel.setLedCounts(config.topLeds, it, config.bottomLeds, config.leftLeds) },
                    modifier = Modifier.weight(1f)
                )
                LedCountStepper(
                    label = "Bottom Edge",
                    count = config.bottomLeds,
                    onCountChange = { viewModel.setLedCounts(config.topLeds, config.rightLeds, it, config.leftLeds) },
                    modifier = Modifier.weight(1f)
                )
                LedCountStepper(
                    label = "Left Edge",
                    count = config.leftLeds,
                    onCountChange = { viewModel.setLedCounts(config.topLeds, config.rightLeds, config.bottomLeds, it) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4-Corner & 4-Edge Calibration Action Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TvActionButton(
                    text = if (isCornerTestActive) "Stop Corner Test" else "4-Corner Alignment Test (2 LEDs per bend)",
                    icon = Icons.Default.FlashOn,
                    onClick = {
                        if (isCornerTestActive) {
                            viewModel.stopCornerCalibration()
                        } else {
                            viewModel.testCornerCalibration()
                        }
                    }
                )
                TvActionButton(
                    text = if (isEdgeTestActive) "Stop Edge Test" else "4-Edge Color Test (Full Edges)",
                    icon = Icons.Default.Lightbulb,
                    onClick = {
                        if (isEdgeTestActive) {
                            viewModel.stopEdgeCalibration()
                        } else {
                            viewModel.testEdgeCalibration()
                        }
                    }
                )
            }

            if (isCornerTestActive) {
                Spacer(modifier = Modifier.height(14.dp))
                CornerCalibrationVisualCard(
                    config = config,
                    onAdjustEdge = { edge, delta -> viewModel.adjustLedCount(edge, delta) },
                    onClose = { viewModel.stopCornerCalibration() }
                )
            } else if (isEdgeTestActive) {
                Spacer(modifier = Modifier.height(14.dp))
                EdgeCalibrationVisualCard(
                    config = config,
                    onAdjustEdge = { edge, delta -> viewModel.adjustLedCount(edge, delta) },
                    onClose = { viewModel.stopEdgeCalibration() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // LED Direction
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Strip Direction", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = YouTubeWhite)
                    Text(text = "Direction the LED strip travels around the screen", fontSize = 13.sp, color = YouTubeSecondaryText)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvSelectablePill(label = "Clockwise", isSelected = config.direction == "CLOCKWISE", onClick = { viewModel.setLayoutGeometry(config.startCorner, "CLOCKWISE", config.bottomGapLeds) })
                    TvSelectablePill(label = "Counter-CW", isSelected = config.direction == "COUNTER_CLOCKWISE", onClick = { viewModel.setLayoutGeometry(config.startCorner, "COUNTER_CLOCKWISE", config.bottomGapLeds) })
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Start Corner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Starting Corner", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = YouTubeWhite)
                    Text(text = "Where the first LED connects to the ESP32", fontSize = 13.sp, color = YouTubeSecondaryText)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("BOTTOM_LEFT" to "Bot-Left", "BOTTOM_RIGHT" to "Bot-Right", "TOP_LEFT" to "Top-Left", "TOP_RIGHT" to "Top-Right").forEach { (corner, lbl) ->
                        TvSelectablePill(
                            label = lbl,
                            isSelected = config.startCorner == corner,
                            onClick = { viewModel.setLayoutGeometry(corner, config.direction, config.bottomGapLeds) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── 3. COLOR CALIBRATION & IMAGE PROCESSING ──
        Text(
            text = "3. COLOR CALIBRATION & PROCESSING",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFAB47BC),
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))

        SettingsDetailCard {
            // Brightness
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Master Brightness", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = YouTubeWhite)
                    Text(text = "Current: ${(config.brightness * 100).toInt()}%", fontSize = 13.sp, color = YouTubeSecondaryText)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.40f to "40%", 0.60f to "60%", 0.85f to "85%", 1.0f to "100%").forEach { (level, lbl) ->
                        TvSelectablePill(label = lbl, isSelected = config.brightness == level, onClick = { viewModel.setBrightness(level) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Saturation Boost (HyperHDR Feature)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Color Saturation Boost", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = YouTubeWhite)
                    Text(text = "Enhances vibrant hues on LED strips", fontSize = 13.sp, color = YouTubeSecondaryText)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1.0f to "1.0x (Natural)", 1.25f to "1.25x (Vivid)", 1.50f to "1.5x (Vibrant Pop)").forEach { (gain, lbl) ->
                        TvSelectablePill(label = lbl, isSelected = config.saturationGain == gain, onClick = { viewModel.setSaturationGain(gain) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Color Temperature
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Color Temperature Bias", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = YouTubeWhite)
                    Text(text = "White balance tuning", fontSize = 13.sp, color = YouTubeSecondaryText)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("NEUTRAL" to "Neutral 6500K", "WARM" to "Warm Cinema", "COOL" to "Cool Display").forEach { (temp, lbl) ->
                        TvSelectablePill(label = lbl, isSelected = config.colorTemperature == temp, onClick = { viewModel.setColorTemperature(temp) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Smoothing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Temporal Smoothing Filter", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = YouTubeWhite)
                    Text(text = "Asymmetric Attack & Decay transition filter", fontSize = 13.sp, color = YouTubeSecondaryText)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.60f to "Fast (Gaming)", 0.35f to "Balanced", 0.15f to "Cinematic").forEach { (factor, lbl) ->
                        TvSelectablePill(label = lbl, isSelected = config.smoothFactor == factor, onClick = { viewModel.setSmoothFactor(factor) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsToggleRow(
                title = "Ambient Warmth Bias Glow",
                subtitle = "Maintains gentle bias lighting during dark scenes so LEDs never cut abruptly black",
                checked = config.keepAmbientGlow,
                onCheckedChange = { viewModel.toggleAmbientGlow(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsToggleRow(
                title = "Cinema Letterbox Detection",
                subtitle = "Samples past 21:9 black bars so top/bottom LEDs illuminate properly on wide movies",
                checked = config.letterboxProtection,
                onCheckedChange = { viewModel.toggleLetterboxProtection(it) }
            )
        }
    }
}

@Composable
private fun WebRemoteSettings(viewModel: SettingsViewModel) {
    SettingsDetailHeader(
        title = "Mobile & Web Remote Control",
        subtitle = "Control RenoTube TV and type using your phone's keyboard"
    )

    SettingsDetailCard {
        Text(
            text = "HOW TO CONNECT",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50),
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 24.dp)) {
                Text(
                    text = "1. Connect your phone to the same Wi-Fi network as this TV.",
                    fontSize = 14.sp,
                    color = YouTubeWhite
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "2. Scan the QR code with your phone camera or visit:",
                    fontSize = 14.sp,
                    color = YouTubeSecondaryText
                )
                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF222222))
                        .border(1.5.dp, Color(0xFF4CAF50), RoundedCornerShape(10.dp))
                        .padding(vertical = 12.dp, horizontal = 16.dp)
                ) {
                    Text(
                        text = viewModel.webRemoteUrl,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50),
                        letterSpacing = 0.5.sp
                    )
                }
            }

            QrCodeView(
                content = viewModel.webRemoteUrl,
                size = 170.dp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        SettingsDetailInfoRow("Remote Server Status", "Active (Port 8080)")
        Spacer(modifier = Modifier.height(10.dp))
        SettingsDetailInfoRow("Local TV IP", viewModel.tvIpAddress)
        Spacer(modifier = Modifier.height(10.dp))
        SettingsDetailInfoRow("Capabilities", "D-Pad, Playback, Voice/Direct Keyboard Search")
    }
}

@Composable
private fun VideoAudioSettings(viewModel: SettingsViewModel) {
    val voiceBoost by viewModel.voiceBoostEnabled.collectAsState()
    val afrEnabled by viewModel.afrEnabled.collectAsState()
    val blockedCount by viewModel.blockedChannelCount.collectAsState()

    SettingsDetailHeader(
        title = "Video & Audio Quality",
        subtitle = "Audio DSP & Match Frame Rate calibrated for Sony BRAVIA 2 4K Ultra HD"
    )

    SettingsDetailCard {
        // Voice Clarity / Dialogue Boost Mode
        SettingsToggleRow(
            title = "Voice Clarity (Dialogue Boost)",
            subtitle = "Enhances vocal compression (+6dB vocal band) so speech is crisp over heavy background music",
            checked = voiceBoost,
            onCheckedChange = { viewModel.toggleVoiceBoost(it) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Match Frame Rate (AFR)
        SettingsToggleRow(
            title = "Match Frame Rate (AFR)",
            subtitle = "Switches display output to match 24p/23.976p film frame rates to prevent judder on Sony Cinema modes",
            checked = afrEnabled,
            onCheckedChange = { viewModel.toggleAfr(it) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Default Audio Language
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Default Audio Language", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = YouTubeWhite)
                Text(text = "Always prefer English audio track by default for multi-language videos", fontSize = 13.sp, color = YouTubeSecondaryText)
            }
            TvSelectablePill(
                label = "English (Default)",
                isSelected = true,
                onClick = {}
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Blocked Channels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Blocked Channels & Content", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = YouTubeWhite)
                Text(text = if (blockedCount > 0) "$blockedCount channel(s) blocked via long-press" else "No channels blocked", fontSize = 13.sp, color = YouTubeSecondaryText)
            }
            if (blockedCount > 0) {
                TvSelectablePill(
                    label = "Clear Blocked",
                    isSelected = false,
                    onClick = { viewModel.clearBlockedChannels() }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Viewing History Memory
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Viewing History Memory", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = YouTubeWhite)
                Text(text = "Reset recommendation algorithm & cached top 100 watch history", fontSize = 13.sp, color = YouTubeSecondaryText)
            }
            TvSelectablePill(
                label = "Clear History",
                isSelected = false,
                onClick = { viewModel.clearWatchHistory() }
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    SettingsDetailCard {
        SettingsDetailInfoRow("Maximum Resolution", "4K Ultra HD (3840 × 2160 @ 60fps)")
        Spacer(modifier = Modifier.height(10.dp))
        SettingsDetailInfoRow("Video Surface Engine", "Smart Dynamic (SurfaceView 4K / TextureView Ambilight)")
        Spacer(modifier = Modifier.height(10.dp))
        SettingsDetailInfoRow("Hardware Video Decoders", "VP9 Profile 2 (HDR10/HLG), HEVC, AVC")
        Spacer(modifier = Modifier.height(10.dp))
        SettingsDetailInfoRow("Audio Stream Mode", "STREAM_MUSIC (Calibrated Unity Gain)")
        Spacer(modifier = Modifier.height(10.dp))
        SettingsDetailInfoRow("Audio Output Codecs", "Dolby Digital Plus (E-AC-3) & Opus")
    }
}

@Composable
private fun SponsorBlockSettings(viewModel: SettingsViewModel) {
    val sponsorBlockEnabled by viewModel.sponsorBlockEnabled.collectAsState()
    val adBlockEnabled by viewModel.adBlockEnabled.collectAsState()

    SettingsDetailHeader(
        title = "SponsorBlock & Ad-Free",
        subtitle = "Community-driven ad and sponsorship skipping engine"
    )

    SettingsDetailCard {
        SettingsToggleRow(
            title = "Enable SponsorBlock Auto-Skip",
            subtitle = "Automatically skips sponsor segments, subscribe reminders, and intro animations",
            checked = sponsorBlockEnabled,
            onCheckedChange = { viewModel.toggleSponsorBlock(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsToggleRow(
            title = "Enable Ad-Free Streaming (AdBlock)",
            subtitle = "Bypasses video pre-roll, mid-roll, and overlay ads via clean InnerTube DASH streams",
            checked = adBlockEnabled,
            onCheckedChange = { viewModel.toggleAdBlock(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsDetailInfoRow("Ad-Blocking Engine", if (adBlockEnabled) "InnerTube Clean DASH MPD (Active)" else "Disabled")
        Spacer(modifier = Modifier.height(10.dp))
        SettingsDetailInfoRow("Sponsor Segments", if (sponsorBlockEnabled) "Auto-Skip Active" else "Disabled")
        Spacer(modifier = Modifier.height(10.dp))
        SettingsDetailInfoRow("Intermission & End Credits", if (sponsorBlockEnabled) "Auto-Skip Active" else "Disabled")
        Spacer(modifier = Modifier.height(10.dp))
        SettingsDetailInfoRow("Self-Promotion / Sub Reminders", if (sponsorBlockEnabled) "Auto-Skip Active" else "Disabled")
    }
}

@Composable
private fun RestrictedModeSettings() {
    SettingsDetailHeader(
        title = "Restricted Mode",
        subtitle = "Restricted Mode helps hide potentially mature videos"
    )

    SettingsDetailCard {
        SettingsToggleRow(
            title = "Restricted Mode",
            subtitle = "No filter is 100% accurate, but this helps you avoid mature content",
            checked = false,
            onCheckedChange = {}
        )
    }
}

@Composable
private fun AccountsSettings(viewModel: SettingsViewModel) {
    val accountState by viewModel.accountState.collectAsState()

    SettingsDetailHeader(
        title = "Accounts & Sign In",
        subtitle = "Sign in to synchronize your subscriptions, playlists, and recommendations"
    )

    SettingsDetailCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                tint = if (accountState.isSigned) YouTubeRed else YouTubeSecondaryText,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = accountState.accountName,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = YouTubeWhite
                )
                Text(
                    text = accountState.accountEmail,
                    fontSize = 13.sp,
                    color = YouTubeSecondaryText
                )
            }
            if (accountState.isSigned) {
                TvActionButton(
                    text = "Sign Out",
                    onClick = { viewModel.signOut() }
                )
            } else {
                TvActionButton(
                    text = if (accountState.isSigningIn) "Generating..." else "Sign In with QR / Code",
                    onClick = { viewModel.startSignIn() }
                )
            }
        }

        if (accountState.signInCode != null) {
            val activateUrl = "https://www.youtube.com/activate?user_code=${accountState.signInCode}"
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF222222))
                    .border(2.dp, YouTubeFocusedBorder, RoundedCornerShape(12.dp))
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 24.dp)) {
                        Text(
                            text = "1. Scan the QR code or visit on phone:",
                            fontSize = 14.sp,
                            color = YouTubeSecondaryText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "youtube.com/activate",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = YouTubeWhite
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "2. Enter this code on your phone:",
                            fontSize = 14.sp,
                            color = YouTubeSecondaryText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = accountState.signInCode ?: "",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = YouTubeRed,
                            letterSpacing = 4.sp
                        )
                    }

                    // QR Code for YouTube Activation
                    QrCodeView(
                        content = activateUrl,
                        size = 180.dp
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    SettingsDetailCard {
        SettingsDetailInfoRow("Cloud Synchronization", "Subscriptions, Playlists, Liked Videos")
        Spacer(modifier = Modifier.height(10.dp))
        SettingsDetailInfoRow("Authentication Protocol", "Google OAuth2 Device Flow (Leanback)")
        Spacer(modifier = Modifier.height(10.dp))
        SettingsDetailInfoRow("Credential Security", "Encrypted token storage in Android TV Keystore")
    }
}

@Composable
private fun AboutDeviceSettings() {
    val context = LocalContext.current
    val perfMetrics by PerformanceTracker.metrics.collectAsState()
    var crashLog by remember { mutableStateOf(CrashHandler.getLastCrashReport(context)) }

    SettingsDetailHeader(
        title = "About & Diagnostics",
        subtitle = "RenoTube TV Leanback Edition • Real-Time Diagnostics & Latency Monitor"
    )

    SettingsDetailCard {
        SettingsDetailInfoRow("App Version", "1.4.0-leanback")
        Spacer(modifier = Modifier.height(10.dp))
        SettingsDetailInfoRow("Target Device", "Sony BRAVIA 2 4K Ultra HD (K-55S25B)")
        Spacer(modifier = Modifier.height(10.dp))
        SettingsDetailInfoRow("Android TV OS", "Google TV 14 (ARM64)")
        Spacer(modifier = Modifier.height(10.dp))
        SettingsDetailInfoRow("Viewing Algorithm Memory", "Top 100 View History (<40KB compact footprint)")
    }

    Spacer(modifier = Modifier.height(16.dp))

    SettingsDetailCard {
        // Latency Checker HUD Toggle
        SettingsToggleRow(
            title = "Real-Time Latency Checker HUD",
            subtitle = "Displays API response time, stream resolve latency, video buffer, and RAM in the top-right corner",
            checked = perfMetrics.isHudVisible,
            onCheckedChange = { PerformanceTracker.toggleHud() }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Crash Log Status & Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(
                    text = "Crash Log Diagnostics",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = YouTubeWhite
                )
                Text(
                    text = if (crashLog != null) "Crash log found on device (saved to crash_log.txt)" else "No crashes recorded. System operating normally.",
                    fontSize = 13.sp,
                    color = if (crashLog != null) YouTubeRed else YouTubeSecondaryText
                )
            }
            if (crashLog != null) {
                TvSelectablePill(
                    label = "Clear Crash Log",
                    isSelected = false,
                    onClick = {
                        CrashHandler.clearCrashLog(context)
                        crashLog = null
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsDetailHeader(title: String, subtitle: String) {
    Text(text = title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = YouTubeWhite)
    Spacer(modifier = Modifier.height(4.dp))
    Text(text = subtitle, fontSize = 13.sp, color = YouTubeSecondaryText)
    Spacer(modifier = Modifier.height(18.dp))
}

@Composable
private fun SettingsDetailCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(YouTubeSurface)
            .padding(20.dp)
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) YouTubeFocusedBorder else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .background(if (isFocused) YouTubeWhite.copy(alpha = 0.08f) else Color.Transparent)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = YouTubeWhite)
            Text(text = subtitle, fontSize = 13.sp, color = YouTubeSecondaryText)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = YouTubeWhite,
                checkedTrackColor = YouTubeRed,
                uncheckedThumbColor = YouTubeSecondaryText,
                uncheckedTrackColor = YouTubeSurface
            )
        )
    }
}

@Composable
private fun SettingsDetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 14.sp, color = YouTubeSecondaryText)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = YouTubeWhite)
    }
}

@Composable
private fun LedCountStepper(
    label: String,
    count: Int,
    onCountChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF262626))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, fontSize = 12.sp, color = YouTubeSecondaryText, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = "$count", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = YouTubeWhite)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TvSmallButton(text = "-", onClick = { onCountChange((count - 2).coerceAtLeast(1)) })
            TvSmallButton(text = "+", onClick = { onCountChange(count + 2) })
        }
    }
}

@Composable
private fun TvSmallButton(text: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(CircleShape)
            .border(if (isFocused) 1.5.dp else 0.dp, if (isFocused) YouTubeFocusedBorder else Color.Transparent, CircleShape)
            .background(if (isFocused) YouTubeRed else Color(0xFF444444))
            .clickable(onClick = onClick)
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = YouTubeWhite)
    }
}

@Composable
private fun TvActionButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) YouTubeFocusedBorder else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .background(if (isFocused) YouTubeWhite else Color(0xFF333333))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isFocused) YouTubeBlack else YouTubeRed,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isFocused) YouTubeBlack else YouTubeWhite
        )
    }
}

@Composable
private fun TvSelectablePill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) YouTubeFocusedBorder else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .background(
                if (isSelected) YouTubeRed else if (isFocused) Color(0xFF444444) else Color(0xFF2C2C2C)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected || isFocused) YouTubeWhite else YouTubeSecondaryText
        )
    }
}

@Composable
private fun CornerCalibrationVisualCard(
    config: AmbilightConfig,
    onAdjustEdge: (edge: String, delta: Int) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C1C))
            .border(2.dp, Color(0xFFFFB300), RoundedCornerShape(12.dp))
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🎯 4-Corner Bend Alignment Active",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFB300)
                )
                Text(
                    text = "Lighting exactly 2 LEDs on each side of every bend (4 LEDs per corner). Adjust counts until corner lights sit right on the physical TV bends.",
                    fontSize = 13.sp,
                    color = YouTubeSecondaryText
                )
            }
            TvActionButton(
                text = "Close Corner Test",
                icon = Icons.Default.Check,
                onClick = onClose
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Screen frame representing the TV with 4 colored corners
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0F0F0F))
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(10.dp))
        ) {
            // TOP-LEFT CORNER: GREEN (2 on left, 2 on top)
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(8.dp)
                    .align(Alignment.TopStart)
                    .background(Color(0xFF00FF00))
            )
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(42.dp)
                    .align(Alignment.TopStart)
                    .background(Color(0xFF00FF00))
            )

            // TOP-RIGHT CORNER: BLUE (2 on top, 2 on right)
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(8.dp)
                    .align(Alignment.TopEnd)
                    .background(Color(0xFF0078FF))
            )
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(42.dp)
                    .align(Alignment.TopEnd)
                    .background(Color(0xFF0078FF))
            )

            // BOTTOM-RIGHT CORNER: YELLOW (2 on right, 2 on bottom)
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(42.dp)
                    .align(Alignment.BottomEnd)
                    .background(Color(0xFFFFE600))
            )
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(8.dp)
                    .align(Alignment.BottomEnd)
                    .background(Color(0xFFFFE600))
            )

            // BOTTOM-LEFT CORNER: RED (2 on bottom, 2 on left)
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(8.dp)
                    .align(Alignment.BottomStart)
                    .background(Color(0xFFFF0000))
            )
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(42.dp)
                    .align(Alignment.BottomStart)
                    .background(Color(0xFFFF0000))
            )

            // Center details & instructions
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🟢 TOP-LEFT (Green)  •  🔵 TOP-RIGHT (Blue)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = YouTubeWhite
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Top: ${config.topLeds} | Bottom: ${config.bottomLeds}",
                        fontSize = 12.sp,
                        color = YouTubeSecondaryText
                    )
                    Text(
                        text = "Total: ${config.totalLeds} LEDs",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB300)
                    )
                    Text(
                        text = "Left: ${config.leftLeds} | Right: ${config.rightLeds}",
                        fontSize = 12.sp,
                        color = YouTubeSecondaryText
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "🔴 BOTTOM-LEFT (Red)  •  🟡 BOTTOM-RIGHT (Yellow)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = YouTubeWhite
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Tuning Steppers right inside test
        Text(
            text = "Tap [+] or [-] on any edge to shift corner alignment live on your TV backlights:",
            fontSize = 13.sp,
            color = YouTubeSecondaryText
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickEdgeTuner("Left (Red/Grn)", config.leftLeds, Color(0xFFFF4444), onAdjust = { onAdjustEdge("left", it) }, modifier = Modifier.weight(1f))
            QuickEdgeTuner("Top (Grn/Blu)", config.topLeds, Color(0xFF00FF00), onAdjust = { onAdjustEdge("top", it) }, modifier = Modifier.weight(1f))
            QuickEdgeTuner("Right (Blu/Yel)", config.rightLeds, Color(0xFF42A5F5), onAdjust = { onAdjustEdge("right", it) }, modifier = Modifier.weight(1f))
            QuickEdgeTuner("Bottom (Yel/Red)", config.bottomLeds, Color(0xFFFFE600), onAdjust = { onAdjustEdge("bottom", it) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun EdgeCalibrationVisualCard(
    config: AmbilightConfig,
    onAdjustEdge: (edge: String, delta: Int) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C1C1C))
            .border(2.dp, Color(0xFF42A5F5), RoundedCornerShape(12.dp))
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🎯 4-Edge Calibration Active",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF42A5F5)
                )
                Text(
                    text = "Distinct colors sent to LED strip. Check where colors meet at the 4 corners of your TV.",
                    fontSize = 13.sp,
                    color = YouTubeSecondaryText
                )
            }
            TvActionButton(
                text = "Close Test",
                icon = Icons.Default.Check,
                onClick = onClose
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Screen frame representing the TV with 4 colored borders
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF0F0F0F))
        ) {
            // Left border overlay (Pure Red)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(10.dp)
                    .align(Alignment.CenterStart)
                    .background(Color(0xFFFF0000))
            )
            // Top border overlay (Pure Green)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .align(Alignment.TopCenter)
                    .background(Color(0xFF00FF00))
            )
            // Right border overlay (Pure Blue)
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(10.dp)
                    .align(Alignment.CenterEnd)
                    .background(Color(0xFF0064FF))
            )
            // Bottom border overlay (Pure Yellow)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color(0xFFFFE600))
            )

            // Center details & instructions
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🟢 TOP: ${config.topLeds} LEDs (Green)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00FF00)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔴 LEFT:\n${config.leftLeds} LEDs (Red)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF4444)
                    )
                    Text(
                        text = "Total: ${config.totalLeds} LEDs",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = YouTubeWhite
                    )
                    Text(
                        text = "🔵 RIGHT:\n${config.rightLeds} LEDs (Blue)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF42A5F5)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "🟡 BOTTOM: ${config.bottomLeds} LEDs (Yellow)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFE600)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Tuning Steppers right inside test
        Text(
            text = "Fine-tune edge counts (each tap updates the physical LEDs immediately):",
            fontSize = 13.sp,
            color = YouTubeSecondaryText
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            QuickEdgeTuner("Left (Red)", config.leftLeds, Color(0xFFFF4444), onAdjust = { onAdjustEdge("left", it) }, modifier = Modifier.weight(1f))
            QuickEdgeTuner("Top (Green)", config.topLeds, Color(0xFF00FF00), onAdjust = { onAdjustEdge("top", it) }, modifier = Modifier.weight(1f))
            QuickEdgeTuner("Right (Blue)", config.rightLeds, Color(0xFF42A5F5), onAdjust = { onAdjustEdge("right", it) }, modifier = Modifier.weight(1f))
            QuickEdgeTuner("Bottom (Yellow)", config.bottomLeds, Color(0xFFFFE600), onAdjust = { onAdjustEdge("bottom", it) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickEdgeTuner(
    name: String,
    count: Int,
    accentColor: Color,
    onAdjust: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF262626))
            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = name, fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(text = "$count", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = YouTubeWhite)
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TvSmallButton(text = "-", onClick = { onAdjust(-1) })
            TvSmallButton(text = "+", onClick = { onAdjust(+1) })
        }
    }
}
