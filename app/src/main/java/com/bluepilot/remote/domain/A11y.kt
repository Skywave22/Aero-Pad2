package com.bluepilot.remote.domain

import com.bluepilot.remote.model.gamepad.ButtonNaming
import com.bluepilot.remote.model.gamepad.GamepadButtonNames
import com.bluepilot.remote.model.gamepad.GamepadControlSpec
import com.bluepilot.remote.model.gamepad.GamepadControlType
import com.bluepilot.remote.model.widgets.WidgetFrame

/**
 * V2 MATRIX 5 — accessibility helpers (pure, unit-tested).
 *
 * [describe] builds the TalkBack content description for a gamepad
 * control: custom label wins; otherwise the real button name in the
 * user's chosen naming scheme (Xbox/PlayStation). The gamepad canvas was
 * previously SILENT for screen readers — every control now announces
 * what it does.
 *
 * [isBelowMinTarget] flags controls smaller than the Android minimum
 * touch target (48dp) so the editor can warn — real WCAG/Material
 * guidance, computed against the real canvas size.
 */
object A11y {

    /** Android/Material minimum touch target in dp. */
    const val MIN_TARGET_DP = 48f

    fun describe(control: GamepadControlSpec, naming: ButtonNaming = ButtonNaming.XBOX): String {
        val base = control.label.trim().ifEmpty {
            when (control.type) {
                GamepadControlType.BUTTON, GamepadControlType.TRIGGER ->
                    GamepadButtonNames.label(control.buttonIndex, naming)
                else -> ""
            }
        }
        return when (control.type) {
            GamepadControlType.BUTTON -> "$base button".trim()
            GamepadControlType.TRIGGER -> "$base trigger".trim()
            GamepadControlType.STICK ->
                (base.ifEmpty { control.stickSide.name.lowercase() } + " stick").trim()
            GamepadControlType.DPAD -> (base.ifEmpty { "directional" } + " pad").trim()
            GamepadControlType.ARROW ->
                (base.ifEmpty { control.arrowDirection.name.lowercase() } + " arrow").trim()
            GamepadControlType.COMBO -> (base.ifEmpty { "bumper and trigger" } + " combo").trim()
        }
    }

    /**
     * True when the control's rendered size falls below [MIN_TARGET_DP]
     * on EITHER axis. Frame is fractional; canvas dims are real dp.
     * NaN/zero canvas = not flagged (no info, no false alarm).
     */
    fun isBelowMinTarget(frame: WidgetFrame, canvasWidthDp: Float, canvasHeightDp: Float): Boolean {
        if (canvasWidthDp.isNaN() || canvasHeightDp.isNaN()) return false
        if (canvasWidthDp <= 0f || canvasHeightDp <= 0f) return false
        val wDp = frame.w * canvasWidthDp
        val hDp = frame.h * canvasHeightDp
        return wDp < MIN_TARGET_DP || hDp < MIN_TARGET_DP
    }
}
