package com.aeropad.remote.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeropad.remote.R
import com.aeropad.remote.domain.CsvListCodec
import com.aeropad.remote.domain.StudioConnection
import com.aeropad.remote.domain.dashboardConnection
import com.aeropad.remote.model.HidConnectionState
import com.aeropad.remote.ui.components.rememberHaptic
import com.aeropad.remote.ui.components.studioGrid
import com.aeropad.remote.ui.navigation.Routes
import com.aeropad.remote.ui.screens.controls.IllustratedControlCard
import com.aeropad.remote.ui.screens.controls.StudioCatalog
import com.aeropad.remote.ui.screens.controls.StudioEntry
import com.aeropad.remote.ui.screens.controls.StudioEntryCard
import com.aeropad.remote.ui.theme.AeroIcons
import com.aeropad.remote.ui.theme.StudioIndexStyle
import com.aeropad.remote.ui.theme.StudioPalette
import com.aeropad.remote.viewmodel.ConnectionViewModel
import com.aeropad.remote.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onNavigate: (String) -> Unit, viewModel: ConnectionViewModel = hiltViewModel()) {
    val state by viewModel.connectionState.collectAsStateWithLifecycle()
    val permissionsGranted by viewModel.permissionsGranted.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val settingsVm: SettingsViewModel = hiltViewModel()
    val app by settingsVm.app.collectAsStateWithLifecycle()
    val haptic = rememberHaptic(app.touchVibrations)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showCustomize by rememberSaveable { mutableStateOf(false) }
    val hidden = remember(app.hiddenTiles) { CsvListCodec.decode(app.hiddenTiles).toSet() }
    val customizable = StudioCatalog.customizable
    fun isHidden(entry: StudioEntry) = entry.route in hidden || entry.legacyTitle in hidden
    fun hideAction(entry: StudioEntry): (() -> Unit)? {
        if (entry !in customizable) return null
        return {
            settingsVm.setDashboardTileVisible(entry.route, entry.legacyTitle, visible = false)
            scope.launch {
                if (snackbar.showSnackbar("Hidden '${entry.title}'", "Undo", duration = SnackbarDuration.Short)
                    == SnackbarResult.ActionPerformed
                ) settingsVm.setDashboardTileVisible(entry.route, entry.legacyTitle, visible = true)
            }
        }
    }
    fun connect() {
        haptic()
        onNavigate(if (!permissionsGranted || state == HidConnectionState.PermissionMissing) Routes.PERMISSIONS else Routes.CONNECTION)
    }

    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onPermissionsResult()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(permissionsGranted) { viewModel.initialize() }

    val connection = dashboardConnection(state, permissionsGranted)
    val connected = permissionsGranted && state.isConnected
    val quick = StudioCatalog.quick.filterNot(::isHidden)
    val studio = StudioCatalog.studioTools.filterNot(::isHidden)
    val more = StudioCatalog.homeSecondary.filterNot(::isHidden)
    val workspace = StudioCatalog.workspace.filterNot(::isHidden)

    Scaffold(containerColor = Color.Transparent, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            BoxWithConstraints(Modifier.widthIn(max = 880.dp).fillMaxSize()) {
                val fontScale = LocalDensity.current.fontScale
                val columns = when {
                    maxWidth >= 756.dp && fontScale <= 1.25f -> 4
                    maxWidth >= 304.dp && fontScale <= 1.45f -> 2
                    else -> 1
                }
                val regular = maxWidth >= 350.dp && fontScale <= 1.25f
                val inset = if (maxWidth < 350.dp) 16.dp else 24.dp
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(start = inset, end = inset, top = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().testTag("dashboardGrid")
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        StudioHeader(connection.badge, connected, roomForBadge = regular && maxWidth >= (if (connection.badge.length >= 10) 412.dp else 384.dp),
                            compact = !regular, onConnect = ::connect)
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        HomeTitle(compact = !regular, onCustomize = { showCustomize = true })
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ConnectionHero(connection, showArt = regular, onConnect = ::connect)
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        StudioSection("Quick controls", modifier = Modifier.padding(top = 10.dp), action = "View all",
                            onAction = { onNavigate(Routes.CONTROLS) })
                    }
                    items(quick, key = { it.route }) { entry ->
                        IllustratedControlCard(entry, onClick = { onNavigate(entry.route) }, onHide = hideAction(entry))
                    }
                    if (quick.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        TextButton(onClick = { showCustomize = true }) { Text("Show shortcuts") }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { StudioSection("Studio tools", Modifier.padding(top = 8.dp)) }
                    items(studio, key = { it.route }) { entry ->
                        StudioEntryCard(entry, onClick = { onNavigate(entry.route) }, onHide = hideAction(entry))
                    }
                    if (more.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { StudioSection("More controls", Modifier.padding(top = 8.dp)) }
                    items(more, key = { it.route }) { entry ->
                        StudioEntryCard(entry, onClick = { onNavigate(entry.route) }, onHide = hideAction(entry))
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { StudioSection("Workspace", Modifier.padding(top = 8.dp)) }
                    items(workspace, key = { it.route }) { entry ->
                        StudioEntryCard(entry, onClick = { onNavigate(entry.route) }, onHide = hideAction(entry))
                    }
                }
            }
        }
    }
    if (showCustomize) {
        AlertDialog(
            onDismissRequest = { showCustomize = false }, title = { Text("Customize dashboard") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Choose which shortcuts appear on Home. The Controls tab always shows the complete library.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    customizable.forEach { entry ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(entry.title, Modifier.weight(1f))
                            Switch(checked = !isHidden(entry),
                                onCheckedChange = { settingsVm.setDashboardTileVisible(entry.route, entry.legacyTitle, it) },
                                modifier = Modifier.semantics { contentDescription = "Show ${entry.title} on Home" })
                        }
                    }
                }
            },
            dismissButton = { if (hidden.isNotEmpty()) TextButton(onClick = { settingsVm.resetHiddenTiles() }) { Text("Reset all") } },
            confirmButton = { TextButton(onClick = { showCustomize = false }) { Text("Done") } }
        )
    }
}

