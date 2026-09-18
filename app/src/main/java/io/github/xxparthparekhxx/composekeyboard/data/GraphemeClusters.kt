package io.github.xxparthparekhxx.composekeyboard.data

/**
 * Backspace helpers that delete one user-perceived character, not one UTF-16
 * code unit. Most emoji are surrogate pairs (two Java chars); ZWJ sequences,
 * flags, skin tones and keycaps are even longer. Deleting a single `char`
 * splits those and leaves a broken glyph that needs a second backspace.
 */
object GraphemeClusters {

    /**
     * UTF-16 units of the last extended grapheme cluster in [text], or 0 if
     * [text] is empty. Suitable for [android.view.inputmethod.InputConnection.deleteSurroundingText].
     */
    fun utf16LengthOfLastCluster(text: CharSequence): Int {
        if (text.isEmpty()) return 0
        return text.length - startOfLastCluster(text)
    }

    internal fun startOfLastCluster(text: CharSequence): Int {
        val end = text.length
        if (end <= 0) return 0

        var pos = offsetBefore(text, end)
        val last = codePointAt(text, pos)

        if (last == '\n'.code && pos > 0) {
            val prevPos = offsetBefore(text, pos)
            if (codePointAt(text, prevPos) == '\r'.code) return prevPos
        }

        if (isRegionalIndicator(last)) {
            var riCount = 1
            var scan = pos
            while (scan > 0) {
                val prevPos = offsetBefore(text, scan)
                if (!isRegionalIndicator(codePointAt(text, prevPos))) break
                riCount++
                scan = prevPos
            }
            // RI characters pair from the left. An even run ends on a complete
            // flag; an odd run leaves a leftover singleton as the last cluster.
            return if (riCount % 2 == 0) offsetBefore(text, pos) else pos
        }

        while (pos > 0) {
            val prevPos = offsetBefore(text, pos)
            val prev = codePointAt(text, prevPos)
            val curr = codePointAt(text, pos)
            if (isGraphemeExtend(curr) || isZwj(curr) || isZwj(prev)) {
                pos = prevPos
                continue
            }
            break
        }
        return pos
    }

    private fun offsetBefore(text: CharSequence, offset: Int): Int {
        if (offset <= 0) return 0
        val cp = Character.codePointBefore(text, offset)
        return offset - Character.charCount(cp)
    }

    private fun codePointAt(text: CharSequence, index: Int): Int =
        Character.codePointAt(text, index)

    private fun isZwj(cp: Int): Boolean = cp == 0x200D

    private fun isRegionalIndicator(cp: Int): Boolean = cp in 0x1F1E6..0x1F1FF

    /**
     * Marks, variation selectors, skin tones, tags and ZWJ — anything that
     * glues to the previous code point as part of the same glyph.
     */
    private fun isGraphemeExtend(cp: Int): Boolean {
        if (isZwj(cp)) return true
        if (cp in 0xFE00..0xFE0F) return true
        if (cp in 0xE0100..0xE01EF) return true
        if (cp in 0x1F3FB..0x1F3FF) return true
        if (cp in 0xE0020..0xE007F) return true
        if (cp == 0x20E3) return true
        val type = Character.getType(cp)
        return type == Character.NON_SPACING_MARK.toInt() ||
            type == Character.ENCLOSING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt()
    }
}
