import re

with open('app/src/main/java/com/aeropad/remote/MainActivity.kt', 'r') as f:
    text = f.read()

perf_start = """            androidx.compose.runtime.LaunchedEffect(app.fpsOverlay) {
                if (app.fpsOverlay) {
                    com.aeropad.remote.perf.FrameStats.start()
                } else {
                    com.aeropad.remote.perf.FrameStats.stop()
                }
            }"""

text = text.replace("            // Section 1 theme engine: resolve the active AppThemeSpec.", perf_start + "\n\n            // Section 1 theme engine: resolve the active AppThemeSpec.")

with open('app/src/main/java/com/aeropad/remote/MainActivity.kt', 'w') as f:
    f.write(text)
