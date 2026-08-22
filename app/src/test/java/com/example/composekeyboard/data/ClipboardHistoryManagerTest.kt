package com.example.composekeyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardHistoryManagerTest {

    @Test
    fun trim_respectsMaxItems() {
        val items = (1..60).map { i ->
            ClipboardItem(id = "id$i", text = "text$i", timestamp = i.toLong())
        }

        val trimmed = ClipboardHistoryManager.trimItems(items)

        assertEquals(ClipboardHistoryManager.MAX_ITEMS, trimmed.size)
        // Newest items survive; oldest are dropped.
        assertEquals("text60", trimmed[0].text)
        assertEquals("text11", trimmed.last().text)
    }

    @Test
    fun trim_underLimit_keepsEverything() {
        val items = (1..10).map { i ->
            ClipboardItem(id = "id$i", text = "text$i", timestamp = i.toLong())
        }

        val trimmed = ClipboardHistoryManager.trimItems(items)

        assertEquals(10, trimmed.size)
    }

    @Test
    fun trim_respectsPinnedLimit() {
        val items = mutableListOf<ClipboardItem>()
        // 25 pinned items (MAX_PINNED = 20)
        for (i in 1..25) {
            items.add(
                ClipboardItem(
                    id = "pin$i", text = "pin$i",
                    timestamp = i.toLong(), isPinned = true
                )
            )
        }
        // 30 unpinned items, newer than the pins
        for (i in 1..30) {
            items.add(
                ClipboardItem(id = "unpin$i", text = "unpin$i", timestamp = (i + 100).toLong())
            )
        }

        val trimmed = ClipboardHistoryManager.trimItems(items)

        assertTrue(trimmed.count { it.isPinned } <= ClipboardHistoryManager.MAX_PINNED)
        assertTrue(trimmed.size <= ClipboardHistoryManager.MAX_ITEMS)
    }

    @Test
    fun trim_demotesOldestExcessPins() {
        val items = (1..25).map { i ->
            ClipboardItem(
                id = "pin$i", text = "pin$i",
                timestamp = i.toLong(), isPinned = true
            )
        }

        val trimmed = ClipboardHistoryManager.trimItems(items)

        // The 5 oldest pins are demoted but not deleted.
        val demoted = trimmed.filter { it.id in (1..5).map { n -> "pin$n" } }
        assertEquals(5, demoted.size)
        assertTrue(demoted.all { !it.isPinned })
        // The newest 20 stay pinned.
        assertEquals(20, trimmed.count { it.isPinned })
    }

    @Test
    fun trim_pinnedItemsAreNeverDropped() {
        val items = mutableListOf<ClipboardItem>()
        for (i in 1..20) {
            items.add(
                ClipboardItem(
                    id = "pin$i", text = "pin$i",
                    timestamp = i.toLong(), isPinned = true
                )
            )
        }
        // 40 newer unpinned items would push the pins out if they weren't protected
        for (i in 1..40) {
            items.add(
                ClipboardItem(id = "unpin$i", text = "unpin$i", timestamp = (i + 100).toLong())
            )
        }

        val trimmed = ClipboardHistoryManager.trimItems(items)

        assertEquals(ClipboardHistoryManager.MAX_ITEMS, trimmed.size)
        assertEquals(20, trimmed.count { it.isPinned })
    }

    @Test
    fun trim_emptyList_returnsEmpty() {
        val trimmed = ClipboardHistoryManager.trimItems(emptyList())

        assertTrue(trimmed.isEmpty())
    }
}
