package io.github.xxparthparekhxx.composekeyboard.service

import android.os.Bundle
import android.os.Handler
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import io.github.xxparthparekhxx.composekeyboard.data.InlineChipSpec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * Tests the editing and selection-tracking logic of [ComposeInputMethodService]:
 * whole-word backspace after a swipe, grapheme-cluster deletes, raw-key (terminal)
 * fields, the [io.github.xxparthparekhxx.composekeyboard.data.SelectionTracker]
 * echo-vs-external-move distinction, and the API 30+ inline-suggestion request.
 *
 * The service runs under Robolectric via [Robolectric.buildService]. The framework
 * normally installs the client [InputConnection] inside `InputMethodService.doStartInput`,
 * which is package-private, so [attachInput] sets the same framework fields
 * (`mStartedInputConnection` / `mInputEditorInfo`) via reflection.
 *
 * The dictionary/decoder that `onCreate` loads on a background thread is never
 * waited on or asserted against; these tests only exercise the foreground edit
 * path, which the async load cannot affect.
 */
@RunWith(RobolectricTestRunner::class)
class ComposeInputMethodServiceTest {

    private lateinit var controller: ServiceController<ComposeInputMethodService>
    private lateinit var service: ComposeInputMethodService

    @Before
    fun setUp() {
        controller = Robolectric.buildService(ComposeInputMethodService::class.java).create()
        service = controller.get()
    }

    @After
    fun tearDown() {
        controller.destroy()
    }

    // ---------------------------------------------------------------------
    // 1. Swipe commit + whole-word backspace
    // ---------------------------------------------------------------------

    @Test
    fun backspace_afterSwipe_commitRemovesWholeWordAndSpace() {
        val fake = FakeInputConnection()
        fake.seed("hi")
        val info = textFieldInfo(caret = 2)
        attachInput(fake, info)
        service.onStartInputView(info, false)

        // The caret sits right after "hi", so the decoder word needs its own space.
        service.commitSwipeWord("there")
        assertEquals("hi there", fake.text)
        assertEquals(listOf(" there"), fake.committedTexts)
        assertEquals(1, fake.batchBegins)
        assertEquals(1, fake.batchEnds)

        // First backspace after the gesture takes the word plus the inserted space.
        service.handleDelete()
        assertEquals(listOf(6 to 0), fake.deletions)
        assertEquals("hi", fake.text)

        // The swipe state is consumed: the next delete is an ordinary one-unit delete.
        service.handleDelete()
        assertEquals(listOf(6 to 0, 1 to 0), fake.deletions)
        assertEquals("h", fake.text)
    }

    // ---------------------------------------------------------------------
    // 2. Grapheme-cluster deletes on plain text
    // ---------------------------------------------------------------------

    @Test
    fun backspace_normalText_removesSingleGrapheme() {
        val fake = FakeInputConnection()
        fake.seed("hi")
        val info = textFieldInfo(caret = 2)
        attachInput(fake, info)
        service.onStartInputView(info, false)

        service.handleDelete()
        assertEquals(listOf(1 to 0), fake.deletions)
        assertEquals("h", fake.text)

        // An emoji is a surrogate pair: one cluster, two UTF-16 units.
        val emojiFake = FakeInputConnection()
        emojiFake.seed("h👍")
        val emojiInfo = textFieldInfo(caret = 3)
        attachInput(emojiFake, emojiInfo)
        service.onStartInputView(emojiInfo, false)

        service.handleDelete()
        assertEquals(listOf(2 to 0), emojiFake.deletions)
        assertEquals("h", emojiFake.text)
    }

    // ---------------------------------------------------------------------
    // 3. Raw-key editors (terminals)
    // ---------------------------------------------------------------------

