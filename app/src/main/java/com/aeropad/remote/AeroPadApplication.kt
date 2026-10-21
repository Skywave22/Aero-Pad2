package com.aeropad.remote

import android.app.Application
import com.aeropad.remote.data.layout.LayoutRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Application entry point.
 *
 * - Boots Hilt dependency graph.
 * - Plants Timber logging (debug tree only in debug builds; release stays silent
 *   so no log noise or PII leaks in production).
 */
@HiltAndroidApp
class AeroPadApplication : Application() {

    /**
     * F2 — seeding must not wait for the Layouts screen to be opened, otherwise
     * a user who upgrades and goes straight to the editor never sees the new
     * shortcut packs.
     */
    @Inject lateinit var layoutRepository: LayoutRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.d("AeroPad Remote v%s starting", BuildConfig.VERSION_NAME)
        appScope.launch {
            runCatching { layoutRepository.seedIfEmpty() }
                .onFailure { Timber.w(it, "startup layout seeding failed") }
        }
    }
}
