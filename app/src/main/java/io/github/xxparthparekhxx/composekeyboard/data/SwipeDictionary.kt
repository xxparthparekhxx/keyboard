package io.github.xxparthparekhxx.composekeyboard.data

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Word list backing the swipe decoder.
 *
 * Entries are bucketed by their first letter because the decoder always knows
 * (within a few candidates) which key a gesture started on, so a bucket lookup
 * throws away ~95% of the dictionary before any geometry is touched.
 *
 * Words the user actually types are learned into a small overlay list that is
 * persisted separately from the shipped asset.
 */
class SwipeDictionary private constructor(private val appContext: Context) {

    /** One dictionary word plus the letter keys a gesture would have to cross. */
    class Entry(
        val word: String,
        /** Indices 0..25; apostrophes and other non-letters are skipped. */
        val keys: ByteArray,
        /** 1..255, roughly `log2(corpus count) * 10`. */
        @Volatile var score: Int
    )

    @Volatile
    var isLoaded: Boolean = false
        private set

    /**
     * Replaced wholesale on every mutation so the decoder thread always walks a
     * list nobody is appending to. Learning is rare; decoding is not.
     */
    @Volatile
    private var buckets: Array<List<Entry>> = Array(ALPHABET) { emptyList() }

    private val byWord = HashMap<String, Entry>(48_000)

    /** Words the user taught us, with the boost applied on top of the base score. */
    private val userBoosts = HashMap<String, Int>()
    private val lock = Any()

    private var userVersion = 0
    private var savedVersion = 0

    /**
     * Bumped only when [byWord] gains an entry — the one change that alters what
     * [allWords] returns in substance, and so the only one worth rebuilding the
     * neural decoder's trie for.
     *
     * Deliberately not moved by a score-only boost. A boost shifts a word's
     * ranking term by `LEARN_STEP * LAMBDA_FREQ` ~= 0.2 against a frequency
     * term spanning ~0..9, which is not worth the ~70 MB a rebuild allocates.
     * Those bumps ride along on the next word that does move this counter.
     */
    private var lexiconRevision = 0

    /** See [lexiconRevision]. Reflects lexicon membership, not learned weight. */
    val lexiconVersion: Int
        get() = synchronized(lock) { lexiconRevision }

    fun bucket(firstLetter: Int): List<Entry> {
        val b = buckets
        return if (firstLetter in b.indices) b[firstLetter] else emptyList()
    }

    /** Blocking; call from a background dispatcher. Safe to call more than once. */
    fun load() {
        if (isLoaded) return
        val staging = Array(ALPHABET) { ArrayList<Entry>(2048) }
        val stagingByWord = HashMap<String, Entry>(48_000)
        var loadFailed = false
        try {
            appContext.assets.open(ASSET_NAME).bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.isEmpty() || line[0] == '#') continue
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    val word = line.substring(0, tab)
                    val score = line.substring(tab + 1).trim().toIntOrNull() ?: continue
                    addStaging(staging, stagingByWord, word, score)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read $ASSET_NAME", e)
            loadFailed = true
        }

        if (loadFailed) return

        val loadedUserWords = readUserWords()
        for ((word, boost) in loadedUserWords) {
            val existing = stagingByWord[word]
            if (existing != null) {
                existing.score = (existing.score + boost).coerceAtMost(MAX_SCORE)
            } else if (boost >= NEW_WORD_THRESHOLD) {
                addStaging(staging, stagingByWord, word, (USER_BASE_SCORE + boost).coerceAtMost(MAX_SCORE))
            }
        }

        for (i in staging.indices) {
            staging[i].sortByDescending { it.score }
        }

