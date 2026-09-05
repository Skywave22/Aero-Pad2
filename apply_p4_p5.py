import re
import os

def read(path):
    with open(path, 'r') as f: return f.read()
def write(path, text):
    with open(path, 'w') as f: f.write(text)
    
# ---- Phase 4: Keyboard 2.0 ----
# 1. Settings.kt
s = read('app/src/main/java/com/aeropad/remote/model/Settings.kt')
s = s.replace("""data class KeyboardSettings(
    val showTextInputBar: Boolean = true
)""", """data class KeyboardSettings(
    val showTextInputBar: Boolean = true,
    val showNumpad: Boolean = false,
    val sendOnEnter: Boolean = true,
    val textToSpeech: Boolean = false,
    val typeDelay: Int = 10
)""")
write('app/src/main/java/com/aeropad/remote/model/Settings.kt', s)

# 2. SettingsViewModel.kt
svm = read('app/src/main/java/com/aeropad/remote/viewmodel/SettingsViewModel.kt')
svm = svm.replace("""    private fun updateApp(transform: (AppSettings) -> AppSettings) =""", """    private fun updateKeyboard(transform: (KeyboardSettings) -> KeyboardSettings) =
        androidx.lifecycle.viewModelScope.launch { store.updateKeyboard(transform(keyboard.value)) }

    private fun updateApp(transform: (AppSettings) -> AppSettings) =""")
svm = svm.replace("""    fun setShowTextInputBar(value: Boolean) =
        viewModelScope.launch { store.updateKeyboard(keyboard.value.copy(showTextInputBar = value)) }""", """    fun setShowTextInputBar(value: Boolean) = updateKeyboard { it.copy(showTextInputBar = value) }
    fun setShowNumpad(value: Boolean) = updateKeyboard { it.copy(showNumpad = value) }
    fun setSendOnEnter(value: Boolean) = updateKeyboard { it.copy(sendOnEnter = value) }
    fun setTextToSpeech(value: Boolean) = updateKeyboard { it.copy(textToSpeech = value) }
    fun setTypeDelay(value: Int) = updateKeyboard { it.copy(typeDelay = value) }""")
write('app/src/main/java/com/aeropad/remote/viewmodel/SettingsViewModel.kt', svm)

# 3. SettingsScreen.kt
ss = read('app/src/main/java/com/aeropad/remote/ui/screens/settings/SettingsScreen.kt')
ss = ss.replace("""            if (matches("keyboard", "text", "input")) SettingsGroup("Keyboard") {
                ToggleRow("Show text input bar", keyboard.showTextInputBar, viewModel::setShowTextInputBar)
            }""", """            if (matches("keyboard", "text", "input", "numpad", "delay")) SettingsGroup("Keyboard") {
                ToggleRow("Show text input bar", keyboard.showTextInputBar, viewModel::setShowTextInputBar)
                ToggleRow("Show Numpad overlay", keyboard.showNumpad, viewModel::setShowNumpad)
                ToggleRow("Send on Enter", keyboard.sendOnEnter, viewModel::setSendOnEnter)
                SliderRow("Type delay (ms)", keyboard.typeDelay, viewModel::setTypeDelay)
            }""")
write('app/src/main/java/com/aeropad/remote/ui/screens/settings/SettingsScreen.kt', ss)

# 4. KeyboardScreen.kt
ks = read('app/src/main/java/com/aeropad/remote/ui/screens/keyboard/KeyboardScreen.kt')
ks = ks.replace("""var showNumpad by remember { mutableStateOf(false) }
                androidx.compose.material3.FilterChip(
                    selected = showNumpad,
                    onClick = { showNumpad = !showNumpad },
                    label = { Text(if (showNumpad) "Hide numpad" else "Numpad") }
                )
                if (showNumpad) {""", """if (keyboardSettings.showNumpad) {""")
ks = ks.replace("""                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type here, send to PC…") },
                        singleLine = true
                    )""", """                    OutlinedTextField(
                        value = text,
                        onValueChange = { newValue -> 
                            text = newValue
                            if (keyboardSettings.sendOnEnter && text.endsWith("\\n")) {
                                val toSend = text.removeSuffix("\\n")
                                if (toSend.isNotEmpty()) {
                                    viewModel.typeText(toSend)
                                    viewModel.keyTap(HidKeys.ENTER, 0)
                                }
                                text = ""
                                haptic()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type here, send to PC…") },
                        singleLine = true
                    )""")
