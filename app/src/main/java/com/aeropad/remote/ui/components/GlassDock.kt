package com.aeropad.remote.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
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
    /** Vertical space the floating dock covers, including its shadow + nav bar. */
    val height = 96.dp
}

data class DockItem(val route: String, val label: String, val icon: AppIcon)

val DockItems = listOf(
    DockItem("home", "Home", AppIcon.HOME),
    DockItem("layouts", "Layouts", AppIcon.LAYOUTS),
    DockItem("devices", "Devices", AppIcon.DEVICES),
    DockItem("settings", "Settings", AppIcon.SETTINGS)
)

@Composable
fun GlassDock(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 40.dp, vertical = 10.dp)
            .shadow(DepthLayer.FLOATING, CircleShape, clip = false)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockItems.forEach { item ->
                val selected = currentRoute?.startsWith(item.route) == true
                val tint by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "dockTint"
                )
                // M4 fix: use Modifier.selectable with Role.Tab so TalkBack announces
                // selected tab state, and set Icon contentDescription = null to prevent
                // duplicate "Home, Home" announcements alongside the visible Text label.
                val view = LocalView.current
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        // UI fix — this was .clip(CircleShape). Each tab is only
                        // ~65dp wide, so clipping it to a circle cut the bottom
                        // corners off the label: the dock literally rendered
                        // "Layouts" as "ayouts". A rounded rect matches the
                        // dock's own shape and cannot clip the text.
                        .clip(RoundedCornerShape(16.dp))
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = {
                                if (!selected) {
                                    view.performHapticFeedback(
                                        android.view.HapticFeedbackConstants.KEYBOARD_TAP
                                    )
                                    onNavigate(item.route)
                                }
                            }
                        )
                        .padding(vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                else Color.Transparent,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            iconFor(item.icon, LocalIconPack.current),
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tint,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
