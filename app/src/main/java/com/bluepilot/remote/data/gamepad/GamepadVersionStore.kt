package com.bluepilot.remote.data.gamepad

import android.content.Context
import com.bluepilot.remote.model.gamepad.GamepadLayoutSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ADV SECTION 3 — profile versioning.
 *
 * Every save of an existing profile pushes the PREVIOUS spec into that
 * profile's version history (newest first, capped). Stored as JSON files
 * under filesDir/gamepad_versions/<profileId>.json — deliberately NOT a
 * Room table, so zero migration risk and the whole history is one
 * defensive JSON read (corrupt file → empty history, never a crash).
 */
object VersionCodec {

    const val MAX_VERSIONS = 5

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    fun encode(versions: List<GamepadLayoutSpec>): String =
        json.encodeToString(ListSerializer(GamepadLayoutSpec.serializer()), versions)

    fun decode(raw: String?): List<GamepadLayoutSpec> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(GamepadLayoutSpec.serializer()), raw)
                .map { it.sanitized() }
        }.getOrDefault(emptyList())
    }

    /** Push newest-first, cap at [MAX_VERSIONS]. */
    fun push(versions: List<GamepadLayoutSpec>, spec: GamepadLayoutSpec): List<GamepadLayoutSpec> =
        (listOf(spec.sanitized()) + versions).take(MAX_VERSIONS)
}

@Singleton
class GamepadVersionStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun dir(): File =
        File(context.filesDir, "gamepad_versions").apply { mkdirs() }

    private fun file(profileId: Long): File = File(dir(), "$profileId.json")

    fun versions(profileId: Long): List<GamepadLayoutSpec> =
        runCatching { VersionCodec.decode(file(profileId).takeIf { it.exists() }?.readText()) }
            .onFailure { Timber.w(it, "version read failed") }
            .getOrDefault(emptyList())

    fun push(profileId: Long, previous: GamepadLayoutSpec) {
        runCatching {
            file(profileId).writeText(VersionCodec.encode(VersionCodec.push(versions(profileId), previous)))
        }.onFailure { Timber.w(it, "version write failed") }
    }

    fun clear(profileId: Long) {
        runCatching { file(profileId).delete() }
    }
}
