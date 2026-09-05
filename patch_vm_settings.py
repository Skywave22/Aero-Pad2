import re

with open('app/src/main/java/com/aeropad/remote/viewmodel/SettingsViewModel.kt', 'r') as f:
    text = f.read()

funcs = """    fun setMovementSmoothing(value: Int) = updateMouse { it.copy(movementSmoothing = value) }
    fun setAcceleration(value: Int) = updateMouse { it.copy(acceleration = value) }
    fun setTrackpadProfile(p: com.aeropad.remote.model.TrackpadProfile) = updateMouse { it.copy(profile = p) }
    fun setAccelerationCurve(c: com.aeropad.remote.model.AccelerationCurve) = updateMouse { it.copy(curve = c) }
    fun setDragLock(value: Boolean) = updateMouse { it.copy(dragLock = value) }
    fun setEdgeScroll(value: Boolean) = updateMouse { it.copy(edgeScroll = value) }
    fun setThreeFingerGestures(value: Boolean) = updateMouse { it.copy(threeFingerGestures = value) }
    fun setPalmRejection(value: Boolean) = updateMouse { it.copy(palmRejection = value) }"""

text = text.replace("    fun setMovementSmoothing(value: Int) = updateMouse { it.copy(movementSmoothing = value) }", funcs)

with open('app/src/main/java/com/aeropad/remote/viewmodel/SettingsViewModel.kt', 'w') as f:
    f.write(text)
