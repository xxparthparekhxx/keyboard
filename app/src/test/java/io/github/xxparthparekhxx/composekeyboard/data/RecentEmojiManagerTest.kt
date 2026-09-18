package io.github.xxparthparekhxx.composekeyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentEmojiManagerTest {

    @Test
    fun updateRecents_prependsNewEmojiToFront() {
        val initial = listOf("🔥", "👍", "😂")
        val result = RecentEmojiManager.updateRecents(initial, "✨")

        assertEquals("✨", result.first())
        assertEquals(listOf("✨", "🔥", "👍", "😂"), result)
    }

    @Test
    fun updateRecents_existingEmoji_movesToFrontWithoutDuplicates() {
        val initial = listOf("🔥", "👍", "😂", "✨")
        val result = RecentEmojiManager.updateRecents(initial, "😂")

        assertEquals("😂", result.first())
        assertEquals(1, result.count { it == "😂" })
        assertEquals(listOf("😂", "🔥", "👍", "✨"), result)
    }

    @Test
    fun updateRecents_respectsMaxCount() {
        val initial = (1..50).map { "$it" }
        val result = RecentEmojiManager.updateRecents(initial, "🎉", maxCount = 42)

        assertEquals(42, result.size)
        assertEquals("🎉", result.first())
    }

    @Test
    fun updateRecents_blankEmoji_returnsOriginal() {
        val initial = listOf("🔥", "👍")
        val result = RecentEmojiManager.updateRecents(initial, "")

        assertEquals(initial, result)
    }

    @Test
    fun defaultRecents_isNotEmptyAndHasUniqueEmojis() {
        assertTrue(RecentEmojiManager.DEFAULT_RECENTS.isNotEmpty())
        assertEquals(RecentEmojiManager.DEFAULT_RECENTS.distinct().size, RecentEmojiManager.DEFAULT_RECENTS.size)
    }
}
