package app.netstrip.core

enum class Transport {
    NONE,
    WIFI,
    CELLULAR,
    ETHERNET,
    OTHER,
}

data class BytePair(
    val rx: Long,
    val tx: Long,
)

sealed interface SpeedReading {
    data object Waiting : SpeedReading

    data object Unsupported : SpeedReading

    data class Live(
        val downBytesPerSec: Double,
        val upBytesPerSec: Double,
        val transport: Transport,
    ) : SpeedReading
}

/**
 * All bytes that entered or left the device, on Wi-Fi, cellular, and every other interface.
 * The per-network counters are ignored so a handoff does not drop traffic.
 */
@Suppress("UNUSED_PARAMETER")
fun selectBytes(
    totalRx: Long,
    totalTx: Long,
    mobileRx: Long,
    mobileTx: Long,
    transport: Transport,
): BytePair? {
    if (totalRx < 0L || totalTx < 0L) return null
    return BytePair(totalRx, totalTx)
}

private data class Sample(
    val bytes: BytePair,
    val transport: Transport,
    val nanos: Long,
)

/**
 * Turns absolute interface counters into bytes per second.
 * The first snapshot only arms the baseline, so the caller waits one more tick.
 */
class SpeedSampler {
    private var previous: Sample? = null

    fun onSnapshot(bytes: BytePair?, transport: Transport, nanos: Long): SpeedReading? {
        if (bytes == null) {
            previous = null
            return SpeedReading.Unsupported
        }
        val prev = previous
        if (prev == null) {
            previous = Sample(bytes, transport, nanos)
            return null
        }
        val elapsed = nanos - prev.nanos
        if (elapsed < 200_000_000L) return null
        previous = Sample(bytes, transport, nanos)
        val seconds = elapsed / 1_000_000_000.0
        val down = (bytes.rx - prev.bytes.rx).coerceAtLeast(0L).toDouble() / seconds
        val up = (bytes.tx - prev.bytes.tx).coerceAtLeast(0L).toDouble() / seconds
        return SpeedReading.Live(down, up, transport)
    }
}
