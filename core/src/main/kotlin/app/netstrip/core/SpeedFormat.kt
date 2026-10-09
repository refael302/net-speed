package app.netstrip.core

import java.util.Locale
import kotlin.math.roundToInt

data class RateLabel(
    val value: String,
    val unit: String,
    val bits: String,
    val icon: String,
)

fun axisLabel(bytesPerSec: Double): String {
    val bytes = bytesPerSec.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
    if (bytes < 1.0) return "0"
    val label = labelFor(bytes)
    return "${label.value} ${label.unit}"
}

fun labelFor(bytesPerSec: Double): RateLabel {
    val bytes = bytesPerSec.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
    val (scaled, unit) = when {
        bytes < 1_000.0 -> bytes to "B/s"
        bytes < 1_000_000.0 -> bytes / 1_000.0 to "KB/s"
        bytes < 1_000_000_000.0 -> bytes / 1_000_000.0 to "MB/s"
        else -> bytes / 1_000_000_000.0 to "GB/s"
    }
    val decimals = when {
        unit == "B/s" -> 0
        scaled < 10.0 -> 2
        scaled < 100.0 -> 1
        else -> 0
    }
    return RateLabel(
        value = String.format(Locale.US, "%.${decimals}f", scaled),
        unit = unit,
        bits = formatBits(bytes * 8.0),
        icon = iconToken(bytes),
    )
}

private fun formatBits(bitsPerSec: Double): String {
    val bits = bitsPerSec.coerceAtLeast(0.0)
    return when {
        bits < 1_000.0 -> "${bits.roundToInt()} bps"
        bits < 1_000_000.0 -> String.format(Locale.US, "%.1f Kbps", bits / 1_000.0)
        bits < 1_000_000_000.0 -> String.format(Locale.US, "%.1f Mbps", bits / 1_000_000.0)
        else -> String.format(Locale.US, "%.2f Gbps", bits / 1_000_000_000.0)
    }
}

/**
 * Below one unit the icon keeps the zero before the decimal ("0.4K", "0.2M").
 * From one unit up it is a whole number ("8K", "2M", "12M").
 */
private fun iconToken(bytesPerSec: Double): String {
    if (bytesPerSec < 100.0) return "0K"
    val kilo = bytesPerSec / 1_000.0
    if (kilo < 100.0) {
        if (kilo < 1.0) return tenths(kilo, "K")
        val whole = kilo.roundToInt()
        if (whole < 100) return "${whole}K"
    }
    val mega = bytesPerSec / 1_000_000.0
    if (mega < 100.0) {
        if (mega < 1.0) return tenths(mega, "M")
        val whole = mega.roundToInt()
        if (whole < 100) return "${whole}M"
    }
    val giga = bytesPerSec / 1_000_000_000.0
    if (giga < 1.0) return tenths(giga, "G")
    val whole = giga.roundToInt()
    return if (whole >= 100) "99G" else "${whole}G"
}

private fun tenths(scaled: Double, unit: String): String {
    val tenths = (scaled * 10.0).roundToInt()
    if (tenths >= 10) return "1$unit"
    if (tenths <= 0) return "0$unit"
    return "0.$tenths$unit"
}
