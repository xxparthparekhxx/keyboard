package io.github.xxparthparekhxx.composekeyboard.ui.keyboard

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
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import io.github.xxparthparekhxx.composekeyboard.data.Capitalization
import io.github.xxparthparekhxx.composekeyboard.data.ClipboardHistoryManager
import io.github.xxparthparekhxx.composekeyboard.data.FieldInputKind
import io.github.xxparthparekhxx.composekeyboard.data.GraphemeClusters
import io.github.xxparthparekhxx.composekeyboard.data.EmojiSuggestions
import io.github.xxparthparekhxx.composekeyboard.data.KeyModel
import io.github.xxparthparekhxx.composekeyboard.data.KeyType
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardLayouts
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardMode
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardSettings
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardThemeType
import io.github.xxparthparekhxx.composekeyboard.data.SwipeDictionary
import io.github.xxparthparekhxx.composekeyboard.input.swipe.nn.SwipeNeuralDecoder
import io.github.xxparthparekhxx.composekeyboard.input.swipe.SwipeConstants
import io.github.xxparthparekhxx.composekeyboard.input.swipe.SwipeController
import io.github.xxparthparekhxx.composekeyboard.input.swipe.SwipeKeyGeometry
import io.github.xxparthparekhxx.composekeyboard.input.swipe.swipeTypingGestures
import io.github.xxparthparekhxx.composekeyboard.theme.ComposeKeyboardTheme
import io.github.xxparthparekhxx.composekeyboard.theme.LocalKeyboardColors
import kotlin.math.abs

