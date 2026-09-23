package io.github.xxparthparekhxx.composekeyboard.data

import android.text.InputType

/**
 * The Android [InputType] class/variation mapped onto the keyboard this IME
 * should open. Every [InputType.TYPE_MASK_CLASS] value has a case here so
 * phone, email, URI, password and datetime fields do not all fall through to
 * generic QWERTY or a calculator pad.
 */
enum class FieldInputKind {
    TEXT,
    EMAIL,
    URI,
    PASSWORD,
    NUMBER,
    DECIMAL,
    NUMBER_PASSWORD,
    PHONE,
    DATETIME,
    OTP,

    /**
     * Terminal emulators ([isRawKeyEditor]): QWERTY, but no suggestion strip
     * and no swipe typing. Every keystroke goes straight to a shell, where
     * completions and auto-spaced words get in the way (as in Gboard).
     */
    TERMINAL;

    val opensAsNumpad: Boolean
        get() = this == NUMBER ||
            this == DECIMAL ||
            this == NUMBER_PASSWORD ||
            this == PHONE ||
            this == DATETIME ||
            this == OTP

    val allowsSuggestions: Boolean
        get() = this == TEXT || this == EMAIL || this == URI

    val allowsSwipe: Boolean
        get() = this == TEXT

    companion object {
        /**
         * Word completions / swipe suggestions. Password, PIN, and
         * [InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS] all suppress the strip;
         * OTP chips are independent and still shown via [OtpCodes].
         */
        fun allowsWordSuggestions(inputType: Int, kind: FieldInputKind): Boolean {
            if (!kind.allowsSuggestions) return false
            if (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return false
            return true
        }

        /**
         * True for editors with no text class ([InputType.TYPE_NULL]), such as
         * terminal emulators like Termux. They keep no text buffer, so
         * `getTextBeforeCursor` reports nothing and edits must be sent as key
         * events (e.g. `KEYCODE_DEL`) instead. Termux's "enforce char based
         * input" mode sets variation bits without a class, so only the class
         * is checked.
         */
        fun isRawKeyEditor(inputType: Int): Boolean =
            inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_NULL

        fun from(inputType: Int, isOtp: Boolean = false): FieldInputKind {
            if (isOtp) {
                val klass = inputType and InputType.TYPE_MASK_CLASS
                if (klass == InputType.TYPE_CLASS_NUMBER ||
                    klass == InputType.TYPE_CLASS_PHONE
                ) {
                    return OTP
                }
            }
            return when (inputType and InputType.TYPE_MASK_CLASS) {
                InputType.TYPE_CLASS_NUMBER -> {
                    val variation = inputType and InputType.TYPE_MASK_VARIATION
                    when {
                        variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD -> NUMBER_PASSWORD
                        inputType and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0 -> DECIMAL
                        else -> NUMBER
                    }
                }
                InputType.TYPE_CLASS_PHONE -> PHONE
                InputType.TYPE_CLASS_DATETIME -> DATETIME
                InputType.TYPE_NULL -> TERMINAL
                InputType.TYPE_CLASS_TEXT -> when (inputType and InputType.TYPE_MASK_VARIATION) {
                    InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                    InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> EMAIL
                    InputType.TYPE_TEXT_VARIATION_URI -> URI
                    InputType.TYPE_TEXT_VARIATION_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                    InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> PASSWORD
                    else -> TEXT
                }
                else -> TEXT
            }
        }
    }
}
