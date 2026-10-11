package com.bluepilot.remote.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Grid4x4
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.domain.LayoutEditorOps
import com.bluepilot.remote.model.widgets.WidgetSpec
import com.bluepilot.remote.model.widgets.WidgetType
import com.bluepilot.remote.ui.widgets.RenderWidget
import com.bluepilot.remote.ui.widgets.WidgetEvents
import com.bluepilot.remote.viewmodel.LayoutEditorViewModel

/**
 * The Layout Editor — Edit Mode ⇄ Preview Mode.
 *
 * Edit mode:
 *  - tap widget = select (blue outline + resize handle)
 *  - drag widget body = move (grid snap)
 *  - drag corner handle = resize
 *  - palette row adds new widgets; style sheet edits selected widget
 * Preview mode: identical rendering, live HID behavior via WidgetInteractor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutEditorScreen(
    onBack: () -> Unit,
    viewModel: LayoutEditorViewModel = hiltViewModel()
) {
    val layout by viewModel.layout.collectAsState()
    val selectedId by viewModel.selectedId.collectAsState()
    val previewMode by viewModel.previewMode.collectAsState()
    val dirty by viewModel.dirty.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val canRedo by viewModel.canRedo.collectAsState()
    val hasClipboard by viewModel.hasClipboard.collectAsState()
    val multiSelection by viewModel.multiSelection.collectAsState()
    val showGrid by viewModel.showGrid.collectAsState()

    var showStyleSheet by remember { mutableStateOf(false) }
    // SECTION 2 — profile metadata dialog (name / category / notes).
    var showMetaDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(layout.name + if (dirty) " •" else "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.undo() }, enabled = canUndo && !previewMode) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo")
                    }
                    // SECTION 2 — redo
                    IconButton(onClick = { viewModel.redo() }, enabled = canRedo && !previewMode) {
                        Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Redo")
                    }
                    // SECTION 2 — grid overlay toggle
                    IconButton(onClick = { viewModel.toggleGrid() }, enabled = !previewMode) {
                        Icon(
                            Icons.Rounded.Grid4x4,
                            contentDescription = if (showGrid) "Hide grid" else "Show grid",
                            tint = if (showGrid) MaterialTheme.colorScheme.primary
                            else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { viewModel.togglePreview() }) {
                        Icon(
                            if (previewMode) Icons.Rounded.Edit else Icons.Rounded.PlayArrow,
                            contentDescription = if (previewMode) "Edit mode" else "Preview mode"
                        )
                    }
                    IconButton(onClick = { viewModel.save() }) {
                        Icon(Icons.Rounded.Save, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ---------- Palette / selected-widget toolbar (edit mode only) ----------
            if (!previewMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedId == null) {
                        // SECTION 2 — rename/category/notes
                        AssistChip(onClick = { showMetaDialog = true }, label = { Text("ℹ Info") })
                        Text(
                            "Add:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        paletteEntries.forEach { (label, type) ->
                            AssistChip(onClick = { viewModel.addWidget(type) }, label = { Text(label) })
                        }
                        Text(
                            "Zones:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AssistChip(
                            onClick = {
                                viewModel.applyZoneSplit(
                                    LayoutEditorOps.ZoneSplit.TOP_BOTTOM_THIRDS,
                                    listOf(WidgetType.TRACKPAD, WidgetType.BUTTON)
                                )
                            },
                            label = { Text("Pad + keys") }
                        )
                        AssistChip(
                            onClick = {
                                viewModel.applyZoneSplit(
                                    LayoutEditorOps.ZoneSplit.HORIZONTAL_2,
                                    listOf(WidgetType.TRACKPAD, WidgetType.DPAD)
                                )
                            },
                            label = { Text("Split H") }
                        )
                        AssistChip(
                            onClick = {
                                viewModel.applyZoneSplit(
                                    LayoutEditorOps.ZoneSplit.VERTICAL_2,
                                    listOf(WidgetType.JOYSTICK, WidgetType.BUTTON)
                                )
                            },
                            label = { Text("Split V") }
                        )
                        AssistChip(
                            onClick = {
                                viewModel.applyZoneSplit(
                                    LayoutEditorOps.ZoneSplit.GRID_2X2,
                                    listOf(WidgetType.BUTTON, WidgetType.BUTTON, WidgetType.BUTTON, WidgetType.BUTTON)
                                )
                            },
                            label = { Text("Grid 2×2") }
                        )
                        AssistChip(
                            onClick = {
                                viewModel.applyZoneSplit(
                                    LayoutEditorOps.ZoneSplit.QUARTER_MAIN,
                                    listOf(WidgetType.TRACKPAD, WidgetType.SLIDER, WidgetType.BUTTON)
                                )
                            },
                            label = { Text("¾ + column") }
                        )
                        AssistChip(
                            onClick = {
                                viewModel.applyZoneSplit(
                                    LayoutEditorOps.ZoneSplit.CORNER_ZONES,
                                    listOf(
                                        WidgetType.BUTTON, WidgetType.BUTTON,
                                        WidgetType.BUTTON, WidgetType.BUTTON,
                                        WidgetType.GESTURE_ZONE
                                    )
                                )
                            },
                            label = { Text("Corners") }
                        )
                    } else {
                        AssistChip(onClick = { showStyleSheet = true }, label = { Text("Style & action") })
                        AssistChip(
                            onClick = { viewModel.duplicateSelected() },
                            label = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(16.dp)) }
                        )
                        // SECTION 2 — copy / paste
                        AssistChip(onClick = { viewModel.copySelected() }, label = { Text("Copy") })
                        if (hasClipboard) {
                            AssistChip(onClick = { viewModel.pasteClipboard() }, label = { Text("Paste") })
                        }
                        // SECTION 2 — layering
                        AssistChip(onClick = { viewModel.bringToFront() }, label = { Text("To front") })
                        AssistChip(onClick = { viewModel.sendToBack() }, label = { Text("To back") })
                        // SECTION 2 — alignment
                        AssistChip(onClick = { viewModel.centerSelectedH() }, label = { Text("Center H") })
                        AssistChip(onClick = { viewModel.centerSelectedV() }, label = { Text("Center V") })
                        AssistChip(
                            onClick = { viewModel.snapSelectedToEdge(com.bluepilot.remote.domain.EditorProOps.Edge.LEFT) },
                            label = { Text("⇤ Edge") }
                        )
                        AssistChip(
                            onClick = { viewModel.snapSelectedToEdge(com.bluepilot.remote.domain.EditorProOps.Edge.RIGHT) },
                            label = { Text("Edge ⇥") }
                        )
                        // SECTION 2 — ungroup (shown when selected widget is grouped)
                        if (viewModel.selectedWidget()?.groupId?.isNotBlank() == true) {
                            AssistChip(onClick = { viewModel.ungroupSelected() }, label = { Text("Ungroup") })
                        }
                        AssistChip(
                            onClick = { viewModel.removeSelected() },
                            label = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, Modifier.size(16.dp)) }
                        )
                        AssistChip(onClick = { viewModel.select(null) }, label = { Text("Deselect") })
                    }
                }
            }

            // SECTION 4 — discoverability hint for the hidden gesture.
            if (!previewMode && multiSelection.isEmpty() && selectedId == null && layout.widgets.isNotEmpty()) {
                com.bluepilot.remote.ui.components.HintBar(
                    "Tap = select • drag = move • long-press = multi-select"
                )
            }

            // ---------- SECTION 2: multi-select action bar ----------
            if (!previewMode && multiSelection.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${multiSelection.size} selected:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    AssistChip(onClick = { viewModel.groupMultiSelection() }, label = { Text("Group") })
                    AssistChip(onClick = { viewModel.duplicateMultiSelection() }, label = { Text("Duplicate") })
                    if (multiSelection.size >= 3) {
                        AssistChip(onClick = { viewModel.distributeMultiH() }, label = { Text("Spread H") })
                        AssistChip(onClick = { viewModel.distributeMultiV() }, label = { Text("Spread V") })
                    }
                    AssistChip(onClick = { viewModel.deleteMultiSelection() }, label = { Text("Delete all") })
                    AssistChip(onClick = { viewModel.clearMultiSelect() }, label = { Text("Clear") })
                }
            }

            // ---------- Canvas ----------
            EditorCanvas(
                viewModel = viewModel,
                previewMode = previewMode,
                selectedId = selectedId,
                multiSelection = multiSelection,
                showGrid = showGrid,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            )
        }
    }

    if (showStyleSheet && selectedId != null) {
        ModalBottomSheet(onDismissRequest = { showStyleSheet = false }) {
            WidgetStylePanel(viewModel = viewModel)
            Spacer(Modifier.height(24.dp))
        }
    }

    // SECTION 2 — profile metadata editor (rename + category + notes).
    if (showMetaDialog) {
        var nameField by remember(layout.name) { mutableStateOf(layout.name) }
        var categoryField by remember(layout.category) { mutableStateOf(layout.category) }
        var notesField by remember(layout.notes) { mutableStateOf(layout.notes) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showMetaDialog = false },
            title = { Text("Layout info") },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = nameField, onValueChange = { nameField = it },
                        label = { Text("Name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = categoryField, onValueChange = { categoryField = it },
                        label = { Text("Category (e.g. FPS, Media)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = notesField, onValueChange = { notesField = it },
                        label = { Text("Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.rename(nameField)
                    viewModel.setCategory(categoryField)
                    viewModel.setNotes(notesField)
                    showMetaDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showMetaDialog = false }) { Text("Cancel") }
            }
        )
    }
}

private val paletteEntries = listOf(
    "Button" to WidgetType.BUTTON,
    "Trackpad" to WidgetType.TRACKPAD,
    "Scroll" to WidgetType.SCROLL_STRIP,
    "Joystick" to WidgetType.JOYSTICK,
    "Slider" to WidgetType.SLIDER,
    "D-pad" to WidgetType.DPAD,
    "Gestures" to WidgetType.GESTURE_ZONE
)

// ----------------------------------------------------------------------
// Canvas
// ----------------------------------------------------------------------

@Composable
private fun EditorCanvas(
    viewModel: LayoutEditorViewModel,
    previewMode: Boolean,
    selectedId: String?,
    multiSelection: Set<String> = emptySet(),
    showGrid: Boolean = false,
    modifier: Modifier = Modifier
) {
    val layout by viewModel.layout.collectAsState()

    BoxWithConstraints(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.background,
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(12.dp)
            )
    ) {
        val canvasW = maxWidth
        val canvasH = maxHeight
        val canvasWpx = constraints.maxWidth.toFloat()
        val canvasHpx = constraints.maxHeight.toFloat()

        // SECTION 2 — grid overlay: one Canvas draw, no layout cost.
        if (showGrid && !previewMode && layout.gridSize > 0f) {
            val gridColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val step = layout.gridSize
                var gx = step
                while (gx < 1f) {
                    drawLine(gridColor, Offset(gx * size.width, 0f), Offset(gx * size.width, size.height), 1f)
                    gx += step
                }
                var gy = step
                while (gy < 1f) {
                    drawLine(gridColor, Offset(0f, gy * size.height), Offset(size.width, gy * size.height), 1f)
                    gy += step
                }
            }
        }

        val events = remember(previewMode) {
            object : WidgetEvents {
                override fun onPrimary(widget: WidgetSpec) { if (previewMode) viewModel.interactor.primary(widget) }
                override fun onSecondary(widget: WidgetSpec) { if (previewMode) viewModel.interactor.secondary(widget) }
                override fun onTrackpadStart() { if (previewMode) viewModel.interactor.trackpadStart() }
                override fun onTrackpadDelta(dx: Float, dy: Float) { if (previewMode) viewModel.interactor.trackpadDelta(dx, dy) }
                override fun onScrollDelta(dy: Float) { if (previewMode) viewModel.interactor.scrollDelta(dy) }
                override fun onJoystick(x: Float, y: Float) { if (previewMode) viewModel.interactor.joystick(x, y) }
                override fun onDpad(dirX: Int, dirY: Int) { if (previewMode) viewModel.interactor.dpad(dirX, dirY) }
                override fun onSwipe(widget: WidgetSpec, direction: com.bluepilot.remote.domain.SwipeDirection) {
                    if (previewMode) viewModel.interactor.swipe(widget, direction)
                }
                override fun onTwoFingerTap(widget: WidgetSpec) {
                    if (previewMode) viewModel.interactor.twoFingerTap(widget)
                }
            }
        }

        layout.widgets.filter { it.style.visible || !previewMode }.forEach { widget ->
            val frame = widget.frame.sanitized()
            val isSelected = widget.id == selectedId && !previewMode
            val inMulti = widget.id in multiSelection && !previewMode

            Box(
                modifier = Modifier
                    .offset(x = canvasW * frame.x, y = canvasH * frame.y)
                    .size(width = canvasW * frame.w, height = canvasH * frame.h)
            ) {
                if (previewMode) {
                    RenderWidget(widget = widget, events = events, modifier = Modifier.fillMaxSize())
                } else {
                    // Edit mode: static render + selection/drag overlay on top.
                    RenderWidget(widget = widget, events = noOpEvents, modifier = Modifier.fillMaxSize())
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (isSelected) Modifier.border(
                                    2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(widget.style.cornerRadius.dp)
                                ) else Modifier
                            )
                            // SECTION 2 — multi-selected: dashed-look secondary outline
                            .then(
                                if (inMulti) Modifier.border(
                                    2.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(widget.style.cornerRadius.dp)
                                ) else Modifier
                            )
                            // SECTION 2 — grouped widgets get a subtle tint
                            .then(
                                if (widget.groupId.isNotBlank()) Modifier.background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                                    RoundedCornerShape(widget.style.cornerRadius.dp)
                                ) else Modifier
                            )
                            .then(
                                if (!widget.style.visible) Modifier.background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                    RoundedCornerShape(widget.style.cornerRadius.dp)
                                ) else Modifier
                            )
                            .pointerInputSelectAndDrag(
                                widgetId = widget.id,
                                onSelect = { viewModel.select(widget.id) },
                                onLongPress = { viewModel.toggleMultiSelect(widget.id) },
                                onDragStart = { viewModel.beginGesture(); viewModel.select(widget.id) },
                                onDrag = { dxPx, dyPx ->
                                    val current = viewModel.layout.value.widgets.firstOrNull { it.id == widget.id } ?: return@pointerInputSelectAndDrag
                                    viewModel.moveSelected(
                                        current.frame.x + dxPx / canvasWpx,
                                        current.frame.y + dyPx / canvasHpx
                                    )
                                }
                            )
                    )
                    // Resize handle (bottom-right corner) for the selected widget.
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(26.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .pointerInputResize(
                                    onStart = { viewModel.beginGesture() },
                                    onResize = { dxPx, dyPx ->
                                        val current = viewModel.layout.value.widgets.firstOrNull { it.id == widget.id } ?: return@pointerInputResize
                                        viewModel.resizeSelected(
                                            current.frame.w + dxPx / canvasWpx,
                                            current.frame.h + dyPx / canvasHpx
                                        )
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}

/** No-op events for static edit-mode rendering. */
private val noOpEvents = object : WidgetEvents {
    override fun onPrimary(widget: WidgetSpec) {}
    override fun onSecondary(widget: WidgetSpec) {}
    override fun onTrackpadStart() {}
    override fun onTrackpadDelta(dx: Float, dy: Float) {}
    override fun onScrollDelta(dy: Float) {}
    override fun onJoystick(x: Float, y: Float) {}
    override fun onDpad(dirX: Int, dirY: Int) {}
}

// Gesture helpers -------------------------------------------------------

private fun Modifier.pointerInputSelectAndDrag(
    widgetId: String,
    onSelect: () -> Unit,
    onLongPress: () -> Unit = {},
    onDragStart: () -> Unit,
    onDrag: (Float, Float) -> Unit
): Modifier = this
    .pointerInput(widgetId + "-tap") {
        detectTapGestures(
            onTap = { onSelect() },
            onLongPress = { onLongPress() }   // SECTION 2 — multi-select
        )
    }
    .pointerInput(widgetId + "-drag") {
        detectDragGestures(
            onDragStart = { onDragStart() }
        ) { change, dragAmount ->
            change.consume()
            onDrag(dragAmount.x, dragAmount.y)
        }
    }

private fun Modifier.pointerInputResize(
    onStart: () -> Unit,
    onResize: (Float, Float) -> Unit
): Modifier = this.pointerInput("resize") {
    detectDragGestures(
        onDragStart = { onStart() }
    ) { change, dragAmount ->
        change.consume()
        onResize(dragAmount.x, dragAmount.y)
    }
}
