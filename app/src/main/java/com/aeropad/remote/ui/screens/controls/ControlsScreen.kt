package com.aeropad.remote.ui.screens.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aeropad.remote.ui.components.StudioTopBar
import com.aeropad.remote.ui.theme.AeroIcons
import com.aeropad.remote.ui.theme.StudioIndexStyle

/** Permanent control directory; dashboard visibility settings never filter this screen. */
@Composable
internal fun ControlsScreen(onBack: () -> Unit, onOpenDevices: () -> Unit, onNavigate: (String) -> Unit) {
    Scaffold(containerColor = Color.Transparent, topBar = {
        StudioTopBar(
            title = { Text("Control library") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            actions = { IconButton(onClick = onOpenDevices) { Icon(AeroIcons.Laptop, "Devices") } }
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            BoxWithConstraints(Modifier.widthIn(max = 800.dp).fillMaxSize()) {
                val twoColumns = maxWidth >= 560.dp && LocalDensity.current.fontScale <= 1.4f
                LazyVerticalGrid(columns = GridCells.Fixed(if (twoColumns) 2 else 1),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("CONTROL INDEX   /   02", style = StudioIndexStyle, color = MaterialTheme.colorScheme.primary)
                    }
                    StudioCatalog.sections.forEach { (label, entries) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(label, style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 8.dp).semantics { heading() })
                        }
                        items(entries, key = { it.route }) { entry ->
                            StudioEntryCard(entry, onClick = { onNavigate(entry.route) })
                        }
                    }
                }
            }
        }
    }
}
