package com.sight.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sight.app.ui.AnalysisUiState
import com.sight.app.ui.components.StatCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    state: AnalysisUiState,
    onNavigate: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("SIGHT", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text("On-device AI visual intelligence", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("System status", style = MaterialTheme.typography.titleLarge)
                    Text(if (state.modelReady) "● Model ready" else "● Model unavailable", color = if (state.modelReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    Text(state.modelStatus, style = MaterialTheme.typography.bodySmall)
                    Text("Frames processed: ${state.framesProcessed} • Avg inference: ${state.avgInferenceMs}ms • FPS: ${"%.1f".format(state.avgFps)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Detections", state.detections.size.toString(), Modifier.weight(1f))
                StatCard("Tracks", state.tracks.size.toString(), Modifier.weight(1f))
                StatCard("OCR blocks", state.ocr.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            Text("Quick access", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onNavigate("vision") }, modifier = Modifier.weight(1f)) { Text("Analyze") }
                Button(onClick = { onNavigate("simulation") }, modifier = Modifier.weight(1f)) { Text("Simulation") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onNavigate("tracking") }, modifier = Modifier.weight(1f)) { Text("Tracking") }
                Button(onClick = { onNavigate("scene") }, modifier = Modifier.weight(1f)) { Text("Scene") }
            }
        }
        item { Text("Recent analyses", style = MaterialTheme.typography.titleLarge) }
        if (state.recentAnalyses.isEmpty()) {
            item { Text("No analyses yet. Pick an image, video, or try the Simulation Lab.", style = MaterialTheme.typography.bodySmall) }
        } else {
            items(state.recentAnalyses) { record ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${record.sourceType} • ${record.sourceName}", style = MaterialTheme.typography.titleLarge)
                        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(record.timestampMs))
                        Text("$date • ${record.frameCount} frames • ${record.detectionCount} detections • ${record.trackCount} tracks", style = MaterialTheme.typography.bodySmall)
                        if (record.summaryJson.isNotEmpty()) Text(record.summaryJson, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
