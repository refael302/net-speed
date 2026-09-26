package app.netstrip.statusbar.ui

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.netstrip.core.GraphSpan
import app.netstrip.core.SAMPLE_PERIOD_MILLIS
import app.netstrip.core.SpeedSampler
import app.netstrip.statusbar.NetStripApp
import app.netstrip.statusbar.R
import app.netstrip.statusbar.data.DeviceCounters
import app.netstrip.statusbar.data.LiveSpeed
import app.netstrip.statusbar.service.Indicator
import app.netstrip.statusbar.update.AppUpdate
import app.netstrip.statusbar.update.UpdateState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = (app as NetStripApp).prefs
    private var previewJob: Job? = null

    val spark = LiveSpeed.spark
    val statusBarOn = prefs.enabled

    private val spanState = MutableStateFlow(GraphSpan.MINUTE)
    val graphSpan: StateFlow<GraphSpan> = spanState

    private val notifications = MutableStateFlow(true)
    val notificationsAllowed: StateFlow<Boolean> = notifications

    private val batteryOk = MutableStateFlow(true)
    val batteryUnrestricted: StateFlow<Boolean> = batteryOk

    private val note = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = note

    private val updates = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = updates

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
        notifications.value = app.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        val power = app.getSystemService(PowerManager::class.java)
        batteryOk.value = power.isIgnoringBatteryOptimizations(app.packageName)
        if (prefs.enabled.value && notifications.value && !LiveSpeed.serviceRunning.value) {
            setStatusBar(true)
        }
    }

    fun allowUnrestrictedBattery() {
        val app = getApplication<Application>()
        val power = app.getSystemService(PowerManager::class.java)
        if (power.isIgnoringBatteryOptimizations(app.packageName)) {
            batteryOk.value = true
            return
        }
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${app.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            app.startActivity(request)
        } catch (_: Exception) {
            try {
                app.startActivity(fallback)
            } catch (_: Exception) {
            }
        }
    }

    fun selectSpan(span: GraphSpan) {
        spanState.value = span
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

    fun openNotificationSettings() {
        val app = getApplication<Application>()
        app.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun dismissUpdate() {
        if (updates.value is UpdateState.Downloading || updates.value is UpdateState.Checking) return
        updates.value = UpdateState.Idle
    }

    fun checkForUpdate() {
        if (updates.value is UpdateState.Checking || updates.value is UpdateState.Downloading) return
        updates.value = UpdateState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            updates.value = try {
                val manifest = AppUpdate.fetchManifest()
                val installed = AppUpdate.installedVersionCode(getApplication())
                if (manifest.versionCode > installed) UpdateState.Available(manifest) else UpdateState.UpToDate
            } catch (_: Exception) {
                UpdateState.Failed
            }
        }
    }

    fun installUpdate() {
        val manifest = (updates.value as? UpdateState.Available)?.manifest ?: return
        val app = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= 26 && !app.packageManager.canRequestPackageInstalls()) {
            app.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return
        }
        updates.value = UpdateState.Downloading
        viewModelScope.launch(Dispatchers.IO) {
            updates.value = try {
                val apk = AppUpdate.downloadApk(app, manifest.apkUrl)
                withContext(Dispatchers.Main) {
                    app.startActivity(AppUpdate.installIntent(app, apk))
                }
                UpdateState.Available(manifest)
            } catch (_: Exception) {
                UpdateState.Failed
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
                delay(SAMPLE_PERIOD_MILLIS)
            }
        }
    }
}
