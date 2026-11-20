package com.aeropad.remote.features

import com.aeropad.remote.domain.dashboardConnection
import com.aeropad.remote.model.HidConnectionState
import com.aeropad.remote.model.RemoteDevice
import org.junit.Assert.assertEquals
import org.junit.Test

class StudioConnectionTest {
    private val host = RemoteDevice("00:00:00:00:00:00", "Desk computer")

    @Test
    fun `permission loss takes precedence over a stale connected state`() {
        val presentation = dashboardConnection(HidConnectionState.Connected(host), hasPermissions = false)
        assertEquals("Offline", presentation.badge)
        assertEquals("Pair device", presentation.action)
        assertEquals(null, presentation.detail)
    }

    @Test
    fun `live and pending connections have different actions`() {
        val connected = dashboardConnection(HidConnectionState.Connected(host), hasPermissions = true)
        assertEquals("Connected", connected.badge)
        assertEquals("Desk computer", connected.detail)
        assertEquals("Manage device", connected.action)
        val pending = dashboardConnection(HidConnectionState.Connecting(host), hasPermissions = true)
        assertEquals("Connecting", pending.badge)
        assertEquals("View connection", pending.action)
    }

    @Test
    fun `recovery actions reflect what actually blocked the link`() {
        assertEquals("Pair device", dashboardConnection(HidConnectionState.Idle, true).action)
        assertEquals("Turn on Bluetooth", dashboardConnection(HidConnectionState.BluetoothDisabled, true).action)
        assertEquals("View details", dashboardConnection(HidConnectionState.HidUnsupported("Not supported"), true).action)
        assertEquals("View details", dashboardConnection(HidConnectionState.Error("Timed out"), true).action)
    }
}
