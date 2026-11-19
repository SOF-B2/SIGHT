package com.sight.app.vision

/**
 * Rule-based scene summarizer. Every statement is derived from actual
 * detection / tracking / OCR / camera-motion data. When data is sparse the
 * summary says so and uncertainty is high — it never invents scene content.
 */
object SceneSummarizer {

    fun summarize(
        detections: List<Detection>,
        tracks: List<Track>,
        ocr: List<OcrBlock>,
        cameraMotion: CameraMotion? = null
    ): SceneSummary {
        val counts = detections.groupBy { it.label }.mapValues { it.value.size }
        val moving = tracks.filter { it.isActive && it.history.size >= 3 }
        val directions = moving.mapNotNull { t ->
            val first = t.history.firstOrNull() ?: return@mapNotNull null
            val last = t.history.lastOrNull() ?: return@mapNotNull null
            val dx = last.centerX - first.centerX
            val dy = last.centerY - first.centerY
            val dist = kotlin.math.sqrt(dx * dx + dy * dy)
            if (dist < 0.01f) null else "${t.label} #${t.id} moving ${MotionAnalyzer.directionLabel(dx, dy)}"
        }
        val events = mutableListOf<String>()
        if (detections.isNotEmpty()) events.add("${detections.size} object(s) detected in current frame")
        if (moving.isNotEmpty()) events.add("${moving.size} tracked object(s) in motion")
        val coasting = tracks.count { !it.isActive }
        if (coasting > 0) events.add("$coasting track(s) temporarily lost (coasting on prediction)")
        if (cameraMotion?.isMoving == true) events.add("Camera moving (global shift ${cameraMotion.magnitudePx.toInt()}px/frame)")
        val warnings = ocr.filter { it.category == OcrCategory.WARNING }
        if (warnings.isNotEmpty()) events.add("Warning text visible: ${warnings.joinToString { "\"${it.text}\"" }}")
        val status = ocr.filter { it.category == OcrCategory.STATUS || it.category == OcrCategory.HEALTH || it.category == OcrCategory.AMMO }
            .map { it.text }
        val headline = when {
            detections.isEmpty() && ocr.isEmpty() -> "No objects or text detected in this frame."
            detections.isEmpty() -> "Text detected, but no objects passed the confidence threshold."
            moving.isEmpty() -> "${detections.size} object(s) detected; none confirmed moving yet."
            else -> "${detections.size} object(s) detected, ${moving.size} moving."
        }
        val uncertainty = when {
            detections.isEmpty() && ocr.isEmpty() -> 0.95f
            detections.isEmpty() || tracks.isEmpty() -> 0.6f
            moving.isEmpty() -> 0.4f
            else -> 0.25f
        }
        return SceneSummary(
            headline = headline,
            objectCounts = counts,
            movingCount = moving.size,
            movingDirections = directions,
            ocrTexts = ocr.map { it.text },
            statusIndicators = status,
            events = events,
            uncertainty = uncertainty,
            cameraMotion = cameraMotion
        )
    }
}
