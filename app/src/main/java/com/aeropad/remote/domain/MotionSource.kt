package com.aeropad.remote.domain

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Round 7 — Pro gamepad motion input.
 *
 *  - Tilt steering: phone roll (GAME_ROTATION_VECTOR) → left-stick X,
 *    centered at flat, full deflection at ±25°.
 *  - Gyro aiming: angular velocity (GYROSCOPE) → right-stick X/Y,
 *    yaw = look left/right, pitch = look up/down.
 *
 * Listeners register only while a gamepad screen enables them and are
 * released on stop — zero battery cost otherwise.
 */
@Singleton
class MotionSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class Motion(val steer: Float, val aimX: Float, val aimY: Float)

    private val _motion = MutableStateFlow(Motion(0f, 0f, 0f))
    val motion: StateFlow<Motion> = _motion.asStateFlow()

    private var running = false
    private val manager: SensorManager? by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    private val rotMat = FloatArray(9)
    private val orient = FloatArray(3)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            when (e.sensor.type) {
                Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rotMat, e.values)
                    SensorManager.getOrientation(rotMat, orient)
                    val roll = orient[2]
                    val full = Math.toRadians(25.0).toFloat()
                    val steer = (roll / full).coerceIn(-1f, 1f)
                    _motion.value = _motion.value.copy(steer = steer)
                }
                Sensor.TYPE_GYROSCOPE -> {
                    // rad/s → stick deflection (÷4 ≈ full look at a fast flick).
                    val ax = (e.values[2] / 4f).coerceIn(-1f, 1f)
                    val ay = (e.values[0] / 4f).coerceIn(-1f, 1f)
                    _motion.value = _motion.value.copy(aimX = ax, aimY = ay)
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun start() {
        val m = manager ?: return
        if (running) return
        running = true
        runCatching {
            m.getSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)?.let {
                m.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
            }
            m.getSensor(Sensor.TYPE_GYROSCOPE)?.let {
                m.registerListener(listener, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }.onFailure { Timber.w(it, "motion sensors unavailable") }
    }

    fun stop() {
        if (!running) return
        running = false
        runCatching { manager?.unregisterListener(listener) }
        _motion.value = Motion(0f, 0f, 0f)
    }
}
