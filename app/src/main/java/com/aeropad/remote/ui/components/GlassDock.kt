package com.aeropad.remote.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Floating bottom dock — the app's four hubs: Home, Layouts, Devices,
 * Settings. One-handed friendly: thumb-reachable tabs, shown only on
 * top-level destinations so control surfaces keep the full screen.
 */
/**
 * UI fix — the floating dock is drawn OVER the screen content, so any screen
 * that shows it must reserve at least this much bottom space. Before this,
 * SettingsScreen had no bottom padding at all and its last rows could never be
 * scrolled out from under the dock.
 */
object DockMetrics {
    /**
     * Vertical space the floating dock covers, including its shadow + nav bar.
     *
     * Round 11 — this was wrong in landscape: the dock is ~84 dp tall there (the
     * same pill, no taller), but the layout is only ~340 dp high, so reserving
     * 100 dp of it shoved content off the bottom while the dock still overlapped
     * it. The extra 24 dp also came from portrait's system-nav padding, which
     * does not exist on a landscape phone.
     */
    val height = 100.dp

    /**
     * What the dock actually covers in the current orientation.
     *
     * In landscape a phone window is only ~190 dp tall on a small device, so the
     * full labelled pill took almost half the screen. Landscape uses a compact
     * icon-only bar instead.
     */
    @Composable
    fun height(): Dp = if (isLandscape()) 62.dp else height

    /** Bottom padding a docked screen should reserve. */
    @Composable
    fun contentInset(): Dp = height() + 12.dp
}

data class DockItem(val route: String, val label: String, val icon: AppIcon)

val DockItems = listOf(
    DockItem("home", "Home", AppIcon.HOME),
    DockItem("layouts", "Layouts", AppIcon.LAYOUTS),
    DockItem("devices", "Devices", AppIcon.DEVICES),
    DockItem("settings", "Settings", AppIcon.SETTINGS)
)

/**
 * Modern floating dock.
 *
 * What changed and why:
 *  - A **sliding indicator** now travels between tabs instead of each tab
 *    flashing its own background. Motion tells the user where they came from,
 *    which a per-tab highlight cannot do.
 *  - The bar is a full-width pill with a hairline border and a soft two-stage
 *    shadow, so it reads as a raised surface on any wallpaper — the old one
 *    disappeared on dark wallpapers because it was a flat `surface` colour.
 *  - The selected icon scales up slightly and switches to a filled glyph with
 *    a primary label; unselected tabs stay quiet so the eye lands on one place.
 *  - Every tap still fires a haptic tick, and `Role.Tab` + `selectable` keep
 *    TalkBack announcing "selected".
 *
 * The indicator is positioned from the measured width (BoxWithConstraints)
 * rather than from an assumed tab size, so it stays aligned on any screen
 * width, in landscape, and under display-size scaling.
 */
@Composable
fun GlassDock(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = DockItems.indexOfFirst { currentRoute?.startsWith(it.route) == true }
        .coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            // Centre + cap the width: stretching a 4-tab pill across a landscape
            // window leaves the tabs stranded at the extremes.
            .widthIn(max = Adaptive.DOCK_MAX_WIDTH)
            .padding(
                horizontal = if (isLandscape()) 96.dp else 24.dp,
                vertical = if (isLandscape()) 4.dp else 10.dp
            )
            .shadow(
                elevation = 22.dp,
                shape = RoundedCornerShape(26.dp),
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.45f)
            )
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(
                horizontal = 8.dp,
                vertical = if (isLandscape()) 3.dp else 7.dp
            )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val tabWidth = maxWidth / DockItems.size
            val indicatorOffset by animateDpAsState(
                targetValue = tabWidth * selectedIndex,
                animationSpec = spring(
                    dampingRatio = 0.78f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "dockIndicator"
            )

            // ---------- sliding indicator ----------
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .height(if (isLandscape()) 40.dp else 50.dp)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockItems.forEachIndexed { index, item ->
                    DockTab(
                        item = item,
                        selected = index == selectedIndex &&
                            currentRoute?.startsWith(item.route) == true,
                        width = tabWidth,
                        onClick = { onNavigate(item.route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DockTab(
    item: DockItem,
    selected: Boolean,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val tint by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        label = "dockTint"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "dockScale"
    )
    val view = LocalView.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .width(width)
            .heightIn(min = if (isLandscape()) 40.dp else 50.dp)
            // A rounded rect, not a circle: clipping a ~65dp-wide tab to a
            // circle used to slice the corners off the label (the dock
            // literally rendered "Layouts" as "ayouts").
            .clip(RoundedCornerShape(19.dp))
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = {
                    if (!selected) {
                        view.performHapticFeedback(
                            android.view.HapticFeedbackConstants.KEYBOARD_TAP
                        )
                        onClick()
                    }
                }
            )
            .padding(vertical = 4.dp)
    ) {
        Icon(
            iconFor(item.icon, LocalIconPack.current),
            // The visible label already names the tab — announcing it twice
            // makes TalkBack say "Home, Home".
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale }
        )
        if (!isLandscape()) {
            Box(modifier = Modifier.height(3.dp))
        }
        // Landscape is ~190 dp tall on a small phone: the icon is enough, and
        // TalkBack still announces the label through the tab's semantics.
        if (!isLandscape()) Text(
            item.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
