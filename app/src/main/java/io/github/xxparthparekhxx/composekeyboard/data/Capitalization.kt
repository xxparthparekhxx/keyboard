package io.github.xxparthparekhxx.composekeyboard.data

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Auto-capitalization derived from the editor's cursor, not from "this field
 * sometimes wants capitals so always start in shift".
 *
 * Android already knows whether the caret is at a sentence start, a word start,
 * or mid-word via [android.view.inputmethod.InputConnection.getCursorCapsMode].
 * That value survives keyboard dismiss, caret moves, and backspace — the local
 * last-committed-char heuristic does not.
 */
object Capitalization {

    /**
     * True when this field's input type is allowed to auto-shift. Password, URI
     * and e-mail variations are excluded — capitalizing those is never wanted.
     */
    fun fieldAllowsAutoCaps(info: EditorInfo): Boolean = fieldAllowsAutoCaps(info.inputType)

    fun fieldAllowsAutoCaps(inputType: Int): Boolean {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_URI ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        ) {
            return false
        }
        return inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0 ||
                inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0 ||
                inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0
    }

    /**
     * Whether the next letter should be shifted, given the user's setting, the
     * field type, and the editor's current [cursorCapsMode] (0 = no caps).
     */
    fun wantsShift(
        autoCapsEnabled: Boolean,
        fieldAllowsCaps: Boolean,
        cursorCapsMode: Int
    ): Boolean = autoCapsEnabled && fieldAllowsCaps && cursorCapsMode != 0

    fun initialMode(isNumericField: Boolean, wantsShift: Boolean): KeyboardMode = when {
        isNumericField -> KeyboardMode.NUMPAD
        wantsShift -> KeyboardMode.UPPERCASE
        else -> KeyboardMode.LOWERCASE
    }

    /** Letter-row mode to restore after symbols, emoji, clipboard, etc. */
    fun lettersMode(wantsShift: Boolean): KeyboardMode =
        if (wantsShift) KeyboardMode.UPPERCASE else KeyboardMode.LOWERCASE

    /**
     * Applies cursor-derived auto-shift without yanking the user out of
     * caps-lock, symbols, emoji, or other non-letter layouts.
     */
    fun applyCursorShift(current: KeyboardMode, wantsShift: Boolean): KeyboardMode = when (current) {
        KeyboardMode.LOWERCASE, KeyboardMode.UPPERCASE -> lettersMode(wantsShift)
        else -> current
    }
}
