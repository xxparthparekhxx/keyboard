package io.github.xxparthparekhxx.composekeyboard.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * The history loads on a background thread. A clip captured right after
 * construction must merge with the persisted history, never be saved over it
 * before the load has read it.
 */
@RunWith(RobolectricTestRunner::class)
class ClipboardHistoryLoadTest {

    @Test
    fun clipAddedDuringLoad_keepsPersistedHistory() {
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.filesDir, "clipboard_history.json")
        val persisted = JSONArray()
        for (i in 1..5) {
            persisted.put(
                JSONObject()
                    .put("id", "old$i")
                    .put("text", "old clip $i")
                    .put("timestamp", System.currentTimeMillis() - i * 1_000L)
                    .put("isPinned", false)
            )
        }
        file.writeText(persisted.toString())

        // Repeated because a lost race is timing-dependent.
        repeat(25) { round ->
            file.writeText(persisted.toString())
            val manager = ClipboardHistoryManager(context)
            manager.addClip("new clip $round")

            val history = awaitHistorySize(manager, expected = 6)
            assertEquals("new clip $round", history.first().text)
            assertEquals(
                (1..5).map { "old clip $it" }.toSet(),
                history.drop(1).map { it.text }.toSet()
            )
        }
    }

    private fun awaitHistorySize(manager: ClipboardHistoryManager, expected: Int): List<ClipboardItem> {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            val current = manager.history.value
            if (current.size >= expected) return current
            Thread.sleep(5)
        }
        return manager.history.value
    }
}
