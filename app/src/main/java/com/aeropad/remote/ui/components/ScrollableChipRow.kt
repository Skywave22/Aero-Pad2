package com.aeropad.remote.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight

/**
 * Horizontally scrolling single row of chips that **tells the user it can
 * scroll**.
 *
 * Phase-1 finding: the Layouts template row, the Home "Quick Tools" row and
 * several chip rows in dialogs cut the last chip off the screen edge with no
 * hint that more existed, so users believed the list ended. This wrapper adds:
 *
 *  - a soft fade on whichever edge still has content hidden behind it, and
 *  - a small chevron on the right while there is more to the right,
 *
 * both of which disappear as soon as the user reaches that end. Used instead of
 * a bare `Row(...horizontalScroll())` wherever chips can overflow.
 */
@Composable
fun ScrollableChipRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    content: @Composable RowScope.() -> Unit
) {
    val scroll = rememberScrollState()
    val canScrollFwd = scroll.value < scroll.maxValue
    val canScrollBack = scroll.value > 0
    val fadeColor = MaterialTheme.colorScheme.surface

    // The chevron lives in its own slot so chips never scroll underneath it
    // (the earlier overlay version covered the last visible chip's label).
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll),
                horizontalArrangement = horizontalArrangement,
                verticalAlignment = Alignment.CenterVertically
            ) {
                content()
                // Round 14 — a real spacer, not padding. The chevron used to sit
                // on top of the last chip (screenshots showed "Paste on PC
                // (Ctrl+V)" cut to "Paste", "Full" cut to "Fu", Photoshop deck
                // cut to "Bler"). With the spacer the final chip can always be
                // scrolled clear of the chevron.
                if (canScrollFwd) Spacer(Modifier.width(CHEVRON_SLOT))
            }
            // The fades are anchored to the VIEWPORT, not to the scrolling
            // content: drawn inside the scroll they drifted with the chips and
            // could sit over a fully visible one.
            if (canScrollBack || canScrollFwd) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawWithContent {
                            drawContent()
                            val w = 16.dp.toPx()
                            if (canScrollBack) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        listOf(fadeColor, fadeColor.copy(alpha = 0f)),
                                        startX = 0f,
                                        endX = w
                                    ),
                                    size = Size(w, size.height),
                                    topLeft = Offset.Zero
                                )
                            }
                            if (canScrollFwd) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        listOf(fadeColor.copy(alpha = 0f), fadeColor),
                                        startX = size.width - w,
                                        endX = size.width
                                    ),
                                    size = Size(w, size.height),
                                    topLeft = Offset(size.width - w, 0f)
                                )
                            }
                        }
                )
            }
        }
        if (canScrollFwd) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "More options — scroll the row sideways",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(CHEVRON_SLOT)
            )
        }
    }
}

/** Width reserved for the chevron so chips never render underneath it. */
private val CHEVRON_SLOT = 20.dp
