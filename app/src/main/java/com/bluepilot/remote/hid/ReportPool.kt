package com.bluepilot.remote.hid

import com.bluepilot.remote.model.GamepadSnapshot

/**
 * V2 PART A — zero-allocation report buffers for the HOT paths
 * (mouse move + gamepad update; fired up to ~100x/sec).
 *
 * Thread-confinement contract: ONLY the single "BluePilot-HID" thread
 * touches these buffers (that thread serializes every send), so reuse is
 * safe without locks. Result: zero new allocations per report -> zero GC
 * pressure during rapid input sequences.
 *
 * HONESTY FLAG: BluetoothHidDevice.sendReport() takes ByteArray, so the
 * framework may copy internally — "zero-copy to radio" is impossible on
 * Android. What this pool guarantees is zero ALLOCATION on our side,
 * which is what actually prevents GC-induced jank.
 *
 * The pure HidReportBuilder stays untouched (tests + low-frequency paths
 * keep using it — ZERO behavior change).
 */
object ReportPool {

    private val mouseBuf = ByteArray(HidDescriptors.SIZE_MOUSE)
    private val gamepadBuf = ByteArray(HidDescriptors.SIZE_GAMEPAD)

    /** Fill + return the reusable mouse report (same bytes as builder). */
    fun mouseInto(buttons: Byte, dx: Int, dy: Int, wheel: Int): ByteArray {
        mouseBuf[0] = buttons
        mouseBuf[1] = dx.coerceIn(-127, 127).toByte()
        mouseBuf[2] = dy.coerceIn(-127, 127).toByte()
        mouseBuf[3] = wheel.coerceIn(-127, 127).toByte()
        return mouseBuf
    }

    /** Fill + return the reusable gamepad report (same bytes as builder). */
    fun gamepadInto(s: GamepadSnapshot): ByteArray {
        val buttons = s.buttons and 0xFFFF
        gamepadBuf[0] = (buttons and 0xFF).toByte()
        gamepadBuf[1] = ((buttons shr 8) and 0xFF).toByte()
        gamepadBuf[2] = s.hat.coerceIn(0, 8).toByte()
        gamepadBuf[3] = HidReportBuilder.axisToByte(s.leftX)
        gamepadBuf[4] = HidReportBuilder.axisToByte(s.leftY)
        gamepadBuf[5] = HidReportBuilder.axisToByte(s.rightX)
        gamepadBuf[6] = HidReportBuilder.axisToByte(s.rightY)
        return gamepadBuf
    }
}
