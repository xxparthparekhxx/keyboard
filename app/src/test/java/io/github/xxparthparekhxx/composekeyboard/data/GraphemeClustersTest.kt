package io.github.xxparthparekhxx.composekeyboard.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GraphemeClustersTest {

    @Test
    fun emptyAndAscii() {
        assertEquals(0, GraphemeClusters.utf16LengthOfLastCluster(""))
        assertEquals(1, GraphemeClusters.utf16LengthOfLastCluster("a"))
        assertEquals(1, GraphemeClusters.utf16LengthOfLastCluster("hello"))
    }

    @Test
    fun surrogatePairEmoji_isOneCluster() {
        val grin = "😀"
        assertEquals(2, grin.length)
        assertEquals(2, GraphemeClusters.utf16LengthOfLastCluster(grin))
        assertEquals(2, GraphemeClusters.utf16LengthOfLastCluster("hi😀"))
    }

    @Test
    fun skinToneModifier_staysWithEmoji() {
        val wave = "👋🏻"
        assertEquals(4, wave.length)
        assertEquals(4, GraphemeClusters.utf16LengthOfLastCluster(wave))
    }

    @Test
    fun zwjSequence_deletesAsOneGlyph() {
        val technologist = "👩‍💻"
        assertEquals(technologist.length, GraphemeClusters.utf16LengthOfLastCluster(technologist))
        assertEquals(
            technologist.length,
            GraphemeClusters.utf16LengthOfLastCluster("ok$technologist")
        )

        val family = "👨‍👩‍👧‍👦"
        assertEquals(family.length, GraphemeClusters.utf16LengthOfLastCluster(family))
    }

    @Test
    fun flagEmoji_isOneCluster() {
        val us = "🇺🇸"
        assertEquals(4, us.length)
        assertEquals(4, GraphemeClusters.utf16LengthOfLastCluster(us))
        // Two flags: only the last pair is the cluster.
        assertEquals(4, GraphemeClusters.utf16LengthOfLastCluster("🇺🇸🇨🇦"))
    }

    @Test
    fun oddRegionalIndicator_isSingleton() {
        val oneRi = String(intArrayOf(0x1F1FA), 0, 1) // 🇺
        assertEquals(2, GraphemeClusters.utf16LengthOfLastCluster(oneRi))
        assertEquals(2, GraphemeClusters.utf16LengthOfLastCluster("🇺🇸$oneRi"))
    }

    @Test
    fun keycapAndCombiningMark() {
        val keycap = "1️⃣"
        assertEquals(keycap.length, GraphemeClusters.utf16LengthOfLastCluster(keycap))

        val combining = "e\u0301"
        assertEquals(2, GraphemeClusters.utf16LengthOfLastCluster(combining))
        assertEquals(2, GraphemeClusters.utf16LengthOfLastCluster("ae\u0301"))
    }

    @Test
    fun loneSurrogate_deletesTheJunk() {
        val broken = "\uD83D"
        assertEquals(1, GraphemeClusters.utf16LengthOfLastCluster(broken))
    }

    @Test
    fun crlf_isOneCluster() {
        assertEquals(2, GraphemeClusters.utf16LengthOfLastCluster("\r\n"))
        assertEquals(1, GraphemeClusters.utf16LengthOfLastCluster("\n"))
    }
}
