package app.netstrip.statusbar

import android.app.Application
import app.netstrip.statusbar.data.Prefs

class NetStripApp : Application() {
    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }
}
