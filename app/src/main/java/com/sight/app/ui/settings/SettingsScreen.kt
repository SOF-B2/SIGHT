package com.sight.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sight.app.ui.AnalysisUiState
import com.sight.app.ui.AnalysisViewModel

@Composable
fun SettingsScreen(state: AnalysisUiState, viewModel: AnalysisViewModel) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("AI model", style = MaterialTheme.typography.titleLarge)
                Text(state.modelStatus, style = MaterialTheme.typography.bodySmall)
                Text("Bundled: COCO SSD MobileNet v1 (quantized TFLite, 90 classes). Runs fully on-device.", style = MaterialTheme.typography.bodySmall)
                Row {
                    Button(onClick = { viewModel.refreshModel() }) { Text("Reload model") }
                }
                Row {
                    Text("Use GPU delegate (if available)", modifier = Modifier.weight(1f))
                    Switch(checked = state.settings.useGpu, onCheckedChange = { viewModel.updateSettings { it.copy(useGpu = it) } })
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Detection", style = MaterialTheme.typography.titleLarge)
                Text("Confidence threshold: ${(state.settings.confidenceThreshold * 100).toInt()}%")
                Slider(value = state.settings.confidenceThreshold, onValueChange = { viewModel.updateSettings { it.copy(confidenceThreshold = it) } }, valueRange = 0.1f..0.9f)
                Text("Frame sampling: ${state.settings.frameSampleFps} fps")
                Slider(value = state.settings.frameSampleFps.toFloat(), onValueChange = { viewModel.updateSettings { it.copy(frameSampleFps = it.toInt()) } }, valueRange = 1f..15f, steps = 13)
                Text("Processing quality: ${when (state.settings.processingQuality) { 1 -> "Fast (640px)"; 2 -> "High (1280px)"; else -> "Balanced (960px)" }}")
                Slider(value = state.settings.processingQuality.toFloat(), onValueChange = { viewModel.updateSettings { it.copy(processingQuality = it.toInt()) } }, valueRange = 0f..2f, steps = 1)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Tracking & overlays", style = MaterialTheme.typography.titleLarge)
                Text("Max track age (frames coasting): ${state.settings.maxTrackAge}")
                Slider(value = state.settings.maxTrackAge.toFloat(), onValueChange = { viewModel.updateSettings { it.copy(maxTrackAge = it.toInt()) } }, valueRange = 5f..90f, steps = 16)
                Row { Text("Show motion trails", modifier = Modifier.weight(1f)); Switch(checked = state.settings.showTrails, onCheckedChange = { viewModel.updateSettings { it.copy(showTrails = it) } }) }
                Row { Text("Show predictions", modifier = Modifier.weight(1f)); Switch(checked = state.settings.showPredictions, onCheckedChange = { viewModel.updateSettings { it.copy(showPredictions = it) } }) }
                Row { Text("Enable OCR (ML Kit, on-device)", modifier = Modifier.weight(1f)); Switch(checked = state.settings.enableOcr, onCheckedChange = { viewModel.updateSettings { it.copy(enableOcr = it) } }) }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Export", style = MaterialTheme.typography.titleLarge)
                Text("Exports are generated from real analysis data (JSON report, CSV stats, OCR text).", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { shareText(context, "SIGHT report.json", viewModel.exportJson()) }) { Text("Share JSON") }
                    Button(onClick = { shareText(context, "SIGHT tracks.csv", viewModel.exportCsv()) }) { Text("Share CSV") }
                    Button(onClick = { shareText(context, "SIGHT ocr.txt", viewModel.exportOcr()) }) { Text("Share OCR") }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Privacy & storage", style = MaterialTheme.typography.titleLarge)
                Text("All analysis runs locally. No account, no uploads. Camera is used only for on-device analysis while the Camera tab is open.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.clearHistory() }) { Text("Delete history") }
                    Button(onClick = { viewModel.clearAllData() }) { Text("Clear all data") }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("About SIGHT", style = MaterialTheme.typography.titleLarge)
                Text("SIGHT v1.0.0 — modular on-device visual intelligence. Detection: TFLite COCO SSD MobileNet. OCR: ML Kit. Tracking: Kalman + IoU. Camera motion: template-match global flow. See README for architecture, tests and limitations.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun shareText(context: android.content.Context, filename: String, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, filename)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share $filename"))
}
