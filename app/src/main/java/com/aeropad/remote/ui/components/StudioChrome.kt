package com.aeropad.remote.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared editor/control header. Retains the system back action and original screen actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    TopAppBar(
        title = title,
        modifier = modifier.drawBehind {
            val y = size.height - 0.5f
            drawLine(colors.outlineVariant, Offset(0f, y), Offset(size.width, y))
        },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            titleContentColor = colors.onBackground,
            navigationIconContentColor = colors.onBackground,
            actionIconContentColor = colors.onSurfaceVariant
        )
    )
}

/** Static drafting grid shared by the pairing panel and precision control surfaces. */
fun Modifier.studioGrid(color: Color, spacing: Dp = 24.dp): Modifier = drawBehind {
    val step = spacing.toPx().coerceAtLeast(1f)
    val xStart = (size.width % step) / 2f
    val yStart = (size.height % step) / 2f
    var x = xStart
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height))
        x += step
    }
    var y = yStart
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y))
        y += step
    }
}
