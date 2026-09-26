package app.netstrip.statusbar.ui

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    private val notifications = MutableStateFlow(true)
    val notificationsAllowed: StateFlow<Boolean> = notifications

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

    fun openNotificationSettings() {
        val app = getApplication<Application>()
        app.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
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
                delay(1_000)
            }
        }
    }
}
