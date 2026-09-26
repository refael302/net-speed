package app.netstrip.statusbar.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import app.netstrip.core.axisLabel
import app.netstrip.core.chartSeries
import app.netstrip.core.niceAxisMax
import app.netstrip.statusbar.BuildConfig
import app.netstrip.statusbar.R
import app.netstrip.statusbar.data.TrafficPoint
import app.netstrip.statusbar.update.UpdateState

@Composable
fun Dashboard(viewModel: DashboardViewModel) {
    val spark by viewModel.spark.collectAsStateWithLifecycle()
    val graphSpan by viewModel.graphSpan.collectAsStateWithLifecycle()
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.show_in_status_bar),
                modifier = Modifier.weight(1f),
                color = NetText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
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
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.openNotificationSettings()
                }
            }) {
                Text(stringResource(R.string.allow_notifications), color = NetDown)
            }
        }
        if (!batteryUnrestricted) {
            TextButton(onClick = viewModel::allowUnrestrictedBattery) {
                Text(stringResource(R.string.boot_battery), color = NetDown)
            }
        }
        if (message != null) {
            Text(message.orEmpty(), color = NetDanger, fontSize = 14.sp)
        }

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SpanButton(stringResource(R.string.range_minute), graphSpan == GraphSpan.MINUTE, Modifier.weight(1f)) {
                viewModel.selectSpan(GraphSpan.MINUTE)
            }
            SpanButton(stringResource(R.string.range_hour), graphSpan == GraphSpan.HOUR, Modifier.weight(1f)) {
                viewModel.selectSpan(GraphSpan.HOUR)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(NetDown, stringResource(R.string.download))
            LegendDot(NetUp, stringResource(R.string.upload))
        }
        Spacer(Modifier.height(10.dp))
        val nowLabel = stringResource(R.string.graph_now)
        val axisSeconds = stringResource(R.string.axis_seconds)
        val axisMinutes = stringResource(R.string.axis_minutes)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            TrafficGraph(
                points = spark,
                span = graphSpan,
                nowLabel = nowLabel,
                axisSeconds = axisSeconds,
                axisMinutes = axisMinutes,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.installed_version, BuildConfig.VERSION_NAME),
            color = NetMuted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = viewModel::checkForUpdate,
            enabled = updateState !is UpdateState.Checking && updateState !is UpdateState.Downloading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = NetCard,
                contentColor = NetText,
                disabledContainerColor = NetCard,
                disabledContentColor = NetMuted,
            ),
        ) {
            Text(
                when (updateState) {
                    UpdateState.Checking -> stringResource(R.string.checking_update)
                    UpdateState.Downloading -> stringResource(R.string.downloading_update)
                    else -> stringResource(R.string.check_update)
                },
            )
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
private fun SpanButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.border(1.dp, if (selected) NetDown else NetLine, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) NetDown else NetCard,
            contentColor = if (selected) Color(0xFF06281C) else NetMuted,
        ),
    ) {
        Text(label, fontSize = 13.sp, maxLines = 1)
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
    Canvas(
        modifier
            .background(NetCard, RoundedCornerShape(18.dp))
            .border(1.dp, NetLine, RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
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

        drawRect(
            color = NetLine,
            topLeft = Offset(plotLeft, plotTop),
            size = Size(plotWidth, plotHeight),
            style = Stroke(width = 1.dp.toPx()),
        )

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
    val coords = samples.mapIndexed { index, sample ->
        val x = plotLeft + index * step
        val y = plotTop + plotHeight - (sample / max).coerceIn(0f, 1f) * plotHeight
        if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
        Offset(x, y)
    }
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
