package com.sight.app

import com.sight.app.vision.BoundingBox
import com.sight.app.vision.Detection
import com.sight.app.vision.KalmanFilter
import com.sight.app.vision.MotionAnalyzer
import com.sight.app.vision.MultiObjectTracker
import com.sight.app.vision.OcrEngine
import com.sight.app.vision.SceneSummarizer
import com.sight.app.vision.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class VisionUnitTests {

    @Test fun kalman_convergesToConstantVelocity() {
        val f = KalmanFilter()
        f.reset(0f, 0f)
        for (i in 1..20) { f.predict(); f.update(i.toFloat(), 0f) }
        val (x, _) = f.position()
        assertTrue("x=$x should be near 20", abs(x - 20f) < 2f)
        val (vx, _) = f.velocity()
        assertTrue("vx=$vx should be near 1", abs(vx - 1f) < 0.5f)
    }

    @Test fun kalman_emptyInputDoesNotCrash() {
        val f = KalmanFilter()
        f.predict(); f.update(5f, 5f)
        assertTrue(f.initialized)
    }

    @Test fun tracker_assignsStableIds() {
        val tracker = MultiObjectTracker()
        val box = BoundingBox(0.1f, 0.1f, 0.3f, 0.3f)
        val d = Detection("car", 2, 0.9f, box)
        val t1 = tracker.update(listOf(d))
        val t2 = tracker.update(listOf(d.copy(box = BoundingBox(0.11f, 0.1f, 0.31f, 0.3f))))
        assertEquals(1, t1.size)
        assertEquals(t1[0].id, t2[0].id)
    }

    @Test fun tracker_recoversAfterTemporaryLoss() {
        val tracker = MultiObjectTracker(maxAgeFrames = 30)
        val box = BoundingBox(0.1f, 0.1f, 0.3f, 0.3f)
        val d = Detection("car", 2, 0.9f, box)
        val first = tracker.update(listOf(d))
        // Lose detection for 5 frames
        repeat(5) { tracker.update(emptyList()) }
        val recovered = tracker.update(listOf(d.copy(box = BoundingBox(0.12f, 0.1f, 0.32f, 0.3f))))
        assertTrue(recovered.any { it.id == first[0].id })
    }

    @Test fun tracker_retiresStaleTracks() {
        val tracker = MultiObjectTracker(maxAgeFrames = 3)
        tracker.update(listOf(Detection("car", 2, 0.9f, BoundingBox(0.1f, 0.1f, 0.3f, 0.3f))))
        repeat(5) { tracker.update(emptyList()) }
        assertTrue(tracker.activeTracks().isEmpty())
    }

    @Test fun tracker_emptyInputReturnsEmpty() {
        val tracker = MultiObjectTracker()
        assertTrue(tracker.update(emptyList()).isEmpty())
    }

    @Test fun motion_velocityAndDirection() {
        val history = (0..9).map { i ->
            val cx = 0.1f + i * 0.02f
            BoundingBox(cx - 0.05f, 0.4f, cx + 0.05f, 0.6f)
        }
        val track = Track(1, "car", history.last(), 0.9f, history, 10, 0, 0f, 0f, true)
        val m = MotionAnalyzer.analyze(track, 1000, 1000)
        assertTrue("speed=${m.speedPxPerSec}", m.speedPxPerSec > 0f)
        assertEquals("right", m.directionLabel)
        assertTrue(m.predictionUncertainty < 0.5f)
    }

    @Test fun motion_singlePointHasMaxUncertainty() {
        val track = Track(1, "car", BoundingBox(0.1f, 0.1f, 0.2f, 0.2f), 0.9f, listOf(BoundingBox(0.1f, 0.1f, 0.2f, 0.2f)), 1, 0, 0f, 0f, true)
        val m = MotionAnalyzer.analyze(track, 1000, 1000)
        assertEquals(0f, m.speedPxPerSec, 0.001f)
        assertEquals(1f, m.predictionUncertainty, 0.001f)
    }

    @Test fun directionLabel_cardinalDirections() {
        assertEquals("right", MotionAnalyzer.directionLabel(1f, 0f))
        assertEquals("left", MotionAnalyzer.directionLabel(-1f, 0f))
        assertEquals("down", MotionAnalyzer.directionLabel(0f, 1f))
        assertEquals("up", MotionAnalyzer.directionLabel(0f, -1f))
        assertEquals("stationary", MotionAnalyzer.directionLabel(0f, 0f))
    }

    @Test fun iou_identicalBoxesIsOne() {
        val b = BoundingBox(0.1f, 0.1f, 0.5f, 0.5f)
        assertEquals(1f, b.iou(b), 0.001f)
        assertEquals(0f, b.iou(BoundingBox(0.6f, 0.6f, 0.9f, 0.9f)), 0.001f)
    }

    @Test fun ocr_categorization() {
        assertEquals(com.sight.app.vision.OcrCategory.HEALTH, OcrEngine.categorize("Health 100"))
        assertEquals(com.sight.app.vision.OcrCategory.AMMO, OcrEngine.categorize("Ammo 30"))
        assertEquals(com.sight.app.vision.OcrCategory.WARNING, OcrEngine.categorize("WARNING low battery"))
        assertEquals(com.sight.app.vision.OcrCategory.NUMBER, OcrEngine.categorize("1250"))
    }

    @Test fun scene_emptyInputReportsHighUncertainty() {
        val s = SceneSummarizer.summarize(emptyList(), emptyList(), emptyList(), null)
        assertTrue(s.uncertainty > 0.9f)
        assertTrue(s.headline.contains("No objects"))
    }

    @Test fun scene_countsDetections() {
        val dets = listOf(
            Detection("car", 2, 0.9f, BoundingBox(0.1f, 0.1f, 0.3f, 0.3f)),
            Detection("car", 2, 0.8f, BoundingBox(0.4f, 0.4f, 0.6f, 0.6f))
        )
        val s = SceneSummarizer.summarize(dets, emptyList(), emptyList(), null)
        assertEquals(2, s.objectCounts["car"])
    }
}
