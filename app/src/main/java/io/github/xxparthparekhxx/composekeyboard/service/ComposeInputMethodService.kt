package io.github.xxparthparekhxx.composekeyboard.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.inline.InlinePresentationSpec
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.xxparthparekhxx.composekeyboard.MainActivity
import io.github.xxparthparekhxx.composekeyboard.data.Capitalization
import io.github.xxparthparekhxx.composekeyboard.data.ClipboardHistoryManager
import io.github.xxparthparekhxx.composekeyboard.data.FieldInputKind
import io.github.xxparthparekhxx.composekeyboard.data.GraphemeClusters
import io.github.xxparthparekhxx.composekeyboard.data.EmojiCatalog
import io.github.xxparthparekhxx.composekeyboard.data.OtpCodes
import io.github.xxparthparekhxx.composekeyboard.data.SelectionTracker
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardPreferences
import io.github.xxparthparekhxx.composekeyboard.data.SwipeDictionary
import io.github.xxparthparekhxx.composekeyboard.input.swipe.SwipeConstants
import io.github.xxparthparekhxx.composekeyboard.input.swipe.nn.SwipeNeuralDecoder
import io.github.xxparthparekhxx.composekeyboard.input.voice.VoiceInputController
import io.github.xxparthparekhxx.composekeyboard.data.InlineChipSpec
import io.github.xxparthparekhxx.composekeyboard.ui.keyboard.KeyboardScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ComposeInputMethodService : InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private lateinit var preferences: KeyboardPreferences
    private lateinit var clipboardHistoryManager: ClipboardHistoryManager
    internal lateinit var swipeDictionary: SwipeDictionary

    /**
     * The neural swipe decoder, once its weights and lexicon trie are built.
     *
     * Exposed as a flow because loading is asynchronous and the keyboard may
     * well be on screen before it finishes. Until then [SwipeController] decodes
     * with the geometric fallback, so early gestures still produce words.
     */
    private val neuralDecoder = MutableStateFlow<SwipeNeuralDecoder?>(null)
    private var audioManager: AudioManager? = null
    internal var currentImeAction by mutableIntStateOf(EditorInfo.IME_ACTION_UNSPECIFIED)
        private set

    /**
     * Bumped for every input session so the keyboard UI can reset per-field
     * state (suggestions, typed prefix) even when the values themselves are
     * unchanged. Auto-shift is not keyed off this — it comes from the caret.
     */
    private var inputSession by mutableIntStateOf(0)

    /** Whether the focused field's input type is allowed to auto-shift. */
    private var fieldAllowsCaps by mutableStateOf(false)

    /**
     * Whether the caret is currently at a position that should auto-shift
     * (sentence start, word start, or characters mode). Derived from the
     * editor's text, so it survives dismiss and caret moves.
     */
    private var cursorWantsShift by mutableStateOf(false)

    /** Layout the focused field asked for (text, email, phone, number, …). */
    private var fieldInputKind by mutableStateOf(FieldInputKind.TEXT)
    private var fieldAllowsSuggestions by mutableStateOf(true)
    private var isOtpField by mutableStateOf(false)
    private var isIncognito by mutableStateOf(false)
    private var otpCode by mutableStateOf<String?>(null)
    private var inlineSuggestions by mutableStateOf<List<InlineSuggestion>>(emptyList())
    private var showLanguageSwitch by mutableStateOf(false)

    /**
     * Bumped whenever the caret moves for a reason other than our own edits.
     * The UI keys its typed prefix and suggestions off it: a completion picked
     * after the caret moved would otherwise delete text at the new position.
     */
    private var externalCaretMoves by mutableIntStateOf(0)
    private var inputViewVisible = false

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var dictionarySaveJob: Job? = null

    /** The word the last gesture put in, while it is still the text at the caret. */
    private data class SwipeCommit(val word: String, val precededBySpace: Boolean)

    private var lastSwipeCommit: SwipeCommit? = null

    /**
     * Predicts where our own edits leave the caret, so `onUpdateSelection`
     * can tell their echoes from the user moving it — which invalidates the
     * gesture state at the cursor. See [SelectionTracker].
     */
    private val selection = SelectionTracker()

    /** Letters tapped since the last word boundary, for dictionary learning. */
    private val typedWord = StringBuilder()

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        preferences = KeyboardPreferences.getInstance(this)
        clipboardHistoryManager = ClipboardHistoryManager.getInstance(this)
        swipeDictionary = SwipeDictionary.getInstance(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        // ~150k words off the main thread; gestures simply decode to nothing
        // until it lands, which takes a fraction of the time it takes the user
        // to focus a field and start swiping.
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                swipeDictionary.load()
                EmojiCatalog.preload(this@ComposeInputMethodService)
            }
            // The trie is built from the dictionary, so this has to follow it.
            neuralDecoder.value = withContext(Dispatchers.IO) {
                SwipeNeuralDecoder.load(this@ComposeInputMethodService, swipeDictionary)
            }
        }
    }

    override fun onCreateInputView(): View {
        val composeView = ComposeView(this)

        window?.window?.decorView?.let { decorView ->
            decorView.setViewTreeLifecycleOwner(this)
            decorView.setViewTreeViewModelStoreOwner(this)
            decorView.setViewTreeSavedStateRegistryOwner(this)
        }

        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeViewModelStoreOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)

        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycle)
        )

        composeView.setContent {
            val settings by preferences.settings.collectAsState()
            val neural by neuralDecoder.collectAsState()

            KeyboardScreen(
                settings = settings,
                clipboardManager = clipboardHistoryManager,
                swipeDictionary = swipeDictionary,
                neuralDecoder = neural,
                imeAction = currentImeAction,
                inputSession = inputSession,
                externalCaretMoves = externalCaretMoves,
                cursorWantsShift = cursorWantsShift,
                fieldInputKind = fieldInputKind,
                fieldAllowsSuggestions = fieldAllowsSuggestions,
                isOtpField = isOtpField,
                isIncognito = isIncognito,
                otpCode = otpCode,
                inlineSuggestions = inlineSuggestions,
                showLanguageSwitch = showLanguageSwitch,
                onTextInput = { text ->
                    playKeySound(AudioManager.FX_KEYPRESS_STANDARD)
                    handleTextInput(text)
                },
                onDelete = {
                    playKeySound(AudioManager.FX_KEYPRESS_DELETE)
                    handleDelete()
                },
                onAction = { actionId ->
                    playKeySound(AudioManager.FX_KEYPRESS_RETURN)
                    handleAction(actionId)
                },
                onMoveCursor = { offset ->
                    lastSwipeCommit = null
                    typedWord.setLength(0)
                    moveCursor(offset)
                },
                onSwipeWord = { word ->
                    playKeySound(AudioManager.FX_KEYPRESS_SPACEBAR)
                    commitSwipeWord(word)
                },
                onSwipeWordReplaced = { word ->
                    replaceSwipeWord(word)
                },
                onAutocompleteSelected = { word, prefix ->
                    playKeySound(AudioManager.FX_KEYPRESS_SPACEBAR)
                    commitAutocomplete(word, prefix)
                },
                onThemeChanged = { theme ->
                    preferences.setTheme(theme)
                },
                onHapticToggled = { enabled ->
                    preferences.setHapticFeedback(enabled)
                },
                onSoundToggled = { enabled ->
                    preferences.setSoundFeedback(enabled)
                },
                onNumberRowToggled = { enabled ->
                    preferences.setShowNumberRow(enabled)
                },
                onAutoCapsToggled = { enabled ->
                    preferences.setAutoCapitalization(enabled)
                    refreshCursorCaps()
                },
                onSwipeTypingToggled = { enabled ->
                    preferences.setSwipeTypingEnabled(enabled)
                },
                onHeightMultiplierChanged = { multiplier ->
                    preferences.setHeightMultiplier(multiplier)
                },
                onFontScaleChanged = { scale ->
                    preferences.setFontScale(scale)
                },
                onEmojiScaleChanged = { scale ->
                    preferences.setEmojiScale(scale)
                },
                onOpenFullSettings = {
                    launchSettingsActivity()
                },
                onRequestMicPermission = {
                    launchSettingsActivity(requestMic = true)
                },
                onOtpSelected = { code ->
                    playKeySound(AudioManager.FX_KEYPRESS_STANDARD)
                    val ic = currentInputConnection ?: return@KeyboardScreen
                    ic.commitText(code, 1)
                    selection.onCommitText(code.length)
                    otpCode = null
                    inlineSuggestions = emptyList()
                },
                onSwitchIme = { switchIme() }
            )
        }

        return composeView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        enterInputViewLifecycle()

        resetInputState()
        selection.reset(info?.initialSelStart ?: -1, info?.initialSelEnd ?: -1)

        clipboardHistoryManager.captureCurrentClip()

        info?.let {
            val action = it.imeOptions and EditorInfo.IME_MASK_ACTION
            // Multi-line EditTexts get IME_ACTION_DONE/NEXT *plus* this flag,
            // meaning "Enter inserts a newline". Honouring the action there
            // would make newlines impossible to type in notes, chats, etc.
            val noEnterAction = it.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
            currentImeAction = if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                action
            } else {
                EditorInfo.IME_ACTION_UNSPECIFIED
            }
            isOtpField = OtpCodes.isOtpField(it)
            isIncognito = it.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0
            fieldAllowsCaps = Capitalization.fieldAllowsAutoCaps(it)
            fieldInputKind = FieldInputKind.from(it.inputType, isOtp = isOtpField)
            fieldAllowsSuggestions = FieldInputKind.allowsWordSuggestions(it.inputType, fieldInputKind)
            otpCode = if (isOtpField) {
                OtpCodes.fromClipboardText(clipboardHistoryManager.peekPlainText())
            } else {
                null
            }
        } ?: run {
            fieldAllowsCaps = false
            fieldInputKind = FieldInputKind.TEXT
            fieldAllowsSuggestions = true
            isOtpField = false
            isIncognito = false
            otpCode = null
        }
        showLanguageSwitch = if (Build.VERSION.SDK_INT >= 28) {
            shouldOfferSwitchingToNextInputMethod()
        } else {
            true
        }
        if (!restarting) {
            inlineSuggestions = emptyList()
        }
        refreshCursorCaps()
        inputSession++
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        VoiceInputController.getInstance(this).cancelRecording()
        flushTypedWord()
        resetInputState()
        saveLearnedWordsNow()
        leaveInputViewLifecycle()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        VoiceInputController.getInstance(this).cancelRecording()
        flushTypedWord()
        resetInputState()
        saveLearnedWordsNow()
        leaveInputViewLifecycle()
    }

    /**
     * Never use fullscreen extract mode. A Compose IME has no extract view, so
     * in landscape the system would otherwise replace the keyboard with a
     * blank fullscreen editor — a well-known breakage. Rendering the keyboard
     * itself in the (shorter) landscape window is always the better trade.
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    @androidx.annotation.RequiresApi(30)
    override fun onCreateInlineSuggestionsRequest(uiExtras: Bundle): InlineSuggestionsRequest? {
        // Spec sizes are in pixels and InlineSuggestion.inflate() rejects any
        // size outside them, so they must match the chip's dp height here.
        //
        // No custom style: forwarding `uiExtras` here pins the chip to a style
        // version the autofill renderer must know, and on devices where the
        // renderer (Google ExtServices) lags the framework it fails with
        // "Cannot find a style with the same version as the slice" and the
        // chip never renders. Omitting setStyle falls back to the platform's
        // default inline chip style, which is what standard OTP chips use.
        val density = resources.displayMetrics.density
        val chipHeight = (InlineChipSpec.HEIGHT_DP * density).toInt()
        val spec = InlinePresentationSpec.Builder(
            Size((InlineChipSpec.MIN_WIDTH_DP * density).toInt(), chipHeight),
            Size((InlineChipSpec.MAX_WIDTH_DP * density).toInt(), chipHeight)
        )
            .build()
        return InlineSuggestionsRequest.Builder(listOf(spec, spec, spec, spec, spec, spec))
            .setMaxSuggestionCount(6)
            .build()
    }

    @androidx.annotation.RequiresApi(30)
    override fun onInlineSuggestionsResponse(response: InlineSuggestionsResponse): Boolean {
        inlineSuggestions = response.inlineSuggestions
        return true
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd
        )
        // Caps follows the caret, including after our own commits and after
        // the user taps into the middle of a sentence.
        refreshCursorCaps()
        if (selection.onUpdate(oldSelStart, oldSelEnd, newSelStart, newSelEnd)) return
        // The caret moved on its own — the user tapped elsewhere, or the app
        // rewrote the field. Whatever the last gesture put in is no longer
        // guaranteed to be sitting at the cursor, so neither whole-word
        // backspace nor suggestion swapping can be trusted any more.
        resetInputState()
        externalCaretMoves++
    }

    override fun onDestroy() {
        super.onDestroy()
        dictionarySaveJob?.cancel()
        VoiceInputController.getInstance(this).release()
        // Detached on purpose: the service scope is about to be cancelled and
        // this last write must still land.
        CoroutineScope(Dispatchers.IO).launch { swipeDictionary.persistLearnedWords() }
        serviceScope.cancel()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    // --- Swipe typing -------------------------------------------------------

    /**
     * Commits a decoded word, inserting the separating space itself when the
     * caret is sitting right after other text. Whether that space was added is
     * remembered, so backspacing the word takes the space with it.
     */
    internal fun commitSwipeWord(word: String) {
        // Decoding finishes a beat after lift-off; if the keyboard went away
        // in between, the word belongs to a field that no longer has focus.
        if (!inputViewVisible) return
        val ic = currentInputConnection ?: return
        typedWord.setLength(0)

        val before = ic.getTextBeforeCursor(1, 0)
        val needsSpace = !before.isNullOrEmpty() && !opensAWord(before[0])

        ic.beginBatchEdit()
        ic.commitText(if (needsSpace) " $word" else word, 1)
        ic.endBatchEdit()
        selection.onCommitText(word.length + if (needsSpace) 1 else 0)

        lastSwipeCommit = SwipeCommit(word, needsSpace)
    }

    /** Swaps the word the last gesture committed for one the user picked instead. */
    internal fun replaceSwipeWord(word: String) {
        val ic = currentInputConnection ?: return
        val previous = lastSwipeCommit ?: return

        ic.beginBatchEdit()
        ic.deleteSurroundingText(previous.word.length, 0)
        ic.commitText(word, 1)
        ic.endBatchEdit()
        selection.onDeleteBefore(previous.word.length)
        selection.onCommitText(word.length)

        lastSwipeCommit = previous.copy(word = word)

        // The user overruled the decoder, which is the strongest signal we get
        // about what they meant. Weight it straight away if the field allows learning.
        if (shouldLearnFromField(currentInputEditorInfo)) {
            learnWord(word)
        }
    }

    /**
     * Commits a tapped autocompletion word, replacing the typed prefix and adding a trailing space.
     */
    internal fun commitAutocomplete(word: String, prefix: String) {
        val ic = currentInputConnection ?: return
        val deleteLen = prefix.length
        lastSwipeCommit = null

        // Only replace the prefix if it really is what sits before the caret.
        // Raw key editors (terminals) report no text, so they are trusted.
        if (deleteLen > 0 && !FieldInputKind.isRawKeyEditor(currentInputEditorInfo?.inputType ?: 0)) {
            val before = ic.getTextBeforeCursor(deleteLen, 0)
            if (before != null && !before.toString().equals(prefix, ignoreCase = true)) {
                Log.w(TAG, "Stale completion prefix; ignoring suggestion tap")
                typedWord.setLength(0)
                return
            }
        }

        ic.beginBatchEdit()
        if (deleteLen > 0) {
            ic.deleteSurroundingText(deleteLen, 0)
        }
        ic.commitText("$word ", 1)
        ic.endBatchEdit()
        selection.onDeleteBefore(deleteLen)
        selection.onCommitText(word.length + 1)

        if (shouldLearnFromField(currentInputEditorInfo)) {
            learnWord(word)
        }
        typedWord.setLength(0)
    }

    internal fun handleTextInput(text: String) {
        val ic = currentInputConnection ?: return
        val wasSwipe = lastSwipeCommit != null
        lastSwipeCommit = null
        val isPunctuation = text.length == 1 && text[0] in ".,!?:;)]}\"'-"
        val isSpace = text == " "

        if (wasSwipe && !isPunctuation && !isSpace) {
            val before = ic.getTextBeforeCursor(1, 0)
            val needsSpace = !before.isNullOrEmpty() && !opensAWord(before[0])
            if (needsSpace) {
                ic.beginBatchEdit()
                ic.commitText(" $text", 1)
                ic.endBatchEdit()
                selection.onCommitText(text.length + 1)
                trackTypedText(text)
                return
            }
        }

        ic.commitText(text, 1)
        selection.onCommitText(text.length)
        trackTypedText(text)
    }

    internal fun handleAction(actionId: Int) {
        flushTypedWord()
        lastSwipeCommit = null
        handleEditorAction(actionId)
    }

    internal fun handleDelete() {
        val ic = currentInputConnection ?: return
        val swipe = lastSwipeCommit
        if (swipe != null) {
            // The first backspace after a gesture takes the whole word, along
            // with the space that was inserted to separate it.
            val length = swipe.word.length + if (swipe.precededBySpace) 1 else 0
            ic.deleteSurroundingText(length, 0)
            selection.onDeleteBefore(length)
            lastSwipeCommit = null
            typedWord.setLength(0)
            return
        }

        if (typedWord.isNotEmpty()) typedWord.setLength(typedWord.length - 1)

        try {
            val selectedText = ic.getSelectedText(0)
            if (!selectedText.isNullOrEmpty()) {
                ic.commitText("", 1)
                selection.onCommitText(0)
            } else {
                deleteLastGrapheme(ic)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Delete failed in the editor; falling back to KEYCODE_DEL", e)
            sendKeyEdit(KeyEvent.KEYCODE_DEL)
        }
    }

    /**
     * Deletes one user-visible character before the caret. Emoji (surrogate
     * pairs, ZWJ sequences, flags, skin tones) are a single cluster, so a
     * one-unit [android.view.inputmethod.InputConnection.deleteSurroundingText]
     * would split them.
     *
     * Never asks the editor to delete "1 code point" when the caret is at the
     * start: Compose [androidx.compose.foundation.text.BasicTextField] throws
     * `start cannot be negative` and takes down the host (this app, in the
     * companion playground).
     *
     */
    private fun deleteLastGrapheme(ic: InputConnection) {
        // Terminals (Termux and friends) have no text buffer to inspect, so
        // the text before the caret always looks empty. They only understand
        // key events.
        val info = currentInputEditorInfo
        if (info != null && FieldInputKind.isRawKeyEditor(info.inputType)) {
            sendKeyEdit(KeyEvent.KEYCODE_DEL)
            return
        }
        val before = ic.getTextBeforeCursor(GRAPHEME_LOOKBACK, 0)
        val units = GraphemeClusters.utf16LengthOfLastCluster(before ?: "")
        if (units > 0) {
            ic.deleteSurroundingText(units, 0)
            selection.onDeleteBefore(units)
            return
        }
        // Unknown surrounding text (some editors return null), or nothing
        // before the caret. KEYCODE_DEL is a no-op on an empty field instead
        // of crashing Compose, and some apps react to it there (split OTP
        // boxes moving back, recipient chips being removed).
        sendKeyEdit(KeyEvent.KEYCODE_DEL)
    }

    /**
     * Sends a key the editor interprets itself. Where the caret lands is up
     * to the editor (Enter may run an action, DPAD may stop at a line end),
     * so the next selection update is adopted as ours.
     */
    private fun sendKeyEdit(keyCode: Int) {
        selection.invalidate()
        sendDownUpKeyEvents(keyCode)
    }

    /** True for characters a new word can follow without a space of its own. */
    private fun opensAWord(c: Char): Boolean =
        c.isWhitespace() || c in "([{<\"'“‘–—-/@#*"

    private fun trackTypedText(text: String) {
        if (!shouldLearnFromField(currentInputEditorInfo)) {
            if (typedWord.isNotEmpty()) typedWord.setLength(0)
            return
        }
        if (text.length == 1) {
            val c = text[0]
            if (c.isLetter() || c == '\'') {
                typedWord.append(c)
                return
            }
        }
        flushTypedWord()
    }

    /**
     * Ends the run of tapped letters and offers it to the dictionary. Words the
     * user types by hand are exactly the ones the shipped list is missing —
     * names, slang, jargon — so learning them is what makes gestures work on
     * their own vocabulary.
     */
    private fun flushTypedWord() {
        if (typedWord.isEmpty()) return
        val word = typedWord.toString()
        typedWord.setLength(0)
        if (!shouldLearnFromField(currentInputEditorInfo)) return
        if (SwipeDictionary.normalize(word) == null) return
        learnWord(word)
    }

    /**
     * Offers a word to the dictionary and pushes an in-lexicon score bump
     * straight into the live beam.
     *
     * [SwipeDictionary.learn] returns the word's new score when it was already
     * part of the lexicon; forwarding that to
     * [SwipeNeuralDecoder.bumpScore] is an O(1) map write on the calling
     * thread, so a reused word wins its ranking boost immediately instead of
     * waiting for the next genuinely-new word to trigger a ~70 MB trie
     * rebuild. A null return means the word is new (or not yet promoted) and
     * only the debounced [updateBeam] path can reflect it.
     */
    private fun learnWord(rawWord: String) {
        val newScore = swipeDictionary.learn(rawWord)
        if (newScore != null) {
            val normalized = SwipeDictionary.normalize(rawWord)
            if (normalized != null) {
                neuralDecoder.value?.bumpScore(normalized, newScore)
            }
        }
        scheduleLearnedWordSave()
    }

    private fun scheduleLearnedWordSave() {
        dictionarySaveJob?.cancel()
        dictionarySaveJob = serviceScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            withContext(Dispatchers.IO) {
                swipeDictionary.persistLearnedWords()
                neuralDecoder.value?.updateBeam(swipeDictionary)
            }
        }
    }

    private fun saveLearnedWordsNow() {
        dictionarySaveJob?.cancel()
        dictionarySaveJob = null
        serviceScope.launch {
            withContext(Dispatchers.IO) {
                swipeDictionary.persistLearnedWords()
                neuralDecoder.value?.updateBeam(swipeDictionary)
            }
        }
    }

    private fun resetInputState() {
        lastSwipeCommit = null
        typedWord.setLength(0)
    }

    // --- Editing helpers ----------------------------------------------------

    internal fun handleEditorAction(actionId: Int) {
        val ic = currentInputConnection ?: return
        if (actionId != EditorInfo.IME_ACTION_UNSPECIFIED && actionId != EditorInfo.IME_ACTION_NONE) {
            ic.performEditorAction(actionId)
        } else {
            sendKeyEdit(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun moveCursor(offset: Int) {
        val ic = currentInputConnection ?: return
        if (offset > 0) {
            for (i in 0 until offset) {
                sendKeyEdit(KeyEvent.KEYCODE_DPAD_RIGHT)
            }
        } else if (offset < 0) {
            for (i in 0 until -offset) {
                sendKeyEdit(KeyEvent.KEYCODE_DPAD_LEFT)
            }
        }
    }

    private fun launchSettingsActivity(requestMic: Boolean = false) {
        try {
            requestHideSelf(0)
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                if (requestMic) {
                    putExtra(MainActivity.EXTRA_REQUEST_MIC, true)
                }
            }
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val pendingIntent = PendingIntent.getActivity(
                this,
                if (requestMic) 1 else 0,
                intent,
                flags
            )
            pendingIntent.send()
        } catch (e: Exception) {
            Log.w(TAG, "PendingIntent launch failed; falling back to startActivity", e)
            try {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (requestMic) {
                        putExtra(MainActivity.EXTRA_REQUEST_MIC, true)
                    }
                }
                startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Could not launch settings activity", e2)
            }
        }
    }

    private fun playKeySound(effectType: Int) {
        if (preferences.settings.value.soundFeedback) {
            audioManager?.playSoundEffect(effectType, 1.0f)
        }
    }

    /**
     * True when the focused field allows personalized dictionary learning.
     * Returns false for password fields, terminals ([FieldInputKind.isRawKeyEditor]),
     * fields requesting [EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING], or when [info] is null.
     */
    private fun shouldLearnFromField(info: EditorInfo?): Boolean {
        if (info == null) return false
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) {
            return false
        }
        val inputType = info.inputType
        // Terminals: everything typed goes to a shell, including sudo/ssh
        // passwords, which the editor has no way to mark as such.
        if (FieldInputKind.isRawKeyEditor(inputType)) return false
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        if (inputClass == InputType.TYPE_CLASS_TEXT) {
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            ) {
                return false
            }
        } else if (inputClass == InputType.TYPE_CLASS_NUMBER) {
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
                return false
            }
        }
        return true
    }

    private fun enterInputViewLifecycle() {
        if (inputViewVisible) return
        if (lifecycleRegistry.currentState < Lifecycle.State.STARTED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }
        if (lifecycleRegistry.currentState < Lifecycle.State.RESUMED) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        inputViewVisible = true
    }

    private fun leaveInputViewLifecycle() {
        if (!inputViewVisible) return
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        }
        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }
        inputViewVisible = false
    }

    private fun switchIme() {
        if (Build.VERSION.SDK_INT >= 28) {
            switchToNextInputMethod(false)
            return
        }
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val token = window?.window?.attributes?.token
        if (imm != null && token != null) {
            @Suppress("DEPRECATION")
            imm.switchToNextInputMethod(token, false)
        }
    }

    /**
     * Re-reads auto-shift from the editor's caret. [android.view.inputmethod.InputConnection.getCursorCapsMode]
     * looks at the actual surrounding text, so a dismissed-and-reshown keyboard
     * mid-sentence stays lowercase, and a caret after ". " comes back in shift.
     */
    private fun refreshCursorCaps() {
        val info = currentInputEditorInfo
        val capsMode = try {
            val ic = currentInputConnection
            when {
                info == null -> 0
                ic != null -> ic.getCursorCapsMode(info.inputType)
                else -> info.initialCapsMode
            }
        } catch (_: Exception) {
            info?.initialCapsMode ?: 0
        }
        cursorWantsShift = Capitalization.wantsShift(
            autoCapsEnabled = preferences.settings.value.autoCapitalization,
            fieldAllowsCaps = fieldAllowsCaps,
            cursorCapsMode = capsMode
        )
    }

    private companion object {
        private const val TAG = "ComposeKeyboard"
        const val SAVE_DEBOUNCE_MS = SwipeConstants.SAVE_DEBOUNCE_MS
        /** Long enough for family ZWJ sequences and subdivision-flag tags. */
        private const val GRAPHEME_LOOKBACK = 64
    }
}
