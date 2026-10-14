package app.novatv.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.requiredWidth
import app.novatv.Channel
import app.novatv.Program
import app.novatv.Scheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.TimeZone

/** Sizes shared between the guide and the player (which shrinks the video into the preview window). */
object GuideMetrics {
    val previewW = 272.dp
    val previewH = 153.dp
    val previewTop = 54.dp
    val previewEnd = 32.dp

    val sideW = 190.dp
    val rowH = 44.dp
    val gutter = 8.dp

    /** Timeline scale: 4 dp per minute -> a 90 minute movie is 360 dp wide. */
    const val DP_PER_MIN = 4f
    const val WINDOW_MIN = 360
}

private const val HALF_HOUR = 30 * 60_000L

private fun floorHalfHour(ms: Long): Long {
    val off = TimeZone.getDefault().getOffset(ms).toLong()
    val local = ms + off
    return local - Math.floorMod(local, HALF_HOUR) - off
}

private fun minToDp(startMs: Long, winStart: Long): Float =
    (startMs - winStart) / 60_000f * GuideMetrics.DP_PER_MIN

@Composable
fun GuideOverlay(
    channels: List<Channel>,
    currentId: String?,
    nowMs: Long,
    onTune: (Channel) -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // one focus handle per channel row: it is attached to that row's programme that is on air right now
    val liveRequesters = remember { HashMap<String, FocusRequester>() }
    val curIdx = channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    // the timeline starts half an hour before the last half-hour mark and stays put while the guide is open
    val winStart = remember { floorHalfHour(nowMs) - HALF_HOUR }

    var detail by remember { mutableStateOf<Pair<Channel, Program>?>(null) }
    var focusEnd by remember { mutableStateOf<Long?>(null) }
    var scrollTarget by remember { mutableFloatStateOf(0f) }
    // where the D-pad focus is: 0 = a programme block, 1 = a channel cell, 2 = the SETTINGS button
    var zone by remember { mutableIntStateOf(0) }
    val scroll = remember { Animatable(0f) }
    var jumpJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(scrollTarget) {
        scroll.animateTo(scrollTarget, tween(220))
    }

    fun liveRequester(id: String): FocusRequester = liveRequesters.getOrPut(id) { FocusRequester() }
    // one focus handle per channel cell (the name box on the far left of each row)
    val cellRequesters = remember { HashMap<String, FocusRequester>() }
    fun cellRequester(id: String): FocusRequester = cellRequesters.getOrPut(id) { FocusRequester() }
    var cellId by remember { mutableStateOf<String?>(null) }

    /** Moves the focus to the programme that is on air now in channel number [i] (scrolling to it first). */
    fun jumpToLive(i: Int) {
        if (i < 0 || i >= channels.size) return
        jumpJob?.cancel()
        jumpJob = scope.launch {
            val vis = listState.layoutInfo.visibleItemsInfo
            val first = vis.firstOrNull()?.index ?: 0
            val last = vis.lastOrNull()?.index ?: 0
            if (i < first || i > last) {
                listState.scrollToItem((i - 2).coerceAtLeast(0))
                delay(60)
            }
            val req = liveRequester(channels[i].id)
            var tries = 0
            while (tries < 4) {
                try {
                    req.requestFocus()
                    break
                } catch (e: Exception) {
                    tries += 1
                    delay(50)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        listState.scrollToItem((curIdx - 2).coerceAtLeast(0))
        delay(150)
        val req = liveRequester(channels.getOrNull(curIdx)?.id ?: "")
        try {
            req.requestFocus()
        } catch (e: Exception) {
            // row not laid out yet; the user can still navigate with the D-pad
        }
    }

    val accent = remember(detail?.first?.iconKey) { accentOf(detail?.first?.iconKey ?: "") }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .drawBehind {
                // dark backdrop with a window cut out where the live preview is shown
                val l = size.width - (GuideMetrics.previewEnd + GuideMetrics.previewW).toPx()
                val r = size.width - GuideMetrics.previewEnd.toPx()
                val t = GuideMetrics.previewTop.toPx()
                val b = t + GuideMetrics.previewH.toPx()
                val brush = Brush.verticalGradient(
                    listOf(Color(0xF5090910), Color(0xF8160D1E)),
                    startY = 0f,
                    endY = size.height
                )
                drawRect(brush, topLeft = Offset(0f, 0f), size = Size(size.width, t))
                drawRect(brush, topLeft = Offset(0f, b), size = Size(size.width, size.height - b))
                drawRect(brush, topLeft = Offset(0f, t), size = Size(l, b - t))
                drawRect(brush, topLeft = Offset(r, t), size = Size(size.width - r, b - t))
                drawRect(
                    Nova.cyan.copy(alpha = 0.75f),
                    topLeft = Offset(l - 2f, t - 2f),
                    size = Size(r - l + 4f, b - t + 4f),
                    style = Stroke(width = 4f)
                )
            }
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) {
                    false
                } else if (ev.key == Key.Menu) {
                    onClose()
                    true
                } else if (ev.key == Key.DirectionLeft && zone == 0 && detail != null &&
                    detail!!.second.startMs <= winStart + 1_000L
                ) {
                    // the first programme of a row: Left goes to that row's channel name box
                    try {
                        cellRequester(detail!!.first.id).requestFocus()
                        true
                    } catch (e: Exception) {
                        false
                    }
                } else if (ev.key == Key.DirectionRight && zone == 1 && cellId != null) {
                    // from a channel name box, Right goes to what is on air now in that row
                    val i = channels.indexOfFirst { it.id == cellId }
                    if (i >= 0) {
                        jumpToLive(i)
                        true
                    } else {
                        false
                    }
                } else if (ev.key == Key.DirectionUp || ev.key == Key.DirectionDown) {
                    // Up / Down always land on what is on air NOW in the next channel, not on whatever
                    // programme happens to sit under the cursor.
                    val d = detail
                    val down = ev.key == Key.DirectionDown
                    val idx = if (d == null) curIdx else channels.indexOfFirst { it.id == d.first.id }
                    if (zone == 2) {
                        if (down) {
                            jumpToLive(if (idx < 0) curIdx else idx)
                            true
                        } else {
                            false
                        }
                    } else if (zone != 0 || d == null || idx < 0) {
                        false
                    } else if (!down && idx == 0) {
                        false // top row: let the focus reach the SETTINGS button
                    } else if (down && idx >= channels.size - 1) {
                        true
                    } else {
                        jumpToLive(if (down) idx + 1 else idx - 1)
                        true
                    }
                } else {
                    false
                }
            }
    ) {
        val viewportDp = maxWidth.value - 64f - GuideMetrics.sideW.value - GuideMetrics.gutter.value
        val totalDp = GuideMetrics.WINDOW_MIN * GuideMetrics.DP_PER_MIN
        val maxScroll = (totalDp - viewportDp).coerceAtLeast(0f)

        fun focusProgram(ch: Channel, p: Program) {
            zone = 0
            detail = Pair(ch, p)
            focusEnd = p.contentEndMs
            val x0 = minToDp(p.startMs, winStart)
            val w = p.spanMs / 60_000f * GuideMetrics.DP_PER_MIN
            val t = scrollTarget
            var nt = t
            val onAir = nowMs >= p.startMs && nowMs < p.endMs
            if (onAir) {
                // keep the red "now" line in view; only scroll when the block is (almost) off screen
                if (x0 + w < t + 120f) {
                    nt = x0 - 12f
                } else if (x0 > t + viewportDp - 120f) {
                    nt = x0 + w - viewportDp + 12f
                }
            } else if (x0 < t) {
                nt = x0 - 12f
            } else if (x0 + w > t + viewportDp) {
                nt = Math.min(x0 + w - viewportDp + 12f, x0 - 12f)
            }
            scrollTarget = nt.coerceIn(0f, maxScroll)
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .padding(top = 12.dp, bottom = 10.dp)
        ) {
            // ---- header ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "BENDIT TV",
                    style = TextStyle(
                        brush = Nova.neon,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 5.sp
                    )
                )
                Spacer(Modifier.width(14.dp))
                Text("CHANNEL GUIDE", color = Nova.textDim, fontSize = 12.sp, letterSpacing = 4.sp)
                Spacer(Modifier.weight(1f))
                Text(fmtTime(nowMs), color = Nova.text, fontSize = 20.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.width(18.dp))
                Box(Modifier.onFocusChanged { if (it.hasFocus) zone = 2 }) {
                    NeonButton("SETTINGS", onClick = onSettings, accent = Nova.violet, compact = true)
                }
            }
            Spacer(Modifier.height(2.dp))

            // ---- detail panel (left) + live preview window (right, drawn by the player) ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(GuideMetrics.previewH)
            ) {
                DetailPanel(detail, nowMs, accent, Modifier.weight(1f).fillMaxHeight())
                Spacer(Modifier.width(GuideMetrics.previewW + 16.dp))
            }
            Spacer(Modifier.height(8.dp))

            // ---- timeline: ruler + channel rows + vertical time lines ----
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(Modifier.fillMaxSize()) {
                    Ruler(winStart, nowMs, focusEnd, accent.c1, scroll)
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(channels, key = { it.id }) { ch ->
                            GuideRow(
                                ch = ch,
                                winStart = winStart,
                                nowMs = nowMs,
                                isCurrent = ch.id == currentId,
                                liveFocus = liveRequester(ch.id),
                                scroll = scroll,
                                onTune = onTune,
                                cellFocus = cellRequester(ch.id),
                                onCellFocus = { zone = 1; cellId = ch.id },
                                onFocusProgram = { p -> focusProgram(ch, p) }
                            )
                        }
                    }
                }
                Canvas(Modifier.fillMaxSize()) {
                    val x0 = (GuideMetrics.sideW + GuideMetrics.gutter).toPx()
                    val h = size.height
                    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 10f), 0f)
                    // a dashed line on every half hour
                    for (i in 0..(GuideMetrics.WINDOW_MIN / 30)) {
                        val x = x0 + (i * 30 * GuideMetrics.DP_PER_MIN - scroll.value).dp.toPx()
                        if (x < x0 || x > size.width) continue
                        drawLine(
                            color = if (i % 2 == 0) Color(0x40FFFFFF) else Color(0x24FFFFFF),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1.5f,
                            pathEffect = dash
                        )
                    }
                    // where the focused programme ends
                    val fe = focusEnd
                    if (fe != null) {
                        val x = x0 + (minToDp(fe, winStart) - scroll.value).dp.toPx()
                        if (x >= x0 && x <= size.width) {
                            drawLine(
                                color = accent.c1.copy(alpha = 0.95f),
                                start = Offset(x, 0f),
                                end = Offset(x, h),
                                strokeWidth = 3f,
                                pathEffect = dash
                            )
                        }
                    }
                    // now
                    val xn = x0 + (minToDp(nowMs, winStart) - scroll.value).dp.toPx()
                    if (xn >= x0 && xn <= size.width) {
                        drawLine(
                            color = Color(0xFFFF4D6D),
                            start = Offset(xn, 0f),
                            end = Offset(xn, h),
                            strokeWidth = 3f
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Ruler (time labels)
// ------------------------------------------------------------------------------------------------

@Composable
private fun Ruler(winStart: Long, nowMs: Long, focusEnd: Long?, endColor: Color, scroll: Animatable<Float, *>) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(GuideMetrics.sideW), contentAlignment = Alignment.CenterStart) {
            Text(fmtDate(nowMs).uppercase(), color = Nova.textDim, fontSize = 11.sp, letterSpacing = 2.sp)
        }
        Spacer(Modifier.width(GuideMetrics.gutter))
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds()
        ) {
            for (i in 0..(GuideMetrics.WINDOW_MIN / 30)) {
                val t = winStart + i * HALF_HOUR
                Text(
                    fmtTime(t),
                    color = Nova.textDim,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset((i * 30 * GuideMetrics.DP_PER_MIN - scroll.value + 4f).dp.roundToPx(), 0) }
                )
            }
            RulerTag(fmtTime(nowMs), minToDp(nowMs, winStart), scroll, Color(0xFFFF4D6D), Color.White)
            if (focusEnd != null) {
                RulerTag("ENDS " + fmtTime(focusEnd), minToDp(focusEnd, winStart), scroll, endColor, Color(0xFF05060F))
            }
        }
    }
}

