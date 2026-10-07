package app.netstrip.statusbar.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.netstrip.core.ChartSample
import app.netstrip.core.GraphSpan
import app.netstrip.core.SpeedReading
import app.netstrip.core.Transport
import app.netstrip.core.UsageSpan
import app.netstrip.core.axisLabel
import app.netstrip.core.chartSeries
import app.netstrip.core.labelFor
import app.netstrip.core.niceAxisMax
import app.netstrip.statusbar.BuildConfig
import app.netstrip.statusbar.R
import app.netstrip.statusbar.data.TrafficPoint
import app.netstrip.statusbar.update.UpdateState

@Composable
fun Dashboard(viewModel: DashboardViewModel) {
    val spark by viewModel.spark.collectAsStateWithLifecycle()
    val reading by viewModel.reading.collectAsStateWithLifecycle()
    val graphSpan by viewModel.graphSpan.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val usageSpan by viewModel.usageSpan.collectAsStateWithLifecycle()
    val statusBarOn by viewModel.statusBarOn.collectAsStateWithLifecycle()
    val notificationsAllowed by viewModel.notificationsAllowed.collectAsStateWithLifecycle()
    val batteryUnrestricted by viewModel.batteryUnrestricted.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.refreshSystemState()
        if (granted) viewModel.setStatusBar(true)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshSystemState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NetBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            stringResource(R.string.app_name),
            color = NetMuted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        val live = reading as? SpeedReading.Live
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            RateBlock(
                caption = stringResource(R.string.download),
                bytesPerSec = live?.downBytesPerSec,
                color = NetDown,
                modifier = Modifier.weight(1f),
            )
            RateBlock(
                caption = stringResource(R.string.upload),
                bytesPerSec = live?.upBytesPerSec,
                color = NetUp,
                modifier = Modifier.weight(1f),
            )
        }
        if (live != null) {
            Spacer(Modifier.height(4.dp))
            Text(TransportLabel(live.transport), color = NetMuted, fontSize = 13.sp)
        } else if (reading is SpeedReading.Unsupported) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.unsupported), color = NetDanger, fontSize = 13.sp)
        } else {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.measuring), color = NetMuted, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.show_in_status_bar),
                modifier = Modifier.weight(1f),
                color = NetText,
                fontSize = 16.sp,
            )
            Switch(
                checked = statusBarOn,
                onCheckedChange = { want ->
                    if (!want) {
                        viewModel.setStatusBar(false)
                    } else if (!notificationsAllowed && Build.VERSION.SDK_INT >= 33) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else if (!notificationsAllowed) {
                        viewModel.openNotificationSettings()
                    } else {
                        viewModel.setStatusBar(true)
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = NetDown,
                    checkedThumbColor = NetBg,
                    uncheckedTrackColor = NetLine,
                    uncheckedThumbColor = NetMuted,
                    uncheckedBorderColor = NetLine,
                ),
            )
        }
        if (!notificationsAllowed) {
            NoticeStrip(stringResource(R.string.allow_notifications)) {
                if (Build.VERSION.SDK_INT >= 33) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.openNotificationSettings()
                }
            }
        }
        if (!batteryUnrestricted) {
            NoticeStrip(stringResource(R.string.boot_battery), viewModel::allowUnrestrictedBattery)
        }
        if (message != null) {
            Spacer(Modifier.height(8.dp))
            Text(message.orEmpty(), color = NetDanger, fontSize = 14.sp)
        }

        Spacer(Modifier.height(20.dp))
        val nowLabel = stringResource(R.string.graph_now)
        val axisSeconds = stringResource(R.string.axis_seconds)
        val axisMinutes = stringResource(R.string.axis_minutes)
        ChartCard(stringResource(R.string.speed_title)) {
            SegmentedControl(
                labels = listOf(stringResource(R.string.range_minute), stringResource(R.string.range_hour)),
                selectedIndex = if (graphSpan == GraphSpan.MINUTE) 0 else 1,
                onSelect = { index ->
                    viewModel.selectSpan(if (index == 0) GraphSpan.MINUTE else GraphSpan.HOUR)
                },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(NetDown, stringResource(R.string.download))
                LegendDot(NetUp, stringResource(R.string.upload))
            }
            Spacer(Modifier.height(8.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                TrafficGraph(
                    points = spark,
                    span = graphSpan,
                    nowLabel = nowLabel,
                    axisSeconds = axisSeconds,
                    axisMinutes = axisMinutes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        ChartCard(stringResource(R.string.usage_title)) {
            Text(
                stringResource(R.string.usage_hint),
                color = NetMuted,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
            SegmentedControl(
                labels = listOf(
                    stringResource(R.string.range_minute),
                    stringResource(R.string.range_hour),
                    stringResource(R.string.range_day),
                ),
                selectedIndex = when (usageSpan) {
                    UsageSpan.MINUTE -> 0
                    UsageSpan.HOUR -> 1
                    UsageSpan.DAY -> 2
                },
                onSelect = { index ->
                    viewModel.selectUsageSpan(
                        when (index) {
                            0 -> UsageSpan.MINUTE
                            1 -> UsageSpan.HOUR
                            else -> UsageSpan.DAY
                        },
                    )
                },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(NetWifi, stringResource(R.string.transport_wifi))
                LegendDot(NetCell, stringResource(R.string.transport_cellular))
            }
            Spacer(Modifier.height(4.dp))
            UsageBars(
                snapshot = usage,
                span = usageSpan,
                tapLabel = stringResource(R.string.usage_tap),
                selectedPattern = stringResource(R.string.usage_selected),
                previousLabel = stringResource(R.string.usage_previous),
                nextLabel = stringResource(R.string.usage_next),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.installed_version, BuildConfig.VERSION_NAME),
                modifier = Modifier.weight(1f),
                color = NetMuted,
                fontSize = 13.sp,
            )
            TextButton(
                onClick = viewModel::checkForUpdate,
                enabled = updateState !is UpdateState.Checking && updateState !is UpdateState.Downloading,
            ) {
                Text(
                    when (updateState) {
                        UpdateState.Checking -> stringResource(R.string.checking_update)
                        UpdateState.Downloading -> stringResource(R.string.downloading_update)
                        else -> stringResource(R.string.check_update)
                    },
                    color = NetMuted,
                    fontSize = 13.sp,
                )
            }
        }
        UpdateResultDialog(updateState, viewModel)
    }
}

@Composable
private fun UpdateResultDialog(state: UpdateState, viewModel: DashboardViewModel) {
    when (state) {
        UpdateState.UpToDate -> NoticeDialog(
            body = stringResource(R.string.no_update),
            onDismiss = viewModel::dismissUpdate,
        )
        UpdateState.Failed -> NoticeDialog(
            body = stringResource(R.string.check_failed),
            onDismiss = viewModel::dismissUpdate,
        )
        is UpdateState.Available -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissUpdate,
                containerColor = NetCard,
                titleContentColor = NetText,
                textContentColor = NetText,
                title = { Text(stringResource(R.string.new_version, state.manifest.versionName)) },
                text = {
                    Text(state.manifest.notes.ifBlank { stringResource(R.string.no_notes) })
                },
                confirmButton = {
                    TextButton(
                        onClick = viewModel::installUpdate,
                        enabled = true,
                    ) {
                        Text(stringResource(R.string.update_app), color = NetDown)
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissUpdate) {
                        Text(stringResource(R.string.ok), color = NetMuted)
                    }
                },
            )
        }
        UpdateState.Downloading -> Unit
        else -> Unit
    }
}

