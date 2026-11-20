package com.aeropad.remote.ui.theme

import androidx.compose.ui.graphics.Color

/** Feature colors are stable identifiers, not connection or button states. */
enum class StudioTone(private val daylight: Color, private val night: Color) {
    VIOLET(Color(0xFFF0E4FA), Color(0xFF342747)),
    MINT(Color(0xFFDEF2DA), Color(0xFF233B30)),
    PEACH(Color(0xFFFFE2C1), Color(0xFF493026)),
    BLUE(Color(0xFFDDE4FA), Color(0xFF283455));

    fun container(dark: Boolean): Color = if (dark) night else daylight
}

object StudioPalette {
    val Blueprint = Color(0xFF242043)
    val BlueprintDeep = Color(0xFF191735)
    val BlueprintInk = Color(0xFFF9F6FF)
    val BlueprintCaption = Color(0xFFE2DCF1)
    val Lime = Color(0xFFAEFB72)
    val OnLime = Color(0xFF18240E)
    val BrandPlate = Color(0xFFE3CDF7)
}
