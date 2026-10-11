package com.bluepilot.remote.data.history

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AEROPAD v1.0 #41 — connection history log (real sessions only).
 *
 * Entries are appended from REAL connection state transitions (BT and
 * WiFi). File-based JSON (same zero-migration pattern as gamepad
 * versioning); corrupt file = empty history, never a crash. Cap 50.
 */
@Serializable
data class ConnectionSession(
    val transport: String,          // "BLUETOOTH" | "WIFI"
    val hostName: String,
    val startedAt: Long,
    val endedAt: Long = 0L,         // 0 = still open when written
    val disconnectReason: String = ""
)

object HistoryCodec {
    const val MAX = 50
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(list: List<ConnectionSession>): String =
        json.encodeToString(ListSerializer(ConnectionSession.serializer()), list)

    fun decode(raw: String?): List<ConnectionSession> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(ConnectionSession.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    /** Newest first, capped. */
    fun push(list: List<ConnectionSession>, s: ConnectionSession): List<ConnectionSession> =
        (listOf(s) + list).take(MAX)

    /** Close the most recent open session for [transport]. */
    fun closeOpen(list: List<ConnectionSession>, transport: String, endedAt: Long, reason: String):
        List<ConnectionSession> {
        val idx = list.indexOfFirst { it.transport == transport && it.endedAt == 0L }
        if (idx < 0) return list
        return list.toMutableList().also {
            it[idx] = it[idx].copy(endedAt = endedAt, disconnectReason = reason)
        }
    }
}

@Singleton
class ConnectionHistoryStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun file() = java.io.File(context.filesDir, "connection_history.json")

    @Synchronized
    fun all(): List<ConnectionSession> =
        runCatching { HistoryCodec.decode(file().takeIf { it.exists() }?.readText()) }
            .getOrDefault(emptyList())

    @Synchronized
    fun onConnected(transport: String, hostName: String) {
        runCatching {
            file().writeText(HistoryCodec.encode(HistoryCodec.push(
                all(), ConnectionSession(transport, hostName, System.currentTimeMillis()))))
        }
    }

    @Synchronized
    fun onDisconnected(transport: String, reason: String) {
        runCatching {
            file().writeText(HistoryCodec.encode(HistoryCodec.closeOpen(
                all(), transport, System.currentTimeMillis(), reason)))
        }
    }
}
