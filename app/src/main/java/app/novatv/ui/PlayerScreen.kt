package app.novatv.ui

import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import app.novatv.AppViewModel
import app.novatv.Channel
import app.novatv.PlayRequest
import app.novatv.Prepared
import app.novatv.Scheduler
import app.novatv.StreamInfo
import app.novatv.StreamsInfo
import app.novatv.TrackKind
import app.novatv.net.Langs
import app.novatv.net.Streams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** One selectable row in the audio / subtitle menu. */
private class TrackChoice(val label: String, val selected: Boolean, val onPick: () -> Unit)

@Composable
fun PlayerScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val channels by vm.channels.collectAsState()
    val currentId by vm.currentChannelId.collectAsState()
    val nonce by vm.tuneNonce.collectAsState()
    val busy by vm.busy.collectAsState()
    val status by vm.status.collectAsState()
    val playback by vm.playback.collectAsState()

    var guideOpen by remember { mutableStateOf(false) }
    var trackMenuOpen by remember { mutableStateOf(false) }
    var bannerVisible by remember { mutableStateOf(false) }
    var bannerTick by remember { mutableIntStateOf(0) }
    var playError by remember { mutableStateOf<String?>(null) }
    var playingId by remember { mutableStateOf("") }
    var errorStreak by remember { mutableIntStateOf(0) }
    var guideNow by remember { mutableLongStateOf(vm.now()) }

    // audio / subtitle state
    var curInfo by remember { mutableStateOf<StreamsInfo?>(null) }
    var curTranscoded by remember { mutableStateOf(false) }
    var audioKey by remember { mutableStateOf<String?>(null) }
    var subKey by remember { mutableStateOf<String?>(null) }
    var exoTracks by remember { mutableStateOf(Tracks.EMPTY) }
    val forceDirect = remember { HashSet<String>() }

    val exo = remember { ExoPlayer.Builder(ctx).build() }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    errorStreak = 0
                } else if (playbackState == Player.STATE_ENDED) {
                    val ch = vm.currentChannel()
                    val p = if (ch != null) Scheduler.programAt(ch, vm.now()) else null
                    // File ended earlier than the schedule says -> move on; otherwise just re-sync.
                    if (p != null && p.entry.id == playingId) vm.skipCurrentProgram() else vm.retune()
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                exoTracks = tracks
            }

            override fun onPlayerError(error: PlaybackException) {
                if (curTranscoded && !forceDirect.contains(playingId)) {
                    // The server couldn't transcode this one: try the original file instead.
                    forceDirect.add(playingId)
                    playError = "Server transcode failed - trying direct play"
                    vm.retune()
                    return
                }
                errorStreak += 1
                if (errorStreak >= 4) {
                    playError = "Playback keeps failing. Check that your media server is reachable."
                    exo.stop()
                } else {
                    playError = "Can't play this title - skipping to the next one"
                    vm.skipCurrentProgram()
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    // Preferred languages (direct play: ExoPlayer picks the matching embedded / side-loaded track).
    LaunchedEffect(playback.subLang, playback.audioLang, curTranscoded) {
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(if (playback.audioLang.isEmpty()) null else playback.audioLang)
            .setPreferredTextLanguage(if (playback.subLang.isEmpty()) null else playback.subLang)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, curTranscoded || playback.subLang.isEmpty())
            .build()
    }

    // Tune to whatever the schedule says is on air right now.
    LaunchedEffect(currentId, nonce) {
        val ch = vm.currentChannel() ?: return@LaunchedEffect
        val now = vm.now()
        val p = Scheduler.programAt(ch, now) ?: return@LaunchedEffect
        if (p.entry.id != playingId) {
            audioKey = null
            subKey = null
        }
        playingId = p.entry.id
        val offset = (now - p.startMs).coerceAtLeast(0L)
        val startAt = if (offset < 5_000L) 0L else offset

        val prep: Prepared = try {
            vm.prepare(p.entry, startAt, forceDirect.contains(p.entry.id), audioKey, subKey)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Prepared(PlayRequest(p.entry.streamUrl, null, emptyList(), startAt, false), null)
        }
        curInfo = prep.info
        curTranscoded = prep.req.transcoded

        val b = MediaItem.Builder().setUri(prep.req.uri)
        val mime = prep.req.mime
        if (mime != null) b.setMimeType(mime)
        if (prep.req.sidecars.isNotEmpty()) {
            b.setSubtitleConfigurations(
                prep.req.sidecars.map { sub ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.sidecarUrl))
                        .setMimeType(sub.mime)
                        .setLanguage(if (sub.lang.isEmpty()) null else sub.lang)
                        .setLabel(sub.title.ifEmpty { Langs.display(sub.lang) })
                        .build()
                }
            )
        }
        exo.setMediaItem(b.build(), prep.req.startMs)
        exo.prepare()
        exo.playWhenReady = true
        bannerTick += 1

        // when this program is scheduled to end, tune again (-> next program)
        delay((p.endMs - vm.now()).coerceAtLeast(500L))
        vm.retune()
    }

    LaunchedEffect(bannerTick) {
        if (bannerTick > 0) {
            bannerVisible = true
            delay(6_000L)
            bannerVisible = false
        }
    }
    LaunchedEffect(playError) {
        if (playError != null) {
            delay(4_000L)
            playError = null
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            guideNow = vm.now()
            delay(20_000L)
        }
    }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(guideOpen, trackMenuOpen) {
        if (!guideOpen && !trackMenuOpen) {
            delay(120L)
            try {
                rootFocus.requestFocus()
            } catch (e: Exception) {
                // not attached yet
            }
        }
    }
    BackHandler(enabled = guideOpen || trackMenuOpen) {
        if (trackMenuOpen) trackMenuOpen = false else guideOpen = false
    }

    // ---- audio / subtitle menu content --------------------------------------------------------
    val subChoices = ArrayList<TrackChoice>()
    val audioChoices = ArrayList<TrackChoice>()
    if (trackMenuOpen) {
        if (!curTranscoded) {
            // direct play: ExoPlayer knows the embedded tracks (and any side-loaded subtitle files)
            val textGroups = exoTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
            val audioGroups = exoTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
            subChoices.add(
                TrackChoice("Off", textGroups.none { it.isSelected }) {
                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                    vm.rememberLang(TrackKind.SUBTITLE, "")
                }
            )
            textGroups.forEachIndexed { i, g ->
                val f = g.getTrackFormat(0)
                subChoices.add(
                    TrackChoice(trackLabel(f, i, false), g.isSelected) {
                        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0))
                            .build()
                        val c = Langs.canon(f.language)
                        if (c.isNotEmpty()) vm.rememberLang(TrackKind.SUBTITLE, c)
                    }
                )
            }
            audioGroups.forEachIndexed { i, g ->
                val f = g.getTrackFormat(0)
                audioChoices.add(
                    TrackChoice(trackLabel(f, i, true), g.isSelected) {
                        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0))
                            .build()
                        val c = Langs.canon(f.language)
                        if (c.isNotEmpty()) vm.rememberLang(TrackKind.AUDIO, c)
                    }
                )
            }
        } else {
            // server transcode: the menu comes from the server's stream list; a change restarts the stream
            val info = curInfo
            val sk = subKey
            val ak = audioKey
            val effSub: String? = when {
                sk == null -> Streams.pickSub(info, playback.subLang)
                sk.isEmpty() -> null
                else -> sk
            }
            val effAudio: String? = ak ?: Streams.pickAudio(info, playback.audioLang)
            subChoices.add(
                TrackChoice("Off", effSub == null) {
                    subKey = ""
                    vm.rememberLang(TrackKind.SUBTITLE, "")
                    vm.retune()
                }
            )
            if (info != null) {
                for (st in info.subs) {
                    subChoices.add(
                        TrackChoice(streamLabel(st), effSub == st.key) {
                            subKey = st.key
                            val c = Langs.canon(st.lang)
                            if (c.isNotEmpty()) vm.rememberLang(TrackKind.SUBTITLE, c)
                            vm.retune()
                        }
                    )
                }
                info.audio.forEachIndexed { i, st ->
                    val sel = if (effAudio != null) effAudio == st.key else i == 0
                    audioChoices.add(
                        TrackChoice(streamLabel(st), sel) {
                            audioKey = st.key
                            val c = Langs.canon(st.lang)
                            if (c.isNotEmpty()) vm.rememberLang(TrackKind.AUDIO, c)
                            vm.retune()
                        }
                    )
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { ev ->
                if (guideOpen || trackMenuOpen || ev.type != KeyEventType.KeyDown || ev.nativeKeyEvent.repeatCount > 0) {
                    false
                } else {
                    when (ev.key) {
                        Key.DirectionUp, Key.ChannelUp -> {
                            vm.channelStep(1)
                            true
                        }
                        Key.DirectionDown, Key.ChannelDown -> {
                            vm.channelStep(-1)
                            true
                        }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Menu, Key.DirectionLeft -> {
                            if (channels.isEmpty()) vm.openSettings() else guideOpen = true
                            true
                        }
                        Key.DirectionRight, Key.Captions -> {
                            if (channels.isNotEmpty()) trackMenuOpen = true
                            true
                        }
                        Key.Info -> {
                            bannerTick += 1
                            true
                        }
                        else -> false
                    }
                }
            }
            .focusable()
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    player = exo
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    keepScreenOn = true
                    subtitleView?.setStyle(
                        CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null
                        )
                    )
                    subtitleView?.setFractionalTextSize(0.06f)
                    subtitleView?.setBottomPaddingFraction(0.08f)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (channels.isEmpty()) {
            EmptyState(busy, status, Modifier.align(Alignment.Center))
        }

        val ch = channels.firstOrNull { it.id == currentId }
        AnimatedVisibility(
            visible = bannerVisible && !guideOpen && !trackMenuOpen && ch != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 }
        ) {
            if (ch != null) NowBanner(ch, curTranscoded) { vm.now() }
        }

        AnimatedVisibility(
            visible = guideOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            GuideOverlay(
                channels = channels,
                currentId = currentId,
                nowMs = guideNow,
                onTune = {
                    vm.tune(it.id)
                    guideOpen = false
                },
                onSettings = {
                    guideOpen = false
                    vm.openSettings()
                },
                onClose = { guideOpen = false }
            )
        }

        AnimatedVisibility(
            visible = trackMenuOpen,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = fadeIn() + slideInHorizontally { it / 2 },
            exit = fadeOut() + slideOutHorizontally { it / 2 }
        ) {
            TrackMenu(
                subs = subChoices,
                audio = audioChoices,
                transcoded = curTranscoded,
                onClose = { trackMenuOpen = false }
            )
        }

        if (busy && channels.isNotEmpty()) {
            Pill(status.ifEmpty { "Syncing…" }, Modifier.align(Alignment.TopEnd).padding(24.dp))
        }
        val err = playError
        if (err != null) {
            Pill(err, Modifier.align(Alignment.TopCenter).padding(24.dp), Nova.danger)
        }
    }
}

