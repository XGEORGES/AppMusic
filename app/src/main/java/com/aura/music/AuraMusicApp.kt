package com.aura.music

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AuraMusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
