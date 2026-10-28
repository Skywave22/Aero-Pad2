package com.aeropad.remote.ui.screens.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aeropad.remote.domain.LayoutEditorOps
import com.aeropad.remote.model.widgets.WidgetSpec
import com.aeropad.remote.model.widgets.WidgetType
import com.aeropad.remote.ui.widgets.RenderWidget
import com.aeropad.remote.ui.widgets.WidgetEvents
import com.aeropad.remote.viewmodel.LayoutEditorViewModel

/**
 * The Layout Editor — Edit Mode ⇄ Preview Mode.
 *
 * Edit mode:
 *  - tap widget = select (blue outline + resize handle)
 *  - drag widget body = move — smooth 1:1 tracking from the gesture-start
 *    frame (🧲 snap-to-grid is opt-in via the toolbar chip)
 *  - drag corner handle = resize (same smooth tracking)
 *  - palette row adds new widgets; style sheet edits selected widget
 * Preview mode: identical rendering, live HID behavior via WidgetInteractor.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LayoutEditorScreen(
    onBack: () -> Unit,
    viewModel: LayoutEditorViewModel = hiltViewModel()
) {
    val layout by viewModel.layout.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val previewMode by viewModel.previewMode.collectAsStateWithLifecycle()
    val dirty by viewModel.dirty.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val undoHistory by viewModel.undoHistory.collectAsStateWithLifecycle()
    val hasClipboard by viewModel.hasClipboard.collectAsStateWithLifecycle()
    val multiSelection by viewModel.multiSelection.collectAsStateWithLifecycle()
    val showGrid by viewModel.showGrid.collectAsStateWithLifecycle()

    var showStyleSheet by remember { mutableStateOf(false) }
    // SECTION 2 — profile metadata dialog (name / category / notes).
    var showMetaDialog by remember { mutableStateOf(false) }
    // P9.4 — long-press on undo button shows undo history list.
    var showUndoHistory by remember { mutableStateOf(false) }
    val snapToGrid by viewModel.snapToGrid.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                // One line is enough now that only two actions compete for the
                // bar, but ellipsis stays so a very long name cannot overflow.
                title = {
                    Text(
                        layout.name + if (dirty) " •" else "",
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Round 14 — this app bar carried FIVE 48 dp action
                    // buttons, which left the layout name about 40 dp wide:
                    // every layout rendered as "Ne..." so you could not tell
                    // which one you were editing. Undo stays (it is the one
                    // action used constantly, and long-press still opens the
                    // history), everything else moves into a menu.
                    var actionsOpen by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .combinedClickable(
                                enabled = canUndo && !previewMode,
                                onClick = { viewModel.undo() },
                                onLongClickLabel = "Show undo history",
                                onLongClick = { if (undoHistory.isNotEmpty()) showUndoHistory = true }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Undo,
                            contentDescription = "Undo (long-press for history)",
                            tint = if (canUndo && !previewMode) LocalContentColor.current
                            else LocalContentColor.current.copy(alpha = 0.38f)
                        )
                    }
                    IconButton(onClick = { viewModel.save() }) {
                        Icon(Icons.Rounded.Save, contentDescription = "Save")
                    }
                    Box {
                        IconButton(onClick = { actionsOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More editor actions")
                        }
                        DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Redo") },
                                enabled = canRedo && !previewMode,
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = null) },
                                onClick = { actionsOpen = false; viewModel.redo() }
                            )
                            DropdownMenuItem(
                                text = { Text(if (showGrid) "Hide grid" else "Show grid") },
                                enabled = !previewMode,
                                leadingIcon = { Icon(Icons.Rounded.Grid4x4, contentDescription = null) },
                                onClick = { actionsOpen = false; viewModel.toggleGrid() }
                            )
                            DropdownMenuItem(
                                text = { Text(if (previewMode) "Back to editing" else "Preview") },
                                leadingIcon = {
                                    Icon(
                                        if (previewMode) Icons.Rounded.Edit else Icons.Rounded.PlayArrow,
                                        contentDescription = null
                                    )
                                },
                                onClick = { actionsOpen = false; viewModel.togglePreview() }
                            )
                        }
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
                        // Round 6 — snap-to-grid is opt-in; default = free smooth move.
                        AssistChip(
                            onClick = { viewModel.toggleSnap() },
                            label = { Text(if (snapToGrid) "🧲 Snap on" else "🧲 Snap off") }
                        )
                        Text(
                            "Add:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        paletteEntries.forEach { (label, type) ->
                            AssistChip(onClick = { viewModel.addWidget(type) }, label = { Text(label) })
                        }
                        Text(
                            "Templates:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        AssistChip(
                            onClick = { viewModel.applyTemplate("gamepad") },
                            label = { Text("🎮 Gamepad") }
                        )
                        AssistChip(
                            onClick = { viewModel.applyTemplate("pc_combo") },
                            label = { Text("💻 PC Combo") }
                        )
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
                            onClick = { viewModel.toggleSnap() },
                            label = { Text(if (snapToGrid) "🧲 On" else "🧲 Off") }
                        )
                        // Round 6 — fine nudge for precise placement.
                        AssistChip(onClick = { viewModel.nudgeSelected(-0.01f, 0f) }, label = { Text("←") })
                        AssistChip(onClick = { viewModel.nudgeSelected(0f, -0.01f) }, label = { Text("↑") })
                        AssistChip(onClick = { viewModel.nudgeSelected(0f, 0.01f) }, label = { Text("↓") })
                        AssistChip(onClick = { viewModel.nudgeSelected(0.01f, 0f) }, label = { Text("→") })
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
                            onClick = { viewModel.snapSelectedToEdge(com.aeropad.remote.domain.EditorProOps.Edge.LEFT) },
                            label = { Text("⇤ Edge") }
                        )
                        AssistChip(
                            onClick = { viewModel.snapSelectedToEdge(com.aeropad.remote.domain.EditorProOps.Edge.RIGHT) },
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
                com.aeropad.remote.ui.components.HintBar(
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
                    .onSizeChanged {
                        if (it.height > 0) viewModel.setCanvasAspect(it.width.toFloat() / it.height)
                    }
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

    // P9.4 — Undo history dialog opened on long-press of the Undo button.
    if (showUndoHistory) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showUndoHistory = false },
            title = { Text("Undo history") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (undoHistory.isEmpty()) {
                        Text("No undo steps recorded yet.")
                    } else {
                        Text(
                            "Tap a step to revert back to that snapshot:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        undoHistory.forEachIndexed { idx, entry ->
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    viewModel.revertUndoSteps(idx + 1)
                                    showUndoHistory = false
                                }
                            ) {
                                Text(entry)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showUndoHistory = false }) {
                    Text("Close")
                }
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
    val layout by viewModel.layout.collectAsStateWithLifecycle()

    // Round 6 — smooth editing: drags/resizes track 1:1 from the frame
    // captured at gesture start, so sub-grid movement is never lost and
    // widgets follow the finger pixel-perfectly.
    var moveBase by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var moveAcc by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var resizeBase by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var resizeAcc by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

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
                override fun onSwipe(widget: WidgetSpec, direction: com.aeropad.remote.domain.SwipeDirection) {
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
                                onDragStart = {
                                    viewModel.beginGesture()
                                    viewModel.select(widget.id)
                                    moveBase = androidx.compose.ui.geometry.Offset(frame.x, frame.y)
                                    moveAcc = androidx.compose.ui.geometry.Offset.Zero
                                },
                                onDrag = { dxPx, dyPx ->
                                    val base = moveBase ?: return@pointerInputSelectAndDrag
                                    moveAcc += androidx.compose.ui.geometry.Offset(dxPx, dyPx)
                                    viewModel.moveSelected(
                                        base.x + moveAcc.x / canvasWpx,
                                        base.y + moveAcc.y / canvasHpx
                                    )
                                },
                                onDragEnd = { moveBase = null }
                            )
                    )
                    // Resize handle (bottom-right corner) for the selected widget.
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(34.dp)   // round 6: bigger grab target
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .pointerInputResize(
                                    onStart = {
                                        viewModel.beginGesture()
                                        resizeBase = androidx.compose.ui.geometry.Offset(frame.w, frame.h)
                                        resizeAcc = androidx.compose.ui.geometry.Offset.Zero
                                    },
                                    onResize = { dxPx, dyPx ->
                                        val base = resizeBase ?: return@pointerInputResize
                                        resizeAcc += androidx.compose.ui.geometry.Offset(dxPx, dyPx)
                                        viewModel.resizeSelected(
                                            base.x + resizeAcc.x / canvasWpx,
                                            base.y + resizeAcc.y / canvasHpx
                                        )
                                    },
                                    onEnd = { resizeBase = null }
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
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit = {}
): Modifier = this
    .pointerInput(widgetId + "-tap") {
        detectTapGestures(
            onTap = { onSelect() },
            onLongPress = { onLongPress() }   // SECTION 2 — multi-select
        )
    }
    .pointerInput(widgetId + "-drag") {
        detectDragGestures(
            onDragStart = { onDragStart() },
            onDragEnd = { onDragEnd() },
            onDragCancel = { onDragEnd() }
        ) { change, dragAmount ->
            change.consume()
            onDrag(dragAmount.x, dragAmount.y)
        }
    }

private fun Modifier.pointerInputResize(
    onStart: () -> Unit,
    onResize: (Float, Float) -> Unit,
    onEnd: () -> Unit = {}
): Modifier = this.pointerInput("resize") {
    detectDragGestures(
        onDragStart = { onStart() },
        onDragEnd = { onEnd() },
        onDragCancel = { onEnd() }
    ) { change, dragAmount ->
        change.consume()
        onResize(dragAmount.x, dragAmount.y)
    }
}
