package com.sight.app.ui.vision

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.sight.app.ui.AnalysisUiState
import com.sight.app.ui.AnalysisViewModel
import com.sight.app.ui.components.OverlayCanvas
import com.sight.app.ui.components.StatCard

@Composable
fun VisionScreen(
    state: AnalysisUiState,
    viewModel: AnalysisViewModel
) {
    var tab by remember { mutableIntStateOf(0) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.analyzeImage(uri)
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.analyzeVideo(uri)
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }) { Text("Image") }
            Tab(selected = tab == 1, onClick = { tab = 1 }) { Text("Video") }
            Tab(selected = tab == 2, onClick = { tab = 2 }) { Text("Camera") }
        }
        if (!state.modelReady) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Detection model unavailable", style = MaterialTheme.typography.titleLarge)
                    Text(state.modelStatus, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { viewModel.refreshModel() }) { Text("Retry loading model") }
                }
            }
        }
        if (state.error != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.clearError() }) { Text("Dismiss") }
                }
            }
        }
        when (tab) {
            0 -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { imagePicker.launch("image/*") }) { Text("Pick image") }
                }
            }
            1 -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { videoPicker.launch("video/*") }) { Text("Pick video") }
                    if (state.isProcessing && !state.isPaused) Button(onClick = { viewModel.pauseVideo() }) { Text("Pause") }
                    if (state.isPaused) Button(onClick = { viewModel.resumeVideo() }) { Text("Resume") }
                    if (state.isProcessing) Button(onClick = { viewModel.stopVideo() }) { Text("Stop") }
                }
                if (state.isProcessing || state.progress > 0f) {
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                    Text(state.progressLabel, style = MaterialTheme.typography.bodySmall)
                }
            }
            2 -> CameraTab(state, viewModel)
        }
        // Preview with overlay
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
            val bmp = state.annotatedBitmap ?: state.bitmap
            if (bmp != null) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = "Analysis preview", modifier = Modifier.fillMaxSize())
                OverlayCanvas(
                    detections = state.detections, tracks = state.tracks, motions = state.motions,
                    showTrails = state.settings.showTrails, showPredictions = state.settings.showPredictions,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Card(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().padding(16.dp)) {
                        Text("No frame yet. Pick an image/video or start the camera.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("Detections", state.detections.size.toString(), Modifier.weight(1f))
            StatCard("Inference", "${state.avgInferenceMs}ms", Modifier.weight(1f))
            StatCard("FPS", "%.1f".format(state.avgFps), Modifier.weight(1f))
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Confidence threshold: ${(state.settings.confidenceThreshold * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = state.settings.confidenceThreshold,
                onValueChange = { viewModel.updateSettings { it.copy(confidenceThreshold = it) } },
                valueRange = 0.1f..0.9f
            )
            Text("Detections", style = MaterialTheme.typography.titleLarge)
            if (state.detections.isEmpty()) Text("None above threshold.", style = MaterialTheme.typography.bodySmall)
            state.detections.forEach { d ->
                Text("• ${d.label} — ${(d.confidence * 100).toInt()}% @ [${"%.2f".format(d.box.x1)}, ${"%.2f".format(d.box.y1)}, ${"%.2f".format(d.box.x2)}, ${"%.2f".format(d.box.y2)}]")
            }
            Text("OCR text", style = MaterialTheme.typography.titleLarge)
            if (state.ocr.isEmpty()) Text("No text recognized.", style = MaterialTheme.typography.bodySmall)
            state.ocr.forEach { o -> Text("• [${o.category}] ${o.text}") }
        }
    }
}

@Composable
private fun CameraTab(state: AnalysisUiState, viewModel: AnalysisViewModel) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var permissionGranted by remember { mutableIntStateOf(0) } // 0=unknown 1=granted 2=denied
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permissionGranted = if (granted) 1 else 2
    }
    DisposableEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
        onDispose { viewModel.setCameraActive(false) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.setCameraActive(true) }, enabled = permissionGranted == 1) { Text("Start analysis") }
            Button(onClick = { viewModel.setCameraActive(false) }) { Text("Stop") }
        }
        if (permissionGranted == 2) Text("Camera permission denied. Enable it in Settings to use live analysis.", color = MaterialTheme.colorScheme.error)
        if (state.cameraActive && permissionGranted == 1) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProvider = ProcessCameraProvider.getInstance(ctx)
                    cameraProvider.addListener({
                        val provider = cameraProvider.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { image: ImageProxy ->
                            try {
                                val bitmap = imageProxyToBitmap(image)
                                if (bitmap != null) viewModel.onCameraFrame(bitmap)
                            } finally { image.close() }
                        }
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                            )
                        } catch (e: Exception) {
                            // Camera unavailable on this device; preview stays blank, no crash.
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)
            )
            Text("Live analysis at ~3 fps (throttled to limit heat). Camera frames stay on-device.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    // Convert YUV_420_888 to RGB bitmap (compact implementation)
    return try {
        val yuv = image.planes
        val y = yuv[0].buffer; val u = yuv[1].buffer; val v = yuv[2].buffer
        val ySize = y.remaining(); val uSize = u.remaining(); val vSize = v.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        y.get(nv21, 0, ySize); v.get(nv21, ySize, vSize); u.get(nv21, ySize + vSize, uSize)
        val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, image.width, image.height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, image.width, image.height), 80, out)
        val bytes = out.toByteArray()
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) { null }
}
