package com.aeropad.remote.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Small, shared answers to "how much room do I have?".
 *
 * The whole app was laid out for a portrait phone: two-column home grid, a dock
 * stretched edge to edge, cards that fill the width. In landscape — which is how
 * you hold a phone when it is propped next to a keyboard or clamped to a TV —
 * that produced a stretched, sparse layout with everything pinned to the corners.
 *
 * These helpers keep the decisions in one place so every screen adapts the same
 * way instead of each one inventing its own breakpoint.
 */
object Adaptive {

    /** Phones/tablets above this width can afford extra columns. */
    val EXPANDED_WIDTH = 620.dp

    /** How wide a phone-shaped dock should ever get. */
    val DOCK_MAX_WIDTH = 520.dp

    /**
     * Content that should stay readable rather than stretch: forms, settings
     * groups, single-column lists.
     */
    val READABLE_MAX_WIDTH = 720.dp
}

/** True when the window is currently wider than it is tall. */
@Composable
fun isLandscape(): Boolean =
    LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

/** True when the window is wide enough for an extra column / two-pane layout. */
@Composable
fun isWideWindow(): Boolean {
    val cfg = LocalConfiguration.current
    return cfg.screenWidthDp >= Adaptive.EXPANDED_WIDTH.value
}

/**
 * Grid column count that respects the available width instead of a hard-coded
 * pair. Portrait phones keep 2 (the established look); landscape and tablets get
 * more, so tiles stop being half-screen-wide slabs.
 */
@Composable
fun adaptiveColumns(portrait: Int = 2, expanded: Int = 3, wide: Int = 4): Int {
    val cfg = LocalConfiguration.current
    val landscape = cfg.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val widthDp = cfg.screenWidthDp
    return when {
        !landscape && widthDp < Adaptive.EXPANDED_WIDTH.value -> portrait
        widthDp >= 900 -> wide
        else -> expanded
    }
}

/**
 * Centres content and stops it from stretching across a wide landscape window.
 * Without this, a settings card in landscape is 800dp of text on one line, which
 * is measurably harder to read than the same card at phone width.
 */
@Composable
fun ReadableWidth(
    maxWidth: Dp = Adaptive.READABLE_MAX_WIDTH,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            content()
        }
    }
}
