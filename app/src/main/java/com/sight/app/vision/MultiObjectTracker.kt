package com.sight.app.vision

/**
 * Multi-object tracker: Kalman-filtered constant-velocity motion model +
 * greedy IoU association (mobile-friendly alternative to full Hungarian).
 * Handles temporary detection loss via [maxAgeFrames] and recovers tracks
 * when detections reappear near the predicted position.
 */
class MultiObjectTracker(
    private val maxAgeFrames: Int = 30,
    private val minIou: Float = 0.1f,
    private val processNoise: Float = 0.5f,
    private val measurementNoise: Float = 1.0f
) {
    private class TrackState(
        val id: Int,
        val label: String,
        var box: BoundingBox,
        var confidence: Float,
        val filter: KalmanFilter,
        var history: MutableList<BoundingBox> = mutableListOf(),
        var age: Int = 0,
        var missed: Int = 0,
        var velocityX: Float = 0f,
        var velocityY: Float = 0f
    )

    private val tracks = mutableListOf<TrackState>()
    private var nextId = 1

    fun reset() { tracks.clear(); nextId = 1 }

    fun update(detections: List<Detection>): List<Track> {
        // Predict all tracks
        for (t in tracks) {
            t.filter.predict()
            t.age++
        }
        // Greedy IoU association: best pairs first
        data class Pair(val trackIdx: Int, val detIdx: Int, val iou: Float)
        val pairs = mutableListOf<Pair>()
        for (ti in tracks.indices) {
            for (di in detections.indices) {
                val iou = tracks[ti].box.iou(detections[di].box)
                if (iou >= minIou) pairs.add(Pair(ti, di, iou))
            }
        }
        pairs.sortByDescending { it.iou }
        val matchedTracks = mutableSetOf<Int>()
        val matchedDets = mutableSetOf<Int>()
        for (pair in pairs) {
            if (pair.trackIdx in matchedTracks || pair.detIdx in matchedDets) continue
            matchedTracks.add(pair.trackIdx); matchedDets.add(pair.detIdx)
            val t = tracks[pair.trackIdx]
            val d = detections[pair.detIdx]
            val prevCx = t.box.centerX; val prevCy = t.box.centerY
            t.box = d.box; t.confidence = d.confidence
            t.filter.update(d.box.centerX, d.box.centerY)
            val (vx, vy) = t.filter.velocity()
            t.velocityX = vx; t.velocityY = vy
            // Blend instantaneous displacement as fallback velocity signal
            if (t.history.isNotEmpty()) {
                val instVx = d.box.centerX - prevCx
                val instVy = d.box.centerY - prevCy
                if (vx == 0f && vy == 0f) { t.velocityX = instVx; t.velocityY = instVy }
            }
            t.missed = 0
            t.history.add(d.box)
            if (t.history.size > 60) t.history.removeAt(0)
        }
        // Unmatched tracks: coast on prediction, mark missed
        for (ti in tracks.indices) {
            if (ti !in matchedTracks) {
                val t = tracks[ti]
                t.missed++
                val (px, py) = t.filter.position()
                val w = t.box.width; val h = t.box.height
                t.box = BoundingBox(
                    (px - w / 2).coerceIn(0f, 1f), (py - h / 2).coerceIn(0f, 1f),
                    (px + w / 2).coerceIn(0f, 1f), (py + h / 2).coerceIn(0f, 1f)
                )
                val (vx, vy) = t.filter.velocity()
                t.velocityX = vx; t.velocityY = vy
                t.history.add(t.box)
                if (t.history.size > 60) t.history.removeAt(0)
            }
        }
        // New tracks for unmatched detections
        for (di in detections.indices) {
            if (di !in matchedDets) {
                val d = detections[di]
                val f = KalmanFilter(processNoise, measurementNoise)
                f.reset(d.box.centerX, d.box.centerY)
                tracks.add(
                    TrackState(
                        id = nextId++, label = d.label, box = d.box,
                        confidence = d.confidence, filter = f,
                        history = mutableListOf(d.box), age = 1
                    )
                )
            }
        }
        // Retire stale tracks
        tracks.removeAll { it.missed > maxAgeFrames }
        return tracks.map {
            Track(
                id = it.id, label = it.label, box = it.box, confidence = it.confidence,
                history = it.history.toList(), ageFrames = it.age, missedFrames = it.missed,
                velocityX = it.velocityX, velocityY = it.velocityY, isActive = it.missed == 0
            )
        }
    }

    fun activeTracks(): List<Track> = tracks.map {
        Track(it.id, it.label, it.box, it.confidence, it.history.toList(), it.age, it.missed, it.velocityX, it.velocityY, it.missed == 0)
    }
}
