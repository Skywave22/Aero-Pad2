package com.bluepilot.remote.features

import com.bluepilot.remote.model.GamepadSnapshot
import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.HidKeys
import com.bluepilot.remote.model.MouseButton
import com.bluepilot.remote.wifi.WifiProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** AEROPAD v1.0 — WiFi wire protocol: exact message mapping + security. */
class WifiProtocolTest {

    @Test
    fun `key tap maps to down plus up`() {
        val msgs = WifiProtocol.fromAction(HidAction.KeyTap(HidKeys.ENTER))
        assertEquals(2, msgs.size)
        assertEquals("kd", msgs[0].t)
        assertEquals(HidKeys.ENTER.toInt(), msgs[0].key)
        assertEquals("ku", msgs[1].t)
    }

    @Test
    fun `mouse actions map to correct types and buttons`() {
        assertEquals("mm", WifiProtocol.fromAction(HidAction.MouseMove(5, -3))[0].t)
        val click = WifiProtocol.fromAction(HidAction.MouseClick(MouseButton.RIGHT))[0]
        assertEquals("mc", click.t)
        assertEquals(2, click.btn)
        assertEquals(2, WifiProtocol.fromAction(HidAction.MouseDoubleClick(MouseButton.LEFT)).size)
        assertEquals("ms", WifiProtocol.fromAction(HidAction.MouseScroll(-2))[0].t)
    }

    @Test
    fun `gamepad snapshot carries full state`() {
        val snap = GamepadSnapshot(buttons = 0b101, hat = 2, leftX = 0.5f, leftY = -1f)
        val m = WifiProtocol.fromSnapshot(snap)
        assertEquals("gp", m.t)
        assertEquals(0b101, m.buttons)
        assertEquals(2, m.hat)
        assertEquals(0.5f, m.lx!!, 1e-4f)
        assertEquals(-1f, m.ly!!, 1e-4f)
    }

    @Test
    fun `encode decode round trip and junk is null`() {
        val m = WifiProtocol.WifiMessage(t = "txt", text = "hello world")
        assertEquals(m, WifiProtocol.decode(WifiProtocol.encode(m)))
        assertNull(WifiProtocol.decode("{not json"))
        assertNull(WifiProtocol.decode(""))
    }

    @Test
    fun `pin proof matches receiver formula and differs per nonce`() {
        val p1 = WifiProtocol.pinProof("123456", "nonceA")
        assertEquals(WifiProtocol.sha256Hex("123456:nonceA"), p1)
        assertNotEquals(p1, WifiProtocol.pinProof("123456", "nonceB"))
        assertNotEquals(p1, WifiProtocol.pinProof("654321", "nonceA"))
    }

    @Test
    fun `keystream is deterministic and xor is symmetric`() {
        val k1 = WifiProtocol.keystream("111111", "n", 100)
        val k2 = WifiProtocol.keystream("111111", "n", 100)
        assertTrue(k1.contentEquals(k2))
        val data = "secret payload".toByteArray()
        val enc = WifiProtocol.xor(data, k1, 0)
        assertTrue(data.contentEquals(WifiProtocol.xor(enc, k1, 0)))
        assertTrue(!data.contentEquals(enc))
    }

    @Test
    fun `qr payload parses valid and rejects junk`() {
        val ok = WifiProtocol.parseQrPayload("aeropad://192.168.1.20:48653/123456")
        assertEquals(Triple("192.168.1.20", 48653, "123456"), ok)
        assertNull(WifiProtocol.parseQrPayload("aeropad://host:99999/123456"))
        assertNull(WifiProtocol.parseQrPayload("http://192.168.1.20:1/123456"))
        assertNull(WifiProtocol.parseQrPayload("aeropad://ip:80/12345"))
        assertNull(WifiProtocol.parseQrPayload(""))
    }

    @Test
    fun `random pin is always six digits`() {
        repeat(50) {
            val pin = WifiProtocol.randomPin()
            assertEquals(6, pin.length)
            assertTrue(pin.all { it.isDigit() })
        }
    }
}
