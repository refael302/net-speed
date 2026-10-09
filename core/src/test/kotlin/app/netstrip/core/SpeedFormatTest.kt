package app.netstrip.core

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeedFormatTest {
    @Test
    fun axisLabelIncludesTheUnit() {
        assertEquals("0", axisLabel(0.0))
        assertEquals("1.50 KB/s", axisLabel(1_500.0))
    }

    @Test
    fun idleIsZeroBytes() {
        val label = labelFor(0.0)
        assertEquals("0", label.value)
        assertEquals("B/s", label.unit)
        assertEquals("0 bps", label.bits)
        assertEquals("0K", label.icon)
    }

    @Test
    fun kilobytesCarryABitRate() {
        val label = labelFor(1_500.0)
        assertEquals("1.50", label.value)
        assertEquals("KB/s", label.unit)
        assertEquals("12.0 Kbps", label.bits)
        assertEquals("2K", label.icon)
    }

    @Test
    fun hundredsOfKilobytesDropTheFraction() {
        val label = labelFor(850_000.0)
        assertEquals("850", label.value)
        assertEquals("KB/s", label.unit)
        assertEquals("6.8 Mbps", label.bits)
        assertEquals("0.9M", label.icon)
    }

    @Test
    fun megabytesKeepTwoDecimalsUnderTen() {
        val label = labelFor(1_500_000.0)
        assertEquals("1.50", label.value)
        assertEquals("MB/s", label.unit)
        assertEquals("12.0 Mbps", label.bits)
        assertEquals("1.5M", label.icon)
    }

    @Test
    fun fasterMegabytesUseAShorterIcon() {
        val label = labelFor(12_400_000.0)
        assertEquals("12.4", label.value)
        assertEquals("MB/s", label.unit)
        assertEquals("99.2 Mbps", label.bits)
        assertEquals("12M", label.icon)
    }

    @Test
    fun roundingIntoTheNextMegabytePromotesTheIcon() {
        assertEquals("1.0M", labelFor(999_500.0).icon)
    }

    @Test
    fun threeDigitIconsUseOneDecimalOfTheNextUnit() {
        assertEquals("0.3K", labelFor(320.0).icon)
        assertEquals("0.2M", labelFor(230_000.0).icon)
    }

    @Test
    fun nonFiniteInputIsTreatedAsIdle() {
        val label = labelFor(Double.NaN)
        assertEquals("0", label.value)
        assertEquals("B/s", label.unit)
    }
}
