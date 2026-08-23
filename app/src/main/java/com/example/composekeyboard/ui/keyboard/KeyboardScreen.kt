package com.example.composekeyboard.ui.keyboard

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.inputmethod.EditorInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.example.composekeyboard.data.ClipboardHistoryManager
import com.example.composekeyboard.data.EmojiSuggestions
import com.example.composekeyboard.data.KeyModel
import com.example.composekeyboard.data.KeyType
import com.example.composekeyboard.data.KeyboardLayouts
import com.example.composekeyboard.data.KeyboardMode
import com.example.composekeyboard.data.KeyboardSettings
import com.example.composekeyboard.data.KeyboardThemeType
import com.example.composekeyboard.data.SwipeDictionary
import com.example.composekeyboard.input.swipe.nn.SwipeNeuralDecoder
import com.example.composekeyboard.input.swipe.SwipeConstants
import com.example.composekeyboard.input.swipe.SwipeController
import com.example.composekeyboard.input.swipe.SwipeKeyGeometry
import com.example.composekeyboard.input.swipe.swipeTypingGestures
import com.example.composekeyboard.theme.ComposeKeyboardTheme
import com.example.composekeyboard.theme.LocalKeyboardColors
import kotlin.math.abs

@Composable
fun KeyboardScreen(
    settings: KeyboardSettings,
    clipboardManager: ClipboardHistoryManager,
    swipeDictionary: SwipeDictionary,
    neuralDecoder: SwipeNeuralDecoder? = null,
    imeAction: Int = EditorInfo.IME_ACTION_UNSPECIFIED,
    inputSession: Int = 0,
    autoCapitalizeField: Boolean = false,
    onTextInput: (String) -> Unit,
    onDelete: () -> Unit,
    onAction: (Int) -> Unit,
    onMoveCursor: (Int) -> Unit,
    onSwipeWord: (String) -> Unit,
    onSwipeWordReplaced: (String) -> Unit,
    onAutocompleteSelected: (word: String, prefix: String) -> Unit = { _, _ -> },
    onThemeChanged: (KeyboardThemeType) -> Unit,
    onHapticToggled: (Boolean) -> Unit,
    onSoundToggled: (Boolean) -> Unit,
    onNumberRowToggled: (Boolean) -> Unit,
    onAutoCapsToggled: (Boolean) -> Unit,
    onSwipeTypingToggled: (Boolean) -> Unit,
    onHeightMultiplierChanged: (Float) -> Unit = {},
    onFontScaleChanged: (Float) -> Unit = {},
    onEmojiScaleChanged: (Float) -> Unit = {},
    onOpenFullSettings: () -> Unit,
    onSwitchIme: () -> Unit,
    modifier: Modifier = Modifier
) {
    ComposeKeyboardTheme(
        themeType = settings.theme,
        customColors = settings.customColors
    ) {
        val colors = LocalKeyboardColors.current
        val view = LocalView.current
        var mode by rememberSaveable { mutableStateOf(KeyboardMode.LOWERCASE) }
        var lastShiftTapTime by remember { mutableLongStateOf(0L) }

        // Keyboard height scale
        val rowHeight = (48 * settings.heightMultiplier).dp
        val numberRowHeight = (40 * settings.heightMultiplier).dp
        val panelHeight = (250 * settings.heightMultiplier).dp

        // --- Swipe typing & Autocomplete ------------------------------------
        val geometry = remember { SwipeKeyGeometry() }
        val scope = rememberCoroutineScope()
        val swipeController = remember(geometry, swipeDictionary) {
            SwipeController(geometry, swipeDictionary, scope)
        }
        var suggestions by remember { mutableStateOf(emptyList<String>()) }
        var selectedSuggestion by remember { mutableIntStateOf(-1) }
        var typedPrefix by remember { mutableStateOf("") }
        var isSwipeResult by remember { mutableStateOf(false) }

        /** Last character this keyboard committed, for sentence detection. */
        var lastCommitted by remember { mutableStateOf(' ') }

        val isAlphaMode = mode == KeyboardMode.LOWERCASE ||
                mode == KeyboardMode.UPPERCASE ||
                mode == KeyboardMode.CAPS_LOCKED
        val swipeEnabled = settings.swipeTypingEnabled && isAlphaMode

        /**
         * Auto-capitalization: a fresh field that asks for capitals starts in
         * shift, and shift comes back after sentence-ending punctuation. The
         * session counter keys the reset so it also fires when the next field
         * has the same input type as the last one.
         */
        LaunchedEffect(inputSession) {
            if (!isAlphaMode) return@LaunchedEffect
            mode = if (settings.autoCapitalization && autoCapitalizeField) {
                KeyboardMode.UPPERCASE
            } else {
                KeyboardMode.LOWERCASE
            }
        }

        // Reassigned on every recomposition so the handlers always see the
        // current shift state and the current input callbacks.
        SideEffect {
            swipeController.neural = neuralDecoder
            swipeController.onRecognized = {
                if (settings.hapticFeedback) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }
            swipeController.onResult = { words ->
                val cased = words.map { applyShift(it, mode) }
                isSwipeResult = true
                suggestions = cased
                selectedSuggestion = 0
                typedPrefix = ""
                onSwipeWord(cased.first())
                if (mode == KeyboardMode.UPPERCASE) mode = KeyboardMode.LOWERCASE
            }
        }

        // Symbol and emoji layouts do not report letter positions, so anything
        // recorded for them would be stale by the time we came back.
        LaunchedEffect(isAlphaMode) {
            if (!isAlphaMode) {
                swipeController.cancel()
                geometry.reset()
                typedPrefix = ""
                suggestions = emptyList()
            }
        }

        fun clearSuggestions() {
            if (suggestions.isNotEmpty()) suggestions = emptyList()
            typedPrefix = ""
            isSwipeResult = false
            selectedSuggestion = -1
        }

        /** Word completions plus matching emojis, for the suggestion strip. */
        fun refreshSuggestions(prefix: String) {
            suggestions = swipeDictionary.getCompletions(prefix, maxCount = 4) +
                    EmojiSuggestions.emojisFor(prefix, maxCount = 2)
        }

        /** Single entry point for every key on every row. */
        fun dispatchKey(type: KeyType) {
            if (type is KeyType.Shift) {
                val now = System.currentTimeMillis()
                mode = if (now - lastShiftTapTime < 350) {
                    // Double tap = Caps Lock
                    if (mode == KeyboardMode.CAPS_LOCKED) KeyboardMode.LOWERCASE
                    else KeyboardMode.CAPS_LOCKED
                } else {
                    when (mode) {
                        KeyboardMode.LOWERCASE -> KeyboardMode.UPPERCASE
                        else -> KeyboardMode.LOWERCASE
                    }
                }
                lastShiftTapTime = now
                if (typedPrefix.isNotEmpty()) {
                    val casedPrefix = when (mode) {
                        KeyboardMode.UPPERCASE, KeyboardMode.CAPS_LOCKED -> typedPrefix.uppercase()
                        else -> typedPrefix.lowercase()
                    }
                    refreshSuggestions(casedPrefix)
                }
                return
            }

            when (type) {
                is KeyType.Character -> {
                    val char = when (mode) {
                        KeyboardMode.UPPERCASE, KeyboardMode.CAPS_LOCKED -> type.primary.uppercase()
                        else -> type.primary
                    }
                    onTextInput(char)
                    lastCommitted = char[0]
                    if (mode == KeyboardMode.UPPERCASE) {
                        mode = KeyboardMode.LOWERCASE
                    }
                    if (char.length == 1 && (char[0].isLetter() || char[0] == '\'')) {
                        isSwipeResult = false
                        selectedSuggestion = -1
                        val next = typedPrefix + char
                        typedPrefix = next
                        refreshSuggestions(next)
                    } else {
                        typedPrefix = ""
                        suggestions = emptyList()
                        isSwipeResult = false
                    }
                }
                is KeyType.Backspace -> {
                    onDelete()
                    if (isSwipeResult) {
                        isSwipeResult = false
                        suggestions = emptyList()
                        typedPrefix = ""
                    } else if (typedPrefix.isNotEmpty()) {
                        val next = typedPrefix.dropLast(1)
                        typedPrefix = next
                        if (next.isNotEmpty()) {
                            refreshSuggestions(next)
                        } else {
                            suggestions = emptyList()
                        }
                    } else {
                        suggestions = emptyList()
                    }
                }
                is KeyType.Space -> {
                    onTextInput(" ")
                    typedPrefix = ""
                    suggestions = emptyList()
                    isSwipeResult = false
                    // A space after . ! ? ends the sentence; the next word
                    // starts with a capital.
                    if (settings.autoCapitalization && isAlphaMode && lastCommitted in ".!?") {
                        mode = KeyboardMode.UPPERCASE
                    }
                    lastCommitted = ' '
                }
                is KeyType.Enter -> {
                    onAction(imeAction)
                    typedPrefix = ""
                    suggestions = emptyList()
                    isSwipeResult = false
                    if (settings.autoCapitalization && isAlphaMode &&
                        (lastCommitted == '\n' || lastCommitted in ".!?")
                    ) {
                        mode = KeyboardMode.UPPERCASE
                    }
                    lastCommitted = '\n'
                }
                is KeyType.SymbolToggle -> {
                    mode = KeyboardMode.SYMBOLS
                    typedPrefix = ""
                    suggestions = emptyList()
                    isSwipeResult = false
                }
                is KeyType.SymbolMoreToggle -> {
                    mode = KeyboardMode.SYMBOLS_MORE
                    typedPrefix = ""
                    suggestions = emptyList()
                    isSwipeResult = false
                }
                is KeyType.AlphabetToggle -> {
                    mode = KeyboardMode.LOWERCASE
                    typedPrefix = ""
                    suggestions = emptyList()
                    isSwipeResult = false
                }
                is KeyType.EmojiToggle -> {
                    mode = KeyboardMode.EMOJI
                    typedPrefix = ""
                    suggestions = emptyList()
                    isSwipeResult = false
                }
                else -> {
                    typedPrefix = ""
                    suggestions = emptyList()
                    isSwipeResult = false
                }
            }
        }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(colors.background)
        ) {
            // The suggestion strip stands in for the toolbar rather than adding a
            // row, so suggestions never shift the keys under the finger.
            if (swipeController.isSwiping || suggestions.isNotEmpty()) {
                SuggestionBar(
                    suggestions = suggestions,
                    selectedIndex = selectedSuggestion,
                    // Lambda, not value: keeps the ~18 Hz preview updates from
                    // recomposing the whole keyboard (see SuggestionBar doc).
                    previewWord = { swipeController.preview },
                    isSwiping = swipeController.isSwiping,
                    hapticEnabled = settings.hapticFeedback,
                    fontScale = settings.fontScale,
                    onSuggestionSelected = { index ->
                        val word = suggestions.getOrNull(index) ?: return@SuggestionBar
                        if (isSwipeResult) {
                            if (index == selectedSuggestion) {
                                clearSuggestions()
                            } else {
                                selectedSuggestion = index
                                onSwipeWordReplaced(word)
                            }
                        } else {
                            onAutocompleteSelected(word, typedPrefix)
                            typedPrefix = ""
                            suggestions = emptyList()
                        }
                    }
                )
            } else {
                KeyboardHeader(
                    currentMode = mode,
                    onEmojiClick = {
                        mode = if (mode == KeyboardMode.EMOJI) KeyboardMode.LOWERCASE else KeyboardMode.EMOJI
                    },
                    onClipboardClick = {
                        mode = if (mode == KeyboardMode.CLIPBOARD) KeyboardMode.LOWERCASE else KeyboardMode.CLIPBOARD
                    },
                    onThemeClick = {
                        mode = if (mode == KeyboardMode.THEMES) KeyboardMode.LOWERCASE else KeyboardMode.THEMES
                    },
                    onSwitchImeClick = onSwitchIme,
                    onSettingsClick = {
                        mode = if (mode == KeyboardMode.SETTINGS) KeyboardMode.LOWERCASE else KeyboardMode.SETTINGS
                    }
                )
            }

            when (mode) {
                KeyboardMode.EMOJI -> {
                    EmojiPicker(
                        hapticEnabled = settings.hapticFeedback,
                        emojiScale = settings.emojiScale,
                        onEmojiSelected = { emoji ->
                            onTextInput(emoji)
                        },
                        onDelete = onDelete,
                        onSwitchToKeyboard = {
                            mode = KeyboardMode.LOWERCASE
                        },
                        modifier = Modifier.height(panelHeight)
                    )
                }
                KeyboardMode.CLIPBOARD -> {
                    ClipboardView(
                        clipboardManager = clipboardManager,
                        hapticEnabled = settings.hapticFeedback,
                        onClipSelected = { text ->
                            onTextInput(text)
                        },
                        onClose = {
                            mode = KeyboardMode.LOWERCASE
                        },
                        modifier = Modifier.height(panelHeight)
                    )
                }
                KeyboardMode.THEMES -> {
                    ThemePicker(
                        currentTheme = settings.theme,
                        customColors = settings.customColors,
                        hapticEnabled = settings.hapticFeedback,
                        onThemeSelected = { theme ->
                            onThemeChanged(theme)
                        },
                        onClose = {
                            mode = KeyboardMode.LOWERCASE
                        },
                        modifier = Modifier.height(panelHeight)
                    )
                }
                KeyboardMode.SETTINGS -> {
                    QuickSettingsView(
                        settings = settings,
                        hapticEnabled = settings.hapticFeedback,
                        onHapticToggled = onHapticToggled,
                        onSoundToggled = onSoundToggled,
                        onNumberRowToggled = onNumberRowToggled,
                        onAutoCapsToggled = onAutoCapsToggled,
                        onSwipeTypingToggled = onSwipeTypingToggled,
                        onHeightMultiplierChanged = onHeightMultiplierChanged,
                        onFontScaleChanged = onFontScaleChanged,
                        onEmojiScaleChanged = onEmojiScaleChanged,
                        onOpenFullSettings = onOpenFullSettings,
                        onClose = {
                            mode = KeyboardMode.LOWERCASE
                        },
                        modifier = Modifier.height(panelHeight)
                    )
                }
                else -> {
                    val trailColor = colors.actionKeyBackground.copy(alpha = 0.85f)
                    val trailWidth = with(LocalDensity.current) {
                        (8 * settings.heightMultiplier).dp.toPx()
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coordinates ->
                                val origin = coordinates.positionInWindow()
                                geometry.setBodyOrigin(origin.x, origin.y)
                            }
                            .swipeTypingGestures(
                                enabled = swipeEnabled,
                                geometry = geometry,
                                handler = swipeController
                            )
                            .drawWithContent {
                                drawContent()
                                // Reading the version here is what schedules the
                                // next repaint as the finger moves: the trail
                                // animates in the draw phase alone, with no
                                // recomposition and no relayout per frame.
                                if (swipeController.trailVersion >= 0 && swipeController.isSwiping) {
                                    drawSwipeTrail(swipeController.path, trailColor, trailWidth)
                                }
                            }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Number row if enabled
                            AnimatedVisibility(
                                visible = settings.showNumberRow && isAlphaMode,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(numberRowHeight)
                                        .padding(horizontal = 2.dp, vertical = 1.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    KeyboardLayouts.numberRow.forEach { key ->
                                        KeyboardKey(
                                            key = key,
                                            mode = mode,
                                            imeAction = imeAction,
                                            hapticEnabled = settings.hapticFeedback,
                                            fontScale = settings.fontScale,
                                            onKeyPress = { type -> dispatchKey(type) },
                                            modifier = Modifier.weight(key.weight)
                                        )
                                    }
                                }
                            }

                            // Main keyboard rows based on current mode
                            val (row1, row2, row3, bottomRow) = when (mode) {
                                KeyboardMode.SYMBOLS -> listOf(
                                    KeyboardLayouts.symbolsRow1,
                                    KeyboardLayouts.symbolsRow2,
                                    KeyboardLayouts.symbolsRow3,
                                    KeyboardLayouts.symbolsBottomRow
                                )
                                KeyboardMode.SYMBOLS_MORE -> listOf(
                                    KeyboardLayouts.moreSymbolsRow1,
                                    KeyboardLayouts.moreSymbolsRow2,
                                    KeyboardLayouts.moreSymbolsRow3,
                                    KeyboardLayouts.moreSymbolsBottomRow
                                )
                                else -> listOf(
                                    KeyboardLayouts.qwertyRow1,
                                    KeyboardLayouts.qwertyRow2,
                                    KeyboardLayouts.qwertyRow3,
                                    KeyboardLayouts.qwertyBottomRow
                                )
                            }

                            // Row 1
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rowHeight)
                                    .padding(horizontal = 2.dp, vertical = 1.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                row1.forEach { key ->
                                    KeyboardKey(
                                        key = key,
                                        mode = mode,
                                        imeAction = imeAction,
                                        hapticEnabled = settings.hapticFeedback,
                                        fontScale = settings.fontScale,
                                        onKeyPress = { type -> dispatchKey(type) },
                                        modifier = Modifier
                                            .weight(key.weight)
                                            .trackLetterKey(key, geometry)
                                    )
                                }
                            }

                            // Row 2
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rowHeight)
                                    .padding(horizontal = 2.dp, vertical = 1.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isAlphaMode) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                row2.forEach { key ->
                                    KeyboardKey(
                                        key = key,
                                        mode = mode,
                                        imeAction = imeAction,
                                        hapticEnabled = settings.hapticFeedback,
                                        fontScale = settings.fontScale,
                                        onKeyPress = { type -> dispatchKey(type) },
                                        modifier = Modifier
                                            .weight(key.weight)
                                            .trackLetterKey(key, geometry)
                                    )
                                }
                                if (isAlphaMode) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                            }

                            // Row 3 (Shift, letters/symbols, Backspace)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rowHeight)
                                    .padding(horizontal = 2.dp, vertical = 1.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                row3.forEach { key ->
                                    KeyboardKey(
                                        key = key,
                                        mode = mode,
                                        imeAction = imeAction,
                                        hapticEnabled = settings.hapticFeedback,
                                        fontScale = settings.fontScale,
                                        onKeyPress = { type -> dispatchKey(type) },
                                        modifier = Modifier
                                            .weight(key.weight)
                                            .trackLetterKey(key, geometry)
                                    )
                                }
                            }

                            // Bottom Row (123, Emoji, Space, Period, Enter)
                            var accumulatedDrag by remember { mutableFloatStateOf(0f) }
                            val dragThreshold = SwipeConstants.DRAG_THRESHOLD_PX

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rowHeight)
                                    .padding(horizontal = 2.dp, vertical = 1.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                bottomRow.forEach { key ->
                                    if (key.type is KeyType.Space) {
                                        Box(
                                            modifier = Modifier
                                                .weight(key.weight)
                                                .draggable(
                                                    orientation = Orientation.Horizontal,
                                                    state = rememberDraggableState { delta ->
                                                        accumulatedDrag += delta
                                                        if (abs(accumulatedDrag) >= dragThreshold) {
                                                            val steps = (accumulatedDrag / dragThreshold).toInt()
                                                            clearSuggestions()
                                                            onMoveCursor(steps)
                                                            accumulatedDrag -= steps * dragThreshold
                                                        }
                                                    },
                                                    onDragStopped = {
                                                        accumulatedDrag = 0f
                                                    }
                                                )
                                        ) {
                                            KeyboardKey(
                                                key = key,
                                                mode = mode,
                                                imeAction = imeAction,
                                                hapticEnabled = settings.hapticFeedback,
                                                fontScale = settings.fontScale,
                                                onKeyPress = { dispatchKey(KeyType.Space) },
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    } else {
                                        KeyboardKey(
                                            key = key,
                                            mode = mode,
                                            imeAction = imeAction,
                                            hapticEnabled = settings.hapticFeedback,
                                            fontScale = settings.fontScale,
                                            onKeyPress = { type -> dispatchKey(type) },
                                            modifier = Modifier.weight(key.weight)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Dynamic bottom safe area spacing for system navigation bar (3-button or gesture pill)
            val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val safeDrawingBottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
            val systemBarsBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

            val viewRootInsets = view.rootWindowInsets
            val viewNavBottomPx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                viewRootInsets?.getInsets(android.view.WindowInsets.Type.navigationBars())?.bottom ?: 0
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                @Suppress("DEPRECATION")
                viewRootInsets?.stableInsetBottom ?: 0
            } else {
                0
            }
            val viewNavBottomDp = with(LocalDensity.current) { viewNavBottomPx.toDp() }

            val bottomPadding = maxOf(
                navBarsBottom,
                safeDrawingBottom,
                systemBarsBottom,
                viewNavBottomDp,
                6.dp
            )
            Spacer(modifier = Modifier.height(bottomPadding))
        }
    }
}

/**
 * Reports a letter key's cell to the swipe geometry every time it is placed.
 *
 * Chained ahead of the key's own padding so the recorded rectangle is the whole
 * cell, leaving no dead strips between keys for a gesture to start in. Keys that
 * are not letters — symbols, shift, space — return the modifier untouched, which
 * is what keeps symbol layouts from polluting the letter map.
 */
private fun Modifier.trackLetterKey(key: KeyModel, geometry: SwipeKeyGeometry): Modifier {
    val letter = SwipeKeyGeometry.letterIndexOf(key.type)
    if (letter < 0) return this
    return this.onGloballyPositioned { coordinates ->
        val position = coordinates.positionInWindow()
        geometry.place(
            letter = letter,
            windowX = position.x,
            windowY = position.y,
            width = coordinates.size.width.toFloat(),
            height = coordinates.size.height.toFloat()
        )
    }
}

/** Applies the live shift state to a decoded word. */
private fun applyShift(word: String, mode: KeyboardMode): String = when (mode) {
    KeyboardMode.CAPS_LOCKED -> word.uppercase()
    KeyboardMode.UPPERCASE -> word.replaceFirstChar { it.uppercase() }
    else -> word
}
