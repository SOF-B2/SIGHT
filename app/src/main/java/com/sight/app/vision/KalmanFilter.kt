package com.sight.app.vision

import kotlin.math.sqrt

/**
 * Constant-velocity Kalman filter over a 2D center point (x, y).
 * State: [x, y, vx, vy]. Transition F = [[1,0,dt,0],[0,1,0,dt],[0,0,1,0],[0,0,0,1]].
 * Prediction: x = F x, P = F P F^T + Q. Update uses H selecting position.
 * Pure Kotlin, unit-testable, no Android deps.
 */
class KalmanFilter(
    private val processNoise: Float = 0.5f,
    private val measurementNoise: Float = 1.0f
) {
    private var x = FloatArray(4)
    private var p = Array(4) { FloatArray(4) }
    var initialized = false
        private set
    var lastInnovation: Float = 0f
        private set

    fun reset(cx: Float, cy: Float) {
        x = floatArrayOf(cx, cy, 0f, 0f)
        p = Array(4) { FloatArray(4) }
        p[0][0] = 1f; p[1][1] = 1f; p[2][2] = 10f; p[3][3] = 10f
        initialized = true
    }

    fun predict(dt: Float = 1f) {
        if (!initialized) return
        // x = F x
        x[0] += x[2] * dt
        x[1] += x[3] * dt
        // P = F P F^T + Q
        val f = arrayOf(
            floatArrayOf(1f, 0f, dt, 0f),
            floatArrayOf(0f, 1f, 0f, dt),
            floatArrayOf(0f, 0f, 1f, 0f),
            floatArrayOf(0f, 0f, 0f, 1f)
        )
        val fp = multiply(f, p)
        val ft = transpose(f)
        val fpfT = multiply(fp, ft)
        // Add process noise Q (diagonal)
        fpfT[0][0] += processNoise * dt * dt
        fpfT[1][1] += processNoise * dt * dt
        fpfT[2][2] += processNoise * dt
        fpfT[3][3] += processNoise * dt
        p = fpfT
    }

    fun update(mx: Float, my: Float): Float {
        if (!initialized) { reset(mx, my); return 0f }
        val y0 = mx - x[0]; val y1 = my - x[1]
        lastInnovation = sqrt(y0 * y0 + y1 * y1)
        // S = H P H^T + R (H selects x,y)
        val s00 = p[0][0] + measurementNoise
        val s01 = p[0][1]
        val s10 = p[1][0]
        val s11 = p[1][1] + measurementNoise
        val det = s00 * s11 - s01 * s10
        if (det == 0f) return lastInnovation
        val inv00 = s11 / det; val inv01 = -s01 / det
        val inv10 = -s10 / det; val inv11 = s00 / det
        // K = P H^T S^-1 (4x2)
        val k = Array(4) { FloatArray(2) }
        for (i in 0 until 4) {
            k[i][0] = p[i][0] * inv00 + p[i][1] * inv10
            k[i][1] = p[i][0] * inv01 + p[i][1] * inv11
        }
        for (i in 0 until 4) x[i] += k[i][0] * y0 + k[i][1] * y1
        // P = (I - K H) P
        val newP = Array(4) { FloatArray(4) }
        for (i in 0 until 4) for (j in 0 until 4) {
            newP[i][j] = p[i][j] - k[i][0] * p[0][j] - k[i][1] * p[1][j]
        }
        p = newP
        return lastInnovation
    }

    fun position(): Pair<Float, Float> = x[0] to x[1]
    fun velocity(): Pair<Float, Float> = x[2] to x[3]
    fun uncertainty(): Float = (p[0][0] + p[1][1]).coerceAtLeast(0f)

    private fun multiply(a: Array<FloatArray>, b: Array<FloatArray>): Array<FloatArray> {
        val n = a.size; val m = b[0].size; val k = b.size
        val out = Array(n) { FloatArray(m) }
        for (i in 0 until n) for (j in 0 until m) {
            var sum = 0f
            for (t in 0 until k) sum += a[i][t] * b[t][j]
            out[i][j] = sum
        }
        return out
    }

    private fun transpose(a: Array<FloatArray>): Array<FloatArray> {
        val n = a.size; val m = a[0].size
        val out = Array(m) { FloatArray(n) }
        for (i in 0 until n) for (j in 0 until m) out[j][i] = a[i][j]
        return out
    }
}
