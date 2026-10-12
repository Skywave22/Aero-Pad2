package com.aeropad.remote.ui.screens.mylayout

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aeropad.remote.model.widgets.WidgetSpec
import com.aeropad.remote.ui.components.NotConnectedBanner
import com.aeropad.remote.ui.widgets.LayoutCanvas
import com.aeropad.remote.ui.widgets.WidgetEvents
import com.aeropad.remote.viewmodel.MyLayoutViewModel

/**
 * My Layout — the personalized control surface. Shows the layout bound to
 * the connected host (laptop / desktop / TV / tablet) and lets you switch
 * instantly; picking one while connected remembers it for that device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyLayoutScreen(
    onBack: () -> Unit,
    viewModel: MyLayoutViewModel = hiltViewModel()
) {
    val layout by viewModel.layout.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val connected by viewModel.connectedAddress.collectAsState()

    val events = remember {
        object : WidgetEvents {
            override fun onPrimary(widget: WidgetSpec) { viewModel.interactor.primary(widget) }
            override fun onSecondary(widget: WidgetSpec) { viewModel.interactor.secondary(widget) }
            override fun onTrackpadStart() { viewModel.interactor.trackpadStart() }
            override fun onTrackpadDelta(dx: Float, dy: Float) { viewModel.interactor.trackpadDelta(dx, dy) }
            override fun onScrollDelta(dy: Float) { viewModel.interactor.scrollDelta(dy) }
            override fun onJoystick(x: Float, y: Float) { viewModel.interactor.joystick(x, y) }
            override fun onDpad(dirX: Int, dirY: Int) { viewModel.interactor.dpad(dirX, dirY) }
            override fun onSwipe(widget: WidgetSpec, direction: com.aeropad.remote.domain.SwipeDirection) {
                viewModel.interactor.swipe(widget, direction)
            }
            override fun onTwoFingerTap(widget: WidgetSpec) { viewModel.interactor.twoFingerTap(widget) }
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("My Layout") },
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
        ) {
            NotConnectedBanner(connected == null)

            if (profiles.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    profiles.forEach { p ->
                        val current = layout?.name == p.spec.name
                        AssistChip(
                            onClick = { viewModel.show(p.id) },
                            label = { Text((if (current) "● " else "") + p.spec.name) }
                        )
                    }
                }
            }

            val spec = layout
            if (spec != null) {
                LayoutCanvas(
                    layout = spec,
                    events = events,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                )
            } else {
                Text(
                    text = "No layout selected yet.\n\n" +
                        "Create one in Layouts, then pick it here (or on the Devices " +
                        "screen) — AeroPad remembers which layout belongs to which " +
                        "paired host, so switching devices switches your controls.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    }
}
