package com.example.composekeyboard.data

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class ClipboardItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)

class ClipboardHistoryManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val historyFile = File(context.filesDir, "clipboard_history.json")

    private val _history = MutableStateFlow<List<ClipboardItem>>(emptyList())
    val history: StateFlow<List<ClipboardItem>> = _history.asStateFlow()

    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureCurrentClip()
    }

    init {
        loadHistory()
        try {
            clipboardManager?.addPrimaryClipChangedListener(clipListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun captureCurrentClip() {
        try {
            val cm = clipboardManager ?: return
            if (cm.hasPrimaryClip() &&
                cm.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
            ) {
                val clip = cm.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0)?.coerceToText(context)?.toString()
                    if (!text.isNullOrBlank()) {
                        addClip(text.trim())
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun addClip(text: String) {
        if (text.isBlank()) return
        scope.launch {
            val current = _history.value.toMutableList()
            // Check if already present
            val existingIndex = current.indexOfFirst { it.text == text }
            if (existingIndex >= 0) {
                val existing = current.removeAt(existingIndex)
                // Move to top and update timestamp
                current.add(0, existing.copy(timestamp = System.currentTimeMillis()))
            } else {
                // Add new item
                current.add(0, ClipboardItem(text = text, timestamp = System.currentTimeMillis()))
            }

            // Keep max 50 items, but preserve pinned ones
            val pinned = current.filter { it.isPinned }
            val unpinned = current.filter { !it.isPinned }.take(50 - pinned.size.coerceAtMost(30))
            val combined = (pinned + unpinned).sortedByDescending { it.timestamp }

            _history.value = combined
            saveHistory(combined)
        }
    }

    fun togglePin(id: String) {
        scope.launch {
            val updated = _history.value.map {
                if (it.id == id) it.copy(isPinned = !it.isPinned) else it
            }
            _history.value = updated
            saveHistory(updated)
        }
    }

    fun deleteClip(id: String) {
        scope.launch {
            val updated = _history.value.filter { it.id != id }
            _history.value = updated
            saveHistory(updated)
        }
    }

    fun clearAllUnpinned() {
        scope.launch {
            val updated = _history.value.filter { it.isPinned }
            _history.value = updated
            saveHistory(updated)
        }
    }

    fun copyToSystemClipboard(text: String) {
        try {
            val clip = ClipData.newPlainText("text", text)
            clipboardManager?.setPrimaryClip(clip)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadHistory() {
        scope.launch {
            if (!historyFile.exists()) {
                _history.value = emptyList()
                return@launch
            }
            try {
                val jsonStr = historyFile.readText()
                val jsonArr = JSONArray(jsonStr)
                val items = mutableListOf<ClipboardItem>()
                for (i in 0 until jsonArr.length()) {
                    val obj = jsonArr.getJSONObject(i)
                    items.add(
                        ClipboardItem(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            text = obj.getString("text"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            isPinned = obj.optBoolean("isPinned", false)
                        )
                    )
                }
                _history.value = items.sortedByDescending { it.timestamp }
            } catch (e: Exception) {
                e.printStackTrace()
                _history.value = emptyList()
            }
        }
    }

    private fun saveHistory(items: List<ClipboardItem>) {
        try {
            val jsonArr = JSONArray()
            items.forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("text", item.text)
                    put("timestamp", item.timestamp)
                    put("isPinned", item.isPinned)
                }
                jsonArr.put(obj)
            }
            historyFile.writeText(jsonArr.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ClipboardHistoryManager? = null

        fun getInstance(context: Context): ClipboardHistoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ClipboardHistoryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
