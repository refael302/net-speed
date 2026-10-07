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
    fun minuteScreenIsTheClockHour() {
        val now = instant(2026, 10, 4, 15, 7)
        val bars = usageBars(emptyList(), UsageSpan.MINUTE, now, jerusalem)
        assertEquals(60, bars.size)
        assertTrue(bars.all { it.totalBytes == 0L })
        assertEquals(instant(2026, 10, 4, 15, 0), bars.first().startMillis)
        assertEquals(instant(2026, 10, 4, 15, 59), bars.last().startMillis)
    }

    @Test
    fun hourScreenStartsAtMidnight() {
        val now = instant(2026, 10, 4, 15, 0)
        val bars = usageBars(emptyList(), UsageSpan.HOUR, now, jerusalem)
        assertEquals(24, bars.size)
        assertEquals(instant(2026, 10, 4, 0, 0), bars.first().startMillis)
        assertEquals(instant(2026, 10, 4, 23, 0), bars.last().startMillis)
    }

    @Test
    fun dayScreenIsTheCalendarMonth() {
        val now = instant(2026, 10, 4, 23, 50)
        val bars = usageBars(emptyList(), UsageSpan.DAY, now, jerusalem)
        assertEquals(31, bars.size)
        assertEquals(instant(2026, 10, 1, 0, 0), bars.first().startMillis)
        assertEquals(instant(2026, 10, 31, 0, 0), bars.last().startMillis)
    }

    @Test
    fun arrowsMoveBetweenKeptScreens() {
        val now = instant(2026, 10, 4, 15, 7)
        val previousHour = usageBars(emptyList(), UsageSpan.MINUTE, now, jerusalem, pageBack = 1)
        assertEquals(instant(2026, 10, 4, 14, 0), previousHour.first().startMillis)
        assertEquals(instant(2026, 10, 4, 14, 59), previousHour.last().startMillis)

        val yesterday = usageBars(emptyList(), UsageSpan.HOUR, now, jerusalem, pageBack = 1)
        assertEquals(instant(2026, 10, 3, 0, 0), yesterday.first().startMillis)

        val september = usageBars(emptyList(), UsageSpan.DAY, now, jerusalem, pageBack = 1)
        assertEquals(30, september.size)
        assertEquals(instant(2026, 9, 1, 0, 0), september.first().startMillis)
        assertEquals(instant(2026, 9, 30, 0, 0), september.last().startMillis)

        val oldestMinutes = usageBars(emptyList(), UsageSpan.MINUTE, now, jerusalem, pageBack = 50)
        assertEquals(usagePageStart(UsageSpan.MINUTE, now, 23, jerusalem), oldestMinutes.first().startMillis)
        assertEquals(24, usagePageCount(UsageSpan.MINUTE))
        assertEquals(7, usagePageCount(UsageSpan.HOUR))
        assertEquals(2, usagePageCount(UsageSpan.DAY))
    }

    @Test
    fun pinnedScreenFollowsTheClockUntilItFallsOut() {
        val now = instant(2026, 10, 4, 15, 7)
        val pinned = usagePageStart(UsageSpan.MINUTE, now, 1, jerusalem)
        assertEquals(1, usagePageBack(UsageSpan.MINUTE, now, pinned, jerusalem))
        val nextHour = instant(2026, 10, 4, 16, 0)
        assertEquals(2, usagePageBack(UsageSpan.MINUTE, nextHour, pinned, jerusalem))
        val tooOld = instant(2026, 10, 5, 16, 0)
        assertNull(usagePageBack(UsageSpan.MINUTE, tooOld, pinned, jerusalem))
    }

    @Test
    fun bytesInTheSameBucketAreSummed() {
        val first = instant(2026, 10, 4, 15, 7)
        val second = first + 20_000L
        var store = recordAll(UsageSnapshot(), wifiBytes = 100, cellularBytes = 40, atMillis = first, jerusalem)
        store = recordAll(store, wifiBytes = 25, cellularBytes = 5, atMillis = second, jerusalem)
        val bars = usageBars(store.minutes, UsageSpan.MINUTE, second, jerusalem)
        assertEquals(125L, bars[7].wifiBytes)
        assertEquals(45L, bars[7].cellularBytes)
    }

    @Test
    fun minuteSamplesRollIntoTheSameHourAndDay() {
        val at15 = instant(2026, 10, 4, 15, 7)
        val at16 = instant(2026, 10, 4, 15, 40)
        var store = recordAll(UsageSnapshot(), 100, 10, at15, jerusalem)
        store = recordAll(store, 50, 5, at16, jerusalem)
        val hours = usageBars(store.hours, UsageSpan.HOUR, at16, jerusalem)
        val days = usageBars(store.days, UsageSpan.DAY, at16, jerusalem)
        assertEquals(150L, hours[15].wifiBytes)
        assertEquals(15L, hours[15].cellularBytes)
        assertEquals(165L, days[3].totalBytes)
        val minutes = usageBars(store.minutes, UsageSpan.MINUTE, at16, jerusalem)
        assertEquals(50L, minutes[40].wifiBytes)
        assertEquals(100L, minutes[7].wifiBytes)
    }

    @Test
    fun retentionKeepsADayOfMinutesAWeekOfHoursAndAMonthOfDays() {
        val now = instant(2026, 10, 4, 15, 7)
        val keptMinute = instant(2026, 10, 3, 16, 5)
        val droppedMinute = instant(2026, 10, 3, 15, 50)
        val keptHour = instant(2026, 9, 28, 3, 0)
        val droppedHour = instant(2026, 9, 27, 23, 0)
        val keptDay = instant(2026, 9, 15, 12, 0)
        val droppedDay = instant(2026, 8, 1, 12, 0)
        val futureDay = instant(2026, 11, 1, 0, 0)
        var store = recordAll(UsageSnapshot(), 16, 16, droppedDay, jerusalem)
        store = recordAll(store, 80, 20, keptDay, jerusalem)
        store = recordAll(store, 8, 8, droppedHour, jerusalem)
        store = recordAll(store, 4, 4, keptHour, jerusalem)
        store = recordAll(store, 2, 2, droppedMinute, jerusalem)
        store = recordAll(store, 1, 1, keptMinute, jerusalem)
        store = recordAll(
            store.copy(days = store.days + UsageBucket(futureDay, 9, 9)),
            5,
            1,
            now,
            jerusalem,
        )

        assertEquals(1L, store.minutes.single { it.startMillis == keptMinute }.wifiBytes)
        assertTrue(store.minutes.none { it.startMillis == droppedMinute })
        assertTrue(store.hours.any { it.startMillis == alignUsageStart(keptHour, UsageSpan.HOUR, jerusalem) })
        assertTrue(store.hours.none { it.startMillis == alignUsageStart(droppedHour, UsageSpan.HOUR, jerusalem) })
        assertTrue(store.days.any { it.startMillis == alignUsageStart(keptDay, UsageSpan.DAY, jerusalem) })
        assertTrue(store.days.none { it.startMillis == alignUsageStart(droppedDay, UsageSpan.DAY, jerusalem) })
        assertTrue(store.days.none { it.startMillis == futureDay })

        val september = usageBars(store.days, UsageSpan.DAY, now, jerusalem, pageBack = 1)
        assertEquals(100L, september[14].totalBytes)
        assertEquals(6L, usageBars(store.days, UsageSpan.DAY, now, jerusalem)[3].totalBytes)
    }

    @Test
    fun hourStepsStayOrderedAcrossDaylightSaving() {
        val zone = ZoneId.of("America/New_York")
        val spring = ZonedDateTime.of(2026, 3, 8, 5, 15, 0, 0, zone).toInstant().toEpochMilli()
        val springBars = usageBars(emptyList(), UsageSpan.HOUR, spring, zone)
        val springStarts = springBars.map { it.startMillis }
        assertEquals(23, springBars.size)
        assertEquals(springStarts.distinct().sorted(), springStarts)
        assertTrue(springStarts.zipWithNext().all { (left, right) -> right > left })

        val fall = ZonedDateTime.of(2026, 11, 1, 5, 15, 0, 0, zone).toInstant().toEpochMilli()
        val fallBars = usageBars(emptyList(), UsageSpan.HOUR, fall, zone)
        val fallStarts = fallBars.map { it.startMillis }
        assertEquals(25, fallBars.size)
        assertEquals(fallStarts.distinct().sorted(), fallStarts)
        assertTrue(fallStarts.zipWithNext().all { (left, right) -> right > left })
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
        assertNull(usageAxisLabel(instant(2026, 10, 4, 16, 0), UsageSpan.HOUR, jerusalem))
        assertEquals("1", usageAxisLabel(instant(2026, 10, 1, 0, 0), UsageSpan.DAY, jerusalem))
        assertEquals("5", usageAxisLabel(instant(2026, 10, 5, 0, 0), UsageSpan.DAY, jerusalem))
        assertNull(usageAxisLabel(instant(2026, 10, 4, 0, 0), UsageSpan.DAY, jerusalem))
        assertEquals("04.10 15:00", usagePageLabel(instant(2026, 10, 4, 15, 0), UsageSpan.MINUTE, jerusalem))
        assertEquals("04.10", usagePageLabel(instant(2026, 10, 4, 0, 0), UsageSpan.HOUR, jerusalem))
        assertEquals("10.2026", usagePageLabel(instant(2026, 10, 1, 0, 0), UsageSpan.DAY, jerusalem))
    }

    @Test
    fun screenTotalsAddTheBarsOnThatScreen() {
        val at15 = instant(2026, 10, 4, 15, 7)
        val at16 = instant(2026, 10, 4, 16, 10)
        var store = recordAll(UsageSnapshot(), 100, 10, at15, jerusalem)
        store = recordAll(store, 50, 5, at16, jerusalem)
        assertEquals(
            UsageTotals(50, 5),
            usageScreenTotals(usageBars(store.minutes, UsageSpan.MINUTE, at16, jerusalem)),
        )
        assertEquals(
            UsageTotals(100, 10),
            usageScreenTotals(usageBars(store.minutes, UsageSpan.MINUTE, at16, jerusalem, pageBack = 1)),
        )
        assertEquals(
            UsageTotals(150, 15),
            usageScreenTotals(usageBars(store.hours, UsageSpan.HOUR, at16, jerusalem)),
        )
        assertEquals(
            UsageTotals(150, 15),
            usageScreenTotals(usageBars(store.days, UsageSpan.DAY, at16, jerusalem)),
        )
        assertEquals(UsageTotals(0, 0), usageScreenTotals(emptyList()))
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
