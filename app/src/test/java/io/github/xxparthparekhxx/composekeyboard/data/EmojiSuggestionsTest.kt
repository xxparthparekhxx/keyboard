package io.github.xxparthparekhxx.composekeyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiSuggestionsTest {

    @Test
    fun emojisFor_exactKeyword_returnsAssociatedEmojis() {
        val smileys = EmojiSuggestions.emojisFor("smile", maxCount = 3)
        assertFalse(smileys.isEmpty())
        assertTrue(smileys.contains("😊"))

        val fires = EmojiSuggestions.emojisFor("fire", maxCount = 2)
        assertFalse(fires.isEmpty())
        assertTrue(fires.contains("🔥"))
    }

    @Test
    fun emojisFor_prefixQuery_returnsMatchingEmojis() {
        val matches = EmojiSuggestions.emojisFor("happ", maxCount = 3)
        assertFalse(matches.isEmpty())
        assertTrue(matches.contains("😊"))
    }

    @Test
    fun emojisFor_shortOrInvalidQuery_returnsEmpty() {
        assertTrue(EmojiSuggestions.emojisFor("").isEmpty())
        assertTrue(EmojiSuggestions.emojisFor("a").isEmpty())
        assertTrue(EmojiSuggestions.emojisFor("123").isEmpty())
        assertTrue(EmojiSuggestions.emojisFor("!@#").isEmpty())
    }

    @Test
    fun emojisFor_respectsMaxCount() {
        val matches = EmojiSuggestions.emojisFor("love", maxCount = 2)
        assertTrue(matches.size <= 2)
    }

    @Test
    fun searchAll_findsExactPrefixAndSubstringMatches() {
        val fireResults = EmojiSuggestions.searchAll("fire", maxCount = 10)
        assertTrue(fireResults.contains("🔥"))

        val subResults = EmojiSuggestions.searchAll("app", maxCount = 10)
        // Should match "apple" and "happy"
        assertTrue(subResults.isNotEmpty())
    }

    @Test
    fun searchAll_blankQuery_returnsEmpty() {
        assertTrue(EmojiSuggestions.searchAll("").isEmpty())
        assertTrue(EmojiSuggestions.searchAll("   ").isEmpty())
    }
}
