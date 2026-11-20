package com.aeropad.remote.ui.screens.controls

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aeropad.remote.ui.theme.LocalAppTheme
import com.aeropad.remote.ui.theme.StudioIndexStyle

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun IllustratedControlCard(
    entry: StudioEntry,
    onClick: () -> Unit,
    onHide: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val art = entry.art ?: return
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 118.dp)
            .clip(shape)
            .background(entry.tone.container(LocalAppTheme.current.isDark))
            .combinedClickable(role = Role.Button, onClickLabel = "Open ${entry.title}", onClick = onClick,
                onLongClickLabel = "Hide ${entry.title}", onLongClick = onHide)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(63.dp)) {
            Image(painterResource(art), contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.Center).widthIn(max = 106.dp).fillMaxWidth().height(60.dp))
            Text(entry.index.orEmpty(), modifier = Modifier.align(Alignment.TopStart),
                style = StudioIndexStyle, color = colors.onSurfaceVariant)
        }
        Text(entry.title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface, textAlign = TextAlign.Center)
        Text(entry.detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StudioEntryCard(entry: StudioEntry, onClick: () -> Unit, onHide: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 70.dp)
            .clip(shape).background(colors.surface).border(1.dp, colors.outlineVariant, shape)
            .combinedClickable(role = Role.Button, onClickLabel = "Open ${entry.title}", onClick = onClick,
                onLongClickLabel = "Hide ${entry.title}", onLongClick = onHide)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(32.dp).clip(MaterialTheme.shapes.small)
            .background(entry.tone.container(LocalAppTheme.current.isDark)), contentAlignment = Alignment.Center) {
            Icon(entry.icon, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
            Text(entry.detail, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, contentDescription = null,
            tint = colors.onSurfaceVariant, modifier = Modifier.size(14.dp))
    }
}
