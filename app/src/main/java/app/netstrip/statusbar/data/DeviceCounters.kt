package app.netstrip.statusbar.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import app.netstrip.core.BytePair
import app.netstrip.core.Transport
import app.netstrip.core.selectBytes

data class CounterSnapshot(
    val total: BytePair?,
    val mobile: BytePair?,
    val transport: Transport,
)

object DeviceCounters {
    fun read(context: Context): CounterSnapshot {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
        val transport = when {
            capabilities == null -> Transport.NONE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
            else -> Transport.OTHER
        }
        val mobileRx = TrafficStats.getMobileRxBytes()
        val mobileTx = TrafficStats.getMobileTxBytes()
        val total = selectBytes(
            totalRx = TrafficStats.getTotalRxBytes(),
            totalTx = TrafficStats.getTotalTxBytes(),
            mobileRx = mobileRx,
            mobileTx = mobileTx,
            transport = transport,
        )
        val mobile = if (mobileRx >= 0L && mobileTx >= 0L) BytePair(mobileRx, mobileTx) else null
        return CounterSnapshot(total, mobile, transport)
    }
}
