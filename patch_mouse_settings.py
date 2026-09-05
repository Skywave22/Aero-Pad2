import re

with open('app/src/main/java/com/aeropad/remote/model/Settings.kt', 'r') as f:
    text = f.read()

profile_enum = """
enum class TrackpadProfile { NATURAL, PRECISE, FAST, GAMING, CUSTOM }
enum class AccelerationCurve { NONE, LINEAR, EXPONENTIAL }
"""

old_settings = """data class MouseSettings(
    val sensitivity: Int = 65,
    val scrollSpeed: Int = 50,
    val movementSmoothing: Int = 20,
    val invertScroll: Boolean = false,
    val tapToClick: Boolean = true,
    val penMode: Boolean = false
) {"""

new_settings = """data class MouseSettings(
    val profile: TrackpadProfile = TrackpadProfile.NATURAL,
    val sensitivity: Int = 65,
    val scrollSpeed: Int = 50,
    val movementSmoothing: Int = 20,
    val acceleration: Int = 30,
    val curve: AccelerationCurve = AccelerationCurve.LINEAR,
    val invertScroll: Boolean = false,
    val tapToClick: Boolean = true,
    val dragLock: Boolean = false,
    val edgeScroll: Boolean = true,
    val threeFingerGestures: Boolean = true,
    val palmRejection: Boolean = true,
    val penMode: Boolean = false
) {"""

text = text.replace(old_settings, profile_enum + new_settings)
text = text.replace("movementSmoothing = movementSmoothing.coerceIn(MIN, MAX)", "movementSmoothing = movementSmoothing.coerceIn(MIN, MAX),\n        acceleration = acceleration.coerceIn(MIN, MAX)")

with open('app/src/main/java/com/aeropad/remote/model/Settings.kt', 'w') as f:
    f.write(text)
