package io.github.xxparthparekhxx.composekeyboard.input.swipe

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Drives [swipeTypingGestures] with real pointer input: a tap must stay a tap
 * (the keys keep it), a glide must be recognized once and completed, and
 * anything that does not start on a letter must be left alone.
 */
class SwipeGestureDetectorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class Recorder : SwipeGestureHandler {
        val events = mutableListOf<String>()
        override fun onTouchDown(position: Offset, startKey: Int, timeMillis: Long) {
            events += "down:${'a' + startKey}"
        }
        override fun onTouchMove(position: Offset, timeMillis: Long) {
            if (events.lastOrNull() != "move") events += "move"
        }
        override fun onSwipeRecognized() { events += "recognized" }
        override fun onTouchUp() { events += "up" }
        override fun onTouchCancel() { events += "cancel" }
    }

    /** A QWERTY grid of [KEY]-pixel cells at the top-left of a larger box. */
    private fun qwerty(): SwipeKeyGeometry {
        val geometry = SwipeKeyGeometry()
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEachIndexed { row, letters ->
            letters.forEachIndexed { col, c ->
                geometry.place(c - 'a', col * KEY, row * KEY, KEY, KEY)
            }
        }
        geometry.setBodyOrigin(0f, 0f)
        return geometry
    }

    private fun setContent(enabled: Boolean = true): Recorder {
        val recorder = Recorder()
        val geometry = qwerty()
        composeRule.setContent {
            val side = with(LocalDensity.current) { (KEY * 10).toDp() }
            Box(
                Modifier
                    .requiredSize(side)
                    .testTag(TAG)
                    .swipeTypingGestures(enabled, geometry, recorder)
            )
        }
        return recorder
    }

    /** Centre of a letter's cell, in the box's pixels. */
    private fun keyCenter(c: Char): Offset {
        val (row, col) = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
            .withIndex()
            .first { c in it.value }
            .let { it.index to it.value.indexOf(c) }
        return Offset(col * KEY + KEY / 2, row * KEY + KEY / 2)
    }

    @Test
    fun tap_isNotRecognizedAndIsHandedBack() {
        val recorder = setContent()
        composeRule.onNodeWithTag(TAG).performTouchInput {
            down(keyCenter('g'))
            up()
        }
        composeRule.waitForIdle()
        assertEquals(listOf("down:g", "cancel"), recorder.events.filter { it != "move" })
    }

    @Test
    fun glide_isRecognizedOnceAndCompleted() {
        val recorder = setContent()
        composeRule.onNodeWithTag(TAG).performTouchInput {
            down(keyCenter('h'))
            moveTo(keyCenter('e'))
            moveTo(keyCenter('l'))
            moveTo(keyCenter('o'))
            up()
        }
        composeRule.waitForIdle()
        val events = recorder.events.filter { it != "move" }
        assertEquals(listOf("down:h", "recognized", "up"), events)
        assertTrue("moves must be reported", "move" in recorder.events)
    }

    @Test
    fun touchOffTheLetters_isIgnored() {
        val recorder = setContent()
        composeRule.onNodeWithTag(TAG).performTouchInput {
            // Below the bottom letter row.
            down(Offset(KEY * 5, KEY * 6))
            moveTo(Offset(KEY * 9, KEY * 6))
            up()
        }
        composeRule.waitForIdle()
        assertTrue(recorder.events.isEmpty())
    }

    @Test
    fun secondFingerBeforeRecognition_bowsOut() {
        val recorder = setContent()
        composeRule.onNodeWithTag(TAG).performTouchInput {
            down(0, keyCenter('a'))
            down(1, keyCenter('p'))
            moveTo(0, keyCenter('l'))
            up(1)
            up(0)
        }
        composeRule.waitForIdle()
        val events = recorder.events.filter { it != "move" }
        assertEquals(listOf("down:a", "cancel"), events)
    }

    @Test
    fun disabled_seesNothing() {
        val recorder = setContent(enabled = false)
        composeRule.onNodeWithTag(TAG).performTouchInput {
            down(keyCenter('h'))
            moveTo(keyCenter('o'))
            up()
        }
        composeRule.waitForIdle()
        assertTrue(recorder.events.isEmpty())
    }

    private companion object {
        const val TAG = "swipe-surface"
        const val KEY = 80f
    }
}
