package com.bluepilot.remote.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.sharp.HelpOutline
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Gamepad
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.sharp.Bluetooth
import androidx.compose.material.icons.sharp.Bolt
import androidx.compose.material.icons.sharp.Computer
import androidx.compose.material.icons.sharp.Dashboard
import androidx.compose.material.icons.sharp.Gamepad
import androidx.compose.material.icons.sharp.Home
import androidx.compose.material.icons.sharp.Keyboard
import androidx.compose.material.icons.sharp.Mouse
import androidx.compose.material.icons.sharp.MusicNote
import androidx.compose.material.icons.sharp.Palette
import androidx.compose.material.icons.sharp.Pin
import androidx.compose.material.icons.sharp.Settings
import androidx.compose.material.icons.sharp.Slideshow
import androidx.compose.material.icons.sharp.SportsEsports
import androidx.compose.material.icons.sharp.Vibration
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * SECTION: Icon system — icon PACKS (Filled / Outlined / Rounded / Sharp).
 *
 * Every app icon is addressed by a semantic [AppIcon] key; [iconFor] resolves
 * it through the active pack (persisted setting, provided app-wide via
 * [LocalIconPack]). Switching the pack re-skins every icon at once.
 */
enum class IconPack { FILLED, OUTLINED, ROUNDED, SHARP }

enum class AppIcon {
    HOME, DEVICES, SETTINGS, LAYOUTS, THEMES, MACROS,
    MOUSE, KEYBOARD, NUMPAD, MULTIMEDIA, PRESENTER,
    GAMEPAD, PAD_BUILDER, CONNECT, HAPTICS, HELP
}

val LocalIconPack = staticCompositionLocalOf { IconPack.ROUNDED }

/** Resolve a semantic icon through the given pack. */
fun iconFor(icon: AppIcon, pack: IconPack): ImageVector = when (pack) {
    IconPack.FILLED -> when (icon) {
        AppIcon.HOME -> Icons.Filled.Home
        AppIcon.DEVICES -> Icons.Filled.Computer
        AppIcon.SETTINGS -> Icons.Filled.Settings
        AppIcon.LAYOUTS -> Icons.Filled.Dashboard
        AppIcon.THEMES -> Icons.Filled.Palette
        AppIcon.MACROS -> Icons.Filled.Bolt
        AppIcon.MOUSE -> Icons.Filled.Mouse
        AppIcon.KEYBOARD -> Icons.Filled.Keyboard
        AppIcon.NUMPAD -> Icons.Filled.Pin
        AppIcon.MULTIMEDIA -> Icons.Filled.MusicNote
        AppIcon.PRESENTER -> Icons.Filled.Slideshow
        AppIcon.GAMEPAD -> Icons.Filled.Gamepad
        AppIcon.PAD_BUILDER -> Icons.Filled.SportsEsports
        AppIcon.CONNECT -> Icons.Filled.Bluetooth
        AppIcon.HAPTICS -> Icons.Filled.Vibration
        AppIcon.HELP -> Icons.AutoMirrored.Filled.HelpOutline
    }
    IconPack.OUTLINED -> when (icon) {
        AppIcon.HOME -> Icons.Outlined.Home
        AppIcon.DEVICES -> Icons.Outlined.Computer
        AppIcon.SETTINGS -> Icons.Outlined.Settings
        AppIcon.LAYOUTS -> Icons.Outlined.Dashboard
        AppIcon.THEMES -> Icons.Outlined.Palette
        AppIcon.MACROS -> Icons.Outlined.Bolt
        AppIcon.MOUSE -> Icons.Outlined.Mouse
        AppIcon.KEYBOARD -> Icons.Outlined.Keyboard
        AppIcon.NUMPAD -> Icons.Outlined.Pin
        AppIcon.MULTIMEDIA -> Icons.Outlined.MusicNote
        AppIcon.PRESENTER -> Icons.Outlined.Slideshow
        AppIcon.GAMEPAD -> Icons.Outlined.Gamepad
        AppIcon.PAD_BUILDER -> Icons.Outlined.SportsEsports
        AppIcon.CONNECT -> Icons.Outlined.Bluetooth
        AppIcon.HAPTICS -> Icons.Outlined.Vibration
        AppIcon.HELP -> Icons.AutoMirrored.Outlined.HelpOutline
    }
    IconPack.ROUNDED -> when (icon) {
        AppIcon.HOME -> Icons.Rounded.Home
        AppIcon.DEVICES -> Icons.Rounded.Computer
        AppIcon.SETTINGS -> Icons.Rounded.Settings
        AppIcon.LAYOUTS -> Icons.Rounded.Dashboard
        AppIcon.THEMES -> Icons.Rounded.Palette
        AppIcon.MACROS -> Icons.Rounded.Bolt
        AppIcon.MOUSE -> Icons.Rounded.Mouse
        AppIcon.KEYBOARD -> Icons.Rounded.Keyboard
        AppIcon.NUMPAD -> Icons.Rounded.Pin
        AppIcon.MULTIMEDIA -> Icons.Rounded.MusicNote
        AppIcon.PRESENTER -> Icons.Rounded.Slideshow
        AppIcon.GAMEPAD -> Icons.Rounded.Gamepad
        AppIcon.PAD_BUILDER -> Icons.Rounded.SportsEsports
        AppIcon.CONNECT -> Icons.Rounded.Bluetooth
        AppIcon.HAPTICS -> Icons.Rounded.Vibration
        AppIcon.HELP -> Icons.AutoMirrored.Rounded.HelpOutline
    }
    IconPack.SHARP -> when (icon) {
        AppIcon.HOME -> Icons.Sharp.Home
        AppIcon.DEVICES -> Icons.Sharp.Computer
        AppIcon.SETTINGS -> Icons.Sharp.Settings
        AppIcon.LAYOUTS -> Icons.Sharp.Dashboard
        AppIcon.THEMES -> Icons.Sharp.Palette
        AppIcon.MACROS -> Icons.Sharp.Bolt
        AppIcon.MOUSE -> Icons.Sharp.Mouse
        AppIcon.KEYBOARD -> Icons.Sharp.Keyboard
        AppIcon.NUMPAD -> Icons.Sharp.Pin
        AppIcon.MULTIMEDIA -> Icons.Sharp.MusicNote
        AppIcon.PRESENTER -> Icons.Sharp.Slideshow
        AppIcon.GAMEPAD -> Icons.Sharp.Gamepad
        AppIcon.PAD_BUILDER -> Icons.Sharp.SportsEsports
        AppIcon.CONNECT -> Icons.Sharp.Bluetooth
        AppIcon.HAPTICS -> Icons.Sharp.Vibration
        AppIcon.HELP -> Icons.AutoMirrored.Sharp.HelpOutline
    }
}

/**
 * Per-control icon glyph library (emoji-based so it serializes into the
 * existing `icon: String` fields of widgets/gamepad controls — every control
 * remains movable/resizable/colorable per the customization rules).
 */
object ControlGlyphs {
    val ALL: List<String> = listOf(
        "🎮", "🕹️", "⌨️", "🖱️", "👆", "✊", "⚡", "🔥", "💥", "⭐",
        "🎯", "🏹", "🔫", "🗡️", "🛡️", "❤️", "💣", "🚀", "🏎️", "🛞",
        "⬆️", "⬇️", "⬅️", "➡️", "🔄", "⏸️", "▶️", "⏩", "🔊", "🔇",
        "🗺️", "🎒", "🔧", "💬", "👁️", "🦘", "🏃", "🚗", "✋", "☰"
    )
}
