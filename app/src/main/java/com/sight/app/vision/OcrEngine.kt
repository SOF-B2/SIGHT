package com.sight.app.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * On-device OCR via ML Kit Text Recognition (Latin script). Runs fully offline
 * after first model download by ML Kit. Categorizes blocks into numbers,
 * health, ammo, warnings, labels and status indicators using transparent
 * keyword/pattern rules — no fabricated text.
 */
class OcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap, frameWidth: Int, frameHeight: Int): List<OcrBlock> {
        if (bitmap.width == 0 || bitmap.height == 0) return emptyList()
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()
        val blocks = mutableListOf<OcrBlock>()
        for (block in result.textBlocks) {
            val text = block.text.trim()
            if (text.isEmpty()) continue
            val r = block.boundingBox ?: continue
            val box = BoundingBox(
                (r.left.toFloat() / frameWidth).coerceIn(0f, 1f),
                (r.top.toFloat() / frameHeight).coerceIn(0f, 1f),
                (r.right.toFloat() / frameWidth).coerceIn(0f, 1f),
                (r.bottom.toFloat() / frameHeight).coerceIn(0f, 1f)
            )
            blocks.add(OcrBlock(text, null, box, categorize(text)))
        }
        return blocks
    }

    companion object {
        private val numberRegex = Regex("""\d+([.,]\d+)?%?""")
        private val healthKeywords = listOf("health", "hp", "life", "heal")
        private val ammoKeywords = listOf("ammo", "bullet", "mag", "reload", "rounds")
        private val warningKeywords = listOf("warning", "danger", "alert", "error", "low", "critical", "!" )
        private val statusKeywords = listOf("online", "offline", "connected", "paused", "recording", "ready", "loading")

        fun categorize(text: String): OcrCategory {
            val lower = text.lowercase()
            if (warningKeywords.any { it in lower }) return OcrCategory.WARNING
            if (healthKeywords.any { it in lower }) return OcrCategory.HEALTH
            if (ammoKeywords.any { it in lower }) return OcrCategory.AMMO
            if (statusKeywords.any { it in lower }) return OcrCategory.STATUS
            val digits = text.count { it.isDigit() }
            if (digits >= 2 && digits.toFloat() / text.length.coerceAtLeast(1) > 0.5f) return OcrCategory.NUMBER
            if (numberRegex.containsMatchIn(text) && text.length <= 8) return OcrCategory.NUMBER
            if (text.length <= 24 && text.none { it == ' ' }) return OcrCategory.LABEL
            return OcrCategory.OTHER
        }
    }
}
