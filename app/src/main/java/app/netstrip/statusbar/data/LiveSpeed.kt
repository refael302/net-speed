package app.netstrip.statusbar.data

import app.netstrip.core.HISTORY_MILLIS
import app.netstrip.core.SpeedReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class TrafficPoint(
    val down: Float,
    val up: Float,
    val atMillis: Long,
)

object LiveSpeed {
    private val lock = Any()
    private val history = ArrayDeque<TrafficPoint>()

    private val readingState = MutableStateFlow<SpeedReading>(SpeedReading.Waiting)
    val reading: StateFlow<SpeedReading> = readingState

    private val sparkState = MutableStateFlow<List<TrafficPoint>>(emptyList())
    val spark: StateFlow<List<TrafficPoint>> = sparkState

    private val serviceState = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = serviceState

    fun setServiceRunning(running: Boolean) {
        serviceState.value = running
    }

    fun publish(reading: SpeedReading) {
        synchronized(lock) {
            readingState.value = reading
            when (reading) {
                is SpeedReading.Live -> {
                    val atMillis = System.currentTimeMillis()
                    history.addLast(
                        TrafficPoint(
                            down = reading.downBytesPerSec.toFloat(),
                            up = reading.upBytesPerSec.toFloat(),
                            atMillis = atMillis,
                        ),
                    )
                    val cutoff = atMillis - HISTORY_MILLIS
                    while (history.isNotEmpty() && history.first().atMillis < cutoff) {
                        history.removeFirst()
                    }
                    sparkState.value = history.toList()
                }
                SpeedReading.Unsupported -> {
                    history.clear()
                    sparkState.value = emptyList()
                }
                SpeedReading.Waiting -> Unit
            }
        }
    }
}
