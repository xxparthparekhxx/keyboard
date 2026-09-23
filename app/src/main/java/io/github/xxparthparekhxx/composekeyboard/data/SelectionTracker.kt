package io.github.xxparthparekhxx.composekeyboard.data

/**
 * Tells our own edits apart from the user (or the app) moving the caret.
 *
 * Every edit we send predicts where the selection will end up. An
 * `onUpdateSelection` that lands on that prediction — or on the way to it,
 * when the editor is still catching up with a burst of edits — is our echo.
 * Anything else moved the caret behind our back.
 *
 * This replaces counting edits and waiting for one echo per edit. Editors
 * that never report selection changes (terminals such as Termux, some web
 * views) left that counter growing forever, so the next real caret move was
 * swallowed as if it were ours. A predicted position has no such drift: a
 * missing echo just leaves the prediction in place for the next update to be
 * compared against.
 *
 * Offsets are UTF-16 units, as in [android.view.inputmethod.InputConnection].
 * The same idea as AOSP LatinIME's `RichInputConnection.isBelatedExpectedUpdate`.
 */
class SelectionTracker {

    /** Where we expect the selection to be, or [UNKNOWN]. */
    var expectedStart: Int = UNKNOWN
        private set
    var expectedEnd: Int = UNKNOWN
        private set

    private val known: Boolean
        get() = expectedStart >= 0 && expectedEnd >= 0

    /** New input session: trust what the editor reports. Negative means unknown. */
    fun reset(selStart: Int, selEnd: Int) {
        if (selStart < 0 || selEnd < 0) {
            invalidate()
        } else {
            expectedStart = minOf(selStart, selEnd)
            expectedEnd = maxOf(selStart, selEnd)
        }
    }

    /**
     * An edit whose outcome we cannot predict (a key event: Enter may insert
     * a newline or run an action, DPAD may stop at a line end). The next
     * update is adopted as ours.
     */
    fun invalidate() {
        expectedStart = UNKNOWN
        expectedEnd = UNKNOWN
    }

    /** `commitText(text, 1)`: replaces the selection and puts the caret after [length] units. */
    fun onCommitText(length: Int) {
        if (!known) return
        val caret = expectedStart + length
        expectedStart = caret
        expectedEnd = caret
    }

    /** `deleteSurroundingText(before, 0)`: the selection shifts left by what was removed. */
    fun onDeleteBefore(before: Int) {
        if (!known) return
        val removed = minOf(before, expectedStart)
        expectedStart -= removed
        expectedEnd -= removed
    }

    /**
     * Feeds one `onUpdateSelection`. Returns true when it is the echo of our
     * own edits, false when something else moved the caret — in which case
     * the new position becomes the baseline for later predictions.
     */
    fun onUpdate(oldStart: Int, oldEnd: Int, newStart: Int, newEnd: Int): Boolean {
        if (!known) {
            reset(newStart, newEnd)
            return true
        }
        if (isEcho(oldStart, oldEnd, newStart, newEnd)) return true
        reset(newStart, newEnd)
        return false
    }

    private fun isEcho(oldStart: Int, oldEnd: Int, newStart: Int, newEnd: Int): Boolean {
        // Landed exactly where our edits said it would.
        if (newStart == expectedStart && newEnd == expectedEnd) return true
        // Started from where we expected and moved elsewhere: not us.
        if (oldStart == expectedStart && oldEnd == expectedEnd &&
            (oldStart != newStart || oldEnd != newEnd)
        ) {
            return false
        }
        // A belated update for an earlier edit in a burst: a caret that sits
        // between where it was and where we expect it to end up.
        return newStart == newEnd &&
            (newStart - oldStart).toLong() * (expectedStart - newStart) >= 0 &&
            (newEnd - oldEnd).toLong() * (expectedEnd - newEnd) >= 0
    }

    companion object {
        const val UNKNOWN: Int = -1
    }
}
