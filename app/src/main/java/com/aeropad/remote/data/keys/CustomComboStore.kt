package com.aeropad.remote.data.keys

import android.content.Context
import com.aeropad.remote.model.keys.NamedCombo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's own shortcuts ("Close window", "Ctrl+Shift+Esc", …).
 *
 * Same defensive file store as host profiles: reads never throw, a corrupt or
 * hand-edited file yields an empty list instead of a crash on launch, and
 * writes go through a temp file so a kill mid-write cannot truncate the real
 * one.
 */
@Singleton
class CustomComboStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun file() = java.io.File(context.filesDir, "custom_combos.json")

    private val _combos = MutableStateFlow(load())
    val combos: StateFlow<List<NamedCombo>> = _combos.asStateFlow()

    private fun load(): List<NamedCombo> = runCatching {
        val f = file().takeIf { it.exists() } ?: return emptyList()
        json.decodeFromString(ListSerializer(NamedCombo.serializer()), f.readText())
            .filter { it.isValid() }
            .map { it.sanitized() }
    }.onFailure { Timber.w(it, "custom combos unreadable — starting empty") }
        .getOrDefault(emptyList())

    private fun persist(list: List<NamedCombo>) {
        _combos.value = list
        runCatching {
            val tmp = java.io.File(context.filesDir, "custom_combos.tmp")
            tmp.writeText(json.encodeToString(ListSerializer(NamedCombo.serializer()), list))
            tmp.renameTo(file())
        }.onFailure { Timber.e(it, "custom combos save failed") }
    }

    /**
     * Create or replace a combo. A combo with the same *name* is replaced
     * (users expect re-saving "Copy" to update it rather than create a
     * duplicate they can't tell apart).
     */
    fun save(name: String, key: Int, mods: Int): NamedCombo? {
        val clean = NamedCombo(
            id = UUID.randomUUID().toString(),
            name = name,
            key = key,
            mods = mods
        ).sanitized()
        if (!clean.isValid()) return null

        val withoutSameName = _combos.value.filterNot {
            it.name.equals(clean.name, ignoreCase = true) && clean.name.isNotBlank()
        }
        persist((listOf(clean) + withoutSameName).take(NamedCombo.MAX_COMBOS))
        return clean
    }

    fun delete(id: String) = persist(_combos.value.filterNot { it.id == id })

    fun clear() = persist(emptyList())
}
