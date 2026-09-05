import re

with open('app/src/main/java/com/aeropad/remote/ui/screens/mouse/MouseScreen.kt', 'r') as f:
    text = f.read()

old_drag = """                        .pointerInput(viewModel) {
                            // keyed on viewModel (stable): gesture coroutine
                            // survives recompositions -> no dropped deltas.
                            detectDragGestures(
                                onDragStart = { viewModel.onTrackpadGestureStart() }
                            ) { change, dragAmount ->
                                change.consume()
                                viewModel.onTrackpadDelta(dragAmount.x, dragAmount.y)
                            }
                        }"""

new_drag = """                        .pointerInput(viewModel) {
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
