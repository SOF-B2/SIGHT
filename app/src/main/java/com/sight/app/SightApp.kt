package com.sight.app

import android.app.Application
import com.sight.app.di.AppContainer

class SightApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
