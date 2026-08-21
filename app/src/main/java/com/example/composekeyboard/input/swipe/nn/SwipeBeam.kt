package com.example.composekeyboard.input.swipe.nn

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * Lexicon trie plus CTC prefix beam search.
 *
 * Two things earn their keep here.
 *
 * **Blank semantics decide doubled letters.** Extending a prefix by the letter
 * it already ends with is only permitted out of the blank-ending probability
 * mass. So "putt" scores well only if the network actually emitted a blank
 * between the two t's — which it does when the finger dwelt on the key. The
 * geometric decoder cannot represent this distinction at all: "put" and "putt"
 * trace the same path through the same keys, and no amount of shape matching
 * separates them. Timing is the only evidence there is, and this is where it
 * gets used.
 *
 * **Length-aware pruning.** Log-probability only ever decreases as a prefix
 * grows, so a plain top-k beam quietly strangles long words in favour of short
 * ones that have not yet paid for their remaining letters. Pruning on
 * `s / depth^gamma + beta * depth` compensates for that bias.
 *
 * The trie is built once per dictionary load. Search allocates nothing.
 */
class SwipeBeam(
    /** Word for each terminal node, parallel to [nodeWord]. */
    private val words: Array<String>,
    private val wordScore: IntArray,
    private val childStart: IntArray,
    private val childLetter: IntArray,
    private val childNode: IntArray,
    private val nodeWord: IntArray,
    private val nodeDepth: IntArray,
    private val nodeLast: IntArray
) {

    private val nodeCount = nodeWord.size

    // Open-addressed map from node id to slot in the frontier being built.
    private var capacity = 1 shl 14
    private var mapKey = IntArray(capacity) { -1 }
    private var mapSlot = IntArray(capacity)

    private var curNode = IntArray(MAX_FRONTIER)
    private var curBlank = FloatArray(MAX_FRONTIER)
    private var curNon = FloatArray(MAX_FRONTIER)
    private var curSize = 0

    private var nxtNode = IntArray(MAX_FRONTIER)
    private var nxtBlank = FloatArray(MAX_FRONTIER)
    private var nxtNon = FloatArray(MAX_FRONTIER)
    private var nxtSize = 0

    private val order = IntArray(MAX_FRONTIER)
    private val pruneScore = FloatArray(MAX_FRONTIER)

    /**
     * @param emissions log emissions, [t * 27 + k], blank at index 26.
     * @param out receives the best words, highest score first.
     * @return how many entries of [out] were filled.
     */
    fun search(
        emissions: FloatArray,
        beamWidth: Int,
        out: Array<String?>,
        outScore: FloatArray,
        gammaPrune: Float = GAMMA_PRUNE,
        betaPrune: Float = BETA_PRUNE,
        gammaScore: Float = GAMMA_SCORE,
        lambdaFreq: Float = LAMBDA_FREQ,
        betaLen: Float = BETA_LEN
    ): Int {
        curSize = 1
        curNode[0] = 0
        curBlank[0] = 0f
        curNon[0] = NEG_INF

        val steps = emissions.size / STRIDE
        for (t in 0 until steps) {
            val base = t * STRIDE
            val lpBlank = emissions[base + ALPHABET]
            clearMap()
            nxtSize = 0

            for (i in 0 until curSize) {
                val node = curNode[i]
                val pb = curBlank[i]
                val pnb = curNon[i]
                val total = logAddExp(pb, pnb)
                val last = nodeLast[node]

                // stay put, emitting blank
                push(node, total + lpBlank, NEG_INF)

                // repeat the final letter with no blank between
                if (last >= 0) push(node, NEG_INF, pnb + emissions[base + last])

                var c = childStart[node]
                val end = childStart[node + 1]
                while (c < end) {
                    val letter = childLetter[c]
                    // a repeat may only grow out of blank-ending mass
                    val src = if (letter == last) pb else total
                    if (src > NEG_INF_HALF) {
                        push(childNode[c], NEG_INF, src + emissions[base + letter])
                    }
                    c++
                }
            }

            prune(beamWidth, gammaPrune, betaPrune)
        }

        return collect(out, outScore, gammaScore, lambdaFreq, betaLen)
    }

    private fun push(node: Int, pb: Float, pnb: Float) {
        var h = (node * -0x61c88647) ushr 18 and (capacity - 1)
        while (true) {
            val k = mapKey[h]
            if (k == node) {
                val s = mapSlot[h]
                nxtBlank[s] = logAddExp(nxtBlank[s], pb)
                nxtNon[s] = logAddExp(nxtNon[s], pnb)
                return
            }
            if (k == -1) {
                if (nxtSize >= MAX_FRONTIER) return    // frontier full; drop the tail
                mapKey[h] = node
                mapSlot[h] = nxtSize
                nxtNode[nxtSize] = node
                nxtBlank[nxtSize] = pb
                nxtNon[nxtSize] = pnb
                nxtSize++
                return
            }
            h = (h + 1) and (capacity - 1)
        }
    }

    private fun clearMap() = java.util.Arrays.fill(mapKey, -1)

    private fun prune(beamWidth: Int, gammaPrune: Float, betaPrune: Float) {
        if (nxtSize <= beamWidth) {
            swapFrontiers(nxtSize)
            return
        }
        for (i in 0 until nxtSize) {
            val d = nodeDepth[nxtNode[i]]
            val s = logAddExp(nxtBlank[i], nxtNon[i])
            val denom = if (d < 1) 1f else d.toFloat().pow(gammaPrune)
            pruneScore[i] = s / denom + betaPrune * d
            order[i] = i
        }
        // partial selection: only the top `beamWidth` need to be in order
        selectTop(beamWidth)
        val keep = beamWidth
        for (i in 0 until keep) {
            val src = order[i]
            curNode[i] = nxtNode[src]
            curBlank[i] = nxtBlank[src]
            curNon[i] = nxtNon[src]
        }
        curSize = keep
    }

    /**
     * Put the [k] highest-scoring candidates into `order[0 until k]`.
     *
     * A bounded min-heap rather than a selection sort: the frontier runs to a
     * few thousand entries every timestep, so O(n log k) beats O(n*k) by more
     * than an order of magnitude and keeps pruning off the critical path.
     * The k survivors come out unordered, which is fine — the next round
     * re-scores them all anyway.
     */
    private fun selectTop(k: Int) {
        for (i in 0 until k) order[i] = i
        // heapify the first k on pruneScore, smallest at the root
        for (i in k / 2 - 1 downTo 0) siftDown(i, k)
        for (i in k until nxtSize) {
            if (pruneScore[i] > pruneScore[order[0]]) {
                order[0] = i
                siftDown(0, k)
            }
        }
    }

    private fun siftDown(start: Int, size: Int) {
        var root = start
        while (true) {
            val left = 2 * root + 1
            if (left >= size) return
            var child = left
            val right = left + 1
            if (right < size && pruneScore[order[right]] < pruneScore[order[left]]) child = right
            if (pruneScore[order[child]] >= pruneScore[order[root]]) return
            val tmp = order[root]; order[root] = order[child]; order[child] = tmp
            root = child
        }
    }

    private fun swapFrontiers(size: Int) {
        var t: IntArray = curNode; curNode = nxtNode; nxtNode = t
        var f: FloatArray = curBlank; curBlank = nxtBlank; nxtBlank = f
        f = curNon; curNon = nxtNon; nxtNon = f
        curSize = size
    }

    private fun collect(
        out: Array<String?>, outScore: FloatArray,
        gammaScore: Float, lambdaFreq: Float, betaLen: Float
    ): Int {
        var n = 0
        for (i in 0 until curSize) {
            val wi = nodeWord[curNode[i]]
            if (wi < 0) continue
            val word = words[wi]
            val len = word.length
            val ctc = logAddExp(curBlank[i], curNon[i])
            val score = ctc / len.toFloat().pow(gammaScore) +
                lambdaFreq * wordScore[wi] + betaLen * len

            // insertion sort into the fixed-size result window
            if (n < out.size) {
                var j = n++
                while (j > 0 && outScore[j - 1] < score) {
                    outScore[j] = outScore[j - 1]; out[j] = out[j - 1]; j--
                }
                outScore[j] = score; out[j] = word
            } else if (score > outScore[n - 1]) {
                var j = n - 1
                while (j > 0 && outScore[j - 1] < score) {
                    outScore[j] = outScore[j - 1]; out[j] = out[j - 1]; j--
                }
                outScore[j] = score; out[j] = word
            }
        }
        return n
    }

    private fun logAddExp(a: Float, b: Float): Float {
        if (a <= NEG_INF_HALF) return b
        if (b <= NEG_INF_HALF) return a
        return if (a > b) a + ln(1f + exp(b - a)) else b + ln(1f + exp(a - b))
    }

    companion object {
        const val ALPHABET = 26
        const val STRIDE = ALPHABET + 1
        const val NEG_INF = -1e30f
        private const val NEG_INF_HALF = -1e29f
        private const val MAX_FRONTIER = 8192

        // Tuned on the dev split by ml/tools/tune_scoring.py.
        const val GAMMA_PRUNE = 0.2582f
        const val BETA_PRUNE = 0.9722f
        const val GAMMA_SCORE = 0.3499f
        const val LAMBDA_FREQ = 0.0351f
        const val BETA_LEN = 0.6065f

        /**
         * Builds the compact trie. Children live in one flat array indexed by
         * [childStart], so walking a node's edges is a contiguous scan.
         */
        fun build(words: List<String>, scores: IntArray): SwipeBeam {
            // growable node table during construction
            var cap = 1 shl 16
            var kids = Array(cap) { IntArray(0) }
            var kidNode = Array(cap) { IntArray(0) }
            var word = IntArray(cap) { -1 }
            var depth = IntArray(cap)
            var last = IntArray(cap) { -1 }
            var count = 1

            fun grow() {
                val n = cap * 2
                val old = cap
                kids = Array(n) { if (it < old) kids[it] else IntArray(0) }
                kidNode = Array(n) { if (it < old) kidNode[it] else IntArray(0) }
                word = word.copyOf(n).also { java.util.Arrays.fill(it, old, n, -1) }
                depth = depth.copyOf(n)
                last = last.copyOf(n).also { java.util.Arrays.fill(it, old, n, -1) }
                cap = n
            }

            for ((wi, w) in words.withIndex()) {
                var node = 0
                var d = 0
                for (ch in w) {
                    if (ch < 'a' || ch > 'z') continue
                    val letter = ch - 'a'
                    var next = -1
                    val arr = kids[node]
                    for (j in arr.indices) if (arr[j] == letter) { next = kidNode[node][j]; break }
                    if (next < 0) {
                        if (count >= cap) grow()
                        next = count++
                        kids[node] = arr + letter
                        kidNode[node] = kidNode[node] + next
                        depth[next] = d + 1
                        last[next] = letter
                    }
                    node = next
                    d++
                }
                if (node != 0 && (word[node] < 0 || scores[wi] > scores[word[node]])) {
                    word[node] = wi
                }
            }

            val start = IntArray(count + 1)
            for (i in 0 until count) start[i + 1] = start[i] + kids[i].size
            val cl = IntArray(start[count])
            val cn = IntArray(start[count])
            for (i in 0 until count) {
                val b = start[i]
                for (j in kids[i].indices) { cl[b + j] = kids[i][j]; cn[b + j] = kidNode[i][j] }
            }

            return SwipeBeam(
                words.toTypedArray(), scores, start, cl, cn,
                word.copyOf(count), depth.copyOf(count), last.copyOf(count)
            )
        }
    }
}
