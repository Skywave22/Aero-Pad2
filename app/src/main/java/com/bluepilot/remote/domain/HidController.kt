package com.bluepilot.remote.domain

import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.HidConnectionState
import com.bluepilot.remote.model.RemoteDevice
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-facing contract of the HID engine.
 *
 * ViewModels and UseCases depend on THIS, never on the concrete engine —
 * which keeps the whole app unit-testable with a fake controller and keeps
 * Android Bluetooth types out of the domain layer.
 */
interface HidController {

    /** Single source of truth for connection state. */
    val state: StateFlow<HidConnectionState>

    /** Boot the engine (register HID app). Safe to call repeatedly. */
    fun start()

    /** Tear everything down (unregister + release proxy). */
    fun stop()

    /** Connect to a host by MAC address (bonded or not — pairing may be triggered). */
    fun connectTo(address: String)

    /** User-initiated disconnect (suppresses auto-reconnect). */
    fun disconnect()

    /** Currently bonded devices, never throws. */
    fun bondedDevices(): List<RemoteDevice>

    /** Fire-and-forget input action. */
    fun send(action: HidAction)
}

/** Domain-facing contract for permission checks (testable without Context). */
interface PermissionChecker {
    fun requiredBluetoothPermissions(): List<String>
    fun optionalPermissions(): List<String>
    fun hasAllBluetoothPermissions(): Boolean
    fun missingPermissions(): List<String>
}

/** Domain-facing contract for classic Bluetooth discovery. */
interface NearbyScanner {
    /** Cold flow of discovered devices; cancelling the collection stops discovery. */
    fun scan(): kotlinx.coroutines.flow.Flow<RemoteDevice>
}
