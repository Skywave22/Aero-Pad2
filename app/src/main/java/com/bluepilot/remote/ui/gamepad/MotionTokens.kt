package com.bluepilot.remote.ui.gamepad

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import com.bluepilot.remote.model.gamepad.MotionStyle

/**
 * V2 MATRIX 1 — Motion Token System.
 *
 * Maps each animation personality to concrete physics (stiffness/damping)
 * plus a press-scale. One source of truth: every element that honors
 * MotionStyle pulls from here, so the 7 personalities feel identical everywhere.
 */
object MotionTokens {

    data class Tokens(
        val press: SpringSpec<Float>,
        val release: SpringSpec<Float>,
        val pressScale: Float
    )

    fun forStyle(style: MotionStyle): Tokens = when (style) {
        MotionStyle.SUBTLE -> Tokens(
            spring(1f, 1200f), spring(1f, 900f), 0.97f)
        MotionStyle.STANDARD -> Tokens(
            spring(0.75f, 900f), spring(0.6f, 600f), 0.94f)
        MotionStyle.BOUNCY -> Tokens(
            spring(0.45f, 700f), spring(0.35f, 380f), 0.90f)
        MotionStyle.SNAPPY -> Tokens(
            spring(1f, 2500f), spring(0.8f, 1600f), 0.93f)
        MotionStyle.ELASTIC -> Tokens(
            spring(0.3f, 500f), spring(0.25f, 260f), 0.88f)
        MotionStyle.GLIDE -> Tokens(
            spring(1f, 350f), spring(1f, 250f), 0.95f)
        MotionStyle.MECHANICAL -> Tokens(
            spring(1f, Spring.StiffnessHigh), spring(1f, Spring.StiffnessHigh), 0.96f)
    }
}
