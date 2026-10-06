package app.netstrip.core

import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** How wide each bar is. One screen is a clock hour, a local day, or a calendar month. */
enum class UsageSpan {
    MINUTE,
    HOUR,
    DAY,
}

data class UsageBucket(
    val startMillis: Long,
    val wifiBytes: Long,
    val cellularBytes: Long,
) {
    val totalBytes: Long get() = wifiBytes + cellularBytes
}

data class UsageSnapshot(
    val minutes: List<UsageBucket> = emptyList(),
    val hours: List<UsageBucket> = emptyList(),
    val days: List<UsageBucket> = emptyList(),
    val atMillis: Long = 0L,
)

data class UsageDelta(
    val wifiBytes: Long,
    val cellularBytes: Long,
)

fun UsageSnapshot.bucketsFor(span: UsageSpan): List<UsageBucket> = when (span) {
    UsageSpan.MINUTE -> minutes
    UsageSpan.HOUR -> hours
    UsageSpan.DAY -> days
}

/** Start of the minute, hour, or local day that contains [atMillis]. */
fun alignUsageStart(atMillis: Long, span: UsageSpan, zone: ZoneId): Long {
    val time = Instant.ofEpochMilli(atMillis).atZone(zone)
    val aligned = when (span) {
        UsageSpan.MINUTE -> time.withSecond(0).withNano(0)
        UsageSpan.HOUR -> time.withMinute(0).withSecond(0).withNano(0)
        UsageSpan.DAY -> time.toLocalDate().atStartOfDay(zone)
    }
    return aligned.toInstant().toEpochMilli()
}

/** How many screens are kept: 24 hours of minutes, 7 days of hours, or 2 calendar months of days. */
fun usagePageCount(span: UsageSpan): Int = when (span) {
    UsageSpan.MINUTE -> 24
    UsageSpan.HOUR -> 7
    UsageSpan.DAY -> 2
}

/**
 * Start of the screen [pageBack] steps before the one that contains [nowMillis].
 * A minute screen starts at the clock hour, an hour screen at local midnight, and a day screen on the 1st.
 */
fun usagePageStart(span: UsageSpan, nowMillis: Long, pageBack: Int, zone: ZoneId): Long {
    val back = pageBack.coerceIn(0, usagePageCount(span) - 1)
    val time = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val start = when (span) {
        UsageSpan.MINUTE -> time.withMinute(0).withSecond(0).withNano(0).minusHours(back.toLong())
        UsageSpan.HOUR -> time.toLocalDate().atStartOfDay(zone).minusDays(back.toLong())
        UsageSpan.DAY -> time.toLocalDate().withDayOfMonth(1).atStartOfDay(zone).minusMonths(back.toLong())
    }
    return start.toInstant().toEpochMilli()
}

/** Which kept screen begins at [pageStart], or null once that screen has scrolled out of retention. */
fun usagePageBack(span: UsageSpan, nowMillis: Long, pageStart: Long, zone: ZoneId): Int? {
    for (back in 0 until usagePageCount(span)) {
        if (usagePageStart(span, nowMillis, back, zone) == pageStart) return back
    }
    return null
}

/**
 * One bar per bucket on a single screen, oldest first.
 * [pageBack] 0 is the current hour, day, or month. Missing buckets stay at zero.
 * Bytes in the same bucket are summed.
 */
fun usageBars(
    stored: List<UsageBucket>,
    span: UsageSpan,
    nowMillis: Long,
    zone: ZoneId,
    pageBack: Int = 0,
): List<UsageBucket> {
    val totals = HashMap<Long, UsageBucket>()
    for (bucket in stored) {
        val existing = totals[bucket.startMillis]
        totals[bucket.startMillis] = if (existing == null) {
            bucket
        } else {
            existing.copy(
                wifiBytes = existing.wifiBytes + bucket.wifiBytes,
                cellularBytes = existing.cellularBytes + bucket.cellularBytes,
            )
        }
    }
    return pageBucketStarts(span, nowMillis, pageBack, zone).map { start ->
        totals[start] ?: UsageBucket(start, 0L, 0L)
    }
}

fun recordAll(
    snapshot: UsageSnapshot,
    wifiBytes: Long,
    cellularBytes: Long,
    atMillis: Long,
    zone: ZoneId,
): UsageSnapshot {
    return UsageSnapshot(
        minutes = recordUsage(snapshot.minutes, wifiBytes, cellularBytes, atMillis, UsageSpan.MINUTE, zone),
        hours = recordUsage(snapshot.hours, wifiBytes, cellularBytes, atMillis, UsageSpan.HOUR, zone),
        days = recordUsage(snapshot.days, wifiBytes, cellularBytes, atMillis, UsageSpan.DAY, zone),
        atMillis = atMillis,
    )
}

/**
 * Cellular comes from the mobile counters. Everything else that moved is counted as Wi-Fi.
 * When the phone does not report mobile counters, the active transport decides the split.
 */
