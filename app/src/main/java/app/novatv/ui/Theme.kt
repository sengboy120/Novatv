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

/** Elapsed / remaining style clock: 4:07 or 1:24:35. */
fun fmtPos(ms: Long): String {
    val t = ms.coerceAtLeast(0L) / 1000L
    val h = t / 3600L
    val m = (t % 3600L) / 60L
    val sec = t % 60L
    return if (h > 0L) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun fmtDate(ms: Long): String = SimpleDateFormat("EEE d/M", Locale.getDefault()).format(Date(ms))