@Composable
private fun StudioHeader(status: String, connected: Boolean, roomForBadge: Boolean, compact: Boolean, onConnect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = if (compact) 4.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(if (compact) 36.dp else 48.dp).clip(RoundedCornerShape(14.dp)).background(StudioPalette.BrandPlate),
                contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_studio_mark), contentDescription = null,
                    modifier = Modifier.size(if (compact) 25.dp else 34.dp))
            }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AeroPad", style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium.copy(fontSize = 31.sp, lineHeight = 36.sp),
                    color = colors.onBackground, modifier = Modifier.weight(1f, fill = false))
                if (roomForBadge) {
                    Text("STUDIO", style = StudioIndexStyle, color = colors.onSurfaceVariant,
                        modifier = Modifier.border(1.dp, colors.outline, RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Row(Modifier.clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Open Bluetooth connection", onClick = onConnect)
                .heightIn(min = 48.dp).padding(vertical = 7.dp)
                .border(1.dp, colors.outline, CircleShape).padding(horizontal = 9.dp, vertical = 5.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(6.dp).background(if (connected) StudioPalette.Lime else colors.onSurfaceVariant, CircleShape))
                Text(status, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = colors.outlineVariant)
    }
}

@Composable
private fun HomeTitle(compact: Boolean, onCustomize: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 4.dp, bottom = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("YOUR WORKSPACE", style = StudioIndexStyle, color = colors.primary)
            Text("01 / 04", style = StudioIndexStyle, color = colors.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Control center", modifier = Modifier.weight(1f).semantics { heading() }, color = colors.onBackground,
                style = if (compact) MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 34.sp) else MaterialTheme.typography.headlineLarge)
            IconButton(onClick = onCustomize, modifier = Modifier.size(48.dp).border(1.dp, colors.outline, MaterialTheme.shapes.small)) {
                Icon(AeroIcons.Sliders, "Choose dashboard tiles", tint = colors.onSurface)
            }
        }
    }
}

@Composable
private fun ConnectionHero(connection: StudioConnection, showArt: Boolean, onConnect: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
        .background(Brush.linearGradient(listOf(StudioPalette.Blueprint, StudioPalette.BlueprintDeep)))
        .studioGrid(StudioPalette.BlueprintInk.copy(alpha = .08f))) {
        Row(Modifier.fillMaxWidth().heightIn(min = if (showArt) 190.dp else 154.dp).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(5.dp).background(StudioPalette.Lime, CircleShape))
                    Text("CONNECTION HUB", color = StudioPalette.BlueprintCaption, style = StudioIndexStyle)
                }
                Text(connection.title, color = StudioPalette.BlueprintInk, style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() })
                connection.detail?.let { Text(it, color = StudioPalette.BlueprintCaption,
                    style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                Button(onClick = onConnect, modifier = Modifier.heightIn(min = 48.dp), shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StudioPalette.Lime, contentColor = StudioPalette.OnLime)) {
                    Text(connection.action, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(5.dp))
                    Icon(AeroIcons.Open, contentDescription = null, modifier = Modifier.size(14.dp))
                }
            }
            if (showArt) {
                Column(Modifier.weight(.92f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Image(painterResource(R.drawable.studio_hero_pad), contentDescription = null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(137.dp))
                    Text("FIG. 01", style = StudioIndexStyle, color = StudioPalette.BlueprintCaption)
                }
            }
        }
    }
}

@Composable
private fun StudioSection(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).semantics { heading() })
        if (action != null && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(action)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(15.dp))
            }
        }
    }
}
