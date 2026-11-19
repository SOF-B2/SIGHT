package com.sight.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import com.sight.app.vision.BoundingBox
import com.sight.app.vision.Detection
import com.sight.app.vision.MotionEstimate
import com.sight.app.vision.Track
import java.io.InputStream

object ImageUtils {

    fun loadBitmap(context: Context, uri: Uri, maxDimension: Int = 1280): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                decodeBounded(stream, maxDimension)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeBounded(stream: InputStream, maxDimension: Int): Bitmap? {
        val bytes = stream.readBytes()
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    /** Draws detection boxes, labels, track trails and predictions onto a copy of [source]. */
    fun annotate(
        source: Bitmap,
        detections: List<Detection> = emptyList(),
        tracks: List<Track> = emptyList(),
        motions: List<MotionEstimate> = emptyList(),
        showTrails: Boolean = true,
        showPredictions: Boolean = true
    ): Bitmap {
        val out = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat(); val h = out.height.toFloat()
        val boxPaint = Paint().apply { color = Color.CYAN; style = Paint.Style.STROKE; strokeWidth = 3f }
        val textPaint = Paint().apply { color = Color.WHITE; textSize = 28f; isFakeBoldText = true }
        val bgPaint = Paint().apply { color = Color.argb(180, 0, 0, 0) }
        val trailPaint = Paint().apply { color = Color.YELLOW; style = Paint.Style.STROKE; strokeWidth = 3f }
        val predPaint = Paint().apply { color = Color.MAGENTA; style = Paint.Style.STROKE; strokeWidth = 3f }

        fun drawBox(box: BoundingBox, paint: Paint, label: String?) {
            val l = box.x1 * w; val t = box.y1 * h; val r = box.x2 * w; val b = box.y2 * h
            canvas.drawRect(l, t, r, b, paint)
            if (label != null) {
                val tw = textPaint.measureText(label)
                canvas.drawRect(l, t - 34f, l + tw + 12f, t, bgPaint)
                canvas.drawText(label, l + 6f, t - 8f, textPaint)
            }
        }

        for (d in detections) {
            drawBox(d.box, boxPaint, "${d.label} ${(d.confidence * 100).toInt()}%")
        }
        if (showTrails) {
            for (t in tracks) {
                if (t.history.size < 2) continue
                for (i in 1 until t.history.size) {
                    val a = t.history[i - 1]; val b = t.history[i]
                    canvas.drawLine(a.centerX * w, a.centerY * h, b.centerX * w, b.centerY * h, trailPaint)
                }
                drawBox(t.box, trailPaint, "#${t.id} ${t.label}")
            }
        }
        if (showPredictions) {
            for (m in motions) drawBox(m.predictedBox, predPaint, "pred #${m.trackId}")
        }
        return out
    }
}