fun splitUsage(
    previousTotal: BytePair,
    previousMobile: BytePair?,
    currentTotal: BytePair,
    currentMobile: BytePair?,
    transport: Transport,
): UsageDelta {
    val total = bytesMoved(previousTotal, currentTotal)
    val cellular = when {
        previousMobile != null && currentMobile != null ->
            bytesMoved(previousMobile, currentMobile).coerceAtMost(total)
        transport == Transport.CELLULAR -> total
        else -> 0L
    }
    return UsageDelta(
        wifiBytes = total - cellular,
        cellularBytes = cellular,
    )
}

/**
 * The first snapshot only arms the baseline. A missing total clears it.
 */
class UsageSampler {
    private var previousTotal: BytePair? = null
    private var previousMobile: BytePair? = null

    fun onSnapshot(
        total: BytePair?,
        mobile: BytePair?,
        transport: Transport,
    ): UsageDelta? {
        if (total == null) {
            previousTotal = null
            previousMobile = null
            return null
        }
        val prevTotal = previousTotal
        val prevMobile = previousMobile
        previousTotal = total
        previousMobile = mobile
        if (prevTotal == null) return null
        return splitUsage(prevTotal, prevMobile, total, mobile, transport)
    }
}

fun volumeLabel(bytes: Double): String {
    val value = bytes.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
    if (value < 1.0) return "0"
    val (scaled, unit) = when {
        value < 1_000.0 -> value to "B"
        value < 1_000_000.0 -> value / 1_000.0 to "KB"
        value < 1_000_000_000.0 -> value / 1_000_000.0 to "MB"
        value < 1_000_000_000_000.0 -> value / 1_000_000_000.0 to "GB"
        else -> value / 1_000_000_000_000.0 to "TB"
    }
    val decimals = when {
        unit == "B" -> 0
        scaled < 10.0 -> 2
        scaled < 100.0 -> 1
        else -> 0
    }
    return String.format(Locale.US, "%.${decimals}f %s", scaled, unit)
}

fun volumeLabel(bytes: Long): String = volumeLabel(bytes.toDouble())

/** Clock time for a bar. A day bar shows the date, an hour bar the hour, a minute bar the minute. */
fun usageTimeLabel(startMillis: Long, span: UsageSpan, zone: ZoneId): String {
    val time = Instant.ofEpochMilli(startMillis).atZone(zone)
    return when (span) {
        UsageSpan.MINUTE -> String.format(Locale.US, "%02d:%02d", time.hour, time.minute)
        UsageSpan.HOUR -> String.format(Locale.US, "%02d:00", time.hour)
        UsageSpan.DAY -> String.format(Locale.US, "%02d.%02d", time.dayOfMonth, time.monthValue)
    }
}

/** Sparse axis caption. Null means this bar stays unlabeled until it is tapped. */
fun usageAxisLabel(startMillis: Long, span: UsageSpan, zone: ZoneId): String? {
    val time = Instant.ofEpochMilli(startMillis).atZone(zone)
    return when (span) {
        UsageSpan.MINUTE ->
            if (time.minute % 10 == 0) String.format(Locale.US, "%02d:%02d", time.hour, time.minute) else null
        UsageSpan.HOUR ->
            if (time.hour % 3 == 0) String.format(Locale.US, "%02d", time.hour) else null
        UsageSpan.DAY ->
            if (time.dayOfMonth == 1 || time.dayOfMonth % 5 == 0) {
                String.format(Locale.US, "%d", time.dayOfMonth)
            } else {
                null
            }
    }
}

/** Caption between the screen arrows: the hour, the day, or the month on screen. */
fun usagePageLabel(startMillis: Long, span: UsageSpan, zone: ZoneId): String {
    val time = Instant.ofEpochMilli(startMillis).atZone(zone)
    return when (span) {
        UsageSpan.MINUTE ->
            String.format(Locale.US, "%02d.%02d %02d:00", time.dayOfMonth, time.monthValue, time.hour)
        UsageSpan.HOUR -> String.format(Locale.US, "%02d.%02d", time.dayOfMonth, time.monthValue)
        UsageSpan.DAY -> String.format(Locale.US, "%02d.%04d", time.monthValue, time.year)
    }
}

data class SegmentHeights(
    val wifi: Float,
    val cellular: Float,
)

/** Pixel heights for a stacked bar. A non-zero side stays visible, and the pair fits in [plotPx]. */
fun usageSegmentHeights(
    wifiBytes: Long,
    cellularBytes: Long,
    axisMax: Float,
    plotPx: Float,
    minPx: Float = 0f,
    gapPx: Float = 0f,
): SegmentHeights {
    if (axisMax <= 0f || plotPx <= 0f) return SegmentHeights(0f, 0f)
    var wifi = if (wifiBytes > 0L) plotPx * (wifiBytes.toFloat() / axisMax) else 0f
    var cellular = if (cellularBytes > 0L) plotPx * (cellularBytes.toFloat() / axisMax) else 0f
    if (wifiBytes > 0L) wifi = maxOf(wifi, minPx)
    if (cellularBytes > 0L) cellular = maxOf(cellular, minPx)
    val gap = if (wifi > 0f && cellular > 0f) gapPx else 0f
    val available = (plotPx - gap).coerceAtLeast(0f)
    val sum = wifi + cellular
    if (sum > available && sum > 0f) {
        val scale = available / sum
        wifi *= scale
        cellular *= scale
    }
    return SegmentHeights(wifi, cellular)
}

