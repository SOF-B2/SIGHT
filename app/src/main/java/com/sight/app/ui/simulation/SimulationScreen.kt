package com.sight.app.ui.simulation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.sight.app.ui.AnalysisUiState
import com.sight.app.ui.AnalysisViewModel
import com.sight.app.ui.components.OverlayCanvas
import com.sight.app.vision.BoundingBox
import com.sight.app.vision.Detection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

private data class SimObject(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    val label: String, val color: Int, var occludedUntilFrame: Int = -1
)

/**
 * Built-in synthetic test environment. Objects move with constant velocity,
 * bounce off walls, can be occluded (hidden for N frames), and the virtual
 * camera can pan. Ground-truth boxes are fed to the real tracker/motion
 * pipeline — so tracking/motion results are genuine, while detection input
 * is labeled synthetic (no TFLite inference on fake pixels).
 */
@Composable
fun SimulationScreen(state: AnalysisUiState, viewModel: AnalysisViewModel) {
    var objectCount by remember { mutableIntStateOf(3) }
    var speed by remember { mutableFloatStateOf(0.004f) }
    var cameraPan by remember { mutableFloatStateOf(0f) }
    var occlusion by remember { mutableFloatStateOf(0f) } // 0..1 probability
    var noise by remember { mutableFloatStateOf(0f) }
    var running by remember { mutableIntStateOf(0) } // 0 stopped, 1 running
    val objects = remember { mutableListOf<SimObject>() }
    val frameIndex = remember { mutableIntStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    DisposableEffect(Unit) { onDispose { running = 0 } }

    fun resetObjects() {
        objects.clear()
        val labels = listOf("car", "person", "bicycle", "dog", "bus", "truck")
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.MAGENTA, Color.CYAN)
        repeat(objectCount) { i ->
            objects.add(
                SimObject(
                    x = 0.15f + i * 0.15f, y = 0.3f + (i % 3) * 0.15f,
                    vx = speed * (if (i % 2 == 0) 1f else -1f),
                    vy = speed * 0.6f * (if (i % 3 == 0) 1f else -1f),
                    label = labels[i % labels.size], color = colors[i % colors.size]
                )
            )
        }
        frameIndex.intValue = 0
    }

    fun renderFrame(): Pair<Bitmap, List<Detection>> {
        val w = 640; val h = 480
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.DKGRAY)
        // Camera pan shifts the world
        val panX = cameraPan * w
        val gridPaint = Paint().apply { color = Color.GRAY; strokeWidth = 1f }
        for (gx in 0 until w step 40) canvas.drawLine(gx + panX, 0f, gx + panX, h.toFloat(), gridPaint)
        val detections = mutableListOf<Detection>()
        for (o in objects) {
            // Physics step
            o.x += o.vx; o.y += o.vy
            if (o.x < 0.05f || o.x > 0.95f) o.vx = -o.vx
            if (o.y < 0.05f || o.y > 0.95f) o.vy = -o.vy
            o.x = o.x.coerceIn(0.05f, 0.95f); o.y = o.y.coerceIn(0.05f, 0.95f)
            // Random occlusion
            if (occlusion > 0f && Random.nextFloat() < occlusion * 0.02f) {
                o.occludedUntilFrame = frameIndex.intValue + 15
            }
            val occluded = frameIndex.intValue < o.occludedUntilFrame
            val px = o.x * w + panX; val py = o.y * h
            val size = 40f
            if (!occluded) {
                val paint = Paint().apply { color = o.color; style = Paint.Style.FILL }
                canvas.drawRect(px - size / 2, py - size / 2, px + size / 2, py + size / 2, paint)
                val box = BoundingBox(
                    ((px - size / 2) / w).coerceIn(0f, 1f), ((py - size / 2) / h).coerceIn(0f, 1f),
                    ((px + size / 2) / w).coerceIn(0f, 1f), ((py + size / 2) / h).coerceIn(0f, 1f)
                )
                detections.add(Detection(o.label, 0, 0.99f, box))
            } else {
                val paint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
                canvas.drawRect(px - size / 2, py - size / 2, px + size / 2, py + size / 2, paint)
            }
        }
        // Noise overlay
        if (noise > 0f) {
            val rnd = Random(frameIndex.intValue)
            val np = Paint().apply { color = Color.WHITE }
            repeat((noise * 300).toInt()) {
                canvas.drawPoint(rnd.nextFloat() * w, rnd.nextFloat() * h, np)
            }
        }
        // HUD text drawn as pixels (not OCR'd in simulation — OCR runs on real inputs)
        val tp = Paint().apply { color = Color.WHITE; textSize = 20f }
        canvas.drawText("SIM frame ${frameIndex.intValue} objects=${objects.size}", 10f, 30f, tp)
        return bmp to detections
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Simulation Lab", style = MaterialTheme.typography.headlineMedium)
        Text("Synthetic ground-truth objects feed the real tracker + motion pipeline. Use this to test tracking, occlusion recovery and camera-motion compensation without a game.", style = MaterialTheme.typography.bodySmall)
        Text("Object count: $objectCount")
        Slider(value = objectCount.toFloat(), onValueChange = { objectCount = it.toInt().coerceIn(1, 6) }, valueRange = 1f..6f, steps = 4)
        Text("Speed: ${"%.4f".format(speed)}")
        Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.001f..0.02f)
        Text("Camera pan: ${"%.2f".format(cameraPan)}")
        Slider(value = cameraPan, onValueChange = { cameraPan = it }, valueRange = -0.3f..0.3f)
        Text("Occlusion chance: ${(occlusion * 100).toInt()}%")
        Slider(value = occlusion, onValueChange = { occlusion = it }, valueRange = 0f..1f)
        Text("Noise: ${(noise * 100).toInt()}%")
        Slider(value = noise, onValueChange = { noise = it }, valueRange = 0f..1f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                resetObjects()
                running = 1
                scope.launch(Dispatchers.Default) {
                    while (running == 1) {
                        val (bmp, dets) = renderFrame()
                        viewModel.onSimulationFrame(bmp, dets)
                        frameIndex.intValue++
                        delay(100) // 10 fps simulation
                    }
                }
            }) { Text("Start") }
            Button(onClick = { running = 0 }) { Text("Stop") }
            Button(onClick = { resetObjects() }) { Text("Reset") }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
            val bmp = state.annotatedBitmap ?: state.bitmap
            if (bmp != null) {
                Image(bitmap = bmp.asImageBitmap(), contentDescription = "Simulation preview", modifier = Modifier.fillMaxSize())
                OverlayCanvas(tracks = state.tracks, motions = state.motions, showTrails = state.settings.showTrails, showPredictions = state.settings.showPredictions, modifier = Modifier.fillMaxSize())
            } else {
                Card(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize().padding(16.dp)) { Text("Press Start to run the synthetic scene.", style = MaterialTheme.typography.bodySmall) } }
            }
        }
        Text("Tracks: ${state.tracks.size} • Frames: ${state.framesProcessed} • Camera moving: ${state.cameraMotion?.isMoving ?: false}", style = MaterialTheme.typography.bodySmall)
        state.tracks.forEach { t ->
            Text("• #${t.id} ${t.label} age=${t.ageFrames} missed=${t.missedFrames} ${if (t.isActive) "active" else "coasting"}")
        }
    }
}
