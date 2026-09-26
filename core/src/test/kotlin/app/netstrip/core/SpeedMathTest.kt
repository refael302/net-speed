package app.netstrip.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedMathTest {
    @Test
    fun wifiAndCellularBothCountTheWholeDevice() {
        val expected = BytePair(10_000, 8_000)
        assertEquals(
            expected,
            selectBytes(10_000, 8_000, 400, 100, Transport.WIFI),
        )
        assertEquals(
            expected,
            selectBytes(10_000, 8_000, 400, 100, Transport.CELLULAR),
        )
    }

    @Test
    fun unsupportedCountersReturnNull() {
        assertNull(
            selectBytes(
                totalRx = TrafficUnsupported,
                totalTx = TrafficUnsupported,
                mobileRx = TrafficUnsupported,
                mobileTx = TrafficUnsupported,
                transport = Transport.WIFI,
            ),
        )
    }

    @Test
    fun rateUsesTheRealElapsedTime() {
        val sampler = SpeedSampler()
        assertNull(sampler.onSnapshot(BytePair(0, 0), Transport.WIFI, 0L))
        val reading = sampler.onSnapshot(
            BytePair(1_500_000, 250_000),
            Transport.WIFI,
            1_500_000_000L,
        ) as SpeedReading.Live
        assertEquals(1_000_000.0, reading.downBytesPerSec, 0.001)
        assertEquals(166_666.7, reading.upBytesPerSec, 0.1)
    }

    @Test
    fun counterResetDoesNotReportNegativeSpeed() {
        val sampler = SpeedSampler()
        sampler.onSnapshot(BytePair(5_000, 5_000), Transport.WIFI, 0L)
        val reading = sampler.onSnapshot(
            BytePair(100, 80),
            Transport.WIFI,
            1_000_000_000L,
        ) as SpeedReading.Live
        assertEquals(0.0, reading.downBytesPerSec, 0.0)
        assertEquals(0.0, reading.upBytesPerSec, 0.0)
    }

    @Test
    fun switchingNetworkKeepsCountingTheSameTraffic() {
        val sampler = SpeedSampler()
        sampler.onSnapshot(BytePair(0, 0), Transport.WIFI, 0L)
        val reading = sampler.onSnapshot(
            BytePair(500, 0),
            Transport.CELLULAR,
            1_000_000_000L,
        )
        assertTrue(reading is SpeedReading.Live)
        assertEquals(500.0, (reading as SpeedReading.Live).downBytesPerSec, 0.001)
        assertEquals(Transport.CELLULAR, reading.transport)
    }

    @Test
    fun unsupportedReadingClearsTheBaseline() {
        val sampler = SpeedSampler()
        sampler.onSnapshot(BytePair(0, 0), Transport.WIFI, 0L)
        assertEquals(
            SpeedReading.Unsupported,
            sampler.onSnapshot(null, Transport.WIFI, 1_000_000_000L),
        )
        assertNull(sampler.onSnapshot(BytePair(0, 0), Transport.WIFI, 2_000_000_000L))
    }

    private companion object {
        const val TrafficUnsupported = -1L
    }
}
