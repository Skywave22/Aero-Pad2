import re

with open('app/src/main/java/com/aeropad/remote/MainActivity.kt', 'r') as f:
    text = f.read()

# Add FPS overlay UI conditionally
overlay = """            // V2 PART A — real-time FPS overlay
            val fpsOverlay by androidx.compose.runtime.collectAsState(app.fpsOverlay, context = kotlin.coroutines.EmptyCoroutineContext) // Need flow collector or simple check
            val stats by com.aeropad.remote.perf.FrameStats.stats.collectAsState()
            
            if (app.fpsOverlay && stats.running) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.TopEnd
                ) {
                    androidx.compose.material3.Surface(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f),
                        contentColor = androidx.compose.ui.graphics.Color.Green,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 8.dp),
                        modifier = androidx.compose.ui.Modifier.padding(top = 32.dp)
                    ) {
                        androidx.compose.foundation.layout.Row(
                            modifier = androidx.compose.ui.Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Text("${stats.fps} FPS", style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                            androidx.compose.material3.Text("${stats.jankPercent}% Jank", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = if (stats.jankPercent > 5) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Green)
                        }
                    }
                }
            }
"""

text = text.replace("            }", overlay + "\n            }")

with open('app/src/main/java/com/aeropad/remote/MainActivity.kt', 'w') as f:
    f.write(text)
