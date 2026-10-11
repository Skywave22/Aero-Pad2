package com.bluepilot.remote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = PilotBlue,
    onPrimary = Color.White,
    primaryContainer = PilotBlueDark,
    onPrimaryContainer = Color.White,
    secondary = PilotCyan,
    onSecondary = Color.Black,
    background = InkBackground,
    onBackground = Color(0xFFE6EAF5),
    surface = InkSurface,
    onSurface = Color(0xFFE6EAF5),
    surfaceVariant = InkSurfaceHigh,
    onSurfaceVariant = Color(0xFFAAB4CE),
    outline = InkOutline,
    error = StatusError,
    onError = Color.White
)

private val LightScheme = lightColorScheme(
    primary = PilotBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FF),
    onPrimaryContainer = PilotBlueDark,
    secondary = PilotCyan,
    onSecondary = Color.Black,
    background = MistBackground,
    onBackground = Color(0xFF16203A),
    surface = MistSurface,
    onSurface = Color(0xFF16203A),
    surfaceVariant = MistSurfaceHigh,
    onSurfaceVariant = Color(0xFF4A5878),
    outline = MistOutline,
    error = StatusError,
    onError = Color.White
)

/**
 * App theme wrapper.
 * `darkTheme` follows the system for now; Module 3 wires it to the user's
 * Light/Dark/System setting, and Module 5 replaces the fixed schemes with
 * the editable skin engine.
 */
@Composable
fun BluePilotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = BluePilotTypography,
        content = content
    )
}
