package com.aeropad.remote.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Round 19 — the AeroPad icon set.
 *
 * Every control on the home screen used `Icons.Rounded.*` from Material, which
 * is the single most recognisable "default template" signal an Android app can
 * send: eight identical circles in eight identical colours. These are drawn
 * here instead — a consistent angular language (cut corners, one 45° motif,
 * solid silhouettes with EvenOdd cut-outs) so the dashboard reads as designed
 * rather than assembled.
 *
 * All shapes are authored on a 24×24 grid and filled with black; `Icon(...)`
 * tints them, so one vector serves light and dark themes.
 */

private fun PathBuilder.poly(vararg pts: Float) {
    if (pts.size < 6) return
    moveTo(pts[0], pts[1])
    var i = 2
    while (i < pts.size - 1) {
        lineTo(pts[i], pts[i + 1])
        i += 2
    }
    close()
}

private fun PathBuilder.rect(x: Float, y: Float, w: Float, h: Float) {
    moveTo(x, y)
    lineTo(x + w, y)
    lineTo(x + w, y + h)
    lineTo(x, y + h)
    close()
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, true, true, cx + r, cy)
    arcTo(r, r, 0f, true, true, cx - r, cy)
    close()
}

private fun vector(
    name: String,
    block: PathBuilder.() -> Unit
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.EvenOdd,
        pathBuilder = block
    )
}.build()

object AeroIcons {

    /**
     * The brand mark: a cut-corner shield holding a pointer. Used at the top of
     * Home instead of a plain wordmark, so the app has something to recognise
     * at a glance in a screenshot.
     */
    val Mark: ImageVector = vector("AeroMark") {
        poly(5f, 2f, 19f, 2f, 22f, 5f, 22f, 16f, 12f, 22f, 2f, 16f, 2f, 5f)
        // pointer cut-out
        poly(9.4f, 6.5f, 9.4f, 15.5f, 11.8f, 13.4f, 13.4f, 17.2f, 15.2f, 16.4f, 13.6f, 12.7f, 16.9f, 12.2f)
    }

    /** Trackpad: cut-corner body with a scroll slot and a press bar. */
    val Mouse: ImageVector = vector("AeroMouse") {
        poly(9f, 2f, 15f, 2f, 18f, 6f, 18f, 18f, 15f, 22f, 9f, 22f, 6f, 18f, 6f, 6f)
        rect(10.9f, 4.6f, 2.2f, 5.2f)          // wheel slot
        poly(9.4f, 17.2f, 14.6f, 17.2f, 13.6f, 20f, 10.4f, 20f) // click pad
    }

    /** Air mouse: pointer with motion ticks. */
    val AirMouse: ImageVector = vector("AeroAir") {
        poly(4f, 2.5f, 4f, 16.5f, 8.2f, 13.2f, 10.6f, 20f, 13.4f, 18.9f, 11f, 12.4f, 16.2f, 11.6f)
        rect(18.2f, 3.4f, 3.6f, 1.9f)
        rect(19.4f, 6.6f, 3.4f, 1.9f)
    }

    /** Keyboard: a key bed with real key cut-outs. */
    val Keyboard: ImageVector = vector("AeroKeyboard") {
        poly(2f, 5.5f, 22f, 5.5f, 22f, 18.5f, 2f, 18.5f)
        var row = 0
        while (row < 3) {
            val y = 7.6f + row * 3.1f
            var col = 0
            while (col < 5) {
                rect(4.2f + col * 3.4f, y, 2.4f, 2.0f)
                col++
            }
            row++
        }
        rect(7.4f, 16.0f, 9.2f, 1.7f) // space bar
    }

    /** Gamepad: two grips, a d-pad and two face buttons. */
    val Gamepad: ImageVector = vector("AeroGamepad") {
        poly(6.4f, 5.5f, 17.6f, 5.5f, 22f, 8.5f, 22f, 15.5f, 18.6f, 19f, 14.4f, 19f, 12.4f, 16.6f, 11.6f, 16.6f, 9.6f, 19f, 5.4f, 19f, 2f, 15.5f, 2f, 8.5f)
        rect(5.4f, 11.2f, 3.2f, 1.6f)  // d-pad horizontal
        rect(6.2f, 10.4f, 1.6f, 3.2f)  // d-pad vertical
        circle(16.4f, 10.6f, 1.3f)
        circle(19.0f, 12.6f, 1.3f)
    }

