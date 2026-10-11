package com.bluepilot.remote.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * SECTION 3D — Motion kit.
 *
 * All 3D effects are pure [graphicsLayer] transforms (rotationX/Y, scale,
 * translation, cameraDistance) — GPU-composited, no layout passes, no
 * per-frame allocations. Every effect respects [LocalReduceMotion]:
 * when the user enables Reduce Motion in Settings, 3D tilts/press-depth
 * collapse to simple, instant states (accessibility + battery).
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Perspective strength: lower = more dramatic. 8f ≈ subtle realistic depth. */
private const val CAMERA_DISTANCE = 8f

/**
 * 3D press-depress effect driven by a real pressed interaction state:
 * the element sinks (translationY + scale down) and tilts slightly back
 * (rotationX) as if pushed into the surface — shadow reduction is handled
 * by the caller lowering elevation when [pressedState] is true.
 */
@Composable
fun Modifier.pressDepth3D(
    interactionSource: InteractionSource,
    maxTiltDegrees: Float = 6f,
    sinkFraction: Float = 0.04f
): Modifier {
    val reduceMotion = LocalReduceMotion.current || LocalQuality3D.current == Quality3D.FLAT
    val pressed by interactionSource.collectIsPressedAsState()

    if (reduceMotion) {
        // Reduce Motion: no tilt, minimal instant scale cue only.
        return this.graphicsLayer {
            val s = if (pressed) 0.97f else 1f
            scaleX = s; scaleY = s
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "pressDepth"
    )
    return this.graphicsLayer {
        cameraDistance = CAMERA_DISTANCE * density
        rotationX = -maxTiltDegrees * progress          // tip away from finger
        val s = 1f - sinkFraction * progress
        scaleX = s; scaleY = s
        translationY = size.height * sinkFraction * 0.5f * progress
    }
}

/** Simple elevation helper: pressed elements drop their shadow. */
@Composable
fun pressedElevation(interactionSource: InteractionSource, idle: Float, pressed: Float): Float {
    val isPressed by interactionSource.collectIsPressedAsState()
    val value by animateFloatAsState(
        targetValue = if (isPressed) pressed else idle,
        animationSpec = spring(stiffness = 900f),
        label = "pressElevation"
    )
    return value
}

/**
 * SECTION 1 - card3D: static 3D presence for cards. Subtle fixed tilt +
 * shadow via graphicsLayer; FLAT quality = no-op.
 */
fun Modifier.card3D(tiltX: Float = 2f, quality: Quality3D = Quality3D.FULL): Modifier =
    if (quality == Quality3D.FLAT) this else this.graphicsLayer {
        cameraDistance = 8f * density
        rotationX = tiltX
    }

/**
 * tiltOnTouch: element tilts TOWARD the touch point (like pressing the
 * edge of a physical plate). Pass normalized touch pos (-1..1 per axis).
 */
fun Modifier.tiltOnTouch(tx: Float, ty: Float, maxDeg: Float = 8f, enabled: Boolean = true): Modifier =
    if (!enabled) this else this.graphicsLayer {
        cameraDistance = 8f * density
        rotationY = tx.coerceIn(-1f, 1f) * maxDeg
        rotationX = -ty.coerceIn(-1f, 1f) * maxDeg
    }
