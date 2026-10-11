package com.bluepilot.remote.ui.screens.macros

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.bluepilot.remote.model.HidConsumer
import com.bluepilot.remote.model.HidKeys
import com.bluepilot.remote.model.HidModifiers
import com.bluepilot.remote.model.macros.MacroSpec
import com.bluepilot.remote.model.macros.MacroStep
import com.bluepilot.remote.ui.components.GlassCard
import com.bluepilot.remote.ui.components.NotConnectedBanner
import com.bluepilot.remote.ui.theme.LocalAppTheme
import com.bluepilot.remote.viewmodel.MacrosViewModel

/**
 * Macros hub: list + step-sequence editor.
 * Editor: add key/chord/text/media/mouse/delay steps, reorder, delete,
 * test-play live, save to Room. Saved macros are bindable to any custom
 * button via the layout editor's action picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacrosScreen(
    onBack: () -> Unit,
    viewModel: MacrosViewModel = hiltViewModel()
) {
    val draft by viewModel.draft.collectAsState()

    if (draft != null) {
        MacroEditor(viewModel)
        return
    }

    val macros by viewModel.macros.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordedSteps by viewModel.recordedSteps.collectAsState()
    val spec = LocalAppTheme.current

    // Red glow color for recording state
    val recRedColor = Color(0xFFFF2D55)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (spec.monoFont) "MACROS" else "Macros",
                        style = if (spec.monoFont) MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace)
                        else MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                actions = {
                    // AEROPAD v1.0 #28 — panic button: instant macro cancel.
                    val isRunning by viewModel.isRunning.collectAsState()
                    if (isRunning) {
                        androidx.compose.material3.Button(
                            onClick = { viewModel.panicStop() },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) { Text("■ STOP") }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.newMacro() },
                containerColor = spec.primary
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "New macro", tint = spec.onPrimary)
            }
        },
        bottomBar = {
            // Frosted glass recorder bar — red-tinted when recording
            val recorderAlpha = if (isRecording) 0.18f else spec.surfaceAlpha * 0.8f
            val recorderBgColor = if (isRecording)
                recRedColor.copy(alpha = recorderAlpha)
            else
                spec.surface.copy(alpha = recorderAlpha)

            val recorderBorderColor = if (isRecording)
                recRedColor.copy(alpha = 0.6f)
            else
                spec.outline.copy(alpha = 0.3f)

            val recorderGlowModifier = if (isRecording) {
                Modifier.drawBehind {
                    drawRoundRect(
                        color = recRedColor.copy(alpha = 0.25f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx()),
                        size = size.copy(
                            width = size.width + 16.dp.toPx(),
                            height = size.height + 16.dp.toPx()
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(-8.dp.toPx(), -8.dp.toPx())
                    )
                }
            } else Modifier

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .then(recorderGlowModifier)
                    .background(recorderBgColor, RoundedCornerShape(16.dp))
                    .border(1.dp, recorderBorderColor, RoundedCornerShape(16.dp))
            ) {
                // Top sheen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0f))
                            ),
                            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                )
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isRecording) {
                                Icon(
                                    imageVector = Icons.Rounded.FiberManualRecord,
                                    contentDescription = null,
                                    tint = recRedColor,
                                    modifier = Modifier.size(12.dp).padding(end = 4.dp)
                                )
                                Spacer(Modifier.size(4.dp))
                            }
                            Text(
                                text = if (spec.monoFont) {
                                    if (isRecording) "REC — $recordedSteps STEPS" else "MACRO RECORDER"
                                } else {
                                    if (isRecording) "● REC — $recordedSteps steps captured" else "Macro recorder"
                                },
                                style = if (spec.monoFont) MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
                                else MaterialTheme.typography.titleMedium,
                                color = if (isRecording) recRedColor else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = if (spec.monoFont) {
                                if (isRecording) "USE KEYBOARD/MEDIA/MOUSE → STOP TO SAVE"
                                else "RECORD INPUTS FROM ANY SCREEN. REPLAY WITH ONE TAP."
                            } else {
                                if (isRecording) "Go use Keyboard/Media/Mouse — then come back and stop."
                                else "Record real inputs from any screen, replay with one tap."
                            },
                            style = if (spec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                            else MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    if (isRecording) {
                        Button(
                            onClick = { viewModel.stopRecording() },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = recRedColor
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(if (spec.monoFont) "STOP" else "Stop")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { viewModel.startRecording() },
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, spec.primary.copy(alpha = 0.6f)),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = spec.primary
                            )
                        ) {
                            Icon(Icons.Rounded.FiberManualRecord, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(if (spec.monoFont) "RECORD" else "● Record")
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column {
                    NotConnectedBanner(!isConnected)
                    Text(
                        text = if (spec.monoFont)
                            "MACROS ARE STEP SEQUENCES (KEYS, CHORDS, TEXT, DELAYS). BIND ONE TO ANY CUSTOM BUTTON IN THE LAYOUT EDITOR."
                        else
                            "Macros are step sequences (keys, chords, text, delays). " +
                                "Bind one to any custom button in the Layout editor.",
                        style = if (spec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                        else MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
            items(macros, key = { it.id }) { macro ->
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (spec.monoFont) macro.spec.name.uppercase() else macro.spec.name,
                                style = if (spec.monoFont) MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
                                else MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${macro.spec.steps.size} steps  •  id ${macro.id}",
                                style = if (spec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                                else MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.play(macro.id) }) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = spec.primary)
                        }
                        // AEROPAD v1.0 #27 — loop until panic-stop.
                        IconButton(onClick = { viewModel.playLooped(macro.id) }) {
                            Icon(Icons.Rounded.Repeat, contentDescription = "Loop", tint = spec.secondary)
                        }
                        IconButton(onClick = { viewModel.edit(macro) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit")
                        }
                        IconButton(onClick = { viewModel.delete(macro.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ----------------------------------------------------------------------
// Editor
// ----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MacroEditor(viewModel: MacrosViewModel) {
    val draft by viewModel.draft.collectAsState()
    val (rowId, spec) = draft ?: return
    val themeSpec = LocalAppTheme.current
    var textInput by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (themeSpec.monoFont) {
                            if (rowId == null) "NEW MACRO" else "EDIT MACRO"
                        } else {
                            if (rowId == null) "New macro" else "Edit macro"
                        },
                        style = if (themeSpec.monoFont) MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace)
                        else MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.closeDraft() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.testDraft() }) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Test-play")
                    }
                    IconButton(onClick = { viewModel.saveDraft() }) {
                        Icon(Icons.Rounded.Save, contentDescription = "Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = spec.name,
                onValueChange = viewModel::renameDraft,
                label = {
                    Text(
                        if (themeSpec.monoFont) "MACRO NAME" else "Macro name",
                        style = if (themeSpec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                        else MaterialTheme.typography.bodyMedium
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ---------- Step palette ----------
            Text(
                text = if (themeSpec.monoFont) "ADD STEP (${spec.steps.size}/${MacroSpec.STEPS_MAX})"
                else "Add step (${spec.steps.size}/${MacroSpec.STEPS_MAX})",
                style = if (themeSpec.monoFont) MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
                else MaterialTheme.typography.titleMedium,
                color = themeSpec.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            StepChipRow(
                entries = listOf(
                    "Enter" to MacroStep.KeyTap(HidKeys.ENTER),
                    "Tab" to MacroStep.KeyTap(HidKeys.TAB),
                    "Esc" to MacroStep.KeyTap(HidKeys.ESCAPE),
                    "Space" to MacroStep.KeyTap(HidKeys.SPACE),
                    "Copy" to MacroStep.KeyTap(HidKeys.C, HidModifiers.LEFT_CTRL),
                    "Paste" to MacroStep.KeyTap(HidKeys.V, HidModifiers.LEFT_CTRL),
                    "Alt+Tab" to MacroStep.KeyTap(HidKeys.TAB, HidModifiers.LEFT_ALT),
                    "Win+D" to MacroStep.KeyTap(HidKeys.D, HidModifiers.LEFT_GUI),
                    "Ctl+Alt+Del" to MacroStep.KeyTap(
                        HidKeys.DELETE,
                        (HidModifiers.LEFT_CTRL.toInt() or HidModifiers.LEFT_ALT.toInt()).toByte()
                    )
                ),
                onAdd = viewModel::addStep
            )
            StepChipRow(
                entries = listOf(
                    "Play/Pause" to MacroStep.Media(HidConsumer.PLAY_PAUSE),
                    "Vol +" to MacroStep.Media(HidConsumer.VOLUME_UP),
                    "Vol −" to MacroStep.Media(HidConsumer.VOLUME_DOWN),
                    "L-click" to MacroStep.MouseClick(0x01),
                    "R-click" to MacroStep.MouseClick(0x02),
                    "Wait 0.5s" to MacroStep.Delay(500),
                    "Wait 1s" to MacroStep.Delay(1000)
                ),
                onAdd = viewModel::addStep
            )
            // V2 MATRIX 2 — hold / humanizer / scroll steps.
            StepChipRow(
                entries = listOf(
                    "Hold Space 0.5s" to MacroStep.KeyHold(HidKeys.SPACE, ms = 500),
                    "Hold W 1s" to MacroStep.KeyHold(HidKeys.W, ms = 1000),
                    "Hold Shift 1s" to MacroStep.KeyHold(0x00, HidModifiers.LEFT_SHIFT, 1000),
                    "Rnd 0.1–0.3s" to MacroStep.RandomDelay(100, 300),
                    "Rnd 0.5–1.5s" to MacroStep.RandomDelay(500, 1500),
                    "Scroll ↑" to MacroStep.Scroll(3),
                    "Scroll ↓" to MacroStep.Scroll(-3)
                ),
                onAdd = viewModel::addStep
            )
            // V2 MATRIX 2 b2 — flow steps: repeat + sub-macro calls.
            StepChipRow(
                entries = listOf(
                    "Repeat last ×2" to MacroStep.RepeatLast(1, 2),
                    "Repeat last ×5" to MacroStep.RepeatLast(1, 5),
                    "Repeat 2 steps ×3" to MacroStep.RepeatLast(2, 3)
                ),
                onAdd = viewModel::addStep
            )
            // Sub-macro palette: every OTHER saved macro is one chip. The
            // macro being edited is excluded (self-call = infinite intent;
            // engine guards anyway, but don't even offer it).
            val allMacros by viewModel.macros.collectAsState()
            val subEntries = allMacros
                .filter { it.id != rowId }
                .take(8)
                .map { m -> "▶ ${m.spec.name.take(14)}" to MacroStep.RunMacro(m.id) as MacroStep }
            if (subEntries.isNotEmpty()) {
                StepChipRow(entries = subEntries, onAdd = viewModel::addStep)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = {
                        Text(
                            if (themeSpec.monoFont) "TEXT STEP" else "Text step",
                            style = if (themeSpec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                            else MaterialTheme.typography.bodyMedium
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.size(8.dp))
                AssistChip(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            viewModel.addStep(MacroStep.TypeText(textInput))
                            textInput = ""
                        }
                    },
                    label = { Text(if (themeSpec.monoFont) "ADD" else "Add") }
                )
            }

            // ---------- Step list ----------
            Text(
                text = if (themeSpec.monoFont) "STEPS" else "Steps",
                style = if (themeSpec.monoFont) MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace)
                else MaterialTheme.typography.titleMedium,
                color = themeSpec.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(spec.steps) { index, step ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}. ${stepLabel(step)}",
                                style = if (themeSpec.monoFont) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                                else MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.moveStepUp(index) }) {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { viewModel.removeStep(index) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Remove", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun StepChipRow(
    entries: List<Pair<String, MacroStep>>,
    onAdd: (MacroStep) -> Unit
) {
    val themeSpec = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        entries.forEach { (label, step) ->
            AssistChip(
                onClick = { onAdd(step) },
                label = {
                    Text(
                        text = if (themeSpec.monoFont) label.uppercase() else label,
                        style = if (themeSpec.monoFont) MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace)
                        else MaterialTheme.typography.labelLarge
                    )
                }
            )
        }
    }
}

private fun stepLabel(step: MacroStep): String = when (step) {
    is MacroStep.KeyTap -> {
        val mods = mutableListOf<String>()
        val m = step.modifiers.toInt()
        if (m and 0x01 != 0) mods += "Ctrl"
        if (m and 0x02 != 0) mods += "Shift"
        if (m and 0x04 != 0) mods += "Alt"
        if (m and 0x08 != 0) mods += "Win"
        val prefix = if (mods.isEmpty()) "" else mods.joinToString("+") + "+"
        "Key: " + prefix + "0x%02X".format(step.key)
    }
    is MacroStep.TypeText -> "Type: \"${step.text.take(30)}${if (step.text.length > 30) "…" else ""}\""
    is MacroStep.Media -> "Media: 0x%04X".format(step.usage)
    is MacroStep.MouseClick -> "Mouse: " + when (step.buttonMask) {
        0x01 -> "left click"; 0x02 -> "right click"; 0x04 -> "middle click"; else -> "?"
    }
    is MacroStep.Delay -> "Wait ${step.ms} ms"
    // V2 MATRIX 2 — new step types.
    is MacroStep.KeyHold -> "Hold 0x%02X for %d ms".format(step.key, step.ms)
    is MacroStep.RandomDelay -> "Wait ${step.minMs}–${step.maxMs} ms (random)"
    is MacroStep.Scroll -> if (step.amount > 0) "Scroll up ${step.amount}" else "Scroll down ${-step.amount}"
    // V2 MATRIX 2 b2 — flow steps.
    is MacroStep.RepeatLast -> "Repeat last ${step.span} step${if (step.span > 1) "s" else ""} ×${step.times}"
    is MacroStep.RunMacro -> "Run macro #${step.macroId}"
}
