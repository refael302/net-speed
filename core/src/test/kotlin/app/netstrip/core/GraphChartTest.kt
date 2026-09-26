package app.netstrip.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphChartTest {
    @Test
    fun emptyWindowIsAllZeros() {
        val series = chartSeries(emptyList(), GraphSpan.SECONDS, nowMillis = 30_000L)
        assertEquals(15, series.size)
        assertTrue(series.all { it.down == 0f && it.up == 0f })
    }

    @Test
    fun newestSampleSitsOnTheRight() {
        val now = 60_000L
        val series = chartSeries(
            listOf(ChartSample(down = 10f, up = 4f, atMillis = now - 500L)),
            GraphSpan.SECONDS,
            now,
        )
        assertEquals(0f, series.first().down, 0.001f)
        assertEquals(10f, series.last().down, 0.001f)
        assertEquals(4f, series.last().up, 0.001f)
    }

    @Test
    fun bucketKeepsThePeak() {
        val series = chartSeries(
            listOf(
                ChartSample(5f, 1f, 100L),
                ChartSample(9f, 2f, 1_500L),
            ),
            GraphSpan.SECONDS,
            nowMillis = 30_000L,
        )
        assertEquals(9f, series.first().down, 0.001f)
        assertEquals(2f, series.first().up, 0.001f)
    }

    @Test
    fun longerRangesUseAReadableBucketCount() {
        assertEquals(30, chartSeries(emptyList(), GraphSpan.MINUTE, 60_000L).size)
        assertEquals(60, chartSeries(emptyList(), GraphSpan.HALF_HOUR, 1_800_000L).size)
        assertEquals(60, chartSeries(emptyList(), GraphSpan.HOUR, 3_600_000L).size)
    }

    @Test
    fun samplesOutsideTheWindowAreDropped() {
        val now = 3_600_000L
        val series = chartSeries(
            listOf(ChartSample(80f, 80f, now - GraphSpan.HOUR.windowMillis - 1L)),
            GraphSpan.HOUR,
            now,
        )
        assertTrue(series.all { it.down == 0f })
    }

    @Test
    fun niceAxisMaxLeavesHeadroom() {
        assertEquals(1_000f, niceAxisMax(0f), 0.01f)
        assertEquals(2_000f, niceAxisMax(1_200f), 0.01f)
        assertEquals(5_000_000f, niceAxisMax(3_200_000f), 0.01f)
        assertTrue(niceAxisMax(2_000f) > 2_000f)
    }
}
