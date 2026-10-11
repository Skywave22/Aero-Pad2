package com.bluepilot.remote.ui.screens.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.bluepilot.remote.ui.components.toComposeColor
import androidx.compose.ui.unit.dp
import com.bluepilot.remote.model.HidConsumer
import com.bluepilot.remote.model.HidKeys
import com.bluepilot.remote.model.HidModifiers
import com.bluepilot.remote.model.widgets.WidgetAction
import com.bluepilot.remote.model.widgets.WidgetStyle
import com.bluepilot.remote.viewmodel.LayoutEditorViewModel

/**
 * Style & action editor for the selected widget.
 * Every visual property from WidgetStyle is editable here; the action
 * picker binds keys/media/mouse/text to the widget.
 */
@Composable
fun WidgetStylePanel(viewModel: LayoutEditorViewModel) {
    val layout by viewModel.layout.collectAsState()
    val selectedId by viewModel.selectedId.collectAsState()
    val widget = layout.widgets.firstOrNull { it.id == selectedId } ?: return
    val style = widget.style

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Widget: ${widget.type.name.lowercase().replaceFirstChar { it.uppercase() }}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))

        // ---------- Label & icon ----------
        OutlinedTextField(
            value = style.label,
            onValueChange = { new -> viewModel.updateSelectedStyle { it.copy(label = new) } },
            label = { Text("Label") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = style.icon,
            onValueChange = { new -> viewModel.updateSelectedStyle { it.copy(icon = new) } },
            label = { Text("Icon (emoji)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // ---------- Colors ----------
        PanelSection("Background color")
        ColorRow(selected = style.backgroundColor) { color ->
            viewModel.updateSelectedStyle { it.copy(backgroundColor = color) }
        }
        PanelSection("Content color")
        ColorRow(selected = style.contentColor) { color ->
            viewModel.updateSelectedStyle { it.copy(contentColor = color) }
        }

        // ---------- Sliders ----------
        PanelSlider("Opacity", style.opacity, 0.1f, 1f) { v ->
            viewModel.updateSelectedStyle { it.copy(opacity = v) }
        }
        PanelSlider("Corner radius", style.cornerRadius.toFloat(), 0f, WidgetStyle.RADIUS_MAX.toFloat()) { v ->
            viewModel.updateSelectedStyle { it.copy(cornerRadius = v.toInt()) }
        }
        PanelSlider("Shadow", style.elevation.toFloat(), 0f, WidgetStyle.ELEVATION_MAX.toFloat()) { v ->
            viewModel.updateSelectedStyle { it.copy(elevation = v.toInt()) }
        }
        PanelSlider("Font size", style.fontSize.toFloat(), WidgetStyle.FONT_MIN.toFloat(), WidgetStyle.FONT_MAX.toFloat()) { v ->
            viewModel.updateSelectedStyle { it.copy(fontSize = v.toInt()) }
        }

        // ---------- Visibility ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Visible", modifier = Modifier.weight(1f))
            Switch(
                checked = style.visible,
                onCheckedChange = { v -> viewModel.updateSelectedStyle { it.copy(visible = v) } }
            )
        }

        // ---------- Action binding ----------
        val macros by viewModel.macros.collectAsState()
        PanelSection("Tap action")
        ActionPicker(current = widget.action, macros = macros) { viewModel.setSelectedAction(it) }
        PanelSection("Long-press action")
        ActionPicker(current = widget.secondaryAction, macros = macros) { viewModel.setSelectedSecondaryAction(it) }

        // GESTURE_ZONE: per-direction swipe + two-finger-tap bindings.
        if (widget.type == com.bluepilot.remote.model.widgets.WidgetType.GESTURE_ZONE) {
            PanelSection("Swipe up")
            ActionPicker(current = widget.swipeUp, macros = macros) {
                viewModel.setSelectedGestureAction(com.bluepilot.remote.viewmodel.LayoutEditorViewModel.GestureSlot.UP, it)
            }
            PanelSection("Swipe down")
            ActionPicker(current = widget.swipeDown, macros = macros) {
                viewModel.setSelectedGestureAction(com.bluepilot.remote.viewmodel.LayoutEditorViewModel.GestureSlot.DOWN, it)
            }
            PanelSection("Swipe left")
            ActionPicker(current = widget.swipeLeft, macros = macros) {
                viewModel.setSelectedGestureAction(com.bluepilot.remote.viewmodel.LayoutEditorViewModel.GestureSlot.LEFT, it)
            }
            PanelSection("Swipe right")
            ActionPicker(current = widget.swipeRight, macros = macros) {
                viewModel.setSelectedGestureAction(com.bluepilot.remote.viewmodel.LayoutEditorViewModel.GestureSlot.RIGHT, it)
            }
            PanelSection("Two-finger tap")
            ActionPicker(current = widget.twoFingerTap, macros = macros) {
                viewModel.setSelectedGestureAction(com.bluepilot.remote.viewmodel.LayoutEditorViewModel.GestureSlot.TWO_FINGER, it)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ----------------------------------------------------------------------

@Composable
private fun PanelSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun PanelSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                if (max <= 1f) String.format("%.2f", value) else value.toInt().toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = min..max)
    }
}

/** Curated color palette (ARGB longs — same format as WidgetStyle). */
private val palette = listOf(
    0xFF1A2238, 0xFF2F6BFF, 0xFF29C5FF, 0xFF2ECC71, 0xFFF1C40F,
    0xFFE67E22, 0xFFE74C3C, 0xFF9B59B6, 0xFFFFFFFF, 0xFF000000,
    0xFF7F8C8D, 0xFF34495E
)

@Composable
private fun ColorRow(selected: Long, onPick: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        palette.forEach { argb ->
            val color = argb.toComposeColor()
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(color, CircleShape)
                    .border(
                        width = if (argb == selected) 3.dp else 1.dp,
                        color = if (argb == selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
                    .clickable { onPick(argb) }
            )
        }
    }
}

// ----------------------------------------------------------------------
// Action picker
// ----------------------------------------------------------------------

/** Common bindable actions, grouped. Text option uses the field below. */
private data class ActionOption(val label: String, val action: WidgetAction)

private val keyOptions = listOf(
    ActionOption("None", WidgetAction.None),
    ActionOption("Enter", WidgetAction.KeyTap(HidKeys.ENTER)),
    ActionOption("Esc", WidgetAction.KeyTap(HidKeys.ESCAPE)),
    ActionOption("Space", WidgetAction.KeyTap(HidKeys.SPACE)),
    ActionOption("Tab", WidgetAction.KeyTap(HidKeys.TAB)),
    ActionOption("Backspace", WidgetAction.KeyTap(HidKeys.BACKSPACE)),
    ActionOption("Delete", WidgetAction.KeyTap(HidKeys.DELETE)),
    ActionOption("↑", WidgetAction.KeyTap(HidKeys.ARROW_UP)),
    ActionOption("↓", WidgetAction.KeyTap(HidKeys.ARROW_DOWN)),
    ActionOption("←", WidgetAction.KeyTap(HidKeys.ARROW_LEFT)),
    ActionOption("→", WidgetAction.KeyTap(HidKeys.ARROW_RIGHT)),
    ActionOption("Copy", WidgetAction.KeyTap(HidKeys.C, HidModifiers.LEFT_CTRL)),
    ActionOption("Paste", WidgetAction.KeyTap(HidKeys.V, HidModifiers.LEFT_CTRL)),
    ActionOption("Undo", WidgetAction.KeyTap(HidKeys.Z, HidModifiers.LEFT_CTRL)),
    ActionOption("Alt+Tab", WidgetAction.KeyTap(HidKeys.TAB, HidModifiers.LEFT_ALT)),
    ActionOption("PgUp", WidgetAction.KeyTap(HidKeys.PAGE_UP)),
    ActionOption("PgDn", WidgetAction.KeyTap(HidKeys.PAGE_DOWN)),
    ActionOption("F5", WidgetAction.KeyTap(HidKeys.F5))
)

private val mediaOptions = listOf(
    ActionOption("Play/Pause", WidgetAction.Media(HidConsumer.PLAY_PAUSE)),
    ActionOption("Next", WidgetAction.Media(HidConsumer.NEXT_TRACK)),
    ActionOption("Prev", WidgetAction.Media(HidConsumer.PREV_TRACK)),
    ActionOption("Vol +", WidgetAction.Media(HidConsumer.VOLUME_UP)),
    ActionOption("Vol −", WidgetAction.Media(HidConsumer.VOLUME_DOWN)),
    ActionOption("Mute", WidgetAction.Media(HidConsumer.MUTE))
)

private val mouseOptions = listOf(
    ActionOption("Left click", WidgetAction.MouseClick(0x01)),
    ActionOption("Right click", WidgetAction.MouseClick(0x02)),
    ActionOption("Middle click", WidgetAction.MouseClick(0x04))
)

@Composable
private fun ActionPicker(
    current: WidgetAction,
    macros: List<com.bluepilot.remote.data.macros.Macro> = emptyList(),
    onPick: (WidgetAction) -> Unit
) {
    var textValue by remember(current) {
        mutableStateOf((current as? WidgetAction.TypeText)?.text ?: "")
    }

    Column {
        ActionChipRow("Keys", keyOptions, current, onPick)
        ActionChipRow("Media", mediaOptions, current, onPick)
        ActionChipRow("Mouse", mouseOptions, current, onPick)
        if (macros.isNotEmpty()) {
            ActionChipRow(
                "Macros",
                macros.map { ActionOption("▶ " + it.spec.name, WidgetAction.RunMacro(it.id)) },
                current,
                onPick
            )
        }

        // Text typing binding
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            OutlinedTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    if (it.isNotBlank()) onPick(WidgetAction.TypeText(it))
                },
                label = { Text("Or type text to send") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ActionChipRow(
    group: String,
    options: List<ActionOption>,
    current: WidgetAction,
    onPick: (WidgetAction) -> Unit
) {
    Text(
        text = group,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option.action == current,
                onClick = { onPick(option.action) },
                label = { Text(option.label) }
            )
        }
    }
}
