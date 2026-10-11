package com.bluepilot.remote.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.bluepilot.remote.domain.HidController
import com.bluepilot.remote.model.HidAction
import com.bluepilot.remote.model.HidConsumer
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * V2 MATRIX 8 — Quick Settings tile.
 *
 * Connected → tap sends media Play/Pause to the host WITHOUT opening the
 * app (fastest possible control: two swipes from anywhere).
 * Not connected → tap opens AeroPad to connect.
 *
 * Tile state is honest: ACTIVE only when the HID link is really up
 * (read from the same StateFlow the whole app uses — no duplicate truth).
 */
@AndroidEntryPoint
class AeroPadTileService : TileService() {

    @Inject lateinit var hid: HidController

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val connected = runCatching { hid.state.value.isConnected }.getOrDefault(false)
        tile.state = if (connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(com.bluepilot.remote.R.string.app_name)
        tile.subtitle = if (connected) "⏯ Play/Pause" else "Tap to connect"
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val connected = runCatching { hid.state.value.isConnected }.getOrDefault(false)
        if (connected) {
            // Media toggle straight through the normal HID path.
            runCatching { hid.send(HidAction.MediaTap(HidConsumer.PLAY_PAUSE)) }
                .onFailure { Timber.w(it, "tile media tap failed") }
        } else {
            // Open the app to connect (collapses the shade first).
            val intent = Intent(this, com.bluepilot.remote.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching {
                if (android.os.Build.VERSION.SDK_INT >= 34) {
                    startActivityAndCollapse(
                        android.app.PendingIntent.getActivity(
                            this, 0, intent,
                            android.app.PendingIntent.FLAG_IMMUTABLE or
                                android.app.PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    )
                } else {
                    @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
                    startActivityAndCollapse(intent)
                }
            }.onFailure { Timber.w(it, "tile open app failed") }
        }
        updateTile()
    }
}
