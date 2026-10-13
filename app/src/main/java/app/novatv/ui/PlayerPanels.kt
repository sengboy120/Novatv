package app.novatv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novatv.Channel
import app.novatv.MediaEntry
import app.novatv.Program
import app.novatv.Scheduler
import app.novatv.net.Cue
import app.novatv.net.SubParser
import kotlinx.coroutines.delay

// ------------------------------------------------------------------------------------------------
// Audio / subtitle menu
// ------------------------------------------------------------------------------------------------

/** One selectable row in the audio / subtitle menu. */
class TrackRowData(
    val label: String,
    val detail: String,
    val selected: Boolean,
    val enabled: Boolean = true,
    val onPick: () -> Unit
)

/** Subtitle position / delay controls shown in the menu. */
class AdjustState(
    val posPct: Int,
    val posEnabled: Boolean,
    val onPos: (Int, Boolean) -> Unit,
    val onPosReset: () -> Unit,
    val delayMs: Long,
    val delayEnabled: Boolean,
    val onDelay: (Int, Boolean) -> Unit,
    val onDelayReset: () -> Unit,
    val note: String
)

@Composable
fun TrackMenu(
    formatLines: List<String>,
    subs: List<TrackRowData>,
    audio: List<TrackRowData>,
    transcoded: Boolean,
    adjust: AdjustState,
    onClose: () -> Unit
) {
    val first = remember { FocusRequester() }
    val startIdx = subs.indexOfFirst { it.selected }.coerceAtLeast(0)
    LaunchedEffect(Unit) {
        delay(100L)
        try {
            first.requestFocus()
        } catch (e: Exception) {
            // ignore
        }
    }
    Box(
        Modifier
            .fillMaxHeight()
            .width(500.dp)
            .background(Brush.horizontalGradient(listOf(Color(0x00060814), Color(0xF2080A1E), Color(0xFA0B0720))))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 64.dp, end = 28.dp, top = 28.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(
                "AUDIO & SUBTITLES",
                style = TextStyle(brush = Nova.neon, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
            )
            if (formatLines.isNotEmpty()) FormatCard(formatLines)
            if (transcoded) {
                Text(
                    "Server transcode · changing a track restarts the stream",
                    color = Nova.textDim,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Text("SUBTITLES", color = Nova.cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            subs.forEachIndexed { i, c ->
                TrackRow(c, if (i == startIdx) first else null, onClose)
            }

            AdjustRow(
                label = "Subtitle position",
                value = "${adjust.posPct}% from bottom",
                hint = "◀ lower   higher ▶   OK = reset",
                enabled = adjust.posEnabled,
                onStep = adjust.onPos,
                onReset = adjust.onPosReset
            )
            AdjustRow(
                label = "Subtitle delay",
                value = fmtDelay(adjust.delayMs),
                hint = "◀ earlier   later ▶   OK = reset",
                enabled = adjust.delayEnabled,
                onStep = adjust.onDelay,
                onReset = adjust.onDelayReset
            )
            if (adjust.note.isNotEmpty()) {
                Text(adjust.note, color = Nova.textDim, fontSize = 12.sp)
            }

            Spacer(Modifier.height(8.dp))
            Text("AUDIO", color = Nova.magenta, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            if (audio.isEmpty()) {
                Text("No other audio tracks", color = Nova.textDim, fontSize = 14.sp)
            }
            audio.forEach { c -> TrackRow(c, null, onClose) }
        }
    }
}

private fun fmtDelay(ms: Long): String {
    val sign = if (ms > 0L) "+" else if (ms < 0L) "−" else ""
    val v = Math.abs(ms) / 1000.0
    return sign + String.format(java.util.Locale.US, "%.1f", v) + " s"
}

@Composable
private fun FormatCard(lines: List<String>) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x990A0E22))
            .border(1.dp, Color(0x332AF5FF), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text("NOW PLAYING", color = Nova.cyan, fontSize = 10.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
        lines.forEachIndexed { i, l ->
            if (l.isNotEmpty()) {
                Text(
                    l,
                    color = if (i == 0) Color.White else Nova.textDim,
                    fontSize = if (i == 0) 17.sp else 12.sp,
                    fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TrackRow(c: TrackRowData, focusRequester: FocusRequester?, onDone: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val base = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    Row(
        base
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) Nova.panelHi else Color(0x990E1226))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Nova.cyan else Color(0x331E2A5A),
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable {
                if (c.enabled) {
                    c.onPick()
                    onDone()
                }
            }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (c.selected) "●" else "○",
            color = if (c.selected) Nova.cyan else Nova.textDim,
            fontSize = 14.sp
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                c.label,
                color = if (!c.enabled) Nova.textDim else if (focused || c.selected) Color.White else Nova.text,
                fontSize = 16.sp,
                fontWeight = if (c.selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (c.detail.isNotEmpty()) {
                Text(
                    c.detail,
                    color = Nova.textDim,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** A row you tune with the D-pad: left / right change the value, OK resets it. */
@Composable
private fun AdjustRow(
    label: String,
    value: String,
    hint: String,
    enabled: Boolean,
    onStep: (Int, Boolean) -> Unit,
    onReset: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) Nova.panelHi else Color(0x990E1226))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) Nova.violet else Color(0x331E2A5A),
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown || !enabled) {
                    false
                } else {
                    val fast = ev.nativeKeyEvent.repeatCount > 12
                    when (ev.key) {
                        Key.DirectionLeft -> {
                            onStep(-1, fast)
                            true
                        }
                        Key.DirectionRight -> {
                            onStep(1, fast)
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                            onReset()
                            true
                        }
                        else -> false
                    }
                }
            }
            .focusable()
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = if (enabled) Nova.text else Nova.textDim,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (enabled) value else "—",
                color = if (enabled) Nova.violet else Nova.textDim,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (focused && enabled) {
            Text(hint, color = Nova.textDim, fontSize = 11.sp, letterSpacing = 1.sp)
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Subtitles drawn by the app (text subtitles only)
// ------------------------------------------------------------------------------------------------

@Composable
fun SubtitleOverlay(
    cues: List<Cue>,
    delayMs: Long,
    bottomPct: Int,
    getPos: () -> Long,
    modifier: Modifier = Modifier
) {
    var line by remember { mutableStateOf("") }
    LaunchedEffect(cues, delayMs) {
        while (true) {
            val t = SubParser.textAt(cues, getPos() - delayMs)
            if (t != line) line = t
            delay(80L)
        }
    }
    if (line.isNotEmpty()) {
        BoxWithConstraints(modifier.fillMaxSize()) {
            val bottom = maxHeight * (bottomPct.coerceIn(0, 60) / 100f)
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottom, start = 80.dp, end = 80.dp)
            ) {
                Text(
                    line,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                        shadow = Shadow(color = Color.Black, offset = Offset(0f, 0f), blurRadius = 12f)
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x66000000))
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Control bar (Pause / Restart / Go Live / ...)
// ------------------------------------------------------------------------------------------------

enum class Ctl(val label: String) {
    PLAY_PAUSE("Pause"),
    RESTART("Restart"),
    LIVE("Go Live"),
    GUIDE("Guide"),
    SURF("Channel Surf"),
    TRACKS("Audio & Subs"),
    INFO("Info")
}

@Composable
fun ControlBar(
    ch: Channel,
    entry: MediaEntry,
    next: Program?,
    live: Boolean,
    paused: Boolean,
    transcoded: Boolean,
    selected: Int,
    elapsedMs: Long,
    totalMs: Long,
    endClockMs: Long,
    infoLines: List<Pair<String, String>>?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Color(0x00050612), Color(0xE6050612), Color(0xF5050612)))
            )
            .padding(horizontal = 44.dp)
            .padding(top = 54.dp, bottom = 22.dp)
    ) {
        Column {
            if (infoLines != null) {
                InfoCard(entry, infoLines)
                Spacer(Modifier.height(14.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelBadge(ch.iconKey, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "CH ${"%02d".format(ch.number)}  ·  ${ch.name.uppercase()}",
                            color = Nova.cyan,
                            fontSize = 12.sp,
                            letterSpacing = 2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(12.dp))
                        StatusChip(live, paused, transcoded)
                    }
                    Text(
                        entry.displayTitle,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val sub = entry.displaySub
                    if (sub.isNotEmpty()) {
                        Text(sub, color = Nova.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (next != null) {
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.width(230.dp)) {
                        Text("UP NEXT · ${fmtTime(next.startMs)}", color = Nova.magenta, fontSize = 11.sp, letterSpacing = 2.sp)
                        Text(
                            next.entry.displayTitle,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            val frac = if (totalMs > 0L) (elapsedMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f) else 0f
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0x33FFFFFF))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(frac)
                        .fillMaxHeight()
                        .background(Nova.neon)
                )
            }
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(fmtPos(elapsedMs), color = Nova.text, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "${fmtDuration((totalMs - elapsedMs).coerceAtLeast(0L))} left  ·  ends ${fmtTime(endClockMs)}",
                    color = Nova.textDim,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Ctl.values().forEachIndexed { i, c ->
                    val label = when (c) {
                        Ctl.PLAY_PAUSE -> if (paused) "Play" else "Pause"
                        Ctl.LIVE -> if (live && !paused) "Live" else "Go Live"
                        else -> c.label
                    }
                    CtlButton(c, paused, label, i == selected, c == Ctl.LIVE && live && !paused)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(live: Boolean, paused: Boolean, transcoded: Boolean) {
    val (text, color) = when {
        paused -> Pair("PAUSED", Color(0xFFFFC857))
        live -> Pair("LIVE", Color(0xFFFF4D6D))
        else -> Pair("TIME-SHIFT", Nova.violet)
    }
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .clip(shape)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.7f), shape)
            .padding(horizontal = 9.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (live && !paused) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text + (if (transcoded) "  ·  TRANSCODE" else ""),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun CtlButton(ctl: Ctl, paused: Boolean, label: String, focused: Boolean, dim: Boolean) {
    val shape = RoundedCornerShape(50)
    val fg = if (focused) Color(0xFF0B0F24) else if (dim) Nova.textDim else Color.White
    Row(
        Modifier
            .shadow(if (focused) 14.dp else 0.dp, shape, ambientColor = Nova.cyan, spotColor = Nova.cyan)
            .clip(shape)
            .background(if (focused) Color(0xFFEAF0FF) else Color(0xB3151A33))
            .border(1.dp, if (focused) Nova.cyan else Color(0x332AF5FF), shape)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CtlIcon(ctl, paused, fg)
        Spacer(Modifier.width(7.dp))
        Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun CtlIcon(ctl: Ctl, paused: Boolean, tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        val s = size.minDimension
        val sw = s * 0.11f
        when (ctl) {
            Ctl.PLAY_PAUSE -> {
                if (paused) {
                    val p = Path().apply {
                        moveTo(s * 0.26f, s * 0.12f)
                        lineTo(s * 0.88f, s * 0.5f)
                        lineTo(s * 0.26f, s * 0.88f)
                        close()
                    }
                    drawPath(p, tint)
                } else {
                    drawRoundRect(tint, Offset(s * 0.2f, s * 0.14f), Size(s * 0.22f, s * 0.72f), CornerRadius(s * 0.05f))
                    drawRoundRect(tint, Offset(s * 0.58f, s * 0.14f), Size(s * 0.22f, s * 0.72f), CornerRadius(s * 0.05f))
                }
            }
            Ctl.RESTART -> {
                drawRoundRect(tint, Offset(s * 0.12f, s * 0.16f), Size(s * 0.13f, s * 0.68f), CornerRadius(s * 0.04f))
                val p = Path().apply {
                    moveTo(s * 0.88f, s * 0.16f)
                    lineTo(s * 0.36f, s * 0.5f)
                    lineTo(s * 0.88f, s * 0.84f)
                    close()
                }
                drawPath(p, tint)
            }
            Ctl.LIVE -> {
                drawCircle(tint, s * 0.17f, Offset(s / 2f, s / 2f))
                drawCircle(tint, s * 0.38f, Offset(s / 2f, s / 2f), style = Stroke(width = sw))
            }
            Ctl.GUIDE -> {
                drawLine(tint, Offset(s * 0.14f, s * 0.24f), Offset(s * 0.86f, s * 0.24f), sw, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.14f, s * 0.5f), Offset(s * 0.86f, s * 0.5f), sw, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.14f, s * 0.76f), Offset(s * 0.6f, s * 0.76f), sw, StrokeCap.Round)
            }
            Ctl.SURF -> {
                drawRoundRect(
                    tint, Offset(s * 0.1f, s * 0.16f), Size(s * 0.8f, s * 0.54f), CornerRadius(s * 0.08f),
                    style = Stroke(width = sw)
                )
                drawLine(tint, Offset(s * 0.32f, s * 0.86f), Offset(s * 0.68f, s * 0.86f), sw, StrokeCap.Round)
            }
            Ctl.TRACKS -> {
                drawRoundRect(
                    tint, Offset(s * 0.08f, s * 0.2f), Size(s * 0.84f, s * 0.6f), CornerRadius(s * 0.1f),
                    style = Stroke(width = sw)
                )
                drawLine(tint, Offset(s * 0.26f, s * 0.45f), Offset(s * 0.74f, s * 0.45f), sw * 0.9f, StrokeCap.Round)
                drawLine(tint, Offset(s * 0.26f, s * 0.62f), Offset(s * 0.58f, s * 0.62f), sw * 0.9f, StrokeCap.Round)
            }
            Ctl.INFO -> {
                drawCircle(tint, s * 0.4f, Offset(s / 2f, s / 2f), style = Stroke(width = sw))
                drawCircle(tint, s * 0.06f, Offset(s / 2f, s * 0.3f))
                drawLine(tint, Offset(s / 2f, s * 0.45f), Offset(s / 2f, s * 0.72f), sw, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun InfoCard(entry: MediaEntry, lines: List<Pair<String, String>>) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xD90B0F24))
            .border(1.dp, Color(0x442AF5FF), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        val meta = ArrayList<String>()
        if (entry.year > 0) meta.add(entry.year.toString())
        if (entry.mpaa.isNotEmpty()) meta.add(entry.mpaa)
        if (entry.rating > 0f) meta.add("★ " + String.format(java.util.Locale.US, "%.1f", entry.rating))
        if (entry.durationMs > 0L) meta.add(fmtDuration(entry.durationMs))
        if (entry.genres.isNotEmpty()) meta.add(entry.genres.take(3).joinToString(" / "))
        if (meta.isNotEmpty()) {
            Text(meta.joinToString("  ·  "), color = Nova.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (entry.overview.isNotEmpty()) {
            Text(entry.overview, color = Nova.textDim, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(2.dp))
        lines.forEach { (k, v) ->
            if (v.isNotEmpty()) {
                Row {
                    Text(k, color = Nova.cyan, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.width(110.dp))
                    Text(v, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Channel surf: a small panel listing the neighbouring channels with what is on now
// ------------------------------------------------------------------------------------------------

@Composable
fun SurfPanel(
    channels: List<Channel>,
    selected: Int,
    nowMs: Long,
    currentId: String?,
    modifier: Modifier = Modifier
) {
    val n = channels.size
    if (n == 0) return
    val shape = RoundedCornerShape(24.dp)
    val rows = Math.min(n, 5)
    val firstOff = -(rows / 2)

    Column(
        modifier
            .width(340.dp)
            .clip(shape)
            .background(Color(0xE60A0E22))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x802AF5FF), Color(0x80FF5CD6))), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("CHANNEL SURF", color = Nova.cyan, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
        for (k in 0 until rows) {
            val off = firstOff + k
            val idx = Math.floorMod(selected + off, n)
            val ch = channels[idx]
            val p = Scheduler.programAt(ch, nowMs)
            if (off == 0) {
                SurfSelected(ch, p, nowMs, ch.id == currentId)
            } else {
                SurfRow(ch, p, if (off < 0) "▲" else "▼")
            }
        }
        Text(
            "▲▼ browse   OK tune   BACK close",
            color = Nova.textDim,
            fontSize = 11.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun SurfRow(ch: Channel, p: Program?, arrow: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(arrow, color = Nova.textDim, fontSize = 11.sp, modifier = Modifier.width(18.dp))
        ChannelBadge(ch.iconKey, size = 38.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${ch.number}  ${ch.name}",
                color = Nova.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (p != null) {
                Text(
                    p.entry.displayTitle,
                    color = Nova.textDim,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SurfSelected(ch: Channel, p: Program?, nowMs: Long, isCurrent: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    val next = if (p != null) Scheduler.upcoming(ch, nowMs, 12 * 3_600_000L, 2).getOrNull(1) else null
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Nova.panelHi)
            .border(2.dp, Nova.neon, shape)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelBadge(ch.iconKey, size = 50.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${"%02d".format(ch.number)}  ${ch.name}",
                    style = TextStyle(brush = Nova.neon, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (isCurrent) "WATCHING NOW" else ch.kind.tag,
                    color = if (isCurrent) Nova.cyan else Nova.textDim,
                    fontSize = 10.sp,
                    letterSpacing = 2.sp
                )
            }
        }
        if (p != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                p.entry.displayTitle,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val sub = p.entry.displaySub
            if (sub.isNotEmpty()) {
                Text(sub, color = Nova.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "${fmtTime(p.startMs)} – ${fmtTime(p.endMs)}",
                color = Nova.cyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(5.dp))
            val frac = ((nowMs - p.startMs).toFloat() / p.durationMs.toFloat()).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x33FFFFFF))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(frac)
                        .fillMaxHeight()
                        .background(Nova.neon)
                )
            }
            if (next != null) {
                Spacer(Modifier.height(5.dp))
                Text(
                    "NEXT ${fmtTime(next.startMs)}  ${next.entry.displayTitle}",
                    color = Nova.textDim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Small shared pieces
// ------------------------------------------------------------------------------------------------

@Composable
fun Pill(text: String, modifier: Modifier = Modifier, accent: Color = Nova.cyan) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(Color(0xCC0B0F24))
            .border(1.dp, accent.copy(alpha = 0.6f), shape)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(text, color = accent, fontSize = 13.sp, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun EmptyState(busy: Boolean, status: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ChannelIcon("tv", size = 96.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            if (busy) "TUNING IN YOUR LIBRARY…" else "NO CHANNELS YET",
            style = TextStyle(brush = Nova.neon, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (status.isNotEmpty()) status else "Press OK to open Settings and connect Plex, Jellyfin or Emby",
            color = Nova.textDim,
            fontSize = 15.sp
        )
    }
}
