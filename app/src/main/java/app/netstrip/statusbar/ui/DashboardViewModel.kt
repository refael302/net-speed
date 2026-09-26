package app.netstrip.statusbar.ui

import android.app.Application
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.netstrip.core.SpeedSampler
import app.netstrip.statusbar.NetStripApp
import app.netstrip.statusbar.R
import app.netstrip.statusbar.data.DeviceCounters
import app.netstrip.statusbar.data.IconMode
import app.netstrip.statusbar.data.LiveSpeed
import app.netstrip.statusbar.service.Indicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = (app as NetStripApp).prefs
    private var previewJob: Job? = null

    val reading = LiveSpeed.reading
    val spark = LiveSpeed.spark
    val statusBarOn = prefs.enabled
    val iconMode = prefs.iconMode
    val startOnBoot = prefs.startOnBoot

    private val notifications = MutableStateFlow(true)
    val notificationsAllowed: StateFlow<Boolean> = notifications

    private val battery = MutableStateFlow(true)
    val batteryUnrestricted: StateFlow<Boolean> = battery

    private val note = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = note

    init {
        refreshSystemState()
        viewModelScope.launch {
            LiveSpeed.serviceRunning.collect { running ->
                if (running) {
                    previewJob?.cancel()
                    previewJob = null
                } else {
                    startPreview()
                }
            }
        }
        if (prefs.enabled.value && notifications.value) {
            setStatusBar(true)
        }
    }

    fun refreshSystemState() {
        val app = getApplication<Application>()
        val manager = app.getSystemService(NotificationManager::class.java)
        notifications.value = manager.areNotificationsEnabled()
        val power = app.getSystemService(PowerManager::class.java)
        battery.value = power.isIgnoringBatteryOptimizations(app.packageName)
        if (prefs.enabled.value && notifications.value && !LiveSpeed.serviceRunning.value) {
            setStatusBar(true)
        }
    }

    fun setStatusBar(enabled: Boolean) {
        note.value = null
        if (!enabled) {
            Indicator.stop(getApplication())
            return
        }
        val started = Indicator.start(getApplication())
        if (!started) {
            note.value = getApplication<Application>().getString(R.string.start_failed)
        }
    }

    fun setIconMode(mode: IconMode) {
        prefs.setIconMode(mode)
    }

    fun setStartOnBoot(enabled: Boolean) {
        prefs.setStartOnBoot(enabled)
    }

    fun openNotificationSettings() {
        val app = getApplication<Application>()
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
    }

    fun openBatterySettings() {
        val app = getApplication<Application>()
        val packageUri = Uri.parse("package:${app.packageName}")
        val intents = listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
        )
        for (intent in intents) {
            try {
                app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }

    private fun startPreview() {
        if (previewJob?.isActive == true) return
        val sampler = SpeedSampler()
        previewJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive && !LiveSpeed.serviceRunning.value) {
                val (bytes, transport) = DeviceCounters.read(getApplication())
                val reading = sampler.onSnapshot(bytes, transport, System.nanoTime())
                if (!LiveSpeed.serviceRunning.value && reading != null) {
                    LiveSpeed.publish(reading)
                }
                delay(1_000)
            }
        }
    }
}
