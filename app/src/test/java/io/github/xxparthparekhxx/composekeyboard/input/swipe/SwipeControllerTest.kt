package io.github.xxparthparekhxx.composekeyboard.input.swipe

import androidx.compose.ui.geometry.Offset
import io.github.xxparthparekhxx.composekeyboard.data.SwipeDictionary
import io.github.xxparthparekhxx.composekeyboard.data.SwipeDictionaryTest
import io.github.xxparthparekhxx.composekeyboard.input.swipe.nn.SwipeBeam
import io.github.xxparthparekhxx.composekeyboard.input.swipe.nn.SwipeNeuralDecoder
import io.github.xxparthparekhxx.composekeyboard.input.swipe.nn.SwipeNet
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives [SwipeController] through its [SwipeGestureHandler] surface, replaying
 * the exact call sequence [SwipeGestureDetector] produces from real pointers:
 * down → (move)* → recognized → up/cancel, plus the layout-change paths
 * [SwipeController.cancel] and [SwipeController.reset].
 *
 * Plain JUnit suffices: the controller needs no framework APIs, and the
 * one test that touches [SwipeNeuralDecoder] constructs it via reflection
 * (the private constructor takes the net + beam directly, so the asset
 * loader's Context/Log dependency is never exercised) and calls only [SwipeNeuralDecoder.decode].
 *
 * Decode jobs are started on a [Dispatchers.Unconfined] scope so they begin
 * synchronously — which is what makes the reset-during-decode test pin-able —
 * but the ranking itself runs on [Dispatchers.Default], so callbacks are
 * observed through [Capture]'s thread-safe state with bounded polling.
 */
class SwipeControllerTest {

    private val jobs = mutableListOf<Job>()

    @After
    fun tearDown() {
        for (job in jobs) job.cancel()
    }

    private fun newController(
        geometry: SwipeKeyGeometry,
        dictionary: SwipeDictionary
    ): Pair<SwipeController, Capture> {
        val job = Job()
        jobs += job
        val scope = CoroutineScope(job + Dispatchers.Unconfined)
        val controller = SwipeController(geometry, dictionary, scope)
        val capture = Capture()
        controller.onResult = { words -> capture.onResult(words) }
        controller.onRecognized = { capture.onRecognized() }
        return controller to capture
    }

    /**
     * QWERTY layout with the same coordinates the decoder and geometry tests
     * use, so a gesture through key centres is known-good for the scorer.
     */
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

    private fun centerOf(geometry: SwipeKeyGeometry, letter: Int): Offset =
        Offset(geometry.centerX(letter), geometry.centerY(letter))

    /**
     * Fresh lexicon with [words] taught in the way a user would. Three
     * sightings clear [SwipeDictionary.NEW_WORD_THRESHOLD] so the words are
     * actually in the bucket, not just the learned-word overlay.
     */
    private fun testDictionary(vararg words: String): SwipeDictionary {
        val dict = SwipeDictionaryTest.createTestDictionary()
        for (word in words) repeat(3) { dict.learn(word) }
        return dict
    }

    /**
     * Runs [word] through [controller] as a dense polyline through key
     * centres: down, a couple of moves, recognition, the rest of the path,
     * up. [stepMs] times the whole gesture; at the default 1 ms per point a
     * five-letter word finishes in 32 ms, inside the 55 ms preview interval,
     * so tests that don't assert on the preview never see one fire.
     */
    private fun swipeWord(
        controller: SwipeController,
        geometry: SwipeKeyGeometry,
        word: String,
        stepMs: Long = 1L
    ) {
        val letters = word.filter { it in 'a'..'z' }.map { it - 'a' }
        var t = 0L
        val start = centerOf(geometry, letters.first())
        controller.onTouchDown(start, letters.first(), t)
        for (k in 0 until letters.size - 1) {
            val from = centerOf(geometry, letters[k])
            val to = centerOf(geometry, letters[k + 1])
            for (s in 1..SUBSTEPS_PER_LEG) {
                val f = s / SUBSTEPS_PER_LEG.toFloat()
                t += stepMs
                controller.onTouchMove(
                    Offset(from.x + (to.x - from.x) * f, from.y + (to.y - from.y) * f),
                    t
                )
            }
            if (k == 0) controller.onSwipeRecognized()
        }
        controller.onTouchUp()
    }

    /** Polls [condition] on wall-clock time; the decodes run on a real pool. */
    private fun awaitUntil(timeoutMs: Int, condition: () -> Boolean): Boolean {
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (true) {
            if (condition()) return true
            if (System.nanoTime() >= deadline) return false
            Thread.sleep(5)
        }
    }

    /** The encoder weights, found the same way the neural decoder's own test does. */
    private fun loadSwipeNet(): SwipeNet {
        val candidates = listOf(
            File("src/main/assets/swipe_encoder.bin"),
            File("app/src/main/assets/swipe_encoder.bin"),
            File("../app/src/main/assets/swipe_encoder.bin")
        )
        val file = candidates.firstOrNull { it.exists() }
        assertNotNull("swipe_encoder.bin not found", file)
        return FileInputStream(file!!).use { SwipeNet.load(it) }
    }

    private fun buildNeuralDecoder(net: SwipeNet, beam: SwipeBeam): SwipeNeuralDecoder {
        val constructor = SwipeNeuralDecoder::class.java.getDeclaredConstructor(
            SwipeNet::class.java, SwipeBeam::class.java, Int::class.javaPrimitiveType
        )
        constructor.isAccessible = true
        return constructor.newInstance(net, beam, 0) as SwipeNeuralDecoder
    }

    @Test
    fun gesture_withoutPlacedKeys_yieldsNoResult() {
        val geometry = buildQwertyGeometry()
        val (controller, capture) = newController(geometry, testDictionary("hello"))

        val h = centerOf(geometry, 'h' - 'a')
        controller.onTouchDown(h, 'h' - 'a', 0L)
        // Layout torn down mid-gesture: the trace is well-formed but there is
        // no key geometry left to decode against.
        geometry.reset()
        for (i in 1..20) {
            controller.onTouchMove(Offset(h.x + i * 20f, h.y), i.toLong())
        }
        controller.onSwipeRecognized()
        controller.onTouchUp()

        assertFalse(awaitUntil(300) { capture.resultCount.get() > 0 })
        assertFalse(controller.isSwiping)
        assertTrue(controller.path.size == 0)
    }

    @Test
    fun lift_beforeRecognition_isIgnoredLikeATap() {
        val geometry = buildQwertyGeometry()
        val (controller, capture) = newController(geometry, testDictionary("hello"))

        val h = centerOf(geometry, 'h' - 'a')
        controller.onTouchDown(h, 'h' - 'a', 0L)
        controller.onTouchMove(Offset(h.x + 8f, h.y + 4f), 10L)
        controller.onTouchUp()

        assertFalse(awaitUntil(300) { capture.resultCount.get() > 0 })
        assertFalse(controller.isSwiping)
        assertEquals(null, controller.preview)
        assertTrue(controller.path.size == 0)
    }

    @Test
    fun touchDown_restartsInFlightTrace() {
        val geometry = buildQwertyGeometry()
        val (controller, capture) = newController(geometry, testDictionary("hello"))

        val h = centerOf(geometry, 'h' - 'a')
        controller.onTouchDown(h, 'h' - 'a', 0L)
        controller.onTouchMove(Offset(h.x + 10f, h.y + 5f), 10L)
        controller.onTouchMove(Offset(h.x + 20f, h.y + 10f), 20L)
        assertEquals(3, controller.path.size)

        // A fresh touch down begins a new gesture from the new key.
        val m = centerOf(geometry, 'm' - 'a')
        controller.onTouchDown(m, 'm' - 'a', 30L)

        assertEquals(1, controller.path.size)
        assertEquals(m.x, controller.path.x(0), 0.001f)
        assertEquals(m.y, controller.path.y(0), 0.001f)
        assertEquals(null, controller.preview)

        controller.onTouchCancel()
        assertTrue(controller.path.size == 0)
        assertFalse(controller.isSwiping)
    }

    @Test
    fun recognizedGesture_decodesWordAndReturnsToIdle() {
        val geometry = buildQwertyGeometry()
        val (controller, capture) = newController(geometry, testDictionary("hello", "hallo"))
        val versionBefore = controller.trailVersion

        swipeWord(controller, geometry, "hello")

        assertTrue(awaitUntil(5000) { capture.resultCount.get() == 1 })
        assertEquals("hello", capture.allResults.single().first())

        assertEquals(1, capture.recognizedCount.get())
        assertTrue(controller.trailVersion > versionBefore)
        assertFalse(controller.isSwiping)
        assertEquals(null, controller.preview)
        assertTrue(controller.path.size == 0)
    }

    @Test
    fun preview_showsPredictedWordAndClearedOnLift() {
        val geometry = buildQwertyGeometry()
        val (controller, capture) = newController(geometry, testDictionary("hello", "hallo"))

        val h = centerOf(geometry, 'h' - 'a')
        val e = centerOf(geometry, 'e' - 'a')
        val l = centerOf(geometry, 'l' - 'a')
        val o = centerOf(geometry, 'o' - 'a')

        controller.onTouchDown(h, 'h' - 'a', 0L)
        controller.onTouchMove(e, 2L)
        controller.onSwipeRecognized()
        controller.onTouchMove(l, 4L)
        controller.onTouchMove(Offset((l.x + o.x) / 2, (l.y + o.y) / 2), 10L)
        // First move past the 55 ms interval: the preview fires on a
        // near-complete path, so it must be the word a lift would commit.
        controller.onTouchMove(o, 100L)

        assertTrue(awaitUntil(5000) { controller.preview != null })
        assertEquals("hello", controller.preview)

        controller.onTouchUp()
        assertTrue(awaitUntil(5000) { capture.resultCount.get() == 1 })
        assertEquals("hello", capture.allResults.single().first())
        assertEquals(null, controller.preview)
        assertFalse(controller.isSwiping)
    }

    @Test
    fun preview_suppressedWithinThrottleWindow() {
        val geometry = buildQwertyGeometry()
        val (controller, capture) = newController(geometry, testDictionary("hello", "hallo"))

        val h = centerOf(geometry, 'h' - 'a')
        val e = centerOf(geometry, 'e' - 'a')
        val l = centerOf(geometry, 'l' - 'a')
        val o = centerOf(geometry, 'o' - 'a')

        controller.onTouchDown(h, 'h' - 'a', 0L)
        controller.onSwipeRecognized()
        // Every move below stays inside the 55 ms interval from the down.
        controller.onTouchMove(Offset(h.x + 20f, h.y + 10f), 10L)
        controller.onTouchMove(e, 20L)
        controller.onTouchMove(l, 30L)
        controller.onTouchMove(Offset((l.x + o.x) / 2, (l.y + o.y) / 2), 40L)

        // If the interval were not enforced, a decode would have completed
        // long before this window closes and set the preview.
        assertFalse(awaitUntil(300) { controller.preview != null })

        controller.onTouchMove(o, 100L)
        assertTrue(awaitUntil(5000) { controller.preview != null })
        assertEquals("hello", controller.preview)

        controller.onTouchCancel()
        assertFalse(awaitUntil(300) { capture.resultCount.get() > 0 })
        assertEquals(null, controller.preview)
        assertTrue(controller.path.size == 0)
        assertFalse(controller.isSwiping)
    }

    @Test
    fun touchCancel_returnsToIdleWithoutDecoding() {
        val geometry = buildQwertyGeometry()
        val (controller, capture) = newController(geometry, testDictionary("hello"))

        val h = centerOf(geometry, 'h' - 'a')
        val e = centerOf(geometry, 'e' - 'a')
        val l = centerOf(geometry, 'l' - 'a')

        controller.onTouchDown(h, 'h' - 'a', 0L)
        controller.onTouchMove(e, 2L)
        controller.onSwipeRecognized()
        controller.onTouchMove(l, 4L)
        controller.onTouchCancel()

        assertFalse(awaitUntil(300) { capture.resultCount.get() > 0 })
        assertFalse(controller.isSwiping)
        assertEquals(null, controller.preview)
        assertTrue(controller.path.size == 0)
    }

    @Test
    fun reset_cancelsInFlightDecodeAndGestureCanFollow() {
        val geometry = buildQwertyGeometry()
        val (controller, capture) = newController(geometry, testDictionary("hello"))

        // A decode is now running for this word; kill it, as a field change
        // would, so it cannot commit into the next field.
        swipeWord(controller, geometry, "hello")
        controller.reset()

        assertFalse(awaitUntil(500) { capture.resultCount.get() > 0 })
        assertFalse(controller.isSwiping)

        swipeWord(controller, geometry, "hello")
        assertTrue(awaitUntil(5000) { capture.resultCount.get() == 1 })
        assertEquals("hello", capture.allResults.single().first())
    }

    @Test
    fun neuralThatDeclines_fallsBackToGeometric() {
        // An empty trie makes the beam search return nothing no matter what
        // the network emits, so the controller must fall back to the
        // geometric decoder for the same gesture.
        val decoder = buildNeuralDecoder(loadSwipeNet(), SwipeBeam.build(emptyList(), intArrayOf()))

        val geometry = buildQwertyGeometry()
        val (controller, capture) = newController(geometry, testDictionary("hello"))
        controller.neural = decoder

        swipeWord(controller, geometry, "hello")

        assertTrue(awaitUntil(5000) { capture.resultCount.get() == 1 })
        assertEquals("hello", capture.allResults.single().first())
    }

    @Test
    fun consecutiveGestures_restartCleanly() {
        val geometry = buildQwertyGeometry()
        val (controller, capture) = newController(geometry, testDictionary("hello", "hallo"))

        swipeWord(controller, geometry, "hello")
        assertTrue(awaitUntil(5000) { capture.resultCount.get() == 1 })

        // A finished gesture leaves the machine idle and empty.
        assertFalse(controller.isSwiping)
        assertEquals(null, controller.preview)
        assertTrue(controller.path.size == 0)

        swipeWord(controller, geometry, "hello")
        assertTrue(awaitUntil(5000) { capture.resultCount.get() == 2 })

        assertEquals(2, capture.recognizedCount.get())
        val all = capture.allResults
        assertEquals(2, all.size)
        assertEquals("hello", all[0].first())
        assertEquals("hello", all[1].first())
    }

    private class Capture {
        val resultCount = AtomicInteger(0)
        val recognizedCount = AtomicInteger(0)

        private val lock = Any()
        private val results = mutableListOf<List<String>>()

        fun onResult(words: List<String>) {
            resultCount.incrementAndGet()
            synchronized(lock) { results.add(words) }
        }

        fun onRecognized() {
            recognizedCount.incrementAndGet()
        }

        val allResults: List<List<String>>
            get() = synchronized(lock) { results.toList() }
    }

    private companion object {
        const val SUBSTEPS_PER_LEG = 8
    }
}
