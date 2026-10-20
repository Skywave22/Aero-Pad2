package com.aeropad.remote.data.gamepad

import android.content.Context
import com.aeropad.remote.model.GamepadMappingMode
import com.aeropad.remote.model.GamepadSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-game controller profiles: a saved [GamepadSettings] bundle under a
 * name (e.g. "Forza — tilt steer", "COD — gyro aim"). Apply = one tap.
 */
@Serializable
data class TuningProfile(
    val id: String,
    val name: String,
    val mode: String = GamepadMappingMode.HID_GAMEPAD.name,
    val joystickSensitivity: Int = 70,
    val deadZone: Int = 10,
    val hapticFeedback: Boolean = true,
    val gyroAim: Boolean = false,
    val tiltSteer: Boolean = false,
    val gyroSensitivity: Int = 50,
    val tiltSensitivity: Int = 50
) {
    fun toSettings(): GamepadSettings = GamepadSettings(
        mappingMode = runCatching { GamepadMappingMode.valueOf(mode) }
            .getOrDefault(GamepadMappingMode.HID_GAMEPAD),
        joystickSensitivity = joystickSensitivity,
        deadZone = deadZone,
        hapticFeedback = hapticFeedback,
        gyroAim = gyroAim,
        tiltSteer = tiltSteer,
        gyroSensitivity = gyroSensitivity,
        tiltSensitivity = tiltSensitivity
    ).sanitized()

    companion object {
        fun from(name: String, s: GamepadSettings): TuningProfile = TuningProfile(
            id = java.util.UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Profile" }.take(40),
            mode = s.mappingMode.name,
            joystickSensitivity = s.joystickSensitivity,
            deadZone = s.deadZone,
            hapticFeedback = s.hapticFeedback,
            gyroAim = s.gyroAim,
            tiltSteer = s.tiltSteer,
            gyroSensitivity = s.gyroSensitivity,
            tiltSensitivity = s.tiltSensitivity
        )
    }

    fun sanitized(): TuningProfile {
        val clean = toSettings()
        return copy(
            id = id.take(64).ifBlank { java.util.UUID.randomUUID().toString() },
            name = name.trim().take(40).ifBlank { "Profile" },
            mode = clean.mappingMode.name,
            joystickSensitivity = clean.joystickSensitivity,
            deadZone = clean.deadZone,
            hapticFeedback = clean.hapticFeedback,
            gyroAim = clean.gyroAim,
            tiltSteer = clean.tiltSteer,
            gyroSensitivity = clean.gyroSensitivity,
            tiltSensitivity = clean.tiltSensitivity
        )
    }
}

/** A11 — pure JSON import/export codec for gamepad profiles (unit-tested, never throws). */
object GamepadProfileCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encodeOne(profile: TuningProfile): String =
        json.encodeToString(TuningProfile.serializer(), profile.sanitized())

    fun encodeList(profiles: List<TuningProfile>): String =
        json.encodeToString(ListSerializer(TuningProfile.serializer()), profiles.map { it.sanitized() })

    /** Decodes either a single [TuningProfile] JSON object or a JSON array of profiles. */
    fun decodeAny(raw: String?): List<TuningProfile> {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return emptyList()
        return if (trimmed.startsWith("[")) {
            runCatching {
                json.decodeFromString(ListSerializer(TuningProfile.serializer()), trimmed)
                    .map { it.sanitized() }
            }.getOrDefault(emptyList())
        } else {
            runCatching {
                listOf(json.decodeFromString(TuningProfile.serializer(), trimmed).sanitized())
            }.getOrDefault(emptyList())
        }
    }

    fun starterMarketplaceProfiles(): List<TuningProfile> = listOf(
        TuningProfile(
            id = "preset-fps-gyro",
            name = "FPS — Gyro Precision",
            mode = GamepadMappingMode.HID_GAMEPAD.name,
            joystickSensitivity = 80,
            deadZone = 6,
            hapticFeedback = true,
            gyroAim = true,
            tiltSteer = false,
            gyroSensitivity = 65,
            tiltSensitivity = 50
        ),
        TuningProfile(
            id = "preset-racing-tilt",
            name = "Racing — Tilt Wheel",
            mode = GamepadMappingMode.HID_GAMEPAD.name,
            joystickSensitivity = 70,
            deadZone = 8,
            hapticFeedback = true,
            gyroAim = false,
            tiltSteer = true,
            gyroSensitivity = 50,
            tiltSensitivity = 70
        ),
        TuningProfile(
            id = "preset-retro-keys",
            name = "Retro / Indie (WASD Fallback)",
            mode = GamepadMappingMode.KEYBOARD_FALLBACK.name,
            joystickSensitivity = 75,
            deadZone = 12,
            hapticFeedback = true,
            gyroAim = false,
            tiltSteer = false,
            gyroSensitivity = 50,
            tiltSensitivity = 50
        )
    )
}

@Singleton
class GamepadProfileStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<TuningProfile>> = _profiles.asStateFlow()

    fun save(name: String, settings: GamepadSettings) {
        persist(_profiles.value + TuningProfile.from(name, settings))
    }

    fun remove(id: String) {
        persist(_profiles.value.filterNot { it.id == id })
    }

    /** A11 — export a single profile as shareable JSON. */
    fun exportProfileJson(id: String): String? =
        _profiles.value.firstOrNull { it.id == id }?.let(GamepadProfileCodec::encodeOne)

    /** A11 — export all saved profiles as shareable JSON. */
    fun exportAllJson(): String = GamepadProfileCodec.encodeList(_profiles.value)

    /** A11 — import one or more profiles from JSON; returns how many were imported. */
    fun importJson(raw: String?): Int {
        val decoded = GamepadProfileCodec.decodeAny(raw)
        if (decoded.isEmpty()) return 0
        val imported = decoded.map {
            it.copy(
                id = java.util.UUID.randomUUID().toString(),
                name = (it.name + " (imported)").take(40)
            ).sanitized()
        }
        persist((_profiles.value + imported).take(24))
        return imported.size
    }

    private fun file() = java.io.File(context.filesDir, "gamepad_profiles.json")

    private fun load(): List<TuningProfile> = runCatching {
        val f = file()
        if (!f.exists()) {
            GamepadProfileCodec.starterMarketplaceProfiles()
        } else {
            GamepadProfileCodec.decodeAny(f.readText()).ifEmpty {
                GamepadProfileCodec.starterMarketplaceProfiles()
            }
        }
    }.getOrElse { GamepadProfileCodec.starterMarketplaceProfiles() }

    private fun persist(list: List<TuningProfile>) {
        _profiles.value = list
        runCatching {
            val tmp = java.io.File(context.filesDir, "gamepad_profiles.tmp")
            tmp.writeText(GamepadProfileCodec.encodeList(list))
            tmp.renameTo(file())
        }.onFailure { Timber.e(it, "gamepad profile save failed") }
    }
}