@Composable
fun KeyboardScreen(
    settings: KeyboardSettings,
    clipboardManager: ClipboardHistoryManager,
    swipeDictionary: SwipeDictionary,
    neuralDecoder: SwipeNeuralDecoder? = null,
    imeAction: Int = EditorInfo.IME_ACTION_UNSPECIFIED,
    inputSession: Int = 0,
    cursorWantsShift: Boolean = false,
    fieldInputKind: FieldInputKind = FieldInputKind.TEXT,
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
    onRequestMicPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    ComposeKeyboardTheme(
        themeType = settings.theme,
        customColors = settings.customColors
    ) {
        val colors = LocalKeyboardColors.current
        val view = LocalView.current
        var mode by remember(inputSession) {
            mutableStateOf(Capitalization.initialMode(fieldInputKind.opensAsNumpad, cursorWantsShift))
        }
        var lastShiftTapTime by remember { mutableLongStateOf(0L) }
        var lastSpaceTapTime by remember(inputSession) { mutableLongStateOf(0L) }
        var lastNonSpaceChar by remember(inputSession) { mutableStateOf(' ') }

        // Keyboard height scale
        val rowHeight = if (mode == KeyboardMode.NUMPAD) (56 * settings.heightMultiplier).dp else (48 * settings.heightMultiplier).dp
        val keyRowHorizontalPadding = if (mode == KeyboardMode.NUMPAD) 8.dp else 2.dp
        val numberRowHeight = (40 * settings.heightMultiplier).dp
        val panelHeight = (250 * settings.heightMultiplier).dp

        // --- Swipe typing & Autocomplete ------------------------------------
        val geometry = remember { SwipeKeyGeometry() }
        val scope = rememberCoroutineScope()
        val swipeController = remember(geometry, swipeDictionary) {
            SwipeController(geometry, swipeDictionary, scope)
        }
        var suggestions by remember(inputSession) { mutableStateOf(emptyList<String>()) }
        var selectedSuggestion by remember(inputSession) { mutableIntStateOf(-1) }
        var typedPrefix by remember(inputSession) { mutableStateOf("") }
        var isSwipeResult by remember(inputSession) { mutableStateOf(false) }
        var emojiSearching by remember(inputSession) { mutableStateOf(false) }
        var emojiSearchQuery by remember(inputSession) { mutableStateOf("") }

        /** Last character this keyboard committed, for the double-space period shortcut. */
        var lastCommitted by remember(inputSession) { mutableStateOf(' ') }

        val isAlphaMode = mode == KeyboardMode.LOWERCASE ||
                mode == KeyboardMode.UPPERCASE ||
                mode == KeyboardMode.CAPS_LOCKED
        val swipeEnabled = settings.swipeTypingEnabled && isAlphaMode && fieldInputKind.allowsSwipe

        fun lettersMode(): KeyboardMode = Capitalization.lettersMode(cursorWantsShift)

        /**
         * Auto-capitalization follows the editor caret, not a one-shot "this
         * field wants capitals" flag. Re-evaluating on [cursorWantsShift] is
         * what keeps shift correct after dismiss, backspace, and tapping into
         * the middle of a sentence. Caps-lock and symbol/emoji layouts are
         * left alone.
         */
        LaunchedEffect(inputSession, fieldInputKind) {
            if (fieldInputKind.opensAsNumpad) {
                mode = KeyboardMode.NUMPAD
            } else {
                mode = Capitalization.applyCursorShift(mode, cursorWantsShift)
            }
        }
        LaunchedEffect(cursorWantsShift) {
            mode = Capitalization.applyCursorShift(mode, cursorWantsShift)
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
                lastCommitted = 'a'
                lastNonSpaceChar = 'a'
                lastSpaceTapTime = 0L
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
            if (!fieldInputKind.allowsSuggestions) {
                suggestions = emptyList()
                return
            }
            suggestions = swipeDictionary.getCompletions(prefix, maxCount = 4) +
                    EmojiSuggestions.emojisFor(prefix, maxCount = 2)
        }

        fun dispatchLongPress(type: KeyType) {
            if (mode == KeyboardMode.EMOJI && emojiSearching) {
                if (type is KeyType.Character && type.popup.isNotEmpty()) {
                    emojiSearchQuery += type.popup.first()
                }
                return
            }
            if (type is KeyType.Character && type.popup.isNotEmpty()) {
                val alt = type.popup.first()
                onTextInput(alt)
                lastCommitted = alt[0]
                lastNonSpaceChar = alt[0]
                lastSpaceTapTime = 0L
                typedPrefix = ""
                suggestions = emptyList()
                isSwipeResult = false
                if (settings.autoCapitalization && isAlphaMode && mode == KeyboardMode.UPPERCASE) {
                    mode = KeyboardMode.LOWERCASE
                }
            }
        }

        /** Single entry point for every key on every row. */
        fun dispatchKey(type: KeyType) {
            if (mode == KeyboardMode.EMOJI && emojiSearching) {
                when (type) {
                    is KeyType.Character -> emojiSearchQuery += type.primary
                    is KeyType.Space -> emojiSearchQuery += " "
                    is KeyType.Backspace -> {
                        val drop = GraphemeClusters.utf16LengthOfLastCluster(emojiSearchQuery)
                        if (drop > 0) emojiSearchQuery = emojiSearchQuery.dropLast(drop)
                    }
                    is KeyType.EmojiToggle -> {
                        emojiSearching = false
                        emojiSearchQuery = ""
                    }
                    is KeyType.AlphabetToggle -> {
                        emojiSearching = false
                        emojiSearchQuery = ""
                        mode = lettersMode()
                    }
                    else -> { }
                }
                return
            }
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
                    lastNonSpaceChar = char[0]
                    lastSpaceTapTime = 0L
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
                    lastSpaceTapTime = 0L
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
                    val now = System.currentTimeMillis()
                    if (now - lastSpaceTapTime < 350L && lastCommitted == ' ' && lastNonSpaceChar !in ".!?\n\t") {
                        // Double-tap spacebar shortcut: replace trailing space with ". "
                        onDelete()
                        onTextInput(". ")
                        lastCommitted = '.'
                        lastNonSpaceChar = '.'
                        if (settings.autoCapitalization && isAlphaMode) {
                            mode = KeyboardMode.UPPERCASE
                        }
                        lastSpaceTapTime = 0L
                    } else {
                        onTextInput(" ")
                        // A space after . ! ? ends the sentence; the next word
                        // starts with a capital.
                        if (settings.autoCapitalization && isAlphaMode && lastCommitted in ".!?") {
                            mode = KeyboardMode.UPPERCASE
                        }
                        lastCommitted = ' '
                        lastSpaceTapTime = now
                    }
                    typedPrefix = ""
                    suggestions = emptyList()
                    isSwipeResult = false
                }
                is KeyType.Enter -> {
                    onAction(imeAction)
                    typedPrefix = ""
                    suggestions = emptyList()
                    isSwipeResult = false
                    lastSpaceTapTime = 0L
                    lastCommitted = '\n'
                    lastNonSpaceChar = '\n'
                }
                is KeyType.SymbolToggle -> {
                    mode = KeyboardMode.SYMBOLS
                    typedPrefix = ""
                    suggestions = emptyList()
                    isSwipeResult = false
                    lastSpaceTapTime = 0L
                }
                is KeyType.SymbolMoreToggle -> {
                    mode = KeyboardMode.SYMBOLS_MORE
                    typedPrefix = ""
                    suggestions = emptyList()
                    isSwipeResult = false
                    lastSpaceTapTime = 0L
                }
                is KeyType.AlphabetToggle -> {
                    mode = lettersMode()
                    typedPrefix = ""
                    suggestions = emptyList()
                    isSwipeResult = false
                    lastSpaceTapTime = 0L
                }
                is KeyType.NumpadToggle -> {
                    mode = KeyboardMode.NUMPAD
                    typedPrefix = ""
                    suggestions = emptyList()
                    isSwipeResult = false
                    lastSpaceTapTime = 0L
                }
                is KeyType.EmojiToggle -> {
                    mode = KeyboardMode.EMOJI
                    typedPrefix = ""
                    suggestions = emptyList()
                    isSwipeResult = false
                    lastSpaceTapTime = 0L
                }
                else -> {
                    typedPrefix = ""
                    suggestions = emptyList()
                    isSwipeResult = false
                    lastSpaceTapTime = 0L
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
                    onNumpadClick = {
                        mode = if (mode == KeyboardMode.NUMPAD) lettersMode() else KeyboardMode.NUMPAD
                    },
                    onEmojiClick = {
                        if (mode == KeyboardMode.EMOJI) {
                            emojiSearching = false
                            emojiSearchQuery = ""
                            mode = lettersMode()
                        } else {
                            mode = KeyboardMode.EMOJI
                        }
                    },
                    onClipboardClick = {
                        mode = if (mode == KeyboardMode.CLIPBOARD) lettersMode() else KeyboardMode.CLIPBOARD
                    },
                    onVoiceClick = {
                        mode = if (mode == KeyboardMode.VOICE) lettersMode() else KeyboardMode.VOICE
                    },
                    onThemeClick = {
                        mode = if (mode == KeyboardMode.THEMES) lettersMode() else KeyboardMode.THEMES
                    },
                    onSettingsClick = {
                        mode = if (mode == KeyboardMode.SETTINGS) lettersMode() else KeyboardMode.SETTINGS
                    }
                )
            }

            when (mode) {
                KeyboardMode.EMOJI -> {
                    val searchPanelHeight = (46 + (50 * settings.emojiScale * 2f) + 20).dp
                    Column(modifier = Modifier.fillMaxWidth()) {
                        EmojiPicker(
                            hapticEnabled = settings.hapticFeedback,
                            emojiScale = settings.emojiScale,
                            isSearching = emojiSearching,
                            searchQuery = emojiSearchQuery,
                            onSearchingChange = { searching ->
                                emojiSearching = searching
                                if (!searching) emojiSearchQuery = ""
                            },
                            onSearchQueryChange = { emojiSearchQuery = it },
                            hideBottomBar = emojiSearching,
                            onEmojiSelected = { emoji ->
                                onTextInput(emoji)
                            },
                            onDelete = onDelete,
                            onSwitchToKeyboard = {
                                emojiSearching = false
                                emojiSearchQuery = ""
                                mode = lettersMode()
                            },
                            modifier = Modifier.height(
                                if (emojiSearching) searchPanelHeight else panelHeight
                            )
                        )
                        if (emojiSearching) {
                            EmojiSearchKeyboard(
                                settings = settings,
                                imeAction = imeAction,
                                rowHeight = rowHeight,
                                onKeyPress = { type -> dispatchKey(type) },
                                onKeyLongPress = { type -> dispatchLongPress(type) }
                            )
                        }
                    }
                }
                KeyboardMode.CLIPBOARD -> {
                    ClipboardView(
                        clipboardManager = clipboardManager,
                        hapticEnabled = settings.hapticFeedback,
                        onClipSelected = { text ->
                            onTextInput(text)
                        },
                        onClose = {
                            mode = lettersMode()
                        },
                        modifier = Modifier.height(panelHeight)
                    )
                }
                KeyboardMode.VOICE -> {
                    VoiceInputView(
                        hapticEnabled = settings.hapticFeedback,
                        onTextCommitted = { text ->
                            onTextInput(text)
                        },
                        onRequestMicPermission = onRequestMicPermission,
                        onClose = {
                            mode = lettersMode()
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
                            mode = lettersMode()
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
                            mode = lettersMode()
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
                                            showKeyPopups = settings.showKeyPopups,
                                            onKeyPress = { type -> dispatchKey(type) },
                                            onKeyLongPress = { type -> dispatchLongPress(type) },
                                            modifier = Modifier.weight(key.weight)
                                        )
                                    }
                                }
                            }

                            // Main keyboard rows based on current mode
                            val (row1, row2, row3, bottomRow) = when (mode) {
                                KeyboardMode.NUMPAD -> KeyboardLayouts.numpadRowsFor(fieldInputKind)
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
                                    KeyboardLayouts.getQwertyRow1(settings.showNumberRow),
                                    KeyboardLayouts.qwertyRow2,
                                    KeyboardLayouts.qwertyRow3,
                                    KeyboardLayouts.qwertyBottomRowFor(fieldInputKind)
                                )
                            }

                            // Row 1
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(rowHeight)
                                    .padding(horizontal = keyRowHorizontalPadding, vertical = 1.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                row1.forEach { key ->
                                    KeyboardKey(
                                        key = key,
                                        mode = mode,
                                        imeAction = imeAction,
                                        hapticEnabled = settings.hapticFeedback,
                                        fontScale = settings.fontScale,
                                        showKeyPopups = settings.showKeyPopups,
                                        onKeyPress = { type -> dispatchKey(type) },
                                        onKeyLongPress = { type -> dispatchLongPress(type) },
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
                                    .padding(horizontal = keyRowHorizontalPadding, vertical = 1.dp),
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
                                        showKeyPopups = settings.showKeyPopups,
                                        onKeyPress = { type -> dispatchKey(type) },
                                        onKeyLongPress = { type -> dispatchLongPress(type) },
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
                                    .padding(horizontal = keyRowHorizontalPadding, vertical = 1.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                row3.forEach { key ->
                                    KeyboardKey(
                                        key = key,
                                        mode = mode,
                                        imeAction = imeAction,
                                        hapticEnabled = settings.hapticFeedback,
                                        fontScale = settings.fontScale,
                                        showKeyPopups = settings.showKeyPopups,
                                        onKeyPress = { type -> dispatchKey(type) },
                                        onKeyLongPress = { type -> dispatchLongPress(type) },
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
                                    .padding(horizontal = keyRowHorizontalPadding, vertical = 1.dp),
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
                                                showKeyPopups = settings.showKeyPopups,
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
                                            showKeyPopups = settings.showKeyPopups,
                                            onKeyPress = { type -> dispatchKey(type) },
                                            onKeyLongPress = { type -> dispatchLongPress(type) },
                                            modifier = Modifier.weight(key.weight)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Safe area bottom spacing for system navigation bars:
            //  • On Android 15+ (API 35+) / Android 16 (API 36+), edge-to-edge is enforced
            //    for IME windows, causing the window to extend behind the navigation bar.
            //    We reactively listen for WindowInsetsCompat.Type.navigationBars() and pad
            //    the keyboard so keys sit above the gesture pill / 3-button navigation bar.
            //  • On older devices (API < 35), the OS WindowManager already docks the IME window
            //    above the navigation bar. Adding navigation bar insets would double-count the space
            //    and cause excessive dead bottom padding, so we use a clean standard 6.dp margin.
            var navInsetPx by remember {
                mutableIntStateOf(
                    if (Build.VERSION.SDK_INT >= 35) {
                        val root = ViewCompat.getRootWindowInsets(view)
                            ?: view.rootWindowInsets?.let { WindowInsetsCompat.toWindowInsetsCompat(it, view) }
                        root?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
                    } else {
                        0
                    }
                )
            }

            if (Build.VERSION.SDK_INT >= 35) {
                DisposableEffect(view) {
                    val listener = OnApplyWindowInsetsListener { _, insets ->
                        navInsetPx = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                        insets
                    }
                    ViewCompat.setOnApplyWindowInsetsListener(view, listener)
                    val initialRoot = ViewCompat.getRootWindowInsets(view)
                    if (initialRoot != null) {
                        val nav = initialRoot.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                        if (nav > 0) {
                            navInsetPx = nav
                        }
                    }
                    ViewCompat.requestApplyInsets(view)
                    onDispose {
                        ViewCompat.setOnApplyWindowInsetsListener(view, null)
                    }
                }
            }

            val density = LocalDensity.current
            val navInsetDp = with(density) { navInsetPx.toDp() }
            val bottomPadding = if (Build.VERSION.SDK_INT >= 35) {
                navInsetDp + 12.dp
            } else {
                2.dp
            }

            if (bottomPadding > 0.dp) {
                Spacer(modifier = Modifier.height(bottomPadding))
            }
        }
    }
}

@Composable
private fun EmojiSearchKeyboard(
    settings: KeyboardSettings,
    imeAction: Int,
    rowHeight: androidx.compose.ui.unit.Dp,
    onKeyPress: (KeyType) -> Unit,
    onKeyLongPress: (KeyType) -> Unit
) {
    val rows = listOf(
        KeyboardLayouts.getQwertyRow1(showNumberRow = true),
        KeyboardLayouts.qwertyRow2,
        KeyboardLayouts.qwertyRow3,
        KeyboardLayouts.emojiSearchBottomRow
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                if (index == 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
                row.forEach { key ->
                    KeyboardKey(
                        key = key,
                        mode = KeyboardMode.LOWERCASE,
                        imeAction = imeAction,
                        hapticEnabled = settings.hapticFeedback,
                        fontScale = settings.fontScale,
                        showKeyPopups = settings.showKeyPopups,
                        onKeyPress = onKeyPress,
                        onKeyLongPress = onKeyLongPress,
                        modifier = Modifier.weight(key.weight)
                    )
                }
                if (index == 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
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
