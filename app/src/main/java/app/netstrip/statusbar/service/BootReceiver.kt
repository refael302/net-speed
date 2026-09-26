package app.netstrip.statusbar.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.netstrip.statusbar.NetStripApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val boot = action == Intent.ACTION_BOOT_COMPLETED
        val replaced = action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!boot && !replaced) return
        val prefs = (context.applicationContext as NetStripApp).prefs
        if (!prefs.enabled.value) return
        if (boot && !prefs.startOnBoot.value) return
        Indicator.start(context)
    }
}
