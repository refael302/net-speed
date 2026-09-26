package app.netstrip.statusbar.data

import app.netstrip.core.SpeedReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object LiveSpeed {
    private val lock = Any()
    private val history = ArrayDeque<Float>()

    private val readingState = MutableStateFlow<SpeedReading>(SpeedReading.Waiting)
    val reading: StateFlow<SpeedReading> = readingState

    private val sparkState = MutableStateFlow<List<Float>>(emptyList())
    val spark: StateFlow<List<Float>> = sparkState

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
                    history.addLast(reading.downBytesPerSec.toFloat())
                    while (history.size > 60) history.removeFirst()
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
