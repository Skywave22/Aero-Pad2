package com.bluepilot.remote.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bluepilot.remote.data.keyboard.KeyboardLayoutStore
import com.bluepilot.remote.data.layout.LayoutRepository
import com.bluepilot.remote.domain.SettingsStore
import com.bluepilot.remote.domain.usecase.ObserveConnectionUseCase
import com.bluepilot.remote.domain.usecase.SendHidActionUseCase
import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.MouseButton
import com.bluepilot.remote.model.MouseSettings
import com.bluepilot.remote.model.keyboard.KeySpec
import com.bluepilot.remote.model.keyboard.KeyboardLayoutSpec
import com.bluepilot.remote.model.widgets.LayoutSpec
import com.bluepilot.remote.model.widgets.WidgetAction
import com.bluepilot.remote.model.widgets.WidgetFrame
import com.bluepilot.remote.model.widgets.WidgetSpec
import com.bluepilot.remote.model.widgets.WidgetStyle
import com.bluepilot.remote.model.widgets.WidgetType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the dedicated scroll bar sits in the PC Combo screen. */
enum class ScrollBarSide { LEFT, RIGHT, HIDDEN }

/**
 * SECTION: "Full PC Control" combo — trackpad (top) + keyboard (bottom)
 * + dedicated scroll bar. Ratio & scroll-bar side persist; the combo can
 * be exported as a normal editable Layout profile.
 *
 * Gesture map (customizability note: 2/3-finger actions follow the
 * industry-standard trackpad convention):
 *  1-finger drag = move · 1-finger tap = left click
 *  2-finger drag = scroll · 2-finger tap = middle click
 *  3-finger tap = right click
 */
@HiltViewModel
class PcComboViewModel @Inject constructor(
    private val keyboardStore: KeyboardLayoutStore,
    settingsStore: SettingsStore,
    observeConnection: ObserveConnectionUseCase,
    private val sendAction: SendHidActionUseCase,
    private val layoutRepository: LayoutRepository
) : ViewModel() {

    val board: StateFlow<KeyboardLayoutSpec> = keyboardStore.layout
        .stateIn(viewModelScope, SharingStarted.Eagerly, KeyboardLayoutSpec())

    val isConnected: StateFlow<Boolean> = observeConnection()
        .map { it.isConnected }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val mouse: StateFlow<MouseSettings> = settingsStore.mouseSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, MouseSettings())

    val ratio: StateFlow<Float> = keyboardStore.comboRatio
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.5f)

    val scrollBarSide: StateFlow<ScrollBarSide> = keyboardStore.comboScrollSide
        .stateIn(viewModelScope, SharingStarted.Eagerly, ScrollBarSide.RIGHT)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // ---------------- divider / prefs ----------------

    fun setRatio(value: Float) = viewModelScope.launch {
        keyboardStore.setComboRatio(value.coerceIn(0.25f, 0.75f))
    }

    fun setScrollSide(side: ScrollBarSide) = viewModelScope.launch {
        keyboardStore.setComboScrollSide(side)
    }

    // ---------------- trackpad input ----------------

    // OPTIMIZATION: shared TrackpadEngine (was ~25 duplicated lines).
    private val trackpad = com.bluepilot.remote.domain.TrackpadEngine { mouse.value }

    fun gestureStart() = trackpad.startGesture()

    fun move(dx: Float, dy: Float) {
        val (ix, iy) = trackpad.move(dx, dy)
        if (ix != 0 || iy != 0) sendAction(HidAction.MouseMove(ix, iy))
    }

    fun scroll(dy: Float) {
        trackpad.scroll(dy).takeIf { it != 0 }?.let { sendAction(HidAction.MouseScroll(it)) }
    }

    /** Tap by finger count: 1=left, 2=middle, 3=right. */
    fun tap(fingers: Int) {
        val button = when (fingers) {
            2 -> MouseButton.MIDDLE
            3 -> MouseButton.RIGHT
            else -> MouseButton.LEFT
        }
        sendAction(HidAction.MouseClick(button))
    }

    fun key(key: KeySpec) = sendAction(HidAction.KeyTap(key.keyCode, key.modifiers))

    // ---------------- save as editable layout profile ----------------

    /**
     * Exports the current combo as a standard Layout profile (trackpad +
     * scroll strip + favorite keys row) — fully editable in the Layout
     * editor like any other combo, honoring the established rule.
     */
    fun saveAsProfile() {
        viewModelScope.launch {
            val r = ratio.value
            val side = scrollBarSide.value
            val padW = if (side == ScrollBarSide.HIDDEN) 0.96f else 0.80f
            val padX = if (side == ScrollBarSide.LEFT) 0.18f else 0.02f
            val widgets = mutableListOf(
                WidgetSpec(
                    id = "pc-pad", type = WidgetType.TRACKPAD,
                    frame = WidgetFrame(padX, 0.02f, padW, r - 0.04f),
                    style = WidgetStyle(label = "Trackpad")
                )
            )
            if (side != ScrollBarSide.HIDDEN) {
                widgets += WidgetSpec(
                    id = "pc-scroll", type = WidgetType.SCROLL_STRIP,
                    frame = WidgetFrame(
                        if (side == ScrollBarSide.LEFT) 0.02f else 0.84f,
                        0.02f, 0.14f, r - 0.04f
                    ),
                    style = WidgetStyle(label = "Scroll")
                )
            }
            // Favorites (or default shortcuts) become the key row(s).
            val favs = board.value.favorites.ifEmpty {
                listOf(
                    KeySpec("d1", "Enter", com.bluepilot.remote.model.HidKeys.ENTER),
                    KeySpec("d2", "Esc", com.bluepilot.remote.model.HidKeys.ESCAPE),
                    KeySpec("d3", "Space", com.bluepilot.remote.model.HidKeys.SPACE)
                )
            }.take(8)
            val kw = 0.96f / favs.size
            favs.forEachIndexed { i, f ->
                widgets += WidgetSpec(
                    id = "pc-k$i", type = WidgetType.BUTTON,
                    frame = WidgetFrame(0.02f + i * kw, r + 0.02f, kw - 0.01f, 0.96f - r - 0.04f),
                    style = WidgetStyle(label = f.label),
                    action = WidgetAction.KeyTap(f.keyCode, f.modifiers)
                )
            }
            runCatching {
                layoutRepository.save(null, LayoutSpec(name = "Full PC Control", widgets = widgets))
            }.onSuccess { _message.value = "Saved as Layout profile ✓ — edit it in Layouts" }
                .onFailure { _message.value = "Save failed" }
        }
    }

    fun consumeMessage() { _message.value = null }
}
