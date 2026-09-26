package app.netstrip.statusbar.service

import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import app.netstrip.statusbar.NetStripApp

object Indicator {
    fun start(context: android.content.Context): Boolean {
        val app = context.applicationContext as NetStripApp
        app.prefs.setEnabled(true)
        return try {
            ContextCompat.startForegroundService(app, Intent(app, SpeedStatusService::class.java))
            true
        } catch (error: Exception) {
            Log.w(TAG, "Could not start the status bar icon", error)
            app.prefs.setEnabled(false)
            false
        }
    }

    fun stop(context: android.content.Context) {
        val app = context.applicationContext as NetStripApp
        app.prefs.setEnabled(false)
        app.stopService(Intent(app, SpeedStatusService::class.java))
    }

    private const val TAG = "NetStrip"
}
