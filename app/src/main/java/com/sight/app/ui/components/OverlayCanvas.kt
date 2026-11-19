package com.sight.app.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sight.app.vision.Detection
import com.sight.app.vision.MotionEstimate
import com.sight.app.vision.Track

/**
 * Draws detection boxes, track trails and prediction boxes over the preview.
 * Coordinates are normalized 0..1, matching the vision pipeline.
 */
@Composable
fun OverlayCanvas(
    detections: List<Detection> = emptyList(),
    tracks: List<Track> = emptyList(),
    motions: List<MotionEstimate> = emptyList(),
    showTrails: Boolean = true,
    showPredictions: Boolean = true,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val textPaint = Paint().apply { color = android.graphics.Color.WHITE; textSize = 11.sp.toPx(); isFakeBoldText = true }
        val bgPaint = Paint().apply { color = android.graphics.Color.argb(180, 0, 0, 0) }

        fun label(x: Float, y: Float, text: String) {
            val tw = textPaint.measureText(text)
            drawContext.canvas.nativeCanvas.drawRect(x, y - 14f, x + tw + 8f, y + 2f, bgPaint)
            drawContext.canvas.nativeCanvas.drawText(text, x + 4f, y, textPaint)
        }

        for (d in detections) {
            val rect = androidx.compose.ui.geometry.Rect(
                d.box.x1 * w, d.box.y1 * h, d.box.x2 * w, d.box.y2 * h
            )
            drawRect(Color.Cyan, rect.topLeft, rect.size, style = Stroke(width = 2f))
            label(rect.left, rect.top, "${d.label} ${(d.confidence * 100).toInt()}%")
        }
        if (showTrails) {
            for (t in tracks) {
                for (i in 1 until t.history.size) {
                    val a = t.history[i - 1]; val b = t.history[i]
                    drawLine(
                        Color.Yellow,
                        Offset(a.centerX * w, a.centerY * h),
                        Offset(b.centerX * w, b.centerY * h),
                        strokeWidth = 2f
                    )
                }
                val rect = androidx.compose.ui.geometry.Rect(
                    t.box.x1 * w, t.box.y1 * h, t.box.x2 * w, t.box.y2 * h
                )
                drawRect(Color.Yellow, rect.topLeft, rect.size, style = Stroke(width = 2f))
                label(rect.left, rect.bottom + 14f, "#${t.id} ${t.label} age ${t.ageFrames}")
            }
        }
        if (showPredictions) {
            for (m in motions) {
                val rect = androidx.compose.ui.geometry.Rect(
                    m.predictedBox.x1 * w, m.predictedBox.y1 * h,
                    m.predictedBox.x2 * w, m.predictedBox.y2 * h
                )
                drawRect(Color.Magenta, rect.topLeft, rect.size, style = Stroke(width = 2f))
                label(rect.left, rect.top, "pred #${m.trackId}")
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
