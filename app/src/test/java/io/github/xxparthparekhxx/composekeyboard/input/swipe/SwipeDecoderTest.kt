package io.github.xxparthparekhxx.composekeyboard.input.swipe

import io.github.xxparthparekhxx.composekeyboard.data.SwipeDictionary
import io.github.xxparthparekhxx.composekeyboard.data.SwipeDictionaryTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the geometric fallback path (SHARK²-family scorer). The neural
 * decoder is preferred when its weights load, but this path is the entire
 * keyboard on devices where the asset is missing — and the live gesture
 * preview always runs through it. Previously the only decoder coverage was a
 * docstring claiming instrumentation tests would handle it; the dictionary
 * fake in [SwipeDictionaryTest] makes plain JUnit sufficient.
 */
class SwipeDecoderTest {

    private fun buildQwertyMap(): SwipeKeyMap {
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
                val x = col * keyW + (if (row == 1f) keyW / 2 else 0f)
                geometry.place(c - 'a', x, row * keyH, keyW, keyH)
            }
        }
        return geometry.snapshot()
    }

    private fun centerOf(map: SwipeKeyMap, c: Char): Pair<Float, Float> {
        val i = c - 'a'
        return map.centerX[i] to map.centerY[i]
    }

    /** Dense timestamped polyline through the given letters' key centres. */
    private fun traceThrough(map: SwipeKeyMap, word: String): SwipeTrace {
        val trace = SwipeTrace()
        var t = 0L
        val perLeg = 12
        val letters = word.filter { it in 'a'..'z' }
        for (k in 0 until letters.length - 1) {
            val (x0, y0) = centerOf(map, letters[k])
            val (x1, y1) = centerOf(map, letters[k + 1])
            for (s in 0 until perLeg) {
                val f = s.toFloat() / perLeg
                trace.add(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, t)
                t += 16
            }
        }
        val (lx, ly) = centerOf(map, letters.last())
        trace.add(lx, ly, t)
        return trace
    }

    private fun learnedDict(vararg words: String): SwipeDictionary {
        val dict = SwipeDictionaryTest.createTestDictionary()
        for (w in words) repeat(3) { dict.learn(w) }
        return dict
    }

    @Test
    fun decode_exactCentres_findsWord() {
        val map = buildQwertyMap()
        val dict = learnedDict("hello")
        val sampled = traceThrough(map, "hello").sample(SwipeDecoder.SAMPLES)!!

        val results = SwipeDecoder.decode(sampled, map, dict, 4)

        assertTrue("expected hello in $results", "hello" in results)
        assertEquals("hello", results.first())
    }

    @Test
    fun decode_emptyDictionary_returnsEmpty() {
        val map = buildQwertyMap()
        val dict = SwipeDictionaryTest.createTestDictionary()
        val sampled = traceThrough(map, "hello").sample(SwipeDecoder.SAMPLES)!!

        assertTrue(SwipeDecoder.decode(sampled, map, dict).isEmpty())
    }

    @Test
    fun decode_tooShortTrace_returnsEmpty() {
        val map = buildQwertyMap()
        val dict = learnedDict("hello")
        val trace = SwipeTrace()
        val (x, y) = centerOf(map, 'h')
        trace.add(x, y, 0L)
        trace.add(x + 1f, y + 1f, 16L)
        val sampled = trace.sample(SwipeDecoder.SAMPLES)!!

        assertTrue(SwipeDecoder.decode(sampled, map, dict).isEmpty())
    }

    @Test
    fun decode_farFromKeys_returnsEmpty() {
        val map = buildQwertyMap()
        val dict = learnedDict("hello")
        val trace = SwipeTrace()
        var t = 0L
        for (i in 0..40) {
            trace.add(5000f + i * 20f, 5000f, t)
            t += 16
        }
        val sampled = trace.sample(SwipeDecoder.SAMPLES)!!

        assertTrue(SwipeDecoder.decode(sampled, map, dict).isEmpty())
    }

    @Test
    fun decode_prefersCorrectWordOverDistractor() {
        val map = buildQwertyMap()
        val dict = learnedDict("hello", "hallo")
        val sampled = traceThrough(map, "hello").sample(SwipeDecoder.SAMPLES)!!

        val results = SwipeDecoder.decode(sampled, map, dict, 4)

        assertTrue("expected hello first in $results", results.firstOrNull() == "hello")
    }
}
