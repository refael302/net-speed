package app.netstrip.statusbar.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.netstrip.core.SpeedReading
import app.netstrip.core.labelFor
import app.netstrip.statusbar.MainActivity
import app.netstrip.statusbar.R
import app.netstrip.statusbar.data.LiveSpeed

class SpeedTileService : TileService() {
    override fun onStartListening() {
        val tile = qsTile ?: return
        val running = LiveSpeed.serviceRunning.value
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= 29) {
            val reading = LiveSpeed.reading.value
            tile.subtitle = if (running && reading is SpeedReading.Live) {
                val down = labelFor(reading.downBytesPerSec).icon
                val up = labelFor(reading.upBytesPerSec).icon
                "↓$down  ↑$up"
            } else {
                null
            }
        }
        tile.updateTile()
    }

    override fun onClick() {
        if (LiveSpeed.serviceRunning.value) {
            Indicator.stop(this)
        } else if (!notificationsAllowed()) {
            openApp()
        } else {
            Indicator.start(this)
        }
        onStartListening()
    }

    private fun notificationsAllowed(): Boolean {
        return getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(
                this,
                2,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
