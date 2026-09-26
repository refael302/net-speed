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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.netstrip.statusbar.R
import app.netstrip.statusbar.data.TrafficPoint
import app.netstrip.statusbar.update.UpdateState

@Composable
fun Dashboard(viewModel: DashboardViewModel) {
    val spark by viewModel.spark.collectAsStateWithLifecycle()
    val statusBarOn by viewModel.statusBarOn.collectAsStateWithLifecycle()
    val notificationsAllowed by viewModel.notificationsAllowed.collectAsStateWithLifecycle()
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
        if (message != null) {
            Text(message.orEmpty(), color = NetDanger, fontSize = 14.sp)
        }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(NetDown, stringResource(R.string.download))
            LegendDot(NetUp, stringResource(R.string.upload))
        }
        Spacer(Modifier.height(10.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            TrafficGraph(
                points = spark,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
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
        when (val state = updateState) {
            UpdateState.UpToDate -> {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.up_to_date), color = NetMuted, fontSize = 14.sp)
            }
            UpdateState.Failed -> {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.check_failed), color = NetDanger, fontSize = 14.sp)
            }
            is UpdateState.Available -> {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = viewModel::installUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NetDown, contentColor = Color(0xFF06281C)),
                ) {
                    Text(stringResource(R.string.update_app))
                }
            }
            else -> Unit
        }
    }
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
private fun TrafficGraph(points: List<TrafficPoint>, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .background(NetCard, RoundedCornerShape(18.dp))
            .border(1.dp, NetLine, RoundedCornerShape(18.dp))
            .padding(12.dp),
    ) {
        val baseline = size.height - 1.dp.toPx()
        drawLine(
            color = NetLine,
            start = Offset(0f, baseline),
            end = Offset(size.width, baseline),
            strokeWidth = 1.dp.toPx(),
        )
        if (points.size < 2) return@Canvas
        val max = points.maxOf { maxOf(it.down, it.up) }.coerceAtLeast(1f)
        drawSeries(points.map { it.down }, max, NetDown)
        drawSeries(points.map { it.up }, max, NetUp)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(
    samples: List<Float>,
    max: Float,
    color: Color,
) {
    val step = size.width / (samples.size - 1).toFloat()
    val line = Path()
    samples.forEachIndexed { index, sample ->
        val x = index * step
        val y = size.height - (sample / max) * size.height * 0.86f - size.height * 0.08f
        if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
    }
    drawPath(
        line,
        color,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
