package com.bluepilot.remote.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * UI/UX REDESIGN — First-run onboarding: a 4-step tutorial overlay shown
 * once (persisted via AppSettings.onboardingDone). Pure overlay — no
 * navigation hijack, skippable at any step.
 */
private data class OnboardStep(val emoji: String, val title: String, val body: String)

private val steps = listOf(
    OnboardStep("📡", "Pair once, control everything",
        "BluePilot makes your phone a real Bluetooth keyboard & mouse. Open Connect, make the phone discoverable, and pair from your PC — no PC software needed."),
    OnboardStep("🖱️", "Mouse, keyboard & more",
        "Trackpad with gestures, full PC keyboard, numpad, media remote, presenter and gamepad — all ready out of the box."),
    OnboardStep("🎨", "Make it yours",
        "Build custom layouts and gamepads with the editors, record macros, and switch the entire look in Themes — 13 designs included."),
    OnboardStep("⚡", "Pro tips",
        "Long-press a trackpad for right-click. Swipe the top bar in a layout to switch profiles. Record macros from ANY screen via Macros → Record.")
)

@Composable
fun OnboardingOverlay(
    visible: Boolean,
    onFinish: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                // BUGFIX: enabled=false does NOT consume touches - taps leaked
                // through to the screen behind. An enabled no-op click without
                // ripple properly swallows everything.
                .clickable(
                    interactionSource = androidx.compose.runtime.remember {
                        androidx.compose.foundation.interaction.MutableInteractionSource()
                    },
                    indication = null
                ) {},
            contentAlignment = Alignment.Center
        ) {
            GlassCard(modifier = Modifier.padding(28.dp)) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val s = steps[step.coerceIn(0, steps.size - 1)]
                    Text(s.emoji, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        s.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        s.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))
                    // Step dots
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        steps.indices.forEach { i ->
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (i == step) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        CircleShape
                                    )
                            )
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onFinish) { Text("Skip") }
                        Button(onClick = {
                            if (step < steps.size - 1) step++ else onFinish()
                        }) {
                            Text(if (step < steps.size - 1) "Next" else "Let's go!")
                        }
                    }
                }
            }
        }
    }
}
