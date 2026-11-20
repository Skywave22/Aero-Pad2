package com.aeropad.remote.features

import com.aeropad.remote.data.layout.JsonDocumentTooLarge
import com.aeropad.remote.data.layout.readJsonDocument
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonDocumentTest {
    @Test
    fun `reads UTF-8 without requiring a provider to report its size`() {
        val payload = "{\"name\":\"AéroPad\"}"
        assertEquals(payload, payload.byteInputStream().readJsonDocument())
    }

    @Test
    fun `accepts the maximum size`() {
        val payload = "x".repeat(1024 * 1024)
        assertEquals(payload, payload.byteInputStream().readJsonDocument())
    }

    @Test(expected = JsonDocumentTooLarge::class)
    fun `rejects a stream that is one byte too large`() {
        ByteArray(1024 * 1024 + 1).inputStream().readJsonDocument()
    }
}
