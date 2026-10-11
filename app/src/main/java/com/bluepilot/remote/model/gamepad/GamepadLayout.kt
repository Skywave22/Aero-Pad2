package com.bluepilot.remote.model.gamepad

import com.bluepilot.remote.model.widgets.WidgetFrame
import kotlinx.serialization.Serializable

/**
 * SECTION 2 — Custom gamepad builder models.
 *
 * A [GamepadLayoutSpec] is a user-designed controller: a set of controls,
 * each with position/size (fractional frame), shape, colors, label and a
 * HID binding. Bindings map DIRECTLY onto the HID gamepad report
 * (report ID 5): 16 buttons, 8-way hat, 4 axes — so anything built in the
 * editor is guaranteed to be expressible on the wire.
 *
 * All serializable; the JSON doubles as the .bpgamepad.json share format.
 */

@Serializable
enum class GamepadControlType {
    /** Momentary button → one of the 16 HID button bits. */
    BUTTON,
    /** Shoulder/trigger — same wire format as BUTTON, different default look. */
    TRIGGER,
    /** 4- or 8-way directional pad → HID hat switch. */
    DPAD,
    /** Analog stick → left (X/Y) or right (Z/Rz) axis pair. */
    STICK,
    /** ADV S1 — single independently-placeable arrow → one hat direction. */
    ARROW,
    /** ADV S1 — bumper+trigger double zone: top half & bottom half are two buttons. */
    COMBO
}

@Serializable
enum class ControlShape { CIRCLE, ROUNDED, SQUARE }

/** V2 MATRIX 1 — animation personality per element (press/release feel).
 *  Each maps to a concrete SpringSpec in Motion tokens. */
@Serializable
enum class MotionStyle { SUBTLE, STANDARD, BOUNCY, SNAPPY, ELASTIC, GLIDE, MECHANICAL }

/** V2 MATRIX 1 — pressed-state style override (state-dependent styling).
 *  Any field null = inherit from the base style (zero behavior change). */
@Serializable
@androidx.compose.runtime.Immutable
data class PressedStyle(
    /** ARGB color while pressed; null = auto-darken like before. */
    val color: Long? = null,
    /** Scale while pressed (0.5..1.2); null = personality default. */
    val scale: Float? = null,
    /** Corner radius while pressed (dp 0..40); null = unchanged. */
    val cornerRadius: Int? = null,
    /** Border width while pressed (dp 0..6); null = unchanged. */
    val borderWidth: Int? = null,
    /** Border color while pressed; null = white glow default. */
    val borderColor: Long? = null
) {
    fun sanitized(): PressedStyle = copy(
        scale = scale?.let { if (it.isNaN()) null else it.coerceIn(0.5f, 1.2f) },
        cornerRadius = cornerRadius?.coerceIn(0, 40),
        borderWidth = borderWidth?.coerceIn(0, 6)
    )
}

// ----------------------------------------------------------------------
// ADV SECTION 1 — advanced control library enums
// ----------------------------------------------------------------------

/** Button press semantics. */
@Serializable
enum class ButtonMode {
    /** Press = down, release = up (also the "hold" behavior). */
    MOMENTARY,
    /** Tap once = latched ON, tap again = OFF (auto-run, crouch-toggle). */
    TOGGLE,
    /** Single tap = primary index, double tap = secondary index. */
    MULTI_TAP,
    /** Fires when a finger slides INTO it (piano-key combos across buttons). */
    SLIDE,
    /** Long-press opens a radial wheel of up to 8 sub-buttons. */
    RADIAL
}

/** One hat direction for ARROW controls (HID hat 0=N..7=NW, 8=neutral). */
@Serializable
enum class ArrowDirection(val hat: Int) {
    UP(0), UP_RIGHT(1), RIGHT(2), DOWN_RIGHT(3),
    DOWN(4), DOWN_LEFT(5), LEFT(6), UP_LEFT(7)
}

/** Visual style for ARROW controls. */
@Serializable
enum class ArrowStyle { SQUARE, ROUNDED, ARROW, DOT }

/** D-pad rendering/behavior style. */
@Serializable
enum class DpadStyle {
    /** Classic cross: 4- or 8-way segments with a center dead zone. */
    CROSS,
    /** Continuous circular zone: always 8-way, small dead zone. */
    CIRCULAR
}

/** Analog stick gate (movement boundary) shape. */
@Serializable
enum class StickGate { CIRCLE, SQUARE }

@Serializable
enum class StickSide { LEFT, RIGHT }

