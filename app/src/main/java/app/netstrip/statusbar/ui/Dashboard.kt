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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.netstrip.core.RateLabel
import app.netstrip.core.SpeedReading
import app.netstrip.core.Transport
import app.netstrip.core.labelFor
import app.netstrip.statusbar.R
import app.netstrip.statusbar.data.IconMode

@Composable
fun Dashboard(viewModel: DashboardViewModel) {
    val reading by viewModel.reading.collectAsStateWithLifecycle()
    val spark by viewModel.spark.collectAsStateWithLifecycle()
    val statusBarOn by viewModel.statusBarOn.collectAsStateWithLifecycle()
    val iconMode by viewModel.iconMode.collectAsStateWithLifecycle()
    val startOnBoot by viewModel.startOnBoot.collectAsStateWithLifecycle()
    val notificationsAllowed by viewModel.notificationsAllowed.collectAsStateWithLifecycle()
    val batteryUnrestricted by viewModel.batteryUnrestricted.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var helpOpen by remember { mutableStateOf(false) }

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
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Header(onHelp = { helpOpen = true })
        Spacer(Modifier.height(18.dp))
        when (val current = reading) {
            SpeedReading.Unsupported -> UnsupportedCard()
            SpeedReading.Waiting -> SpeedStack(live = null, spark = spark)
            is SpeedReading.Live -> SpeedStack(live = current, spark = spark)
        }
        Spacer(Modifier.height(18.dp))
        if (!notificationsAllowed) {
            NoticeCard(
                title = stringResource(R.string.notifications_needed),
                body = null,
                action = stringResource(
                    if (Build.VERSION.SDK_INT >= 33) R.string.allow_notifications
                    else R.string.open_notification_settings,
                ),
                onAction = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.openNotificationSettings()
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
        }
        StatusCard(
            statusBarOn = statusBarOn,
            iconMode = iconMode,
            startOnBoot = startOnBoot,
            onToggle = { want ->
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
            onMode = viewModel::setIconMode,
            onBoot = viewModel::setStartOnBoot,
        )
        if (statusBarOn && !batteryUnrestricted) {
            Spacer(Modifier.height(12.dp))
            NoticeCard(
                title = stringResource(R.string.battery_title),
                body = stringResource(R.string.battery_body),
                action = stringResource(R.string.battery_action),
                onAction = viewModel::openBatterySettings,
            )
        }
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            Text(message.orEmpty(), color = NetDanger, fontSize = 14.sp)
        }
        Spacer(Modifier.height(22.dp))
        Text(
            stringResource(R.string.privacy),
            color = NetMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(12.dp))
    }

    if (helpOpen) {
        AlertDialog(
            onDismissRequest = { helpOpen = false },
            confirmButton = {
                TextButton(onClick = { helpOpen = false }) {
                    Text(stringResource(R.string.close), color = NetDown)
                }
            },
            title = { Text(stringResource(R.string.help_title)) },
            text = {
                Text(
                    stringResource(R.string.help_body),
                    lineHeight = 22.sp,
                )
            },
            containerColor = NetCard,
            titleContentColor = NetText,
            textContentColor = NetMuted,
        )
    }
}

@Composable
private fun Header(onHelp: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.app_name),
                color = NetText,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.subtitle),
                color = NetMuted,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
        TextButton(onClick = onHelp) {
            Text(stringResource(R.string.help), color = NetDown)
        }
    }
}

@Composable
private fun SpeedStack(live: SpeedReading.Live?, spark: List<Float>) {
    val transport = live?.transport
    NetworkPill(transport)
    Spacer(Modifier.height(8.dp))
    Text(
        stringResource(R.string.throughput_hint),
        color = NetMuted,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    Spacer(Modifier.height(16.dp))
    SpeedReadout(
        title = stringResource(R.string.download),
        label = live?.let { labelFor(it.downBytesPerSec) },
        accent = NetDown,
    )
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.last_minute), color = NetMuted, fontSize = 12.sp)
    Spacer(Modifier.height(6.dp))
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Sparkline(
            samples = spark,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        )
    }
    Spacer(Modifier.height(16.dp))
    SpeedReadout(
        title = stringResource(R.string.upload),
        label = live?.let { labelFor(it.upBytesPerSec) },
        accent = NetUp,
    )
}

