package com.sight.app.di

import android.content.Context
import com.sight.app.data.AnalysisDao
import com.sight.app.data.SettingsRepository
import com.sight.app.data.SightDatabase
import com.sight.app.vision.DetectionEngine
import com.sight.app.vision.MultiObjectTracker
import com.sight.app.vision.OcrEngine

/** Manual dependency container (no Hilt) to keep the build simple and reliable. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val settingsRepository = SettingsRepository(appContext)
    val database: SightDatabase = SightDatabase.getInstance(appContext)
    val analysisDao: AnalysisDao = database.analysisDao()
    val ocrEngine = OcrEngine()

    fun createDetectionEngine(useGpu: Boolean): DetectionEngine = DetectionEngine(appContext, useGpu)

    fun createTracker(maxAge: Int): MultiObjectTracker = MultiObjectTracker(maxAgeFrames = maxAge)
}
