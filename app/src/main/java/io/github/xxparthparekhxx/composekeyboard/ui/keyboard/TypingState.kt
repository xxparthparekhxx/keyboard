package io.github.xxparthparekhxx.composekeyboard.ui.keyboard

import android.os.SystemClock
import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.xxparthparekhxx.composekeyboard.data.Capitalization
import io.github.xxparthparekhxx.composekeyboard.data.FieldInputKind
import io.github.xxparthparekhxx.composekeyboard.data.GraphemeClusters
import io.github.xxparthparekhxx.composekeyboard.data.KeyType
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardMode

/** What a key press asks the editor to do. Implemented by the service. */
interface TypingActions {
    fun commitText(text: String)
    fun deleteBackward()
    fun performAction(actionId: Int)
    fun switchIme()
    fun commitSwipeWord(word: String)
    fun replaceSwipeWord(word: String)

    /** Commits a suggestion-strip word in place of the word being typed (if any). */
    fun pickSuggestion(word: String)
}

/**
 * The keyboard's typing state machine: layout mode, shift and caps lock,
 * double-space to period, sentence auto-caps, swipe alternatives and the
 * emoji search query.
 *
 * Plain Kotlin over snapshot state, so it is unit-tested without Compose UI;
 * the composable only renders it. The word being typed is *not* tracked
 * here: the service owns it (it sees every edit, including ones that do not
 * come from keys) and passes it in to [suggestions].
 */
