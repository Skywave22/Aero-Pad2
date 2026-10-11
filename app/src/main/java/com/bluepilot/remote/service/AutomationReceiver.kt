package com.bluepilot.remote.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bluepilot.remote.data.macros.MacroRepository
import com.bluepilot.remote.domain.AutomationCommands
import com.bluepilot.remote.domain.HidController
import com.bluepilot.remote.domain.MacroEngine
import com.bluepilot.remote.domain.SettingsStore
import com.bluepilot.remote.model.HidAction
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * V2 MATRIX 8 b2 — Tasker/automation broadcast receiver.
 *
 * SECURITY MODEL (layered):
 *  1. The Settings toggle "Automation API" is OFF by default — every
 *     broadcast is dropped until the user opts in.
 *  2. Command parsing is total (AutomationCommands.parse) — malformed
 *     extras can't throw.
 *  3. Only four verbs exist: macro / media / type / disconnect. No raw
 *     keycodes, no connect-to-arbitrary-MAC — an automation app can only
 *     trigger things the user has already built or standard media taps.
 *
 * Example (Tasker "Send Intent" / adb):
 *   am broadcast -a com.bluepilot.remote.AUTOMATION \
 *      -n com.bluepilot.remote/.service.AutomationReceiver \
 *      --es cmd macro --es name "Open browser"
 */
@AndroidEntryPoint
class AutomationReceiver : BroadcastReceiver() {

    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var macros: MacroRepository
    @Inject lateinit var engine: MacroEngine
    @Inject lateinit var hid: HidController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AutomationCommands.ACTION) return
        val command = AutomationCommands.parse(
            cmd = intent.getStringExtra(AutomationCommands.EXTRA_CMD),
            name = intent.getStringExtra(AutomationCommands.EXTRA_NAME),
            key = intent.getStringExtra(AutomationCommands.EXTRA_KEY),
            text = intent.getStringExtra(AutomationCommands.EXTRA_TEXT)
        ) ?: run {
            Timber.w("automation: unparseable command dropped")
            return
        }
        // goAsync: broadcast handlers must return fast; the gate check +
        // macro lookup are suspend work. Result finished within the 10s
        // async window (all operations here are quick local reads/sends).
        val pending = goAsync()
        scope.launch {
            try {
                val enabled = runCatching {
                    settings.appSettings.first().automationApi
                }.getOrDefault(false)
                if (!enabled) {
                    Timber.w("automation: API disabled in Settings — dropped")
                    return@launch
                }
                when (command) {
                    is AutomationCommands.Command.PlayMacro -> {
                        val all = runCatching { macros.observeAll().first() }
                            .getOrDefault(emptyList())
                        val match = all.firstOrNull {
                            it.spec.name.equals(command.name, ignoreCase = true)
                        }
                        if (match != null) engine.play(match.id)
                        else Timber.w("automation: macro '%s' not found", command.name)
                    }
                    is AutomationCommands.Command.MediaKey ->
                        hid.send(HidAction.MediaTap(command.usage))
                    is AutomationCommands.Command.TypeText ->
                        hid.send(HidAction.TypeText(command.text))
                    AutomationCommands.Command.Disconnect ->
                        hid.disconnect()
                }
            } catch (t: Throwable) {
                Timber.w(t, "automation command failed")
            } finally {
                pending.finish()
            }
        }
    }
}
