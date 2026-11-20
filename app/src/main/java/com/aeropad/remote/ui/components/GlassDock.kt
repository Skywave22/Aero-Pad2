package com.aeropad.remote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aeropad.remote.ui.navigation.Routes
import com.aeropad.remote.ui.theme.AeroIcons

private data class DockItem(val route: String, val label: String, val icon: AppIcon)

private val dockItems = listOf(
    DockItem(Routes.HOME, "Home", AppIcon.HOME),
    DockItem(Routes.CONTROLS, "Controls", AppIcon.PAD_BUILDER),
    DockItem(Routes.LAYOUTS, "Layouts", AppIcon.LAYOUTS),
    DockItem(Routes.SETTINGS, "Settings", AppIcon.SETTINGS)
)

/** In-flow Studio tab bar. Control surfaces use the available height without this bar. */
@Composable
fun GlassDock(currentRoute: String?, onNavigate: (String) -> Unit, modifier: Modifier = Modifier, hapticsEnabled: Boolean = true) {
    val compact = isLandscape()
    val colors = MaterialTheme.colorScheme
    val pack = LocalIconPack.current
    val haptic = rememberHaptic(hapticsEnabled)
    val activePlate = colors.primary.copy(alpha = 0.26f).compositeOver(colors.surface)
    Column(modifier.fillMaxWidth().background(colors.surface), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalDivider(color = colors.outlineVariant)
        Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().selectableGroup().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            dockItems.forEach { item ->
                val selected = currentRoute == item.route
                val tint = if (selected) colors.primary else colors.onSurfaceVariant
                Column(Modifier.weight(1f)
                    .heightIn(min = if (compact) 48.dp else 64.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .selectable(selected = selected, role = Role.Tab, onClick = { if (!selected) { haptic(); onNavigate(item.route) } })
                    .then(if (compact) Modifier.semantics { contentDescription = item.label } else Modifier)
                    .padding(horizontal = 3.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)) {
                    Box(Modifier.fillMaxWidth(.85f).height(if (compact) 32.dp else 34.dp)
                        .clip(CircleShape).then(if (selected) Modifier.background(activePlate) else Modifier),
                        contentAlignment = Alignment.Center) {
                        val image = if (item.route == Routes.LAYOUTS && pack == IconPack.ROUNDED) AeroIcons.Deck
                            else iconFor(item.icon, if (pack == IconPack.ROUNDED) IconPack.OUTLINED else pack)
                        Icon(image, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    }
                    if (!compact) Text(item.label, color = tint, style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
