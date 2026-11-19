package com.sight.app.ui

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sight.app.SightApp
import com.sight.app.data.AnalysisRecord
import com.sight.app.data.SightSettings
import com.sight.app.util.ImageUtils
import com.sight.app.vision.CameraMotion
import com.sight.app.vision.CameraMotionEstimator
import com.sight.app.vision.Detection
import com.sight.app.vision.DetectionEngine
import com.sight.app.vision.FrameResult
import com.sight.app.vision.MotionAnalyzer
import com.sight.app.vision.MotionEstimate
import com.sight.app.vision.MultiObjectTracker
import com.sight.app.vision.OcrBlock
import com.sight.app.vision.SceneSummarizer
import com.sight.app.vision.SceneSummary
import com.sight.app.vision.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class AnalysisUiState(
    val bitmap: Bitmap? = null,
    val annotatedBitmap: Bitmap? = null,
    val detections: List<Detection> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val motions: List<MotionEstimate> = emptyList(),
    val ocr: List<OcrBlock> = emptyList(),
    val scene: SceneSummary? = null,
    val cameraMotion: CameraMotion? = null,
    val isProcessing: Boolean = false,
    val isPaused: Boolean = false,
    val progress: Float = 0f,
    val progressLabel: String = "",
    val error: String? = null,
    val modelStatus: String = "Checking model…",
    val modelReady: Boolean = false,
    val avgInferenceMs: Long = 0,
    val avgFps: Double = 0.0,
    val framesProcessed: Int = 0,
    val sourceName: String = "",
    val settings: SightSettings = SightSettings(),
    val cameraActive: Boolean = false,
    val recentAnalyses: List<AnalysisRecord> = emptyList()
)

class AnalysisViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as SightApp).container
    private var engine: DetectionEngine? = null
    private var tracker: MultiObjectTracker? = null
    private var videoJob: Job? = null
    private var previousFrame: Bitmap? = null
    private val frameResults = mutableListOf<FrameResult>()
    private val inferenceTimes = mutableListOf<Long>()
    private var lastFrameTimeMs = 0L

    private val _state = MutableStateFlow(AnalysisUiState())
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.settingsRepository.settings.collect { s ->
                _state.value = _state.value.copy(settings = s)
                if (engine == null || engine?.status == null) initEngine(s.useGpu)
            }
        }
        viewModelScope.launch {
            container.analysisDao.recent().collect { records ->
                _state.value = _state.value.copy(recentAnalyses = records)
            }
        }
        initEngine(false)
    }

    private fun initEngine(useGpu: Boolean) {
        engine?.close()
        engine = container.createDetectionEngine(useGpu)
        tracker = container.createTracker(_state.value.settings.maxTrackAge)
        val e = engine!!
        _state.value = _state.value.copy(
            modelStatus = e.statusMessage,
            modelReady = e.status.name == "READY"
        )
    }

    fun refreshModel() {
        initEngine(_state.value.settings.useGpu)
    }

    fun updateSettings(transform: (SightSettings) -> SightSettings) {
        viewModelScope.launch {
            container.settingsRepository.update(transform)
        }
    }

    fun clearHistory() {
        viewModelScope.launch { container.analysisDao.deleteAll() }
    }

    fun clearAllData() {
        viewModelScope.launch {
            container.analysisDao.deleteAll()
            container.settingsRepository.clearAll()
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    // ---------- Image ----------
    fun analyzeImage(uri: Uri) {
        viewModelScope.launch {
            resetRun("image")
            val settings = _state.value.settings
            val bitmap = withContext(Dispatchers.IO) { ImageUtils.loadBitmap(getApplication(), uri) }
            if (bitmap == null) {
                _state.value = _state.value.copy(error = "Could not decode the selected image. Try a JPG/PNG file.")
                return@launch
            }
            processBitmap(bitmap, frameIndex = 0, sourceName = uri.lastPathSegment ?: "image")
            saveRecord("IMAGE", uri.lastPathSegment ?: "image")
        }
    }

    // ---------- Video ----------
    fun analyzeVideo(uri: Uri) {
        videoJob?.cancel()
        resetRun("video")
        videoJob = viewModelScope.launch {
            val settings = _state.value.settings
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(getApplication(), uri)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                if (durationMs <= 0) {
                    _state.value = _state.value.copy(error = "Could not read video duration. The file may be unsupported.")
                    return@launch
                }
                val fps = settings.frameSampleFps.coerceIn(1, 15)
                val stepMs = 1000L / fps
                var frameIndex = 0
                var timeMs = 0L
                while (timeMs < durationMs && isActive) {
                    while (_state.value.isPaused && isActive) delay(200)
                    if (!isActive) break
                    val frame = withContext(Dispatchers.IO) {
                        retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    }
                    if (frame != null) {
                        val scaled = scaleForQuality(frame, settings.processingQuality)
                        processBitmap(scaled, frameIndex, "video frame $frameIndex")
                        if (frame !== scaled) frame.recycle()
                    }
                    frameIndex++
                    timeMs += stepMs
                    _state.value = _state.value.copy(
                        progress = (timeMs.toFloat() / durationMs).coerceIn(0f, 1f),
                        progressLabel = "Frame $frameIndex • ${(timeMs / 1000)}s / ${(durationMs / 1000)}s"
                    )
                }
                saveRecord("VIDEO", uri.lastPathSegment ?: "video")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = _state.value.copy(error = "Video processing failed: ${e.message}")
            } finally {
                try { retriever.release() } catch (_: Exception) {}
                _state.value = _state.value.copy(isProcessing = false, progress = 1f, progressLabel = "Done")
            }
        }
    }

    fun pauseVideo() { _state.value = _state.value.copy(isPaused = true) }
    fun resumeVideo() { _state.value = _state.value.copy(isPaused = false) }
    fun stopVideo() { videoJob?.cancel(); _state.value = _state.value.copy(isProcessing = false, isPaused = false) }

    // ---------- Camera ----------
    fun setCameraActive(active: Boolean) {
        _state.value = _state.value.copy(cameraActive = active)
        if (active) resetRun("camera")
    }

    fun onCameraFrame(bitmap: Bitmap) {
        // Throttle: process at most ~3 fps to avoid overheating
        val now = System.currentTimeMillis()
        if (now - lastFrameTimeMs < 333) return
        lastFrameTimeMs = now
        viewModelScope.launch(Dispatchers.Default) {
            processBitmap(bitmap, _state.value.framesProcessed, "camera")
        }
    }

    // ---------- Simulation ----------
    fun onSimulationFrame(bitmap: Bitmap, syntheticDetections: List<Detection>) {
        viewModelScope.launch(Dispatchers.Default) {
            val settings = _state.value.settings
            val t = tracker ?: return@launch
            val tracks = t.update(syntheticDetections)
            val motions = tracks.map { MotionAnalyzer.analyze(it, bitmap.width, bitmap.height) }
            val cam = previousFrame?.let { CameraMotionEstimator.estimate(it, bitmap) }
            previousFrame = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            val scene = SceneSummarizer.summarize(syntheticDetections, tracks, emptyList(), cam)
            val annotated = ImageUtils.annotate(bitmap, syntheticDetections, tracks, motions, settings.showTrails, settings.showPredictions)
            _state.value = _state.value.copy(
                bitmap = bitmap, annotatedBitmap = annotated, detections = syntheticDetections,
                tracks = tracks, motions = motions, scene = scene, cameraMotion = cam,
                framesProcessed = _state.value.framesProcessed + 1,
                sourceName = "simulation"
            )
        }
    }

    // ---------- Core pipeline ----------
    private suspend fun processBitmap(bitmap: Bitmap, frameIndex: Int, sourceName: String) {
        val settings = _state.value.settings
        val eng = engine
        if (eng == null || ! _state.value.modelReady) {
            _state.value = _state.value.copy(
                error = "Detection model is not ready: ${_state.value.modelStatus}",
                bitmap = bitmap
            )
            return
        }
        _state.value = _state.value.copy(isProcessing = true, sourceName = sourceName)
        val start = System.currentTimeMillis()
        val detections = withContext(Dispatchers.Default) {
            eng.detect(bitmap, settings.confidenceThreshold)
        }
        val inferenceMs = System.currentTimeMillis() - start
        inferenceTimes.add(inferenceMs)
        val ocrStart = System.currentTimeMillis()
        val ocr = if (settings.enableOcr) {
            try { withContext(Dispatchers.Default) { container.ocrEngine.recognize(bitmap, bitmap.width, bitmap.height) } }
            catch (e: Exception) { emptyList() }
        } else emptyList()
        val ocrMs = System.currentTimeMillis() - ocrStart
        val t = tracker ?: container.createTracker(settings.maxTrackAge).also { tracker = it }
        val tracks = t.update(detections)
        val motions = tracks.map { MotionAnalyzer.analyze(it, bitmap.width, bitmap.height) }
        val cam = previousFrame?.let { CameraMotionEstimator.estimate(it, bitmap) }
        previousFrame = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val scene = SceneSummarizer.summarize(detections, tracks, ocr, cam)
        val annotated = ImageUtils.annotate(bitmap, detections, tracks, motions, settings.showTrails, settings.showPredictions)
        frameResults.add(FrameResult(frameIndex, System.currentTimeMillis(), detections, ocr, inferenceMs, ocrMs))
        val avgInf = if (inferenceTimes.isNotEmpty()) inferenceTimes.average().roundToLong() else inferenceMs
        val fps = if (inferenceMs > 0) 1000.0 / inferenceMs else 0.0
        _state.value = _state.value.copy(
            bitmap = bitmap, annotatedBitmap = annotated, detections = detections,
            tracks = tracks, motions = motions, ocr = ocr, scene = scene, cameraMotion = cam,
            avgInferenceMs = avgInf, avgFps = fps,
            framesProcessed = _state.value.framesProcessed + 1,
            isProcessing = false, error = null
        )
    }

    private fun scaleForQuality(bitmap: Bitmap, quality: Int): Bitmap {
        val maxDim = when (quality) { 1 -> 640; 2 -> 1280; else -> 960 }
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDim) return bitmap
        val scale = maxDim.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
    }

    private fun resetRun(kind: String) {
        tracker = container.createTracker(_state.value.settings.maxTrackAge)
        previousFrame = null
        frameResults.clear()
        inferenceTimes.clear()
        _state.value = _state.value.copy(
            detections = emptyList(), tracks = emptyList(), motions = emptyList(),
            ocr = emptyList(), scene = null, cameraMotion = null,
            framesProcessed = 0, progress = 0f, progressLabel = "",
            error = null, sourceName = kind, isPaused = false
        )
    }

    private suspend fun saveRecord(sourceType: String, sourceName: String) {
        val s = _state.value
        if (s.framesProcessed == 0) return
        container.analysisDao.insert(
            AnalysisRecord(
                timestampMs = System.currentTimeMillis(),
                sourceType = sourceType,
                sourceName = sourceName,
                frameCount = s.framesProcessed,
                detectionCount = s.detections.size,
                trackCount = s.tracks.size,
                ocrCount = s.ocr.size,
                avgInferenceMs = s.avgInferenceMs,
                avgFps = s.avgFps,
                summaryJson = s.scene?.headline ?: ""
            )
        )
    }

    fun exportJson(): String = ExportManagerJson.build(
        _state.value.sourceName, _state.value.modelStatus, frameResults.toList(),
        _state.value.tracks, _state.value.motions
    )

    fun exportCsv(): String = com.sight.app.data.ExportManager.buildCsv(_state.value.tracks, _state.value.motions)
    fun exportOcr(): String = com.sight.app.data.ExportManager.buildOcrText(frameResults.toList())

    override fun onCleared() {
        videoJob?.cancel()
        engine?.close()
        super.onCleared()
    }
}

private object ExportManagerJson {
    fun build(source: String, model: String, frames: List<FrameResult>, tracks: List<Track>, motions: List<MotionEstimate>): String =
        com.sight.app.data.ExportManager.buildJsonReport(source, model, frames, tracks, motions)
}
