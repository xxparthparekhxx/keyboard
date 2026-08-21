package com.example.composekeyboard.ui.keyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.composekeyboard.input.swipe.SwipeTrace

/**
 * Paints the comet trail that follows the finger.
 *
 * Only the tail end of the path is drawn — the last [TRAIL_DURATION_MS] of it —
 * so a long word leaves a ribbon behind the finger instead of gradually filling
 * the keyboard with a scribble. Width and opacity ramp towards the head, which
 * reads as motion and keeps the letters under the older part legible.
 *
 * Called from the draw phase only; it reads the live trace directly rather than
 * copying it, because this runs on every motion event.
 */
fun DrawScope.drawSwipeTrail(
    path: SwipeTrace,
    color: Color,
    headWidth: Float
) {
    val count = path.size
    if (count < 2) return

    val now = path.timeAt(count - 1)
    var start = count - 1
    while (start > 0 &&
        now - path.timeAt(start - 1) <= TRAIL_DURATION_MS &&
        count - start < MAX_TRAIL_SEGMENTS
    ) {
        start--
    }

    val span = count - 1 - start
    if (span < 1) return

    for (i in start until count - 1) {
        // 0 at the oldest surviving point, 1 at the fingertip.
        val t = (i - start + 1).toFloat() / span
        val eased = t * t
        drawLine(
            color = color.copy(alpha = color.alpha * (0.10f + 0.90f * eased)),
            start = Offset(path.x(i), path.y(i)),
            end = Offset(path.x(i + 1), path.y(i + 1)),
            strokeWidth = headWidth * (0.22f + 0.78f * eased),
            cap = StrokeCap.Round
        )
    }
}

/** How much of the recent path stays visible behind the finger. */
private const val TRAIL_DURATION_MS = 320L

/** Hard cap so a very fast finger cannot blow up the per-frame segment count. */
private const val MAX_TRAIL_SEGMENTS = 96
