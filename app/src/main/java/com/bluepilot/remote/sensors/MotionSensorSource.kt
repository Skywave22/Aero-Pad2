package com.bluepilot.remote.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SECTION: Air Mouse / Motion controls — sensor source.
 *
 * Streams gyroscope angular velocity (rad/s, device axes) as a cold Flow.
 * TYPE_GYROSCOPE already benefits from Android's factory calibration +
 * sensor fusion; we add our own smoothing/drift handling downstream in
 * [AirMouseCore]. Collect → listener registered; cancel → unregistered
 * (no leaks, no battery drain when unused).
 *
 * NOTE: sensors only exist on real hardware — the UI shows a clear
 * "no gyroscope" state on emulators/devices without one.
 */
data class GyroSample(val x: Float, val y: Float, val z: Float, val timestampNs: Long)

@Singleton
class MotionSensorSource @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    /** True when this device has a gyroscope at all. */
    val hasGyroscope: Boolean
        get() = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null

    /** V2 MATRIX 3 — true when linear acceleration is available (flicks). */
    val hasAccelerometer: Boolean
        get() = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) != null ||
            sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    /**
     * V2 MATRIX 3 — linear-acceleration stream (gravity already removed by
     * the platform fusion where TYPE_LINEAR_ACCELERATION exists; falls back
     * to raw accelerometer). Same lifecycle contract as [gyro]: collect =
     * register, cancel = unregister — no leaks.
     */
    fun linearAcceleration(): Flow<GyroSample> = callbackFlow {
        val manager = sensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) {
            Timber.w("accelerometer unavailable")
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(GyroSample(event.values[0], event.values[1], event.values[2], event.timestamp))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val ok = runCatching {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }.getOrDefault(false)
        if (!ok) { close(); return@callbackFlow }
        awaitClose { runCatching { manager.unregisterListener(listener) } }
    }

    /** V2 MATRIX 3 — true when a gravity vector source exists (steering). */
    val hasGravity: Boolean
        get() = sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY) != null ||
            sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    /** V2 MATRIX 3 — true when a proximity sensor exists (wave trigger). */
    val hasProximity: Boolean
        get() = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null

    /** V2 MATRIX 3 finale — true when an ambient light sensor exists. */
    val hasLight: Boolean
        get() = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT) != null

    /**
     * V2 MATRIX 3 finale — ambient light stream (lux). Slow rate is fine:
     * theme switching wants stability, not latency. Collect = register,
     * cancel = unregister.
     */
    fun ambientLight(): Flow<Float> = callbackFlow {
        val manager = sensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_LIGHT)
        if (manager == null || sensor == null) {
            Timber.w("light sensor unavailable")
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values[0])
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val ok = runCatching {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }.getOrDefault(false)
        if (!ok) { close(); return@callbackFlow }
        awaitClose { runCatching { manager.unregisterListener(listener) } }
    }

    /**
     * V2 MATRIX 3 — gravity-vector stream for steering-wheel mode.
     * TYPE_GRAVITY is the platform-fused isolated gravity vector; falls
     * back to raw accelerometer (gravity dominates when held steady,
     * which is exactly the steering-wheel posture). Collect = register,
     * cancel = unregister.
     */
    fun gravity(): Flow<GyroSample> = callbackFlow {
        val manager = sensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) {
            Timber.w("gravity source unavailable")
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(GyroSample(event.values[0], event.values[1], event.values[2], event.timestamp))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val ok = runCatching {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }.getOrDefault(false)
        if (!ok) { close(); return@callbackFlow }
        awaitClose { runCatching { manager.unregisterListener(listener) } }
    }

    /**
     * V2 MATRIX 3 — proximity distance stream (cm). Most phones report a
     * binary near/far (0 or max range) — ProximityTrigger only needs the
     * near/far edge so that is fine. Collect = register, cancel = unregister.
     */
    fun proximity(): Flow<Float> = callbackFlow {
        val manager = sensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (manager == null || sensor == null) {
            Timber.w("proximity unavailable")
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values[0])
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val ok = runCatching {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }.getOrDefault(false)
        if (!ok) { close(); return@callbackFlow }
        awaitClose { runCatching { manager.unregisterListener(listener) } }
    }

    /** Gyro stream at game rate (~50Hz). Empty flow when unsupported. */
    fun gyro(): Flow<GyroSample> = callbackFlow {
        val manager = sensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (manager == null || sensor == null) {
            Timber.w("gyroscope unavailable")
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(GyroSample(event.values[0], event.values[1], event.values[2], event.timestamp))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val ok = runCatching {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }.getOrDefault(false)
        if (!ok) { close(); return@callbackFlow }
        Timber.i("gyro listener registered")
        awaitClose {
            runCatching { manager.unregisterListener(listener) }
            Timber.i("gyro listener unregistered")
        }
    }
}
