package com.aeropad.remote.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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

private data class HostOsGuide(
    val id: String,
    val label: String,
    val emoji: String,
    val steps: List<String>
)

private val hostGuides = listOf(
    HostOsGuide(
        id = "win",
        label = "Windows",
        emoji = "🪟",
        steps = listOf(
            "1. Open Connect in AeroPad and tap 'Make phone discoverable'.",
            "2. On Windows: Settings → Bluetooth & devices → Add device → Bluetooth.",
            "3. Pick your phone, confirm the 6-digit PIN on both screens, then tap Connect in AeroPad."
        )
    ),
    HostOsGuide(
        id = "mac",
        label = "macOS",
        emoji = "🍎",
        steps = listOf(
            "1. Open Connect in AeroPad and tap 'Make phone discoverable'.",
            "2. On Mac: System Settings → Bluetooth → Nearby Devices.",
            "3. Click Connect next to your phone and accept the pairing request on both devices."
        )
    ),
    HostOsGuide(
        id = "tv",
        label = "Android TV",
        emoji = "📺",
        steps = listOf(
            "1. Open Connect in AeroPad and tap 'Make phone discoverable'.",
            "2. On TV: Settings → Remotes & Accessories → Pair accessory.",
            "3. Select your phone. Tip: use the Android tile for D-pad, Home, Back & Media!"
        )
    ),
    HostOsGuide(
        id = "linux",
        label = "Linux / Deck",
        emoji = "🐧",
        steps = listOf(
            "1. Open Connect in AeroPad and tap 'Make phone discoverable'.",
            "2. In Bluetooth settings (or Steam Deck Bluetooth), scan and pair your phone.",
            "3. Trust the device so it reconnects automatically as a standard HID controller."
        )
    )
)

private val steps = listOf(
    OnboardStep("📡", "Pair once, control everything",
        "AeroPad makes your phone a real Bluetooth keyboard & mouse. Pick your host OS below to see the 3-step pairing flow — no PC software needed."),
    OnboardStep("🖱️", "Mouse, keyboard & more",
        "Trackpad with edge-scroll & gestures, full PC keyboard, numpad, media remote, presenter and gamepad — all ready out of the box."),
    OnboardStep("🎨", "Make it yours",
        "Build custom layouts and gamepads with the editors, record macros — and pick Light or Dark mode in Settings."),
    OnboardStep("⚡", "Pro tips",
        "Long-press a trackpad for right-click. Use the gear icon inside Mouse or Keyboard for live tuning. Tap the floating ⇄ pill to quick-switch screens.")
)

@Composable
fun OnboardingOverlay(
    visible: Boolean,
    onFinish: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    var selectedOs by remember { mutableIntStateOf(0) }

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
            GlassCard(modifier = Modifier.padding(20.dp)) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
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
                    if (step == 0) {
                        Spacer(Modifier.height(12.dp))
                        val guide = hostGuides[selectedOs.coerceIn(0, hostGuides.lastIndex)]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            hostGuides.forEachIndexed { idx, os ->
                                androidx.compose.material3.FilterChip(
                                    selected = selectedOs == idx,
                                    onClick = { selectedOs = idx },
                                    label = { Text("${os.emoji} ${os.label}", maxLines = 1) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                    MaterialTheme.shapes.medium
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            guide.steps.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
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
                    Spacer(Modifier.height(16.dp))
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
