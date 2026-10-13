package app.novatv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novatv.Channel
import app.novatv.Program
import app.novatv.Scheduler
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

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
    val startFocus = remember { FocusRequester() }
    var detail by remember { mutableStateOf<Pair<Channel, Program>?>(null) }
    val curIdx = channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)

    LaunchedEffect(Unit) {
        listState.scrollToItem((curIdx - 1).coerceAtLeast(0))
        delay(120)
        try {
            startFocus.requestFocus()
        } catch (e: Exception) {
            // row not laid out yet; the user can still navigate with the D-pad
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xF206081A), Color(0xF4130A2A))))
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.Menu) {
                    onClose()
                    true
                } else {
                    false
                }
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp)
                .padding(top = 26.dp, bottom = 22.dp)
        ) {
            // ---- header ----
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "NOVA TV",
                    style = TextStyle(
                        brush = Nova.neon,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 6.sp
                    )
                )
                Spacer(Modifier.width(18.dp))
                Text("CHANNEL GUIDE", color = Nova.textDim, fontSize = 14.sp, letterSpacing = 4.sp)
                Spacer(Modifier.weight(1f))
                Text(fmtTime(nowMs), color = Nova.text, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.width(24.dp))
                NeonButton("SETTINGS", onClick = onSettings, accent = Nova.violet)
            }
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Nova.neon)
            )

            // ---- channel rows ----
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                items(channels, key = { it.id }) { ch ->
                    GuideRow(
                        ch = ch,
                        nowMs = nowMs,
                        isCurrent = ch.id == currentId,
                        focusRequester = if (ch.id == currentId) startFocus else null,
                        onTune = onTune,
                        onFocusProgram = { p -> detail = Pair(ch, p) }
                    )
                }
            }

            DetailPanel(detail)
        }
    }
}

@Composable
private fun GuideRow(
    ch: Channel,
    nowMs: Long,
    isCurrent: Boolean,
    focusRequester: FocusRequester?,
    onTune: (Channel) -> Unit,
    onFocusProgram: (Program) -> Unit
) {
    val programs = remember(ch.id, nowMs / 60_000L) {
        Scheduler.upcoming(ch, nowMs, 4 * 3_600_000L, 10)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(84.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelCell(
            ch = ch,
            isCurrent = isCurrent,
            focusRequester = focusRequester,
            onClick = { onTune(ch) },
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
        )
        Spacer(Modifier.width(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            itemsIndexed(programs) { i, p ->
                ProgramCard(
                    p = p,
                    nowMs = nowMs,
                    isNow = i == 0,
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
    isCurrent: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    modifier: Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val base = if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier
    val border: Brush = if (focused) Nova.neon else SolidColor(Color(0x331E2A5A))
    Row(
        base
            .shadow(if (focused) 18.dp else 0.dp, shape, ambientColor = Nova.cyan, spotColor = Nova.cyan)
            .clip(shape)
            .background(if (focused) Nova.panelHi else Nova.panel)
            .border(if (focused) 2.dp else 1.dp, border, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "%02d".format(ch.number),
            style = TextStyle(
                brush = Nova.neon,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.width(44.dp)
        )
        ChannelBadge(ch.iconKey, size = 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                ch.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Nova.cyan)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    if (isCurrent) "ON NOW" else ch.kind.tag,
                    color = if (isCurrent) Nova.cyan else Nova.textDim,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

@Composable
private fun ProgramCard(
    p: Program,
    nowMs: Long,
    isNow: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "cardScale")
    val shape = RoundedCornerShape(14.dp)
    val minutes = (p.durationMs / 60_000L).toInt()
    val width = (minutes * 4).coerceIn(170, 520).dp
    val border: Brush = if (focused) Nova.neon else SolidColor(Color(0x331E2A5A))

    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (focused) 16.dp else 0.dp, shape, ambientColor = Nova.magenta, spotColor = Nova.magenta)
            .clip(shape)
            .background(if (focused) Nova.panelHi else Nova.panel)
            .border(if (focused) 2.dp else 1.dp, border, shape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(Modifier.align(Alignment.CenterStart)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    fmtTime(p.startMs),
                    color = if (isNow) Nova.cyan else Nova.textDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                if (isNow) {
                    Spacer(Modifier.width(8.dp))
                    Text("NOW", color = Nova.magenta, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                }
            }
            Text(
                p.entry.displayTitle,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                p.entry.displaySub,
                color = Nova.textDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isNow && p.durationMs > 0) {
            val frac = ((nowMs - p.startMs).toFloat() / p.durationMs.toFloat()).coerceIn(0f, 1f)
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(frac)
                    .height(3.dp)
                    .background(Nova.neon)
            )
        }
    }
}

@Composable
private fun DetailPanel(detail: Pair<Channel, Program>?) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(112.dp)
            .clip(shape)
            .background(Color(0xCC0B0F24))
            .border(1.dp, Color(0x332AF5FF), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (detail == null) {
            Text("Use ◀ ▶ ▲ ▼ to browse · OK to tune in · BACK to close", color = Nova.textDim, fontSize = 14.sp, letterSpacing = 1.sp)
            return@Row
        }
        val (ch, p) = detail
        val e = p.entry
        Box(
            Modifier
                .width(140.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(10.dp))
                .background(Nova.panel),
            contentAlignment = Alignment.Center
        ) {
            ChannelIcon(ch.iconKey, size = 44.dp)
            if (e.thumbUrl.isNotEmpty()) {
                AsyncImage(
                    model = e.thumbUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${ch.name.uppercase()}  ·  CH ${"%02d".format(ch.number)}",
                color = Nova.cyan,
                fontSize = 12.sp,
                letterSpacing = 2.sp
            )
            Text(
                e.displayTitle,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = e.displaySub
            if (sub.isNotEmpty()) {
                Text(sub, color = Nova.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "${fmtTime(p.startMs)} – ${fmtTime(p.endMs)}  ·  ${fmtDuration(p.durationMs)}" +
                    (if (e.genres.isNotEmpty()) "  ·  " + e.genres.take(3).joinToString(" / ") else ""),
                color = Nova.textDim,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