    /** Media: speaker cone with two sound bars. */
    val Media: ImageVector = vector("AeroMedia") {
        poly(2.5f, 9f, 6f, 9f, 10.5f, 4.5f, 10.5f, 19.5f, 6f, 15f, 2.5f, 15f)
        rect(14.2f, 8.2f, 1.9f, 7.6f)
        rect(18.2f, 5.4f, 1.9f, 13.2f)
    }

    /** Presenter: screen with a stand and a pointer dot. */
    val Presenter: ImageVector = vector("AeroPresenter") {
        poly(1.8f, 3.2f, 22.2f, 3.2f, 22.2f, 15.4f, 1.8f, 15.4f)
        rect(10.2f, 16.6f, 3.6f, 2.6f)
        rect(7.6f, 19.2f, 8.8f, 1.8f)
        circle(12f, 9.3f, 2.4f)
    }

    /** TV display: a screen on a short stand, matching the angular AeroPad icon set. */
    val Television: ImageVector = vector("AeroTelevision") {
        poly(3f, 4f, 21f, 4f, 21f, 17f, 3f, 17f)
        rect(4.8f, 5.8f, 14.4f, 9.4f)
        poly(10.5f, 17f, 13.5f, 17f, 14.5f, 20f, 9.5f, 20f)
        rect(7.5f, 20f, 9f, 1.6f)
    }

    /**
     * Android target: a phone, not a monitor. It started as a TV, which made it
     * nearly identical to [Presenter] at 19 dp — two tiles in different sections
     * that looked like the same control.
     */
    val Screen: ImageVector = vector("AeroScreen") {
        poly(6.4f, 1.8f, 17.6f, 1.8f, 19.6f, 3.8f, 19.6f, 20.2f, 17.6f, 22.2f, 6.4f, 22.2f, 4.4f, 20.2f, 4.4f, 3.8f)
        rect(8.2f, 4.4f, 7.6f, 1.4f)    // earpiece
        rect(8.2f, 17.4f, 7.6f, 1.6f)   // nav bar
        circle(12f, 10.8f, 2.2f)        // screen cut-out
    }

    /** Text & clips: lines on a page with a torn corner. */
    val TextLines: ImageVector = vector("AeroText") {
        poly(3.4f, 2.4f, 17f, 2.4f, 20.6f, 6f, 20.6f, 21.6f, 3.4f, 21.6f)
        rect(6.2f, 7.4f, 11.6f, 1.8f)
        rect(6.2f, 11.0f, 11.6f, 1.8f)
        rect(6.2f, 14.6f, 7.6f, 1.8f)
    }

    /** Layouts: a 2×2 deck grid. */
    val Grid: ImageVector = vector("AeroGrid") {
        poly(2.6f, 2.6f, 11f, 2.6f, 11f, 11f, 2.6f, 11f)
        poly(13f, 2.6f, 21.4f, 2.6f, 21.4f, 11f, 13f, 11f)
        poly(2.6f, 13f, 11f, 13f, 11f, 21.4f, 2.6f, 21.4f)
        poly(13f, 13f, 21.4f, 13f, 21.4f, 21.4f, 13f, 21.4f)
    }

    /** Macros: the bolt, cut-corner style. */
    val Bolt: ImageVector = vector("AeroBolt") {
        poly(13.6f, 1.8f, 20.4f, 1.8f, 12.4f, 10.2f, 17.4f, 10.2f, 7.6f, 22.2f, 10.6f, 12.4f, 6.2f, 12.4f)
    }

    /** Full board: a keyboard with a full five rows. */
    val FullKeyboard: ImageVector = vector("AeroFullKeyboard") {
        poly(1.6f, 4.8f, 22.4f, 4.8f, 22.4f, 19.2f, 1.6f, 19.2f)
        var row = 0
        while (row < 4) {
            val y = 6.9f + row * 2.7f
            var col = 0
            while (col < 6) {
                rect(3.4f + col * 3.0f, y, 2.1f, 1.6f)
                col++
            }
            row++
        }
        rect(6.6f, 17.1f, 10.8f, 1.5f)
    }

