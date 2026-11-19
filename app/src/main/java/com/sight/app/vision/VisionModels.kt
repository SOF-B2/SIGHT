package com.sight.app.vision

/** Bounding box in normalized 0..1 coordinates (x1,y1 top-left, x2,y2 bottom-right). */
data class BoundingBox(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {
    val width: Float get() = (x2 - x1).coerceAtLeast(0f)
    val height: Float get() = (y2 - y1).coerceAtLeast(0f)
    val centerX: Float get() = (x1 + x2) / 2f
    val centerY: Float get() = (y1 + y2) / 2f
    val area: Float get() = width * height

    fun iou(other: BoundingBox): Float {
        val ix1 = maxOf(x1, other.x1); val iy1 = maxOf(y1, other.y1)
        val ix2 = minOf(x2, other.x2); val iy2 = minOf(y2, other.y2)
        val iw = (ix2 - ix1).coerceAtLeast(0f); val ih = (iy2 - iy1).coerceAtLeast(0f)
        val inter = iw * ih
        val union = area + other.area - inter
        return if (union <= 0f) 0f else inter / union
    }
}

data class Detection(
    val label: String,
    val classId: Int,
    val confidence: Float,
    val box: BoundingBox
)

data class OcrBlock(
    val text: String,
    val confidence: Float?,
    val box: BoundingBox,
    val category: OcrCategory
)

enum class OcrCategory { NUMBER, HEALTH, AMMO, WARNING, LABEL, STATUS, OTHER }

data class FrameResult(
    val frameIndex: Int,
    val timestampMs: Long,
    val detections: List<Detection> = emptyList(),
    val ocr: List<OcrBlock> = emptyList(),
    val inferenceMs: Long = 0,
    val ocrMs: Long = 0
)

data class Track(
    val id: Int,
    val label: String,
    val box: BoundingBox,
    val confidence: Float,
    val history: List<BoundingBox> = emptyList(),
    val ageFrames: Int = 0,
    val missedFrames: Int = 0,
    val velocityX: Float = 0f,
    val velocityY: Float = 0f,
    val isActive: Boolean = true
)

data class MotionEstimate(
    val trackId: Int,
    val velocityPxPerSec: Float,
    val speedPxPerSec: Float,
    val directionDegrees: Float, // 0 = right, 90 = down (image coords), -180..180
    val directionLabel: String,
    val accelerationPxPerSec2: Float,
    val predictedBox: BoundingBox,
    val predictionUncertainty: Float, // 0..1, higher = less reliable
    val historyLength: Int
)

data class CameraMotion(
    val dxPx: Float, // global frame-to-frame shift (downscaled frame coords)
    val dyPx: Float,
    val magnitudePx: Float,
    val isMoving: Boolean,
    val method: String = "template-match"
)

data class SceneSummary(
    val headline: String,
    val objectCounts: Map<String, Int>,
    val movingCount: Int,
    val movingDirections: List<String>,
    val ocrTexts: List<String>,
    val statusIndicators: List<String>,
    val events: List<String>,
    val uncertainty: Float,
    val cameraMotion: CameraMotion? = null
)
