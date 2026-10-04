package app.netstrip.statusbar.data

import android.content.Context
import android.util.Log
import app.netstrip.core.BytePair
import app.netstrip.core.Transport
import app.netstrip.core.UsageDelta
import app.netstrip.core.UsageSampler
import app.netstrip.core.UsageSnapshot
import app.netstrip.core.decodeUsageStore
import app.netstrip.core.encodeUsageStore
import app.netstrip.core.recordAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.ZoneId

object UsageHistory {
    private val lock = Any()
    private val snapshot = MutableStateFlow(UsageSnapshot())
    val state: StateFlow<UsageSnapshot> = snapshot

    private var file: File? = null
    private var attached = false

    fun attach(context: Context) {
        val target = File(context.applicationContext.filesDir, FILE_NAME)
        synchronized(lock) {
            file = target
            if (attached) return
            val now = System.currentTimeMillis()
            val loaded = try {
                val text = if (target.exists()) target.readText() else ""
                recordAll(decodeUsageStore(text), 0L, 0L, now, ZoneId.systemDefault())
            } catch (error: Exception) {
                Log.w(TAG, "Could not read stored usage", error)
                UsageSnapshot(atMillis = now)
            }
            snapshot.value = loaded
            attached = true
        }
    }

    fun onCounters(
        sampler: UsageSampler,
        total: BytePair?,
        mobile: BytePair?,
        transport: Transport,
        atMillis: Long,
    ) {
        val delta = sampler.onSnapshot(total, mobile, transport)
        if (delta == null || (delta.wifiBytes == 0L && delta.cellularBytes == 0L)) {
            touch(atMillis)
            return
        }
        add(delta, atMillis)
    }

    fun touch(atMillis: Long) {
        synchronized(lock) {
            val current = snapshot.value
            if (current.atMillis == atMillis) return
            snapshot.value = current.copy(atMillis = atMillis)
        }
    }

    private fun add(delta: UsageDelta, atMillis: Long) {
        val encoded: String
        synchronized(lock) {
            val next = recordAll(
                snapshot.value,
                delta.wifiBytes,
                delta.cellularBytes,
                atMillis,
                ZoneId.systemDefault(),
            )
            snapshot.value = next
            encoded = encodeUsageStore(next)
            persist(encoded)
        }
    }

    private fun persist(text: String) {
        val target = file ?: return
        try {
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(target)) {
                target.writeText(text)
                tmp.delete()
            }
        } catch (error: Exception) {
            Log.w(TAG, "Could not store usage", error)
        }
    }

    private const val TAG = "NetStrip"
    private const val FILE_NAME = "usage-buckets.txt"
}
