package com.example.composekeyboard.input.swipe

import kotlin.math.sqrt

/**
 * Growable buffer of the raw finger path for one gesture.
 *
 * Kept as parallel primitive arrays rather than a list of points: a fast finger
 * on a 120 Hz panel produces a few hundred samples per word, and this is touched
 * on every motion event and on every frame the trail is drawn.
 *
 * Owned by the main thread. [sample] produces the immutable snapshot the decoder
 * works from.
 */
class SwipeTrace {

    private var xs = FloatArray(INITIAL_CAPACITY)
    private var ys = FloatArray(INITIAL_CAPACITY)
    private var ts = LongArray(INITIAL_CAPACITY)

    var size: Int = 0
        private set

    /** Arc length of the path so far, in pixels. */
    var length: Float = 0f
        private set

    fun x(i: Int): Float = xs[i]

    fun y(i: Int): Float = ys[i]

    fun timeAt(i: Int): Long = ts[i]

    fun clear() {
        size = 0
        length = 0f
    }

    /**
     * Appends a point, dropping samples that land essentially on top of the
     * previous one. Returns true if the buffer actually grew.
     */
    fun add(x: Float, y: Float, timeMillis: Long): Boolean {
        if (size > 0) {
            val dx = x - xs[size - 1]
            val dy = y - ys[size - 1]
            val d = sqrt(dx * dx + dy * dy)
            if (d < MIN_POINT_SPACING_PX) return false
            length += d
        }
        if (size == xs.size) grow()
        xs[size] = x
        ys[size] = y
        ts[size] = timeMillis
        size++
        return true
    }

    /**
     * Resamples the path into [count] points spaced equally by arc length.
     *
     * Equal-arc-length spacing is what makes the shape comparison in the decoder
     * meaningful: it removes any dependence on how fast the finger was moving,
     * so a slow careful gesture and a quick flick of the same word produce the
     * same series of points.
     */
    fun sample(count: Int): SampledSwipe? {
        if (size < 2 || count < 2) return null
        val outX = FloatArray(count)
        val outY = FloatArray(count)

        val step = length / (count - 1)
        if (step <= 0f) return null

        outX[0] = xs[0]
        outY[0] = ys[0]

        var src = 0
        var walked = 0f          // arc length consumed up to the start of `src`
        var target = step
        var out = 1

        while (out < count - 1 && src < size - 1) {
            val dx = xs[src + 1] - xs[src]
            val dy = ys[src + 1] - ys[src]
            val segment = sqrt(dx * dx + dy * dy)
            if (segment <= 0f) {
                src++
                continue
            }
            if (walked + segment >= target) {
                val t = (target - walked) / segment
                outX[out] = xs[src] + dx * t
                outY[out] = ys[src] + dy * t
                out++
                target += step
            } else {
                walked += segment
                src++
            }
        }

        // Floating point drift can leave the tail unfilled; pin it to the end.
        while (out < count) {
            outX[out] = xs[size - 1]
            outY[out] = ys[size - 1]
            out++
        }

        return SampledSwipe(outX, outY, length)
    }

    /**
     * Immutable-in-practice copy for the decode thread.
     *
     * The neural decoder needs the raw timestamped points, not the arc-length
     * resample, so it cannot share [sample]'s output -- and the live buffer is
     * cleared the instant the finger lifts.
     */
    fun snapshot(): SwipeTrace {
        val copy = SwipeTrace()
        copy.xs = xs.copyOf(size)
        copy.ys = ys.copyOf(size)
        copy.ts = ts.copyOf(size)
        copy.size = size
        copy.length = length
        return copy
    }

    private fun grow() {
        val capacity = xs.size * 2
        xs = xs.copyOf(capacity)
        ys = ys.copyOf(capacity)
        ts = ts.copyOf(capacity)
    }

    private companion object {
        const val INITIAL_CAPACITY = SwipeConstants.INITIAL_CAPACITY

        /** Below this the sample adds nothing but noise and buffer pressure. */
        const val MIN_POINT_SPACING_PX = SwipeConstants.MIN_POINT_SPACING_PX
    }
}

/** Arc-length-normalised copy of a gesture, safe to hand to another thread. */
class SampledSwipe(
    val xs: FloatArray,
    val ys: FloatArray,
    /** Arc length of the original path in pixels. */
    val traceLength: Float
) {
    val size: Int get() = xs.size
}
