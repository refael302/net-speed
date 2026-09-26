package app.netstrip.statusbar

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.netstrip.statusbar.ui.Dashboard
import app.netstrip.statusbar.ui.DashboardViewModel
import app.netstrip.statusbar.ui.NetStripTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bar = Color.parseColor("#FF101418")
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(bar),
            navigationBarStyle = SystemBarStyle.dark(bar),
        )
        setContent {
            NetStripTheme {
                Dashboard(viewModel<DashboardViewModel>())
            }
        }
    }
}
