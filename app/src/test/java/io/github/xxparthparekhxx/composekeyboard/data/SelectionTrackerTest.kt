package io.github.xxparthparekhxx.composekeyboard.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionTrackerTest {

    @Test
    fun exactEchoOfOwnCommitIsOurs() {
        val t = SelectionTracker().apply { reset(5, 5) }
        t.onCommitText(3)
        assertTrue(t.onUpdate(5, 5, 8, 8))
    }

    @Test
    fun tapElsewhereIsExternal() {
        val t = SelectionTracker().apply { reset(5, 5) }
        assertFalse(t.onUpdate(5, 5, 1, 1))
        // The new position becomes the baseline.
        t.onCommitText(1)
        assertTrue(t.onUpdate(1, 1, 2, 2))
    }

    @Test
    fun belatedUpdatesDuringABurstAreOurs() {
        val t = SelectionTracker().apply { reset(0, 0) }
        t.onCommitText(1)
        t.onCommitText(1)
        t.onCommitText(1)
        // The editor reports each step late, one at a time.
        assertTrue(t.onUpdate(0, 0, 1, 1))
        assertTrue(t.onUpdate(1, 1, 2, 2))
        assertTrue(t.onUpdate(2, 2, 3, 3))
    }

    @Test
    fun missingEchoesDoNotSwallowALaterRealMove() {
        // Termux-style editor: dozens of edits, no updates at all.
        val t = SelectionTracker().apply { reset(0, 0) }
        repeat(50) { t.onCommitText(1) }
        // The first update that does arrive is a tap back to the start.
        assertFalse(t.onUpdate(50, 50, 10, 10))
    }

    @Test
    fun deleteShiftsTheCaretLeft() {
        val t = SelectionTracker().apply { reset(10, 10) }
        t.onDeleteBefore(4)
        assertEquals(6, t.expectedStart)
        assertTrue(t.onUpdate(10, 10, 6, 6))
        t.onDeleteBefore(100)
        assertEquals(0, t.expectedStart)
    }

    @Test
    fun commitReplacesSelection() {
        val t = SelectionTracker().apply { reset(8, 3) }
        t.onCommitText(2)
        assertTrue(t.onUpdate(3, 8, 5, 5))
    }

    @Test
    fun unknownAdoptsTheNextUpdate() {
        val t = SelectionTracker().apply { reset(-1, -1) }
        assertTrue(t.onUpdate(-1, -1, 4, 4))
        assertEquals(4, t.expectedStart)
        t.invalidate()
        assertTrue(t.onUpdate(4, 4, 5, 5))
        t.onCommitText(0)
        assertFalse(t.onUpdate(5, 5, 0, 0))
    }
}
