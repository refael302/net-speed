package app.netstrip.statusbar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import app.netstrip.core.usageSegmentHeights
import app.netstrip.core.usageTimeLabel
import app.netstrip.core.volumeLabel
import java.time.ZoneId
import kotlin.math.max

private val TooltipLane = 64.dp
private val PlotHeight = 148.dp
private val AxisLane = 22.dp
private val SlotWidth = 46.dp
private val ChartHeight = TooltipLane + PlotHeight + AxisLane
private val ChipColor = Color(0xFF24313C)

@Composable
fun UsageBars(
    snapshot: UsageSnapshot,
    span: UsageSpan,
    tapLabel: String,
    selectedPattern: String,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val now = snapshot.atMillis.takeIf { it > 0L } ?: System.currentTimeMillis()
    val bars = usageBars(snapshot.bucketsFor(span), span, now, zone)
    var selectedStart by remember(span) { mutableStateOf<Long?>(null) }
    val selected = bars.firstOrNull { it.startMillis == selectedStart }
    val axisMax = niceAxisMax(bars.maxOfOrNull { it.totalBytes.toFloat() } ?: 0f)
    val description = if (selected == null) {
        tapLabel
    } else {
        selectedPattern.format(
            usageTimeLabel(selected.startMillis, span, zone),
            volumeLabel(selected.totalBytes),
            volumeLabel(selected.wifiBytes),
            volumeLabel(selected.cellularBytes),
        )
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier
                .semantics { contentDescription = description }
                .background(NetCard, RoundedCornerShape(18.dp))
                .border(1.dp, NetLine, RoundedCornerShape(18.dp))
                .padding(start = 8.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        ) {
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
    val scroll = rememberScrollState()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val slotPx = with(density) { SlotWidth.toPx() }
    val barsState by rememberUpdatedState(bars)
    var viewportWidth by remember { mutableIntStateOf(0) }
    val scrollX = scroll.value

    LaunchedEffect(span) {
        withFrameNanos { }
        withFrameNanos { }
        scroll.scrollTo(scroll.maxValue)
    }

    val axisStyle = TextStyle(color = NetMuted, fontSize = 10.sp)
    val timeStyle = TextStyle(color = NetText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val valueStyle = TextStyle(color = NetText, fontSize = 12.sp)
    val hintStyle = TextStyle(color = NetMuted, fontSize = 12.sp)

    Box(
        modifier
            .onSizeChanged { viewportWidth = it.width }
            .horizontalScroll(scroll),
    ) {
        Canvas(
            Modifier
                .width(SlotWidth * max(bars.size, 1))
                .height(ChartHeight)
                .pointerInput(slotPx) {
                    detectTapGestures { offset ->
                        if (offset.y < TooltipLane.toPx()) return@detectTapGestures
                        val index = (offset.x / slotPx).toInt()
                        val current = barsState
                        if (index in current.indices) onSelect(current[index].startMillis)
                    }
                },
        ) {
            val plotTop = TooltipLane.toPx()
            val plotPx = PlotHeight.toPx()
            val plotBottom = plotTop + plotPx
            val slot = SlotWidth.toPx()
            val barWidth = 18.dp.toPx()

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
                drawTapHint(measurer, tapLabel, hintStyle, scrollX.toFloat(), viewportWidth)
            } else {
                drawUsageChip(
                    measurer = measurer,
                    bucket = bars[selectedIndex],
                    span = span,
                    zone = zone,
                    centerX = selectedIndex * slot + slot / 2f,
                    plotTop = plotTop,
                    scrollX = scrollX.toFloat(),
                    viewportWidth = viewportWidth,
                    timeStyle = timeStyle,
                    valueStyle = valueStyle,
                )
            }
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
    if (selected) {
        drawRoundRect(
            color = Color.White.copy(alpha = 0.07f),
            topLeft = Offset(left + 3.dp.toPx(), plotTop + 2.dp.toPx()),
            size = Size(slot - 6.dp.toPx(), plotPx - 4.dp.toPx()),
            cornerRadius = CornerRadius(8.dp.toPx()),
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
    if (heights.wifi <= 0f && heights.cellular <= 0f) {
        drawRoundRect(
            color = NetLine,
            topLeft = Offset(barLeft, plotBottom - 2.dp.toPx()),
            size = Size(barWidth, 2.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
    } else {
        if (heights.wifi > 0f) {
            drawRoundRect(
                color = NetWifi,
                topLeft = Offset(barLeft, plotBottom - heights.wifi),
                size = Size(barWidth, heights.wifi),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
        if (heights.cellular > 0f) {
            val top = plotBottom - heights.wifi - (if (heights.wifi > 0f) 2.dp.toPx() else 0f) - heights.cellular
            drawRoundRect(
                color = NetCell,
                topLeft = Offset(barLeft, top),
                size = Size(barWidth, heights.cellular),
                cornerRadius = CornerRadius(4.dp.toPx()),
            )
        }
    }
    if (selected) {
        drawRoundRect(
            color = NetText.copy(alpha = 0.85f),
            topLeft = Offset(left + 4.dp.toPx(), plotTop + 2.dp.toPx()),
            size = Size(slot - 8.dp.toPx(), plotPx - 4.dp.toPx()),
            cornerRadius = CornerRadius(8.dp.toPx()),
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
    scrollX: Float,
    viewportWidth: Int,
) {
    if (viewportWidth <= 0) return
    val hint = measurer.measure(tapLabel, hintStyle, softWrap = false, overflow = TextOverflow.Visible)
    val hintLeft = (scrollX + (viewportWidth - hint.size.width) / 2f)
        .coerceIn(0f, (size.width - hint.size.width).coerceAtLeast(0f))
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
    scrollX: Float,
    viewportWidth: Int,
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
    val minLeft = if (viewportWidth > 0) scrollX + 2.dp.toPx() else 0f
    val maxLeft = if (viewportWidth > 0) {
        (scrollX + viewportWidth - blockWidth - 2.dp.toPx()).coerceAtLeast(minLeft)
    } else {
        (size.width - blockWidth).coerceAtLeast(0f)
    }
    val left = (centerX - blockWidth / 2f).coerceIn(minLeft, maxLeft)
        .coerceIn(0f, (size.width - blockWidth).coerceAtLeast(0f))
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
