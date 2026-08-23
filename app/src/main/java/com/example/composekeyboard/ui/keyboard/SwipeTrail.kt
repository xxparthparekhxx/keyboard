package com.example.composekeyboard.ui.keyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.composekeyboard.input.swipe.SwipeConstants
import com.example.composekeyboard.input.swipe.SwipeTrace
import kotlin.math.pow

/**
 * Pre-allocated point buffers to avoid object allocations in hot draw loop.
 * UI operations run strictly on the Main thread.
 */
private const val MAX_SMOOTH_POINTS = 512
private val smoothX = FloatArray(MAX_SMOOTH_POINTS)
private val smoothY = FloatArray(MAX_SMOOTH_POINTS)

/**
 * Paints an authentic Gboard-style glide trail that smoothly follows the finger.
 *
 * Design:
 * - Fluid, tapered ribbon matching Gboard's Material glide tracer.
 * - Continuous quadratic Bézier spline smoothing eliminating touch polygon kinks.
 * - Soft edge anti-aliased bloom layer for silky smooth rendering.
 * - Smooth exponential alpha and width dissipation along the trail.
 * - Clean rounded fingertip cap.
 * - Zero allocations during frame drawing for smooth 120 FPS performance.
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
        now - path.timeAt(start - 1) <= SwipeConstants.TRAIL_DURATION_MS &&
        count - start < SwipeConstants.MAX_TRAIL_SEGMENTS
    ) {
        start--
    }

    val activeCount = count - start
    if (activeCount < 2) return

    // --- 1. Compute smoothed spline points ---
    var smoothCount = 0

    if (activeCount == 2) {
        smoothX[0] = path.x(start)
        smoothY[0] = path.y(start)
        smoothX[1] = path.x(start + 1)
        smoothY[1] = path.y(start + 1)
        smoothCount = 2
    } else {
        smoothX[smoothCount] = path.x(start)
        smoothY[smoothCount] = path.y(start)
        smoothCount++

        val firstMidX = (path.x(start) + path.x(start + 1)) * 0.5f
        val firstMidY = (path.y(start) + path.y(start + 1)) * 0.5f
        smoothX[smoothCount] = firstMidX
        smoothY[smoothCount] = firstMidY
        smoothCount++

        for (k in 1 until activeCount - 1) {
            val idx = start + k
            val p0X = (path.x(idx - 1) + path.x(idx)) * 0.5f
            val p0Y = (path.y(idx - 1) + path.y(idx)) * 0.5f

            val p1X = path.x(idx)
            val p1Y = path.y(idx)

            val p2X = (path.x(idx) + path.x(idx + 1)) * 0.5f
            val p2Y = (path.y(idx) + path.y(idx + 1)) * 0.5f

            // Intermediate Bézier step at u = 0.5 for fluid curvature
            if (smoothCount < MAX_SMOOTH_POINTS - 2) {
                val subX = 0.25f * p0X + 0.5f * p1X + 0.25f * p2X
                val subY = 0.25f * p0Y + 0.5f * p1Y + 0.25f * p2Y
                smoothX[smoothCount] = subX
                smoothY[smoothCount] = subY
                smoothCount++
            }

            if (smoothCount < MAX_SMOOTH_POINTS - 1) {
                smoothX[smoothCount] = p2X
                smoothY[smoothCount] = p2Y
                smoothCount++
            }
        }

        // Final point (fingertip head)
        if (smoothCount < MAX_SMOOTH_POINTS) {
            smoothX[smoothCount] = path.x(count - 1)
            smoothY[smoothCount] = path.y(count - 1)
            smoothCount++
        }
    }

    if (smoothCount < 2) return

    val totalSegments = smoothCount - 1
    val baseAlpha = if (color.alpha <= 0.05f) 0.95f else color.alpha

    // --- 2. Draw Gboard Ribbon Segments ---
    for (i in 0 until totalSegments) {
        val t = (i + 1).toFloat() / totalSegments
        val easedWidth = t.pow(1.5f)
        val easedAlpha = t.pow(1.3f)

        val startOffset = Offset(smoothX[i], smoothY[i])
        val endOffset = Offset(smoothX[i + 1], smoothY[i + 1])

        // Layer 1: Soft edge bloom / anti-aliasing aura
        drawLine(
            color = color.copy(alpha = baseAlpha * 0.22f * easedAlpha),
            start = startOffset,
            end = endOffset,
            strokeWidth = headWidth * (0.22f + 1.25f * easedWidth),
            cap = StrokeCap.Round
        )

        // Layer 2: Primary vibrant Gboard fluid ribbon
        drawLine(
            color = color.copy(alpha = baseAlpha * (0.08f + 0.92f * easedAlpha)),
            start = startOffset,
            end = endOffset,
            strokeWidth = headWidth * (0.12f + 0.88f * easedWidth),
            cap = StrokeCap.Round
        )
    }

    // --- 3. Clean Fingertip Rounded Cap ---
    val headOffset = Offset(path.x(count - 1), path.y(count - 1))

    // Subtle outer halo
    drawCircle(
        color = color.copy(alpha = baseAlpha * 0.25f),
        radius = headWidth * 0.65f,
        center = headOffset
    )

    // Solid fingertip cap matching the ribbon head
    drawCircle(
        color = color.copy(alpha = baseAlpha * 0.95f),
        radius = headWidth * 0.48f,
        center = headOffset
    )
}
