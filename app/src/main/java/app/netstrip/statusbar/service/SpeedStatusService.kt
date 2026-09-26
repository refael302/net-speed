package app.netstrip.statusbar.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.service.quicksettings.TileService
import android.util.Log
import app.netstrip.core.SAMPLE_PERIOD_MILLIS
import app.netstrip.core.SpeedReading
import app.netstrip.core.SpeedSampler
import app.netstrip.core.labelFor
import app.netstrip.statusbar.MainActivity
import app.netstrip.statusbar.NetStripApp
import app.netstrip.statusbar.R
import app.netstrip.statusbar.data.DeviceCounters
import app.netstrip.statusbar.data.IconMode
import app.netstrip.statusbar.data.LiveSpeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SpeedStatusService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private val icons = SpeedIconRenderer()

    private val openApp: PendingIntent by lazy {
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private val stopAction: PendingIntent by lazy {
        PendingIntent.getService(
            this,
            1,
            Intent(this, SpeedStatusService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            (application as NetStripApp).prefs.setEnabled(false)
            LiveSpeed.setServiceRunning(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        ensureChannel()
        val notification = buildNotification(SpeedReading.Waiting)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        LiveSpeed.setServiceRunning(true)
        if (loop == null) {
            loop = scope.launch { sampleLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loop?.cancel()
        loop = null
        scope.coroutineContext[Job]?.cancel()
        LiveSpeed.setServiceRunning(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private suspend fun sampleLoop() {
        val sampler = SpeedSampler()
        while (scope.isActive) {
            val screenOn = interactive()
            try {
                val (bytes, transport) = DeviceCounters.read(this)
                val reading = sampler.onSnapshot(bytes, transport, System.nanoTime())
                if (reading != null) {
                    LiveSpeed.publish(reading)
                    if (screenOn) {
                        val notification = buildNotification(reading)
                        withContext(Dispatchers.Main) {
                            notificationManager().notify(NOTIFICATION_ID, notification)
                            refreshTile()
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Speed sample failed", error)
            }
            delay(SAMPLE_PERIOD_MILLIS)
        }
    }

    private fun interactive(): Boolean {
        return getSystemService(PowerManager::class.java).isInteractive
    }

    private fun ensureChannel() {
        val manager = notificationManager()
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(reading: SpeedReading): Notification {
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setContentIntent(openApp)
            .setColor(0xFF3EE0A2.toInt())
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_stop),
                    getString(R.string.stop),
                    stopAction,
                ).build(),
            )
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        when (reading) {
            SpeedReading.Waiting -> {
                builder.setSmallIcon(R.drawable.ic_stat)
                builder.setContentText(getString(R.string.notification_measuring))
            }
            SpeedReading.Unsupported -> {
                builder.setSmallIcon(R.drawable.ic_stat)
                builder.setContentText(getString(R.string.unsupported))
            }
            is SpeedReading.Live -> {
                val down = labelFor(reading.downBytesPerSec)
                val up = labelFor(reading.upBytesPerSec)
                val bitmap = icons.render(down.icon, up.icon, IconMode.BOTH)
                builder.setSmallIcon(Icon.createWithBitmap(bitmap))
                builder.setContentText(speedLine(down.value, down.unit, up.value, up.unit))
                builder.setSubText(getString(R.string.all_traffic))
            }
        }
        return builder.build()
    }

    private fun speedLine(downValue: String, downUnit: String, upValue: String, upUnit: String): String {
        return "\u200E↓ $downValue $downUnit    ↑ $upValue $upUnit"
    }

    private fun refreshTile() {
        try {
            TileService.requestListeningState(this, ComponentName(this, SpeedTileService::class.java))
        } catch (_: Exception) {
        }
    }

    private fun notificationManager(): NotificationManager {
        return getSystemService(NotificationManager::class.java)
    }

    companion object {
        const val ACTION_STOP = "app.netstrip.statusbar.STOP"
        private const val TAG = "NetStrip"
        private const val CHANNEL_ID = "speed_v1"
        private const val NOTIFICATION_ID = 4108
    }
}
