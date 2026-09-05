import re

with open('app/src/main/java/com/aeropad/remote/hid/HidEngine.kt', 'r') as f:
    text = f.read()

funcs = """    /** ADV S5 — real measured connection-health metrics (public read). */
    val health = com.aeropad.remote.domain.ConnectionHealthTracker()
    
    fun getHealthSnapshot(): com.aeropad.remote.domain.ConnectionHealthTracker.Snapshot {
        return health.snapshot()
    }"""

text = text.replace("    /** ADV S5 — real measured connection-health metrics (public read). */\n    val health = com.aeropad.remote.domain.ConnectionHealthTracker()", funcs)

with open('app/src/main/java/com/aeropad/remote/hid/HidEngine.kt', 'w') as f:
    f.write(text)

