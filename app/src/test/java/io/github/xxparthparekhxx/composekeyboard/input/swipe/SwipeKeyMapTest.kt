package io.github.xxparthparekhxx.composekeyboard.input.swipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeKeyMapTest {

    private fun uniformMap(): SwipeKeyMap {
        // 26 keys on a grid: col = i % 13, row = i / 13.
        val cx = FloatArray(26) { (it % 13) * 40f + 20f }
        val cy = FloatArray(26) { (it / 13) * 50f + 25f }
        return SwipeKeyMap(cx, cy, BooleanArray(26) { true }, 40f, 50f)
    }

    @Test
    fun nearest_returnsClosestFirst() {
        val map = uniformMap()
        val out = IntArray(3)
        // 'a' (index 0) sits at (20, 25).
        val n = map.nearest(22f, 27f, 200f, 3, out)

        assertEquals(3, n)
        assertEquals(0, out[0])
    }

    @Test
    fun nearest_respectsLimitAndDistance() {
        val map = uniformMap()
        val out = IntArray(26)
        val n = map.nearest(22f, 27f, 1f, 26, out)

        assertEquals(0, n)
    }

    @Test
    fun nearest_skipsUnplacedKeys() {
        val map = uniformMap()
        map.apply { placed['a' - 'a'] = false }
        val out = IntArray(3)
        val n = map.nearest(20f, 25f, 200f, 3, out)

        assertTrue(n > 0)
        for (i in 0 until n) assertTrue(out[i] != 0)
    }

    @Test
    fun letterBounds_spansKeyExtents() {
        val map = uniformMap()
        val box = map.letterBounds()

        // x: 0..520, y: 0..100 (13 cols * 40, 2 rows * 50).
        assertEquals(0f, box[0], 0.001f)
        assertEquals(0f, box[1], 0.001f)
        assertEquals(520f, box[2], 0.001f)
        assertEquals(100f, box[3], 0.001f)
    }

    @Test
    fun layoutHash_stableForSameGeometry() {
        val a = uniformMap()
        val b = uniformMap()
        assertEquals(a.layoutHash(), b.layoutHash())

        b.centerX[0] = 999f
        assertTrue(a.layoutHash() != b.layoutHash())
    }
}