    @Test
    fun backspace_terminalField_neverCallsDeleteSurrounding() {
        val fake = FakeInputConnection()
        fake.seed("")
        val info = terminalFieldInfo()
        attachInput(fake, info)
        service.onStartInputView(info, false)

        service.handleDelete()

        // A terminal has no text buffer: the delete must go out as a key event,
        // never as deleteSurroundingText (or a text commit).
        assertTrue("deleteSurroundingText must not be called", fake.deletions.isEmpty())
        assertTrue("commitText must not be called", fake.committedTexts.isEmpty())
        assertEquals(
            listOf(KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_DEL),
            fake.sentKeyCodes
        )
    }

    // ---------------------------------------------------------------------
    // 4. Selection echoes of our own edits
    // ---------------------------------------------------------------------

    @Test
    fun selectionEcho_isNotCountedAsExternalMove() {
        val fake = FakeInputConnection()
        fake.seed("hi")
        val info = textFieldInfo(caret = 2)
        attachInput(fake, info)
        service.onStartInputView(info, false)

        service.commitSwipeWord("there")

        // Our commit moved the caret from 2 to 8; the editor reports that move as
        // an update. It matches the tracker's prediction, so it is our echo.
        service.onUpdateSelection(2, 2, 8, 8, -1, -1)

        // If the echo had been misclassified as an external move, the swipe state
        // would have been cleared and this delete would remove only one unit.
        service.handleDelete()
        assertEquals(listOf(6 to 0), fake.deletions)
        assertEquals("hi", fake.text)
    }

    // ---------------------------------------------------------------------
    // 5. A real external caret move invalidates the swipe state
    // ---------------------------------------------------------------------

    @Test
    fun externalCaretMove_resetsSwipeState() {
        val fake = FakeInputConnection()
        fake.seed("hi")
        val info = textFieldInfo(caret = 2)
        attachInput(fake, info)
        service.onStartInputView(info, false)

        service.commitSwipeWord("there")

        // The user taps the start of the line: old=(8,8) is where our commit left
        // the caret, new=(0,0) is nowhere near the prediction.
        service.onUpdateSelection(8, 8, 0, 0, -1, -1)

        // The swipe state is gone, so this is an ordinary one-grapheme delete.
        service.handleDelete()
        assertEquals(listOf(1 to 0), fake.deletions)
        assertEquals("hi ther", fake.text)
    }

    // ---------------------------------------------------------------------
    // 6. Inline suggestion request (API 30+)
    // ---------------------------------------------------------------------

    // `android.widget.inline.*` exists only on API 30+, while the default
    // Robolectric SDK for this suite resolves to the framework floor (API 23,
    // because unit tests cannot read the app manifest to learn targetSdk).
    // This test pins SDK 35 — the newest SDK this Robolectric version runs on
    // the project's Java 17 (SDK 36+ requires Java 21).
    @Test
    @Config(sdk = [35])
    fun inlineSuggestionsRequest_specMatchesChipDpBounds() {
        val raw = service.onCreateInlineSuggestionsRequest(Bundle())
        assertNotNull(raw)
        val request = raw!!

        assertEquals(6, request.maxSuggestionCount)
        val specs = request.inlinePresentationSpecs
        assertEquals(6, specs.size)

        val density = service.resources.displayMetrics.density
        val minWidth = (InlineChipSpec.MIN_WIDTH_DP * density).toInt()
        val maxWidth = (InlineChipSpec.MAX_WIDTH_DP * density).toInt()
        val height = (InlineChipSpec.HEIGHT_DP * density).toInt()
        assertTrue(minWidth < maxWidth)

        for (spec in specs) {
            assertEquals(minWidth, spec.minSize.width)
            assertEquals(height, spec.minSize.height)
            assertEquals(maxWidth, spec.maxSize.width)
            assertEquals(height, spec.maxSize.height)
        }
    }

    // ---------------------------------------------------------------------
    // 7. Action and Enter handling (handleAction / handleEditorAction)
    // ---------------------------------------------------------------------