        synchronized(lock) {
            if (isLoaded) return
            byWord.clear()
            byWord.putAll(stagingByWord)
            userBoosts.clear()
            userBoosts.putAll(loadedUserWords)
            buckets = Array(ALPHABET) { staging[it] }
            isLoaded = true
            Log.i(TAG, "Loaded ${byWord.size} words (${userBoosts.size} learned)")
        }
    }

    /**
     * Records that the user really did mean this word — either they typed it out
     * by hand or picked it out of the suggestion strip over the decoder's own
     * first choice. Cheap enough to call on the main thread.
     *
     * Returns the word's new score when it was already part of the lexicon (so
     * a caller can push that bump into the neural decoder's beam immediately,
     * via [io.github.xxparthparekhxx.composekeyboard.input.swipe.nn.SwipeNeuralDecoder.bumpScore],
     * instead of waiting for the next full trie rebuild), or null when the word
     * is brand new to the lexicon (or not yet promoted into it) — that case is
     * only reflected once [lexiconVersion] moves and the trie is rebuilt.
     */
    fun learn(rawWord: String): Int? {
        val word = normalize(rawWord) ?: return null
        synchronized(lock) {
            if (!isLoaded) return null
            val previousBoost = userBoosts[word] ?: 0
            val boost = (previousBoost + LEARN_STEP).coerceAtMost(MAX_BOOST)
            userBoosts[word] = boost
            evictLearnedIfNeeded(keep = word)
            userVersion++

            val existing = byWord[word]
            if (existing != null) {
                // Apply only the boost actually recorded, so the live score
                // matches what load() rebuilds (base + boost, boost <= MAX_BOOST)
                // instead of creeping up without bound during a session.
                val delta = boost - previousBoost
                if (delta == 0) return existing.score
                existing.score = (existing.score + delta).coerceAtMost(MAX_SCORE)
                val first = keySequenceOf(word)?.get(0)?.toInt()
                if (first != null && first in buckets.indices) {
                    val next = buckets.copyOf()
                    next[first] = reinsertByScore(buckets[first], existing)
                    buckets = next
                }
                return existing.score
            }
            // A word the shipped list has never heard of is as likely to be a
            // typo as a real one, so it has to be seen more than once before it
            // can win a gesture. Picking it out of the suggestion strip is not
            // affected — those words are already in the dictionary.
            if (boost < NEW_WORD_THRESHOLD) return null

            val keys = keySequenceOf(word) ?: return null
            val first = keys[0].toInt()
            val entry = Entry(word, keys, USER_BASE_SCORE)
            byWord[word] = entry
            lexiconRevision++
            // Copy-on-write: only the one bucket is rebuilt, and the new array is
            // published atomically so an in-flight decode never sees a torn list.
            val next = buckets.copyOf()
            next[first] = reinsertByScore(buckets[first], entry)
            buckets = next
            return null
        }
    }

    /**
     * Returns top word autocompletions for the given typed prefix, ordered by word frequency.
     * Fast (<0.1ms) and allocation-minimal.
     */
    fun getCompletions(rawPrefix: String, maxCount: Int = 4): List<String> {
        if (!isLoaded || rawPrefix.isEmpty()) return emptyList()
        val prefix = rawPrefix.lowercase()
        val firstChar = prefix[0]
        if (firstChar !in 'a'..'z') return emptyList()
        val firstLetter = firstChar - 'a'
        val bucket = bucket(firstLetter)

        // byWord is mutated under the lock; take a consistent snapshot rather
        // than risking an unsynchronized read during a rehash.
        val exactWord: String?
        synchronized(lock) {
            exactWord = byWord[prefix]?.word
        }
        val results = ArrayList<String>(maxCount)
        if (exactWord != null) {
            results.add(formatCased(exactWord, rawPrefix))
        }

        for (entry in bucket) {
            if (results.size >= maxCount) break
            if (entry.word.startsWith(prefix) && entry.word != prefix) {
                results.add(formatCased(entry.word, rawPrefix))
            }
        }
        return results
    }

    private fun formatCased(word: String, rawPrefix: String): String {
        return when {
            rawPrefix.length > 1 && rawPrefix.all { it.isUpperCase() } -> word.uppercase()
            rawPrefix.first().isUpperCase() -> word.replaceFirstChar { it.uppercase() }
            else -> word
        }
    }

    /**
     * Every word and its score, for building the neural decoder's lexicon trie.
     * Snapshotted under the lock so a concurrent [learn] cannot tear the list.
     */
    fun allWords(): Pair<List<String>, IntArray> {
        synchronized(lock) {
            val words = ArrayList<String>(byWord.size)
            val scores = IntArray(byWord.size)
            var i = 0
            for (entry in byWord.values) {
                words.add(entry.word)
                scores[i++] = entry.score
            }
            return words to scores
        }
    }

    /** Blocking; call from a background dispatcher. No-op when nothing changed. */
    fun persistLearnedWords() {
        val snapshot: Map<String, Int>
        val snapshotVersion: Int
        synchronized(lock) {
            if (userVersion == savedVersion) return
            snapshotVersion = userVersion
            snapshot = HashMap(userBoosts)
        }
        try {
            val text = buildString {
                for ((word, boost) in snapshot) {
                    append(word).append('\t').append(boost).append('\n')
                }
            }
            ClipboardHistoryManager.writeAtomically(File(appContext.filesDir, USER_FILE), text)
            synchronized(lock) {
                savedVersion = maxOf(savedVersion, snapshotVersion)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist learned words", e)
        }
    }

    /**
     * Keeps the learned-word overlay from growing without bound over the
     * lifetime of a device: once past [MAX_LEARNED_WORDS], the weakest-boosted
     * words (least-seen, oldest signal) are forgotten first.
     * Caller must hold [lock]. [keep] is the word currently being learned.
     */
    private fun evictLearnedIfNeeded(keep: String) {
        if (userBoosts.size <= MAX_LEARNED_WORDS) return
        // Evict down to a low-water mark so the O(n log n) sort runs once per
        // few hundred new words rather than on every learn() at the cap, and
        // never evict the word being learned: at a single sighting it is always
        // the weakest, so it would otherwise be dropped instantly and no new
        // word could ever be learned once the overlay was full.
        val excess = userBoosts.size - EVICT_TO
        userBoosts.entries.asSequence()
            .filter { it.key != keep }
            .sortedBy { it.value }
            .take(excess)
            .map { it.key }
            .toList()
            .forEach { userBoosts.remove(it) }
    }

    private fun readUserWords(): Map<String, Int> {
        val file = File(appContext.filesDir, USER_FILE)
        if (!file.exists()) return emptyMap()
        val loaded = HashMap<String, Int>()
        try {
            file.forEachLine { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) return@forEachLine
                val word = line.substring(0, tab)
                val boost = line.substring(tab + 1).trim().toIntOrNull() ?: return@forEachLine
                loaded[word] = boost.coerceIn(0, MAX_BOOST)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read learned words", e)
        }
        return loaded
    }

    /**
     * Puts [entry] into [current] in descending-score order without sorting the
     * whole bucket. Called from [learn] on the main thread; a full sort of a
     * several-thousand-word letter bucket would jank the IME.
     */
    private fun reinsertByScore(current: List<Entry>, entry: Entry): List<Entry> {
        val out = ArrayList<Entry>(current.size + 1)
        var inserted = false
        for (existing in current) {
            if (existing === entry) continue
            if (!inserted && entry.score >= existing.score) {
                out.add(entry)
                inserted = true
            }
            out.add(existing)
        }
        if (!inserted) out.add(entry)
        return out
    }

    private fun addStaging(
        staging: Array<ArrayList<Entry>>,
        stagingByWord: HashMap<String, Entry>,
        word: String,
        score: Int
    ) {
        if (stagingByWord.containsKey(word)) return
        val keys = keySequenceOf(word) ?: return
        val entry = Entry(word, keys, score.coerceIn(1, MAX_SCORE))
        stagingByWord[word] = entry
        staging[keys[0].toInt()].add(entry)
    }

    companion object {
        private const val TAG = "SwipeDictionary"
        private const val ASSET_NAME = "swipe_words.txt"
        private const val USER_FILE = "swipe_user_words.txt"

        const val ALPHABET = 26

        /** Longest word the decoder will consider; guards pathological input. */
        const val MAX_WORD_LENGTH = 22

        private const val MAX_SCORE = 255
        private const val USER_BASE_SCORE = 120
        private const val LEARN_STEP = 6
        private const val MAX_BOOST = 60
        private const val MAX_LEARNED_WORDS = 5_000
        private const val EVICT_TO = MAX_LEARNED_WORDS * 9 / 10

        /** Sightings needed before an unknown word becomes gesture-reachable. */
        private const val NEW_WORD_THRESHOLD = LEARN_STEP * 2

        @Volatile
        private var instance: SwipeDictionary? = null

        fun getInstance(context: Context): SwipeDictionary {
            return instance ?: synchronized(this) {
                instance ?: SwipeDictionary(context.applicationContext).also { instance = it }
            }
        }

        /** Lower-cases and strips anything the keyboard cannot produce by gesture. */
        fun normalize(raw: String): String? {
            val word = raw.trim().lowercase()
            if (word.length < 2 || word.length > MAX_WORD_LENGTH) return null
            var letters = 0
            for (c in word) {
                when {
                    c in 'a'..'z' -> letters++
                    c == '\'' || c == '-' -> Unit
                    else -> return null
                }
            }
            return if (letters >= 2) word else null
        }

        /** Letter-key indices for a word, ignoring apostrophes and hyphens. */
        fun keySequenceOf(word: String): ByteArray? {
            var n = 0
            for (c in word) if (c in 'a'..'z') n++
            if (n < 1 || n > MAX_WORD_LENGTH) return null
            val keys = ByteArray(n)
            var i = 0
            for (c in word) if (c in 'a'..'z') keys[i++] = (c - 'a').toByte()
            return keys
        }
    }
}
