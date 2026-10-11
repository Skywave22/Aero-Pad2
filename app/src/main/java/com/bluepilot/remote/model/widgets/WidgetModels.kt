package com.bluepilot.remote.model.widgets

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The widget vocabulary — heart of the customization engine.
 *
 * EVERY control on a custom screen is DATA:
 *   WidgetSpec = type + frame (position/size) + style + action bindings.
 * One generic renderer (ui/widgets/WidgetRenderer.kt) draws any spec;
 * the Module 6 editor mutates specs; Room stores them as JSON.
 *
 * All models are kotlinx-serializable → the DB payload doubles as the
 * import/export file format.
 */

// ----------------------------------------------------------------------
// Widget types
// ----------------------------------------------------------------------

@Serializable
enum class WidgetType {
    /** Tap button firing its primary action. */
    BUTTON,
    /** Mouse trackpad region (move + tap-click + long-press right-click). */
    TRACKPAD,
    /** Vertical scroll strip. */
    SCROLL_STRIP,
    /** Draggable joystick (emits -1..1 axes). */
    JOYSTICK,
    /** Vertical slider firing primary action on up-steps, secondary on down. */
    SLIDER,
    /** 4-way arrow pad (up/down/left/right arrow keys or bound actions). */
    DPAD,
    /** Swipe-gesture zone: 4 directions + two-finger tap, each bindable. */
    GESTURE_ZONE
}

// ----------------------------------------------------------------------
// Geometry — everything fractional (0..1 of the canvas) so layouts scale
// to any screen size and orientation.
// ----------------------------------------------------------------------

@Serializable
@androidx.compose.runtime.Immutable
data class WidgetFrame(
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 0.25f,
    val h: Float = 0.15f
) {
    companion object {
        const val MIN_SIZE = 0.05f // 5% of canvas — nothing smaller is touchable
    }

    /** Clamp to the canvas; enforce minimum touchable size. */
    fun sanitized(): WidgetFrame {
        val cw = w.coerceIn(MIN_SIZE, 1f)
        val ch = h.coerceIn(MIN_SIZE, 1f)
        return WidgetFrame(
            x = x.coerceIn(0f, 1f - cw),
            y = y.coerceIn(0f, 1f - ch),
            w = cw,
            h = ch
        )
    }
}

// ----------------------------------------------------------------------
// Style — every visual property user-editable, all validated.
// Colors are ARGB Long (0xAARRGGBB) for stable JSON.
// ----------------------------------------------------------------------

@Serializable
@androidx.compose.runtime.Immutable
data class WidgetStyle(
    val backgroundColor: Long = 0xFF1A2238,
    val contentColor: Long = 0xFFE6EAF5,
    val opacity: Float = 1f,
    val cornerRadius: Int = 14,      // dp
    val elevation: Int = 2,          // dp (shadow)
    val fontSize: Int = 14,          // sp
    val label: String = "",
    val icon: String = "",           // emoji/short glyph rendered above label
    val visible: Boolean = true
) {
    companion object {
        const val RADIUS_MAX = 60
        const val ELEVATION_MAX = 24
        const val FONT_MIN = 8
        const val FONT_MAX = 40
        const val LABEL_MAX = 24
    }

    fun sanitized(): WidgetStyle = copy(
        opacity = if (opacity.isNaN()) 1f else opacity.coerceIn(0.1f, 1f),
        cornerRadius = cornerRadius.coerceIn(0, RADIUS_MAX),
        elevation = elevation.coerceIn(0, ELEVATION_MAX),
        fontSize = fontSize.coerceIn(FONT_MIN, FONT_MAX),
        label = label.take(LABEL_MAX),
        icon = icon.take(4)
    )
}

// ----------------------------------------------------------------------
// Action bindings — what a widget DOES. Serializable mirror of HidAction
// (kept separate so stored layouts never depend on runtime-only types).
// ----------------------------------------------------------------------

@Serializable
sealed class WidgetAction {

    /** Keyboard key tap with optional modifiers. */
    @Serializable
    @SerialName("key")
    data class KeyTap(val key: Byte, val modifiers: Byte = 0) : WidgetAction()

