package com.bluepilot.remote.wifi

import com.bluepilot.remote.model.GamepadSnapshot
import com.bluepilot.remote.model.HidAction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * AEROPAD v1.0 — WiFi LAN control protocol (pure, unit-tested).
 *
 * Wire format: newline-delimited JSON over TCP. One [WifiMessage] per
 * line. Deliberately simple so the companion receiver can be a ~100-line
 * Python script on any OS.
 *
 * Security: after the TCP connect, the phone must send HELLO carrying a
 * SHA-256 proof of the 6-digit PIN displayed by the receiver
 * (proof = sha256(pin + ":" + nonce), nonce chosen by receiver in its
 * WELCOME line). All subsequent traffic is obfuscated with a keystream
 * derived from the PIN+nonce (XOR stream via SHA-256 counter mode) —
 * honest labeling: this is lightweight LAN-grade protection against
 * casual snooping, not TLS. Flagged in the UI as such.
 */
object WifiProtocol {

    const val SERVICE_TYPE = "_aeropad._tcp."
    const val DEFAULT_PORT = 48653
    const val PROTOCOL_VERSION = 1

    val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ------------------------------------------------------------------
    // Messages
    // ------------------------------------------------------------------

    @Serializable
    data class WifiMessage(
        val t: String,                 // type tag
        val v: Int = PROTOCOL_VERSION,
        // HELLO
        val proof: String? = null,
        val deviceName: String? = null,
        // key/text
        val key: Int? = null,
        val mods: Int? = null,
        val text: String? = null,
        // mouse
        val dx: Int? = null,
        val dy: Int? = null,
        val btn: Int? = null,
        val scroll: Int? = null,
        // gamepad
        val buttons: Int? = null,
        val hat: Int? = null,
        val lx: Float? = null,
        val ly: Float? = null,
        val rx: Float? = null,
        val ry: Float? = null,
        // ping
        val seq: Long? = null,
        val echo: Long? = null
    )

    fun encode(m: WifiMessage): String = json.encodeToString(WifiMessage.serializer(), m)

    fun decode(line: String): WifiMessage? =
        runCatching { json.decodeFromString(WifiMessage.serializer(), line) }.getOrNull()

    // ------------------------------------------------------------------
    // HidAction → wire messages (the transport-swap seam)
    // ------------------------------------------------------------------

    /** Maps a HidAction to protocol messages (some actions = 2 messages). */
    fun fromAction(action: HidAction): List<WifiMessage> = when (action) {
        is HidAction.KeyTap -> listOf(
            WifiMessage(t = "kd", key = action.key.toInt(), mods = action.modifiers.toInt()),
            WifiMessage(t = "ku")
        )
        is HidAction.KeyDown -> listOf(
            WifiMessage(t = "kd", key = action.key.toInt(), mods = action.modifiers.toInt())
        )
        is HidAction.KeyRelease -> listOf(WifiMessage(t = "ku"))
        is HidAction.TypeText -> listOf(WifiMessage(t = "txt", text = action.text))
        is HidAction.MouseMove -> listOf(WifiMessage(t = "mm", dx = action.dx, dy = action.dy))
        is HidAction.MouseClick -> listOf(
            WifiMessage(t = "mc", btn = action.button.mask.toInt())
        )
        is HidAction.MouseDoubleClick -> listOf(
            WifiMessage(t = "mc", btn = action.button.mask.toInt()),
            WifiMessage(t = "mc", btn = action.button.mask.toInt())
        )
        is HidAction.MouseDown -> listOf(WifiMessage(t = "md", btn = action.button.mask.toInt()))
        is HidAction.MouseUp -> listOf(WifiMessage(t = "mu", btn = action.button.mask.toInt()))
        is HidAction.MouseScroll -> listOf(WifiMessage(t = "ms", scroll = action.amount))
        is HidAction.MouseDrag -> listOf(
            WifiMessage(t = "mm", dx = action.dx, dy = action.dy, btn = action.button.mask.toInt())
        )
        is HidAction.MediaTap -> listOf(WifiMessage(t = "media", key = action.usage))
        is HidAction.SystemTap -> listOf(WifiMessage(t = "sys", key = action.bits.toInt()))
        is HidAction.GamepadUpdate -> listOf(fromSnapshot(action.snapshot))
    }

    fun fromSnapshot(s: GamepadSnapshot): WifiMessage = WifiMessage(
        t = "gp", buttons = s.buttons, hat = s.hat,
        lx = s.leftX, ly = s.leftY, rx = s.rightX, ry = s.rightY
    )

    // ------------------------------------------------------------------
    // PIN handshake + keystream (pure crypto helpers)
    // ------------------------------------------------------------------

    fun sha256Hex(input: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** proof = sha256(pin:nonce) — receiver validates the same way. */
    fun pinProof(pin: String, nonce: String): String = sha256Hex("$pin:$nonce")

    /**
     * Deterministic keystream: block i = sha256(pin:nonce:i). XOR applied
     * symmetric on both sides after handshake.
     */
    fun keystream(pin: String, nonce: String, length: Int): ByteArray {
        val out = ByteArray(length)
        var offset = 0
        var block = 0
        val md = java.security.MessageDigest.getInstance("SHA-256")
        while (offset < length) {
            val digest = md.digest("$pin:$nonce:$block".toByteArray(Charsets.UTF_8))
            val n = minOf(digest.size, length - offset)
            System.arraycopy(digest, 0, out, offset, n)
            offset += n
            block++
        }
        return out
    }

    fun xor(data: ByteArray, key: ByteArray, streamOffset: Long): ByteArray {
        val out = ByteArray(data.size)
        for (i in data.indices) {
            out[i] = (data[i].toInt() xor key[((streamOffset + i) % key.size).toInt()].toInt()).toByte()
        }
        return out
    }

    /** 6-digit PIN generator for the receiver side / QR payload parsing. */
    fun randomPin(): String = (100000..999999).random().toString()

    /** QR payload: "aeropad://host:port/pin". Returns (host, port, pin) or null. */
    fun parseQrPayload(payload: String): Triple<String, Int, String>? {
        val m = Regex("^aeropad://([0-9a-zA-Z_.-]+):(\\d{1,5})/(\\d{6})$").find(payload.trim())
            ?: return null
        val port = m.groupValues[2].toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        return Triple(m.groupValues[1], port, m.groupValues[3])
    }
}
