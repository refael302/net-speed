package app.netstrip.statusbar.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.os.UserManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in START_ACTIONS) return
        if (!context.getSystemService(UserManager::class.java).isUserUnlocked) return
        val justBooted = SystemClock.elapsedRealtime() < BOOT_WINDOW_MILLIS
        if (action == Intent.ACTION_USER_UNLOCKED && !justBooted) return
        val pending = goAsync()
        try {
            Indicator.start(context)
            if (action != Intent.ACTION_MY_PACKAGE_REPLACED) {
                Indicator.scheduleBootRetries(context)
            }
        } finally {
            pending.finish()
        }
    }

    companion object {
        const val ACTION_RETRY = "app.netstrip.statusbar.RETRY_START"
        private const val BOOT_WINDOW_MILLIS = 30 * 60 * 1000L
        private val START_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            ACTION_RETRY,
        )
    }
}