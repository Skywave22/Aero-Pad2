import re

with open('app/src/main/java/com/aeropad/remote/MainActivity.kt', 'r') as f:
    text = f.read()

lifecycle_observer = """        // V2 PART B — bind HidService lifecycle to the activity.
        androidx.lifecycle.lifecycleScope.launchWhenStarted {
            com.aeropad.remote.service.HidService.start(this@MainActivity)
        }"""

text = text.replace("        // V2 M4 b2 — one-shot, fully gated inside (opt-in setting, silent on failure).\n        autoReconnector.maybeReconnect()", "        // V2 M4 b2 — one-shot, fully gated inside (opt-in setting, silent on failure).\n        autoReconnector.maybeReconnect()\n\n" + lifecycle_observer)

with open('app/src/main/java/com/aeropad/remote/MainActivity.kt', 'w') as f:
    f.write(text)
