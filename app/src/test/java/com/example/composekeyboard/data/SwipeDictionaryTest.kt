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

    @Test
    fun getCompletions_returnsMatchingWordsAndHandlesCasing() {
        val dict = createTestDictionary()
        // Learn words 3 times so they pass NEW_WORD_THRESHOLD (12)
        repeat(3) {
            dict.learn("help")
            dict.learn("hello")
            dict.learn("hero")
            dict.learn("world")
        }

        val completions = dict.getCompletions("he")
        assertTrue(completions.contains("help"))
        assertTrue(completions.contains("hello"))
        assertTrue(completions.contains("hero"))
        assertFalse(completions.contains("world"))

        // Exact match comes first
        val exactMatchCompletions = dict.getCompletions("help")
        assertEquals("help", exactMatchCompletions.first())

        // Capitalized prefix
        val capCompletions = dict.getCompletions("He")
        assertTrue(capCompletions.contains("Help"))
        assertTrue(capCompletions.contains("Hello"))

        // Uppercase prefix
        val upperCompletions = dict.getCompletions("HE")
        assertTrue(upperCompletions.contains("HELP"))
        assertTrue(upperCompletions.contains("HELLO"))
    }

    @Test
    fun getCompletions_emptyWhenUnloadedOrInvalid() {
        val dict = createTestDictionary()
        // Unloaded dictionary returns empty
        val isLoadedField = SwipeDictionary::class.java.getDeclaredField("isLoaded")
        isLoadedField.isAccessible = true
        isLoadedField.set(dict, false)

        assertTrue(dict.getCompletions("he").isEmpty())
        assertTrue(dict.getCompletions("").isEmpty())
        assertTrue(dict.getCompletions("123").isEmpty())
    }

    private class DummyContext : android.content.ContextWrapper(null) {
        override fun getApplicationContext(): android.content.Context = this
        override fun getFilesDir(): File = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
    }

    companion object {
        fun createTestDictionary(): SwipeDictionary {
            val dummyContext = DummyContext()
            val constructor = SwipeDictionary::class.java.getDeclaredConstructor(android.content.Context::class.java)
            constructor.isAccessible = true
            val dict = constructor.newInstance(dummyContext)

            val isLoadedField = SwipeDictionary::class.java.getDeclaredField("isLoaded")
            isLoadedField.isAccessible = true
            isLoadedField.set(dict, true)

            return dict
        }
    }
}