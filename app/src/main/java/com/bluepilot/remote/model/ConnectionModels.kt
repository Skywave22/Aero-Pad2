package com.bluepilot.remote.model

/**
 * Connection domain models. HidConnectionState is a sealed class so each
 * state carries exactly the data it needs — no nullable soup.
 */

/** A Bluetooth device as the app sees it (no framework types leak upward). */
data class RemoteDevice(
    val address: String,
    val name: String,
    val isPaired: Boolean = false
)

/** The single source of truth for HID connection state. */
sealed class HidConnectionState {

    /** Bluetooth is off. */
    data object BluetoothDisabled : HidConnectionState()

    /** Required runtime permissions not granted yet. */
    data object PermissionMissing : HidConnectionState()

    /** This phone/ROM refused HID Device registration. */
    data class HidUnsupported(val reason: String) : HidConnectionState()

    /** Proxy/app registration in progress. */
    data object Initializing : HidConnectionState()

    /** Registered and ready; waiting for a host to connect (or user to pick one). */
    data object Idle : HidConnectionState()

    /** Outgoing or incoming connection in progress. */
    data class Connecting(val device: RemoteDevice) : HidConnectionState()

    /** Live HID link. */
    data class Connected(val device: RemoteDevice) : HidConnectionState()

    /** Something failed; message is user-displayable. */
    data class Error(val message: String) : HidConnectionState()

    val isConnected: Boolean get() = this is Connected
}
