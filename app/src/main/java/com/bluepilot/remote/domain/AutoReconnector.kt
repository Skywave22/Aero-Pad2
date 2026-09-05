package com.bluepilot.remote.domain

import com.bluepilot.remote.data.hosts.HostProfile
import com.bluepilot.remote.data.hosts.HostProfiles
import com.bluepilot.remote.model.HidConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * V2 MATRIX 4 b2 — auto-reconnect to the most recent saved host on app
 * start. OPT-IN via Settings ("Reconnect on launch", default OFF —
 * silent auto-connections surprise people).
 *
 * Rules (deliberate, conservative):
 *  - fires at most ONCE per process lifetime (no reconnect loops);
 *  - skipped when something is already connected/connecting;
 *  - failures are silent — the user simply connects manually as before.
 */
@Singleton
class AutoReconnector @Inject constructor(
    private val settings: SettingsStore,
    private val hosts: HostProfiles,
    private val hid: HidController
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var attempted = false

    /** Call once from MainActivity.onCreate — all gating happens inside. */
    fun maybeReconnect() {
        if (attempted) return
        attempted = true
        scope.launch {
            runCatching {
                val app = settings.appSettings.first()
                if (!app.autoReconnectLast) return@launch
                // Already live/being established? Don't interfere.
                if (hid.state.value !is HidConnectionState.Idle &&
                    hid.state.value !is HidConnectionState.Error
                ) return@launch
                val last = hosts.profiles.value.maxByOrNull { it.lastUsedAt } ?: return@launch
                Timber.i("auto-reconnect: trying '%s'", last.label)
                hid.start()
                hid.connectTo(last.address)
            }.onFailure { Timber.w(it, "auto-reconnect failed (silent)") }
        }
    }
}

