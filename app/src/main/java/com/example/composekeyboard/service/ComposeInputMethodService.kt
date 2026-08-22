package com.example.composekeyboard.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
import com.example.composekeyboard.MainActivity
import com.example.composekeyboard.data.ClipboardHistoryManager
import com.example.composekeyboard.data.KeyboardPreferences
import com.example.composekeyboard.data.SwipeDictionary
import com.example.composekeyboard.input.swipe.SwipeConstants
import com.example.composekeyboard.input.swipe.nn.SwipeNeuralDecoder
import com.example.composekeyboard.ui.keyboard.KeyboardScreen
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
    private lateinit var swipeDictionary: SwipeDictionary

    /**
     * The neural swipe decoder, once its weights and lexicon trie are built.
     *
     * Exposed as a flow because loading is asynchronous and the keyboard may
     * well be on screen before it finishes. Until then [SwipeController] decodes
     * with the geometric fallback, so early gestures still produce words.
     */
    private val neuralDecoder = MutableStateFlow<SwipeNeuralDecoder?>(null)
    private var audioManager: AudioManager? = null
    private var currentImeAction by mutableIntStateOf(EditorInfo.IME_ACTION_UNSPECIFIED)

    /**
     * Bumped for every input session so the keyboard UI can reset per-field
     * state (like auto-capitalization) even when the value itself is unchanged.
     */
    private var inputSession by mutableIntStateOf(0)

    /** Whether the focused field's input type asks for sentence-style capitals. */
    private var fieldWantsCaps by mutableStateOf(false)

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var dictionarySaveJob: Job? = null

    /** The word the last gesture put in, while it is still the text at the caret. */
    private data class SwipeCommit(val word: String, val precededBySpace: Boolean)

    private var lastSwipeCommit: SwipeCommit? = null

    /**
     * Edits we made ourselves, awaiting their `onUpdateSelection` echo. Anything
     * left over is the user moving the caret, which invalidates the gesture
     * state at the cursor.
     */
    private var selfEditsPending = 0

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
            withContext(Dispatchers.IO) { swipeDictionary.load() }
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
                autoCapitalizeField = fieldWantsCaps,
                onTextInput = { text ->
                    playKeySound(AudioManager.FX_KEYPRESS_STANDARD)
                    lastSwipeCommit = null
                    currentInputConnection?.commitText(text, 1)
                    selfEditsPending++
                    trackTypedText(text)
                },
                onDelete = {
                    playKeySound(AudioManager.FX_KEYPRESS_DELETE)
                    handleDelete()
                },
                onAction = { actionId ->
                    playKeySound(AudioManager.FX_KEYPRESS_RETURN)
                    flushTypedWord()
                    lastSwipeCommit = null
                    handleEditorAction(actionId)
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
                },
                onSwipeTypingToggled = { enabled ->
                    preferences.setSwipeTypingEnabled(enabled)
                },
                onOpenFullSettings = {
                    launchSettingsActivity()
                },
                onSwitchIme = {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showInputMethodPicker()
                }
            )
        }

        return composeView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        resetInputState()

        // Capture newly copied items when keyboard opens
        clipboardHistoryManager.captureCurrentClip()

        info?.let {
            val action = it.imeOptions and EditorInfo.IME_MASK_ACTION
            currentImeAction = if (action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                action
            } else {
                EditorInfo.IME_ACTION_UNSPECIFIED
            }
            fieldWantsCaps = fieldRequestsCapitalization(it)
        }
        inputSession++
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        flushTypedWord()
        resetInputState()
        saveLearnedWordsNow()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
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
        if (selfEditsPending > 0) {
            selfEditsPending--
            return
        }
        // The caret moved on its own — the user tapped elsewhere, or the app
        // rewrote the field. Whatever the last gesture put in is no longer
        // guaranteed to be sitting at the cursor, so neither whole-word
        // backspace nor suggestion swapping can be trusted any more.
        resetInputState()
    }

    override fun onDestroy() {
        super.onDestroy()
        dictionarySaveJob?.cancel()
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
    private fun commitSwipeWord(word: String) {
        val ic = currentInputConnection ?: return
        typedWord.setLength(0)

        val before = ic.getTextBeforeCursor(1, 0)
        val needsSpace = !before.isNullOrEmpty() && !opensAWord(before[0])

        ic.beginBatchEdit()
        ic.commitText(if (needsSpace) " $word" else word, 1)
        ic.endBatchEdit()
        selfEditsPending++

        lastSwipeCommit = SwipeCommit(word, needsSpace)
    }

    /** Swaps the word the last gesture committed for one the user picked instead. */
    private fun replaceSwipeWord(word: String) {
        val ic = currentInputConnection ?: return
        val previous = lastSwipeCommit ?: return

        ic.beginBatchEdit()
        ic.deleteSurroundingText(previous.word.length, 0)
        ic.commitText(word, 1)
        ic.endBatchEdit()
        selfEditsPending++

        lastSwipeCommit = previous.copy(word = word)

        // The user overruled the decoder, which is the strongest signal we get
        // about what they meant. Weight it straight away.
        swipeDictionary.learn(word)
        scheduleLearnedWordSave()
    }

    /**
     * Commits a tapped autocompletion word, replacing the typed prefix and adding a trailing space.
     */
    private fun commitAutocomplete(word: String, prefix: String) {
        val ic = currentInputConnection ?: return
        val deleteLen = prefix.length
        lastSwipeCommit = null

        ic.beginBatchEdit()
        if (deleteLen > 0) {
            ic.deleteSurroundingText(deleteLen, 0)
        }
        ic.commitText("$word ", 1)
        ic.endBatchEdit()
        selfEditsPending++

        swipeDictionary.learn(word)
        scheduleLearnedWordSave()
        typedWord.setLength(0)
    }

    private fun handleDelete() {
        val ic = currentInputConnection ?: return
        val swipe = lastSwipeCommit
        if (swipe != null) {
            // The first backspace after a gesture takes the whole word, along
            // with the space that was inserted to separate it.
            ic.deleteSurroundingText(
                swipe.word.length + if (swipe.precededBySpace) 1 else 0,
                0
            )
            selfEditsPending++
            lastSwipeCommit = null
            typedWord.setLength(0)
            return
        }

        if (typedWord.isNotEmpty()) typedWord.setLength(typedWord.length - 1)

        val selectedText = ic.getSelectedText(0)
        if (selectedText.isNullOrEmpty()) {
            ic.deleteSurroundingText(1, 0)
        } else {
            ic.commitText("", 1)
        }
        selfEditsPending++
    }

    /** True for characters a new word can follow without a space of its own. */
    private fun opensAWord(c: Char): Boolean =
        c.isWhitespace() || c in "([{<\"'“‘–—-/@#*"

    private fun trackTypedText(text: String) {
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
        if (SwipeDictionary.normalize(word) == null) return
        swipeDictionary.learn(word)
        scheduleLearnedWordSave()
    }

    private fun scheduleLearnedWordSave() {
        dictionarySaveJob?.cancel()
        dictionarySaveJob = serviceScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            withContext(Dispatchers.IO) { swipeDictionary.persistLearnedWords() }
        }
    }

    private fun saveLearnedWordsNow() {
        dictionarySaveJob?.cancel()
        dictionarySaveJob = null
        serviceScope.launch {
            withContext(Dispatchers.IO) { swipeDictionary.persistLearnedWords() }
        }
    }

    private fun resetInputState() {
        lastSwipeCommit = null
        typedWord.setLength(0)
        selfEditsPending = 0
    }

    // --- Editing helpers ----------------------------------------------------

    private fun handleEditorAction(actionId: Int) {
        val ic = currentInputConnection ?: return
        if (actionId != EditorInfo.IME_ACTION_UNSPECIFIED && actionId != EditorInfo.IME_ACTION_NONE) {
            ic.performEditorAction(actionId)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun moveCursor(offset: Int) {
        val ic = currentInputConnection ?: return
        if (offset > 0) {
            for (i in 0 until offset) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
            }
        } else if (offset < 0) {
            for (i in 0 until -offset) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
            }
        }
    }

    private fun launchSettingsActivity() {
        try {
            requestHideSelf(0)
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
            pendingIntent.send()
        } catch (e: Exception) {
            Log.w(TAG, "PendingIntent launch failed; falling back to startActivity", e)
            try {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
     * True when the focused field's [EditorInfo.inputType] asks for capitals at
     * the start of sentences (the usual case for prose fields). Password, URI
     * and e-mail variations are excluded — capitalizing those is never wanted.
     */
    private fun fieldRequestsCapitalization(info: EditorInfo): Boolean {
        val inputType = info.inputType
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_URI ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        ) {
            return false
        }
        return inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0 ||
                inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0
    }

    private companion object {
        private const val TAG = "ComposeKeyboard"
        const val SAVE_DEBOUNCE_MS = SwipeConstants.SAVE_DEBOUNCE_MS
    }
}
