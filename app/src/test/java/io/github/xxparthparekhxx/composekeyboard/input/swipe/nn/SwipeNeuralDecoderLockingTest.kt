package io.github.xxparthparekhxx.composekeyboard.input.swipe.nn

import io.github.xxparthparekhxx.composekeyboard.data.SwipeDictionaryTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * [SwipeNeuralDecoder.bumpScore] runs on the main thread and must never queue
 * behind a gesture decode (tens of ms) or a trie rebuild (much longer).
 */
@RunWith(RobolectricTestRunner::class)
class SwipeNeuralDecoderLockingTest {

    private fun loadNet(): SwipeNet {
        val file = listOf(
            File("src/main/assets/swipe_encoder.bin"),
            File("app/src/main/assets/swipe_encoder.bin"),
            File("../app/src/main/assets/swipe_encoder.bin")
        ).firstOrNull { it.exists() }
        assertNotNull("swipe_encoder.bin not found", file)
        return FileInputStream(file!!).use { SwipeNet.load(it) }
    }

    @Test
    fun bumpAndRebuild_doNotWaitForDecodeLock() {
        val dict = SwipeDictionaryTest.createTestDictionary()
        repeat(3) { dict.learn("hello") }
        val (words, scores) = dict.allWords()
        val decoder = SwipeNeuralDecoder(loadNet(), SwipeBeam.build(words, scores), dict.lexiconVersion)

        // A long decode holds the decoder's monitor (decode is @Synchronized).
        val holding = CountDownLatch(1)
        val releaseDecode = CountDownLatch(1)
        val decodeThread = Thread {
            synchronized(decoder) {
                holding.countDown()
                releaseDecode.await()
            }
        }.apply { start() }
        holding.await()

        val pool = Executors.newSingleThreadExecutor()
        try {
            val bumped = pool.submit<Boolean> { decoder.bumpScore("hello", 200) }
            assertTrue(
                "bumpScore must not block on a decode in flight",
                bumped.get(2, TimeUnit.SECONDS)
            )

            // A brand-new word becomes bumpable only after the rebuild, which
            // must also complete while the decode is still running.
            repeat(4) { dict.learn("quizzify") }
            assertFalse(decoder.bumpScore("quizzify", 120))
            pool.submit { decoder.updateBeam(dict) }.get(10, TimeUnit.SECONDS)
            assertTrue(decoder.bumpScore("quizzify", 120))
        } finally {
            releaseDecode.countDown()
            decodeThread.join()
            pool.shutdownNow()
        }
    }
}