/** Face-button naming convention (industry standards). */
@Serializable
enum class ButtonNaming { XBOX, PLAYSTATION }

/** Stick/trigger response curve. */
@Serializable
enum class ResponseCurve { LINEAR, EXPONENTIAL, AGGRESSIVE }

/** Haptic pattern assignable per control (Section 8 library). */
@Serializable
enum class HapticPattern { NONE, LIGHT_TAP, MEDIUM_CLICK, HEAVY_THUD, DOUBLE_PULSE, LONG_BUZZ }

@Serializable
@androidx.compose.runtime.Immutable
data class GamepadControlSpec(
    val id: String,
    val type: GamepadControlType,
    val frame: WidgetFrame = WidgetFrame(),
    val shape: ControlShape = ControlShape.CIRCLE,
    /** ARGB fill color. */
    val color: Long = 0xFF2F6BFF,
    val opacity: Float = 0.9f,
    val label: String = "",
    /** HID button index 0..15 (BUTTON / TRIGGER). */
    val buttonIndex: Int = 0,
    /** Which axis pair a STICK drives. */
    val stickSide: StickSide = StickSide.LEFT,
    /** Radial dead zone percent 0..50 (STICK only). */
    val deadZone: Int = 10,
    /** DPAD: true = 8-way (diagonals), false = 4-way. */
    val eightWay: Boolean = false,
    /** Turbo/rapid-fire: auto-repeat while held (BUTTON/TRIGGER). */
    val turbo: Boolean = false,
    /** Turbo repeat rate in presses per second (2..20). */
    val turboRate: Int = 8,
    /** Response curve for STICK axes. */
    val curve: ResponseCurve = ResponseCurve.LINEAR,
    /** Haptic pattern played on press. */
    val haptic: HapticPattern = HapticPattern.LIGHT_TAP,
    /** Custom emoji/icon rendered above the label ("" = none). */
    val icon: String = "",

    // ----- ADV SECTION 1 fields (all defaulted → old JSON loads fine) -----
    /** Button press semantics (BUTTON/TRIGGER). */
    val buttonMode: ButtonMode = ButtonMode.MOMENTARY,
    /** MULTI_TAP: second action's HID button index. RADIAL: unused. */
    val secondaryButtonIndex: Int = 1,
    /** Double-tap window in ms (MULTI_TAP). */
    val multiTapWindowMs: Int = 250,
    /** RADIAL: HID button indices of the wheel options (2..8 entries). */
    val radialOptions: List<Int> = emptyList(),
    /** ARROW: which hat direction this arrow sends. */
    val arrowDirection: ArrowDirection = ArrowDirection.UP,
    /** ARROW: visual style. */
    val arrowStyle: ArrowStyle = ArrowStyle.ROUNDED,
    /** ARROW: hold-to-repeat (else single press). */
    val arrowRepeat: Boolean = false,
    /** ARROW: repeat rate in repeats/second (2..30). */
    val arrowRepeatRate: Int = 10,
    /** DPAD: cross vs continuous circular zone. */
    val dpadStyle: DpadStyle = DpadStyle.CROSS,
    /** DPAD: diagonals-only mode (only NE/SE/SW/NW register). */
    val diagonalOnly: Boolean = false,
    /** STICK: gate shape (circle vs square boundary). */
    val stickGate: StickGate = StickGate.CIRCLE,
    /** STICK: show 8-direction guide-line overlay. */
    val stickGuides: Boolean = false,
    /** STICK: sticky mode — knob stays where released (no auto-center). */
    val stickSticky: Boolean = false,
    /** STICK: stick-click (L3/R3) — long-press the knob sends this button; -1 = off. */
    val stickClickIndex: Int = -1,
    /** STICK: outer range as fraction of geometric max (0.5..1.0). */
    val outerRange: Float = 1f,
    /** STICK: anti-deadzone — output jumps to this % as soon as input leaves center. */
    val antiDeadZone: Int = 0,
    /** COMBO: HID index for the second (bottom) zone; top uses buttonIndex. */
    val comboSecondIndex: Int = 7,
    /** Visual press-confirmation ripple/glow toggle. */
    val pressGlow: Boolean = true,
    // ----- V2 MATRIX 6: esports precision (all defaulted) -----
    /** STICK: snap to N notches (0=analog, 8, 16). */
    val snapNotches: Int = 0,
    /** STICK: aim-smoothing center zone radius fraction (0=off). */
    val aimZoneRadius: Float = 0f,
    /** STICK: sensitivity inside the aim zone, percent 1..100. */
    val aimZoneInner: Int = 10,
    /** STICK: output as digital 8-way d-pad instead of analog axes. */
    val stickAsDpad: Boolean = false,
    /** TRIGGER: hold-duration overlap — 0=off; else ms to reach full pull. */
    val triggerTravelMs: Int = 0,
    /** TRIGGER overlap: pull fraction where stage-1 (buttonIndex) fires. */
    val overlapL1At: Float = 0.4f,
    /** TRIGGER overlap: pull fraction where stage-2 (comboSecondIndex) fires. */
    val overlapL2At: Float = 0.8f,
    /** V2 MATRIX 1 — press/release animation personality. */
    val motionStyle: MotionStyle = MotionStyle.STANDARD,
    /** V2 MATRIX 1 — pressed-state style overrides (null fields inherit). */
    val pressedStyle: PressedStyle = PressedStyle(),
    /** V2 MATRIX 1 b2 — cooldown ring: after each press the control shows a
     *  sweeping ring for this many ms (0 = off). Visual only — input is
     *  NEVER blocked (honest: HID still fires; the ring is a timing aid). */
    val cooldownMs: Int = 0,
    /** V2 MATRIX 1 b3 — duration-to-activate: press must be held this many
     *  ms before the action fires (0 = instant, unchanged behavior).
     *  Prevents accidental taps on dangerous buttons. */
    val holdToActivateMs: Int = 0,
    /** V2 MATRIX 1 b4 — per-corner radius (dp): TL,TR,BR,BL. Empty list =
     *  uniform shape from [shape] (unchanged). Only for ROUNDED/SQUARE. */
    val cornerRadii: List<Int> = emptyList(),
    /** V2 MATRIX 1 b4 — extra touch slop (dp) added around the control's
     *  hit area tolerance before a press cancels; 0 = system default. */
    val touchSlopDp: Int = 0,
    /** V2 MATRIX 1 b4 — reject presses when more than N pointers are
     *  already down on this control (0 = no limit, old behavior). */
    val maxPointers: Int = 0,
    /** V2 MATRIX 1 b6 — idle breathing glow (CTA attention pulse; off by
     *  default). Respects Reduce Motion + FLAT quality automatically. */
    val breathingGlow: Boolean = false,
    /** ADV S2 — layer: 0 = base (always visible), 1 = shift layer
     *  (visible only while the layout's shift button is held). */
    val layer: Int = 0
) {
    companion object {
        const val LABEL_MAX = 8
        const val BUTTON_INDEX_MAX = 15
        const val RADIAL_MAX = 8
    }

    fun sanitized(): GamepadControlSpec = copy(
        frame = frame.sanitized(),
        opacity = if (opacity.isNaN()) 0.9f else opacity.coerceIn(0.15f, 1f),
        label = label.take(LABEL_MAX),
        buttonIndex = buttonIndex.coerceIn(0, BUTTON_INDEX_MAX),
        deadZone = deadZone.coerceIn(0, 50),
        turboRate = turboRate.coerceIn(2, 20),
        icon = icon.take(4),
        secondaryButtonIndex = secondaryButtonIndex.coerceIn(0, BUTTON_INDEX_MAX),
        multiTapWindowMs = multiTapWindowMs.coerceIn(120, 600),
        radialOptions = radialOptions.take(RADIAL_MAX).map { it.coerceIn(0, BUTTON_INDEX_MAX) },
        arrowRepeatRate = arrowRepeatRate.coerceIn(2, 30),
        stickClickIndex = stickClickIndex.coerceIn(-1, BUTTON_INDEX_MAX),
        outerRange = if (outerRange.isNaN()) 1f else outerRange.coerceIn(0.5f, 1f),
        antiDeadZone = antiDeadZone.coerceIn(0, 40),
        snapNotches = if (snapNotches in listOf(0, 8, 16)) snapNotches else 0,
        triggerTravelMs = triggerTravelMs.coerceIn(0, 2000),
        overlapL1At = if (overlapL1At.isNaN()) 0.4f else overlapL1At.coerceIn(0.05f, 0.95f),
        overlapL2At = if (overlapL2At.isNaN()) 0.8f else overlapL2At.coerceIn(0.1f, 1f),
        pressedStyle = pressedStyle.sanitized(),
        cooldownMs = cooldownMs.coerceIn(0, 60_000),
        holdToActivateMs = holdToActivateMs.coerceIn(0, 3000),
        cornerRadii = if (cornerRadii.size == 4) cornerRadii.map { it.coerceIn(0, 40) } else emptyList(),
        touchSlopDp = touchSlopDp.coerceIn(0, 48),
        maxPointers = maxPointers.coerceIn(0, 5),
        aimZoneRadius = if (aimZoneRadius.isNaN()) 0f else aimZoneRadius.coerceIn(0f, 0.9f),
        aimZoneInner = aimZoneInner.coerceIn(1, 100),
        comboSecondIndex = comboSecondIndex.coerceIn(0, BUTTON_INDEX_MAX),
        layer = layer.coerceIn(0, 1)
    )
}

