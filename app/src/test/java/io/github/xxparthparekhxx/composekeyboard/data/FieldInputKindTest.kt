package io.github.xxparthparekhxx.composekeyboard.data

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldInputKindTest {

    @Test
    fun from_coversEveryAndroidInputClass() {
        assertEquals(FieldInputKind.TEXT, FieldInputKind.from(InputType.TYPE_CLASS_TEXT))
        assertEquals(
            FieldInputKind.EMAIL,
            FieldInputKind.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        )
        assertEquals(
            FieldInputKind.EMAIL,
            FieldInputKind.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
        )
        assertEquals(
            FieldInputKind.URI,
            FieldInputKind.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        )
        assertEquals(
            FieldInputKind.PASSWORD,
            FieldInputKind.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        )
        assertEquals(
            FieldInputKind.PASSWORD,
            FieldInputKind.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
        )
        assertEquals(
            FieldInputKind.PASSWORD,
            FieldInputKind.from(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
        )
        assertEquals(FieldInputKind.TEXT, FieldInputKind.from(InputType.TYPE_NULL))
        assertEquals(FieldInputKind.NUMBER, FieldInputKind.from(InputType.TYPE_CLASS_NUMBER))
        assertEquals(
            FieldInputKind.NUMBER,
            FieldInputKind.from(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED)
        )
        assertEquals(
            FieldInputKind.DECIMAL,
            FieldInputKind.from(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        )
        assertEquals(
            FieldInputKind.NUMBER_PASSWORD,
            FieldInputKind.from(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        )
        assertEquals(FieldInputKind.PHONE, FieldInputKind.from(InputType.TYPE_CLASS_PHONE))
        assertEquals(FieldInputKind.DATETIME, FieldInputKind.from(InputType.TYPE_CLASS_DATETIME))
        assertEquals(
            FieldInputKind.DATETIME,
            FieldInputKind.from(InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME)
        )
    }

    @Test
    fun numpadKinds_openAsNumpad_textKindsDoNot() {
        assertTrue(FieldInputKind.PHONE.opensAsNumpad)
        assertTrue(FieldInputKind.NUMBER.opensAsNumpad)
        assertTrue(FieldInputKind.DECIMAL.opensAsNumpad)
        assertTrue(FieldInputKind.NUMBER_PASSWORD.opensAsNumpad)
        assertTrue(FieldInputKind.DATETIME.opensAsNumpad)
        assertFalse(FieldInputKind.TEXT.opensAsNumpad)
        assertFalse(FieldInputKind.EMAIL.opensAsNumpad)
        assertFalse(FieldInputKind.PASSWORD.opensAsNumpad)
    }

    @Test
    fun passwordAndPin_hideSuggestionsAndSwipe() {
        assertFalse(FieldInputKind.PASSWORD.allowsSuggestions)
        assertFalse(FieldInputKind.PASSWORD.allowsSwipe)
        assertFalse(FieldInputKind.NUMBER_PASSWORD.allowsSuggestions)
        assertFalse(FieldInputKind.NUMBER_PASSWORD.allowsSwipe)
        assertTrue(FieldInputKind.TEXT.allowsSuggestions)
        assertTrue(FieldInputKind.TEXT.allowsSwipe)
        assertTrue(FieldInputKind.EMAIL.allowsSuggestions)
        assertFalse(FieldInputKind.EMAIL.allowsSwipe)
        assertTrue(FieldInputKind.URI.allowsSuggestions)
        assertFalse(FieldInputKind.URI.allowsSwipe)
        assertFalse(FieldInputKind.PHONE.allowsSuggestions)
        assertFalse(FieldInputKind.DATETIME.allowsSwipe)
    }
}
