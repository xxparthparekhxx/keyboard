package io.github.xxparthparekhxx.composekeyboard.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RecentEmojiManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("recent_emojis_prefs", Context.MODE_PRIVATE)

    private val _recentEmojis = MutableStateFlow(loadRecentEmojis())
    val recentEmojis: StateFlow<List<String>> = _recentEmojis.asStateFlow()

    private fun loadRecentEmojis(): List<String> {
        val raw = prefs.getString(KEY_RECENTS, null)
        if (raw.isNullOrBlank()) {
            return DEFAULT_RECENTS
        }
        val list = raw.split(",").filter { it.isNotBlank() }
        return if (list.isEmpty()) DEFAULT_RECENTS else list
    }

    fun recordEmoji(emoji: String) {
        if (emoji.isBlank()) return
        val trimmed = synchronized(this) {
            val list = updateRecents(_recentEmojis.value, emoji, MAX_RECENTS)
            _recentEmojis.value = list
            list
        }
        prefs.edit().putString(KEY_RECENTS, trimmed.joinToString(",")).apply()
    }

    companion object {
        const val KEY_RECENTS = "recent_emoji_list"
        const val MAX_RECENTS = 42

        fun updateRecents(current: List<String>, emoji: String, maxCount: Int = MAX_RECENTS): List<String> {
            if (emoji.isBlank()) return current
            val updated = current.toMutableList()
            updated.remove(emoji)
            updated.add(0, emoji)
            return updated.take(maxCount)
        }

        val DEFAULT_RECENTS = listOf(
            "😂", "❤️", "🔥", "👍", "😊", "✨", "🙏", "😍",
            "🥰", "🎉", "🤣", "👏", "💯", "🥺", "😎", "🥳",
            "😭", "💖", "👀", "🙌", "💀", "🤔", "💪", "🤩"
        )

        @Volatile
        private var INSTANCE: RecentEmojiManager? = null

        fun getInstance(context: Context): RecentEmojiManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RecentEmojiManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
