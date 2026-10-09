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
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.xxparthparekhxx.composekeyboard.data.Capitalization
import io.github.xxparthparekhxx.composekeyboard.data.ClipboardHistoryManager
import io.github.xxparthparekhxx.composekeyboard.data.FieldInputKind
import io.github.xxparthparekhxx.composekeyboard.data.EmojiCatalog
import io.github.xxparthparekhxx.composekeyboard.data.EmojiSuggestions
import io.github.xxparthparekhxx.composekeyboard.data.KeyModel
import io.github.xxparthparekhxx.composekeyboard.data.KeyType
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardLayouts
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardMode
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardSettings
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardThemeType
import io.github.xxparthparekhxx.composekeyboard.data.RecentEmojiManager
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
    onTextInput: (String) -> Unit,
    onDelete: () -> Unit,
    onAction: (Int) -> Unit,
    onMoveCursor: (Int) -> Unit,
    onSwipeWord: (String) -> Unit,
    onSwipeWordReplaced: (String) -> Unit,
    onThemeChanged: (KeyboardThemeType) -> Unit,
    onHapticToggled: (Boolean) -> Unit,
    onSoundToggled: (Boolean) -> Unit,
    onNumberRowToggled: (Boolean) -> Unit,
    onAutoCapsToggled: (Boolean) -> Unit,
    onSwipeTypingToggled: (Boolean) -> Unit,
    onOpenFullSettings: () -> Unit,
    modifier: Modifier = Modifier,
    neuralDecoder: SwipeNeuralDecoder? = null,
    imeAction: Int = EditorInfo.IME_ACTION_UNSPECIFIED,
    inputSession: Int = 0,
    externalCaretMoves: Int = 0,
    cursorWantsShift: Boolean = false,
    fieldInputKind: FieldInputKind = FieldInputKind.TEXT,
    fieldAllowsSuggestions: Boolean = true,
    isOtpField: Boolean = false,
    isIncognito: Boolean = false,
    otpCode: String? = null,
    inlineSuggestions: List<android.view.inputmethod.InlineSuggestion> = emptyList(),
    showLanguageSwitch: Boolean = false,
    currentWord: String = "",
    previousWord: String? = null,
    predictNextWords: (previous: String) -> List<String> = { emptyList() },
    onSuggestionPicked: (String) -> Unit = {},
    onHeightMultiplierChanged: (Float) -> Unit = {},
    onFontScaleChanged: (Float) -> Unit = {},
    onEmojiScaleChanged: (Float) -> Unit = {},
    onRequestMicPermission: () -> Unit = {},
    onOtpSelected: (String) -> Unit = {},
    onSwitchIme: () -> Unit = {}
) {
    ComposeKeyboardTheme(
        themeType = settings.theme,
        customColors = settings.customColors
    ) {
        val colors = LocalKeyboardColors.current
        val view = LocalView.current
        val context = LocalContext.current

        // Read through rememberUpdatedState so the state machine, which
        // outlives recompositions, always calls the current callbacks.
        val currentOnTextInput by rememberUpdatedState(onTextInput)
        val currentOnDelete by rememberUpdatedState(onDelete)
        val currentOnAction by rememberUpdatedState(onAction)
        val currentOnSwitchIme by rememberUpdatedState(onSwitchIme)
        val currentOnSwipeWord by rememberUpdatedState(onSwipeWord)
        val currentOnSwipeWordReplaced by rememberUpdatedState(onSwipeWordReplaced)
        val currentOnSuggestionPicked by rememberUpdatedState(onSuggestionPicked)
        val typing = remember(inputSession) {
            TypingState(
                initialMode = Capitalization.initialMode(fieldInputKind.opensAsNumpad, cursorWantsShift),
                actions = object : TypingActions {
                    override fun commitText(text: String) = currentOnTextInput(text)
                    override fun deleteBackward() = currentOnDelete()
                    override fun performAction(actionId: Int) = currentOnAction(actionId)
                    override fun switchIme() = currentOnSwitchIme()
                    override fun commitSwipeWord(word: String) = currentOnSwipeWord(word)
                    override fun replaceSwipeWord(word: String) = currentOnSwipeWordReplaced(word)
                    override fun pickSuggestion(word: String) = currentOnSuggestionPicked(word)
                }
            ).apply {
                // Enter inserts the result EmojiPicker highlights first.
                onEmojiSearchSubmit = {
                    val top = EmojiCatalog.peek()
                        ?.let { EmojiCatalog.search(it, emojiSearchQuery, maxCount = 1) }
                        ?.firstOrNull()
                    if (top != null) {
                        RecentEmojiManager.getInstance(context).recordEmoji(top)
                        currentOnTextInput(top)
                    }
                }
            }
        }
        SideEffect {
            typing.autoCapitalization = settings.autoCapitalization
            typing.imeAction = imeAction
        }
        val mode = typing.mode
        val isAlphaMode = typing.isAlphaMode

        // Keyboard height scale
        val rowHeight = if (mode == KeyboardMode.NUMPAD) (56 * settings.heightMultiplier).dp else (48 * settings.heightMultiplier).dp
        val keyRowHorizontalPadding = if (mode == KeyboardMode.NUMPAD) 8.dp else 2.dp
        val numberRowHeight = (40 * settings.heightMultiplier).dp
        val panelHeight = (250 * settings.heightMultiplier).dp

        // --- Swipe typing & suggestions -------------------------------------
        val geometry = remember { SwipeKeyGeometry() }
        val scope = rememberCoroutineScope()
        val swipeController = remember(geometry, swipeDictionary) {
            SwipeController(geometry, swipeDictionary, scope)
        }
        val swipeEnabled = settings.swipeTypingEnabled && isAlphaMode && fieldInputKind.allowsSwipe

        LaunchedEffect(inputSession, fieldInputKind) {
            typing.onFieldStart(fieldInputKind)
        }
        LaunchedEffect(cursorWantsShift) {
            typing.onCursorWantsShift(cursorWantsShift)
        }

        // Reassigned on every recomposition so the handlers always see the
        // current decoder and haptics setting.
        SideEffect {
            swipeController.neural = neuralDecoder
            swipeController.onRecognized = {
                if (settings.hapticFeedback) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }
            swipeController.onResult = { words -> typing.onSwipeResult(words) }
        }

        // A new field: a gesture decoded for the old one must not land here.
        LaunchedEffect(inputSession) {
            swipeController.reset()
        }

        // Symbol and emoji layouts do not report letter positions, so anything
        // recorded for them would be stale by the time we came back.
        LaunchedEffect(isAlphaMode) {
            if (!isAlphaMode) {
                swipeController.cancel()
                geometry.reset()
                typing.clearSwipeAlternatives()
            }
        }

        LaunchedEffect(externalCaretMoves) {
            if (externalCaretMoves != 0) typing.onExternalCaretMove()
        }

        val swipeAlternatives = typing.swipeAlternatives
        val suggestions = remember(
            currentWord, previousWord, mode, swipeAlternatives, fieldAllowsSuggestions
        ) {
            typing.suggestions(
                currentWord = currentWord,
                previousWord = previousWord,
                allowsSuggestions = fieldAllowsSuggestions,
                completions = { prefix ->
                    swipeDictionary.getCompletions(prefix, maxCount = 4) +
                        EmojiSuggestions.emojisFor(prefix, maxCount = 2)
                },
                predictions = { previous ->
                    predictNextWords(previous) + EmojiSuggestions.emojisFor(previous, maxCount = 2)
                }
            )
        }

        val stableKeyPress = remember(typing) { { type: KeyType -> typing.onKey(type) } }
        val stableKeyLongPress = remember(typing) { { type: KeyType -> typing.onLongPress(type) } }
        val stablePopup = remember(typing) { { alt: String -> typing.onPopup(alt) } }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(colors.background)
        ) {
            // The suggestion strip stands in for the toolbar rather than adding a
            // row, so suggestions never shift the keys under the finger.
            if (swipeController.isSwiping || suggestions.isNotEmpty() ||
                !otpCode.isNullOrEmpty() || inlineSuggestions.isNotEmpty()
            ) {
                SuggestionBar(
                    suggestions = suggestions,
                    selectedIndex = typing.selectedSuggestion,
                    // Lambda, not value: keeps the ~18 Hz preview updates from
                    // recomposing the whole keyboard (see SuggestionBar doc).
                    previewWord = { swipeController.preview },
                    isSwiping = swipeController.isSwiping,
                    hapticEnabled = settings.hapticFeedback,
                    fontScale = settings.fontScale,
                    otpCode = otpCode,
                    inlineSuggestions = inlineSuggestions,
                    onOtpSelected = { code ->
                        onOtpSelected(code)
                        typing.clearSwipeAlternatives()
                    },
                    onSuggestionSelected = { index ->
                        typing.onSuggestionTapped(index, suggestions)
                    }
                )
            } else {
                KeyboardHeader(
                    currentMode = mode,
                    isIncognito = isIncognito,
                    onNumpadClick = { typing.togglePanel(KeyboardMode.NUMPAD) },
                    onEmojiClick = { typing.togglePanel(KeyboardMode.EMOJI) },
                    onClipboardClick = { typing.togglePanel(KeyboardMode.CLIPBOARD) },
                    onVoiceClick = { typing.togglePanel(KeyboardMode.VOICE) },
                    onThemeClick = { typing.togglePanel(KeyboardMode.THEMES) },
                    onSettingsClick = { typing.togglePanel(KeyboardMode.SETTINGS) }
                )
            }

            when (mode) {
                KeyboardMode.EMOJI -> {
                    val searchPanelHeight = (46 + (50 * settings.emojiScale * 2f) + 20).dp
                    Column(modifier = Modifier.fillMaxWidth()) {
                        EmojiPicker(
                            hapticEnabled = settings.hapticFeedback,
                            emojiScale = settings.emojiScale,
                            isSearching = typing.emojiSearching,
                            searchQuery = typing.emojiSearchQuery,
                            onSearchingChange = { searching ->
                                if (searching) typing.emojiSearching = true else typing.closeEmojiSearch()
                            },
                            onSearchQueryChange = { typing.emojiSearchQuery = it },
                            hideBottomBar = typing.emojiSearching,
                            onEmojiSelected = { emoji ->
                                onTextInput(emoji)
                            },
                            onDelete = onDelete,
                            onSwitchToKeyboard = { typing.showLetters() },
                            modifier = Modifier.height(
                                if (typing.emojiSearching) searchPanelHeight else panelHeight
                            )
                        )
                        if (typing.emojiSearching) {
                            EmojiSearchKeyboard(
                                settings = settings,
                                // Enter picks the top result, not the field's action.
                                imeAction = EditorInfo.IME_ACTION_DONE,
                                rowHeight = rowHeight,
                                onKeyPress = stableKeyPress,
                                onKeyLongPress = stableKeyLongPress,
                                onPopupSelected = stablePopup
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
                        onClose = { typing.showLetters() },
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
                        onClose = { typing.showLetters() },
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
                        onClose = { typing.showLetters() },
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
                        onClose = { typing.showLetters() },
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
                            // Space doubles as a cursor trackpad: horizontal drags move the caret.
                            var accumulatedDrag by remember { mutableFloatStateOf(0f) }
                            val dragThreshold = SwipeConstants.DRAG_THRESHOLD_PX
                            val dragState = rememberDraggableState { delta ->
                                accumulatedDrag += delta
                                if (abs(accumulatedDrag) >= dragThreshold) {
                                    val steps = (accumulatedDrag / dragThreshold).toInt()
                                    typing.clearSwipeAlternatives()
                                    onMoveCursor(steps)
                                    accumulatedDrag -= steps * dragThreshold
                                }
                            }
                            val spaceKey: @Composable RowScope.(KeyModel) -> Unit = { key ->
                                Box(
                                    modifier = Modifier
                                        .weight(key.weight)
                                        .draggable(
                                            orientation = Orientation.Horizontal,
                                            state = dragState,
                                            onDragStopped = { accumulatedDrag = 0f }
                                        )
                                ) {
                                    KeyboardKey(
                                        key = key,
                                        mode = mode,
                                        imeAction = imeAction,
                                        hapticEnabled = settings.hapticFeedback,
                                        fontScale = settings.fontScale,
                                        showKeyPopups = settings.showKeyPopups,
                                        onKeyPress = stableKeyPress,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            val keyRow: @Composable (KeyRowSpec) -> Unit = { spec ->
                                KeyRow(
                                    spec = spec,
                                    mode = mode,
                                    imeAction = imeAction,
                                    settings = settings,
                                    geometry = geometry,
                                    onKeyPress = stableKeyPress,
                                    onKeyLongPress = stableKeyLongPress,
                                    onPopupSelected = stablePopup,
                                    spaceKey = spaceKey
                                )
                            }

                            // Number row if enabled
                            AnimatedVisibility(
                                visible = (settings.showNumberRow || isOtpField) && isAlphaMode,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                keyRow(KeyRowSpec(KeyboardLayouts.numberRow, numberRowHeight, 2.dp))
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
                                    KeyboardLayouts.getQwertyRow1(settings.showNumberRow || isOtpField),
                                    KeyboardLayouts.qwertyRow2,
                                    KeyboardLayouts.qwertyRow3,
                                    KeyboardLayouts.qwertyBottomRowFor(fieldInputKind, showLanguageSwitch)
                                )
                            }

                            keyRow(KeyRowSpec(row1, rowHeight, keyRowHorizontalPadding))
                            // Alpha rows stagger the home row in from both edges.
                            keyRow(KeyRowSpec(row2, rowHeight, keyRowHorizontalPadding, sideInset = if (isAlphaMode) 8.dp else 0.dp))
                            keyRow(KeyRowSpec(row3, rowHeight, keyRowHorizontalPadding))
                            keyRow(KeyRowSpec(bottomRow, rowHeight, keyRowHorizontalPadding))
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
    rowHeight: Dp,
    onKeyPress: (KeyType) -> Unit,
    onKeyLongPress: (KeyType) -> Unit,
    onPopupSelected: (String) -> Unit
) {
    val rows = listOf(
        KeyboardLayouts.getQwertyRow1(showNumberRow = true),
        KeyboardLayouts.qwertyRow2,
        KeyboardLayouts.qwertyRow3,
        KeyboardLayouts.emojiSearchBottomRow
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, keys ->
            KeyRow(
                spec = KeyRowSpec(keys, rowHeight, 2.dp, sideInset = if (index == 1) 8.dp else 0.dp),
                mode = KeyboardMode.LOWERCASE,
                imeAction = imeAction,
                settings = settings,
                geometry = null,
                onKeyPress = onKeyPress,
                onKeyLongPress = onKeyLongPress,
                onPopupSelected = onPopupSelected
            )
        }
    }
}

/** One row of keys: its keys, height, horizontal padding and stagger inset. */
private data class KeyRowSpec(
    val keys: List<KeyModel>,
    val height: Dp,
    val horizontalPadding: Dp,
    val sideInset: Dp = 0.dp
)

/**
 * A row of [KeyboardKey]s. Letter keys report their cells to [geometry] for
 * swipe typing; [spaceKey], when given, renders the space bar instead of the
 * default key (the main layout uses it for the cursor-drag trackpad).
 */
@Composable
private fun KeyRow(
    spec: KeyRowSpec,
    mode: KeyboardMode,
    imeAction: Int,
    settings: KeyboardSettings,
    geometry: SwipeKeyGeometry?,
    onKeyPress: (KeyType) -> Unit,
    onKeyLongPress: (KeyType) -> Unit,
    onPopupSelected: (String) -> Unit,
    spaceKey: (@Composable RowScope.(KeyModel) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(spec.height)
            .padding(horizontal = spec.horizontalPadding, vertical = 1.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        if (spec.sideInset > 0.dp) Spacer(modifier = Modifier.width(spec.sideInset))
        spec.keys.forEach { key ->
            if (key.type is KeyType.Space && spaceKey != null) {
                spaceKey(key)
            } else {
                KeyboardKey(
                    key = key,
                    mode = mode,
                    imeAction = imeAction,
                    hapticEnabled = settings.hapticFeedback,
                    fontScale = settings.fontScale,
                    showKeyPopups = settings.showKeyPopups,
                    onKeyPress = onKeyPress,
                    onKeyLongPress = onKeyLongPress,
                    onPopupSelected = onPopupSelected,
                    modifier = Modifier
                        .weight(key.weight)
                        .let { if (geometry != null) it.trackLetterKey(key, geometry) else it }
                )
            }
        }
        if (spec.sideInset > 0.dp) Spacer(modifier = Modifier.width(spec.sideInset))
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

