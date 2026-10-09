package io.github.xxparthparekhxx.composekeyboard.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStream

/**
 * Next-word prediction: after a word is finished, [predict] offers the words
 * most likely to come next.
 *
 * Two sources are merged. A shipped table ([SEED_ASSET], built by
 * `ml/tools/build_next_words.py` from C4) gives P(next | previous) for the
 * 20k most common words, so prediction works out of the box. On top of it,
 * the pairs this user types are learned: each sighting adds [USER_WEIGHT]
 * thousandths, so a pair typed a few times outranks the generic table. User
 * learning is driven by the service and gated exactly like [SwipeDictionary]
 * learning (no passwords, terminals or incognito fields), and it stays on the
 * device: the learned file is excluded from backups like the learned words.
 *
 * Bounded on both axes so it cannot grow over the lifetime of a device:
 * at most [MAX_FOLLOWERS] followers per word, and at most [MAX_CONTEXTS]
 * preceding words, with the least-used evicted first.
 *
 * Thread-safe: [learn], [unlearn] and [predict] run on the main thread,
 * [persist] on a background thread.
 */
class NextWordPredictor internal constructor(
    private val file: File,
    private val openSeed: (() -> InputStream)? = null
) {

    /** One context's shipped followers, best first, in display casing. */
    private class SeedRow(val words: Array<String>, val scores: IntArray)

    private val lock = Any()

    /** previous word → (next word → count), learned on this device. Guarded by [lock]. */
    private val pairs = HashMap<String, HashMap<String, Int>>()

    /** previous word → shipped followers. Replaced once by [load]; guarded by [lock]. */
    private var seed: Map<String, SeedRow> = emptyMap()

    /**
     * Lower-case → usual casing ("i" → "I", "york" → "York") for shipped
     * words whose casing is not lower-case, so learned pairs, which are
     * stored lower-case, display the same way. Guarded by [lock].
     */
    private var displayForms: Map<String, String> = emptyMap()

    /** Bumped on every change; [persist] writes only when it moved. Guarded by [lock]. */
    private var version = 0
    private var savedVersion = 0

    @Volatile
    private var loaded = false

    /** Blocking; call from a background dispatcher. */
    fun load() {
        if (loaded) return
        val (seedRows, forms) = readSeed()
        val read = HashMap<String, HashMap<String, Int>>()
        if (file.exists()) {
            try {
                file.forEachLine { line ->
                    val parts = line.split('\t')
                    if (parts.size != 3) return@forEachLine
                    val prev = normalize(parts[0]) ?: return@forEachLine
                    val next = normalize(parts[1]) ?: return@forEachLine
                    val count = parts[2].trim().toIntOrNull()?.coerceIn(1, MAX_COUNT)
                        ?: return@forEachLine
                    read.getOrPut(prev) { HashMap() }[next] = count
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read next-word table", e)
            }
        }
        synchronized(lock) {
            seed = seedRows
            displayForms = forms
            // Pairs learned while the file was loading win over the file.
            for ((prev, followers) in read) {
                val live = pairs.getOrPut(prev) { HashMap() }
                for ((next, count) in followers) live.putIfAbsent(next, count)
            }
            loaded = true
        }
    }

    /** Records that [next] was typed right after [previous]. */
    fun learn(previous: String, next: String) {
        val prev = normalize(previous) ?: return
        val word = normalize(next) ?: return
        synchronized(lock) {
            val followers = pairs.getOrPut(prev) { HashMap() }
            followers[word] = ((followers[word] ?: 0) + 1).coerceAtMost(MAX_COUNT)
            if (followers.size > MAX_FOLLOWERS) {
                // Never the pair just learned: at count 1 it is always the weakest.
                followers.entries
                    .filter { it.key != word }
                    .minByOrNull { it.value }
                    ?.let { followers.remove(it.key) }
            }
            evictContextsIfNeeded(keep = prev)
            version++
        }
    }

    /**
     * Takes back one [learn], for a pair that turned out to be wrong (the
     * user replaced the word a swipe committed).
     */
    fun unlearn(previous: String, next: String) {
        val prev = normalize(previous) ?: return
        val word = normalize(next) ?: return
        synchronized(lock) {
            val followers = pairs[prev] ?: return
            val count = followers[word] ?: return
            if (count <= 1) followers.remove(word) else followers[word] = count - 1
            if (followers.isEmpty()) pairs.remove(prev)
            version++
        }
    }

    /** Up to [maxCount] likely next words after [previous], most likely first. */
    fun predict(previous: String, maxCount: Int = 3): List<String> {
        val prev = normalize(previous) ?: return emptyList()
        synchronized(lock) {
            val learned = pairs[prev]
            val shipped = seed[prev]
            if (learned == null && shipped == null) return emptyList()
            // lower-case word → combined score, and the casing to show it in
            val scores = HashMap<String, Int>()
            val shown = HashMap<String, String>()
            if (shipped != null) {
                for (i in shipped.words.indices) {
                    val word = shipped.words[i]
                    val key = word.lowercase()
                    scores[key] = shipped.scores[i]
                    shown[key] = word
                }
            }
            learned?.forEach { (key, count) ->
                scores[key] = (scores[key] ?: 0) + count * USER_WEIGHT
                if (key !in shown) shown[key] = displayForms[key] ?: key
            }
            return scores.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(maxCount)
                .map { shown.getValue(it.key) }
        }
    }

    /**
     * Parses the shipped table: `prev<TAB>next,score<TAB>next,score...`.
     * A missing or broken table only costs the head start; learning still works.
     */
    private fun readSeed(): Pair<Map<String, SeedRow>, Map<String, String>> {
        val open = openSeed ?: return emptyMap<String, SeedRow>() to emptyMap()
        val rows = HashMap<String, SeedRow>(SEED_CAPACITY)
        val forms = HashMap<String, String>()
        // Followers repeat across rows ("the", "to", "a"); share one String each.
        val pool = HashMap<String, String>()
        try {
            open().bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.isEmpty() || line[0] == '#') continue
                    val cells = line.split('\t')
                    val prev = normalize(cells[0]) ?: continue
                    val words = ArrayList<String>(cells.size - 1)
                    val scores = IntArray(cells.size - 1)
                    for (cell in cells.subList(1, cells.size)) {
                        val comma = cell.lastIndexOf(',')
                        if (comma <= 0) continue
                        val word = cell.substring(0, comma)
                        val key = normalize(word) ?: continue
                        val score = cell.substring(comma + 1).toIntOrNull() ?: continue
                        val shared = pool.getOrPut(word) { word }
                        if (shared != key) forms[key] = shared
                        scores[words.size] = score.coerceIn(1, 999)
                        words.add(shared)
                    }
                    if (words.isNotEmpty()) {
                        rows[prev] = SeedRow(words.toTypedArray(), scores.copyOf(words.size))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read the shipped next-word table", e)
        }
        return rows to forms
    }

    /** Blocking; call from a background dispatcher. No-op when nothing changed. */
    fun persist() {
        val snapshot: List<Triple<String, String, Int>>
        val snapshotVersion: Int
        synchronized(lock) {
            // Never overwrite the file before it has been read.
            if (!loaded || version == savedVersion) return
            snapshotVersion = version
            snapshot = pairs.flatMap { (prev, followers) ->
                followers.map { (next, count) -> Triple(prev, next, count) }
            }
        }
        try {
            val text = buildString {
                for ((prev, next, count) in snapshot) {
                    append(prev).append('\t').append(next).append('\t').append(count).append('\n')
                }
            }
            ClipboardHistoryManager.writeAtomically(file, text)
            synchronized(lock) {
                savedVersion = maxOf(savedVersion, snapshotVersion)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist next-word table", e)
        }
    }

    /** Caller must hold [lock]. Drops the least-used contexts once past [MAX_CONTEXTS]. */
    private fun evictContextsIfNeeded(keep: String) {
        if (pairs.size <= MAX_CONTEXTS) return
        // Down to a low-water mark, so the sort runs once per many new words.
        val excess = pairs.size - EVICT_TO
        pairs.entries.asSequence()
            .filter { it.key != keep }
            .sortedBy { entry -> entry.value.values.sum() }
            .take(excess)
            .map { it.key }
            .toList()
            .forEach { pairs.remove(it) }
    }

    companion object {
        private const val TAG = "NextWordPredictor"
        internal const val USER_FILE = "next_words_user.txt"
        private const val SEED_ASSET = "next_words.txt"
        private const val SEED_CAPACITY = 32_768

        /**
         * What one sighting of a pair typed by this user is worth, in the
         * shipped table's thousandths. 150 is a 15-point boost each time:
         * typed twice, a pair beats almost any generic follower.
         */
        const val USER_WEIGHT = 150

        const val MAX_FOLLOWERS = 8
        const val MAX_CONTEXTS = 10_000
        private const val EVICT_TO = MAX_CONTEXTS * 9 / 10
        private const val MAX_COUNT = 10_000
        private const val MAX_WORD_LENGTH = SwipeDictionary.MAX_WORD_LENGTH

        @Volatile
        private var instance: NextWordPredictor? = null

        fun getInstance(context: Context): NextWordPredictor {
            return instance ?: synchronized(this) {
                val app = context.applicationContext
                instance ?: NextWordPredictor(
                    File(app.filesDir, USER_FILE),
                    openSeed = { app.assets.open(SEED_ASSET) }
                ).also { instance = it }
            }
        }

        /**
         * Lower-cased word, or null for anything that is not one. Unlike
         * [SwipeDictionary.normalize], one-letter words count: "I" and "a"
         * are among the most predictive words there are.
         */
        fun normalize(raw: String): String? {
            val word = raw.trim().lowercase()
            if (word.isEmpty() || word.length > MAX_WORD_LENGTH) return null
            var letters = 0
            for (c in word) {
                when {
                    c.isLetter() -> letters++
                    c == '\'' || c == '-' -> Unit
                    else -> return null
                }
            }
            return if (letters > 0) word else null
        }
    }
}
