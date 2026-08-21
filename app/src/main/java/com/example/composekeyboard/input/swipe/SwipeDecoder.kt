package com.example.composekeyboard.input.swipe

import com.example.composekeyboard.data.SwipeDictionary
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Turns a finger path into ranked words.
 *
 * The scoring is a weighted blend of four independent signals, so that no single
 * one can carry a bad candidate:
 *
 *  - **corridor** — every letter of the word must be passed, *in order*, within
 *    about a key's width of the path. Cheap, and it eliminates the vast bulk of
 *    the dictionary before anything expensive runs.
 *  - **pivot coverage** — wherever the finger visibly turned a corner, some
 *    letter of the word has to explain that corner. This is what separates
 *    "hello" from "hero": both thread the same letters in order, but only
 *    "hello" accounts for the sharp turn down at `l`.
 *  - **shape** — the word's ideal path (the polyline through its key centres) is
 *    resampled to the same number of arc-length-equal points as the gesture and
 *    compared point for point. Speed-independent, and sensitive to the overall
 *    silhouette rather than to any single letter.
 *  - **prior** — corpus frequency, plus whatever the user has taught us.
 *
 * Everything is measured in units of one key width, so the weights hold across
 * screen sizes, keyboard height settings and orientation.
 */
object SwipeDecoder {

    /** Points the gesture and each candidate's ideal path are resampled to. */
    const val SAMPLES = 32

    // --- Gating -------------------------------------------------------------

    /** Shorter than this and it was a sloppy tap, not a word. */
    private const val MIN_TRACE_KEY_WIDTHS = 0.7f

    /** How far from the touch-down point a word's first letter may sit. */
    private const val START_RADIUS = 1.25f

    /** Lift-off is usually the sloppiest part of a gesture, so allow more room. */
    private const val END_RADIUS = 1.40f

    private const val MAX_ENDPOINT_CANDIDATES = 4

    /**
     * A gesture that satisfies nothing in the dictionary is retried with the
     * tolerances multiplied by this and the pivot rule switched off. Swallowing
     * a swipe entirely is the worst outcome available — a wrong-but-close word
     * the user can fix from the suggestion strip beats no word at all.
     */
    private const val RELAXED_SCALE = 1.55f
    private const val RELAXED_ENDPOINT_CANDIDATES = 6

    /** Corridor half-width every interior letter must fall inside. */
    private const val CORRIDOR = 1.15f

    /** A corner has to be explained by a letter this close to it. */
    private const val PIVOT_RADIUS = 1.25f

    /** Letters within this of the path cost nothing; only real drift is charged. */
    private const val TUNNEL = 0.42f

    // --- Weights ------------------------------------------------------------

    private const val W_SHAPE = 1.90f
    private const val W_LOCATION = 1.50f
    private const val W_LENGTH = 0.35f
    private const val W_FREQUENCY = 1.35f

    /** Charged per rank when the word's first/last letter is not the nearest key. */
    private const val START_RANK_PENALTY = 0.22f
    private const val END_RANK_PENALTY = 0.20f

    // --- Pivot detection ----------------------------------------------------

    /** Turn sharper than this counts as a deliberate corner (radians ≈ 62°). */
    private const val PIVOT_ANGLE = 1.08f
    private const val MAX_PIVOTS = 8

    private val REJECTED = Float.MAX_VALUE

    /**
     * Ranks dictionary words against [swipe]. Pure and thread-safe: call it from
     * a background dispatcher with a [SwipeKeyGeometry.snapshot].
     */
    fun decode(
        swipe: SampledSwipe,
        keys: SwipeKeyMap,
        dictionary: SwipeDictionary,
        maxResults: Int = 4
    ): List<String> {
        val strict = decodePass(swipe, keys, dictionary, maxResults, relaxed = false)
        if (strict.isNotEmpty()) return strict
        return decodePass(swipe, keys, dictionary, maxResults, relaxed = true)
    }

