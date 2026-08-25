package com.example.composekeyboard.input.swipe.nn

import com.example.composekeyboard.input.swipe.SwipeKeyMap
import com.example.composekeyboard.input.swipe.SwipeTrace
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class SwipeNeuralDecoderTest {

    @Test
    fun decode_shortTrace_returnsEmpty() {
        val assetCandidates = listOf(
            File("src/main/assets/swipe_encoder.bin"),
            File("app/src/main/assets/swipe_encoder.bin"),
            File("../app/src/main/assets/swipe_encoder.bin")
        )
        val assetFile = assetCandidates.firstOrNull { it.exists() }
        assertNotNull("swipe_encoder.bin not found", assetFile)

        val net = FileInputStream(assetFile!!).use { SwipeNet.load(it) }
        val beam = SwipeBeam.build(listOf("hello", "world"), intArrayOf(100, 90))

        // Create decoder via reflection since constructor is private
        val constructor = SwipeNeuralDecoder::class.java.getDeclaredConstructor(
            SwipeNet::class.java, SwipeBeam::class.java, Int::class.javaPrimitiveType
        )
        constructor.isAccessible = true
        val decoder = constructor.newInstance(net, beam, 0)

        val trace = SwipeTrace()
        trace.add(100f, 100f, 0L) // Only 1 point
        val keyMap = SwipeKeyMap(
            centerX = FloatArray(26) { 100f },
            centerY = FloatArray(26) { 100f },
            placed = BooleanArray(26) { true },
            keyWidth = 50f,
            keyHeight = 50f
        )

        val result = decoder.decode(trace, keyMap)
        assertTrue(result.isEmpty())
    }

    @Test
    fun updateBeam_exercisesUpdateBeamAndRespectsLexiconVersionGate() {
        val assetCandidates = listOf(
            File("src/main/assets/swipe_encoder.bin"),
            File("app/src/main/assets/swipe_encoder.bin"),
            File("../app/src/main/assets/swipe_encoder.bin")
        )
        val assetFile = assetCandidates.firstOrNull { it.exists() }
        assertNotNull("swipe_encoder.bin not found", assetFile)

        val net = FileInputStream(assetFile!!).use { SwipeNet.load(it) }
        val dict = com.example.composekeyboard.data.SwipeDictionaryTest.createTestDictionary()
        repeat(3) { dict.learn("initial") }

        val beam = SwipeBeam.build(listOf("initial"), intArrayOf(100))

        val constructor = SwipeNeuralDecoder::class.java.getDeclaredConstructor(
            SwipeNet::class.java, SwipeBeam::class.java, Int::class.javaPrimitiveType
        )
        constructor.isAccessible = true
        // Initialize decoder with beamVersion = 0
        val decoder = constructor.newInstance(net, beam, 0)

        val beamField = SwipeNeuralDecoder::class.java.getDeclaredField("beam").apply { isAccessible = true }
        val initialBeam = beamField.get(decoder)

        // Calling updateBeam when dict.lexiconVersion == 1 rebuilds the beam
        decoder.updateBeam(dict)
        val rebuiltBeam = beamField.get(decoder)
        assertNotSame(initialBeam, rebuiltBeam)

        // Calling updateBeam again without new words is a no-op (beam stays same reference)
        decoder.updateBeam(dict)
        val noOpBeam = beamField.get(decoder)
        assertSame(rebuiltBeam, noOpBeam)

        // Learning a new word past threshold bumps lexiconVersion -> updateBeam rebuilds
        repeat(3) { dict.learn("novelword") }
        decoder.updateBeam(dict)
        val updatedBeam = beamField.get(decoder)
        assertNotSame(rebuiltBeam, updatedBeam)
    }
}
