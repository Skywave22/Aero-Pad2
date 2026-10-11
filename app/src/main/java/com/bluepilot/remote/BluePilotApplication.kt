package com.bluepilot.remote

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application entry point.
 *
 * - Boots Hilt dependency graph.
 * - Plants Timber logging (debug tree only in debug builds; release stays silent
 *   so no log noise or PII leaks in production).
 */
@HiltAndroidApp
class BluePilotApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.d("BluePilot Remote v%s starting", BuildConfig.VERSION_NAME)
    }
}
