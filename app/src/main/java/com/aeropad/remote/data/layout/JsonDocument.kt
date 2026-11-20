package com.aeropad.remote.data.layout

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/** SAF providers need not report a reliable size. Bound the bytes read from the stream itself. */
internal class JsonDocumentTooLarge : IOException("JSON file is larger than 1 MiB")

internal fun InputStream.readJsonDocument(): String {
    val limit = 1024 * 1024
    val buffer = ByteArray(8192)
    val output = ByteArrayOutputStream()
    while (true) {
        // Read at most one byte beyond the limit to distinguish exact-size files from oversized ones.
        val count = read(buffer, 0, minOf(buffer.size, limit - output.size() + 1))
        if (count < 0) break
        if (output.size() + count > limit) throw JsonDocumentTooLarge()
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}
