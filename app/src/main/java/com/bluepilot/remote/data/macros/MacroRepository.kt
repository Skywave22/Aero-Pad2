package com.bluepilot.remote.data.macros

import com.bluepilot.remote.data.db.MacroDao
import com.bluepilot.remote.data.db.MacroEntity
import com.bluepilot.remote.model.macros.MacroSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** A stored macro with its decoded spec. */
data class Macro(
    val id: Long,
    val spec: MacroSpec
)

/**
 * Room-backed macro storage. Same defensive JSON contract as layouts:
 * decode never throws, corrupt rows are dropped, writes sanitize first.
 */
@Singleton
class MacroRepository @Inject constructor(
    private val dao: MacroDao
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    fun toJson(spec: MacroSpec): String =
        json.encodeToString(MacroSpec.serializer(), spec.sanitized())

    fun fromJson(raw: String?): MacroSpec? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(MacroSpec.serializer(), raw).sanitized() }
            .onFailure { Timber.w(it, "macro JSON decode failed") }
            .getOrNull()
    }

    fun observeAll(): Flow<List<Macro>> =
        dao.observeAll().map { entities ->
            entities.mapNotNull { entity ->
                fromJson(entity.stepsJson)?.let { Macro(entity.id, it) }
            }
        }

    suspend fun byId(id: Long): Macro? {
        val entity = dao.byId(id) ?: return null
        return fromJson(entity.stepsJson)?.let { Macro(entity.id, it) }
    }

    suspend fun save(id: Long?, spec: MacroSpec): Long {
        val clean = spec.sanitized()
        return dao.upsert(
            MacroEntity(
                id = id ?: 0,
                name = clean.name,
                stepsJson = toJson(clean)
            )
        )
    }

    suspend fun delete(id: Long) {
        dao.byId(id)?.let { dao.delete(it) }
    }
}
