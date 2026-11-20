package com.aeropad.remote.ui.screens.controls

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.aeropad.remote.R
import com.aeropad.remote.ui.navigation.Routes
import com.aeropad.remote.ui.theme.AeroIcons
import com.aeropad.remote.ui.theme.StudioTone

internal data class StudioEntry(
    val route: String,
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val tone: StudioTone = StudioTone.VIOLET,
    @DrawableRes val art: Int? = null,
    val index: String? = null,
    val legacyTitle: String = title
)

/** One catalog drives the complete control library and the dashboard shortcuts. */
internal object StudioCatalog {
    val quick = listOf(
        StudioEntry(Routes.MOUSE, "Trackpad", "Point, click & scroll", AeroIcons.Mouse,
            StudioTone.VIOLET, R.drawable.studio_trackpad, "C / 01", "Mouse"),
        StudioEntry(Routes.KEYBOARD, "Keyboard", "Type & run shortcuts", AeroIcons.Keyboard,
            StudioTone.MINT, R.drawable.studio_keyboard, "C / 02"),
        StudioEntry(Routes.GAMEPAD, "Gamepad", "Sticks & buttons", AeroIcons.Gamepad,
            StudioTone.PEACH, R.drawable.studio_gamepad, "C / 03"),
        StudioEntry(Routes.MULTIMEDIA, "Media remote", "Playback & volume", AeroIcons.Media,
            StudioTone.BLUE, R.drawable.studio_media, "C / 04", "Multimedia")
    )
    private val fullBoard = StudioEntry(Routes.FULL_KEYBOARD, "Full Board", "Complete QWERTY keyboard", AeroIcons.FullKeyboard, StudioTone.MINT)
    private val numpad = StudioEntry(Routes.NUMPAD, "Numpad", "Numbers & calculator keys", AeroIcons.FullKeyboard, StudioTone.MINT)
    private val airMouse = StudioEntry(Routes.AIR_MOUSE, "Air Mouse", "Point with motion", AeroIcons.AirMouse)
    private val pcCombo = StudioEntry(Routes.PC_COMBO, "PC Combo", "Pointer and keys in one view", AeroIcons.Combo)
    private val presenter = StudioEntry(Routes.PRESENTER, "Presenter", "Slides & presentation keys", AeroIcons.Presenter, StudioTone.BLUE)
    private val android = StudioEntry(Routes.ANDROID, "Android", "Remote for phones & TVs", AeroIcons.Screen, StudioTone.BLUE)
    private val digitizer = StudioEntry(Routes.TOUCH, "Touch Screen", "Absolute touch on supported hosts", AeroIcons.Screen, StudioTone.BLUE)
    private val text = StudioEntry(Routes.TEXT, "Text & Clips", "Clipboard, snippets & relay", AeroIcons.TextLines, StudioTone.MINT)
    private val decks = StudioEntry(Routes.SHORTCUTS, "Decks", "Shortcut collections", AeroIcons.Deck, StudioTone.PEACH)
    private val layouts = StudioEntry(Routes.LAYOUTS, "Custom layouts", "Create and run control layouts", AeroIcons.Grid, legacyTitle = "Layouts")
    private val macros = StudioEntry(Routes.MACROS, "Macros", "Record & replay sequences", AeroIcons.Bolt, StudioTone.PEACH)
    private val builder = StudioEntry(Routes.GAMEPAD_BUILDER, "Pad Builder", "Design a custom controller", AeroIcons.GamepadPlus, StudioTone.PEACH)
    private val myLayout = StudioEntry(Routes.MY_LAYOUT, "My Layout", "Your custom keys", AeroIcons.Frame)
    private val settings = StudioEntry(Routes.SETTINGS, "App settings", "Appearance and input preferences", AeroIcons.Sliders, legacyTitle = "Settings")
    private val help = StudioEntry(Routes.HELP, "Help", "Pairing & troubleshooting", AeroIcons.Help)
    private val devices = StudioEntry(Routes.DEVICES, "Devices", "Paired and nearby hosts", AeroIcons.Laptop, StudioTone.BLUE)
    private val health = StudioEntry(Routes.CONNECTION_HEALTH, "Connection health", "Live HID and link diagnostics", AeroIcons.Pulse, StudioTone.MINT)

    val studioTools = listOf(layouts, builder, macros, myLayout)
    val workspace = listOf(devices, health, settings, help)
    val homeSecondary = listOf(fullBoard, presenter, android, text, airMouse, pcCombo, numpad, decks)
    // Preserve the meaning of every existing dashboard personalization entry.
    val customizable = quick + listOf(fullBoard, presenter, android, text, layouts, macros, settings, help)

    val sections: List<Pair<String, List<StudioEntry>>> = listOf(
        "Pointer & typing" to listOf(quick[0], quick[1], fullBoard, numpad, airMouse, pcCombo, text),
        "Game, media & TV" to listOf(quick[2], quick[3], presenter, android, digitizer),
        "Custom controls" to (listOf(decks) + studioTools)
    )
}
