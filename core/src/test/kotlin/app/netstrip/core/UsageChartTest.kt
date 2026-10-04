package app.netstrip.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class UsageChartTest {
    private val jerusalem = ZoneId.of("Asia/Jerusalem")

    @Test
    fun emptyWindowIsZeroBars() {
        val now = instant(2026, 10, 4, 15, 7)
        val bars = usageBars(emptyList(), UsageSpan.MINUTE, now, jerusalem)
        assertEquals(60, bars.size)
        assertTrue(bars.all { it.totalBytes == 0L })
        assertEquals(instant(2026, 10, 4, 15, 7), bars.last().startMillis)
        assertEquals(instant(2026, 10, 4, 14, 8), bars.first().startMillis)
    }

    @Test
    fun longerSpansKeepAReadableBarCount() {
        val now = instant(2026, 10, 4, 15, 0)
        assertEquals(24, usageBars(emptyList(), UsageSpan.HOUR, now, jerusalem).size)
        assertEquals(14, usageBars(emptyList(), UsageSpan.DAY, now, jerusalem).size)
    }

    @Test
    fun bytesInTheSameBucketAreSummed() {
        val first = instant(2026, 10, 4, 15, 7)
        val second = first + 20_000L
        var store = recordAll(UsageSnapshot(), wifiBytes = 100, cellularBytes = 40, atMillis = first, jerusalem)
        store = recordAll(store, wifiBytes = 25, cellularBytes = 5, atMillis = second, jerusalem)
        val bars = usageBars(store.minutes, UsageSpan.MINUTE, second, jerusalem)
        assertEquals(125L, bars.last().wifiBytes)
        assertEquals(45L, bars.last().cellularBytes)
    }

    @Test
    fun minuteSamplesRollIntoTheSameHourAndDay() {
        val at15 = instant(2026, 10, 4, 15, 7)
        val at16 = instant(2026, 10, 4, 15, 40)
        var store = recordAll(UsageSnapshot(), 100, 10, at15, jerusalem)
        store = recordAll(store, 50, 5, at16, jerusalem)
        val hours = usageBars(store.hours, UsageSpan.HOUR, at16, jerusalem)
        val days = usageBars(store.days, UsageSpan.DAY, at16, jerusalem)
        assertEquals(150L, hours.last().wifiBytes)
        assertEquals(15L, hours.last().cellularBytes)
        assertEquals(165L, days.last().totalBytes)
        val minutes = usageBars(store.minutes, UsageSpan.MINUTE, at16, jerusalem)
        assertEquals(50L, minutes.last().wifiBytes)
        assertEquals(100L, minutes[minutes.lastIndex - 33].wifiBytes)
    }

    @Test
    fun bucketsOutsideTheWindowAreDropped() {
        val old = instant(2026, 8, 1, 12, 0)
        val now = instant(2026, 10, 4, 12, 0)
        val store = recordAll(
            recordAll(UsageSnapshot(), 80, 20, old, jerusalem),
            5,
            1,
            now,
            jerusalem,
        )
        assertTrue(store.days.none { it.startMillis == alignUsageStart(old, UsageSpan.DAY, jerusalem) })
        assertEquals(6L, usageBars(store.days, UsageSpan.DAY, now, jerusalem).last().totalBytes)
    }

    @Test
    fun dayBarsAlignToLocalMidnight() {
        val now = instant(2026, 10, 4, 23, 50)
        val bars = usageBars(emptyList(), UsageSpan.DAY, now, jerusalem)
        assertEquals(instant(2026, 10, 4, 0, 0), bars.last().startMillis)
        assertEquals(instant(2026, 9, 21, 0, 0), bars.first().startMillis)
    }

    @Test
    fun hourStepsStayOrderedAcrossDaylightSaving() {
        val zone = ZoneId.of("America/New_York")
        val spring = ZonedDateTime.of(2026, 3, 8, 5, 15, 0, 0, zone).toInstant().toEpochMilli()
        val bars = usageBars(emptyList(), UsageSpan.HOUR, spring, zone)
        val starts = bars.map { it.startMillis }
        assertEquals(starts.distinct().sorted(), starts)
        assertTrue(starts.zipWithNext().all { (left, right) -> right > left })
    }

    @Test
    fun mobileCountersSplitWifiFromCellular() {
        val delta = splitUsage(
            previousTotal = BytePair(1_000, 100),
            previousMobile = BytePair(200, 50),
            currentTotal = BytePair(1_800, 160),
            currentMobile = BytePair(500, 50),
            transport = Transport.WIFI,
        )
        assertEquals(300L, delta.cellularBytes)
        assertEquals(560L, delta.wifiBytes)
    }

    @Test
    fun cellularCannotExceedTheBytesThatActuallyMoved() {
        val delta = splitUsage(
            previousTotal = BytePair(0, 0),
            previousMobile = BytePair(0, 0),
            currentTotal = BytePair(100, 0),
            currentMobile = BytePair(500, 0),
            transport = Transport.WIFI,
        )
        assertEquals(100L, delta.cellularBytes)
        assertEquals(0L, delta.wifiBytes)
    }

    @Test
    fun counterResetDoesNotInventUsage() {
        val delta = splitUsage(
            previousTotal = BytePair(5_000, 5_000),
            previousMobile = BytePair(100, 100),
            currentTotal = BytePair(10, 10),
            currentMobile = BytePair(100, 100),
            transport = Transport.CELLULAR,
        )
        assertEquals(0L, delta.wifiBytes)
        assertEquals(0L, delta.cellularBytes)
    }

    @Test
    fun missingMobileCountersFollowTheActiveTransport() {
        val wifi = splitUsage(BytePair(0, 0), null, BytePair(80, 20), null, Transport.WIFI)
        val cellular = splitUsage(BytePair(0, 0), null, BytePair(80, 20), null, Transport.CELLULAR)
        val ethernet = splitUsage(BytePair(0, 0), null, BytePair(40, 0), null, Transport.ETHERNET)
        assertEquals(UsageDelta(100, 0), wifi)
        assertEquals(UsageDelta(0, 100), cellular)
        assertEquals(UsageDelta(40, 0), ethernet)
    }

    @Test
    fun samplerSkipsTheBaselineAndClearsItWhenCountersDisappear() {
        val sampler = UsageSampler()
        assertNull(sampler.onSnapshot(BytePair(10, 10), BytePair(1, 1), Transport.WIFI))
        val delta = sampler.onSnapshot(BytePair(40, 15), BytePair(5, 1), Transport.WIFI)
        assertEquals(UsageDelta(31, 4), delta)
        assertNull(sampler.onSnapshot(null, null, Transport.WIFI))
        assertNull(sampler.onSnapshot(BytePair(40, 15), BytePair(5, 1), Transport.WIFI))
    }

    @Test
    fun volumeLabelUsesAmountUnits() {
        assertEquals("0", volumeLabel(0L))
        assertEquals("999 B", volumeLabel(999L))
        assertEquals("1.50 KB", volumeLabel(1_500L))
        assertEquals("850 KB", volumeLabel(850_000L))
        assertEquals("1.50 MB", volumeLabel(1_500_000L))
        assertEquals("5.00 GB", volumeLabel(5_000_000_000L))
        assertEquals("0", volumeLabel(Double.NaN))
    }

    @Test
    fun timeLabelMatchesTheBarWidth() {
        val at = instant(2026, 10, 4, 15, 7)
        assertEquals("15:07", usageTimeLabel(at, UsageSpan.MINUTE, jerusalem))
        assertEquals("15:00", usageTimeLabel(alignUsageStart(at, UsageSpan.HOUR, jerusalem), UsageSpan.HOUR, jerusalem))
        assertEquals("04.10", usageTimeLabel(alignUsageStart(at, UsageSpan.DAY, jerusalem), UsageSpan.DAY, jerusalem))
        assertNull(usageAxisLabel(at, UsageSpan.MINUTE, jerusalem))
        assertEquals("15:00", usageAxisLabel(instant(2026, 10, 4, 15, 0), UsageSpan.MINUTE, jerusalem))
        assertEquals("15", usageAxisLabel(alignUsageStart(at, UsageSpan.HOUR, jerusalem), UsageSpan.HOUR, jerusalem))
    }

    @Test
    fun stackedHeightsStayInsideThePlot() {
        val heights = usageSegmentHeights(
            wifiBytes = 1,
            cellularBytes = 1,
            axisMax = 1_000f,
            plotPx = 100f,
            minPx = 80f,
            gapPx = 10f,
        )
        assertEquals(100f, heights.wifi + heights.cellular + 10f, 0.01f)
        assertTrue(heights.wifi > 0f && heights.cellular > 0f)
    }

    @Test
    fun storeRoundTripDropsGarbage() {
        val now = instant(2026, 10, 4, 15, 7)
        val store = recordAll(UsageSnapshot(), 12, 3, now, jerusalem)
        val text = encodeUsageStore(store) + "nope\nm,not,a,number\n"
        val restored = decodeUsageStore(text)
        assertEquals(store.minutes, restored.minutes)
        assertEquals(store.hours, restored.hours)
        assertEquals(store.days, restored.days)
        assertTrue(text.startsWith("v1\n"))
    }

    private fun instant(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, jerusalem).toInstant().toEpochMilli()
    }
}
