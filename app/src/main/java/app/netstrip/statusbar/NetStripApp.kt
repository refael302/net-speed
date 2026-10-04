package app.netstrip.statusbar

import android.app.Application
import app.netstrip.statusbar.data.Prefs
import app.netstrip.statusbar.data.UsageHistory
import app.netstrip.statusbar.service.Indicator

class NetStripApp : Application() {
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        UsageHistory.attach(this)
        Indicator.ensureWatchdog(this)
    }
}
