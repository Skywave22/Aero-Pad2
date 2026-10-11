package com.bluepilot.remote.ui.screens.preview3d

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bluepilot.remote.ui.components.KeyCard
import com.bluepilot.remote.ui.components.Material3D
import com.bluepilot.remote.ui.components.surface3D

/**
 * SECTION 10.5 - 3D Preview Room: showcases every 3D effect in one place.
 * Also SECTION 10.4-lite: material + lighting sliders let users tune and
 * preview a custom 3D look live.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Preview3DScreen(onBack: () -> Unit) {
    var material by remember { mutableStateOf(Material3D.GLOSSY) }
    var elevation by remember { mutableFloatStateOf(8f) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("3D Preview Room") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Materials", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Material3D.entries.forEach { m ->
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .height(52.dp)
                            .fillMaxWidth(0.18f)
                            .surface3D(
                                base = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.medium,
                                material = m,
                                elevation = elevation.dp
                            )
                            .clickable { material = m }
                    )
                }
            }
            Text("Shadow depth: ${elevation.toInt()}dp",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            Slider(value = elevation, onValueChange = { elevation = it }, valueRange = 0f..24f)
            Text("Press me (3D key)", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyCard("Tap", Modifier.fillMaxWidth(0.3f), 56.dp) {}
                KeyCard("Hold", Modifier.fillMaxWidth(0.42f), 56.dp, emphasized = true) {}
            }
            Text("Toggle & slider", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            var demo by remember { mutableStateOf(true) }
            Switch(checked = demo, onCheckedChange = { demo = it })
            Spacer(Modifier.height(24.dp))
        }
    }
}
