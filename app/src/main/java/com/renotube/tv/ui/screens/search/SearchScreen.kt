package com.renotube.tv.ui.screens.search

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.renotube.tv.data.model.SectionRowModel
import com.renotube.tv.ui.components.VideoCard
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.renotube.tv.data.model.VideoCardModel
import com.renotube.tv.ui.components.SectionRow
import com.renotube.tv.ui.theme.YouTubeBlack
import com.renotube.tv.ui.theme.YouTubeFocusedBorder
import com.renotube.tv.ui.theme.YouTubeRed
import com.renotube.tv.ui.theme.YouTubeSecondaryText
import com.renotube.tv.ui.theme.YouTubeSurface
import com.renotube.tv.ui.theme.YouTubeWhite

private const val TAG = "SearchScreen"

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onVideoSelected: (VideoCardModel) -> Unit,
    modifier: Modifier = Modifier,
    onViewChannel: ((channelName: String, channelId: String?) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var recognizedPartial by remember { mutableStateOf("") }
    var spokenRms by remember { mutableStateOf(0f) }
    var isKeyboardMinimized by remember { mutableStateOf(false) }
    val keyboardFocusRequester = remember { FocusRequester() }
    val searchPaneWidth by animateDpAsState(targetValue = if (isKeyboardMinimized) 68.dp else 370.dp)

    var selectedVideoForAction by remember { mutableStateOf<VideoCardModel?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            kotlinx.coroutines.delay(2600)
            toastMessage = null
        }
    }

    // Auto-clear search query when navigating away from Search
    DisposableEffect(Unit) {
        onDispose {
            viewModel.resetSearch()
        }
    }

    // Speech Recognizer instance (in-app to prevent TV app from minimizing/closing)
    val speechRecognizer = remember {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                SpeechRecognizer.createSpeechRecognizer(context)
            } else null
        } catch (t: Throwable) {
            Log.w(TAG, "SpeechRecognizer creation failed: ${t.message}")
            null
        }
    }

    // Fallback external launcher in case SpeechRecognizer is unavailable
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                viewModel.onQueryChange(spoken)
                viewModel.executeSearch(spoken)
            }
        }
    }

    val startVoiceRecognition = {
        voiceError = null
        recognizedPartial = ""
        spokenRms = 0f
        if (speechRecognizer != null) {
            try {
                speechRecognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                    }
                    override fun onBeginningOfSpeech() {
                        isListening = true
                    }
                    override fun onRmsChanged(rmsdB: Float) {
                        spokenRms = rmsdB.coerceIn(0f, 10f)
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isListening = false
                    }
                    override fun onError(error: Int) {
                        isListening = false
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected. Try speaking closer to remote."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout. Tap mic to try again."
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                            SpeechRecognizer.ERROR_NETWORK -> "Network connection error."
                            else -> "Voice recognition notice ($error)"
                        }
                        voiceError = msg
                        Log.w(TAG, "SpeechRecognizer error: $msg")
                    }
                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()
                        if (!text.isNullOrBlank()) {
                            viewModel.onQueryChange(text)
                            viewModel.executeSearch(text)
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.firstOrNull()?.let { recognizedPartial = it }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                speechRecognizer.startListening(intent)
                isListening = true
            } catch (e: Exception) {
                Log.e(TAG, "In-app SpeechRecognizer failed, using fallback: ${e.message}")
                launchVoiceIntent(context, speechLauncher, onListening = { isListening = true }, onError = { voiceError = it })
            }
        } else {
            launchVoiceIntent(context, speechLauncher, onListening = { isListening = true }, onError = { voiceError = it })
        }
    }

    // Audio recording permission requester
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVoiceRecognition()
        } else {
            voiceError = "Microphone permission required for voice search"
        }
    }

    val requestVoiceSearch: () -> Unit = {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            startVoiceRecognition()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(speechRecognizer) {
        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(YouTubeBlack)
                .padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 20.dp)
        ) {
        // ── LEFT PANE: Search Bar & QWERTY Keyboard (Auto-collapses to icon strip) ──────────────
        Column(
            modifier = Modifier
                .width(searchPaneWidth)
                .fillMaxHeight()
        ) {
            if (isKeyboardMinimized) {
                // Minimized Icon Strip (like Settings)
                SearchMinimizedIconsStrip(
                    onExpand = {
                        isKeyboardMinimized = false
                        try { keyboardFocusRequester.requestFocus() } catch (_: Exception) {}
                    },
                    onVoiceClick = requestVoiceSearch,
                    onClearClick = {
                        viewModel.onQueryChange("")
                        isKeyboardMinimized = false
                        try { keyboardFocusRequester.requestFocus() } catch (_: Exception) {}
                    }
                )
            } else {
                // Search Input Field Header
                SearchInputHeader(
                    query = uiState.query,
                    onClear = { viewModel.onQueryChange("") },
                    onMicClick = requestVoiceSearch
                )

                Spacer(modifier = Modifier.height(14.dp))

                // The QWERTY Keyboard
                OriginalYouTubeTvKeyboard(
                    firstKeyRequester = keyboardFocusRequester,
                    onChar = { char ->
                        val updated = uiState.query + char
                        viewModel.onQueryChange(updated)
                    },
                    onSpace = {
                        val updated = uiState.query + " "
                        viewModel.onQueryChange(updated)
                    },
                    onBackspace = {
                        if (uiState.query.isNotEmpty()) {
                            val updated = uiState.query.dropLast(1)
                            viewModel.onQueryChange(updated)
                        }
                    },
                    onClear = {
                        viewModel.onQueryChange("")
                    },
                    onSearch = {
                        if (uiState.query.isNotEmpty()) {
                            viewModel.executeSearch(uiState.query)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.width(if (isKeyboardMinimized) 20.dp else 28.dp))

        // ── RIGHT PANE: Voice Listening / Predictions / Search Results ────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            if (isListening) {
                // In-App Voice Search Listening Animation (Pulsing Microphone HUD)
                VoiceListeningCard(
                    partialText = recognizedPartial,
                    rmsValue = spokenRms,
                    onCancel = {
                        isListening = false
                        try { speechRecognizer?.stopListening() } catch (_: Exception) {}
                    }
                )
            } else if (uiState.isSearching) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = YouTubeRed)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Searching YouTube...", color = YouTubeSecondaryText, fontSize = 14.sp)
                    }
                }
            } else if (uiState.searchResults.isNotEmpty()) {
                // Search Results Shelf (5 rows x 10 cards = 50 top results)
                val totalRows = uiState.searchResults.size
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    itemsIndexed(
                        items = uiState.searchResults,
                        key = { index, section -> "search_${section.title}_$index" }
                    ) { rIndex, section ->
                        SearchSectionRow(
                            section = section,
                            rIndex = rIndex,
                            totalRows = totalRows,
                            onVideoClick = onVideoSelected,
                            onVideoLongClick = { selectedVideoForAction = it },
                            onVideoFocused = { cIndex, video ->
                                viewModel.prefetchVideo(video.videoId)
                                if (cIndex > 0) {
                                    isKeyboardMinimized = true
                                }
                            },
                            onLeftFromFirstCard = {
                                isKeyboardMinimized = false
                                try { keyboardFocusRequester.requestFocus() } catch (_: Exception) {}
                            }
                        )
                    }
                }
            } else if (uiState.suggestions.isNotEmpty()) {
                // Search Predictions List (Matching YouTube Leanback TV)
                Text(
                    text = if (uiState.query.isEmpty()) "Trending Searches" else "Search Predictions",
                    color = YouTubeSecondaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 10.dp, start = 8.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = uiState.suggestions,
                        key = { index, suggestion -> "suggestion_${suggestion}_$index" }
                    ) { _, suggestion ->
                        SearchPredictionItem(
                            prediction = suggestion,
                            onClick = {
                                viewModel.onQueryChange(suggestion)
                                viewModel.executeSearch(suggestion)
                            }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = YouTubeSecondaryText.copy(alpha = 0.4f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = voiceError ?: "Type with remote or use voice search to find videos, songs, channels",
                            color = if (voiceError != null) YouTubeRed else YouTubeSecondaryText,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    // ── Long-Press Context Menu Modal ────────────────────────────
    if (selectedVideoForAction != null) {
            val targetVideo = selectedVideoForAction!!
            com.renotube.tv.ui.components.VideoActionModal(
                video = targetVideo,
                onViewChannel = { author, chId ->
                    onViewChannel?.invoke(author, chId)
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
                    toastMessage = "Removed from search results"
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

private fun launchVoiceIntent(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
    onListening: () -> Unit,
    onError: (String) -> Unit
) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak into remote...")
    }
    try {
        if (intent.resolveActivity(context.packageManager) != null) {
            onListening()
            launcher.launch(intent)
        } else {
            onError("Voice recognition service not found on this device")
        }
    } catch (e: Exception) {
        Log.e(TAG, "Voice intent error: ${e.message}")
        onError("Voice recognition notice: ${e.message}")
    }
}

@Composable
private fun SearchInputHeader(
    query: String,
    onClear: () -> Unit,
    onMicClick: () -> Unit
) {
    var isInputFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Query Box
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(YouTubeSurface)
                .border(
                    width = if (isInputFocused) 2.dp else 0.dp,
                    color = if (isInputFocused) YouTubeFocusedBorder else Color.Transparent,
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 14.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = YouTubeSecondaryText,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = query.ifEmpty { "Search YouTube" },
                color = if (query.isEmpty()) YouTubeSecondaryText else YouTubeWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear",
                    tint = YouTubeSecondaryText,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onClear() }
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // YouTube Leanback Microphone TV Button
        var isMicFocused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .size(44.dp)
                .onFocusChanged { isMicFocused = it.isFocused }
                .clip(CircleShape)
                .border(
                    width = if (isMicFocused) 2.dp else 0.dp,
                    color = if (isMicFocused) YouTubeFocusedBorder else Color.Transparent,
                    shape = CircleShape
                )
                .background(if (isMicFocused) YouTubeWhite else YouTubeSurface)
                .clickable { onMicClick() }
                .onKeyEvent { keyEvent ->
                    if ((keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) &&
                        keyEvent.type == KeyEventType.KeyUp
                    ) {
                        onMicClick()
                        true
                    } else false
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Voice Search",
                tint = if (isMicFocused) YouTubeBlack else YouTubeRed,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun OriginalYouTubeTvKeyboard(
    firstKeyRequester: FocusRequester? = null,
    onChar: (Char) -> Unit,
    onSpace: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit
) {
    val qwertyRows = listOf(
        listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0'),
        listOf('Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'),
        listOf('A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L', '\''),
        listOf('Z', 'X', 'C', 'V', 'B', 'N', 'M', '-', '_', '.')
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(YouTubeSurface.copy(alpha = 0.5f))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        qwertyRows.forEachIndexed { rIdx, row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEachIndexed { cIdx, char ->
                    var keyModifier: Modifier = Modifier.weight(1f).height(38.dp)
                    if (rIdx == 0 && cIdx == 0 && firstKeyRequester != null) {
                        keyModifier = keyModifier.focusRequester(firstKeyRequester)
                    }
                    TvCharKey(
                        char = char,
                        onClick = { onChar(char) },
                        modifier = keyModifier
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Action Keys Row: [ SPACE ] [ DEL ] [ CLEAR ] [ SEARCH ]
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            TvActionKey(
                text = "SPACE",
                icon = Icons.Default.SpaceBar,
                onClick = onSpace,
                modifier = Modifier.weight(2.4f).height(40.dp)
            )
            TvActionKey(
                text = "DEL",
                icon = Icons.Default.Backspace,
                onClick = onBackspace,
                modifier = Modifier.weight(1.5f).height(40.dp)
            )
            TvActionKey(
                text = "CLEAR",
                icon = Icons.Default.Clear,
                onClick = onClear,
                modifier = Modifier.weight(1.3f).height(40.dp)
            )
            TvActionKey(
                text = "SEARCH",
                icon = Icons.Default.Search,
                isPrimary = true,
                onClick = onSearch,
                modifier = Modifier.weight(2.0f).height(40.dp)
            )
        }
    }
}

@Composable
private fun TvCharKey(
    char: Char,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) YouTubeFocusedBorder else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .background(if (isFocused) YouTubeWhite else YouTubeSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onKeyEvent { keyEvent ->
                if ((keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) &&
                    keyEvent.type == KeyEventType.KeyUp
                ) {
                    onClick()
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = char.toString(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (isFocused) YouTubeBlack else YouTubeWhite
        )
    }
}

@Composable
private fun TvActionKey(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isPrimary: Boolean = false
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) YouTubeFocusedBorder else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                when {
                    isFocused -> YouTubeWhite
                    isPrimary -> YouTubeRed
                    else -> Color(0xFF2C2C2C)
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onKeyEvent { keyEvent ->
                if ((keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) &&
                    keyEvent.type == KeyEventType.KeyUp
                ) {
                    onClick()
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = if (isFocused) YouTubeBlack else YouTubeWhite,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isFocused) YouTubeBlack else YouTubeWhite
            )
        }
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
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isFocused) 1.5.dp else 0.dp,
                color = if (isFocused) YouTubeFocusedBorder else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .background(if (isFocused) YouTubeWhite else Color(0xFF2A2A2A))
            .clickable(onClick = onClick)
            .onKeyEvent { keyEvent ->
                if ((keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) &&
                    keyEvent.type == KeyEventType.KeyUp
                ) {
                    onClick()
                    true
                } else false
            }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isFocused) YouTubeBlack else YouTubeSecondaryText
        )
    }
}

@Composable
private fun SearchPredictionItem(
    prediction: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) YouTubeFocusedBorder else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .background(if (isFocused) YouTubeWhite.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onKeyEvent { keyEvent ->
                if ((keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) &&
                    keyEvent.type == KeyEventType.KeyUp
                ) {
                    onClick()
                    true
                } else false
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = if (isFocused) YouTubeWhite else YouTubeSecondaryText,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = prediction,
            color = if (isFocused) YouTubeWhite else YouTubeWhite.copy(alpha = 0.9f),
            fontSize = 15.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * In-App Voice Search Listening Overlay (Pulsing Red Microphone HUD).
 * Remains inside the app without switching to external Google Assistant activity.
 */
@Composable
private fun VoiceListeningCard(
    partialText: String,
    rmsValue: Float,
    onCancel: () -> Unit
) {
    val pulseScale by animateFloatAsState(targetValue = 1f + (rmsValue / 15f).coerceIn(0f, 0.4f), label = "pulse")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(YouTubeSurface)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size((72 * pulseScale).dp)
                    .clip(CircleShape)
                    .background(YouTubeRed)
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Listening",
                    tint = YouTubeWhite,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = if (partialText.isNotEmpty()) "\"$partialText\"" else "Listening...",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = YouTubeWhite
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Speak into your TV remote or microphone to search",
                fontSize = 14.sp,
                color = YouTubeSecondaryText
            )
            Spacer(modifier = Modifier.height(24.dp))
            TvActionKey(
                text = "CANCEL",
                icon = Icons.Default.Clear,
                onClick = onCancel,
                modifier = Modifier.width(120.dp).height(38.dp)
            )
        }
    }
}

@Composable
private fun SearchMinimizedIconsStrip(
    onExpand: () -> Unit,
    onVoiceClick: () -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(68.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        SearchIconButton(
            icon = Icons.Default.Search,
            contentDescription = "Expand Keyboard",
            onClick = onExpand
        )
        SearchIconButton(
            icon = Icons.Default.Mic,
            contentDescription = "Voice Search",
            onClick = onVoiceClick
        )
        SearchIconButton(
            icon = Icons.Default.Clear,
            contentDescription = "Clear Search",
            onClick = onClearClick
        )
    }
}

@Composable
private fun SearchIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .border(
                width = if (isFocused) 2.5.dp else 0.dp,
                color = if (isFocused) YouTubeFocusedBorder else Color.Transparent,
                shape = CircleShape
            )
            .background(if (isFocused) YouTubeWhite else YouTubeSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(true)
            .onKeyEvent { keyEvent ->
                if ((keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter) &&
                    keyEvent.type == KeyEventType.KeyUp
                ) {
                    onClick()
                    true
                } else false
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isFocused) Color.Black else YouTubeWhite,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun SearchSectionRow(
    section: SectionRowModel,
    rIndex: Int,
    totalRows: Int,
    onVideoClick: (VideoCardModel) -> Unit,
    onVideoLongClick: (VideoCardModel) -> Unit,
    onVideoFocused: (Int, VideoCardModel) -> Unit,
    onLeftFromFirstCard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = section.title,
            color = YouTubeWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = section.items,
                key = { cIndex, video -> "search_r${rIndex}_${video.videoId}_$cIndex" }
            ) { cIndex, video ->
                VideoCard(
                    video = video,
                    cardWidth = 220.dp,
                    onClick = { onVideoClick(video) },
                    onLongClick = { onVideoLongClick(video) },
                    onFocused = { onVideoFocused(cIndex, video) },
                    isFirstInRow = (cIndex == 0),
                    isLastInRow = (cIndex == section.items.lastIndex),
                    onLeftBoundaryPress = {
                        if (cIndex == 0) {
                            onLeftFromFirstCard()
                            true
                        } else false
                    },
                    onRightBoundaryPress = {
                        if (cIndex == section.items.lastIndex) {
                            true // Clamp at column 9: stays on last card!
                        } else false
                    },
                    onUpBoundaryPress = {
                        if (rIndex == 0) true // Clamp top row
                        else false
                    },
                    onDownBoundaryPress = {
                        if (rIndex == totalRows - 1) true // Clamp bottom row
                        else false
                    }
                )
            }
        }
    }
}
