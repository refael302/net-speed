package app.netstrip.statusbar.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import app.netstrip.statusbar.NetStripApp

object Indicator {
    fun start(context: Context): Boolean {
        val app = context.applicationContext as NetStripApp
        app.prefs.setEnabled(true)
        ensureWatchdog(app)
        return try {
            ContextCompat.startForegroundService(app, Intent(app, SpeedStatusService::class.java))
            true
        } catch (error: Exception) {
            Log.w(TAG, "Could not start the status bar icon", error)
            false
        }
    }

    fun stop(context: Context) {
        val app = context.applicationContext as NetStripApp
        app.prefs.setEnabled(false)
        app.stopService(Intent(app, SpeedStatusService::class.java))
    }

    /** Runs again after reboot, and restarts the icon if the phone kills it while it should be on. */
    fun ensureWatchdog(context: Context) {
        val app = context.applicationContext
        val job = JobInfo.Builder(WATCHDOG_JOB_ID, ComponentName(app, KeepAliveJob::class.java))
            .setPersisted(true)
            .setPeriodic(15 * 60 * 1000L)
            .build()
        app.getSystemService(JobScheduler::class.java).schedule(job)
    }

    fun scheduleBootRetries(context: Context) {
        val app = context.applicationContext
        val alarm = app.getSystemService(AlarmManager::class.java)
        listOf(20_000L to 71, 90_000L to 72).forEach { (delayMillis, requestCode) ->
            val pending = PendingIntent.getBroadcast(
                app,
                requestCode,
                Intent(app, BootReceiver::class.java).setAction(BootReceiver.ACTION_RETRY),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            alarm.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delayMillis,
                pending,
            )
        }
    }

    private const val TAG = "NetStrip"
    private const val WATCHDOG_JOB_ID = 4109
}
