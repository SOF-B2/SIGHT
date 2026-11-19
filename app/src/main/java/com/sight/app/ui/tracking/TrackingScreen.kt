package com.sight.app.ui.tracking

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.sight.app.ui.AnalysisUiState
import com.sight.app.ui.components.OverlayCanvas
import com.sight.app.ui.components.StatCard

@Composable
fun TrackingScreen(state: AnalysisUiState) {
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Object Tracking", style = MaterialTheme.typography.headlineMedium)
        Text("Kalman-filtered multi-object tracking with IoU association. IDs persist across frames; lost tracks coast on prediction and recover when detections reappear.", style = MaterialTheme.typography.bodySmall)
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
            val bmp = state.annotatedBitmap ?: state.bitmap
            if (bmp != null) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = "Tracking preview", modifier = Modifier.fillMaxSize())
                OverlayCanvas(tracks = state.tracks, motions = state.motions, showTrails = state.settings.showTrails, showPredictions = state.settings.showPredictions, modifier = Modifier.fillMaxSize())
            } else {
                Card(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().padding(16.dp)) { Text("Run an image/video/camera analysis first (Vision tab).", style = MaterialTheme.typography.bodySmall) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Active tracks", state.tracks.count { it.isActive }.toString(), Modifier.weight(1f))
            StatCard("Coasting", state.tracks.count { !it.isActive }.toString(), Modifier.weight(1f))
            StatCard("Frames", state.framesProcessed.toString(), Modifier.weight(1f))
        }
        if (state.tracks.isEmpty()) Text("No tracks yet.", style = MaterialTheme.typography.bodySmall)
        state.tracks.forEach { t ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Track #${t.id} — ${t.label} ${if (t.isActive) "(active)" else "(coasting, missed ${t.missedFrames})"}", style = MaterialTheme.typography.titleLarge)
                    Text("Confidence: ${(t.confidence * 100).toInt()}% • Age: ${t.ageFrames} frames • History: ${t.history.size} points", style = MaterialTheme.typography.bodySmall)
                    Text("Position: [${"%.2f".format(t.box.x1)}, ${"%.2f".format(t.box.y1)}, ${"%.2f".format(t.box.x2)}, ${"%.2f".format(t.box.y2)}]", style = MaterialTheme.typography.bodySmall)
                    Text("Velocity (normalized/frame): vx=${"%.4f".format(t.velocityX)}, vy=${"%.4f".format(t.velocityY)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
