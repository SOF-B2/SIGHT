package com.sight.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "sight_settings")

data class SightSettings(
    val confidenceThreshold: Float = 0.5f,
    val frameSampleFps: Int = 5,
    val processingQuality: Int = 0, // 0=balanced, 1=fast/low, 2=high
    val useGpu: Boolean = false,
    val enableOcr: Boolean = true,
    val maxTrackAge: Int = 30,
    val showTrails: Boolean = true,
    val showPredictions: Boolean = true
)

class SettingsRepository(private val context: Context) {
    private val CONF = floatPreferencesKey("confidence_threshold")
    private val FPS = intPreferencesKey("frame_sample_fps")
    private val QUALITY = intPreferencesKey("processing_quality")
    private val GPU = booleanPreferencesKey("use_gpu")
    private val OCR = booleanPreferencesKey("enable_ocr")
    private val MAXAGE = intPreferencesKey("max_track_age")
    private val TRAILS = booleanPreferencesKey("show_trails")
    private val PRED = booleanPreferencesKey("show_predictions")

    val settings: Flow<SightSettings> = context.dataStore.data.map { prefs ->
        SightSettings(
            confidenceThreshold = prefs[CONF] ?: 0.5f,
            frameSampleFps = prefs[FPS] ?: 5,
            processingQuality = prefs[QUALITY] ?: 0,
            useGpu = prefs[GPU] ?: false,
            enableOcr = prefs[OCR] ?: true,
            maxTrackAge = prefs[MAXAGE] ?: 30,
            showTrails = prefs[TRAILS] ?: true,
            showPredictions = prefs[PRED] ?: true
        )
    }

    suspend fun update(transform: (SightSettings) -> SightSettings) {
        val current = settings.firstOrNull() ?: SightSettings()
        // Read current synchronously via flow collection is avoided; caller passes full copy.
        val next = transform(current)
        context.dataStore.edit { prefs ->
            prefs[CONF] = next.confidenceThreshold
            prefs[FPS] = next.frameSampleFps
            prefs[QUALITY] = next.processingQuality
            prefs[GPU] = next.useGpu
            prefs[OCR] = next.enableOcr
            prefs[MAXAGE] = next.maxTrackAge
            prefs[TRAILS] = next.showTrails
            prefs[PRED] = next.showPredictions
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
