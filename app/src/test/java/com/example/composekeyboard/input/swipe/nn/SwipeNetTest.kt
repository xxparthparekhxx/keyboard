package com.example.composekeyboard.input.swipe.nn

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class SwipeNetTest {

    @Test
    fun basisFor_computesExpectedDimensionsAndBounds() {
        val keyX = FloatArray(26) { it / 25f }
        val keyY = FloatArray(26) { (it % 3) / 2f }

        val basis = SwipeNet.basisFor(keyX, keyY)

        assertEquals(26 * SwipeNet.N_COEFF, basis.size)
        for (v in basis) {
            assertTrue("Basis value out of cosine range: $v", v in -1.0001f..1.0001f)
            assertFalse(v.isNaN())
        }
    }

    @Test
    fun load_and_forward_runsCleanlyOnModelAsset() {
        val assetCandidates = listOf(
            File("src/main/assets/swipe_encoder.bin"),
            File("app/src/main/assets/swipe_encoder.bin"),
            File("../app/src/main/assets/swipe_encoder.bin")
        )
        val assetFile = assetCandidates.firstOrNull { it.exists() }
        assertNotNull("swipe_encoder.bin not found in test search paths", assetFile)

        val net = FileInputStream(assetFile!!).use { SwipeNet.load(it) }
        assertNotNull(net)

        // Test forward pass with normalized coordinates (e.g. straight line diagonal)
        val xy = FloatArray(SwipeNet.T_IN * 2) { i ->
            val t = (i / 2).toFloat() / (SwipeNet.T_IN - 1)
            t // x in 0..1, y in 0..1
        }
        val keyX = FloatArray(26) { 0.5f }
        val keyY = FloatArray(26) { 0.5f }
        val basis = SwipeNet.basisFor(keyX, keyY)

        net.forward(xy, basis)

        assertEquals(SwipeNet.T_OUT * (SwipeNet.ALPHABET + 1), net.emissions.size)
        for (e in net.emissions) {
            assertFalse("Emissions contained NaN", e.isNaN())
            assertFalse("Emissions contained positive infinity", e == Float.POSITIVE_INFINITY)
            assertTrue("Emissions should be log-probabilities (<= 0)", e <= 0.001f)
        }
    }
}
