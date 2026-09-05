package com.aeropad.remote.domain.usecase

import com.aeropad.remote.domain.HidController
import com.aeropad.remote.model.HidAction
import com.aeropad.remote.model.HidConnectionState
import com.aeropad.remote.model.RemoteDevice
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * UseCase layer (Clean Architecture).
 *
 * Each class is one intention. ViewModels compose these instead of talking
 * to the engine directly, so business rules have a single home and tests
 * can inject fakes trivially.
 */

/** Seam for tests: anything exposing connection state. */
interface ConnectionStateSource {
    val state: StateFlow<HidConnectionState>
}

@javax.inject.Singleton
class ConnectionStateHub @Inject constructor(
    controller: HidController
) : ConnectionStateSource {
    override val state: StateFlow<HidConnectionState> = controller.state
}

/** Observe the connection state machine. */
class ObserveConnectionUseCase @Inject constructor(
    private val source: ConnectionStateSource
) {
    /** Test convenience: plain BT-only view over a bare controller. */
    constructor(controller: HidController) : this(
        object : ConnectionStateSource {
            override val state: StateFlow<HidConnectionState> = controller.state
        }
    )

    operator fun invoke(): StateFlow<HidConnectionState> = source.state
}

/** Boot the HID engine (registers the combined HID app). */
class StartHidEngineUseCase @Inject constructor(
    private val controller: HidController
) {
    operator fun invoke() = controller.start()
}

/** Shut the HID engine down completely. */
class StopHidEngineUseCase @Inject constructor(
    private val controller: HidController
) {
    operator fun invoke() = controller.stop()
}

/** Connect to a host by MAC address. */
class ConnectDeviceUseCase @Inject constructor(
    private val controller: HidController
) {
    operator fun invoke(address: String) {
        // Input validation: a malformed MAC must never reach the framework.
        val mac = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
        if (mac.matches(address)) controller.connectTo(address)
    }
}

/** User-initiated disconnect. */
class DisconnectDeviceUseCase @Inject constructor(
    private val controller: HidController
) {
    operator fun invoke() = controller.disconnect()
}

/** List bonded devices for pickers. */
class GetBondedDevicesUseCase @Inject constructor(
    private val controller: HidController
) {
    operator fun invoke(): List<RemoteDevice> = controller.bondedDevices()
}

/** Send any HID input action (keyboard/mouse/media/system/gamepad). */
class SendHidActionUseCase @Inject constructor(
    private val controller: HidController,
    private val recorder: com.aeropad.remote.domain.MacroRecorder
) {
    operator fun invoke(action: HidAction) {
        // Macro recorder taps the pipeline: captures recordable actions
        // from EVERY screen while armed; zero overhead when idle.
        recorder.capture(action)
        controller.send(action)
    }
}


class UnpairDeviceUseCase @javax.inject.Inject constructor(
    private val controller: com.aeropad.remote.domain.HidController
) {
    operator fun invoke(address: String): Boolean = controller.unpair(address)
}
