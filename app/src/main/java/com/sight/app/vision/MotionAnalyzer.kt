package com.sight.app.vision

import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Derives velocity, direction, acceleration and short-term trajectory
 * predictions purely from observed track history. No fabricated motion:
 * with fewer than 2 observations velocity is reported as 0 with maximum
 * uncertainty.
 */
object MotionAnalyzer {

    fun analyze(track: Track, frameWidthPx: Int, frameHeightPx: Int, dtSeconds: Float = 1f / 30f): MotionEstimate {
        val hist = track.history
        if (hist.size < 2) {
            return MotionEstimate(
                trackId = track.id, velocityPxPerSec = 0f, speedPxPerSec = 0f,
                directionDegrees = 0f, directionLabel = "stationary",
                accelerationPxPerSec2 = 0f, predictedBox = track.box,
                predictionUncertainty = 1f, historyLength = hist.size
            )
        }
        val dt = dtSeconds.coerceAtLeast(1f / 240f)
        // Velocity from recent window (up to 5 frames) for stability
        val window = minOf(5, hist.size)
        val from = hist[hist.size - window]
        val to = hist.last()
        val spanFrames = (window - 1).coerceAtLeast(1)
        val spanSec = spanFrames * dt
        val vxNorm = (to.centerX - from.centerX) / spanSec
        val vyNorm = (to.centerY - from.centerY) / spanSec
        val vxPx = vxNorm * frameWidthPx
        val vyPx = vyNorm * frameHeightPx
        val speed = sqrt(vxPx * vxPx + vyPx * vyPx)
        val angle = Math.toDegrees(atan2(vyPx.toDouble(), vxPx.toDouble())).toFloat()
        val label = when {
            speed < 5f -> "stationary"
            angle in -22.5..22.5 -> "right"
            angle in 22.5..67.5 -> "down-right"
            angle in 67.5..112.5 -> "down"
            angle in 112.5..157.5 -> "down-left"
            angle in 157.5..180 || angle in -180..-157.5 -> "left"
            angle in -157.5..-112.5 -> "up-left"
            angle in -112.5..-67.5 -> "up"
            else -> "up-right"
        }
        // Acceleration: change in velocity between first and second half of window
        var accel = 0f
        if (hist.size >= 4) {
            val mid = hist.size / 2
            val v1x = (hist[mid].centerX - hist[0].centerX) / (mid * dt)
            val v1y = (hist[mid].centerY - hist[0].centerY) / (mid * dt)
            val v2x = (hist.last().centerX - hist[mid].centerX) / ((hist.size - mid) * dt)
            val v2y = (hist.last().centerY - hist[mid].centerY) / ((hist.size - mid) * dt)
            val halfSec = (hist.size / 2 * dt).coerceAtLeast(dt)
            accel = sqrt((v2x - v1x) * (v2x - v1x) + (v2y - v1y) * (v2y - v1y)) / halfSec *
                ((frameWidthPx + frameHeightPx) / 2f)
        }
        // Predict 0.5s ahead using observed velocity (normalized)
        val horizon = 0.5f
        val predCx = (to.centerX + vxNorm * horizon).coerceIn(0f, 1f)
        val predCy = (to.centerY + vyNorm * horizon).coerceIn(0f, 1f)
        val w = to.width; val h = to.height
        val predicted = BoundingBox(
            (predCx - w / 2).coerceIn(0f, 1f), (predCy - h / 2).coerceIn(0f, 1f),
            (predCx + w / 2).coerceIn(0f, 1f), (predCy + h / 2).coerceIn(0f, 1f)
        )
        // Uncertainty: high when history short, track coasting, or speed near zero with jitter
        var uncertainty = when {
            hist.size < 3 -> 0.9f
            hist.size < 6 -> 0.6f
            hist.size < 12 -> 0.35f
            else -> 0.2f
        }
        if (!track.isActive) uncertainty = (uncertainty + 0.3f).coerceAtMost(1f)
        if (track.missedFrames > 0) uncertainty = (uncertainty + track.missedFrames * 0.05f).coerceAtMost(1f)
        return MotionEstimate(
            trackId = track.id, velocityPxPerSec = speed, speedPxPerSec = speed,
            directionDegrees = angle, directionLabel = label,
            accelerationPxPerSec2 = accel, predictedBox = predicted,
            predictionUncertainty = uncertainty, historyLength = hist.size
        )
    }

    /** Direction label from a raw displacement — exposed for unit tests. */
    fun directionLabel(dx: Float, dy: Float): String {
        val speed = sqrt(dx * dx + dy * dy)
        if (speed < 1e-6f) return "stationary"
        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        return when {
            angle in -22.5..22.5 -> "right"
            angle in 22.5..67.5 -> "down-right"
            angle in 67.5..112.5 -> "down"
            angle in 112.5..157.5 -> "down-left"
            angle in 157.5..180 || angle in -180..-157.5 -> "left"
            angle in -157.5..-112.5 -> "up-left"
            angle in -112.5..-67.5 -> "up"
            else -> "up-right"
        }
    }
}