/** A small filled label centred on a time position of the ruler. */
@Composable
private fun RulerTag(text: String, xDp: Float, scroll: Animatable<Float, *>, bg: Color, fg: Color) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .offset { IntOffset((xDp - scroll.value).dp.roundToPx() - 52.dp.roundToPx(), 0) }
            .requiredWidth(104.dp)
            .height(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .clip(shape)
                .background(bg)
                .padding(horizontal = 9.dp, vertical = 1.dp)
        ) {
            Text(text, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
    }
}

// ------------------------------------------------------------------------------------------------
// One channel row
// ------------------------------------------------------------------------------------------------

@Composable
private fun GuideRow(
    ch: Channel,
    winStart: Long,
    nowMs: Long,
    isCurrent: Boolean,
    liveFocus: FocusRequester,
    cellFocus: FocusRequester,
    scroll: Animatable<Float, *>,
    onTune: (Channel) -> Unit,
    onCellFocus: () -> Unit,
    onFocusProgram: (Program) -> Unit
) {
    val programs = remember(ch.id, winStart) {
        Scheduler.upcoming(ch, winStart, GuideMetrics.WINDOW_MIN * 60_000L, 40)
    }
    val acc = remember(ch.iconKey) { accentOf(ch.iconKey) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(GuideMetrics.rowH),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelCell(
            ch = ch,
            acc = acc,
            isCurrent = isCurrent,
            onClick = { onTune(ch) },
            onFocus = onCellFocus,
            modifier = Modifier
                .focusRequester(cellFocus)
                .width(GuideMetrics.sideW)
                .fillMaxHeight()
        )
        Spacer(Modifier.width(GuideMetrics.gutter))
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds()
        ) {
            for (p in programs) {
                val isNow = nowMs >= p.startMs && nowMs < p.endMs
                ProgramBlock(
                    p = p,
                    acc = acc,
                    winStart = winStart,
                    nowMs = nowMs,
                    isNow = isNow,
                    scroll = scroll,
                    focusRequester = if (isNow) liveFocus else null,
                    onClick = { onTune(ch) },
                    onFocus = { onFocusProgram(p) }
                )
            }
        }
    }
}

