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
data class GamepadProfile(
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
        fun from(name: String, s: GamepadSettings): GamepadProfile = GamepadProfile(
            id = java.util.UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Profile" },
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
}

@Singleton
class GamepadProfileStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<GamepadProfile>> = _profiles.asStateFlow()

    fun save(name: String, settings: GamepadSettings) {
        persist(_profiles.value + GamepadProfile.from(name, settings))
    }

    fun remove(id: String) {
        persist(_profiles.value.filterNot { it.id == id })
    }

    private fun file() = java.io.File(context.filesDir, "gamepad_profiles.json")

    private fun load(): List<GamepadProfile> = runCatching {
        file().takeIf { it.exists() }?.let {
            json.decodeFromString(ListSerializer(GamepadProfile.serializer()), it.readText())
        }
    }.getOrNull().orEmpty()

    private fun persist(list: List<GamepadProfile>) {
        _profiles.value = list
        runCatching {
            val tmp = java.io.File(context.filesDir, "gamepad_profiles.tmp")
            tmp.writeText(json.encodeToString(ListSerializer(GamepadProfile.serializer()), list))
            tmp.renameTo(file())
        }.onFailure { Timber.e(it, "gamepad profile save failed") }
    }
}
