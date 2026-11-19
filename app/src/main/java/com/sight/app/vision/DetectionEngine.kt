package com.sight.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Genuine on-device object detection using TensorFlow Lite.
 * Bundled model: COCO SSD MobileNet v1 (quantized, 300x300 input, 90 COCO classes)
 * from TensorFlow's model zoo (Apache 2.0). Output: [1,10,4] boxes, [1,10] classes,
 * [1,10] scores, [1] count. If the model file is missing the engine reports
 * [ModelStatus.MISSING] instead of fabricating results.
 */
enum class ModelStatus { READY, MISSING, ERROR }

class DetectionEngine(
    context: Context,
    private val useGpu: Boolean = false
) {
    private val appContext = context.applicationContext
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    var status: ModelStatus = ModelStatus.MISSING
        private set
    var statusMessage: String = ""
        private set
    var labels: List<String> = emptyList()
        private set

    companion object {
        const val MODEL_ASSET = "coco_ssd_mobilenet.tflite"
        const val LABELS_ASSET = "labelmap.txt"
        private const val INPUT_SIZE = 300
        private const val MAX_RESULTS = 10
        private const val TAG = "DetectionEngine"
    }

    init { load() }

    private fun load() {
        try {
            labels = appContext.assets.open(LABELS_ASSET).bufferedReader().readLines()
                .map { it.trim() }.filter { it.isNotEmpty() && it != "???" }
            val buffer = loadModelFile()
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                if (useGpu) {
                    try {
                        gpuDelegate = GpuDelegate()
                        addDelegate(gpuDelegate)
                    } catch (e: Exception) {
                        Log.w(TAG, "GPU delegate unavailable, falling back to CPU", e)
                    }
                }
            }
            interpreter = Interpreter(buffer, options)
            status = ModelStatus.READY
            statusMessage = "COCO SSD MobileNet v1 (quantized, 300x300) ready"
        } catch (e: Exception) {
            val missing = e.message?.contains("Unable to open asset", ignoreCase = true) == true ||
                e.message?.contains("No such file", ignoreCase = true) == true
            status = if (missing) ModelStatus.MISSING else ModelStatus.ERROR
            statusMessage = if (missing)
                "Model file '$MODEL_ASSET' not found in assets. See README: Model setup."
            else "Model load failed: ${e.message}"
            Log.e(TAG, statusMessage, e)
        }
    }

    fun detect(bitmap: Bitmap, confidenceThreshold: Float = 0.5f): List<Detection> {
        val interp = interpreter ?: return emptyList()
        if (bitmap.width == 0 || bitmap.height == 0) return emptyList()
        val start = System.currentTimeMillis()
        val scaled = if (bitmap.width != INPUT_SIZE || bitmap.height != INPUT_SIZE)
            Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true) else bitmap
        val input = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (p in pixels) {
            input.put(((p shr 16) and 0xFF).toByte())
            input.put(((p shr 8) and 0xFF).toByte())
            input.put((p and 0xFF).toByte())
        }
        input.rewind()
        val boxes = Array(1) { Array(MAX_RESULTS) { FloatArray(4) } }
        val classes = Array(1) { FloatArray(MAX_RESULTS) }
        val scores = Array(1) { FloatArray(MAX_RESULTS) }
        val count = FloatArray(1)
        val outputs = mapOf(
            0 to boxes, 1 to classes, 2 to scores, 3 to count
        )
        try {
            interp.runForMultipleInputsOutputs(input, outputs)
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
            return emptyList()
        }
        val results = mutableListOf<Detection>()
        val n = count[0].toInt().coerceIn(0, MAX_RESULTS)
        for (i in 0 until n) {
            val score = scores[0][i]
            if (score < confidenceThreshold) continue
            val cls = classes[0][i].toInt()
            val label = labels.getOrNull(cls) ?: "class_$cls"
            val b = boxes[0][i]
            // Model outputs [ymin, xmin, ymax, xmax] normalized.
            results.add(
                Detection(
                    label = label, classId = cls, confidence = score,
                    box = BoundingBox(
                        x1 = b[1].coerceIn(0f, 1f), y1 = b[0].coerceIn(0f, 1f),
                        x2 = b[3].coerceIn(0f, 1f), y2 = b[2].coerceIn(0f, 1f)
                    )
                )
            )
        }
        Log.d(TAG, "detect: ${results.size} objects in ${System.currentTimeMillis() - start}ms")
        return results
    }

    fun close() {
        try { interpreter?.close() } catch (_: Exception) {}
        try { gpuDelegate?.close() } catch (_: Exception) {}
        interpreter = null
    }

    private fun loadModelFile(): ByteBuffer {
        // Read via stream (not openFd/mmap) so it works even if aapt compresses the asset.
        val bytes = appContext.assets.open(MODEL_ASSET).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buffer.put(bytes)
        buffer.rewind()
        return buffer
    }
}
