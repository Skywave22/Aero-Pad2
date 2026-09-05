import os, re

def replace(path, old, new):
    if not os.path.exists(path): return
    with open(path, 'r') as f:
        text = f.read()
    if old in text:
        text = text.replace(old, new)
        with open(path, 'w') as f:
            f.write(text)

# 1. HidAction
replace('app/src/main/java/com/aeropad/remote/model/HidAction.kt',
        '    data class GamepadUpdate(val snapshot: GamepadSnapshot) : HidAction',
        '    data class GamepadUpdate(val snapshot: GamepadSnapshot) : HidAction\n\n    /** V2 MATRIX 2 — global safety: release all keys, mouse buttons, media, and gamepad. */\n    data object ReleaseAll : HidAction')

# 2. HidEngine
replace('app/src/main/java/com/aeropad/remote/hid/HidEngine.kt',
        'override fun stop() {\n        userInitiatedDisconnect = true',
        'override fun stop() {\n        userInitiatedDisconnect = true\n        runCatching {\n            report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboardRelease())\n            report(HidDescriptors.REPORT_ID_MOUSE, HidReportBuilder.mouse())\n            report(HidDescriptors.REPORT_ID_CONSUMER, HidReportBuilder.consumerRelease())\n            report(HidDescriptors.REPORT_ID_SYSTEM, HidReportBuilder.systemRelease())\n            report(HidDescriptors.REPORT_ID_GAMEPAD, HidReportBuilder.gamepadNeutral())\n        }')
replace('app/src/main/java/com/aeropad/remote/hid/HidEngine.kt',
        'override fun disconnect() {\n        userInitiatedDisconnect = true\n        val hd = hidDevice ?: return',
        'override fun disconnect() {\n        userInitiatedDisconnect = true\n        runCatching {\n            report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboardRelease())\n            report(HidDescriptors.REPORT_ID_MOUSE, HidReportBuilder.mouse())\n            report(HidDescriptors.REPORT_ID_CONSUMER, HidReportBuilder.consumerRelease())\n            report(HidDescriptors.REPORT_ID_SYSTEM, HidReportBuilder.systemRelease())\n            report(HidDescriptors.REPORT_ID_GAMEPAD, HidReportBuilder.gamepadNeutral())\n        }\n        val hd = hidDevice ?: return')

with open('app/src/main/java/com/aeropad/remote/hid/HidEngine.kt', 'r') as f:
    text = f.read()
connect_pattern = r"(_state.value = HidConnectionState.Connecting\(device.toRemote\(\)\)\n\s*val ok = runCatching \{ hd.connect\(device\) \})"
replacement = r"""if (connectedDevice != null && connectedDevice != device) {
            runCatching {
                report(HidDescriptors.REPORT_ID_KEYBOARD, HidReportBuilder.keyboardRelease())
                report(HidDescriptors.REPORT_ID_MOUSE, HidReportBuilder.mouse())
                report(HidDescriptors.REPORT_ID_CONSUMER, HidReportBuilder.consumerRelease())
                report(HidDescriptors.REPORT_ID_SYSTEM, HidReportBuilder.systemRelease())
                report(HidDescriptors.REPORT_ID_GAMEPAD, HidReportBuilder.gamepadNeutral())
            }
        }
        \1"""
text = re.sub(connect_pattern, replacement, text)
with open('app/src/main/java/com/aeropad/remote/hid/HidEngine.kt', 'w') as f:
    f.write(text)

# 3. MacroEngine
with open('app/src/main/java/com/aeropad/remote/domain/MacroEngine.kt', 'r') as f:
    text = f.read()
text = text.replace("import javax.inject.Singleton", "import com.aeropad.remote.domain.usecase.ObserveConnectionUseCase\nimport kotlinx.coroutines.flow.launchIn\nimport kotlinx.coroutines.flow.onEach\nimport javax.inject.Singleton")
text = text.replace("    private val sendAction: SendHidActionUseCase\n) {", "    private val sendAction: SendHidActionUseCase,\n    observeConnection: ObserveConnectionUseCase\n) {\n\n    init {\n        observeConnection().onEach { state ->\n            if (!state.isConnected) stop()\n        }.launchIn(CoroutineScope(SupervisorJob() + Dispatchers.Default))\n    }")
text = text.replace("runCatching { sendAction(HidAction.KeyRelease) }", "runCatching { sendAction(HidAction.ReleaseAll) }")
with open('app/src/main/java/com/aeropad/remote/domain/MacroEngine.kt', 'w') as f:
    f.write(text)

# 4. HidService
replace('app/src/main/java/com/aeropad/remote/service/HidService.kt',
        'serviceScope.cancel()\n        Timber.i("HidService destroyed")',
        'hidController.stop()\n        serviceScope.cancel()\n        Timber.i("HidService destroyed")')

# 5. ViewModels
vms = [
    'app/src/main/java/com/aeropad/remote/viewmodel/FullKeyboardViewModel.kt',
    'app/src/main/java/com/aeropad/remote/viewmodel/RemoteControlViewModel.kt',
    'app/src/main/java/com/aeropad/remote/viewmodel/PcComboViewModel.kt',
    'app/src/main/java/com/aeropad/remote/viewmodel/AirMouseViewModel.kt'
]
for path in vms:
    with open(path, 'r') as f:
        text = f.read()
    if 'override fun onCleared()' not in text:
        text = text.replace('}\n\n', '}\n')
        text = text[:-1] + "\n    override fun onCleared() {\n        super.onCleared()\n        runCatching { sendAction(com.aeropad.remote.model.HidAction.ReleaseAll) }\n    }\n}\n"
        with open(path, 'w') as f:
            f.write(text)
    if "}\n    override fun onCleared() {" in text:
        text = text.replace("}\n    override fun onCleared() {", "    override fun onCleared() {")
        with open(path, 'w') as f:
            f.write(text)

