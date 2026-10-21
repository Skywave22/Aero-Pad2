package com.aeropad.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
                .drawWithContent {
                    drawContent()
                    val w = 24.dp.toPx()
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
                // Leave room so the last chip never hides under the chevron.
                .padding(end = if (canScrollFwd) 22.dp else 0.dp),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        if (canScrollFwd) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "More",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}
