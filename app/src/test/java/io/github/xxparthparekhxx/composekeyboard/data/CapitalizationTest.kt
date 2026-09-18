package io.github.xxparthparekhxx.composekeyboard.data

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapitalizationTest {

    private val sentenceText = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    private val wordText = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
    private val charsText = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS

    @Test
    fun fieldAllowsAutoCaps_acceptsSentenceWordAndCharacterFlags() {
        assertTrue(Capitalization.fieldAllowsAutoCaps(sentenceText))
        assertTrue(Capitalization.fieldAllowsAutoCaps(wordText))
        assertTrue(Capitalization.fieldAllowsAutoCaps(charsText))
    }

    @Test
    fun fieldAllowsAutoCaps_rejectsPasswordEmailUriAndNonText() {
        assertFalse(
            Capitalization.fieldAllowsAutoCaps(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            )
        )
        assertFalse(
            Capitalization.fieldAllowsAutoCaps(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            )
        )
        assertFalse(
            Capitalization.fieldAllowsAutoCaps(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            )
        )
        assertFalse(Capitalization.fieldAllowsAutoCaps(InputType.TYPE_CLASS_NUMBER))
        assertFalse(Capitalization.fieldAllowsAutoCaps(InputType.TYPE_CLASS_TEXT))
    }

    @Test
    fun wantsShift_followsCursorNotJustFieldType() {
        // Mid-sentence / mid-word: field allows caps, but the cursor does not.
        assertFalse(Capitalization.wantsShift(true, true, cursorCapsMode = 0))
        // Sentence start after ". " or an empty prose field.
        assertTrue(Capitalization.wantsShift(true, true, cursorCapsMode = 4))
        // Setting off, or a field that never capitalizes.
        assertFalse(Capitalization.wantsShift(false, true, cursorCapsMode = 4))
        assertFalse(Capitalization.wantsShift(true, false, cursorCapsMode = 4))
    }

    @Test
    fun initialMode_usesCursorContext() {
        assertEquals(KeyboardMode.NUMPAD, Capitalization.initialMode(true, true))
        assertEquals(KeyboardMode.UPPERCASE, Capitalization.initialMode(false, true))
        assertEquals(KeyboardMode.LOWERCASE, Capitalization.initialMode(false, false))
    }

    @Test
    fun applyCursorShift_onlyTouchesLetterShift() {
        assertEquals(
            KeyboardMode.UPPERCASE,
            Capitalization.applyCursorShift(KeyboardMode.LOWERCASE, wantsShift = true)
        )
        assertEquals(
            KeyboardMode.LOWERCASE,
            Capitalization.applyCursorShift(KeyboardMode.UPPERCASE, wantsShift = false)
        )
        assertEquals(
            KeyboardMode.CAPS_LOCKED,
            Capitalization.applyCursorShift(KeyboardMode.CAPS_LOCKED, wantsShift = false)
        )
        assertEquals(
            KeyboardMode.SYMBOLS,
            Capitalization.applyCursorShift(KeyboardMode.SYMBOLS, wantsShift = true)
        )
        assertEquals(
            KeyboardMode.EMOJI,
            Capitalization.applyCursorShift(KeyboardMode.EMOJI, wantsShift = true)
        )
    }

    @Test
    fun lettersMode_restoresShiftFromCursor() {
        assertEquals(KeyboardMode.UPPERCASE, Capitalization.lettersMode(true))
        assertEquals(KeyboardMode.LOWERCASE, Capitalization.lettersMode(false))
    }
}
