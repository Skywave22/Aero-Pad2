package com.aeropad.remote.features

import com.aeropad.remote.domain.SetupDoctor
import com.aeropad.remote.domain.SetupDoctor.Fix
import com.aeropad.remote.domain.SetupDoctor.Level
import com.aeropad.remote.domain.SetupDoctor.SetupInputs
import com.aeropad.remote.domain.SetupDoctor.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F1 — Setup Doctor decision table. */
class SetupDoctorTest {

    private fun ids(input: SetupInputs) =
        SetupDoctor.diagnose(input).findings.map { it.id }

    @Test
    fun `healthy connected link is READY`() {
        val r = SetupDoctor.diagnose(
            SetupInputs(
                connected = true,
                hostName = "DESKTOP-1",
                batteryPercent = 80,
                sendTotal = 100,
                sendFailures = 0,
                recentMaxSendUs = 800,
                rssiDbm = -50,
                extendedDescriptor = true
            )
        )
        assertEquals(Verdict.READY, r.verdict)
        assertTrue(r.findings.any { it.level == Level.OK })
        assertTrue(r.findings.none { it.level == Level.BLOCKER })
    }

    @Test
    fun `bluetooth off is a blocker and comes first`() {
        val r = SetupDoctor.diagnose(SetupInputs(bluetoothOn = false))
        assertEquals(Verdict.BLOCKED, r.verdict)
        assertEquals("bt-off", r.findings.first().id)
        assertEquals(Fix.BLUETOOTH, r.findings.first().fix)
    }

    @Test
    fun `missing permissions blocks`() {
        val r = SetupDoctor.diagnose(SetupInputs(permissionsGranted = false))
        assertEquals(Verdict.BLOCKED, r.verdict)
        assertTrue(r.findings.any { it.id == "perms" && it.fix == Fix.PERMISSIONS })
    }

    @Test
    fun `unsupported rom surfaces the real reason`() {
        val r = SetupDoctor.diagnose(SetupInputs(hidUnsupportedReason = "profile refused"))
        val f = r.findings.first { it.id == "hid-unsupported" }
        assertEquals(Level.BLOCKER, f.level)
        assertTrue(f.detail.contains("profile refused"))
    }

    @Test
    fun `blockers sort above warnings and info`() {
        val r = SetupDoctor.diagnose(
            SetupInputs(
                bluetoothOn = false,
                batterySaverOn = true,
                sendTotal = 10,
                sendFailures = 5
            )
        )
        val levels = r.findings.map { it.level }
        val firstWarning = levels.indexOfFirst { it == Level.WARNING }
        val firstBlocker = levels.indexOfFirst { it == Level.BLOCKER }
        assertTrue(firstBlocker < firstWarning)
        assertFalse(levels.drop(firstWarning).contains(Level.BLOCKER))
    }

    @Test
    fun `failed reports are reported but not a blocker`() {
        val r = SetupDoctor.diagnose(
            SetupInputs(connected = true, sendTotal = 1000, sendFailures = 3)
        )
        assertEquals(Verdict.NEEDS_ATTENTION, r.verdict)
        val f = r.findings.first { it.id == "send-failures" }
        assertEquals(Level.WARNING, f.level)
        assertTrue(f.detail.contains(java.lang.String.format(java.util.Locale.getDefault(), "%.1f%%", 0.3)))
    }

    @Test
    fun `slow sends only matter while connected`() {
        val slow = SetupInputs(connected = true, recentMaxSendUs = 50_000)
        assertTrue(ids(slow).contains("slow-sends"))
        assertFalse(ids(slow.copy(connected = false)).contains("slow-sends"))
    }

    @Test
    fun `no failures means no failure finding`() {
        assertFalse(ids(SetupInputs(connected = true, sendTotal = 500)).contains("send-failures"))
    }

    @Test
    fun `repeated disconnects and weak rssi both warn`() {
        val r = SetupDoctor.diagnose(
            SetupInputs(connected = true, disconnects = 3, rssiDbm = -90)
        )
        assertTrue(r.findings.any { it.id == "unstable" })
        assertTrue(r.findings.any { it.id == "weak-rssi" })
        assertEquals(Verdict.NEEDS_ATTENTION, r.verdict)
    }

    @Test
    fun `unknown rssi is never treated as weak`() {
        assertFalse(ids(SetupInputs(connected = true, rssiDbm = null)).contains("weak-rssi"))
        assertFalse(ids(SetupInputs(connected = true, rssiDbm = -40)).contains("weak-rssi"))
    }

    @Test
    fun `battery saver and low battery warn with battery fix`() {
        val r = SetupDoctor.diagnose(SetupInputs(connected = true, batterySaverOn = true))
        assertEquals(Fix.BATTERY, r.findings.first { it.id == "battery-saver" }.fix)

        val low = SetupDoctor.diagnose(SetupInputs(connected = true, batteryPercent = 9))
        assertTrue(low.findings.any { it.id == "low-battery" })
    }

    @Test
    fun `unknown battery is not low battery`() {
        assertFalse(ids(SetupInputs(connected = true, batteryPercent = null)).contains("low-battery"))
    }

    @Test
    fun `not connected shows pairing help and no OK line`() {
        val r = SetupDoctor.diagnose(SetupInputs())
        assertEquals(Verdict.NOT_CONNECTED, r.verdict)
        assertTrue(r.findings.any { it.id == "how-to-pair" })
        assertTrue(r.findings.none { it.level == Level.OK })
    }

    @Test
    fun `reconnecting is informational only`() {
        val r = SetupDoctor.diagnose(SetupInputs(reconnecting = true))
        assertEquals(Verdict.NOT_CONNECTED, r.verdict)
        assertEquals(Level.INFO, r.findings.first { it.id == "reconnecting" }.level)
    }

    @Test
    fun `legacy descriptor is informational, extended hides it`() {
        assertTrue(ids(SetupInputs()).contains("legacy-descriptor"))
        assertFalse(ids(SetupInputs(extendedDescriptor = true)).contains("legacy-descriptor"))
    }

    @Test
    fun `every fix is an actionable place, never NONE`() {
        val r = SetupDoctor.diagnose(
            SetupInputs(
                bluetoothOn = false,
                permissionsGranted = false,
                hidUnsupportedReason = "x",
                errorMessage = "boom",
                batterySaverOn = true
            )
        )
        r.findings.filter { it.action.isNotEmpty() }.forEach {
            assertTrue("finding ${it.id} has text but no fix", it.fix != Fix.NONE)
        }
    }
}