    @Test
    fun action_specificImeAction_callsPerformEditorAction() {
        val fake = FakeInputConnection()
        val info = textFieldInfo(caret = 0)
        attachInput(fake, info)
        service.onStartInputView(info, false)

        val actions = listOf(
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_NEXT
        )
        for (action in actions) {
            service.handleAction(action)
        }

        assertEquals(actions, fake.performedActions)
        assertTrue("No raw key events should be sent for handled actions", fake.sentKeyCodes.isEmpty())
    }

    @Test
    fun action_unspecifiedOrNone_sendsEnterKeyEvent() {
        val fake = FakeInputConnection()
        val info = textFieldInfo(caret = 0)
        attachInput(fake, info)
        service.onStartInputView(info, false)

        service.handleAction(EditorInfo.IME_ACTION_UNSPECIFIED)
        assertEquals(
            listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_ENTER),
            fake.sentKeyCodes
        )
        assertTrue(fake.performedActions.isEmpty())

        service.handleAction(EditorInfo.IME_ACTION_NONE)
        assertEquals(
            listOf(
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_ENTER
            ),
            fake.sentKeyCodes
        )
        assertTrue(fake.performedActions.isEmpty())
    }

    @Test
    fun action_flushesTypedWordAndClearsSwipeState() {
        val fake = FakeInputConnection()
        val info = textFieldInfo(caret = 0)
        attachInput(fake, info)
        service.onStartInputView(info, false)

        // Commit a swipe word, then type some letters into the typed word tracker.
        service.commitSwipeWord("hello")
        service.handleTextInput("c")
        service.handleTextInput("a")
        service.handleTextInput("t")
        assertEquals("hello cat", fake.text)

        // Triggering an action flushes the typed word and resets lastSwipeCommit.
        service.handleAction(EditorInfo.IME_ACTION_DONE)
        assertEquals(listOf(EditorInfo.IME_ACTION_DONE), fake.performedActions)

        // Because lastSwipeCommit was cleared, backspace is a single grapheme delete,
        // not a whole-word delete.
        service.handleDelete()
        assertEquals(listOf(1 to 0), fake.deletions)
        assertEquals("hello ca", fake.text)
    }

    @Test
    fun onStartInputView_derivesCurrentImeActionFromImeOptions() {
        val fake = FakeInputConnection()

        // Standard actions
        val searchInfo = EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_SEARCH }
        attachInput(fake, searchInfo)
        service.onStartInputView(searchInfo, false)
        assertEquals(EditorInfo.IME_ACTION_SEARCH, service.currentImeAction)

        val goInfo = EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_GO }
        attachInput(fake, goInfo)
        service.onStartInputView(goInfo, false)
        assertEquals(EditorInfo.IME_ACTION_GO, service.currentImeAction)

        // Multi-line editor flag suppresses action
        val multiLineInfo = EditorInfo().apply {
            imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_ENTER_ACTION
        }
        attachInput(fake, multiLineInfo)
        service.onStartInputView(multiLineInfo, false)
        assertEquals(EditorInfo.IME_ACTION_UNSPECIFIED, service.currentImeAction)

        // IME_ACTION_NONE resolves to UNSPECIFIED
        val noneInfo = EditorInfo().apply { imeOptions = EditorInfo.IME_ACTION_NONE }
        attachInput(fake, noneInfo)
        service.onStartInputView(noneInfo, false)
        assertEquals(EditorInfo.IME_ACTION_UNSPECIFIED, service.currentImeAction)

        // Null info defaults to UNSPECIFIED
        attachInput(fake, null)
        service.onStartInputView(null, false)
        assertEquals(EditorInfo.IME_ACTION_UNSPECIFIED, service.currentImeAction)
    }

    // ---------------------------------------------------------------------
    // 8. Swipe commit and follow-up typing flow
    // ---------------------------------------------------------------------

    @Test
    fun commitSwipeWord_withoutPrecedingTextOrAfterWordOpening_doesNotInsertSpace() {
        val fake = FakeInputConnection()
        val info = textFieldInfo(caret = 0)
        attachInput(fake, info)
        service.onStartInputView(info, false)

        // 1. Empty field: no leading space needed
        service.commitSwipeWord("hello")
        assertEquals("hello", fake.text)
        assertEquals(listOf("hello"), fake.committedTexts)

        // 2. After opening bracket: no space
        fake.seed("(")
        service.commitSwipeWord("world")
        assertEquals("(world", fake.text)
        assertEquals(listOf("hello", "world"), fake.committedTexts)

        // 3. After trailing space: no extra space
        fake.seed("hi ")
        service.commitSwipeWord("there")
        assertEquals("hi there", fake.text)
        assertEquals(listOf("hello", "world", "there"), fake.committedTexts)
    }

    @Test
    fun commitSwipeWord_whenViewHidden_isDropped() {
        val fake = FakeInputConnection()
        val info = textFieldInfo(caret = 0)
        attachInput(fake, info)
        service.onStartInputView(info, false)

        // Simulate keyboard window closing before late async decode finishes
        service.onFinishInputView(false)

        service.commitSwipeWord("ghost")
        assertTrue(fake.committedTexts.isEmpty())
        assertTrue(fake.text.isEmpty())
    }

    @Test
    fun handleTextInput_afterSwipeCommit_insertsSpaceBeforeLettersOnly() {
        val fake = FakeInputConnection()
        val info = textFieldInfo(caret = 0)
        attachInput(fake, info)
        service.onStartInputView(info, false)

        // Swipe a word
        service.commitSwipeWord("hello")
        assertEquals("hello", fake.text)

        // Typing a letter after a swipe automatically inserts a separating space
        service.handleTextInput("w")
        assertEquals("hello w", fake.text)
        assertEquals(listOf("hello", " w"), fake.committedTexts)

        // Subsequent letters in the same word do not insert space
        service.handleTextInput("o")
        assertEquals("hello wo", fake.text)
        assertEquals(listOf("hello", " w", "o"), fake.committedTexts)

        // Swipe another word
        service.commitSwipeWord("there")
        assertEquals("hello wo there", fake.text)

        // Typing punctuation attaches directly to the swiped word without space
        service.handleTextInput(".")
        assertEquals("hello wo there.", fake.text)
        assertEquals(listOf("hello", " w", "o", " there", "."), fake.committedTexts)

        // Swipe a third word
        service.commitSwipeWord("friend")
        assertEquals("hello wo there. friend", fake.text)

        // Typing a space directly commits a single space without duplication
        service.handleTextInput(" ")
        assertEquals("hello wo there. friend ", fake.text)
    }

    @Test
    fun replaceSwipeWord_swapsCommittedWordAndUpdatesDeleteState() {
        val fake = FakeInputConnection()
        fake.seed("see ")
        val info = textFieldInfo(caret = 4)
        attachInput(fake, info)
        service.onStartInputView(info, false)

        // Initial swipe commit
        service.commitSwipeWord("helo")
        assertEquals("see helo", fake.text)

        // User picks "hello" from the suggestion strip
        service.replaceSwipeWord("hello")
        assertEquals("see hello", fake.text)
        assertEquals(listOf(4 to 0), fake.deletions)

        // Backspace after replacement takes the full replaced word
        service.handleDelete()
        assertEquals("see ", fake.text)
        assertEquals(listOf(4 to 0, 5 to 0), fake.deletions)
    }

    @Test
    fun commitAutocomplete_replacesPrefixWithWordAndTrailingSpace() {
        val fake = FakeInputConnection()
        fake.seed("see comp")
        val info = textFieldInfo(caret = 8)
        attachInput(fake, info)
        service.onStartInputView(info, false)

        service.commitAutocomplete("compose", "comp")
        assertEquals("see compose ", fake.text)
        assertEquals(listOf(4 to 0), fake.deletions)
        assertEquals(listOf("compose "), fake.committedTexts)
    }

    @Test
    fun commitAutocomplete_withStalePrefix_isIgnored() {
        val fake = FakeInputConnection()
        fake.seed("see other")
        val info = textFieldInfo(caret = 9)
        attachInput(fake, info)
        service.onStartInputView(info, false)

        // Tapping a suggestion whose prefix no longer matches what's before the cursor
        service.commitAutocomplete("compose", "comp")
        assertEquals("see other", fake.text)
        assertTrue(fake.deletions.isEmpty())
        assertTrue(fake.committedTexts.isEmpty())
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Mirrors what `InputMethodService.doStartInput` does internally, which is
     * package-private and unreachable from a test package.
     */
    private fun attachInput(fake: FakeInputConnection, editorInfo: EditorInfo?) {
        ReflectionHelpers.setField(service, "mStartedInputConnection", fake)
        ReflectionHelpers.setField(service, "mInputEditorInfo", editorInfo)
    }

    private fun textFieldInfo(caret: Int): EditorInfo = EditorInfo().apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        initialSelStart = caret
        initialSelEnd = caret
    }

    private fun terminalFieldInfo(): EditorInfo = EditorInfo().apply {
        // Class bits are TYPE_NULL (a Termux-style shell): no text buffer.
        inputType = InputType.TYPE_NULL or InputType.TYPE_TEXT_VARIATION_NORMAL
    }

    /**
     * A small in-memory editor. The text buffer and caret behave like a real
     * [InputConnection] for the calls the service makes, and every edit is
     * recorded so tests can assert on what was actually sent.
     */
    private class FakeInputConnection : InputConnection {

        val buffer = StringBuilder()
        var caret = 0

        val committedTexts = mutableListOf<String>()
        val deletions = mutableListOf<Pair<Int, Int>>()
        val sentKeyCodes = mutableListOf<Int>()
        var batchBegins = 0
        var batchEnds = 0

        val text: String
            get() = buffer.toString()

        fun seed(text: String) {
            buffer.setLength(0)
            buffer.append(text)
            caret = buffer.length
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            val t = text?.toString() ?: ""
            committedTexts.add(t)
            buffer.replace(caret, caret, t)
            caret += t.length
            return true
        }

        override fun setSelection(start: Int, end: Int): Boolean {
            caret = start
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            deletions.add(beforeLength to afterLength)
            val from = (caret - beforeLength).coerceAtLeast(0)
            val to = (caret + afterLength).coerceAtMost(buffer.length)
            if (from < to) buffer.delete(from, to)
            caret = from
            return true
        }

        override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean = true

        override fun setComposingRegion(start: Int, end: Int): Boolean = true

        override fun getCursorCapsMode(reqModes: Int): Int = 0

        override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence? {
            if (caret <= 0) return ""
            return buffer.substring((caret - n).coerceAtLeast(0), caret)
        }

        override fun getTextAfterCursor(n: Int, flags: Int): CharSequence? = ""

        override fun getSelectedText(flags: Int): CharSequence? = null

        override fun sendKeyEvent(event: KeyEvent?): Boolean {
            if (event != null) sentKeyCodes.add(event.keyCode)
            return true
        }

        override fun beginBatchEdit(): Boolean {
            batchBegins++
            return true
        }

        override fun endBatchEdit(): Boolean {
            batchEnds++
            return true
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = true

        override fun finishComposingText(): Boolean = true

        override fun commitCompletion(text: CompletionInfo): Boolean = true

        override fun commitCorrection(correctionInfo: CorrectionInfo): Boolean = true

        override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean = true

        override fun closeConnection() {}

        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText? = null

        override fun getHandler(): Handler? = null

        val performedActions = mutableListOf<Int>()

        override fun performEditorAction(editorAction: Int): Boolean {
            performedActions.add(editorAction)
            return true
        }

        override fun performContextMenuAction(id: Int): Boolean = false

        override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = false

        override fun reportFullscreenMode(enabled: Boolean): Boolean = true

        override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = false

        override fun clearMetaKeyStates(states: Int): Boolean = true
    }
}
