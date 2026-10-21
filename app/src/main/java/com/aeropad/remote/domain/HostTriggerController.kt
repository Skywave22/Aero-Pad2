package com.aeropad.remote.domain

import com.aeropad.remote.data.hosts.HostProfile
import com.aeropad.remote.model.HidConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F6 — run a macro when a specific host connects or disconnects.
 *
 * Why this is the cheapest automation feature in the app: the state machine,
 * the host profiles and the macro engine already exist. All that was missing
 * was this glue, which lets a user say "when I connect to my TV, mute the PC"
 * once instead of every single time.
 */
class HostTriggerController(
    private val profileFor: (String) -> HostProfile?,
    private val playMacro: (Long) -> Unit
) {

    /** Address of the host we are currently connected to (null = not connected). */
    var connectedAddress: String? = null
        private set

    /**
     * Feed every connection state here. Fires at most one trigger per actual
     * connect / disconnect edge.
     *
     * Deliberately only treats [HidConnectionState.Idle] as "disconnected":
     * a transient Error or a reconnect in progress must not fire the
     * disconnect macro, because the link may come straight back.
     */
    fun onState(state: HidConnectionState) {
        val address = (state as? HidConnectionState.Connected)?.device?.address

        if (address != null) {
            if (address != connectedAddress) {
                connectedAddress = address
                fire(profileFor(address)?.onConnectMacroId?.takeIf { it > 0 })
            }
            return
        }

        if (state is HidConnectionState.Idle) {
            val previous = connectedAddress ?: return
            connectedAddress = null
            fire(profileFor(previous)?.onDisconnectMacroId?.takeIf { it > 0 })
        }
    }

    private fun fire(macroId: Long?) {
        if (macroId == null) return
        runCatching { playMacro(macroId) }
            .onFailure { Timber.w(it, "host trigger macro %d failed", macroId) }
    }
}

/**
 * App-lifetime owner of the trigger logic: collects the HID state flow and
 * asks [MacroEngine] to play the bound macros.
 *
 * Started by the foreground [com.aeropad.remote.service.HidService], which is
 * alive for exactly as long as the HID link can be.
 */
@Singleton
class HostTriggerRunner @Inject constructor(
    private val controller: HidController,
    private val hosts: com.aeropad.remote.data.hosts.HostProfiles,
    private val macroEngine: MacroEngine
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val logic = HostTriggerController(
        profileFor = { address -> hosts.profileFor(address) },
        playMacro = { id -> macroEngine.play(id) }
    )

    /** Idempotent — starting twice does not double-fire triggers. */
    fun start() {
        if (job != null) return
        // HidController.state is a StateFlow: it already conflates and only
        // emits on change, so no distinctUntilChanged() here (Kotlin flags it
        // as a no-op on StateFlow).
        job = controller.state
            .onEach { state ->
                runCatching { logic.onState(state) }
                    .onFailure { Timber.w(it, "host trigger handling failed") }
            }
            .launchIn(scope)
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
