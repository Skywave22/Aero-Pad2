package com.aeropad.remote.data.hosts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Saved host profiles (multi-host quick-switch) — Bluetooth only.
 *
 * One entry per machine the user connects to (MAC address). Persisted as
 * JSON in app files (same defensive pattern as ConnectionHistoryStore:
 * reads never throw, corrupt file = empty list, writes are atomic-ish via
 * temp file).
 *
 * AeroPad is a pure Bluetooth HID remote: legacy WiFi entries that may
 * still exist in old JSON files are dropped on decode.
 */
@Serializable
data class HostProfile(
    val id: String,
    val label: String,
    /** Always "BT" — kept as a field for storage compatibility. */
    val transport: String,
    /** Bluetooth MAC address. */
    val address: String,
    val port: Int = 0,
    /** Legacy field (unused, kept so old JSON decodes cleanly). */
    @SerialName("p") val obfuscatedPin: String = "",
    val lastUsedAt: Long = 0
) {
    companion object {
        const val LABEL_MAX = 30
        const val TRANSPORT_BT = "BT"
        val MAC_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
    }

    fun sanitized(): HostProfile = copy(
        label = label.take(LABEL_MAX).ifBlank { address },
        transport = TRANSPORT_BT,
        port = port.coerceIn(0, 65535)
    )
}

/** Pure list logic (unit-tested). */
object HostProfileCodec {

    const val MAX_PROFILES = 12

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(list: List<HostProfile>): String =
        json.encodeToString(ListSerializer(HostProfile.serializer()), list)

    fun decode(raw: String?): List<HostProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(HostProfile.serializer()), raw)
                .map { it.sanitized() }
                // Legacy WiFi entries (non-MAC address) are dropped — the app
                // is Bluetooth-only now.
                .filter { HostProfile.MAC_REGEX.matches(it.address) }
        }.getOrDefault(emptyList())
    }

    /**
     * Upsert by address: same machine updates in place (keeping its
     * position fresh at the front), new ones prepend.
     * Capped at [MAX_PROFILES] — oldest drops off.
     */
    fun upsert(list: List<HostProfile>, profile: HostProfile): List<HostProfile> {
        val clean = profile.sanitized()
        val rest = list.filterNot { it.address == clean.address }
        return (listOf(clean) + rest).take(MAX_PROFILES)
    }

    fun remove(list: List<HostProfile>, id: String): List<HostProfile> =
        list.filterNot { it.id == id }
}

/**
 * Domain-facing seam so ViewModels stay unit-testable with a fake
 * (same pattern as HidController). Implemented by [HostProfileStore].
 */
interface HostProfiles {
    val profiles: StateFlow<List<HostProfile>>
    fun saveBt(label: String, mac: String)
    fun touch(id: String)
    fun remove(id: String)
}

@Singleton
class HostProfileStore @Inject constructor(
    @ApplicationContext private val context: Context
) : HostProfiles {
    private fun file() = java.io.File(context.filesDir, "host_profiles.json")

    private val _profiles = MutableStateFlow(load())
    override val profiles: StateFlow<List<HostProfile>> = _profiles.asStateFlow()

    private fun load(): List<HostProfile> = runCatching {
        HostProfileCodec.decode(file().takeIf { it.exists() }?.readText())
    }.getOrDefault(emptyList())

    private fun persist(list: List<HostProfile>) {
        _profiles.value = list
        runCatching {
            val tmp = java.io.File(context.filesDir, "host_profiles.tmp")
            tmp.writeText(HostProfileCodec.encode(list))
            tmp.renameTo(file())
        }.onFailure { Timber.e(it, "host profiles save failed") }
    }

    override fun saveBt(label: String, mac: String) {
        persist(
            HostProfileCodec.upsert(
                _profiles.value,
                HostProfile(
                    id = java.util.UUID.randomUUID().toString(),
                    label = label, transport = HostProfile.TRANSPORT_BT,
                    address = mac, lastUsedAt = System.currentTimeMillis()
                )
            )
        )
    }

    override fun touch(id: String) {
        val p = _profiles.value.firstOrNull { it.id == id } ?: return
        persist(HostProfileCodec.upsert(_profiles.value, p.copy(lastUsedAt = System.currentTimeMillis())))
    }

    override fun remove(id: String) {
        persist(HostProfileCodec.remove(_profiles.value, id))
    }
}