@Composable
private fun NoticeDialog(body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NetCard,
        textContentColor = NetText,
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok), color = NetDown)
            }
        },
    )
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = NetMuted, fontSize = 13.sp)
    }
}

@Composable
private fun ChartCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NetCard, RoundedCornerShape(18.dp))
            .border(1.dp, NetLine, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(title, color = NetText, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun SegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NetBg)
            .padding(3.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) Color(0xFF314250) else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) NetText else NetMuted,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RateBlock(
    caption: String,
    bytesPerSec: Double?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val label = bytesPerSec?.let { labelFor(it) }
    Column(modifier) {
        Text(caption, color = NetMuted, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    label?.value ?: stringResource(R.string.dash),
                    color = if (label == null) NetMuted else color,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (label != null) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        label.unit,
                        modifier = Modifier.padding(bottom = 5.dp),
                        color = color,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeStrip(text: String, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NetDanger.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        color = NetDanger,
        fontSize = 13.sp,
    )
}

@Composable
private fun TransportLabel(transport: Transport): String {
    return when (transport) {
        Transport.WIFI -> stringResource(R.string.transport_wifi)
        Transport.CELLULAR -> stringResource(R.string.transport_cellular)
        Transport.ETHERNET -> stringResource(R.string.transport_ethernet)
        Transport.OTHER -> stringResource(R.string.transport_other)
        Transport.NONE -> stringResource(R.string.transport_none)
    }
}

@Composable
private fun TrafficGraph(
    points: List<TrafficPoint>,
    span: GraphSpan,
    nowLabel: String,
    axisSeconds: String,
    axisMinutes: String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = NetMuted, fontSize = 11.sp)
    val now = System.currentTimeMillis()
    val series = chartSeries(
        points.map { ChartSample(it.down, it.up, it.atMillis) },
        span,
        now,
    )
    val axisMax = niceAxisMax(series.maxOf { maxOf(it.down, it.up) })
    Canvas(modifier) {
        if (size.width < 1f || size.height < 1f) return@Canvas
        val tickFractions = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        val yLabels = tickFractions.map { axisLabel((axisMax * it).toDouble()) }
        val yGutter = yLabels.maxOf { measurer.measure(it, labelStyle).size.width }.toFloat() + 10.dp.toPx()
        val xGutter = 22.dp.toPx()
        val plotLeft = yGutter
        val plotTop = 12.dp.toPx()
        val plotRight = size.width - 4.dp.toPx()
        val plotBottom = size.height - xGutter
        val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)

        tickFractions.forEachIndexed { index, fraction ->
            val y = plotBottom - fraction * plotHeight
            drawLine(
                color = NetLine,
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = 1.dp.toPx(),
            )
            drawLine(
                color = NetMuted,
                start = Offset(plotLeft - 5.dp.toPx(), y),
                end = Offset(plotLeft, y),
                strokeWidth = 1.5.dp.toPx(),
            )
            val layout = measurer.measure(yLabels[index], labelStyle)
            drawText(
                layout,
                color = NetMuted,
                topLeft = Offset(
                    plotLeft - 8.dp.toPx() - layout.size.width,
                    y - layout.size.height / 2f,
                ),
            )
        }

        val xDivisions = 4
        for (mark in 0..xDivisions) {
            val fromRight = 1f - mark / xDivisions.toFloat()
            val x = plotLeft + (1f - fromRight) * plotWidth
            drawLine(
                color = NetMuted,
                start = Offset(x, plotBottom),
                end = Offset(x, plotBottom + 5.dp.toPx()),
                strokeWidth = 1.5.dp.toPx(),
            )
            if (mark % 2 != 0) continue
            val age = (span.windowMillis * fromRight).toLong()
            val label = when {
                age < 1_000L -> nowLabel
                age >= 60_000L -> axisMinutes.format(age / 60_000L)
                else -> axisSeconds.format(age / 1_000L)
            }
            val layout = measurer.measure(label, labelStyle)
            val maxLeft = (size.width - layout.size.width).coerceAtLeast(0f)
            val left = when (mark) {
                0 -> x
                xDivisions -> x - layout.size.width
                else -> x - layout.size.width / 2f
            }.coerceIn(0f, maxLeft)
            drawText(layout, color = NetMuted, topLeft = Offset(left, plotBottom + 7.dp.toPx()))
        }

        drawSeries(series.map { it.down }, axisMax, NetDown, plotLeft, plotTop, plotWidth, plotHeight)
        drawSeries(series.map { it.up }, axisMax, NetUp, plotLeft, plotTop, plotWidth, plotHeight)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(
    samples: List<Float>,
    max: Float,
    color: Color,
    plotLeft: Float,
    plotTop: Float,
    plotWidth: Float,
    plotHeight: Float,
) {
    if (samples.size < 2 || max <= 0f) return
    val step = plotWidth / (samples.size - 1).toFloat()
    val line = Path()
    val fill = Path()
    val baseline = plotTop + plotHeight
    val coords = samples.mapIndexed { index, sample ->
        val x = plotLeft + index * step
        val y = plotTop + plotHeight - (sample / max).coerceIn(0f, 1f) * plotHeight
        if (index == 0) {
            line.moveTo(x, y)
            fill.moveTo(x, baseline)
            fill.lineTo(x, y)
        } else {
            line.lineTo(x, y)
            fill.lineTo(x, y)
        }
        Offset(x, y)
    }
    fill.lineTo(coords.last().x, baseline)
    fill.close()
    drawPath(fill, color.copy(alpha = 0.18f))
    drawPath(
        line,
        color,
        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    if (samples.size <= 30) {
        coords.forEach { point ->
            drawCircle(color, radius = 2.5.dp.toPx(), center = point)
        }
    }
}