@Composable
private fun NetworkPill(transport: Transport?) {
    val label = when (transport) {
        null -> stringResource(R.string.measuring)
        Transport.WIFI -> stringResource(R.string.transport_wifi)
        Transport.CELLULAR -> stringResource(R.string.transport_cellular)
        Transport.ETHERNET -> stringResource(R.string.transport_ethernet)
        Transport.OTHER -> stringResource(R.string.transport_other)
        Transport.NONE -> stringResource(R.string.transport_none)
    }
    val connected = transport != null && transport != Transport.NONE
    Row(
        modifier = Modifier
            .background(NetCard, RoundedCornerShape(100))
            .border(1.dp, NetLine, RoundedCornerShape(100))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(if (connected) NetDown else NetMuted, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, color = NetText, fontSize = 14.sp)
    }
}

@Composable
private fun SpeedReadout(title: String, label: RateLabel?, accent: androidx.compose.ui.graphics.Color) {
    val value = label?.value ?: stringResource(R.string.dash)
    val spoken = "$title $value ${label?.unit.orEmpty()} ${label?.bits.orEmpty()}"
    Column(
        modifier = Modifier.semantics { contentDescription = spoken },
    ) {
        Text(title, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(
            text = value,
            color = NetText,
            fontSize = 52.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = (-1).sp,
        )
        Text(label?.unit.orEmpty(), color = NetText.copy(alpha = 0.82f), fontSize = 16.sp)
        Text(label?.bits.orEmpty(), color = NetMuted, fontSize = 13.sp)
    }
}

@Composable
private fun Sparkline(samples: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val baseline = size.height - 1.dp.toPx()
        drawLine(
            color = NetLine,
            start = Offset(0f, baseline),
            end = Offset(size.width, baseline),
            strokeWidth = 1.dp.toPx(),
        )
        if (samples.size < 2) return@Canvas
        val max = samples.max().coerceAtLeast(1f)
        val step = size.width / (samples.size - 1).toFloat()
        val line = Path()
        val fill = Path()
        samples.forEachIndexed { index, sample ->
            val x = index * step
            val y = size.height - (sample / max) * size.height * 0.86f - size.height * 0.08f
            if (index == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        drawPath(fill, NetDown.copy(alpha = 0.16f))
        drawPath(
            line,
            NetDown,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun StatusCard(
    statusBarOn: Boolean,
    iconMode: IconMode,
    startOnBoot: Boolean,
    onToggle: (Boolean) -> Unit,
    onMode: (IconMode) -> Unit,
    onBoot: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NetCard, RoundedCornerShape(18.dp))
            .border(1.dp, NetLine, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.show_in_status_bar),
                    color = NetText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Switch(
                checked = statusBarOn,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = NetDown,
                    checkedThumbColor = NetBg,
                    uncheckedTrackColor = NetLine,
                    uncheckedThumbColor = NetMuted,
                    uncheckedBorderColor = NetLine,
                ),
            )
        }
        Text(
            stringResource(R.string.status_hint),
            color = NetMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.icon_content), color = NetMuted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        ModePicker(selected = iconMode, onSelect = onMode)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.boot),
                modifier = Modifier.weight(1f),
                color = NetText,
                fontSize = 15.sp,
            )
            Switch(
                checked = startOnBoot,
                onCheckedChange = onBoot,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = NetDown,
                    checkedThumbColor = NetBg,
                    uncheckedTrackColor = NetLine,
                    uncheckedThumbColor = NetMuted,
                    uncheckedBorderColor = NetLine,
                ),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.boot_hint),
            color = NetMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun ModePicker(selected: IconMode, onSelect: (IconMode) -> Unit) {
    val labels = listOf(
        IconMode.DOWNLOAD to stringResource(R.string.icon_download),
        IconMode.UPLOAD to stringResource(R.string.icon_upload),
        IconMode.BOTH to stringResource(R.string.icon_both),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NetBg, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEach { (mode, label) ->
            val on = mode == selected
            Text(
                text = label,
                color = if (on) NetDown else NetMuted,
                fontSize = 14.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (on) NetDown.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(9.dp),
                    )
                    .clickable { onSelect(mode) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun NoticeCard(title: String, body: String?, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NetCard, RoundedCornerShape(18.dp))
            .border(1.dp, NetLine, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Text(title, color = NetText, fontSize = 15.sp, fontWeight = FontWeight.Medium, lineHeight = 21.sp)
        if (body != null) {
            Spacer(Modifier.height(6.dp))
            Text(body, color = NetMuted, fontSize = 13.sp, lineHeight = 18.sp)
        }
        TextButton(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) {
            Text(action, color = NetDown)
        }
    }
}

@Composable
private fun UnsupportedCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NetCard, RoundedCornerShape(18.dp))
            .border(1.dp, NetLine, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Text(
            stringResource(R.string.unsupported),
            color = NetDanger,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )
    }
}
