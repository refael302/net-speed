package app.netstrip.statusbar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NetBg = Color(0xFF101418)
val NetCard = Color(0xFF1B232B)
val NetLine = Color(0xFF2C3944)
val NetText = Color(0xFFF3F6F8)
val NetMuted = Color(0xFF93A1AD)
val NetDown = Color(0xFF3EE0A2)
val NetUp = Color(0xFFFFB020)
val NetWifi = Color(0xFF4C9AFF)
val NetCell = Color(0xFFE06AD8)
val NetDanger = Color(0xFFFF8B7B)

@Composable
fun NetStripTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = NetBg,
            surface = NetCard,
            primary = NetDown,
            onPrimary = Color(0xFF06281C),
            onBackground = NetText,
            onSurface = NetText,
        ),
        content = content,
    )
}
