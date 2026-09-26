package app.netstrip.statusbar.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import app.netstrip.core.BytePair
import app.netstrip.core.Transport
import app.netstrip.core.selectBytes

object DeviceCounters {
    fun read(context: Context): Pair<BytePair?, Transport> {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity.activeNetwork?.let(connectivity::getNetworkCapabilities)
        val transport = when {
            capabilities == null -> Transport.NONE
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.ETHERNET
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
            else -> Transport.OTHER
        }
        val bytes = selectBytes(
            totalRx = TrafficStats.getTotalRxBytes(),
            totalTx = TrafficStats.getTotalTxBytes(),
            mobileRx = TrafficStats.getMobileRxBytes(),
            mobileTx = TrafficStats.getMobileTxBytes(),
            transport = transport,
        )
        return bytes to transport
    }
}