@Stable
class TypingState(
    initialMode: KeyboardMode,
    private val actions: TypingActions,
    private val clock: () -> Long = { SystemClock.uptimeMillis() }
) {
    var mode by mutableStateOf(initialMode)
        private set

    /** Kept current by the composable; read when a key is handled. */
    var autoCapitalization: Boolean = true
    var cursorWantsShift: Boolean = false
    var imeAction: Int = EditorInfo.IME_ACTION_UNSPECIFIED

    /** Ranked words from the last gesture, while the strip offers swapping them. */
    var swipeAlternatives by mutableStateOf(emptyList<String>())
        private set

    /** Which of [swipeAlternatives] is in the text; -1 when none. */
    var selectedSuggestion by mutableIntStateOf(-1)
        private set

    var emojiSearching by mutableStateOf(false)
    var emojiSearchQuery by mutableStateOf("")

    /** Enter in emoji search: the composable inserts the top result. */
    var onEmojiSearchSubmit: () -> Unit = {}

    private var lastShiftTapTime = 0L
    private var lastSpaceTapTime = 0L

    /** Last character this keyboard committed, for the double-space period shortcut. */
    private var lastCommitted = ' '
    private var lastNonSpaceChar = ' '

    val isAlphaMode: Boolean
        get() = mode == KeyboardMode.LOWERCASE ||
            mode == KeyboardMode.UPPERCASE ||
            mode == KeyboardMode.CAPS_LOCKED

    private fun lettersMode(): KeyboardMode = Capitalization.lettersMode(cursorWantsShift)

    /**
     * What the suggestion strip shows: the last gesture's alternatives; else
     * completions of [currentWord]; else, between words, predictions of the
     * word that follows [previousWord].
     */
    fun suggestions(
        currentWord: String,
        previousWord: String?,
        allowsSuggestions: Boolean,
        completions: (prefix: String) -> List<String>,
        predictions: (previous: String) -> List<String>
    ): List<String> {
        if (swipeAlternatives.isNotEmpty()) return swipeAlternatives
        if (!allowsSuggestions || !isAlphaMode) return emptyList()
        if (currentWord.isNotEmpty()) {
            // Shift pressed mid-word asks for the completion in capitals.
            val prefix = if (mode == KeyboardMode.LOWERCASE) currentWord else currentWord.uppercase()
            return completions(prefix)
        }
        if (previousWord != null) {
            return predictions(previousWord).map { applyShift(it, mode) }
        }
        return emptyList()
    }

    /** A new field, or this field's layout changed. */
    fun onFieldStart(kind: FieldInputKind) {
        mode = if (kind.opensAsNumpad) {
            KeyboardMode.NUMPAD
        } else {
            Capitalization.applyCursorShift(mode, cursorWantsShift)
        }
    }

    /**
     * Auto-capitalization follows the editor caret, not a one-shot "this
     * field wants capitals" flag. Re-evaluating on every change is what keeps
     * shift correct after dismiss, backspace, and tapping into the middle of
     * a sentence. Caps-lock and symbol/emoji layouts are left alone.
     */
    fun onCursorWantsShift(wantsShift: Boolean) {
        cursorWantsShift = wantsShift
        mode = Capitalization.applyCursorShift(mode, wantsShift)
    }

    /**
     * The caret moved without us (tap elsewhere, app rewrote the field): the
     * last gesture's word may no longer be at the caret, and the last
     * committed character is no longer what precedes it.
     */
    fun onExternalCaretMove() {
        clearSwipeAlternatives()
        lastCommitted = ' '
        lastSpaceTapTime = 0L
    }

    fun clearSwipeAlternatives() {
        swipeAlternatives = emptyList()
        selectedSuggestion = -1
    }

    /** Opens [panel], or goes back to letters if it is already open. */
    fun togglePanel(panel: KeyboardMode) {
        if (mode == panel) {
            if (panel == KeyboardMode.EMOJI) closeEmojiSearch()
            mode = lettersMode()
        } else {
            mode = panel
        }
    }

    fun showLetters() {
        closeEmojiSearch()
        mode = lettersMode()
    }

    fun closeEmojiSearch() {
        emojiSearching = false
        emojiSearchQuery = ""
    }

    fun onSwipeResult(words: List<String>) {
        if (words.isEmpty()) return
        val cased = words.map { applyShift(it, mode) }
        swipeAlternatives = cased
        selectedSuggestion = 0
        actions.commitSwipeWord(cased.first())
        lastCommitted = 'a'
        lastNonSpaceChar = 'a'
        lastSpaceTapTime = 0L
        if (mode == KeyboardMode.UPPERCASE) mode = KeyboardMode.LOWERCASE
    }

    /** A tap on the strip's [index]th entry of [shown] (as returned by [suggestions]). */
    fun onSuggestionTapped(index: Int, shown: List<String>) {
        val word = shown.getOrNull(index) ?: return
        if (swipeAlternatives.isNotEmpty()) {
            if (index == selectedSuggestion) {
                clearSwipeAlternatives()
            } else {
                selectedSuggestion = index
                actions.replaceSwipeWord(word)
            }
            return
        }
        actions.pickSuggestion(word)
        // The pick ends with a space, like a typed word followed by space.
        lastNonSpaceChar = word.lastOrNull() ?: lastNonSpaceChar
        lastCommitted = ' '
        lastSpaceTapTime = 0L
        if (mode == KeyboardMode.UPPERCASE) mode = KeyboardMode.LOWERCASE
    }

    fun onLongPress(type: KeyType) {
        if (type is KeyType.Character && type.popup.isNotEmpty()) {
            onPopup(type.popup.first())
        }
    }

    fun onPopup(alt: String) {
        if (mode == KeyboardMode.EMOJI && emojiSearching) {
            emojiSearchQuery += alt
            return
        }
        actions.commitText(alt)
        lastCommitted = alt.firstOrNull() ?: lastCommitted
        lastNonSpaceChar = lastCommitted
        lastSpaceTapTime = 0L
        clearSwipeAlternatives()
        if (autoCapitalization && isAlphaMode && mode == KeyboardMode.UPPERCASE) {
            mode = KeyboardMode.LOWERCASE
        }
    }

    /** Single entry point for every key on every row. */
    fun onKey(type: KeyType) {
        if (mode == KeyboardMode.EMOJI && emojiSearching) {
            onEmojiSearchKey(type)
            return
        }
        when (type) {
            is KeyType.Shift -> onShift()
            is KeyType.Character -> onCharacter(type)
            is KeyType.Backspace -> {
                actions.deleteBackward()
                lastSpaceTapTime = 0L
                clearSwipeAlternatives()
            }
            is KeyType.Space -> onSpace()
            is KeyType.Enter -> {
                actions.performAction(imeAction)
                clearSwipeAlternatives()
                lastSpaceTapTime = 0L
                lastCommitted = '\n'
                lastNonSpaceChar = '\n'
            }
            is KeyType.SymbolToggle -> switchLayout(KeyboardMode.SYMBOLS)
            is KeyType.SymbolMoreToggle -> switchLayout(KeyboardMode.SYMBOLS_MORE)
            is KeyType.AlphabetToggle -> switchLayout(lettersMode())
            is KeyType.NumpadToggle -> switchLayout(KeyboardMode.NUMPAD)
            is KeyType.EmojiToggle -> switchLayout(KeyboardMode.EMOJI)
            is KeyType.LanguageSwitch -> actions.switchIme()
        }
    }

    private fun onEmojiSearchKey(type: KeyType) {
        when (type) {
            is KeyType.Character -> emojiSearchQuery += type.primary
            is KeyType.Space -> emojiSearchQuery += " "
            is KeyType.Backspace -> {
                val drop = GraphemeClusters.utf16LengthOfLastCluster(emojiSearchQuery)
                if (drop > 0) emojiSearchQuery = emojiSearchQuery.dropLast(drop)
            }
            is KeyType.Enter -> onEmojiSearchSubmit()
            is KeyType.EmojiToggle -> closeEmojiSearch()
            is KeyType.AlphabetToggle -> showLetters()
            else -> Unit
        }
    }

    private fun onShift() {
        val now = clock()
        mode = if (now - lastShiftTapTime < DOUBLE_TAP_MS) {
            // Double tap = Caps Lock
            if (mode == KeyboardMode.CAPS_LOCKED) KeyboardMode.LOWERCASE else KeyboardMode.CAPS_LOCKED
        } else {
            if (mode == KeyboardMode.LOWERCASE) KeyboardMode.UPPERCASE else KeyboardMode.LOWERCASE
        }
        lastShiftTapTime = now
    }

    private fun onCharacter(type: KeyType.Character) {
        val char = when (mode) {
            KeyboardMode.UPPERCASE, KeyboardMode.CAPS_LOCKED -> type.primary.uppercase()
            else -> type.primary
        }
        actions.commitText(char)
        lastCommitted = char[0]
        lastNonSpaceChar = char[0]
        lastSpaceTapTime = 0L
        clearSwipeAlternatives()
        if (mode == KeyboardMode.UPPERCASE) mode = KeyboardMode.LOWERCASE
    }

    private fun onSpace() {
        val now = clock()
        if (now - lastSpaceTapTime < DOUBLE_TAP_MS && lastCommitted == ' ' &&
            lastNonSpaceChar !in ".!?\n\t"
        ) {
            // Double-tap spacebar shortcut: replace trailing space with ". "
            actions.deleteBackward()
            actions.commitText(". ")
            lastCommitted = '.'
            lastNonSpaceChar = '.'
            if (autoCapitalization && isAlphaMode) mode = KeyboardMode.UPPERCASE
            lastSpaceTapTime = 0L
        } else {
            actions.commitText(" ")
            // A space after . ! ? ends the sentence; the next word starts
            // with a capital.
            if (autoCapitalization && isAlphaMode && lastCommitted in ".!?") {
                mode = KeyboardMode.UPPERCASE
            }
            lastCommitted = ' '
            lastSpaceTapTime = now
        }
        clearSwipeAlternatives()
    }

    private fun switchLayout(target: KeyboardMode) {
        mode = target
        clearSwipeAlternatives()
        lastSpaceTapTime = 0L
    }

    companion object {
        /** Two taps of shift or space closer than this count as a double tap. */
        const val DOUBLE_TAP_MS = 350L

        /** Applies the live shift state to a word the keyboard is about to offer or commit. */
        fun applyShift(word: String, mode: KeyboardMode): String = when (mode) {
            KeyboardMode.CAPS_LOCKED -> word.uppercase()
            KeyboardMode.UPPERCASE -> word.replaceFirstChar { it.uppercase() }
            else -> word
        }
    }
}