@Serializable
@androidx.compose.runtime.Immutable
data class GamepadLayoutSpec(
    val name: String = "Custom pad",
    val controls: List<GamepadControlSpec> = emptyList(),
    /** Face-button naming shown in the binding picker (Xbox/PlayStation). */
    val naming: ButtonNaming = ButtonNaming.XBOX,
    /** Stick sensitivity preset percent 0..100 (per-profile, e.g. FPS vs Racing). */
    val stickSensitivity: Int = 70,
    /** ADV S2 — id of the control that acts as the shift key ("" = no shift layer).
     *  While held, layer-1 controls show and layer-0 controls dim. */
    val shiftControlId: String = "",
    /** ADV S2 — magnetic snap grid size as canvas fraction (0 = off). */
    val gridSize: Float = 0f,
    /** ADV S3 — profile tags/categories (FPS, Racing, ...). */
    val tags: List<String> = emptyList(),
    /** V2 M3 deferred-item — persisted flick mapping: direction name
     *  ("UP"/"DOWN"/"LEFT"/"RIGHT") → HID button 0..15, -1 = off.
     *  Empty map = the built-in default (UP=0). Legacy JSON loads fine. */
    val flickMap: Map<String, Int> = emptyMap()
) {
    companion object {
        const val MAX_CONTROLS = 32
        const val NAME_MAX = 40
    }

    fun sanitized(): GamepadLayoutSpec = copy(
        name = name.take(NAME_MAX).ifBlank { "Gamepad" },
        controls = controls.take(MAX_CONTROLS).map { it.sanitized() },
        stickSensitivity = stickSensitivity.coerceIn(0, 100),
        gridSize = if (gridSize.isNaN()) 0f else gridSize.coerceIn(0f, 0.25f),
        tags = tags.filter { it.isNotBlank() }.take(6).map { it.take(16) },
        // V2 M3 — only the 4 known directions, values clamped to -1..15.
        flickMap = flickMap
            .filterKeys { it in setOf("UP", "DOWN", "LEFT", "RIGHT") }
            .mapValues { it.value.coerceIn(-1, 15) }
    )
}

