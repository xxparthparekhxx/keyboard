package com.example.composekeyboard.input.swipe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import kotlin.math.max

/** Receives raw pointer activity for a candidate swipe. */
interface SwipeGestureHandler {
    fun onTouchDown(position: Offset, startKey: Int, timeMillis: Long)
    fun onTouchMove(position: Offset, timeMillis: Long)

    /** The finger has travelled far enough that this is definitely a gesture. */
    fun onSwipeRecognized()
    fun onTouchUp()
    fun onTouchCancel()
}

/**
 * Watches for glide gestures over the letter rows without getting in the way of
 * ordinary tap typing.
 *
 * This has to sit on the *container* rather than on an overlay: an overlay on
 * top would win hit-testing outright and no key would ever receive a tap. So it
 * listens on [PointerEventPass.Initial] — which reaches ancestors before
 * descendants — and stays passive, letting the key underneath light up and
 * behave normally. Only once the finger has clearly left its starting key does
 * it begin consuming events, which cancels the key's pending tap and hands the
 * gesture over.
 *
 * The upshot is that a tap is still a tap even though every tap starts out
 * looking exactly like the first moment of a swipe.
 */
// PointerInputChange.historical is still marked experimental, but it is the only
// way to see the motion samples the platform batches between frames — without it
// a fast gesture decodes from a handful of widely spaced points.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.swipeTypingGestures(
    enabled: Boolean,
    geometry: SwipeKeyGeometry,
    handler: SwipeGestureHandler
): Modifier {
    val currentHandler by rememberUpdatedState(handler)

    return this.pointerInput(enabled) {
        if (!enabled) return@pointerInput

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!geometry.isReady) return@awaitEachGesture

            // A gesture has to begin on a letter. Anything else — space, shift,
            // the symbol keys — keeps its existing behaviour untouched, which is
            // what leaves the spacebar cursor drag working.
            val startKey = geometry.letterAt(down.position)
            if (startKey < 0) return@awaitEachGesture

            val threshold = max(
                viewConfiguration.touchSlop * SwipeConstants.SWIPE_THRESHOLD_MULTIPLIER,
                geometry.keyWidth * SwipeConstants.SWIPE_MIN_KEY_WIDTH_FRACTION
            )
            val thresholdSquared = threshold * threshold

            currentHandler.onTouchDown(down.position, startKey, down.uptimeMillis)

            var recognized = false
            var completed = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.fastFirstOrNull { it.id == down.id } ?: break

                    // A second finger before recognition means fast two-thumb
                    // tapping, not a glide. Bow out and leave the keys to it.
                    if (!recognized && event.changes.size > 1) break

                    if (!change.pressed) {
                        if (recognized) {
                            change.consume()
                            currentHandler.onTouchUp()
                            completed = true
                        }
                        break
                    }

                    // Batched samples between frames; feeding them in keeps both
                    // the trail and the decoded shape faithful on fast gestures.
                    change.historical.fastForEach {
                        currentHandler.onTouchMove(it.position, it.uptimeMillis)
                    }
                    currentHandler.onTouchMove(change.position, change.uptimeMillis)

                    if (!recognized) {
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        if (dx * dx + dy * dy >= thresholdSquared) {
                            recognized = true
                            currentHandler.onSwipeRecognized()
                        }
                    }

                    if (recognized) change.consume()
                }
            } finally {
                if (!completed) currentHandler.onTouchCancel()
            }
        }
    }
}
