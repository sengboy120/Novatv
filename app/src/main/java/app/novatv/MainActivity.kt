package app.novatv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.novatv.ui.AddServerScreen
import app.novatv.ui.Nova
import app.novatv.ui.PlayerScreen
import app.novatv.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Nova.cyan,
                    secondary = Nova.magenta,
                    background = Nova.bg,
                    surface = Nova.panel
                )
            ) {
                val screen by vm.screen.collectAsState()
                Surface(color = Nova.bg, contentColor = Nova.text) {
                    when (screen) {
                        Screen.PLAYER -> PlayerScreen(vm)
                        Screen.SETTINGS -> SettingsScreen(vm)
                        Screen.ADD_SERVER -> AddServerScreen(vm)
                    }
                }
            }
        }
    }
}