    /** Consumer/media usage tap (play/pause, volume...). */
    @Serializable
    @SerialName("media")
    data class Media(val usage: Int) : WidgetAction()

    /** Mouse button click. 1=left 2=right 4=middle (HID mask). */
    @Serializable
    @SerialName("mouse")
    data class MouseClick(val buttonMask: Int) : WidgetAction()

    /** Type a text string. */
    @Serializable
    @SerialName("text")
    data class TypeText(val text: String) : WidgetAction()

    /** Run a stored macro by id (executes in Module 7). */
    @Serializable
    @SerialName("macro")
    data class RunMacro(val macroId: Long) : WidgetAction()

    /** No-op (unbound widget). */
    @Serializable
    @SerialName("none")
    data object None : WidgetAction()
}

// ----------------------------------------------------------------------
// The widget itself + the full layout
// ----------------------------------------------------------------------

@Serializable
@androidx.compose.runtime.Immutable
data class WidgetSpec(
    val id: String,                          // stable UUID string
    val type: WidgetType,
    val frame: WidgetFrame = WidgetFrame(),
    val style: WidgetStyle = WidgetStyle(),
    /** Primary binding (tap / slider-up / dpad-up depending on type). */
    val action: WidgetAction = WidgetAction.None,
    /** Secondary binding (slider-down / dpad-down). */
    val secondaryAction: WidgetAction = WidgetAction.None,
    /** GESTURE_ZONE bindings — one per swipe direction + two-finger tap. */
    val swipeUp: WidgetAction = WidgetAction.None,
    val swipeDown: WidgetAction = WidgetAction.None,
    val swipeLeft: WidgetAction = WidgetAction.None,
    val swipeRight: WidgetAction = WidgetAction.None,
    val twoFingerTap: WidgetAction = WidgetAction.None,
    /** SECTION 2 — element grouping: same non-blank id = moves as one unit.
     *  Serialized with a default so every pre-upgrade layout loads fine. */
    val groupId: String = ""
) {
    fun sanitized(): WidgetSpec = copy(
        frame = frame.sanitized(),
        style = style.sanitized(),
        groupId = groupId.take(64)
    )
}

@Serializable
@androidx.compose.runtime.Immutable
data class LayoutSpec(
    val name: String = "Untitled",
    /** Grid snap size as canvas fraction; 0 disables snapping. */
    val gridSize: Float = 0.025f,
    val widgets: List<WidgetSpec> = emptyList(),
    /** SECTION 2 — profile category/folder (e.g. "FPS", "Media", "Work"). */
    val category: String = "",
    /** SECTION 2 — free-text description/notes shown in the profile list. */
    val notes: String = ""
) {
    companion object {
        const val MAX_WIDGETS = 60
        const val NAME_MAX = 40
        const val CATEGORY_MAX = 24
        const val NOTES_MAX = 200
    }

    fun sanitized(): LayoutSpec = copy(
        name = name.take(NAME_MAX).ifBlank { "Untitled" },
        gridSize = if (gridSize.isNaN()) 0f else gridSize.coerceIn(0f, 0.25f),
        widgets = widgets.take(MAX_WIDGETS).map { it.sanitized() },
        category = category.take(CATEGORY_MAX),
        notes = notes.take(NOTES_MAX)
    )
}

// ----------------------------------------------------------------------
// Skins — full visual presets. Widget styles override skin defaults.
// ----------------------------------------------------------------------

@Serializable
@androidx.compose.runtime.Immutable
data class SkinSpec(
    val name: String = "Default",
    val isDark: Boolean = true,
    val primary: Long = 0xFF2F6BFF,
    val background: Long = 0xFF0B0F1A,
    val surface: Long = 0xFF121828,
    val surfaceVariant: Long = 0xFF1A2238,
    val onSurface: Long = 0xFFE6EAF5,
    /** Default widget style applied when a widget doesn't override. */
    val widgetDefaults: WidgetStyle = WidgetStyle()
) {
    fun sanitized(): SkinSpec = copy(
        name = name.take(40).ifBlank { "Skin" },
        widgetDefaults = widgetDefaults.sanitized()
    )
}
