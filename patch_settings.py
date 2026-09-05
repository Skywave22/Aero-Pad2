import re

with open('app/src/main/java/com/aeropad/remote/viewmodel/SettingsViewModel.kt', 'r') as f:
    text = f.read()

funcs = """    fun setMovementSmoothing(v: Int) = viewModelScope.launch { store.updateMouse { it.copy(movementSmoothing = v) } }
    fun setAcceleration(v: Int) = viewModelScope.launch { store.updateMouse { it.copy(acceleration = v) } }
    fun setTrackpadProfile(p: com.aeropad.remote.model.TrackpadProfile) = viewModelScope.launch { store.updateMouse { it.copy(profile = p) } }
    fun setAccelerationCurve(c: com.aeropad.remote.model.AccelerationCurve) = viewModelScope.launch { store.updateMouse { it.copy(curve = c) } }
    fun setDragLock(v: Boolean) = viewModelScope.launch { store.updateMouse { it.copy(dragLock = v) } }
    fun setEdgeScroll(v: Boolean) = viewModelScope.launch { store.updateMouse { it.copy(edgeScroll = v) } }
    fun setThreeFingerGestures(v: Boolean) = viewModelScope.launch { store.updateMouse { it.copy(threeFingerGestures = v) } }
    fun setPalmRejection(v: Boolean) = viewModelScope.launch { store.updateMouse { it.copy(palmRejection = v) } }"""

text = text.replace("    fun setMovementSmoothing(v: Int) = viewModelScope.launch { store.updateMouse { it.copy(movementSmoothing = v) } }", funcs)

with open('app/src/main/java/com/aeropad/remote/viewmodel/SettingsViewModel.kt', 'w') as f:
    f.write(text)

with open('app/src/main/java/com/aeropad/remote/ui/screens/settings/SettingsScreen.kt', 'r') as f:
    text = f.read()

mouse_group = """            // ---------- Mouse ----------
            if (matches("mouse", "trackpad", "sensitivity", "scroll", "smoothing", "pen", "tap", "acceleration", "profile", "drag", "edge")) SettingsGroup("Mouse & trackpad") {
                // Profile dropdown missing standard combo box, so just list buttons or radio. Let's use SegmentedRow or list.
                // Simple representation:
                SliderRow("Sensitivity", mouse.sensitivity, viewModel::setMouseSensitivity)
                SliderRow("Acceleration", mouse.acceleration, viewModel::setAcceleration)
                SliderRow("Movement smoothing", mouse.movementSmoothing, viewModel::setMovementSmoothing)
                SliderRow("Scroll speed", mouse.scrollSpeed, viewModel::setScrollSpeed)
                ToggleRow("Invert scroll", mouse.invertScroll, viewModel::setInvertScroll)
                ToggleRow("Tap to click", mouse.tapToClick, viewModel::setTapToClick)
                ToggleRow("Drag lock (double tap and hold)", mouse.dragLock, viewModel::setDragLock)
                ToggleRow("Edge scrolling", mouse.edgeScroll, viewModel::setEdgeScroll)
                ToggleRow("Three-finger gestures", mouse.threeFingerGestures, viewModel::setThreeFingerGestures)
                ToggleRow("Palm rejection", mouse.palmRejection, viewModel::setPalmRejection)
                ToggleRow("Pen mode", mouse.penMode, viewModel::setPenMode, subtitle = "Slower, precise pointer")
            }"""

old_mouse_group = """            // ---------- Mouse ----------
            if (matches("mouse", "trackpad", "sensitivity", "scroll", "smoothing", "pen", "tap")) SettingsGroup("Mouse & trackpad") {
                SliderRow("Sensitivity", mouse.sensitivity, viewModel::setMouseSensitivity)
                SliderRow("Scroll speed", mouse.scrollSpeed, viewModel::setScrollSpeed)
                SliderRow("Movement smoothing", mouse.movementSmoothing, viewModel::setMovementSmoothing)
                ToggleRow("Invert scroll", mouse.invertScroll, viewModel::setInvertScroll)
                ToggleRow("Tap to click", mouse.tapToClick, viewModel::setTapToClick)
                ToggleRow("Pen mode", mouse.penMode, viewModel::setPenMode, subtitle = "Slower, precise pointer")
            }"""

text = text.replace(old_mouse_group, mouse_group)

with open('app/src/main/java/com/aeropad/remote/ui/screens/settings/SettingsScreen.kt', 'w') as f:
    f.write(text)

