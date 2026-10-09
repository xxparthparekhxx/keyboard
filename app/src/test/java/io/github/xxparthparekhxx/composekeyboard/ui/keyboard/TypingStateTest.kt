package io.github.xxparthparekhxx.composekeyboard.ui.keyboard

import android.view.inputmethod.EditorInfo
import io.github.xxparthparekhxx.composekeyboard.data.FieldInputKind
import io.github.xxparthparekhxx.composekeyboard.data.KeyType
import io.github.xxparthparekhxx.composekeyboard.data.KeyboardMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingStateTest {

    /** Records what the state machine asked the editor to do. */
    private class Recorder : TypingActions {
        val log = mutableListOf<String>()
        override fun commitText(text: String) { log += "text:$text" }
        override fun deleteBackward() { log += "delete" }
        override fun performAction(actionId: Int) { log += "action:$actionId" }
        override fun switchIme() { log += "switchIme" }
        override fun commitSwipeWord(word: String) { log += "swipe:$word" }
        override fun replaceSwipeWord(word: String) { log += "replace:$word" }
        override fun pickSuggestion(word: String) { log += "pick:$word" }
    }

    private var now = 10_000L
    private val actions = Recorder()

    private fun state(mode: KeyboardMode = KeyboardMode.LOWERCASE) =
        TypingState(mode, actions, clock = { now })

    private fun TypingState.type(text: String) {
        for (c in text) onKey(if (c == ' ') KeyType.Space else KeyType.Character(c.toString()))
    }

    @Test
    fun shift_capitalizesOneLetterThenReleases() {
        val s = state()
        s.onKey(KeyType.Shift)
        assertEquals(KeyboardMode.UPPERCASE, s.mode)
        s.type("hi")
        assertEquals(listOf("text:H", "text:i"), actions.log)
        assertEquals(KeyboardMode.LOWERCASE, s.mode)
    }

    @Test
    fun doubleTapShift_locksCapsAndAThirdTapReleases() {
        val s = state()
        s.onKey(KeyType.Shift)
        now += 100
        s.onKey(KeyType.Shift)
        assertEquals(KeyboardMode.CAPS_LOCKED, s.mode)
        s.type("ab")
        assertEquals(KeyboardMode.CAPS_LOCKED, s.mode)
        assertEquals(listOf("text:A", "text:B"), actions.log)
        now += 1_000
        s.onKey(KeyType.Shift)
        assertEquals(KeyboardMode.LOWERCASE, s.mode)
    }

    @Test
    fun slowShiftTaps_doNotLockCaps() {
        val s = state()
        s.onKey(KeyType.Shift)
        now += TypingState.DOUBLE_TAP_MS + 1
        s.onKey(KeyType.Shift)
        assertEquals(KeyboardMode.LOWERCASE, s.mode)
    }

    @Test
    fun doubleSpace_becomesPeriodAndCapitalizesNextWord() {
        val s = state()
        s.type("ok ")
        now += 100
        s.onKey(KeyType.Space)
        assertEquals(listOf("text:o", "text:k", "text: ", "delete", "text:. "), actions.log)
        assertEquals(KeyboardMode.UPPERCASE, s.mode)
    }

    @Test
    fun doubleSpace_isNotAPeriodAfterPunctuationOrWhenSlow() {
        val s = state()
        s.type("ok")
        s.onKey(KeyType.Character("."))
        s.onKey(KeyType.Space)
        now += 100
        s.onKey(KeyType.Space)
        assertEquals("text: ", actions.log.last())

        actions.log.clear()
        s.type("so ")
        now += TypingState.DOUBLE_TAP_MS + 1
        s.onKey(KeyType.Space)
        assertTrue("delete" !in actions.log)
    }

    @Test
    fun spaceAfterSentenceEnd_shiftsOnlyWithAutoCaps() {
        val s = state()
        s.type("hi")
        s.onKey(KeyType.Character("!"))
        s.onKey(KeyType.Space)
        assertEquals(KeyboardMode.UPPERCASE, s.mode)

        val off = state().apply { autoCapitalization = false }
        off.type("hi")
        off.onKey(KeyType.Character("!"))
        off.onKey(KeyType.Space)
        assertEquals(KeyboardMode.LOWERCASE, off.mode)
    }

    @Test
    fun enter_performsTheFieldsAction() {
        val s = state().apply { imeAction = EditorInfo.IME_ACTION_SEND }
        s.onKey(KeyType.Enter)
        assertEquals(listOf("action:${EditorInfo.IME_ACTION_SEND}"), actions.log)
    }

    @Test
    fun swipeResult_commitsBestWordCasedForShiftAndOffersAlternatives() {
        val s = state()
        s.onKey(KeyType.Shift)
        s.onSwipeResult(listOf("hello", "jello"))
        assertEquals(listOf("swipe:Hello"), actions.log)
        assertEquals(listOf("Hello", "Jello"), s.swipeAlternatives)
        assertEquals(0, s.selectedSuggestion)
        assertEquals(KeyboardMode.LOWERCASE, s.mode)

        s.onSuggestionTapped(1, s.swipeAlternatives)
        assertEquals("replace:Jello", actions.log.last())
        assertEquals(1, s.selectedSuggestion)

        // Tapping the word already in the text dismisses the alternatives.
        s.onSuggestionTapped(1, s.swipeAlternatives)
        assertTrue(s.swipeAlternatives.isEmpty())
    }

    @Test
    fun anyKey_dropsSwipeAlternatives() {
        val s = state()
        s.onSwipeResult(listOf("hello", "jello"))
        s.onKey(KeyType.Backspace)
        assertTrue(s.swipeAlternatives.isEmpty())
    }

    @Test
    fun suggestions_completeTheCurrentWord() {
        val s = state()
        val shown = s.suggestions(
            currentWord = "hel",
            previousWord = "say",
            allowsSuggestions = true,
            completions = { listOf("${it}lo", "${it}p") },
            predictions = { error("not between words") }
        )
        assertEquals(listOf("hello", "help"), shown)
    }

    @Test
    fun suggestions_predictTheNextWordBetweenWords() {
        val s = state()
        val shown = s.suggestions(
            currentWord = "",
            previousWord = "good",
            allowsSuggestions = true,
            completions = { error("no word to complete") },
            predictions = { if (it == "good") listOf("morning", "night") else emptyList() }
        )
        assertEquals(listOf("morning", "night"), shown)

        s.onSuggestionTapped(0, shown)
        assertEquals("pick:morning", actions.log.last())
    }

    @Test
    fun suggestions_predictionsFollowShift() {
        val s = state(KeyboardMode.UPPERCASE)
        val shown = s.suggestions("", "good", true, { emptyList() }, { listOf("morning") })
        assertEquals(listOf("Morning"), shown)
    }

    @Test
    fun suggestions_uppercaseThePrefixWhenShiftIsOnMidWord() {
        val s = state()
        s.onKey(KeyType.Shift)
        var asked = ""
        s.suggestions("hel", null, true, { asked = it; emptyList() }, { emptyList() })
        assertEquals("HEL", asked)
    }

    @Test
    fun suggestions_areHiddenWhenTheFieldDisallowsThemOrOffLetters() {
        val s = state()
        assertTrue(s.suggestions("hel", "a", false, { listOf("x") }, { listOf("y") }).isEmpty())
        s.onKey(KeyType.SymbolToggle)
        assertTrue(s.suggestions("hel", "a", true, { listOf("x") }, { listOf("y") }).isEmpty())
    }

    @Test
    fun suggestions_preferSwipeAlternatives() {
        val s = state()
        s.onSwipeResult(listOf("hello", "jello"))
        val shown = s.suggestions("", "say", true, { listOf("x") }, { listOf("y") })
        assertEquals(listOf("hello", "jello"), shown)
    }

    @Test
    fun emojiSearch_capturesKeysInsteadOfTyping() {
        val s = state()
        s.onKey(KeyType.EmojiToggle)
        s.emojiSearching = true
        s.type("cat")
        s.onKey(KeyType.Backspace)
        assertEquals("ca", s.emojiSearchQuery)
        assertTrue(actions.log.isEmpty())

        s.onKey(KeyType.AlphabetToggle)
        assertEquals(KeyboardMode.LOWERCASE, s.mode)
        assertEquals("", s.emojiSearchQuery)
    }

    @Test
    fun togglePanel_opensAndClosesBackToLetters() {
        val s = state()
        s.togglePanel(KeyboardMode.CLIPBOARD)
        assertEquals(KeyboardMode.CLIPBOARD, s.mode)
        s.togglePanel(KeyboardMode.CLIPBOARD)
        assertEquals(KeyboardMode.LOWERCASE, s.mode)
    }

    @Test
    fun fieldStart_opensNumpadForNumericFields() {
        val s = state()
        s.onFieldStart(FieldInputKind.PHONE)
        assertEquals(KeyboardMode.NUMPAD, s.mode)
    }

    @Test
    fun cursorWantsShift_followsTheCaretButLeavesCapsLockAlone() {
        val s = state()
        s.onCursorWantsShift(true)
        assertEquals(KeyboardMode.UPPERCASE, s.mode)
        s.onCursorWantsShift(false)
        assertEquals(KeyboardMode.LOWERCASE, s.mode)

        s.onKey(KeyType.Shift)
        now += 100
        s.onKey(KeyType.Shift)
        s.onCursorWantsShift(true)
        assertEquals(KeyboardMode.CAPS_LOCKED, s.mode)
    }

    @Test
    fun languageSwitch_switchesIme() {
        state().onKey(KeyType.LanguageSwitch)
        assertEquals(listOf("switchIme"), actions.log)
    }
}
