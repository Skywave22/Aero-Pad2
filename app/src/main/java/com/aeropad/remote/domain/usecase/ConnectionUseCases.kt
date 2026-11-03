package com.aeropad.remote.domain.usecase

import com.aeropad.remote.domain.HidController
import com.aeropad.remote.data.tv.WifiTvSession
import com.aeropad.remote.model.HidAction
import com.aeropad.remote.model.HidConnectionState
import com.aeropad.remote.model.RemoteDevice
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * UseCase layer (Clean Architecture).
 *
 * Each class is one intention. ViewModels compose these instead of talking
 * to the engine directly, so business rules have a single home and tests
 * can inject fakes trivially.
 */

/** Observe the connection state machine. */
class ObserveConnectionUseCase @Inject constructor(
    private val controller: HidController
) {
    operator fun invoke(): StateFlow<HidConnectionState> = controller.state
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
    /** Incremental IME edit fragment; Wi-Fi TV sessions accumulate it into a live search string. */
    fun typeLive(text: String) {
        if (text.isEmpty()) return
        val action = HidAction.TypeText(text)
        recorder.capture(action)
        if (!WifiTvSession.typeLiveChunk(text)) controller.send(action)
    }

    operator fun invoke(action: HidAction) {
        // Macro recorder taps the pipeline: captures recordable actions
        // from EVERY screen while armed; zero overhead when idle.
        recorder.capture(action)
        // When a Wi-Fi TV target is selected, use the TV protocol for the
        // subset it understands. HID-only reports (gamepad, touch, held keys)
        // stay on Bluetooth; never pretend the TV accepts a full HID report.
        when (WifiTvSession.route(action, bluetoothAvailable = controller.state.value.isConnected)) {
            WifiTvSession.RouteResult.ROUTED_TO_TV -> Unit
            WifiTvSession.RouteResult.NO_TV_TARGET -> controller.send(action)
            WifiTvSession.RouteResult.UNSUPPORTED_BY_TV -> {
                if (controller.state.value.isConnected) controller.send(action)
            }
        }
    }
}
