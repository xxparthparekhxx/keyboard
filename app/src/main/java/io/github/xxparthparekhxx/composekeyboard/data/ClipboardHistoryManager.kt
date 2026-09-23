package io.github.xxparthparekhxx.composekeyboard.data

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val historyFile = File(context.filesDir, "clipboard_history.json")

    private val _history = MutableStateFlow<List<ClipboardItem>>(emptyList())
    val history: StateFlow<List<ClipboardItem>> = _history.asStateFlow()

    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureCurrentClip()
    }

    /**
     * Serializes every read-modify-write of the history and every disk write,
     * so concurrent copies/pins/deletes can never lose updates and saves can
     * never interleave. [loadHistory] runs under it too, which guarantees no
     * mutation lands before the persisted state has been restored.
     */
    private val mutex = Mutex()

    init {
        loadHistory()
        try {
            clipboardManager?.addPrimaryClipChangedListener(clipListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register clipboard listener", e)
        }
    }

    fun captureCurrentClip() {
        pruneExpired()
        try {
            val text = peekPlainText() ?: return
            if (OtpCodes.isBareOtpCode(text)) return
            addClip(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture clipboard contents", e)
        }
    }

    /**
     * Current plain-text clip, or null. Does not persist anything — used to
     * offer an OTP chip without writing the code into clipboard history.
     */
    fun peekPlainText(): String? {
        return try {
            val cm = clipboardManager ?: return null
            val desc = cm.primaryClipDescription ?: return null
            if (isSensitiveClip(desc)) return null
            // text/* rather than text/plain: browsers copy as text/html only,
            // and coerceToText() below yields the plain text for those.
            if (!cm.hasPrimaryClip() || !desc.hasMimeType("text/*")) {
                return null
            }
            val clip = cm.primaryClip ?: return null
            if (clip.itemCount <= 0) return null
            clip.getItemAt(0)?.coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read clipboard contents", e)
            null
        }
    }

    fun addClip(text: String) {
        if (text.isBlank()) return
        if (OtpCodes.isBareOtpCode(text)) return
        scope.launch {
            mutex.withLock {
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
                _history.value = trim(current)
                saveHistoryLocked(_history.value)
            }
        }
    }

    /**
     * Forgets unpinned clips older than [UNPINNED_TTL_MS]. Called whenever the
     * keyboard opens, so a copied password or address does not sit in history
     * (and on disk) indefinitely just because its app did not mark it sensitive.
     */
    fun pruneExpired() {
        scope.launch {
            mutex.withLock {
                val current = _history.value
                val kept = dropExpired(current, System.currentTimeMillis())
                if (kept.size != current.size) {
                    _history.value = kept
                    saveHistoryLocked(kept)
                }
            }
        }
    }

    fun togglePin(id: String) {
        scope.launch {
            mutex.withLock {
                val updated = _history.value.map {
                    if (it.id == id) it.copy(isPinned = !it.isPinned) else it
                }
                _history.value = trim(updated)
                saveHistoryLocked(_history.value)
            }
        }
    }

    fun deleteClip(id: String) {
        scope.launch {
            mutex.withLock {
                val updated = _history.value.filter { it.id != id }
                _history.value = updated
                saveHistoryLocked(updated)
            }
        }
    }

    fun clearAllUnpinned() {
        scope.launch {
            mutex.withLock {
                val updated = _history.value.filter { it.isPinned }
                _history.value = updated
                saveHistoryLocked(updated)
            }
        }
    }

    fun copyToSystemClipboard(text: String) {
        try {
            val clip = ClipData.newPlainText("text", text)
            clipboardManager?.setPrimaryClip(clip)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy to system clipboard", e)
        }
    }

    /**
     * Caps the list: at most [MAX_PINNED] pinned entries survive (oldest excess
     * pins are demoted rather than deleted), and the total never exceeds
     * [MAX_ITEMS], keeping the newest entries.
     */
    private fun trim(items: List<ClipboardItem>): List<ClipboardItem> =
        trimItems(dropExpired(items, System.currentTimeMillis()))

    private fun loadHistory() {
        scope.launch {
            mutex.withLock {
                if (!historyFile.exists()) {
                    _history.value = emptyList()
                    return@withLock
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
                    _history.value = trim(items).sortedByDescending { it.timestamp }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read clipboard history; starting fresh", e)
                    _history.value = emptyList()
                }
            }
        }
    }

    /** Caller must hold [mutex]. Writes atomically via temp file + rename. */
    private fun saveHistoryLocked(items: List<ClipboardItem>) {
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
            writeAtomically(historyFile, jsonArr.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist clipboard history", e)
        }
    }

    companion object {
        private const val TAG = "ClipboardHistory"

        const val MAX_ITEMS = 50
        const val MAX_PINNED = 20

        /** Unpinned clips are kept for an hour, as in Gboard. Pins never expire. */
        const val UNPINNED_TTL_MS: Long = 60 * 60 * 1000L

        /**
         * Drops unpinned items copied more than [UNPINNED_TTL_MS] before [now].
         * A timestamp in the future (clock moved back) counts as fresh rather
         * than making the item immortal or deleting it early.
         */
        internal fun dropExpired(items: List<ClipboardItem>, now: Long): List<ClipboardItem> =
            items.filter { it.isPinned || now - it.timestamp <= UNPINNED_TTL_MS || it.timestamp > now }

        /**
         * Pure trimming logic, split out of [trim] so it can be unit tested
         * without an Android Context.
         *
         * Two passes: the first counts surviving pins so their slots are
         * reserved before unpinned entries are admitted — otherwise newer
         * unpinned items could crowd the list past [MAX_ITEMS] and evict
         * pins that arrive later in the sort order.
         */
        internal fun trimItems(items: List<ClipboardItem>): List<ClipboardItem> {
            val sorted = items.sortedByDescending { it.timestamp }

            var reservedPins = 0
            for (item in sorted) {
                if (item.isPinned && reservedPins < MAX_PINNED) reservedPins++
            }

            val kept = ArrayList<ClipboardItem>(MAX_ITEMS)
            var pinned = 0
            for (item in sorted) {
                if (kept.size >= MAX_ITEMS) break
                if (item.isPinned && pinned < MAX_PINNED) {
                    pinned++
                    kept.add(item)
                } else if (kept.size < MAX_ITEMS - reservedPins + pinned) {
                    kept.add(if (item.isPinned) item.copy(isPinned = false) else item)
                }
            }
            return kept
        }

        /**
         * Crash-safe write: serialize to a sibling temp file first, then swap
         * it in with an atomic rename so a power loss can never leave a
         * truncated or half-written target behind.
         */
        fun writeAtomically(target: File, text: String) {
            val tmp = File(target.parentFile, target.name + ".tmp")
            try {
                tmp.writeText(text)
                if (!tmp.renameTo(target)) {
                    // Rename across the same directory only fails on exotic filesystems;
                    // fall back to a plain copy rather than losing the data.
                    target.writeText(text)
                    tmp.delete()
                }
            } finally {
                if (tmp.exists()) {
                    tmp.delete()
                }
            }
        }

        fun isSensitiveClip(description: ClipDescription): Boolean {
            val extras = description.extras ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)
            } else {
                extras.getBoolean("android.content.extra.IS_SENSITIVE", false)
            }
        }

        @Volatile
        private var INSTANCE: ClipboardHistoryManager? = null

        fun getInstance(context: Context): ClipboardHistoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ClipboardHistoryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
