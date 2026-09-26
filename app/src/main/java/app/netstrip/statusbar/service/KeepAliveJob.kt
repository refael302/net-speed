package app.netstrip.statusbar.service

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.SystemClock
import app.netstrip.statusbar.NetStripApp

class KeepAliveJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val stillBooting = SystemClock.elapsedRealtime() < BOOT_WINDOW_MILLIS
        val wanted = (application as NetStripApp).prefs.enabled.value
        if (stillBooting || wanted) {
            Indicator.start(this)
        } else {
            Indicator.ensureWatchdog(this)
        }
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    private companion object {
        const val BOOT_WINDOW_MILLIS = 30 * 60 * 1000L
    }
}