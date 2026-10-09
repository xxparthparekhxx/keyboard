package io.github.xxparthparekhxx.composekeyboard.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WordContextTest {

    private fun parse(text: String, truncated: Boolean = false) = WordContext.parse(text, truncated)

    @Test
    fun midWord_givesCurrentAndPrevious() {
        assertEquals(WordContext("mor", "good"), parse("say good mor"))
    }

    @Test
    fun afterSpace_givesPreviousOnly() {
        assertEquals(WordContext("", "good"), parse("say good "))
        assertEquals(WordContext("", "good"), parse("say good   "))
    }

    @Test
    fun emptyOrStartOfField_givesNothing() {
        assertEquals(WordContext.EMPTY, parse(""))
        assertEquals(WordContext("hel", null), parse("hel"))
        assertEquals(WordContext("", null), parse("   "))
    }

    @Test
    fun punctuation_endsTheContext() {
        assertEquals(WordContext("", null), parse("Done. "))
        assertEquals(WordContext("an", null), parse("hello, an"))
        assertEquals(WordContext("", null), parse("e.g. "))
    }

    @Test
    fun apostrophes_belongToWords() {
        assertEquals(WordContext("", "don't"), parse("I don't "))
    }

    @Test
    fun wordGluedToSymbol_hasNoPrevious() {
        assertEquals(WordContext("abc", null), parse("#abc"))
    }

    @Test
    fun newline_keepsTheWordBeforeIt() {
        assertEquals(WordContext("", "dear"), parse("Hello\ndear "))
    }

    @Test
    fun truncatedWindow_doesNotTrustAWordCutAtItsStart() {
        assertEquals(WordContext("", null), parse("lo ", truncated = true))
        assertEquals(WordContext.EMPTY, parse("abcdef", truncated = true))
        assertEquals(WordContext("", "two"), parse("one two ", truncated = true))
    }
}
