package com.sight.app.ui.scene

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import com.sight.app.ui.AnalysisUiState

@Composable
fun SceneScreen(state: AnalysisUiState) {
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Scene Understanding", style = MaterialTheme.typography.headlineMedium)
        Text("Summaries are built only from detected objects, tracks, OCR and camera motion. Uncertainty is shown when data is sparse.", style = MaterialTheme.typography.bodySmall)
        val scene = state.scene
        if (scene == null) {
            Text("No scene analyzed yet. Run an analysis from the Vision tab or Simulation Lab.", style = MaterialTheme.typography.bodySmall)
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(scene.headline, style = MaterialTheme.typography.titleLarge)
                    Text("Uncertainty: ${(scene.uncertainty * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Objects detected", style = MaterialTheme.typography.titleLarge)
                    if (scene.objectCounts.isEmpty()) Text("None.", style = MaterialTheme.typography.bodySmall)
                    scene.objectCounts.forEach { (label, count) -> Text("• $label × $count") }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Moving objects (${scene.movingCount})", style = MaterialTheme.typography.titleLarge)
                    if (scene.movingDirections.isEmpty()) Text("None confirmed moving.", style = MaterialTheme.typography.bodySmall)
                    scene.movingDirections.forEach { Text("• $it") }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Extracted text", style = MaterialTheme.typography.titleLarge)
                    if (scene.ocrTexts.isEmpty()) Text("No text recognized.", style = MaterialTheme.typography.bodySmall)
                    scene.ocrTexts.forEach { Text("• \"$it\"") }
                    if (scene.statusIndicators.isNotEmpty()) {
                        Text("Status indicators: ${scene.statusIndicators.joinToString()}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Important events", style = MaterialTheme.typography.titleLarge)
                    if (scene.events.isEmpty()) Text("No events.", style = MaterialTheme.typography.bodySmall)
                    scene.events.forEach { Text("• $it") }
                    scene.cameraMotion?.let { cam ->
                        Text(if (cam.isMoving) "Camera is moving (global shift ${"%.1f".format(cam.magnitudePx)}px/frame)." else "Camera appears stationary.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
