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

        override fun performEditorAction(editorAction: Int): Boolean = true

        override fun performContextMenuAction(id: Int): Boolean = false

        override fun performPrivateCommand(action: String?, data: Bundle?): Boolean = false

        override fun reportFullscreenMode(enabled: Boolean): Boolean = true

        override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean = false

        override fun clearMetaKeyStates(states: Int): Boolean = true
    }
}
