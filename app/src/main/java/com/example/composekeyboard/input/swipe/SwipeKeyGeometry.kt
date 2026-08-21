package com.example.composekeyboard.input.swipe

import androidx.compose.ui.geometry.Offset
import com.example.composekeyboard.data.KeyType
import com.example.composekeyboard.data.SwipeDictionary.Companion.ALPHABET

/**
 * Live map of where each letter key sits, in the coordinate space of the
 * keyboard body — i.e. the same space the swipe pointer events arrive in.
 *
 * Keys report themselves in *window* coordinates and the body reports its own
 * window origin; the two are subtracted on read. That way it does not matter
 * whether a key is placed before or after its container in a given layout pass.
 *
 * Mutated only from the composition/main thread. Background decoding works off
 * [snapshot], never off this object.
 */
class SwipeKeyGeometry {

    private val winX = FloatArray(ALPHABET)
    private val winY = FloatArray(ALPHABET)
    private val widths = FloatArray(ALPHABET)
    private val heights = FloatArray(ALPHABET)
    private val placed = BooleanArray(ALPHABET)

    private var originX = 0f
    private var originY = 0f
    private var placedCount = 0

    private var metricsDirty = true
    private var cachedKeyWidth = 0f
    private var cachedKeyHeight = 0f

    /** Typical letter-key width; the unit every swipe tolerance is expressed in. */
    val keyWidth: Float
        get() {
            recomputeMetrics()
            return cachedKeyWidth
        }

    val keyHeight: Float
        get() {
            recomputeMetrics()
            return cachedKeyHeight
        }

    /**
     * True once enough of the alphabet has been laid out to decode against.
     * A handful of missing keys is tolerable — a whole missing row is not.
     */
    val isReady: Boolean
        get() = placedCount >= MIN_PLACED_KEYS && keyWidth > 0f

    fun setBodyOrigin(x: Float, y: Float) {
        originX = x
        originY = y
    }

    fun place(letter: Int, windowX: Float, windowY: Float, width: Float, height: Float) {
        if (letter !in 0 until ALPHABET || width <= 0f || height <= 0f) return
        if (!placed[letter]) {
            placed[letter] = true
            placedCount++
        }
        winX[letter] = windowX + width / 2f
        winY[letter] = windowY + height / 2f
        widths[letter] = width
        heights[letter] = height
        metricsDirty = true
    }

    fun reset() {
        placed.fill(false)
        placedCount = 0
        metricsDirty = true
    }

    fun centerX(letter: Int): Float = winX[letter] - originX

    fun centerY(letter: Int): Float = winY[letter] - originY

    /**
     * Index of the letter key under [position], or -1 if it is not on a letter.
     * Cells are inflated slightly so the hairline gaps between rows do not read
     * as "not a letter" and swallow the start of a gesture.
     */
    fun letterAt(position: Offset): Int {
        for (i in 0 until ALPHABET) {
            if (!placed[i]) continue
            val halfW = widths[i] / 2f + HIT_SLOP_PX
            val halfH = heights[i] / 2f + HIT_SLOP_PX
            val cx = winX[i] - originX
            val cy = winY[i] - originY
            if (position.x >= cx - halfW && position.x <= cx + halfW &&
                position.y >= cy - halfH && position.y <= cy + halfH
            ) {
                return i
            }
        }
        return -1
    }

    /** Immutable copy for the decoder, which runs off the main thread. */
    fun snapshot(): SwipeKeyMap {
        val cx = FloatArray(ALPHABET)
        val cy = FloatArray(ALPHABET)
        for (i in 0 until ALPHABET) {
            cx[i] = winX[i] - originX
            cy[i] = winY[i] - originY
        }
        return SwipeKeyMap(cx, cy, placed.copyOf(), keyWidth, keyHeight)
    }

    private fun recomputeMetrics() {
        if (!metricsDirty) return
        metricsDirty = false
        var sumW = 0f
        var sumH = 0f
        var n = 0
        for (i in 0 until ALPHABET) {
            if (!placed[i]) continue
            sumW += widths[i]
            sumH += heights[i]
            n++
        }
        cachedKeyWidth = if (n > 0) sumW / n else 0f
        cachedKeyHeight = if (n > 0) sumH / n else 0f
    }

    companion object {
        private const val MIN_PLACED_KEYS = 20
        private const val HIT_SLOP_PX = 3f

        /** Letter index for a key model, or -1 for anything not a plain letter. */
        fun letterIndexOf(type: KeyType): Int {
            if (type !is KeyType.Character) return -1
            if (type.primary.length != 1) return -1
            val c = type.primary[0].lowercaseChar()
            return if (c in 'a'..'z') c - 'a' else -1
        }
    }
}
