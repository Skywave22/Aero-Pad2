package com.bluepilot.remote.data.layout

import com.bluepilot.remote.model.widgets.LayoutSpec
import com.bluepilot.remote.model.widgets.SkinSpec
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * JSON (de)serialization for layouts and skins.
 *
 * Contract:
 *  - Serialization never fails for sanitized specs.
 *  - Deserialization NEVER throws: bad/corrupt/hostile JSON → null.
 *  - Every decoded spec is sanitized before it reaches the app.
 * This is simultaneously the Room column format AND the import/export
 * file format (.bplayout.json / .bpskin.json).
 */
object LayoutSerializer {

    val json: Json = Json {
        ignoreUnknownKeys = true   // forward compatibility: newer fields don't break old app
        encodeDefaults = true      // stable, complete export files
        classDiscriminator = "kind"
    }

    fun layoutToJson(spec: LayoutSpec): String =
        json.encodeToString(LayoutSpec.serializer(), spec.sanitized())

    fun layoutFromJson(raw: String?): LayoutSpec? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(LayoutSpec.serializer(), raw).sanitized() }
            .onFailure { Timber.w(it, "layout JSON decode failed") }
            .getOrNull()
    }

    fun skinToJson(spec: SkinSpec): String =
        json.encodeToString(SkinSpec.serializer(), spec.sanitized())

    fun skinFromJson(raw: String?): SkinSpec? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(SkinSpec.serializer(), raw).sanitized() }
            .onFailure { Timber.w(it, "skin JSON decode failed") }
            .getOrNull()
    }
}
