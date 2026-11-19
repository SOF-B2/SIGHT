package com.sight.app.data

import com.sight.app.vision.Detection
import com.sight.app.vision.FrameResult
import com.sight.app.vision.MotionEstimate
import com.sight.app.vision.Track
import org.json.JSONArray
import org.json.JSONObject

/** Builds JSON / CSV export payloads from real analysis data. */
object ExportManager {

    fun buildJsonReport(
        sourceName: String,
        modelInfo: String,
        frames: List<FrameResult>,
        tracks: List<Track>,
        motions: List<MotionEstimate>
    ): String {
        val root = JSONObject()
        root.put("app", "SIGHT")
        root.put("timestamp", System.currentTimeMillis())
        root.put("source", sourceName)
        root.put("model", modelInfo)
        root.put("frameCount", frames.size)
        val framesArr = JSONArray()
        for (f in frames) {
            val fo = JSONObject()
            fo.put("frameIndex", f.frameIndex)
            fo.put("timestampMs", f.timestampMs)
            fo.put("inferenceMs", f.inferenceMs)
            fo.put("ocrMs", f.ocrMs)
            val dets = JSONArray()
            for (d in f.detections) dets.put(detectionJson(d))
            fo.put("detections", dets)
            val ocrArr = JSONArray()
            for (o in f.ocr) {
                val oo = JSONObject()
                oo.put("text", o.text)
                oo.put("category", o.category.name)
                oo.put("box", boxJson(o.box.x1, o.box.y1, o.box.x2, o.box.y2))
                ocrArr.put(oo)
            }
            fo.put("ocr", ocrArr)
            framesArr.put(fo)
        }
        root.put("frames", framesArr)
        val tracksArr = JSONArray()
        for (t in tracks) {
            val to = JSONObject()
            to.put("id", t.id)
            to.put("label", t.label)
            to.put("confidence", t.confidence.toDouble())
            to.put("ageFrames", t.ageFrames)
            to.put("missedFrames", t.missedFrames)
            to.put("historyLength", t.history.size)
            to.put("box", boxJson(t.box.x1, t.box.y1, t.box.x2, t.box.y2))
            tracksArr.put(to)
        }
        root.put("tracks", tracksArr)
        val motionsArr = JSONArray()
        for (m in motions) {
            val mo = JSONObject()
            mo.put("trackId", m.trackId)
            mo.put("speedPxPerSec", m.speedPxPerSec.toDouble())
            mo.put("directionDegrees", m.directionDegrees.toDouble())
            mo.put("directionLabel", m.directionLabel)
            mo.put("accelerationPxPerSec2", m.accelerationPxPerSec2.toDouble())
            mo.put("predictionUncertainty", m.predictionUncertainty.toDouble())
            motionsArr.put(mo)
        }
        root.put("motions", motionsArr)
        return root.toString(2)
    }

    fun buildCsv(tracks: List<Track>, motions: List<MotionEstimate>): String {
        val sb = StringBuilder()
        sb.append("track_id,label,confidence,age_frames,missed_frames,speed_px_per_sec,direction_degrees,direction_label,acceleration_px_per_sec2,prediction_uncertainty\n")
        val motionById = motions.associateBy { it.trackId }
        for (t in tracks) {
            val m = motionById[t.id]
            sb.append("${t.id},${t.label},${t.confidence},${t.ageFrames},${t.missedFrames},${m?.speedPxPerSec ?: 0f},${m?.directionDegrees ?: 0f},${m?.directionLabel ?: "stationary"},${m?.accelerationPxPerSec2 ?: 0f},${m?.predictionUncertainty ?: 1f}\n")
        }
        return sb.toString()
    }

    fun buildOcrText(frames: List<FrameResult>): String {
        val sb = StringBuilder()
        for (f in frames) {
            for (o in f.ocr) sb.append("[frame ${f.frameIndex}] (${o.category}) ${o.text}\n")
        }
        return if (sb.isEmpty()) "No OCR text extracted.\n" else sb.toString()
    }

    private fun detectionJson(d: Detection): JSONObject {
        val o = JSONObject()
        o.put("label", d.label)
        o.put("classId", d.classId)
        o.put("confidence", d.confidence.toDouble())
        o.put("box", boxJson(d.box.x1, d.box.y1, d.box.x2, d.box.y2))
        return o
    }

    private fun boxJson(x1: Float, y1: Float, x2: Float, y2: Float): JSONObject {
        val o = JSONObject()
        o.put("x1", x1.toDouble()); o.put("y1", y1.toDouble())
        o.put("x2", x2.toDouble()); o.put("y2", y2.toDouble())
        return o
    }
}
