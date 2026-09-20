package io.github.xxparthparekhxx.composekeyboard.data

import android.os.Build
import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * SMS / email one-time-passcode detection, matching what Gboard surfaces as
 * an OTP chip above the keys.
 *
 * The IME never reads SMS itself. Codes arrive through:
 *  - Autofill inline suggestions (`smsOTPCode` / `oneTimeCode` hints)
 *  - the clipboard, when the user (or the SMS app) copied a short numeric code
 */
object OtpCodes {

    /** Autofill hint strings used by Android, Web, and androidx.autofill. */
    private val OTP_HINTS = setOf(
        "smsOTPCode",
        "emailOTPCode",
        "2faAppOTPCode",
        "oneTimeCode",
        "sms_otp",
        "otpCode",
        "otp",
        "2fa"
    )

    /**
     * Words that mark a field hint or a copied snippet as an OTP prompt.
     * `code` alone is deliberately included: a copied SMS body is only offered
     * as a code when it says so ("Your code is 123456"), which is what keeps
     * plain phone numbers and IDs out of the chip.
     */
    private val HINT_TEXT = Regex(
        """\b(otp|2fa|2-fa|one[-\s]?time|verif(?:y|ication)|security code|auth(?:entication)? code|code)\b""",
        RegexOption.IGNORE_CASE
    )

    private val STANDALONE_CODE = Regex("""^\d{4,8}$""")
    private val EMBEDDED_CODE = Regex("""(?<!\d)(\d{4,8})(?!\d)""")

    fun matchesField(
        inputType: Int,
        autofillHints: Array<String>?,
        hintText: CharSequence?,
        variationExtra: CharSequence? = null
    ): Boolean {
        if (autofillHints != null) {
            for (hint in autofillHints) {
                val key = hint.trim()
                if (key.isNotEmpty() && (key in OTP_HINTS || key.contains("otp", ignoreCase = true) ||
                        key.contains("oneTimeCode", ignoreCase = true))
                ) {
                    return true
                }
            }
        }
        val combined = buildString {
            if (!hintText.isNullOrBlank()) append(hintText).append(' ')
            if (!variationExtra.isNullOrBlank()) append(variationExtra)
        }
        if (combined.isNotEmpty() && HINT_TEXT.containsMatchIn(combined)) return true

        // Number fields that also ask for no suggestions are very often PIN/OTP
        // entry. Password-number (PIN) is handled separately by FieldInputKind.
        val klass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (klass == InputType.TYPE_CLASS_NUMBER &&
            variation != InputType.TYPE_NUMBER_VARIATION_PASSWORD &&
            inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0
        ) {
            return true
        }
        return false
    }

    fun isOtpField(info: EditorInfo): Boolean =
        matchesField(
            inputType = info.inputType,
            autofillHints = autofillHintsOf(info),
            hintText = info.hintText,
            variationExtra = info.privateImeOptions
        )

    private fun autofillHintsOf(info: EditorInfo): Array<String>? {
        if (Build.VERSION.SDK_INT < 26) return null
        return try {
            val field = EditorInfo::class.java.getField("autofillHints")
            @Suppress("UNCHECKED_CAST")
            field.get(info) as? Array<String>
        } catch (_: Exception) {
            null
        }
    }

    /** True when [text] itself is a 4–8 digit code, not a longer message. */
    fun isBareOtpCode(text: String): Boolean = STANDALONE_CODE.matches(text.trim())

    /**
     * Pulls a one-time code from clipboard text. Bare 4–8 digit strings win;
     * a longer snippet (an SMS body) is accepted only when it contains exactly
     * one such group *and* reads like a code prompt, so a copied phone number
     * or ID is not treated as an OTP.
     */
    fun fromClipboardText(text: String?): String? {
        val trimmed = text?.trim() ?: return null
        if (trimmed.isEmpty() || trimmed.length > 160) return null
        if (STANDALONE_CODE.matches(trimmed)) return trimmed
        if (!HINT_TEXT.containsMatchIn(trimmed)) return null
        val matches = EMBEDDED_CODE.findAll(trimmed).map { it.groupValues[1] }.toList()
        return matches.singleOrNull()
    }
}
