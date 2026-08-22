package com.example.composekeyboard.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SwipeDictionaryTest {

    @Test
    fun normalize_validWord_returnsLowercase() {
        val result = SwipeDictionary.normalize("Hello")
        assertEquals("hello", result)
    }

    @Test
    fun normalize_withApostrophe_keepsApostrophe() {
        val result = SwipeDictionary.normalize("don't")
        assertEquals("don't", result)
    }

    @Test
    fun normalize_withHyphen_keepsHyphen() {
        val result = SwipeDictionary.normalize("well-known")
        assertEquals("well-known", result)
    }

    @Test
    fun normalize_tooShort_returnsNull() {
        val result = SwipeDictionary.normalize("a")
        assertNull(result)
    }

    @Test
    fun normalize_tooLong_returnsNull() {
        val longWord = "a".repeat(SwipeDictionary.MAX_WORD_LENGTH + 1)
        val result = SwipeDictionary.normalize(longWord)
        assertNull(result)
    }

    @Test
    fun normalize_withNumbers_returnsNull() {
        val result = SwipeDictionary.normalize("hello123")
        assertNull(result)
    }

    @Test
    fun normalize_onlyApostrophes_returnsNull() {
        val result = SwipeDictionary.normalize("''")
        assertNull(result)
    }

    @Test
    fun keySequenceOf_validWord_returnsIndices() {
        val result = SwipeDictionary.keySequenceOf("cat")
        assertNotNull(result)
        assertEquals(3, result?.size)
        assertEquals('c' - 'a', result?.get(0)?.toInt())
        assertEquals('a' - 'a', result?.get(1)?.toInt())
        assertEquals('t' - 'a', result?.get(2)?.toInt())
    }

    @Test
    fun keySequenceOf_skipsApostrophe() {
        val result = SwipeDictionary.keySequenceOf("don't")
        assertNotNull(result)
        assertEquals(4, result?.size) // d, o, n, t
        assertEquals('d' - 'a', result?.get(0)?.toInt())
        assertEquals('o' - 'a', result?.get(1)?.toInt())
        assertEquals('n' - 'a', result?.get(2)?.toInt())
        assertEquals('t' - 'a', result?.get(3)?.toInt())
    }

    @Test
    fun keySequenceOf_skipsHyphen() {
        val result = SwipeDictionary.keySequenceOf("well-known")
        assertNotNull(result)
        // w, e, l, l, k, n, o, w, n = 9 letters
        assertEquals(9, result?.size)
    }

    @Test
    fun keySequenceOf_empty_returnsNull() {
        val result = SwipeDictionary.keySequenceOf("")
        assertNull(result)
    }

    @Test
    fun keySequenceOf_tooLong_returnsNull() {
        val longWord = "a".repeat(SwipeDictionary.MAX_WORD_LENGTH + 1)
        val result = SwipeDictionary.keySequenceOf(longWord)
        assertNull(result)
    }
}