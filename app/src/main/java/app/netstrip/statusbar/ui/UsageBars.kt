package app.netstrip.statusbar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.netstrip.core.UsageBucket
import app.netstrip.core.UsageSnapshot
import app.netstrip.core.UsageSpan
import app.netstrip.core.bucketsFor
import app.netstrip.core.niceAxisMax
import app.netstrip.core.usageAxisLabel
import app.netstrip.core.usageBars
import app.netstrip.core.usagePageBack
import app.netstrip.core.usagePageCount
import app.netstrip.core.usagePageLabel
import app.netstrip.core.usagePageStart
import app.netstrip.core.usageSegmentHeights
import app.netstrip.core.usageTimeLabel
import app.netstrip.core.volumeLabel
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max

private val TooltipLane = 64.dp
private val PlotHeight = 148.dp
private val AxisLane = 22.dp
private val ChartHeight = TooltipLane + PlotHeight + AxisLane
private val ChipColor = Color(0xFF24313C)

@Composable
fun UsageBars(
    snapshot: UsageSnapshot,
    span: UsageSpan,
    tapLabel: String,
    selectedPattern: String,
    previousLabel: String,
    nextLabel: String,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val now = snapshot.atMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
    var pinnedStart by remember(span) { mutableStateOf<Long?>(null) }
    val resolvedBack = pinnedStart?.let { usagePageBack(span, now, it, zone) }
    LaunchedEffect(pinnedStart, resolvedBack) {
        if (pinnedStart != null && resolvedBack == null) pinnedStart = null
    }
    val pageBack = resolvedBack ?: 0
    val bars = usageBars(snapshot.bucketsFor(span), span, now, zone, pageBack)
    val pageCount = usagePageCount(span)
    val pageStart = bars.firstOrNull()?.startMillis ?: usagePageStart(span, now, pageBack, zone)
    val pageLabel = usagePageLabel(pageStart, span, zone)
    val nowState by rememberUpdatedState(now)
    var selectedStart by remember(span) { mutableStateOf<Long?>(null) }
    val selected = bars.firstOrNull { it.startMillis == selectedStart }
    val axisMax = niceAxisMax(bars.maxOfOrNull { it.totalBytes.toFloat() } ?: 0f)
    val description = buildString {
        append(pageLabel)
        append(". ")
        if (selected == null) {
            append(tapLabel)
        } else {
            append(
                selectedPattern.format(
                    usageTimeLabel(selected.startMillis, span, zone),
                    volumeLabel(selected.totalBytes),
                    volumeLabel(selected.wifiBytes),
                    volumeLabel(selected.cellularBytes),
                ),
            )
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier.semantics { contentDescription = description },
        ) {
            UsagePager(
                label = pageLabel,
                canGoOlder = pageBack < pageCount - 1,
                canGoNewer = pageBack > 0,
                previousLabel = previousLabel,
                nextLabel = nextLabel,
                onOlder = {
                    val next = pageBack + 1
                    if (next < pageCount) {
                        pinnedStart = usagePageStart(span, nowState, next, zone)
                    }
                },
                onNewer = {
                    val next = pageBack - 1
                    pinnedStart = if (next <= 0) null else usagePageStart(span, nowState, next, zone)
                },
            )
            Row {
                UsageAxis(axisMax)
                UsagePlot(
                    bars = bars,
                    span = span,
                    zone = zone,
                    axisMax = axisMax,
                    tapLabel = tapLabel,
                    selectedStart = selectedStart,
                    onSelect = { selectedStart = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun UsagePager(
    label: String,
    canGoOlder: Boolean,
    canGoNewer: Boolean,
    previousLabel: String,
    nextLabel: String,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageArrow("‹", canGoOlder, previousLabel, onOlder)
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = NetText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        PageArrow("›", canGoNewer, nextLabel, onNewer)
    }
}

@Composable
private fun PageArrow(
    symbol: String,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            color = if (enabled) NetText else NetMuted.copy(alpha = 0.35f),
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun UsageAxis(axisMax: Float) {
    val style = TextStyle(color = NetMuted, fontSize = 10.sp)
    Column(Modifier.width(62.dp)) {
        Spacer(Modifier.height(TooltipLane))
        Box(Modifier.height(PlotHeight).fillMaxWidth()) {
            Text(
                volumeLabel(axisMax.toDouble()),
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp),
                style = style,
                maxLines = 1,
            )
            Text(
                volumeLabel(axisMax / 2.0),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
                style = style,
                maxLines = 1,
            )
            Text(
                volumeLabel(0.0),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp),
                style = style,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(AxisLane))
    }
}

@Composable
private fun UsagePlot(
    bars: List<UsageBucket>,
    span: UsageSpan,
    zone: ZoneId,
    axisMax: Float,
    tapLabel: String,
    selectedStart: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val barsState by rememberUpdatedState(bars)
    val selectBar by rememberUpdatedState(onSelect)

    val axisStyle = TextStyle(color = NetMuted, fontSize = 10.sp)
    val timeStyle = TextStyle(color = NetText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val valueStyle = TextStyle(color = NetText, fontSize = 12.sp)
    val hintStyle = TextStyle(color = NetMuted, fontSize = 12.sp)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(ChartHeight)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId = down.id
                    val start = down.position
                    var pastSlop = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) {
                            if (!pastSlop) {
                                val current = barsState
                                val count = current.size
                                val slot = if (count == 0) 0f else size.width.toFloat() / count.toFloat()
                                val index = if (slot <= 0f) -1 else (start.x / slot).toInt()
                                if (index in current.indices) selectBar(current[index].startMillis)
                            }
                            break
                        }
                        val delta = change.position - start
                        if (!pastSlop && delta.getDistance() > viewConfiguration.touchSlop) {
                            pastSlop = true
                        }
                        if (pastSlop) {
                            if (abs(delta.x) >= abs(delta.y)) {
                                change.consume()
                            } else {
                                break
                            }
                        }
                    }
                }
            },
    ) {
        if (bars.isEmpty()) return@Canvas
        val plotTop = TooltipLane.toPx()
        val plotPx = PlotHeight.toPx()
        val plotBottom = plotTop + plotPx
        val slot = size.width / bars.size
        val barWidth = (slot * 0.5f).coerceIn(1.5.dp.toPx(), 14.dp.toPx())

        listOf(0f, 0.5f, 1f).forEach { fraction ->
            val y = plotBottom - fraction * plotPx
            drawLine(
                color = NetLine,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        bars.forEachIndexed { index, bucket ->
            drawUsageBar(
                bucket = bucket,
                index = index,
                slot = slot,
                barWidth = barWidth,
                plotTop = plotTop,
                plotBottom = plotBottom,
                plotPx = plotPx,
                axisMax = axisMax,
                selected = bucket.startMillis == selectedStart,
                caption = usageAxisLabel(bucket.startMillis, span, zone),
                measurer = measurer,
                axisStyle = axisStyle,
            )
        }

        val selectedIndex = bars.indexOfFirst { it.startMillis == selectedStart }
        if (selectedIndex < 0) {
            drawTapHint(measurer, tapLabel, hintStyle)
        } else {
            drawUsageChip(
                measurer = measurer,
                bucket = bars[selectedIndex],
                span = span,
                zone = zone,
                centerX = selectedIndex * slot + slot / 2f,
                plotTop = plotTop,
                timeStyle = timeStyle,
                valueStyle = valueStyle,
            )
        }
    }
}

private fun DrawScope.drawUsageBar(
    bucket: UsageBucket,
    index: Int,
    slot: Float,
    barWidth: Float,
    plotTop: Float,
    plotBottom: Float,
    plotPx: Float,
    axisMax: Float,
    selected: Boolean,
    caption: String?,
    measurer: TextMeasurer,
    axisStyle: TextStyle,
) {
    val left = index * slot
    val center = left + slot / 2f
    val inset = (slot * 0.08f).coerceAtMost(3.dp.toPx())
    val highlightWidth = (slot - inset * 2f).coerceAtLeast(1f)
    if (selected) {
        drawRoundRect(
            color = Color.White.copy(alpha = 0.07f),
            topLeft = Offset(left + inset, plotTop + 2.dp.toPx()),
            size = Size(highlightWidth, plotPx - 4.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
    }
    val heights = usageSegmentHeights(
        wifiBytes = bucket.wifiBytes,
        cellularBytes = bucket.cellularBytes,
        axisMax = axisMax,
        plotPx = plotPx,
        minPx = 3.dp.toPx(),
        gapPx = 2.dp.toPx(),
    )
    val barLeft = center - barWidth / 2f
    drawRoundRect(
        color = Color.White.copy(alpha = 0.05f),
        topLeft = Offset(barLeft, plotTop),
        size = Size(barWidth, plotPx),
        cornerRadius = CornerRadius(2.dp.toPx()),
    )
    if (heights.wifi > 0f || heights.cellular > 0f) {
        if (heights.wifi > 0f) {
            drawRoundRect(
                color = NetWifi,
                topLeft = Offset(barLeft, plotBottom - heights.wifi),
                size = Size(barWidth, heights.wifi),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
        if (heights.cellular > 0f) {
            val top = plotBottom - heights.wifi - (if (heights.wifi > 0f) 2.dp.toPx() else 0f) - heights.cellular
            drawRoundRect(
                color = NetCell,
                topLeft = Offset(barLeft, top),
                size = Size(barWidth, heights.cellular),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
    if (selected) {
        drawRoundRect(
            color = NetText.copy(alpha = 0.85f),
            topLeft = Offset(left + inset, plotTop + 2.dp.toPx()),
            size = Size(highlightWidth, plotPx - 4.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx()),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
    if (caption != null) {
        val layout = measurer.measure(caption, axisStyle, softWrap = false, overflow = TextOverflow.Visible)
        val captionLeft = (center - layout.size.width / 2f)
            .coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
        drawText(
            layout,
            color = if (selected) NetText else NetMuted,
            topLeft = Offset(captionLeft, plotBottom + 4.dp.toPx()),
        )
    }
}

private fun DrawScope.drawTapHint(
    measurer: TextMeasurer,
    tapLabel: String,
    hintStyle: TextStyle,
) {
    val hint = measurer.measure(tapLabel, hintStyle, softWrap = false, overflow = TextOverflow.Visible)
    val hintLeft = ((size.width - hint.size.width) / 2f).coerceAtLeast(0f)
    drawText(
        hint,
        color = NetMuted,
        topLeft = Offset(hintLeft, (TooltipLane.toPx() - hint.size.height) / 2f),
    )
}

private fun DrawScope.drawUsageChip(
    measurer: TextMeasurer,
    bucket: UsageBucket,
    span: UsageSpan,
    zone: ZoneId,
    centerX: Float,
    plotTop: Float,
    timeStyle: TextStyle,
    valueStyle: TextStyle,
) {
    val time = measurer.measure(
        usageTimeLabel(bucket.startMillis, span, zone),
        timeStyle,
        softWrap = false,
        overflow = TextOverflow.Visible,
    )
    val total = measurer.measure(
        volumeLabel(bucket.totalBytes),
        valueStyle,
        softWrap = false,
        overflow = TextOverflow.Visible,
    )
    val wifi = measurer.measure(
        volumeLabel(bucket.wifiBytes),
        valueStyle,
        softWrap = false,
        overflow = TextOverflow.Visible,
    )
    val cellular = measurer.measure(
        volumeLabel(bucket.cellularBytes),
        valueStyle,
        softWrap = false,
        overflow = TextOverflow.Visible,
    )
    val gap = 8.dp.toPx()
    val padX = 8.dp.toPx()
    val padY = 5.dp.toPx()
    val lineGap = 2.dp.toPx()
    val dot = 6.dp.toPx()
    val dotGap = 4.dp.toPx()
    val line1Width = time.size.width + gap + total.size.width
    val line2Width = dot + dotGap + wifi.size.width + gap + dot + dotGap + cellular.size.width
    val line1Height = max(time.size.height, total.size.height).toFloat()
    val line2Height = max(wifi.size.height, cellular.size.height).toFloat()
    val blockWidth = max(line1Width, line2Width) + padX * 2f
    val blockHeight = padY * 2f + line1Height + lineGap + line2Height
    val left = (centerX - blockWidth / 2f).coerceIn(0f, (size.width - blockWidth).coerceAtLeast(0f))
    val top = (plotTop - blockHeight - 4.dp.toPx()).coerceAtLeast(2.dp.toPx())
    drawRoundRect(
        color = ChipColor,
        topLeft = Offset(left, top),
        size = Size(blockWidth, blockHeight),
        cornerRadius = CornerRadius(8.dp.toPx()),
    )
    drawRoundRect(
        color = NetLine,
        topLeft = Offset(left, top),
        size = Size(blockWidth, blockHeight),
        cornerRadius = CornerRadius(8.dp.toPx()),
        style = Stroke(width = 1.dp.toPx()),
    )
    val line1Left = left + (blockWidth - line1Width) / 2f
    val line1Top = top + padY
    drawText(time, color = NetText, topLeft = Offset(line1Left, line1Top))
    drawText(total, color = NetText, topLeft = Offset(line1Left + time.size.width + gap, line1Top))
    val line2Left = left + (blockWidth - line2Width) / 2f
    val line2Top = line1Top + line1Height + lineGap
    val dotRadius = dot / 2f
    val dotY = line2Top + line2Height / 2f
    drawCircle(NetWifi, dotRadius, Offset(line2Left + dotRadius, dotY))
    val wifiLeft = line2Left + dot + dotGap
    drawText(wifi, color = NetWifi, topLeft = Offset(wifiLeft, line2Top))
    val cellDotLeft = wifiLeft + wifi.size.width + gap
    drawCircle(NetCell, dotRadius, Offset(cellDotLeft + dotRadius, dotY))
    drawText(
        cellular,
        color = NetCell,
        topLeft = Offset(cellDotLeft + dot + dotGap, line2Top),
    )
}