fun encodeUsageStore(snapshot: UsageSnapshot): String = buildString {
    append("v1\n")
    appendBuckets("m", snapshot.minutes)
    appendBuckets("h", snapshot.hours)
    appendBuckets("d", snapshot.days)
}

fun decodeUsageStore(text: String): UsageSnapshot {
    val minutes = mutableListOf<UsageBucket>()
    val hours = mutableListOf<UsageBucket>()
    val days = mutableListOf<UsageBucket>()
    for (line in text.lineSequence()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed == "v1") continue
        val parts = trimmed.split(',')
        if (parts.size != 4) continue
        val start = parts[1].toLongOrNull() ?: continue
        val wifi = parts[2].toLongOrNull() ?: continue
        val cellular = parts[3].toLongOrNull() ?: continue
        if (wifi < 0L || cellular < 0L) continue
        val bucket = UsageBucket(start, wifi, cellular)
        when (parts[0]) {
            "m" -> minutes.add(bucket)
            "h" -> hours.add(bucket)
            "d" -> days.add(bucket)
        }
    }
    return UsageSnapshot(minutes, hours, days)
}

private fun pageBucketStarts(span: UsageSpan, nowMillis: Long, pageBack: Int, zone: ZoneId): List<Long> {
    val start = usagePageStart(span, nowMillis, pageBack, zone)
    val startTime = Instant.ofEpochMilli(start).atZone(zone)
    return when (span) {
        UsageSpan.MINUTE -> List(60) { index ->
            startTime.plusMinutes(index.toLong()).toInstant().toEpochMilli()
        }
        UsageSpan.HOUR -> {
            val nextMidnight = startTime.toLocalDate().plusDays(1).atStartOfDay(zone)
            val starts = ArrayList<Long>(25)
            var cursor = startTime
            while (cursor.isBefore(nextMidnight)) {
                starts.add(cursor.toInstant().toEpochMilli())
                cursor = cursor.plusHours(1)
            }
            starts
        }
        UsageSpan.DAY -> {
            val date = startTime.toLocalDate()
            List(date.lengthOfMonth()) { index ->
                date.plusDays(index.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            }
        }
    }
}

/** Every bucket still inside the kept screens, including later slots on the current screen. */
private fun retainedStarts(span: UsageSpan, nowMillis: Long, zone: ZoneId): Set<Long> {
    return buildSet {
        for (pageBack in 0 until usagePageCount(span)) {
            addAll(pageBucketStarts(span, nowMillis, pageBack, zone))
        }
    }
}

private fun recordUsage(
    stored: List<UsageBucket>,
    wifiBytes: Long,
    cellularBytes: Long,
    atMillis: Long,
    span: UsageSpan,
    zone: ZoneId,
): List<UsageBucket> {
    val allowed = retainedStarts(span, atMillis, zone)
    val merged = LinkedHashMap<Long, UsageBucket>()
    for (bucket in stored) {
        if (bucket.startMillis !in allowed) continue
        val existing = merged[bucket.startMillis]
        merged[bucket.startMillis] = if (existing == null) {
            bucket
        } else {
            existing.copy(
                wifiBytes = existing.wifiBytes + bucket.wifiBytes,
                cellularBytes = existing.cellularBytes + bucket.cellularBytes,
            )
        }
    }
    if (wifiBytes > 0L || cellularBytes > 0L) {
        val start = alignUsageStart(atMillis, span, zone)
        if (start in allowed) {
            val existing = merged[start]
            merged[start] = if (existing == null) {
                UsageBucket(start, wifiBytes, cellularBytes)
            } else {
                existing.copy(
                    wifiBytes = existing.wifiBytes + wifiBytes,
                    cellularBytes = existing.cellularBytes + cellularBytes,
                )
            }
        }
    }
    return merged.values.toList()
}

private fun bytesMoved(previous: BytePair, current: BytePair): Long {
    val rx = (current.rx - previous.rx).coerceAtLeast(0L)
    val tx = (current.tx - previous.tx).coerceAtLeast(0L)
    return rx + tx
}

private fun StringBuilder.appendBuckets(kind: String, buckets: List<UsageBucket>) {
    for (bucket in buckets.sortedBy { it.startMillis }) {
        append(kind)
        append(',')
        append(bucket.startMillis)
        append(',')
        append(bucket.wifiBytes)
        append(',')
        append(bucket.cellularBytes)
        append('\n')
    }
}
