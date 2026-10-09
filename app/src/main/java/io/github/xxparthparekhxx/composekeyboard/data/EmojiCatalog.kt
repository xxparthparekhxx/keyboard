package io.github.xxparthparekhxx.composekeyboard.data

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
    ) {
        /** Each entry's name split into words, parallel to [entries]; built on first search. */
        internal val nameWords: List<List<String>> by lazy {
            entries.map { entry -> entry.name.split(WORD_SPLIT).filter { it.isNotEmpty() } }
        }
    }

    @Volatile
    private var snapshot: Snapshot? = null

    val empty: Snapshot = Snapshot(emptyList(), emptyList())

    fun peek(): Snapshot? = snapshot

    fun preload(context: Context) {
        get(context)
    }

    fun get(context: Context): Snapshot {
        snapshot?.let { return it }
        return synchronized(this) {
            snapshot ?: load(context.applicationContext).also { snapshot = it }
        }
    }

    /**
     * Ranked emoji search. Every query word must match the start of a word in
     * the emoji's name ("red hea" finds "red heart", "face" finds "grinning
     * face"), so typing narrows results instead of piling up substring noise.
     * Results are ordered by tier, then Unicode order within a tier:
     *
     * 0. hand-picked aliases for the exact query ("lol" → 😂)
     * 1. names equal to the query
     * 2. names starting with the query
     * 3. aliases starting with the query
     * 4. every query word prefixes a name word
     * 5. plain substring of the name (3+ chars, catches "fly" in "butterfly")
     */
    fun search(snapshot: Snapshot, rawQuery: String, maxCount: Int = 120): List<String> {
        val query = rawQuery.trim().lowercase()
        if (query.isEmpty()) return emptyList()
        val queryWords = query.split(WORD_SPLIT).filter { it.isNotEmpty() }
        if (queryWords.isEmpty()) return emptyList()

        val tiers = Array(TIER_COUNT) { ArrayList<String>() }
        tiers[0].addAll(EmojiSuggestions.exactAliases(query))
        tiers[3].addAll(EmojiSuggestions.prefixAliases(query))

        val index = snapshot.nameWords
        for (i in snapshot.entries.indices) {
            val entry = snapshot.entries[i]
            val name = entry.name
            if (name.isEmpty()) continue
            val tier = when {
                name == query -> 1
                name.startsWith(query) -> 2
                queryWords.all { q -> index[i].any { it.startsWith(q) } } -> 4
                query.length >= 3 && name.contains(query) -> 5
                else -> continue
            }
            tiers[tier].add(entry.emoji)
        }

        val results = LinkedHashSet<String>()
        for (tier in tiers) {
            for (emoji in tier) {
                if (results.size >= maxCount) return results.toList()
                results.add(emoji)
            }
        }
        return results.toList()
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
    private const val TIER_COUNT = 6
    private val WORD_SPLIT = Regex("[^\\p{L}\\p{N}]+")
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
