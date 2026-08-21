package com.example.composekeyboard.input.swipe

import com.example.composekeyboard.data.SwipeDictionary.Companion.ALPHABET
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Frozen key centres handed to the decoder. */
class SwipeKeyMap(
    val centerX: FloatArray,
    val centerY: FloatArray,
    val placed: BooleanArray,
    val keyWidth: Float,
    val keyHeight: Float
) {
    fun distanceTo(letter: Int, x: Float, y: Float): Float {
        val dx = centerX[letter] - x
        val dy = centerY[letter] - y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Fills [out] with up to [limit] letter keys within [maxDistance] of the
     * point, nearest first. Returns how many were written.
     */
    fun nearest(x: Float, y: Float, maxDistance: Float, limit: Int, out: IntArray): Int {
        if (limit <= 0) return 0
        val dists = FloatArray(limit)
        var count = 0
        for (i in 0 until ALPHABET) {
            if (!placed[i]) continue
            val d = distanceTo(i, x, y)
            if (d > maxDistance) continue
            if (count == limit && d >= dists[limit - 1]) continue
            // Insertion sort into the fixed-size result window, evicting the worst.
            var slot = if (count < limit) count else limit - 1
            while (slot > 0 && dists[slot - 1] > d) {
                dists[slot] = dists[slot - 1]
                out[slot] = out[slot - 1]
                slot--
            }
            dists[slot] = d
            out[slot] = i
            if (count < limit) count++
        }
        return count
    }

    /**
     * Bounding box of the letter keys as [left, top, width, height].
     *
     * This is the box the neural decoder normalizes into. The training layouts
     * define their keys to span exactly [0,1] on both axes, so matching that
     * convention here is what lets one set of encoder weights read this app's
     * keyboard -- whose three rows have three different key widths -- without
     * any layout-specific handling.
     *
     * Cached: it only changes when the keyboard is resized.
     */
    fun letterBounds(): FloatArray {
        bounds?.let { return it }
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        val halfW = keyWidth / 2f
        val halfH = keyHeight / 2f
        for (i in 0 until ALPHABET) {
            if (!placed[i]) continue
            left = min(left, centerX[i] - halfW)
            top = min(top, centerY[i] - halfH)
            right = max(right, centerX[i] + halfW)
            bottom = max(bottom, centerY[i] + halfH)
        }
        val w = if (right > left) right - left else 1f
        val h = if (bottom > top) bottom - top else 1f
        return floatArrayOf(left, top, w, h).also { bounds = it }
    }

    /** Cheap identity for the current geometry, so derived tables can be cached. */
    fun layoutHash(): Int {
        var h = 17
        for (i in 0 until ALPHABET) {
            if (!placed[i]) continue
            h = h * 31 + centerX[i].toRawBits()
            h = h * 31 + centerY[i].toRawBits()
        }
        return h * 31 + keyWidth.toRawBits()
    }

    private var bounds: FloatArray? = null
}
