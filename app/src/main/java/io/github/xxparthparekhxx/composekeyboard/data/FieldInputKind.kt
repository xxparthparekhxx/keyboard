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
    DATETIME;

    val opensAsNumpad: Boolean
        get() = this == NUMBER ||
            this == DECIMAL ||
            this == NUMBER_PASSWORD ||
            this == PHONE ||
            this == DATETIME

    val allowsSuggestions: Boolean
        get() = this == TEXT || this == EMAIL || this == URI

    val allowsSwipe: Boolean
        get() = this == TEXT

    companion object {
        fun from(inputType: Int): FieldInputKind {
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
