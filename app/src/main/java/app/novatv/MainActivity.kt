package app.novatv

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import app.novatv.ui.AddServerScreen
import app.novatv.ui.Nova
import app.novatv.ui.PlayerScreen
import app.novatv.ui.ReorderScreen
import app.novatv.ui.SettingsScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    /** True while the Bendit TV logo is on screen at start-up. */
    private var splash by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // full screen on phones / tablets / car units (hide the status + navigation bars, swipe to peek)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        hideBars()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Nova.cyan,
                    secondary = Nova.magenta,
                    background = Nova.bg,
                    surface = Nova.panel
                )
            ) {
                LaunchedEffect(Unit) {
                    delay(2600L)
                    splash = false
                }
                val screen by vm.screen.collectAsState()
                Box(Modifier.fillMaxSize().displayCutoutPadding()) {
                    Surface(color = Nova.bg, contentColor = Nova.text) {
                        when (screen) {
                            Screen.PLAYER -> PlayerScreen(vm)
                            Screen.SETTINGS -> SettingsScreen(vm)
                            Screen.ADD_SERVER -> AddServerScreen(vm)
                            Screen.REORDER -> ReorderScreen(vm)
                        }
                    }
                    // start-up logo, drawn over the app while it loads (any key skips it)
                    AnimatedVisibility(
                        visible = splash,
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(600))
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0B0907))
                        ) {
                            Image(
                                painter = painterResource(R.drawable.logo),
                                contentDescription = "Bendit TV",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    private fun hideBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (splash) {
            // the first key press only dismisses the logo; it must not reach the player underneath
            if (event.action == KeyEvent.ACTION_DOWN) splash = false
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
