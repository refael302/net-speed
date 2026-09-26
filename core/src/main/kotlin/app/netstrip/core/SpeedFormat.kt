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

private fun iconToken(bytesPerSec: Double): String {
    return when {
        bytesPerSec < 1_000.0 -> "0K"
        bytesPerSec < 1_000_000.0 -> {
            val kilo = (bytesPerSec / 1_000.0).roundToInt()
            if (kilo >= 1_000) "1.0M" else "${kilo}K"
        }
        bytesPerSec < 10_000_000.0 -> String.format(Locale.US, "%.1fM", bytesPerSec / 1_000_000.0)
        bytesPerSec < 1_000_000_000.0 -> {
            val mega = (bytesPerSec / 1_000_000.0).roundToInt()
            if (mega >= 1_000) "1.0G" else "${mega}M"
        }
        else -> String.format(Locale.US, "%.1fG", bytesPerSec / 1_000_000_000.0)
    }
}
