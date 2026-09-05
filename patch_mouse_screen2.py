import re

with open('app/src/main/java/com/aeropad/remote/ui/screens/mouse/MouseScreen.kt', 'r') as f:
    text = f.read()

text = text.replace("val isConnected by viewModel.connectionState.collectAsState()", "val isConnected by viewModel.connectionState.collectAsState()\n    val mouseSettings by viewModel.mouseSettings.collectAsState()")

# Also need to replace in pointerInput(viewModel, mouseSettings)

new_drag = """                        .pointerInput(viewModel, mouseSettings) {
                            detectTrackpadGestures(
                                viewModel = viewModel,
                                isEdgeScrollEnabled = mouseSettings.edgeScroll,
                                isThreeFingerEnabled = mouseSettings.threeFingerGestures,
                                isPalmRejectionEnabled = mouseSettings.palmRejection
                            )
                        }"""

old_drag = """                        .pointerInput(viewModel) {
                            val store = viewModel.getSettingsStore()
                            val s = store.mouse.value
                            detectTrackpadGestures(
                                viewModel = viewModel,
                                isEdgeScrollEnabled = s.edgeScroll,
                                isThreeFingerEnabled = s.threeFingerGestures,
                                isPalmRejectionEnabled = s.palmRejection
                            )
                        }"""

text = text.replace(old_drag, new_drag)

with open('app/src/main/java/com/aeropad/remote/ui/screens/mouse/MouseScreen.kt', 'w') as f:
    f.write(text)
