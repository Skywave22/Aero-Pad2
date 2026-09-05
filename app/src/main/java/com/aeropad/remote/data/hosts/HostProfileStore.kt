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
 * V2 MATRIX 4 — saved host profiles (multi-host quick-switch).
 */
@Serializable
data class HostProfile(
    val id: String,
    val label: String,
    /** "BT". */
    val transport: String,
    /** BT: MAC address. */
    val address: String,
    val lastUsedAt: Long = 0
) {
    companion object {
        const val LABEL_MAX = 30
        const val TRANSPORT_BT = "BT"
    }

    fun sanitized(): HostProfile = copy(
        label = label.take(LABEL_MAX).ifBlank { address },
        transport = TRANSPORT_BT
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
        }.getOrDefault(emptyList())
    }

    /**
     * Upsert by (transport, address): same machine updates in place
     * (keeping its position fresh at the front), new ones prepend.
     * Capped at [MAX_PROFILES] — oldest drops off.
     */
    fun upsert(list: List<HostProfile>, profile: HostProfile): List<HostProfile> {
        val clean = profile.sanitized()
        val rest = list.filterNot {
            it.transport == clean.transport && it.address == clean.address
        }
        return (listOf(clean) + rest).take(MAX_PROFILES)
    }

    fun remove(list: List<HostProfile>, id: String): List<HostProfile> =
        list.filterNot { it.id == id }
}

/**
 * V2 M4 — domain-facing seam so ViewModels stay unit-testable with a fake
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

    override fun remove(id: String) = persist(HostProfileCodec.remove(_profiles.value, id))
}