/** Standard HID button-index names shown in the binding picker. */
object GamepadButtonNames {
    /** Xbox-style names: A/B/X/Y, LB/RB, LT/RT, View/Menu, L3/R3, Guide. */
    private val XBOX: List<String> = listOf(
        "A (1)", "B (2)", "X (3)", "Y (4)",
        "LB (5)", "RB (6)", "LT (7)", "RT (8)",
        "View (9)", "Menu (10)", "L3 (11)", "R3 (12)",
        "Guide (13)", "B14", "B15", "B16"
    )

    /** PlayStation-style names: Cross/Circle/Square/Triangle, L1/R1, L2/R2, Share/Options. */
    private val PLAYSTATION: List<String> = listOf(
        "Cross (1)", "Circle (2)", "Square (3)", "Triangle (4)",
        "L1 (5)", "R1 (6)", "L2 (7)", "R2 (8)",
        "Share (9)", "Options (10)", "L3 (11)", "R3 (12)",
        "PS (13)", "B14", "B15", "B16"
    )

    fun label(index: Int, naming: ButtonNaming = ButtonNaming.XBOX): String {
        val list = if (naming == ButtonNaming.PLAYSTATION) PLAYSTATION else XBOX
        return list.getOrElse(index.coerceIn(0, 15)) { "B${index + 1}" }
    }
}

/** Response-curve math (pure, unit-tested). Input/output -1..1 per axis. */
object ResponseCurves {
    fun apply(value: Float, curve: ResponseCurve): Float {
        val v = if (value.isNaN()) 0f else value.coerceIn(-1f, 1f)
        val mag = kotlin.math.abs(v)
        val shaped = when (curve) {
            ResponseCurve.LINEAR -> mag
            // Slow start, fast finish (FPS aim style)
            ResponseCurve.EXPONENTIAL -> mag * mag
            // Even slower start, snappier end
            ResponseCurve.AGGRESSIVE -> mag * mag * mag
        }
        return if (v < 0) -shaped else shaped
    }
}
