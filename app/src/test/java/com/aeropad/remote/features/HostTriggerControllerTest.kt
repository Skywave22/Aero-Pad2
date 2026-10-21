package com.aeropad.remote.features

import com.aeropad.remote.data.hosts.HostProfile
import com.aeropad.remote.domain.HostTriggerController
import com.aeropad.remote.model.HidConnectionState
import com.aeropad.remote.model.RemoteDevice
import org.junit.Assert.assertEquals
import org.junit.Test

/** F6 — connect / disconnect macro triggers. */
class HostTriggerControllerTest {

    private val tv = RemoteDevice(address = "AA:TV", name = "Living Room TV")
    private val pc = RemoteDevice(address = "BB:PC", name = "Desktop")

    private fun profiles() = mapOf(
        "AA:TV" to HostProfile(id = "AA:TV", label = "TV", transport = "BT", address = "AA:TV", onConnectMacroId = 11, onDisconnectMacroId = 12),
        "BB:PC" to HostProfile(id = "BB:PC", label = "PC", transport = "BT", address = "BB:PC", onConnectMacroId = 21, onDisconnectMacroId = 0)
    )

    private class Recorder {
        val played = mutableListOf<Long>()
        fun play(id: Long) { played += id }
    }

    private fun controller(rec: Recorder) = HostTriggerController(
        profileFor = { addr -> profiles()[addr] },
        playMacro = { rec.play(it) }
    )

    @Test
    fun `fires connect macro once on the connect edge`() {
        val rec = Recorder()
        val c = controller(rec)

        c.onState(HidConnectionState.Idle)
        c.onState(HidConnectionState.Connecting(tv))
        assertEquals(emptyList<Long>(), rec.played)

        c.onState(HidConnectionState.Connected(tv))
        assertEquals(listOf(11L), rec.played)

        // Still the same host — repeated Connected states must not re-fire.
        c.onState(HidConnectionState.Connected(tv))
        c.onState(HidConnectionState.Connected(tv))
        assertEquals(listOf(11L), rec.played)
    }

    @Test
    fun `fires disconnect macro when returning to idle`() {
        val rec = Recorder()
        val c = controller(rec)
        c.onState(HidConnectionState.Connected(tv))
        c.onState(HidConnectionState.Idle)
        assertEquals(listOf(11L, 12L), rec.played)
    }

    @Test
    fun `missing disconnect macro fires nothing`() {
        val rec = Recorder()
        val c = controller(rec)
        c.onState(HidConnectionState.Connected(pc))
        c.onState(HidConnectionState.Idle)
        assertEquals(listOf(21L), rec.played)
    }

    @Test
    fun `transitional error does not count as a disconnect`() {
        val rec = Recorder()
        val c = controller(rec)
        c.onState(HidConnectionState.Connected(tv))
        c.onState(HidConnectionState.Error("link lost"))
        c.onState(HidConnectionState.Connecting(tv))
        c.onState(HidConnectionState.Connected(tv))
        assertEquals("only the connect macro should have fired", listOf(11L), rec.played)
    }

    @Test
    fun `switching hosts fires disconnect then connect`() {
        val rec = Recorder()
        val c = controller(rec)
        c.onState(HidConnectionState.Connected(tv))
        c.onState(HidConnectionState.Connected(pc))
        assertEquals(listOf(11L, 21L), rec.played)
    }

    @Test
    fun `unknown host fires nothing but still tracks state`() {
        val rec = Recorder()
        val c = controller(rec)
        val stranger = RemoteDevice("CC:GONE", "Old Phone")
        c.onState(HidConnectionState.Connected(stranger))
        c.onState(HidConnectionState.Idle)
        assertEquals(emptyList<Long>(), rec.played)
        assertEquals(null, c.connectedAddress)
    }

    @Test
    fun `idle without a prior connect fires nothing`() {
        val rec = Recorder()
        val c = controller(rec)
        c.onState(HidConnectionState.Idle)
        c.onState(HidConnectionState.BluetoothDisabled)
        assertEquals(emptyList<Long>(), rec.played)
    }

    @Test
    fun `zero macro ids are treated as unset`() {
        val rec = Recorder()
        val c = HostTriggerController(
            profileFor = { HostProfile(id = it, label = it, transport = "BT", address = it) },
            playMacro = { rec.play(it) }
        )
        c.onState(HidConnectionState.Connected(tv))
        c.onState(HidConnectionState.Idle)
        assertEquals(emptyList<Long>(), rec.played)
    }

    @Test
    fun `reconnect to the same host fires connect again after a real disconnect`() {
        val rec = Recorder()
        val c = controller(rec)
        c.onState(HidConnectionState.Connected(tv))
        c.onState(HidConnectionState.Idle)
        c.onState(HidConnectionState.Connected(tv))
        assertEquals(listOf(11L, 12L, 11L), rec.played)
    }

    @Test
    fun `a throwing macro engine does not wedge the controller`() {
        var calls = 0
        val c = HostTriggerController(
            profileFor = { profiles()[it] },
            playMacro = { calls++; throw IllegalStateException("boom") }
        )
        // fire() swallows failures; these must not throw out of onState.
        c.onState(HidConnectionState.Connected(tv))
        c.onState(HidConnectionState.Idle)
        assertEquals(2, calls)
        assertEquals(null, c.connectedAddress)
    }
}
