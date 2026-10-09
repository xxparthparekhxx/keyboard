package io.github.xxparthparekhxx.composekeyboard.data

/**
 * The word being typed at the caret and the finished word before it, read
 * back from the editor's text.
 *
 * Used when the keyboard cannot know what is before the caret from its own
 * edits: a new field, or the caret moved by the user or the app. [current]
 * drives completions and [previous] drives next-word prediction.
 */
data class WordContext(val current: String, val previous: String?) {

    companion object {
        val EMPTY = WordContext("", null)

        /** Characters that continue a word, as the service tracks it. */
        fun isWordChar(c: Char): Boolean = c.isLetter() || c == '\''

        /**
         * Parses the text before the caret.
         *
         * [previous] is only set when the word before [current] is separated
         * from it by spaces alone: punctuation or a line break ends the
         * context, because predicting across a sentence boundary is noise.
         *
         * [truncated] says [textBeforeCaret] is a window that may have cut
         * a word in half at its start; such a word is not trusted.
         */
        fun parse(textBeforeCaret: CharSequence, truncated: Boolean = false): WordContext {
            var end = textBeforeCaret.length
            var start = end
            while (start > 0 && isWordChar(textBeforeCaret[start - 1])) start--
            if (start == 0 && truncated && end > 0) return EMPTY
            val current = textBeforeCaret.substring(start, end)

            end = start
            while (end > 0 && textBeforeCaret[end - 1] == ' ') end--
            // No space at all means `current` is glued to something (a symbol,
            // a digit), and nothing precedes it as far as words go.
            if (end == start || end == 0) return WordContext(current, null)
            start = end
            while (start > 0 && isWordChar(textBeforeCaret[start - 1])) start--
            if (start == end) return WordContext(current, null)
            if (start == 0 && truncated) return WordContext(current, null)
            // "e.g. word": a word glued to punctuation on its left is not a
            // clean predecessor either.
            if (start > 0 && !textBeforeCaret[start - 1].isWhitespace()) {
                return WordContext(current, null)
            }
            return WordContext(current, textBeforeCaret.substring(start, end))
        }
    }
}
