package app.novatv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novatv.AppViewModel
import app.novatv.Channel
import kotlinx.coroutines.delay

/**
 * Lets the viewer put the channels in their own order.
 * Remote: ▲▼ choose a channel, OK picks it up, ▲▼ moves it, OK puts it down, BACK saves and leaves.
 * Touch: the ▲ ▼ buttons on every row.
 */
@Composable
fun ReorderScreen(vm: AppViewModel) {
    val channels by vm.channels.collectAsState()
    val byId = remember(channels) { channels.associateBy { it.id } }
    // the order is edited locally and handed to the view-model once, when the screen is left
    val ids = remember { mutableStateListOf<String>().also { it.addAll(vm.channels.value.map { c -> c.id }) } }
    var grabbed by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val first = remember { FocusRequester() }

    fun leave() {
        vm.saveChannelOrder(ids.toList())
        vm.back()
    }

    fun move(id: String, delta: Int) {
        val i = ids.indexOf(id)
        val j = i + delta
        if (i < 0 || j < 0 || j >= ids.size) return
        val t = ids[i]
        ids[i] = ids[j]
        ids[j] = t
    }

    BackHandler {
        if (grabbed != null) grabbed = null else leave()
    }
    LaunchedEffect(Unit) {
        delay(150L)
        try {
            first.requestFocus()
        } catch (e: Exception) {
            // not laid out yet
        }
    }
    // keep a picked-up channel on screen while it travels through a long list
    LaunchedEffect(ids.toList(), grabbed) {
        val g = grabbed ?: return@LaunchedEffect
        val i = ids.indexOf(g)
        val vis = listState.layoutInfo.visibleItemsInfo
        if (i >= 0 && vis.isNotEmpty()) {
            if (i < vis.first().index + 1) listState.scrollToItem((i - 1).coerceAtLeast(0))
            else if (i > vis.last().index - 1) listState.scrollToItem((i - vis.size + 2).coerceAtLeast(0))
        }
    }

    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 1000
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Nova.bg, Color(0xFF130A2A))))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = if (compact) 24.dp else 72.dp, vertical = if (compact) 14.dp else 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "CHANNEL ORDER",
                    style = TextStyle(brush = Nova.neon, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 5.sp)
                )
                Spacer(Modifier.weight(1f))
                NeonButton("RESET", onClick = {
                    vm.resetChannelOrder()
                    ids.clear()
                    ids.addAll(vm.defaultOrder())
                    grabbed = null
                }, accent = Nova.violet, compact = true)
                Spacer(Modifier.width(10.dp))
                NeonButton("DONE", onClick = { leave() }, accent = Nova.cyan, compact = true)
            }
            Text(
                if (grabbed != null) "Moving - ▲ ▼ to move, OK to put it down"
                else "Select a channel and press OK to pick it up  ·  BACK saves and leaves",
                color = if (grabbed != null) Nova.magenta else Nova.textDim,
                fontSize = 13.sp,
                letterSpacing = 1.sp
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(ids.toList(), key = { _, id -> id }) { idx, id ->
                    val ch = byId[id]
                    if (ch != null) {
                        ReorderRow(
                            number = idx + 1,
                            ch = ch,
                            grabbed = grabbed == id,
                            requester = if (idx == 0) first else null,
                            onToggle = { grabbed = if (grabbed == id) null else id },
                            onMove = { d -> move(id, d) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderRow(
    number: Int,
    ch: Channel,
    grabbed: Boolean,
    requester: FocusRequester?,
    onToggle: () -> Unit,
    onMove: (Int) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val acc = if (grabbed) Nova.magenta else Nova.cyan
    val base = if (requester != null) Modifier.focusRequester(requester) else Modifier
    Row(
        base
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(
                when {
                    grabbed -> Nova.magenta.copy(alpha = 0.22f)
                    focused -> Nova.cyan.copy(alpha = 0.14f)
                    else -> Nova.panel
                }
            )
            .border(
                if (focused || grabbed) 2.dp else 1.dp,
                if (focused || grabbed) acc else Color(0x26FFFFFF),
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        onToggle()
                        true
                    }
                    Key.DirectionUp -> if (grabbed) {
                        onMove(-1)
                        true
                    } else false
                    Key.DirectionDown -> if (grabbed) {
                        onMove(1)
                        true
                    } else false
                    else -> false
                }
            }
            .focusable()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "%02d".format(number),
            color = if (focused || grabbed) acc else Nova.textDim,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(44.dp)
        )
        ChannelBadge(ch.iconKey, size = 38.dp, chId = ch.id)
        Spacer(Modifier.width(12.dp))
        Text(
            ch.name,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text("${ch.entries.size} titles", color = Nova.text, fontSize = 12.sp)
        Spacer(Modifier.width(14.dp))
        Text(ch.kind.tag, color = Nova.textDim, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.width(76.dp))
        Spacer(Modifier.width(6.dp))
        // touch buttons (not focus targets, so the remote is not slowed down)
        ArrowChip("▲") { onMove(-1) }
        Spacer(Modifier.width(6.dp))
        ArrowChip("▼") { onMove(1) }
    }
}

@Composable
private fun ArrowChip(label: String, onTap: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier
            .clip(shape)
            .background(Color(0x22FFFFFF))
            .tapOnly(onTap)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Nova.text, fontSize = 13.sp)
    }
}