    private fun decodePass(
        swipe: SampledSwipe,
        keys: SwipeKeyMap,
        dictionary: SwipeDictionary,
        maxResults: Int,
        relaxed: Boolean
    ): List<String> {
        val unit = keys.keyWidth
        if (unit <= 0f || !dictionary.isLoaded) return emptyList()
        if (swipe.traceLength < unit * MIN_TRACE_KEY_WIDTHS) return emptyList()

        val n = swipe.size
        if (n < 2) return emptyList()

        val px = swipe.xs
        val py = swipe.ys

        val slack = if (relaxed) RELAXED_SCALE else 1f
        val limit = if (relaxed) RELAXED_ENDPOINT_CANDIDATES else MAX_ENDPOINT_CANDIDATES

        val startKeys = IntArray(limit)
        val startCount =
            keys.nearest(px[0], py[0], unit * START_RADIUS * slack, limit, startKeys)
        if (startCount == 0) return emptyList()

        val endKeys = IntArray(limit)
        val endCount = keys.nearest(
            px[n - 1], py[n - 1], unit * END_RADIUS * slack, limit, endKeys
        )
        if (endCount == 0) return emptyList()

        val endRank = IntArray(SwipeDictionary.ALPHABET) { -1 }
        for (r in 0 until endCount) endRank[endKeys[r]] = r

        val pivots = if (relaxed) EMPTY_PIVOTS else detectPivots(swipe, unit)

        val corridor = unit * CORRIDOR * slack
        val pivotRadius = unit * PIVOT_RADIUS

        // Reused across every candidate so scoring allocates nothing in the loop.
        val idealX = FloatArray(n)
        val idealY = FloatArray(n)

        val best = TopWords(maxResults)

        for (sRank in 0 until startCount) {
            val startKey = startKeys[sRank]
            val startBias = START_RANK_PENALTY * sRank
            val bucket = dictionary.bucket(startKey)

            for (index in bucket.indices) {
                val entry = bucket[index]
                val letters = entry.keys
                val length = letters.size
                if (length < 2 || length > SwipeDictionary.MAX_WORD_LENGTH) continue

                val eRank = endRank[letters[length - 1].toInt()]
                if (eRank < 0) continue

                val cost = score(
                    swipe = swipe,
                    keys = keys,
                    letters = letters,
                    unit = unit,
                    corridor = corridor,
                    pivots = pivots,
                    pivotRadius = pivotRadius,
                    idealX = idealX,
                    idealY = idealY
                )
                if (cost == REJECTED) continue

                val prior = W_FREQUENCY * (entry.score / 255f)
                best.offer(entry.word, cost + startBias + END_RANK_PENALTY * eRank - prior)
            }
        }

        return best.toList()
    }

    /**
     * Cost of explaining [swipe] with [letters], or [REJECTED].
     *
     * Ordered cheapest-test-first: the corridor walk throws out almost every
     * candidate, and only survivors pay for pivot and shape comparison.
     */
    private fun score(
        swipe: SampledSwipe,
        keys: SwipeKeyMap,
        letters: ByteArray,
        unit: Float,
        corridor: Float,
        pivots: IntArray,
        pivotRadius: Float,
        idealX: FloatArray,
        idealY: FloatArray
    ): Float {
        val px = swipe.xs
        val py = swipe.ys
        val n = swipe.size
        val length = letters.size
        val cx = keys.centerX
        val cy = keys.centerY
        val placed = keys.placed

        for (i in 0 until length) {
            if (!placed[letters[i].toInt()]) return REJECTED
        }

        // --- Corridor walk ---------------------------------------------------
        // The first letter is pinned to touch-down and the last to lift-off; the
        // interior ones must be met in order, without ever walking backwards.
        val first = letters[0].toInt()
        var drift = max(0f, dist(px[0], py[0], cx[first], cy[first]) / unit - TUNNEL)

        var cursor = 0
        for (i in 1 until length - 1) {
            val key = letters[i].toInt()
            val kx = cx[key]
            val ky = cy[key]

            var hit = -1
            var hitDistance = 0f
            var j = cursor
            while (j < n) {
                val d = dist(px[j], py[j], kx, ky)
                if (d <= corridor) {
                    hit = j
                    hitDistance = d
                    break
                }
                j++
            }
            if (hit < 0) return REJECTED

            // Slide forward to the closest approach so the drift charge reflects
            // how near the finger actually came, not merely where it entered.
            var m = hit + 1
            while (m < n) {
                val d = dist(px[m], py[m], kx, ky)
                if (d >= hitDistance) break
                hitDistance = d
                hit = m
                m++
            }

            drift += max(0f, hitDistance / unit - TUNNEL)
            cursor = hit
        }

        val last = letters[length - 1].toInt()
        drift += max(0f, dist(px[n - 1], py[n - 1], cx[last], cy[last]) / unit - TUNNEL)
        val locationCost = drift / length

        // --- Pivot coverage --------------------------------------------------
        for (p in pivots) {
            var explained = false
            for (i in 0 until length) {
                val key = letters[i].toInt()
                if (dist(px[p], py[p], cx[key], cy[key]) <= pivotRadius) {
                    explained = true
                    break
                }
            }
            if (!explained) return REJECTED
        }

        // --- Shape -----------------------------------------------------------
        val idealLength = buildIdealPath(letters, cx, cy, idealX, idealY)
        var shapeSum = 0f
        for (i in 0 until n) {
            shapeSum += dist(px[i], py[i], idealX[i], idealY[i])
        }
        val shapeCost = shapeSum / (n * unit)

        // --- Length plausibility ---------------------------------------------
        val lengthCost = abs(idealLength - swipe.traceLength) / (swipe.traceLength + unit)

        return W_SHAPE * shapeCost + W_LOCATION * locationCost + W_LENGTH * lengthCost
    }

