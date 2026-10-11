package com.bluepilot.remote.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.bluepilot.remote.model.gamepad.HapticPattern
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SECTION 8 — Enhanced haptic feedback engine.
 *
 * Pattern library: LIGHT_TAP, MEDIUM_CLICK, HEAVY_THUD, DOUBLE_PULSE,
 * LONG_BUZZ — assignable per gamepad control and used by app events
 * (connection success/failure, theme change).
 *
 * Tiering:
 *  - Android 12+ (API 31): VibrationEffect.Composition primitives when the
 *    hardware supports them (rich, crisp).
 *  - Android 10-11 / no primitives: waveform/one-shot fallbacks.
 *  - No vibrator: silently no-ops. Never throws.
 *
 * [intensityScale] (0.25/0.6/1.0 from the Light/Medium/Strong setting)
 * scales amplitudes globally.
 */
/** Test-friendly abstraction over the vibrator hardware. */
interface Haptics {
    var intensityScale: Float
    fun play(pattern: HapticPattern)
}

@Singleton
class HapticEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : Haptics {
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    /** Global intensity multiplier (set from the user's haptic setting). */
    @Volatile
    override var intensityScale: Float = 0.6f

    override fun play(pattern: HapticPattern) {
        if (pattern == HapticPattern.NONE) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supportsPrimitives(vib)) {
                playComposition(vib, pattern)
            } else {
                playFallback(vib, pattern)
            }
        }.onFailure { Timber.w(it, "haptic play failed") }
    }

    // ---------- rich path (API 31+, primitive-capable hardware) ----------

    private fun supportsPrimitives(vib: Vibrator): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            vib.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK,
                VibrationEffect.Composition.PRIMITIVE_TICK
            )

    private fun playComposition(vib: Vibrator, pattern: HapticPattern) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val s = intensityScale.coerceIn(0.05f, 1f)
        val effect = when (pattern) {
            HapticPattern.LIGHT_TAP -> VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f * s)
                .compose()
            HapticPattern.MEDIUM_CLICK -> VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f * s)
                .compose()
            HapticPattern.HEAVY_THUD -> VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f * s)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f * s, 40)
                .compose()
            HapticPattern.DOUBLE_PULSE -> VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f * s)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.6f * s, 110)
                .compose()
            HapticPattern.LONG_BUZZ -> VibrationEffect.createOneShot(
                220, (200 * s).toInt().coerceIn(1, 255)
            )
            HapticPattern.NONE -> return
        }
        vib.vibrate(effect)
    }

    // ---------- fallback path (API 29-30 or basic motors) ----------

    private fun playFallback(vib: Vibrator, pattern: HapticPattern) {
        val amp = (200 * intensityScale).toInt().coerceIn(1, 255)
        val effect = when (pattern) {
            HapticPattern.LIGHT_TAP -> VibrationEffect.createOneShot(12, amp / 2)
            HapticPattern.MEDIUM_CLICK -> VibrationEffect.createOneShot(25, amp)
            HapticPattern.HEAVY_THUD -> VibrationEffect.createOneShot(55, 255)
            HapticPattern.DOUBLE_PULSE -> VibrationEffect.createWaveform(
                longArrayOf(0, 25, 80, 25), intArrayOf(0, amp, 0, amp), -1
            )
            HapticPattern.LONG_BUZZ -> VibrationEffect.createOneShot(220, amp)
            HapticPattern.NONE -> return
        }
        vib.vibrate(effect)
    }
}
