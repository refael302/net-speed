package app.netstrip.core

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

const val SAMPLE_PERIOD_MILLIS = 2_000L

/** Enough history for the hour graph. */
const val HISTORY_MILLIS = 60 * 60_000L

enum class GraphSpan(
    val windowMillis: Long,
    val bucketMillis: Long,
) {
    SECONDS(30_000L, SAMPLE_PERIOD_MILLIS),
    MINUTE(60_000L, SAMPLE_PERIOD_MILLIS),
    HALF_HOUR(30 * 60_000L, 30_000L),
    HOUR(60 * 60_000L, 60_000L),
}

data class ChartSample(
    val down: Float,
    val up: Float,
    val atMillis: Long,
)

data class ChartPoint(
    val down: Float,
    val up: Float,
)

/**
 * Fills [GraphSpan.windowMillis] with fixed buckets ending at [nowMillis].
 * Missing time stays at zero. A bucket keeps the peak so a short burst remains visible.
 */
fun chartSeries(samples: List<ChartSample>, span: GraphSpan, nowMillis: Long): List<ChartPoint> {
    val bucketCount = (span.windowMillis / span.bucketMillis).toInt().coerceAtLeast(1)
    val start = nowMillis - span.windowMillis
    val points = MutableList(bucketCount) { ChartPoint(0f, 0f) }
    for (sample in samples) {
        if (sample.atMillis < start || sample.atMillis > nowMillis) continue
        val index = ((sample.atMillis - start) / span.bucketMillis).toInt().coerceIn(0, bucketCount - 1)
        val current = points[index]
        points[index] = ChartPoint(
            down = maxOf(current.down, sample.down),
            up = maxOf(current.up, sample.up),
        )
    }
    return points
}

/** Rounds a peak bytes/sec up to a 1-2-5 grid so the line is not glued to the top. */
fun niceAxisMax(peak: Float): Float {
    if (!peak.isFinite() || peak < 1f) return 1_000f
    val padded = peak * 1.08f
    val exponent = floor(log10(padded.toDouble()))
    val base = 10.0.pow(exponent)
    val fraction = padded / base
    val nice = when {
        fraction <= 1.0 -> 1.0
        fraction <= 2.0 -> 2.0
        fraction <= 5.0 -> 5.0
        else -> 10.0
    }
    return (nice * base).toFloat()
}