    /** Gamepad builder: a pad next to a plus. */
    val GamepadPlus: ImageVector = vector("AeroGamepadPlus") {
        poly(5.6f, 7.0f, 17.0f, 7.0f, 20.4f, 9.6f, 20.4f, 16.2f, 17.6f, 19.4f, 14.2f, 19.4f, 12.6f, 17.4f, 11.4f, 17.4f, 9.8f, 19.4f, 6.4f, 19.4f, 3.6f, 16.2f, 3.6f, 9.6f)
        rect(5.6f, 12.6f, 3.0f, 1.5f)
        rect(6.35f, 11.85f, 1.5f, 3.0f)
        circle(15.4f, 12.2f, 1.2f)
        circle(17.8f, 14.2f, 1.2f)
        rect(17.6f, 2.2f, 4.6f, 1.9f)
        rect(18.95f, 0.85f, 1.9f, 4.6f)
    }

    /** Deck of shortcut cards. */
    val Deck: ImageVector = vector("AeroDeck") {
        poly(4.6f, 1.8f, 19.4f, 1.8f, 21.2f, 4.6f, 19.4f, 7.4f, 4.6f, 7.4f, 2.8f, 4.6f)
        poly(2.8f, 8.8f, 21.2f, 8.8f, 21.2f, 15.2f, 2.8f, 15.2f)
        poly(4.6f, 16.6f, 19.4f, 16.6f, 21.2f, 19.4f, 19.4f, 22.2f, 4.6f, 22.2f, 2.8f, 19.4f)
    }

    /** Combo: two overlapping zones. */
    val Combo: ImageVector = vector("AeroCombo") {
        poly(1.8f, 1.8f, 14.2f, 1.8f, 14.2f, 14.2f, 1.8f, 14.2f)
        poly(9.8f, 9.8f, 22.2f, 9.8f, 22.2f, 22.2f, 9.8f, 22.2f)
    }

    /** Layout frame with corner ticks. */
    val Frame: ImageVector = vector("AeroFrame") {
        rect(2.6f, 3.4f, 7.2f, 1.9f)
        rect(2.6f, 3.4f, 1.9f, 7.2f)
        rect(14.2f, 3.4f, 7.2f, 1.9f)
        rect(19.5f, 3.4f, 1.9f, 7.2f)
        rect(2.6f, 18.7f, 7.2f, 1.9f)
        rect(2.6f, 13.4f, 1.9f, 7.2f)
        rect(14.2f, 18.7f, 7.2f, 1.9f)
        rect(19.5f, 13.4f, 1.9f, 7.2f)
        rect(9.3f, 11.0f, 5.4f, 2.0f)
    }

    /** Health: an ECG trace. */
    val Pulse: ImageVector = vector("AeroPulse") {
        poly(1.6f, 11.2f, 5.6f, 11.2f, 8.2f, 4.6f, 11.6f, 19.4f, 14.6f, 9.4f, 17.0f, 13.6f, 22.4f, 13.6f)
        rect(1.6f, 13.2f, 4.0f, 1.0f)
        rect(18.4f, 13.2f, 4.0f, 1.0f)
    }

    /** Laptop / desktop host. */
    val Laptop: ImageVector = vector("AeroLaptop") {
        poly(3.4f, 3.4f, 20.6f, 3.4f, 20.6f, 15.4f, 3.4f, 15.4f)
        poly(1.6f, 16.6f, 22.4f, 16.6f, 22.4f, 19.4f, 1.6f, 19.4f)
    }

    /** Settings: two sliders. */
    val Sliders: ImageVector = vector("AeroSliders") {
        rect(2.4f, 4.5f, 19.2f, 1.9f)
        rect(2.4f, 11.05f, 19.2f, 1.9f)
        rect(2.4f, 17.6f, 19.2f, 1.9f)
        rect(6.2f, 2.6f, 2.6f, 5.7f)
        rect(14.4f, 9.15f, 2.6f, 5.7f)
        rect(8.8f, 15.7f, 2.6f, 5.7f)
    }

    /** Help: a block-built question mark (no arc guessing, stays crisp at 17 dp). */
    val Help: ImageVector = vector("AeroHelp") {
        rect(7.9f, 2.6f, 8.2f, 1.9f)    // top bar
        rect(14.2f, 4.5f, 1.9f, 4.0f)   // right shoulder
        rect(11.3f, 8.5f, 4.8f, 1.9f)   // step toward the stem
        rect(10.9f, 10.4f, 1.9f, 3.4f)  // stem
        circle(11.85f, 16.6f, 1.5f)     // dot
    }
}