@Composable
private fun ChannelCell(
    ch: Channel,
    acc: Accent,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val border: Brush = if (focused) acc.brush else SolidColor(acc.c1.copy(alpha = 0.28f))
    Row(
        modifier
            .shadow(if (focused) 14.dp else 0.dp, shape, ambientColor = acc.c1, spotColor = acc.c1)
            .clip(shape)
            .background(tint(Nova.panel, acc.c1, if (focused) 0.34f else 0.16f))
            .border(if (focused) 2.dp else 1.dp, border, shape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "%02d".format(ch.number),
            style = TextStyle(brush = acc.brush, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.width(30.dp)
        )
        ChannelBadge(ch.iconKey, size = 32.dp, chId = ch.id)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                ch.name,
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(acc.c1)
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    if (isCurrent) "ON NOW" else ch.kind.tag,
                    color = if (isCurrent) acc.c1 else Nova.textDim,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

/** A programme drawn at its real position on the shared timeline. */
@Composable
private fun ProgramBlock(
    p: Program,
    acc: Accent,
    winStart: Long,
    nowMs: Long,
    isNow: Boolean,
    scroll: Animatable<Float, *>,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    onFocus: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val x0 = minToDp(p.startMs, winStart)
    val w = Math.max(p.spanMs / 60_000f * GuideMetrics.DP_PER_MIN, 56f)
    val border: Brush = when {
        focused -> acc.brush
        isNow -> SolidColor(acc.c1.copy(alpha = 0.65f))
        else -> SolidColor(acc.c1.copy(alpha = 0.2f))
    }
    val base = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    val bg = when {
        focused -> tint(Nova.panelHi, acc.c1, 0.38f)
        isNow -> tint(Nova.panel, acc.c1, 0.27f)
        else -> tint(Nova.panel, acc.c1, 0.11f)
    }

    Box(
        base
            .offset { IntOffset((x0 - scroll.value).dp.roundToPx(), 0) }
            .width((w - 3f).dp)
            .fillMaxHeight()
            .shadow(if (focused) 12.dp else 0.dp, shape, ambientColor = acc.c2, spotColor = acc.c2)
            .clip(shape)
            .background(bg)
            .border(if (focused) 2.dp else 1.dp, border, shape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(onClick = onClick)
    ) {
        // slotted channels: the part of the slot after the picture ends is drawn darker
        val gapMin = (p.spanMs - p.durationMs) / 60_000f
        if (gapMin >= 3f) {
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width((gapMin * GuideMetrics.DP_PER_MIN).dp)
                    .background(Color(0x66000000))
            )
        }
        // keep the title readable when the left part of the block has scrolled out of view
        val maxShift = Math.max(w - 100f, 0f)
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((scroll.value - x0).coerceIn(0f, maxShift).dp.roundToPx(), 0) }
                .padding(horizontal = 10.dp)
        ) {
            val en = p.entry
            val epTag = if (en.kind == app.novatv.EntryKind.EPISODE) en.subtitle else ""
            val titleText = buildAnnotatedString {
                append(en.displayTitle)
                if (epTag.isNotEmpty()) {
                    withStyle(SpanStyle(color = tint(Color.White, acc.c1, 0.65f), fontWeight = FontWeight.Bold, fontSize = 11.sp)) {
                        append("  " + epTag)
                    }
                }
            }
            Text(
                titleText,
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    fmtTime(p.startMs) + "  ·  " + fmtDuration(p.durationMs),
                    color = if (isNow) tint(Color.White, acc.c1, 0.55f) else Nova.textDim,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    maxLines = 1
                )
                if (isNow) {
                    Spacer(Modifier.width(6.dp))
                    Text("NOW", color = acc.c2, fontSize = 9.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }
        }
        if (isNow && p.durationMs > 0) {
            val frac = ((nowMs - p.startMs).toFloat() / p.durationMs.toFloat()).coerceIn(0f, 1f)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(frac * (p.durationMs.toFloat() / p.spanMs.toFloat()))
                    .height(3.dp)
                    .background(acc.brush)
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Detail panel
// ------------------------------------------------------------------------------------------------

@Composable
private fun DetailPanel(detail: Pair<Channel, Program>?, nowMs: Long, acc: Accent, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(tint(Color(0xE60C0C13), acc.c1, 0.09f))
            .border(1.dp, acc.c1.copy(alpha = 0.55f), shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center
    ) {
        if (detail == null) {
            Text(
                "Use ◀ ▶ ▲ ▼ to browse  ·  OK to tune in  ·  BACK to close",
                color = Nova.textDim,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
            return@Column
        }
        val (ch, p) = detail
        val e = p.entry
        Text(
            "CH ${"%02d".format(ch.number)}  ·  ${ch.name.uppercase()}  ·  ${ch.kind.tag}",
            color = acc.c1,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            e.displayTitle,
            color = Color.White,
            fontSize = 20.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val meta = ArrayList<String>()
        val sub = e.displaySub
        if (sub.isNotEmpty() && e.kind == app.novatv.EntryKind.EPISODE) meta.add(sub)
        if (e.kind == app.novatv.EntryKind.MOVIE && e.year > 0) meta.add(e.year.toString())
        if (e.mpaa.isNotEmpty()) meta.add(e.mpaa)
        if (e.rating > 0f) meta.add("★ " + String.format(java.util.Locale.US, "%.1f", e.rating))
        meta.add(fmtDuration(p.durationMs))
        if (e.genres.isNotEmpty()) meta.add(e.genres.take(3).joinToString(" / "))
        Text(
            meta.joinToString("  ·  "),
            color = Nova.text,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (e.overview.isNotEmpty()) {
            Text(
                e.overview,
                color = Nova.textDim,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(3.dp))
        val onAir = nowMs >= p.startMs && nowMs < p.endMs
        val ended = onAir && nowMs >= p.contentEndMs
        val timeLine = when {
            ended -> "Finished ${fmtTime(p.contentEndMs)}   ·   next starts ${fmtTime(p.endMs)}"
            onAir -> "${fmtTime(p.startMs)} – ${fmtTime(p.contentEndMs)}   ·   ends ${fmtTime(p.contentEndMs)}   ·   ${fmtDuration(p.contentEndMs - nowMs)} left"
            nowMs < p.startMs -> "Starts ${fmtTime(p.startMs)}   ·   ends ${fmtTime(p.contentEndMs)}   ·   in ${fmtDuration(p.startMs - nowMs)}"
            else -> "Aired ${fmtTime(p.startMs)} – ${fmtTime(p.contentEndMs)}"
        }
        Text(
            timeLine,
            color = if (onAir) acc.c1 else Nova.text,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (onAir && p.durationMs > 0) {
            Spacer(Modifier.height(3.dp))
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
                        .background(acc.brush)
                )
            }
        }
    }
}