private fun trackLabel(f: Format, idx: Int, audio: Boolean): String {
    val own = f.label
    val base = if (!own.isNullOrBlank()) own else Langs.display(f.language).ifEmpty { "Track ${idx + 1}" }
    if (!audio) return base
    val ch = when (f.channelCount) {
        1 -> "mono"
        2 -> "stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> ""
    }
    return if (ch.isEmpty()) base else "$base · $ch"
}

private fun streamLabel(st: StreamInfo): String {
    val t = st.title
    if (t.isNotEmpty()) return t
    val l = Langs.display(st.lang)
    return if (l.isNotEmpty()) l else "Track ${st.key}"
}

@Composable
private fun TrackMenu(
    subs: List<TrackChoice>,
    audio: List<TrackChoice>,
    transcoded: Boolean,
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
            .width(440.dp)
            .background(Brush.horizontalGradient(listOf(Color(0x00060814), Color(0xF2080A1E), Color(0xFA0B0720))))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 56.dp, end = 32.dp, top = 36.dp, bottom = 36.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "AUDIO & SUBTITLES",
                style = TextStyle(brush = Nova.neon, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
            )
            if (transcoded) {
                Text(
                    "Server transcode · changing a track restarts the stream",
                    color = Nova.textDim,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Text("SUBTITLES", color = Nova.cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            subs.forEachIndexed { i, c ->
                TrackRow(c, if (i == startIdx) first else null, onClose)
            }
            Spacer(Modifier.height(10.dp))
            Text("AUDIO", color = Nova.magenta, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            if (audio.isEmpty()) {
                Text("No other audio tracks", color = Nova.textDim, fontSize = 14.sp)
            }
            audio.forEach { c -> TrackRow(c, null, onClose) }
        }
    }
}

@Composable
private fun TrackRow(c: TrackChoice, focusRequester: FocusRequester?, onDone: () -> Unit) {
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
                c.onPick()
                onDone()
            }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (c.selected) "●" else "○",
            color = if (c.selected) Nova.cyan else Nova.textDim,
            fontSize = 14.sp
        )
        Spacer(Modifier.width(12.dp))
        Text(
            c.label,
            color = if (focused || c.selected) Color.White else Nova.text,
            fontSize = 16.sp,
            fontWeight = if (c.selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier = Modifier, accent: Color = Nova.cyan) {
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
private fun EmptyState(busy: Boolean, status: String, modifier: Modifier = Modifier) {
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

@Composable
private fun NowBanner(ch: Channel, transcoded: Boolean, getNow: () -> Long) {
    var now by remember { mutableLongStateOf(getNow()) }
    LaunchedEffect(ch.id) {
        while (true) {
            now = getNow()
            delay(1_000L)
        }
    }
    val program = Scheduler.programAt(ch, now) ?: return
    val next = Scheduler.upcoming(ch, now, 12 * 3_600_000L, 2).getOrNull(1)
    val e = program.entry
    val shape = RoundedCornerShape(24.dp)

    Column(Modifier.padding(horizontal = 48.dp, vertical = 30.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Brush.horizontalGradient(listOf(Color(0xE60A0E22), Color(0xE6150A2E))))
                .border(1.dp, Brush.horizontalGradient(listOf(Color(0x802AF5FF), Color(0x80FF5CD6))), shape)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "%02d".format(ch.number),
                style = TextStyle(brush = Nova.neon, fontSize = 56.sp, fontWeight = FontWeight.ExtraBold)
            )
            Spacer(Modifier.width(18.dp))
            ChannelBadge(ch.iconKey, size = 68.dp)
            Spacer(Modifier.width(22.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${ch.name.uppercase()}  ·  ${ch.kind.tag}" + (if (transcoded) "  ·  SERVER TRANSCODE" else ""),
                    color = Nova.cyan,
                    fontSize = 13.sp,
                    letterSpacing = 3.sp
                )
                Text(
                    e.displayTitle,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = e.displaySub
                if (sub.isNotEmpty()) {
                    Text(sub, color = Nova.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(10.dp))
                val frac = ((now - program.startMs).toFloat() / program.durationMs.toFloat()).coerceIn(0f, 1f)
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
                Spacer(Modifier.height(6.dp))
                Text(
                    "${fmtTime(program.startMs)} – ${fmtTime(program.endMs)}  ·  ${fmtDuration((program.endMs - now).coerceAtLeast(0L))} left",
                    color = Nova.textDim,
                    fontSize = 13.sp
                )
            }
            if (next != null) {
                Spacer(Modifier.width(24.dp))
                Column(Modifier.width(240.dp)) {
                    Text("UP NEXT · ${fmtTime(next.startMs)}", color = Nova.magenta, fontSize = 12.sp, letterSpacing = 2.sp)
                    Text(
                        next.entry.displayTitle,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "▲▼ CHANNEL      ◀ GUIDE      ▶ AUDIO & SUBTITLES",
            color = Nova.textDim,
            fontSize = 12.sp,
            letterSpacing = 3.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}
