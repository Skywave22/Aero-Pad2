package com.bluepilot.remote.ui.screens.layouts

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.model.widgets.WidgetSpec
import com.bluepilot.remote.ui.components.NotConnectedBanner
import com.bluepilot.remote.ui.widgets.LayoutCanvas
import com.bluepilot.remote.ui.widgets.WidgetEvents
import com.bluepilot.remote.viewmodel.LayoutsViewModel
import timber.log.Timber

/**
 * Layouts hub:
 *  - list of profiles (built-in + user)
 *  - tap = USE (live player)   - pencil = EDIT (Module 6 editor)
 *  - duplicate / delete / export per row, import + new via toolbar/FAB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutsScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: LayoutsViewModel = hiltViewModel()
) {
    val profiles by viewModel.filteredProfiles.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val active by viewModel.activeProfile.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val message by viewModel.message.collectAsState()
    val exportPayload by viewModel.exportPayload.collectAsState()

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // ---------- SAF: import ----------
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }.onFailure { Timber.e(it, "import read failed") }.getOrNull()
        viewModel.importFromJson(text)
    }

    // ---------- SAF: export ----------
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val payload = exportPayload
        if (uri != null && payload != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(payload.second.toByteArray())
                }
            }.onFailure { Timber.e(it, "export write failed") }
        }
        viewModel.consumeExport()
    }

    // Launch export picker when a payload is ready.
    LaunchedEffect(exportPayload) {
        exportPayload?.let { exportLauncher.launch(it.first) }
    }

    // Snackbar messages.
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val activeProfile = active
    if (activeProfile != null) {
        // ---------------- Player (use mode) ----------------
        // Swipe accumulation for combo-profile switching (top strip only,
        // so it never fights trackpad/joystick gestures on the canvas).
        val swipeAccum = remember { floatArrayOf(0f) }
        Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text(activeProfile.spec.name) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.closeProfile() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.switchProfile(-1) }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous layout")
                        }
                        IconButton(onClick = { viewModel.switchProfile(1) }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowForwardIos, contentDescription = "Next layout")
                        }
                    },
                    modifier = Modifier.pointerInput(activeProfile.id) {
                        detectHorizontalDragGestures(
                            onDragStart = { swipeAccum[0] = 0f },
                            onDragEnd = {
                                when {
                                    swipeAccum[0] < -120f -> viewModel.switchProfile(1)
                                    swipeAccum[0] > 120f -> viewModel.switchProfile(-1)
                                }
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            swipeAccum[0] += dragAmount
                        }
                    }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(8.dp)) {
                NotConnectedBanner(!isConnected)
                com.bluepilot.remote.ui.components.HintBar("Swipe the title bar left/right to switch layouts")
                // Perf: remember(viewModel) — stable events object, so
                // LayoutCanvas skips recomposition of unchanged widgets.
                val canvasEvents = remember(viewModel) {
                    object : WidgetEvents {
                        override fun onPrimary(widget: WidgetSpec) = viewModel.interactor.primary(widget)
                        override fun onSecondary(widget: WidgetSpec) = viewModel.interactor.secondary(widget)
                        override fun onTrackpadStart() = viewModel.interactor.trackpadStart()
                        override fun onTrackpadDelta(dx: Float, dy: Float) = viewModel.interactor.trackpadDelta(dx, dy)
                        override fun onScrollDelta(dy: Float) = viewModel.interactor.scrollDelta(dy)
                        override fun onJoystick(x: Float, y: Float) = viewModel.interactor.joystick(x, y)
                        override fun onDpad(dirX: Int, dirY: Int) = viewModel.interactor.dpad(dirX, dirY)
                        override fun onSwipe(widget: WidgetSpec, direction: com.bluepilot.remote.domain.SwipeDirection) =
                            viewModel.interactor.swipe(widget, direction)
                        override fun onTwoFingerTap(widget: WidgetSpec) =
                            viewModel.interactor.twoFingerTap(widget)
                    }
                }
                LayoutCanvas(
                    layout = activeProfile.spec,
                    events = canvasEvents,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        return
    }

    // ---------------- Profile list ----------------
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Layouts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = "Import layout")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.createNew { newId -> onEdit(newId) } }) {
                Icon(Icons.Rounded.Add, contentDescription = "New layout")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp)
        ) {
            item {
                Text(
                    text = "Tap a layout to use it • pencil to edit • + to create • ⇩ to import",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            // SECTION 2 — search/filter across name, category and notes.
            item {
                androidx.compose.material3.OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    singleLine = true,
                    placeholder = { Text("Search layouts (name, category, notes)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )
            }
            items(profiles, key = { it.id }) { profile ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.openProfile(profile.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // SECTION 2 — auto thumbnail: real widget frames painted
                        // at miniature scale (always in sync with the layout).
                        LayoutThumbnail(spec = profile.spec)
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile.spec.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${profile.spec.widgets.size} widgets" +
                                    (if (profile.spec.category.isNotBlank()) "  •  ${profile.spec.category}" else "") +
                                    (if (profile.isBuiltIn) "  •  built-in template" else ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (profile.spec.notes.isNotBlank()) {
                                Text(
                                    text = profile.spec.notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                        IconButton(onClick = { onEdit(profile.id) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { viewModel.duplicate(profile.id) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate")
                        }
                        IconButton(onClick = { viewModel.requestExport(profile.id) }) {
                            Icon(Icons.Rounded.FileUpload, contentDescription = "Export")
                        }
                        if (!profile.isBuiltIn) {
                            IconButton(onClick = { viewModel.delete(profile.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * SECTION 2 — auto-generated layout thumbnail: paints every widget frame
 * as a rounded mini-rect at 1/8 scale. Pure Canvas (no bitmaps, no
 * caching needed) and always pixel-true to the saved layout.
 */
@Composable
private fun LayoutThumbnail(spec: com.bluepilot.remote.model.widgets.LayoutSpec) {
    val bg = MaterialTheme.colorScheme.background
    val outline = MaterialTheme.colorScheme.outline
    val accent = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .size(width = 56.dp, height = 42.dp)
    ) {
        // Canvas backdrop
        drawRoundRect(
            color = bg,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
        )
        drawRoundRect(
            color = outline.copy(alpha = 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
        )
        // Widgets at fractional frames
        spec.widgets.take(com.bluepilot.remote.model.widgets.LayoutSpec.MAX_WIDGETS).forEach { w ->
            val f = w.frame
            drawRoundRect(
                color = accent.copy(alpha = 0.55f),
                topLeft = androidx.compose.ui.geometry.Offset(f.x * size.width, f.y * size.height),
                size = androidx.compose.ui.geometry.Size(f.w * size.width, f.h * size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
            )
        }
    }
}
