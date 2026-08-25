package com.example.composekeyboard.input.swipe.nn

import org.junit.Assert.*
import org.junit.Test

class SwipeBeamTest {

    @Test
    fun build_createsValidBeam() {
        val words = listOf("cat", "car", "cart", "care", "dog")
        val scores = intArrayOf(100, 90, 80, 70, 60)
        val beam = SwipeBeam.build(words, scores)
        assertNotNull(beam)
    }

    @Test
    fun search_findsTargetWordFromClearEmissions() {
        val words = listOf("cat", "dog", "car", "bat")
        val scores = intArrayOf(100, 100, 100, 100)
        val beam = SwipeBeam.build(words, scores)

        // Generate synthetic emissions [T_OUT=32, 27 channels]
        // Steps 0..9: strongly emit 'c' (index 2)
        // Steps 10..19: strongly emit 'a' (index 0)
        // Steps 20..29: strongly emit 't' (index 19)
        // Steps 30..31: blank (index 26)
        val emissions = FloatArray(SwipeNet.T_OUT * (SwipeNet.ALPHABET + 1)) { -10f }
        for (t in 0..9) {
            val base = t * (SwipeNet.ALPHABET + 1)
            emissions[base + 2] = 0f // 'c'
        }
        for (t in 10..19) {
            val base = t * (SwipeNet.ALPHABET + 1)
            emissions[base + 0] = 0f // 'a'
        }
        for (t in 20..29) {
            val base = t * (SwipeNet.ALPHABET + 1)
            emissions[base + 19] = 0f // 't'
        }
        for (t in 30..31) {
            val base = t * (SwipeNet.ALPHABET + 1)
            emissions[base + 26] = 0f // blank
        }

        val out = arrayOfNulls<String>(4)
        val outScore = FloatArray(4)
        val count = beam.search(emissions, beamWidth = 20, out = out, outScore = outScore)

        assertTrue(count > 0)
        assertEquals("cat", out[0])
    }

    @Test
    fun search_doubledLettersRequiresBlank() {
        val words = listOf("put", "putt")
        val scores = intArrayOf(100, 100)
        val beam = SwipeBeam.build(words, scores)

        // Case A: p -> u -> t without blank between consecutive t's -> favors "put"
        val emissionsNoBlank = FloatArray(SwipeNet.T_OUT * (SwipeNet.ALPHABET + 1)) { -15f }
        for (t in 0..9) emissionsNoBlank[t * 27 + ('p' - 'a')] = 0f
        for (t in 10..19) emissionsNoBlank[t * 27 + ('u' - 'a')] = 0f
        for (t in 20..31) emissionsNoBlank[t * 27 + ('t' - 'a')] = 0f

        val outA = arrayOfNulls<String>(2)
        val scoreA = FloatArray(2)
        beam.search(emissionsNoBlank, beamWidth = 20, out = outA, outScore = scoreA)
        assertEquals("put", outA[0])

        // Case B: p -> u -> t -> blank -> t -> favors "putt"
        val emissionsWithBlank = FloatArray(SwipeNet.T_OUT * (SwipeNet.ALPHABET + 1)) { -15f }
        for (t in 0..6) emissionsWithBlank[t * 27 + ('p' - 'a')] = 0f
        for (t in 7..13) emissionsWithBlank[t * 27 + ('u' - 'a')] = 0f
        for (t in 14..18) emissionsWithBlank[t * 27 + ('t' - 'a')] = 0f
        for (t in 19..22) emissionsWithBlank[t * 27 + 26] = 0f // blank between double letters
        for (t in 23..31) emissionsWithBlank[t * 27 + ('t' - 'a')] = 0f

        val outB = arrayOfNulls<String>(2)
        val scoreB = FloatArray(2)
        beam.search(emissionsWithBlank, beamWidth = 20, out = outB, outScore = scoreB)
        assertEquals("putt", outB[0])
    }
}