    /**
     * Writes the word's ideal gesture — the polyline through its key centres,
     * resampled to equal arc-length steps — into [outX]/[outY]. Returns the
     * polyline's total length.
     */
    private fun buildIdealPath(
        letters: ByteArray,
        cx: FloatArray,
        cy: FloatArray,
        outX: FloatArray,
        outY: FloatArray
    ): Float {
        val count = outX.size
        val n = letters.size

        var total = 0f
        for (i in 1 until n) {
            total += dist(
                cx[letters[i - 1].toInt()], cy[letters[i - 1].toInt()],
                cx[letters[i].toInt()], cy[letters[i].toInt()]
            )
        }

        val startX = cx[letters[0].toInt()]
        val startY = cy[letters[0].toInt()]
        if (total <= 0f) {
            // A word whose letters all sit on one key (e.g. "aa").
            for (i in 0 until count) {
                outX[i] = startX
                outY[i] = startY
            }
            return 0f
        }

        val step = total / (count - 1)
        outX[0] = startX
        outY[0] = startY

        var segment = 0
        var walked = 0f
        var target = step
        var out = 1

        while (out < count - 1 && segment < n - 1) {
            val ax = cx[letters[segment].toInt()]
            val ay = cy[letters[segment].toInt()]
            val bx = cx[letters[segment + 1].toInt()]
            val by = cy[letters[segment + 1].toInt()]
            val len = dist(ax, ay, bx, by)
            if (len <= 0f) {
                segment++
                continue
            }
            if (walked + len >= target) {
                val t = (target - walked) / len
                outX[out] = ax + (bx - ax) * t
                outY[out] = ay + (by - ay) * t
                out++
                target += step
            } else {
                walked += len
                segment++
            }
        }

        val endX = cx[letters[n - 1].toInt()]
        val endY = cy[letters[n - 1].toInt()]
        while (out < count) {
            outX[out] = endX
            outY[out] = endY
            out++
        }
        return total
    }

    /**
     * Indices of the resampled points where the finger clearly changed
     * direction.
     *
     * The comparison window is sized in key widths rather than in samples, so a
     * two-letter flick and a ten-letter sentence-long gesture are measured with
     * the same physical sensitivity. Corners within the window of either end are
     * skipped — the start and end of a gesture are noisy, and both are already
     * constrained by the endpoint keys.
     */
    private fun detectPivots(swipe: SampledSwipe, unit: Float): IntArray {
        val n = swipe.size
        val stepLength = swipe.traceLength / (n - 1)
        if (stepLength <= 0f) return EMPTY_PIVOTS

        val window = (unit * 0.6f / stepLength).toInt().coerceIn(1, n / 5)
        if (window < 1 || n < 2 * window + 3) return EMPTY_PIVOTS

        val px = swipe.xs
        val py = swipe.ys

        val found = IntArray(MAX_PIVOTS)
        var count = 0
        var sharpest = 0f
        var sharpestAt = -1
        var i = window

        while (i < n - window) {
            val inX = px[i] - px[i - window]
            val inY = py[i] - py[i - window]
            val outX = px[i + window] - px[i]
            val outY = py[i + window] - py[i]

            val angle = turnAngle(inX, inY, outX, outY)
            if (angle >= PIVOT_ANGLE) {
                // Keep only the sharpest point of each run of corner-ish samples,
                // otherwise one turn registers as a cluster of pivots.
                if (angle > sharpest) {
                    sharpest = angle
                    sharpestAt = i
                }
            } else if (sharpestAt >= 0) {
                if (count < MAX_PIVOTS) found[count++] = sharpestAt
                sharpest = 0f
                sharpestAt = -1
            }
            i++
        }
        if (sharpestAt >= 0 && count < MAX_PIVOTS) found[count++] = sharpestAt

        return if (count == 0) EMPTY_PIVOTS else found.copyOf(count)
    }

    /** Angle in radians between two direction vectors; 0 means dead straight. */
    private fun turnAngle(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val aLen = sqrt(ax * ax + ay * ay)
        val bLen = sqrt(bx * bx + by * by)
        if (aLen <= 0f || bLen <= 0f) return 0f
        val cos = ((ax * bx + ay * by) / (aLen * bLen)).coerceIn(-1f, 1f)
        return acos(cos)
    }

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return sqrt(dx * dx + dy * dy)
    }

    private val EMPTY_PIVOTS = IntArray(0)

    /** Fixed-size ascending-cost leaderboard; avoids sorting the whole survivor set. */
    private class TopWords(private val capacity: Int) {
        private val words = arrayOfNulls<String>(capacity)
        private val costs = FloatArray(capacity)
        private var count = 0

        fun offer(word: String, cost: Float) {
            if (count == capacity && cost >= costs[capacity - 1]) return
            var slot = if (count < capacity) count else capacity - 1
            while (slot > 0 && costs[slot - 1] > cost) {
                costs[slot] = costs[slot - 1]
                words[slot] = words[slot - 1]
                slot--
            }
            costs[slot] = cost
            words[slot] = word
            if (count < capacity) count++
        }

        fun toList(): List<String> {
            if (count == 0) return emptyList()
            val result = ArrayList<String>(count)
            for (i in 0 until count) result.add(words[i]!!)
            return result
        }
    }
}
