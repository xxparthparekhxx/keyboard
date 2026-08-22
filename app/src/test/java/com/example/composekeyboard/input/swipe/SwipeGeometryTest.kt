package com.example.composekeyboard.input.swipe

import com.example.composekeyboard.data.KeyType
import com.example.composekeyboard.data.SwipeDictionary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure geometric pieces of the swipe engine. The decoder itself
 * needs a loaded [SwipeDictionary], which requires an Android Context, so it is
 * covered by instrumentation tests; everything here runs on plain JUnit.
 */
class SwipeGeometryTest {

    private fun buildQwertyGeometry(): SwipeKeyGeometry {
        val geometry = SwipeKeyGeometry()
        val rows = listOf(
            "qwertyuiop" to 0f,
            "asdfghjkl" to 1f,
            "zxcvbnm" to 2f
        )
        val keyW = 40f
        val keyH = 50f
        for ((letters, row) in rows) {
            letters.forEachIndexed { col, c ->
                // Row 2 is inset by half a key, as on a real keyboard.
                val x = col * keyW + (if (row == 1f) keyW / 2 else 0f)
                geometry.place(c - 'a', x, row * keyH, keyW, keyH)
            }
        }
        return geometry
    }

    @Test
    fun letterIndexOf_character_returnsIndex() {
        assertEquals(0, SwipeKeyGeometry.letterIndexOf(KeyType.Character("a")))
        assertEquals(25, SwipeKeyGeometry.letterIndexOf(KeyType.Character("Z")))
    }

    @Test
    fun letterIndexOf_nonLetters_returnMinusOne() {
        assertEquals(-1, SwipeKeyGeometry.letterIndexOf(KeyType.Shift))
        assertEquals(-1, SwipeKeyGeometry.letterIndexOf(KeyType.Backspace))
        assertEquals(-1, SwipeKeyGeometry.letterIndexOf(KeyType.Space))
        assertEquals(-1, SwipeKeyGeometry.letterIndexOf(KeyType.Character(",")))
        assertEquals(-1, SwipeKeyGeometry.letterIndexOf(KeyType.Character("ab")))
    }

    @Test
    fun place_and_letterAt_roundTrip() {
        val geometry = buildQwertyGeometry()

        // Centre of the 'q' key cell.
        val q = geometry.letterAt(androidx.compose.ui.geometry.Offset(20f, 25f))
        assertEquals('q' - 'a', q)
    }

    @Test
    fun letterAt_outsideKeys_returnsMinusOne() {
        val geometry = buildQwertyGeometry()

        val miss = geometry.letterAt(androidx.compose.ui.geometry.Offset(5000f, 5000f))
        assertEquals(-1, miss)
    }

    @Test
    fun isReady_requiresTwentyPlacedKeys() {
        val geometry = SwipeKeyGeometry()
        assertFalse(geometry.isReady)

        // Place 19 keys: still not ready.
        for (i in 0 until 19) {
            geometry.place(i, 0f, 0f, 40f, 50f)
        }
        assertFalse(geometry.isReady)

        // The 20th crosses the threshold.
        geometry.place(19, 40f, 0f, 40f, 50f)
        assertTrue(geometry.isReady)
    }

    @Test
    fun reset_clearsPlacement() {
        val geometry = buildQwertyGeometry()
        assertTrue(geometry.isReady)

        geometry.reset()

        assertFalse(geometry.isReady)
    }

    @Test
    fun snapshot_subtractsBodyOrigin() {
        val geometry = buildQwertyGeometry()
        geometry.setBodyOrigin(100f, 200f)

        val map = geometry.snapshot()

        // Key centres must be expressed relative to the body origin.
        assertEquals(geometry.centerX('q' - 'a'), map.centerX['q' - 'a'], 0.001f)
        assertEquals(geometry.centerY('q' - 'a'), map.centerY['q' - 'a'], 0.001f)
    }

    @Test
    fun nearest_findsClosestKeysInOrder() {
        val geometry = buildQwertyGeometry()
        val map = geometry.snapshot()

        val out = IntArray(4)
        val count = map.nearest(
            map.centerX['t' - 'a'], map.centerY['t' - 'a'],
            maxDistance = 100f, limit = 4, out = out
        )

        assertTrue(count > 0)
        assertEquals('t' - 'a', out[0])
        // Results are sorted nearest-first.
        var prev = 0f
        for (i in 0 until count) {
            val d = map.distanceTo(out[i], map.centerX['t' - 'a'], map.centerY['t' - 'a'])
            assertTrue(d >= prev)
            prev = d
        }
    }

    @Test
    fun nearest_respectsMaxDistance() {
        val geometry = buildQwertyGeometry()
        val map = geometry.snapshot()

        // A tiny radius admits only the key under the point itself.
        val out = IntArray(4)
        val count = map.nearest(
            map.centerX['q' - 'a'], map.centerY['q' - 'a'],
            maxDistance = 1f, limit = 4, out = out
        )

        assertEquals(1, count)
        assertEquals('q' - 'a', out[0])

        // A point far from every key admits nothing.
        val far = IntArray(4)
        assertEquals(
            0,
            map.nearest(-10000f, -10000f, maxDistance = 100f, limit = 4, out = far)
        )
    }

    @Test
    fun trace_add_accumulatesLengthAndSize() {
        val trace = SwipeTrace()

        trace.add(0f, 0f, 0L)
        trace.add(30f, 40f, 16L) // distance 50

        assertEquals(2, trace.size)
        assertEquals(50f, trace.length, 0.001f)
    }

    @Test
    fun trace_add_dropsPointsCloserThanMinSpacing() {
        val trace = SwipeTrace()

        assertTrue(trace.add(0f, 0f, 0L))
        // 1px apart: below MIN_POINT_SPACING_PX, rejected.
        assertFalse(trace.add(1f, 0f, 8L))
        assertEquals(1, trace.size)
        assertEquals(0f, trace.length, 0.001f)
    }

    @Test
    fun trace_sample_resamplesToEqualArcLength() {
        val trace = SwipeTrace()
        // Straight line of length 310.
        for (i in 0..31) {
            trace.add(i * 10f, 0f, i * 10L)
        }

        val sampled = trace.sample(SwipeConstants.SAMPLES)

        assertNotNull(sampled)
        assertEquals(SwipeConstants.SAMPLES, sampled!!.size)
        assertEquals(310f, sampled.traceLength, 0.5f)
        // Equal spacing along a straight line.
        assertEquals(10f, sampled.xs[1] - sampled.xs[0], 0.5f)
        assertEquals(310f, sampled.xs[sampled.size - 1], 0.5f)
    }

    @Test
    fun trace_sample_tooShort_returnsNull() {
        val trace = SwipeTrace()
        trace.add(0f, 0f, 0L)

        assertNull(trace.sample(SwipeConstants.SAMPLES))
    }

    @Test
    fun trace_snapshot_isIndependentCopy() {
        val trace = SwipeTrace()
        trace.add(0f, 0f, 0L)
        trace.add(50f, 0f, 10L)

        val copy = trace.snapshot()
        trace.clear()

        assertEquals(2, copy.size)
        assertEquals(50f, copy.length, 0.001f)
        assertEquals(0, trace.size)
    }

    @Test
    fun trace_grow_handlesMoreThanInitialCapacity() {
        val trace = SwipeTrace()
        // INITIAL_CAPACITY is 256; push past it with well-spaced points.
        val n = SwipeConstants.INITIAL_CAPACITY + 64
        for (i in 0 until n) {
            trace.add(i * 5f, 0f, i.toLong())
        }

        assertEquals(n, trace.size)
    }
}
