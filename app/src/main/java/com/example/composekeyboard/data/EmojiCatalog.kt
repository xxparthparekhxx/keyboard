package com.example.composekeyboard.data

import android.content.Context
import android.util.Log

data class EmojiEntry(
    val emoji: String,
    val name: String,
    val group: String
)

/**
 * Full Unicode emoji keyboard set, loaded from `emoji-test.txt` (UTS #51).
 *
 * Skin-tone variants are omitted so the grid stays one glyph per emoji; ZWJ
 * sequences, flags and professions are kept. Search matches Unicode names and
 * the colloquial aliases in [EmojiSuggestions].
 */
object EmojiCatalog {

    data class Snapshot(
        val entries: List<EmojiEntry>,
        val categories: List<EmojiCategory>
    )

    @Volatile
    private var snapshot: Snapshot? = null

    fun get(context: Context): Snapshot {
        snapshot?.let { return it }
        return synchronized(this) {
            snapshot ?: load(context.applicationContext).also { snapshot = it }
        }
    }

    fun search(snapshot: Snapshot, rawQuery: String, maxCount: Int = 80): List<String> {
        val query = rawQuery.trim().lowercase()
        if (query.isEmpty()) return emptyList()

        val results = LinkedHashSet<String>()
        results.addAll(EmojiSuggestions.searchAll(query, maxCount))

        if (results.size < maxCount) {
            for (entry in snapshot.entries) {
                if (results.size >= maxCount) break
                if (entry.name.startsWith(query)) results.add(entry.emoji)
            }
        }
        if (results.size < maxCount && query.length >= 2) {
            for (entry in snapshot.entries) {
                if (results.size >= maxCount) break
                if (entry.name.contains(query)) results.add(entry.emoji)
            }
        }
        return results.take(maxCount)
    }

    internal fun parse(text: String): List<EmojiEntry> {
        val out = ArrayList<EmojiEntry>(2000)
        var group = ""
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("# group:")) {
                group = line.removePrefix("# group:").trim()
                continue
            }
            if (line.isEmpty() || line.startsWith("#")) continue
            if (group == "Component") continue
            if (!line.contains("; fully-qualified")) continue

            val semi = line.indexOf(';')
            val hash = line.indexOf('#')
            if (semi < 0 || hash < 0 || hash < semi) continue

            val cps = parseCodePoints(line.substring(0, semi)) ?: continue
            if (cps.any { it in SKIN_TONE_START..SKIN_TONE_END }) continue

            val comment = line.substring(hash + 1).trim()
            val name = NAME_REGEX.find(comment)?.groupValues?.get(1)?.lowercase() ?: continue
            val emoji = String(cps.toIntArray(), 0, cps.size)
            out.add(EmojiEntry(emoji, name, group.ifEmpty { "Symbols" }))
        }
        return out
    }

    internal fun categoriesFrom(entries: List<EmojiEntry>): List<EmojiCategory> {
        val grouped = LinkedHashMap<String, MutableList<String>>()
        for (entry in entries) {
            grouped.getOrPut(entry.group) { ArrayList() }.add(entry.emoji)
        }
        return grouped.map { (name, emojis) ->
            EmojiCategory(
                name = name,
                icon = GROUP_ICONS[name] ?: emojis.firstOrNull() ?: "😀",
                emojis = emojis
            )
        }
    }

    private fun load(context: Context): Snapshot {
        return try {
            val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            val entries = parse(text)
            if (entries.isEmpty()) fallbackSnapshot() else Snapshot(entries, categoriesFrom(entries))
        } catch (e: Exception) {
            Log.w(TAG, "Could not load $ASSET_NAME; using built-in emoji set", e)
            fallbackSnapshot()
        }
    }

    private fun fallbackSnapshot(): Snapshot {
        val entries = EmojiData.categories.flatMap { cat ->
            cat.emojis.map { EmojiEntry(it, "", cat.name) }
        }
        return Snapshot(entries, EmojiData.categories)
    }

    private fun parseCodePoints(hexField: String): List<Int>? {
        val parts = hexField.trim().split(Regex("\\s+"))
        if (parts.isEmpty() || parts[0].isEmpty()) return null
        val cps = ArrayList<Int>(parts.size)
        for (part in parts) {
            val cp = part.toIntOrNull(16) ?: return null
            cps.add(cp)
        }
        return cps
    }

    private const val ASSET_NAME = "emoji-test.txt"
    private const val TAG = "EmojiCatalog"
    private const val SKIN_TONE_START = 0x1F3FB
    private const val SKIN_TONE_END = 0x1F3FF
    private val NAME_REGEX = Regex("""E[0-9.]+ (.+)$""")

    private val GROUP_ICONS = mapOf(
        "Smileys & Emotion" to "😀",
        "People & Body" to "👋",
        "Animals & Nature" to "🐶",
        "Food & Drink" to "🍕",
        "Travel & Places" to "✈️",
        "Activities" to "⚽",
        "Objects" to "💡",
        "Symbols" to "❤️",
        "Flags" to "🏳️"
    )
}