write('app/src/main/java/com/aeropad/remote/ui/screens/keyboard/KeyboardScreen.kt', ks)

# 5. HidEngine.kt
he = read('app/src/main/java/com/aeropad/remote/hid/HidEngine.kt')
he = he.replace("""    private suspend fun typeText(text: String) {
        for (char in text) {
            val stroke = CharToHidMapper.map(char)
            if (stroke == null) {
                Timber.w("typeText: unmappable char '%s' skipped", char)
                continue
            }
            report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboard(stroke.modifiers, listOf(stroke.key)))
            delay(KEY_TAP_DELAY_MS)
            report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboardRelease())
            delay(KEY_TAP_DELAY_MS)
        }
    }""", """    private suspend fun typeText(text: String, overrideDelayMs: Long? = null) {
        val activeDelay = overrideDelayMs ?: KEY_TAP_DELAY_MS
        for (char in text) {
            val stroke = CharToHidMapper.map(char)
            if (stroke == null) {
                Timber.w("typeText: unmappable char '%s' skipped", char)
                continue
            }
            report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboard(stroke.modifiers, listOf(stroke.key)))
            delay(activeDelay)
            report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboardRelease())
            delay(activeDelay)
        }
    }""")
he = he.replace("is HidAction.TypeText -> typeText(action.text)", "is HidAction.TypeText -> typeText(action.text, action.delayMs)")
write('app/src/main/java/com/aeropad/remote/hid/HidEngine.kt', he)

ha = read('app/src/main/java/com/aeropad/remote/model/HidAction.kt')
ha = ha.replace("data class TypeText(val text: String) : HidAction", "data class TypeText(val text: String, val delayMs: Long? = null) : HidAction")
write('app/src/main/java/com/aeropad/remote/model/HidAction.kt', ha)

rmv = read('app/src/main/java/com/aeropad/remote/viewmodel/RemoteControlViewModel.kt')
rmv = rmv.replace("""    fun typeText(text: String) {
        if (text.isNotEmpty()) {
            sendAction(HidAction.TypeText(text))
            // AEROPAD v1.0 #12 — clipboard history: last 10 sent texts,""", """    fun typeText(text: String) {
        if (text.isNotEmpty()) {
            val kb = keyboardSettings.value
            sendAction(HidAction.TypeText(text, kb.typeDelay.toLong()))
            // AEROPAD v1.0 #12 — clipboard history: last 10 sent texts,""")
write('app/src/main/java/com/aeropad/remote/viewmodel/RemoteControlViewModel.kt', rmv)


# ---- Phase 5: Gamepad Reliability ----
gmr = read('app/src/main/java/com/aeropad/remote/domain/GamepadRuntimeCore.kt')
gmr = gmr.replace("""    /** Toggle-button fold: returns new latched state after a tap. */""", """    fun withDpad(
        snapshot: GamepadSnapshot,
        up: Boolean = false,
        down: Boolean = false,
        left: Boolean = false,
        right: Boolean = false
    ): GamepadSnapshot {
        val hat = if (up && left) 8
        else if (up && right) 2
        else if (down && right) 4
        else if (down && left) 6
        else if (up) 1
        else if (right) 3
        else if (down) 5
        else if (left) 7
        else 0 // Neutral (Note: Android expects 0 for neutral in some mappings, but old hid report uses 8 for neutral? Oh wait, in previous version circularHat returns 0 for neutral)
        return snapshot.copy(hat = hat)
    }

    /**
     * Set the exact hat value directly. Neutral is 0.
     */
    fun withHat(snapshot: GamepadSnapshot, hat: Int): GamepadSnapshot {
        return snapshot.copy(hat = hat)
    }

    /** Jump out of host deadzone by pushing output to minimum limit */
    fun antiDeadZone(value: Float, deadZonePercent: Int): Float {
        if (value == 0f) return 0f
        val dz = deadZonePercent / 100f
        val sign = kotlin.math.sign(value)
        val mag = kotlin.math.abs(value)
        val res = dz + mag * (1f - dz)
        return sign * res.coerceIn(0f, 1f)
    }

    /** Toggle-button fold: returns new latched state after a tap. */""")
write('app/src/main/java/com/aeropad/remote/domain/GamepadRuntimeCore.kt', gmr)

act = read('app/src/test/java/com/aeropad/remote/gamepad/AdvancedControlsTest.kt')
act = act.replace("AdvancedControls", "GamepadRuntimeCore")
write('app/src/test/java/com/aeropad/remote/gamepad/AdvancedControlsTest.kt', act)

