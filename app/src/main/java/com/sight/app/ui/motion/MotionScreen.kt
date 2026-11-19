package com.sight.app.ui.motion

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sight.app.ui.AnalysisUiState
import com.sight.app.ui.components.StatCard

@Composable
fun MotionScreen(state: AnalysisUiState) {
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Motion Analysis", style = MaterialTheme.typography.headlineMedium)
        Text("Velocity, direction, acceleration and 0.5s trajectory predictions computed from observed track history. Uncertainty rises when history is short or a track is coasting.", style = MaterialTheme.typography.bodySmall)
        val cam = state.cameraMotion
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Camera motion (global)", style = MaterialTheme.typography.titleLarge)
                if (cam == null) Text("No frames compared yet.", style = MaterialTheme.typography.bodySmall)
                else if (!cam.isMoving) Text("Camera appears stationary (shift < 1.5px/frame).", style = MaterialTheme.typography.bodySmall)
                else Text("Camera moving: dx=${"%.1f".format(cam.dxPx)}px, dy=${"%.1f".format(cam.dyPx)}px, magnitude=${"%.1f".format(cam.magnitudePx)}px/frame via ${cam.method}. Object motion should be read relative to this global shift.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.motions.isEmpty()) Text("No motion estimates yet. Run a video/camera/simulation analysis.", style = MaterialTheme.typography.bodySmall)
        state.motions.forEach { m ->
            val track = state.tracks.find { it.id == m.trackId }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Track #${m.trackId} ${track?.label ?: ""}", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatCard("Speed", "${"%.0f".format(m.speedPxPerSec)} px/s", Modifier.weight(1f))
                        StatCard("Direction", m.directionLabel, Modifier.weight(1f))
                    }
                    Text("Angle: ${"%.1f".format(m.directionDegrees)}° • Acceleration: ${"%.0f".format(m.accelerationPxPerSec2)} px/s²", style = MaterialTheme.typography.bodySmall)
                    Text("Prediction uncertainty: ${(m.predictionUncertainty * 100).toInt()}% (history: ${m.historyLength} frames) — magenta box in Vision/Tracking previews shows the 0.5s predicted position.", style = MaterialTheme.typography.bodySmall)
                    // Simple speed bar graph (single bar, real value)
                    Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                        val maxSpeed = state.motions.maxOfOrNull { it.speedPxPerSec }?.coerceAtLeast(1f) ?: 1f
                        val frac = (m.speedPxPerSec / maxSpeed).coerceIn(0f, 1f)
                        drawRect(Color.DarkGray, size = size)
                        drawRect(Color.Cyan, topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(size.width * frac, size.height))
                    }
                }
            }
        }
    }
}
