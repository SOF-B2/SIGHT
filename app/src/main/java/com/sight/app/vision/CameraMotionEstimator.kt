package com.sight.app.vision

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Global camera-motion estimation via downscaled grayscale template matching
 * (block-matching optical-flow style). Compares a center patch of the previous
 * frame against a search window in the current frame to find the dominant
 * frame-to-frame displacement. This distinguishes global camera movement from
 * individual object movement. Pure Kotlin on Bitmap pixels — no native deps.
 */
object CameraMotionEstimator {

    private const val SMALL = 160 // downscaled frame dimension

    fun estimate(previous: Bitmap, current: Bitmap): CameraMotion {
        if (previous.width == 0 || current.width == 0) {
            return CameraMotion(0f, 0f, 0f, false)
        }
        val prev = toGray(previous)
        val curr = toGray(current)
        val patch = 48
        val search = 16
        val cx = SMALL / 2; val cy = SMALL / 2
        var bestDx = 0; var bestDy = 0; var bestScore = Float.MAX_VALUE
        var baseScore = 0f; var count = 0
        for (py in cy - patch / 2 until cy + patch / 2) {
            for (px in cx - patch / 2 until cx + patch / 2) {
                baseScore += abs(prev[py * SMALL + px] - curr[py * SMALL + px])
                count++
            }
        }
        baseScore /= count.coerceAtLeast(1)
        for (dy in -search..search) {
            for (dx in -search..search) {
                var score = 0f
                for (py in cy - patch / 2 until cy + patch / 2) {
                    for (px in cx - patch / 2 until cx + patch / 2) {
                        val sx = px + dx; val sy = py + dy
                        if (sx < 0 || sy < 0 || sx >= SMALL || sy >= SMALL) continue
                        score += abs(prev[py * SMALL + px] - curr[sy * SMALL + sx])
                    }
                }
                if (score < bestScore) { bestScore = score; bestDx = dx; bestDy = dy }
            }
        }
        val magnitude = sqrt((bestDx * bestDx + bestDy * bestDy).toFloat())
        // Only report movement if the shifted match is meaningfully better than zero-shift.
        val improved = bestScore < baseScore * 0.98f
        val moving = improved && magnitude >= 1.5f
        return CameraMotion(
            dxPx = if (moving) bestDx.toFloat() else 0f,
            dyPx = if (moving) bestDy.toFloat() else 0f,
            magnitudePx = if (moving) magnitude else 0f,
            isMoving = moving
        )
    }

    private fun toGray(bitmap: Bitmap): FloatArray {
        val scaled = if (bitmap.width != SMALL || bitmap.height != SMALL)
            Bitmap.createScaledBitmap(bitmap, SMALL, SMALL, true) else bitmap
        val pixels = IntArray(SMALL * SMALL)
        scaled.getPixels(pixels, 0, SMALL, 0, 0, SMALL, SMALL)
        val out = FloatArray(SMALL * SMALL)
        for (i in pixels.indices) {
            val p = pixels[i]
            out[i] = (0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF))
        }
        return out
    }
}
