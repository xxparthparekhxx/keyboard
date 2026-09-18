package io.github.xxparthparekhxx.composekeyboard.input.swipe.nn

import io.github.xxparthparekhxx.composekeyboard.data.SwipeDictionaryTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the learn → bumpScore contract that [io.github.xxparthparekhxx.composekeyboard
 * .service.ComposeInputMethodService.learnWord] relies on: a non-null return
 * from [io.github.xxparthparekhxx.composekeyboard.data.SwipeDictionary.learn] is always
 * immediately bumpable into the live beam, while a null return (brand-new
 * word) is only visible after the next trie rebuild.
 */
class LearnBumpIntegrationTest {

    @Test
    fun relearnedWord_bumpAppliesToLiveBeam() {
        val dict = SwipeDictionaryTest.createTestDictionary()
        repeat(3) { dict.learn("hello") }
        val (words, scores) = dict.allWords()
        val beam = SwipeBeam.build(words, scores)

        // Reuse bumps the dictionary score; the beam must accept it in place.
        val newScore = dict.learn("hello")
        assertNotNull("expected in-lexicon learn to return a score", newScore)
        assertTrue(beam.bumpScore("hello", newScore!!))
    }

    @Test
    fun brandNewWord_isNotBumpableUntilRebuild() {
        val dict = SwipeDictionaryTest.createTestDictionary()
        repeat(3) { dict.learn("hello") }
        val (words, scores) = dict.allWords()
        val beam = SwipeBeam.build(words, scores)

        // First sighting: typo-guard holds it back, null means "needs rebuild".
        val first = dict.learn("quizzify")
        assertNull(first)
        assertFalse(beam.bumpScore("quizzify", 120))

        // Past the promotion threshold the trie rebuild picks it up.
        repeat(3) { dict.learn("quizzify") }
        val (words2, scores2) = dict.allWords()
        assertTrue("quizzify" in words2)
        val rebuilt = SwipeBeam.build(words2, scores2)
        val bumped = dict.learn("quizzify")
        assertNotNull(bumped)
        assertTrue(rebuilt.bumpScore("quizzify", bumped!!))
    }
}
