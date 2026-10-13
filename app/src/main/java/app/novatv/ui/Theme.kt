package app.novatv.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** NovaTV palette: deep space background with cyan / magenta / violet neon accents. */
object Nova {
    val bg = Color(0xFF05060F)
    val panel = Color(0xFF0E1226)
    val panelHi = Color(0xFF191F42)
    val cyan = Color(0xFF2AF5FF)
    val magenta = Color(0xFFFF5CD6)
    val violet = Color(0xFF8A7BFF)
    val text = Color(0xFFEAF0FF)
    val textDim = Color(0xFF8E9AC4)
    val danger = Color(0xFFFF5C7A)

    val neon: Brush = Brush.horizontalGradient(listOf(cyan, magenta))
    val neonVertical: Brush = Brush.verticalGradient(listOf(cyan, violet, magenta))
}

fun fmtTime(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))

fun fmtDuration(ms: Long): String {
    val m = ms / 60_000L
    return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
}
