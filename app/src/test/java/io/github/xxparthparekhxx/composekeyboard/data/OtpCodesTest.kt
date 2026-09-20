package io.github.xxparthparekhxx.composekeyboard.data

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpCodesTest {

    @Test
    fun isOtpField_smsHint() {
        assertTrue(
            OtpCodes.matchesField(
                inputType = InputType.TYPE_CLASS_NUMBER,
                autofillHints = arrayOf("smsOTPCode"),
                hintText = null
            )
        )
        assertTrue(
            OtpCodes.matchesField(
                inputType = InputType.TYPE_CLASS_TEXT,
                autofillHints = arrayOf("oneTimeCode"),
                hintText = null
            )
        )
    }

    @Test
    fun isOtpField_hintText() {
        assertTrue(
            OtpCodes.matchesField(
                inputType = InputType.TYPE_CLASS_TEXT,
                autofillHints = null,
                hintText = "Enter verification code"
            )
        )
        assertFalse(
            OtpCodes.matchesField(
                inputType = InputType.TYPE_CLASS_TEXT,
                autofillHints = null,
                hintText = "Email address"
            )
        )
    }

    @Test
    fun fromClipboardText_bareDigits() {
        assertEquals("123456", OtpCodes.fromClipboardText("123456"))
        assertEquals("0000", OtpCodes.fromClipboardText("  0000  "))
        assertNull(OtpCodes.fromClipboardText("123"))
        assertNull(OtpCodes.fromClipboardText("123456789"))
    }

    @Test
    fun fromClipboardText_embeddedInSms() {
        assertEquals("847291", OtpCodes.fromClipboardText("Your code is 847291. Do not share it."))
        assertNull(OtpCodes.fromClipboardText("Call 5551234 or 5559876"))
        assertNull(OtpCodes.fromClipboardText(null))
        assertNull(OtpCodes.fromClipboardText(""))
    }

    @Test
    fun fromClipboardText_embeddedCodeNeedsOtpLanguage() {
        assertNull(OtpCodes.fromClipboardText("Call 555-1234567 now"))
        assertEquals("482913", OtpCodes.fromClipboardText("Please enter the code 482913 to verify."))
        assertEquals("987654", OtpCodes.fromClipboardText("Your security code is 987654."))
    }

    @Test
    fun isBareOtpCode() {
        assertTrue(OtpCodes.isBareOtpCode("847291"))
        assertFalse(OtpCodes.isBareOtpCode("Your code is 847291"))
        assertFalse(OtpCodes.isBareOtpCode("12ab56"))
    }
}
