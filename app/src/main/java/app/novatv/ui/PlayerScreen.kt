package app.novatv.ui

import android.content.Context
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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import app.novatv.DtsMode
import app.novatv.AppViewModel
import app.novatv.Channel
import app.novatv.Program
import app.novatv.PlayRequest
import app.novatv.Prepared
import app.novatv.Scheduler
import app.novatv.StreamInfo
import app.novatv.StreamsInfo
import app.novatv.TrackKind
import app.novatv.VideoInfo
import app.novatv.net.Cue
import app.novatv.net.Http
import app.novatv.net.Labels
import app.novatv.net.Langs
import app.novatv.net.Streams
import app.novatv.net.SubParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Audio / subtitle menu and channel surf are drawn at this fraction of their normal size. */
private const val PANEL_SCALE = 0.75f

/** What the full-screen slate says (between programmes, or when a title can't be played). */
private class SlateMsg(val headline: String, val title: String, val detail: String, val isError: Boolean)

private fun errorText(e: PlaybackException): String {
    val name = e.errorCodeName.removePrefix("ERROR_CODE_").lowercase().replace('_', ' ')
    val cause = e.cause?.message?.lineSequence()?.firstOrNull()?.take(70) ?: ""
    return if (cause.isEmpty()) name else "$name: $cause"
}

/** Set from the server's stream info: is the audio track that is playing really DTS-HD (MA / HRA / X)? */
object AudioHints {
    @Volatile
    var dtsHd: Boolean = false
}

/**
 * MKV files usually label DTS-HD MA tracks "A_DTS", so ExoPlayer sends only the plain DTS core to the receiver.
 * When the server says the track is DTS-HD we hand the sink a DTS-HD format instead, so the receiver gets the full stream.
 */
private class DtsHdSink(sink: AudioSink) : ForwardingAudioSink(sink) {
    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        val f = if (AudioHints.dtsHd && inputFormat.sampleMimeType == MimeTypes.AUDIO_DTS) {
            inputFormat.buildUpon().setSampleMimeType(MimeTypes.AUDIO_DTS_HD).build()
        } else {
            inputFormat
        }
        super.configure(f, specifiedBufferSize, outputChannels)
    }
}

private fun dtsHdOf(inf: StreamsInfo?, key: String?): Boolean {
    if (inf == null) return false
    val a = (if (key == null) null else inf.audio.firstOrNull { it.key == key })
        ?: inf.audio.firstOrNull { it.isDefault } ?: inf.audio.firstOrNull()
    return a != null && Streams.isDtsHd(a)
}

@Composable
fun PlayerScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val isTv = remember { Device.isTv(ctx) }
    // short screens (a phone held sideways) get a smaller guide header / preview window
    GuideMetrics.applyFor(LocalConfiguration.current.screenHeightDp)
    val channels by vm.channels.collectAsState()
    val currentId by vm.currentChannelId.collectAsState()
    val nonce by vm.tuneNonce.collectAsState()
    val busy by vm.busy.collectAsState()
    val status by vm.status.collectAsState()
    val playback by vm.playback.collectAsState()

    // overlays
    var guideOpen by remember { mutableStateOf(false) }
    var trackMenuOpen by remember { mutableStateOf(false) }
    var surfOpen by remember { mutableStateOf(false) }
    var surfIdx by remember { mutableIntStateOf(0) }
    var infoOpen by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(false) }
    // TV: BACK inside the guide opens a two-way choice (A back to TV / B exit the app)
    var backAsk by remember { mutableStateOf(false) }
    val backAskFocus = remember { FocusRequester() }
    var controlsTick by remember { mutableIntStateOf(0) }
    var ctrlIndex by remember { mutableIntStateOf(0) }

    // playback state
    var playError by remember { mutableStateOf<String?>(null) }
    var playingId by remember { mutableStateOf("") }
    var slate by remember { mutableStateOf<SlateMsg?>(null) }
    var guideNow by remember { mutableLongStateOf(vm.now()) }
    var tickNow by remember { mutableLongStateOf(vm.now()) }
    var posNow by remember { mutableLongStateOf(0L) }
    var posBaseMs by remember { mutableLongStateOf(0L) }
    var live by remember { mutableStateOf(true) }
    var paused by remember { mutableStateOf(false) }
    var exitAsk by remember { mutableStateOf(false) }
    val exitNoFocus = remember { FocusRequester() }
    var startOverrideMs by remember { mutableStateOf<Long?>(null) }

    // audio / subtitle state
    var curInfo by remember { mutableStateOf<StreamsInfo?>(null) }
    var curTranscoded by remember { mutableStateOf(false) }
    // true when the current stream is a Jellyfin / Emby transcode (text subtitles are drawn by the app, not burned in)
    var curSoft by remember { mutableStateOf(false) }
    var audioKey by remember { mutableStateOf<String?>(null) }
    var subKey by remember { mutableStateOf<String?>(null) }
    var exoTracks by remember { mutableStateOf(Tracks.EMPTY) }
    var videoFmt by remember { mutableStateOf<Format?>(null) }
    var cues by remember { mutableStateOf<List<Cue>>(emptyList()) }
    var ownReady by remember { mutableStateOf(false) }
    var ownFailed by remember { mutableStateOf(false) }
    var subDelayMs by remember { mutableLongStateOf(0L) }
    val forceDirect = remember { HashSet<String>() }
    val forceTranscode = remember { HashSet<String>() }
    val retried = remember { HashSet<String>() }

    /** Slate for the time between the end of a film and the next slot on a "real" channel. */
    fun gapSlate(c: Channel, p: Program): SlateMsg {
        val nxt = Scheduler.programAt(c, p.endMs)
        return SlateMsg(
            "UP NEXT  ·  ${fmtTime(p.endMs)}",
            nxt?.entry?.displayTitle ?: "Next programme",
            nxt?.entry?.displaySub ?: "",
            false
        )
    }

    val exo = remember {
        val coreOnly = vm.playback.value.dtsMode == DtsMode.CORE
        val rf = object : DefaultRenderersFactory(ctx) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                if (!coreOnly) {
                    val base = super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
                    return if (base == null) null else DtsHdSink(base)
                }
                // "DTS-HD: CORE ONLY": pretend the receiver can't take DTS-HD so ExoPlayer falls back to the DTS core
                val caps = AudioCapabilities.getCapabilities(context)
                val keep = listOf(
                    C.ENCODING_PCM_16BIT, C.ENCODING_AC3, C.ENCODING_E_AC3, C.ENCODING_E_AC3_JOC,
                    C.ENCODING_AC4, C.ENCODING_DTS, C.ENCODING_DOLBY_TRUEHD
                ).filter { it == C.ENCODING_PCM_16BIT || caps.supportsEncoding(it) }.toIntArray()
                return DefaultAudioSink.Builder(context)
                    .setAudioCapabilities(AudioCapabilities(keep, 8))
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }
        // patient network settings: a server transcode (or a far-away server) can take a while to answer
        val http = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(60_000)
            .setAllowCrossProtocolRedirects(true)
        ExoPlayer.Builder(ctx, rf)
            .setMediaSourceFactory(DefaultMediaSourceFactory(ctx).setDataSourceFactory(http))
            .build()
    }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    retried.remove(playingId)
                } else if (playbackState == Player.STATE_ENDED) {
                    val ch = vm.currentChannel()
                    val p = if (ch != null) Scheduler.programAt(ch, vm.now()) else null
                    if (ch != null && p != null && p.entry.id == playingId) {
                        val left = p.endMs - vm.now()
                        if (left > 5_000L && (ch.slotMs > 0L || left > 90_000L)) {
                            // picture is over but the slot isn't: show what's next until the slot ends
                            slate = gapSlate(ch, p)
                        } else {
                            // File ended a moment before the schedule says -> move on.
                            vm.skipCurrentProgram()
                        }
                    } else {
                        vm.retune()
                    }
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                paused = !playWhenReady
            }

            override fun onTracksChanged(tracks: Tracks) {
                exoTracks = tracks
                videoFmt = exo.videoFormat
            }

            override fun onPlayerError(error: PlaybackException) {
                val id = playingId
                val why = errorText(error)
                val c = vm.currentChannel()
                if (!retried.contains(id)) {
                    // 1) one plain retry (network hiccups, the surface being resized ...)
                    retried.add(id)
                    playError = "Playback hiccup ($why) - retrying"
                    vm.retune()
                } else if (curTranscoded && !forceTranscode.contains(id) && !forceDirect.contains(id)) {
                    // 2a) the server's transcode failed: try the original file
                    forceDirect.add(id)
                    playError = "Server transcode failed ($why) - trying direct play"
                    vm.retune()
                } else if (!curTranscoded && !forceDirect.contains(id) && !forceTranscode.contains(id)) {
                    // 2b) the Shield can't decode the original: let the server convert it
                    forceTranscode.add(id)
                    playError = "Direct play failed ($why) - asking the server to transcode"
                    vm.retune()
                } else {
                    // give up on this title for now, but leave the schedule alone
                    exo.stop()
                    val p = if (c != null) Scheduler.programAt(c, vm.now()) else null
                    val e = p?.entry
                    slate = SlateMsg(
                        "CAN'T PLAY THIS TITLE",
                        e?.displayTitle ?: "",
                        why + (if (p != null) "   ·   next programme at ${fmtTime(p.endMs)}" else ""),
                        true
                    )
                    playError = null
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    // ---- which subtitle / audio stream is in effect ---------------------------------------------
    val info = curInfo
    val effSub: StreamInfo? = if (info == null) {
        null
    } else {
        val sk = subKey
        val k: String? = when {
            sk == null -> Streams.pickSub(info, playback.subLang)
            sk.isEmpty() -> null
            else -> sk
        }
        if (k == null) null else info.subs.firstOrNull { it.key == k }
    }
    val effAudioKey: String? = if (info == null) null else (audioKey ?: Streams.pickAudio(info, playback.audioLang))
    val wantOwn = (!curTranscoded || curSoft) && effSub != null && effSub.isText && effSub.sidecarUrl.isNotEmpty()
    val ownActive = wantOwn && ownReady

    // ---- tune to whatever the schedule says is on air right now ----------------------------------
    LaunchedEffect(currentId, nonce) {
        val ch = vm.currentChannel() ?: return@LaunchedEffect
        val now = vm.now()
        val p = Scheduler.programAt(ch, now) ?: return@LaunchedEffect
        slate = null
        if (p.entry.id != playingId) {
            audioKey = null
            subKey = null
            curInfo = null
            cues = emptyList()
            ownReady = false
            ownFailed = false
            subDelayMs = 0L
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()
        }
        playingId = p.entry.id
        val ovr = startOverrideMs
        startOverrideMs = null
        live = ovr == null
        val offset = (now - p.startMs).coerceAtLeast(0L)
        val startAt = ovr ?: (if (offset < 5_000L) 0L else offset)

        // a "real" channel whose picture already ended: nothing to play until the next slot starts
        if (ovr == null && p.endMs - p.contentEndMs > 5_000L && now >= p.contentEndMs - 1_500L) {
            exo.stop()
            slate = gapSlate(ch, p)
            controlsTick += 1
            delay((p.endMs - vm.now()).coerceAtLeast(500L))
            vm.retune()
            return@LaunchedEffect
        }

        val prep: Prepared = try {
            vm.prepare(p.entry, startAt, forceDirect.contains(p.entry.id), audioKey, subKey, forceTranscode.contains(p.entry.id))
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Prepared(PlayRequest(p.entry.streamUrl, null, emptyList(), startAt, false), null)
        }
        curInfo = prep.info
        AudioHints.dtsHd = dtsHdOf(prep.info, audioKey ?: Streams.pickAudio(prep.info, playback.audioLang))
        curTranscoded = prep.req.transcoded
        curSoft = prep.req.softCapable
        // Plex HLS starts at the requested offset, so the player's own clock begins at 0 there.
        posBaseMs = if (prep.req.transcoded && prep.req.startMs == 0L) startAt else 0L

        val b = MediaItem.Builder().setUri(prep.req.uri)
        val mime = prep.req.mime
        if (mime != null) b.setMimeType(mime)
        exo.setMediaItem(b.build(), prep.req.startMs)
        exo.prepare()
        exo.playWhenReady = true
        controlsTick += 1

        // when this program is scheduled to end, tune again (-> next program), unless the viewer
        // has paused / restarted and is watching time-shifted
        delay((p.endMs - vm.now()).coerceAtLeast(500L))
        if (live) vm.retune()
    }

    // ---- text subtitles: fetch + parse so we can draw (and shift) them ---------------------------
    LaunchedEffect(playingId, curInfo, effSub?.key, curTranscoded, curSoft) {
        cues = emptyList()
        ownReady = false
        ownFailed = false
        val st = effSub
        if ((curTranscoded && !curSoft) || st == null || !st.isText || st.sidecarUrl.isEmpty()) return@LaunchedEffect
        val loaded: List<Cue> = withContext(Dispatchers.IO) {
            try {
                SubParser.parse(Http.get(st.sidecarUrl))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
        if (loaded.isEmpty()) {
            ownFailed = true
            if (st.external || curTranscoded) playError = "Couldn't load this subtitle file"
        } else {
            cues = loaded
            ownReady = true
        }
    }

    // ---- apply language / track choices to ExoPlayer ----------------------------------------------
    LaunchedEffect(
        exoTracks, curInfo, effSub?.key, effAudioKey, ownReady, ownFailed, curTranscoded,
        playback.subLang, playback.audioLang, audioKey
    ) {
        val inf = curInfo
        AudioHints.dtsHd = dtsHdOf(inf, effAudioKey)
        val b = exo.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguage(if (playback.audioLang.isEmpty()) null else playback.audioLang)
            .setPreferredTextLanguage(if (playback.subLang.isEmpty()) null else playback.subLang)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        var textOff = true
        if (!curTranscoded) {
            val st = effSub
            if (inf == null) {
                // no server details: let ExoPlayer pick by preferred language
                textOff = playback.subLang.isEmpty()
            } else if (st != null) {
                val ownWanted = st.isText && st.sidecarUrl.isNotEmpty()
                val useNative = if (ownWanted) ownFailed && !st.external else !st.external
                if (useNative) {
                    val groups = exoTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                    val g = mapGroup(inf.subs.filter { !it.external }, st, groups)
                    if (g != null) {
                        textOff = false
                        b.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0))
                    }
                }
            }
            val ak = audioKey
            if (ak != null && inf != null) {
                val st = inf.audio.firstOrNull { it.key == ak }
                val groups = exoTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                val g = if (st != null) mapGroup(inf.audio, st, groups) else null
                if (g != null) b.setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0))
            }
        }
        b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, textOff)
        val np = b.build()
        if (np != exo.trackSelectionParameters) exo.trackSelectionParameters = np
    }

    // ---- small timers --------------------------------------------------------------------------
    LaunchedEffect(controlsTick) {
        if (controlsTick > 0) {
            controlsVisible = true
            delay(7_000L)
            controlsVisible = false
        }
    }
    LaunchedEffect(guideOpen) {
        if (!guideOpen) backAsk = false
    }
    LaunchedEffect(backAsk) {
        if (backAsk) {
            delay(120L)
            try {
                backAskFocus.requestFocus()
            } catch (e: Exception) {
                // dialog not laid out yet
            }
        }
    }
    LaunchedEffect(playError) {
        if (playError != null) {
            delay(7_000L)
            playError = null
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            guideNow = vm.now()
            delay(20_000L)
        }
    }

    val barWanted = controlsVisible || paused || infoOpen
    val ch = channels.firstOrNull { it.id == currentId }
    val overlayOpen = guideOpen || trackMenuOpen || surfOpen
    val barShown = barWanted && !overlayOpen && ch != null

    LaunchedEffect(barShown, surfOpen) {
        while (barShown || surfOpen) {
            tickNow = vm.now()
            posNow = exo.currentPosition
            delay(500L)
        }
    }

    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(exitAsk) {
        if (exitAsk) {
            delay(120L)
            try {
                exitNoFocus.requestFocus()
            } catch (e: Exception) {
                // dialog not laid out yet
            }
        }
    }
    LaunchedEffect(guideOpen, trackMenuOpen, exitAsk, backAsk) {
        if (!guideOpen && !trackMenuOpen && !exitAsk && !backAsk) {
            delay(120L)
            try {
                rootFocus.requestFocus()
            } catch (e: Exception) {
                // not attached yet
            }
        }
    }

    // ---- actions ---------------------------------------------------------------------------------
    fun togglePause() {
        if (exo.playWhenReady) {
            exo.playWhenReady = false
            live = false
        } else {
            exo.playWhenReady = true
        }
    }

    fun restart() {
        live = false
        if (!curTranscoded && exo.playbackState != Player.STATE_IDLE) {
            exo.seekTo(0L)
            exo.playWhenReady = true
            slate = null
        } else {
            startOverrideMs = 0L
            vm.retune()
        }
    }

    fun goLive() {
        val c = vm.currentChannel() ?: return
        val p = Scheduler.programAt(c, vm.now()) ?: return
        if (!curTranscoded && p.entry.id == playingId) {
            exo.seekTo((vm.now() - p.startMs).coerceAtLeast(0L))
            exo.playWhenReady = true
            live = true
        } else {
            vm.retune()
        }
    }

    fun seekBy(deltaMs: Long) {
        if (curTranscoded) return
        var t = exo.currentPosition + deltaMs
        val d = exo.duration
        if (d != C.TIME_UNSET) t = t.coerceAtMost(d - 1_000L)
        exo.seekTo(t.coerceAtLeast(0L))
        live = false
        controlsTick += 1
    }

    fun openSurf() {
        surfIdx = channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        controlsVisible = false
        infoOpen = false
        surfOpen = true
    }

    fun activate(i: Int) {
        controlsTick += 1
        when (Ctl.values()[i]) {
            Ctl.PLAY_PAUSE -> togglePause()
            Ctl.RESTART -> restart()
            Ctl.LIVE -> goLive()
            Ctl.GUIDE -> guideOpen = true
            Ctl.SURF -> openSurf()
            Ctl.TRACKS -> trackMenuOpen = true
            Ctl.INFO -> infoOpen = !infoOpen
        }
    }

    fun handleKey(key: Key): Boolean {
        if (channels.isEmpty()) {
            if (key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter || key == Key.Menu) {
                vm.openSettings()
                return true
            }
            return false
        }
        val last = Ctl.values().size - 1
        return when (key) {
            Key.DirectionUp, Key.ChannelUp -> {
                vm.channelStep(1)
                true
            }
            Key.DirectionDown, Key.ChannelDown -> {
                vm.channelStep(-1)
                true
            }
            Key.DirectionLeft -> {
                if (barWanted) {
                    ctrlIndex = (ctrlIndex - 1).coerceAtLeast(0)
                    controlsTick += 1
                } else {
                    guideOpen = true
                }
                true
            }
            Key.DirectionRight -> {
                if (barWanted) {
                    ctrlIndex = (ctrlIndex + 1).coerceAtMost(last)
                    controlsTick += 1
                } else {
                    trackMenuOpen = true
                }
                true
            }
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                if (barWanted) {
                    activate(ctrlIndex)
                } else {
                    ctrlIndex = 0
                    controlsTick += 1
                }
                true
            }
            Key.Menu -> {
                guideOpen = true
                true
            }
            Key.Captions -> {
                trackMenuOpen = true
                true
            }
            Key.Info -> {
                if (infoOpen) infoOpen = false else {
                    infoOpen = true
                    controlsTick += 1
                }
                true
            }
            Key.MediaPlayPause, Key.Spacebar -> {
                togglePause()
                controlsTick += 1
                true
            }
            Key.MediaPlay -> {
                exo.playWhenReady = true
                true
            }
            Key.MediaPause -> {
                if (exo.playWhenReady) togglePause()
                controlsTick += 1
                true
            }
            Key.MediaFastForward -> {
                seekBy(30_000L)
                true
            }
            Key.MediaRewind -> {
                seekBy(-30_000L)
                true
            }
            else -> false
        }
    }

    fun handleSurfKey(key: Key): Boolean {
        val n = channels.size
        if (n == 0) return false
        return when (key) {
            Key.DirectionUp, Key.ChannelUp -> {
                surfIdx = Math.floorMod(surfIdx - 1, n)
                true
            }
            Key.DirectionDown, Key.ChannelDown -> {
                surfIdx = Math.floorMod(surfIdx + 1, n)
                true
            }
            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                vm.tune(channels[surfIdx.coerceIn(0, n - 1)].id)
                surfOpen = false
                true
            }
            Key.DirectionLeft, Key.DirectionRight -> true
            else -> false
        }
    }

    BackHandler {
        // TV: BACK in the guide asks "A: back to TV  /  B: exit app"; BACK again answers A.
        when {
            exitAsk -> exitAsk = false
            backAsk -> {
                backAsk = false
                guideOpen = false
            }
            trackMenuOpen -> trackMenuOpen = false
            surfOpen -> surfOpen = false
            guideOpen -> if (isTv) backAsk = true else guideOpen = false
            infoOpen -> infoOpen = false
            controlsVisible -> controlsVisible = false
            paused -> exo.playWhenReady = true
            else -> exitAsk = true
        }
    }

    // ---- touch --------------------------------------------------------------------------------------
    val onScreenTap: () -> Unit = {
        when {
            exitAsk -> exitAsk = false
            guideOpen -> Unit
            trackMenuOpen -> trackMenuOpen = false
            surfOpen -> surfOpen = false
            channels.isEmpty() -> vm.openSettings()
            infoOpen -> infoOpen = false
            controlsVisible -> controlsVisible = false
            else -> controlsTick += 1
        }
    }
    val onScreenDoubleTap: (Boolean) -> Unit = { left ->
        if (!exitAsk && !guideOpen && !trackMenuOpen && !surfOpen && channels.isNotEmpty()) {
            seekBy(if (left) -10_000L else 30_000L)
        }
    }
    val onScreenSwipe: (Int) -> Unit = { dir ->
        if (!exitAsk && !guideOpen && !trackMenuOpen && channels.isNotEmpty()) {
            vm.channelStep(dir)
            controlsTick += 1
        }
    }

    /** In a transcode: does switching to [st] (null = off) need a new stream (burn-in added / removed)? */
    fun subNeedsRestart(st: StreamInfo?): Boolean {
        if (!curTranscoded) return false
        val newSoft = curSoft && st != null && st.isText && st.sidecarUrl.isNotEmpty()
        val nowBurned = effSub != null && !wantOwn
        return nowBurned || (st != null && !newSoft)
    }

    // ---- audio / subtitle menu content --------------------------------------------------------------
    val subRows = ArrayList<TrackRowData>()
    val audioRows = ArrayList<TrackRowData>()
    val formatLines = ArrayList<String>()
    val vInfo: VideoInfo? = info?.video ?: exoVideo(videoFmt)
    val audioGroups = exoTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }

    if (trackMenuOpen) {
        if (vInfo != null) {
            formatLines.add(Labels.videoLine(vInfo))
            val d = Labels.videoDetail(vInfo)
            if (d.isNotEmpty()) formatLines.add(d)
        }
        formatLines.add(if (curTranscoded) "Server transcode  ·  up to ${playback.maxMbps} Mbps" else "Direct play")

        val subOff = if (info == null) playback.subLang.isEmpty() else effSub == null
        subRows.add(
            TrackRowData("Off", "", subOff) {
                subKey = ""
                vm.rememberLang(TrackKind.SUBTITLE, "")
                if (subNeedsRestart(null)) vm.retune()
            }
        )
        if (info != null) {
            for (st in info.subs) {
                val usable = curTranscoded || (st.isText && st.sidecarUrl.isNotEmpty()) || !st.external
                subRows.add(
                    TrackRowData(
                        Labels.subLabel(st),
                        Labels.subDetail(st),
                        effSub?.key == st.key,
                        usable
                    ) {
                        subKey = st.key
                        val c = Langs.canon(st.lang)
                        if (c.isNotEmpty()) vm.rememberLang(TrackKind.SUBTITLE, c)
                        if (subNeedsRestart(st)) vm.retune()
                    }
                )
            }
            info.audio.forEachIndexed { i, st ->
                val g = if (curTranscoded) null else mapGroup(info.audio, st, audioGroups)
                val supported = g?.isSupported ?: true
                val selected = if (curTranscoded) {
                    if (effAudioKey != null) effAudioKey == st.key else i == 0
                } else {
                    g?.isSelected ?: false
                }
                val detail = Labels.audioDetail(st) +
                    (if (!supported) (if (Labels.audioDetail(st).isEmpty()) "" else "  ·  ") + "Not supported on this device" else "")
                audioRows.add(
                    TrackRowData(Labels.audioLabel(st), detail, selected, supported) {
                        audioKey = st.key
                        AudioHints.dtsHd = Streams.isDtsHd(st)
                        val c = Langs.canon(st.lang)
                        if (c.isNotEmpty()) vm.rememberLang(TrackKind.AUDIO, c)
                        if (curTranscoded || playback.dtsMode == DtsMode.CONVERT) vm.retune()
                    }
                )
            }
        } else if (!curTranscoded) {
            // the server gave us no stream details: describe what ExoPlayer found in the file
            val textGroups = exoTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
            textGroups.forEachIndexed { i, g ->
                val f = g.getTrackFormat(0)
                val lang = Langs.display(f.language).ifEmpty { f.label ?: "Track ${i + 1}" }
                val fmt = subMimeName(f.sampleMimeType)
                subRows.add(
                    TrackRowData(if (fmt.isEmpty()) lang else "$lang ($fmt)", "", g.isSelected) {
                        val c = Langs.canon(f.language)
                        if (c.isNotEmpty()) vm.rememberLang(TrackKind.SUBTITLE, c)
                    }
                )
            }
            audioGroups.filter { it.isSupported }.forEachIndexed { i, g ->
                val f = g.getTrackFormat(0)
                val lang = Langs.display(f.language).ifEmpty { f.label ?: "Track ${i + 1}" }
                val tech = listOf(
                    Labels.mimeToAudio(f.sampleMimeType, f.codecs),
                    Labels.layout(if (f.channelCount > 0) f.channelCount else 0, "")
                ).filter { it.isNotEmpty() }.joinToString(" ")
                audioRows.add(
                    TrackRowData(if (tech.isEmpty()) lang else "$lang  ·  $tech", "", g.isSelected) {
                        val c = Langs.canon(f.language)
                        if (c.isNotEmpty()) vm.rememberLang(TrackKind.AUDIO, c)
                    }
                )
            }
        }
    }

    val nativeTextFallback = !curTranscoded && effSub != null && effSub.isText && ownFailed
    val loadingSub = wantOwn && !ownReady && !ownFailed
    val adjustNote = when {
        curTranscoded && effSub != null && !wantOwn -> "This subtitle is burned into the video by the server, so position and delay can't be changed."
        effSub == null -> "Select a subtitle to adjust its position and delay."
        loadingSub -> "Loading subtitle…"
        ownActive -> "Position and delay apply to this text subtitle."
        nativeTextFallback -> "Delay isn't available for this subtitle (position only)."
        else -> "Image subtitles (PGS / VobSub) can't be moved or delayed. Pick an SRT / text track for that."
    }
    val adjust = AdjustState(
        posPct = playback.subPos,
        posEnabled = ownActive || nativeTextFallback,
        onPos = { dir, fast ->
            val step = if (fast) 3 else 1
            vm.updatePlayback(playback.copy(subPos = (playback.subPos + dir * step).coerceIn(-20, 50)))
        },
        onPosReset = { vm.updatePlayback(playback.copy(subPos = 8)) },
        delayMs = subDelayMs,
        delayEnabled = ownActive,
        onDelay = { dir, fast ->
            val step = if (fast) 500L else 100L
            subDelayMs = (subDelayMs + dir * step).coerceIn(-60_000L, 60_000L)
        },
        onDelayReset = { subDelayMs = 0L },
        sizePct = playback.subSize,
        sizeEnabled = ownActive || nativeTextFallback,
        onSize = { dir, fast ->
            val step = if (fast) 20 else 10
            vm.updatePlayback(playback.copy(subSize = (playback.subSize + dir * step).coerceIn(50, 250)))
        },
        onSizeReset = { vm.updatePlayback(playback.copy(subSize = 100)) },
        note = adjustNote
    )

    // ---- info panel + control bar data ------------------------------------------------------------------
    val entry = ch?.entries?.firstOrNull { it.id == playingId }
    val infoLines: List<Pair<String, String>>? = if (infoOpen && entry != null) {
        val lines = ArrayList<Pair<String, String>>()
        if (vInfo != null) {
            lines.add(Pair("VIDEO", Labels.videoLine(vInfo)))
            lines.add(Pair("", ""))
            val d = Labels.videoDetail(vInfo)
            if (d.isNotEmpty()) lines[lines.size - 1] = Pair("CODEC", d)
        }
        val selAudio: StreamInfo? = if (info == null) {
            null
        } else {
            info.audio.firstOrNull { st ->
                !curTranscoded && mapGroup(info.audio, st, audioGroups)?.isSelected == true
            } ?: info.audio.firstOrNull { it.key == effAudioKey } ?: info.audio.firstOrNull()
        }
        lines.add(Pair("AUDIO", if (selAudio != null) Labels.audioLabel(selAudio) else ""))
        lines.add(
            Pair(
                "SUBTITLES",
                if (effSub != null) Labels.subLabel(effSub) + (if (curTranscoded && !wantOwn) "  ·  burned in" else "") else "Off"
            )
        )
        lines.add(
            Pair(
                "PLAYBACK",
                if (curTranscoded) "Server transcode  ·  up to ${playback.maxMbps} Mbps" else "Direct play"
            )
        )
        lines
    } else {
        null
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { ev ->
                if (exitAsk || backAsk || guideOpen || trackMenuOpen || ev.type != KeyEventType.KeyDown) {
                    false
                } else if (surfOpen) {
                    handleSurfKey(ev.key)
                } else if (ev.nativeKeyEvent.repeatCount > 0) {
                    false
                } else {
                    handleKey(ev.key)
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
                }
            },
            update = { view ->
                view.subtitleView?.let { sv ->
                    val pos = playback.subPos
                    sv.setBottomPaddingFraction(pos.coerceIn(0, 60) / 100f)
                    sv.setFractionalTextSize(0.06f * playback.subSize.coerceIn(40, 250) / 100f)
                    // below 0% the text is pushed down past the picture's bottom edge (into the black bar)
                    sv.translationY = if (pos < 0) -pos / 100f * view.height else 0f
                    if (pos < 0) {
                        var par = sv.parent
                        while (par is ViewGroup) {
                            par.clipChildren = false
                            par = par.parent
                        }
                    }
                }
            },
            // full screen normally; shrinks into the guide's preview window while the guide is open
            modifier = if (guideOpen) {
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = GuideMetrics.previewTop, end = GuideMetrics.previewEnd)
                    .size(GuideMetrics.previewW, GuideMetrics.previewH)
            } else {
                Modifier.fillMaxSize()
            }
        )

        if (channels.isEmpty()) {
            EmptyState(busy, status, Modifier.align(Alignment.Center))
        }

        val sl = slate
        if (sl != null && ch != null && !guideOpen) {
            Slate(ch, sl.headline, sl.title, sl.detail, sl.isError)
        }

        if (ownActive && !guideOpen && sl == null) {
            SubtitleOverlay(cues, subDelayMs, playback.subPos, { exo.currentPosition }, sizePct = playback.subSize)
        }

        // touch: tap = show / hide controls, double tap = skip, swipe = change channel
        Box(
            Modifier
                .fillMaxSize()
                .touchLayer(onScreenTap, onScreenDoubleTap, onScreenSwipe)
        )

        // ---- control bar (Pause / Restart / Go Live / Guide / Channel Surf / Audio & Subs / Info) ----
        AnimatedVisibility(
            visible = barShown && entry != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 }
        ) {
            if (ch != null && entry != null) {
                val liveProg = Scheduler.programAt(ch, tickNow)
                val total = entry.durationMs
                val elapsed = if (live && liveProg != null && liveProg.entry.id == entry.id) {
                    tickNow - liveProg.startMs
                } else {
                    posBaseMs + posNow
                }
                val endClock = tickNow + (total - elapsed).coerceAtLeast(0L)
                val next = Scheduler.upcoming(ch, tickNow, 12 * 3_600_000L, 2).getOrNull(1)
                ControlBar(
                    ch = ch,
                    entry = entry,
                    next = next,
                    live = live,
                    paused = paused,
                    transcoded = curTranscoded,
                    selected = ctrlIndex,
                    elapsedMs = elapsed.coerceAtLeast(0L),
                    totalMs = total,
                    endClockMs = endClock,
                    infoLines = infoLines,
                    onActivate = { activate(it) },
                    onChannel = {
                        vm.channelStep(it)
                        controlsTick += 1
                    }
                )
            }
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
                onClose = { guideOpen = false },
                loadInfo = { vm.streamsFor(it) }
            )
        }

        // the channel-surf and audio / subtitle panels are drawn smaller than everything else
        val baseDensity = LocalDensity.current
        val panelDensity = remember(baseDensity) { Density(baseDensity.density * (if (isTv) PANEL_SCALE else 1f), baseDensity.fontScale) }
        CompositionLocalProvider(LocalDensity provides panelDensity) {
            AnimatedVisibility(
                visible = surfOpen,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = fadeIn() + slideInHorizontally { it / 2 },
                exit = fadeOut() + slideOutHorizontally { it / 2 }
            ) {
                SurfPanel(
                    channels, surfIdx, tickNow, currentId,
                    modifier = Modifier.swallowTouch(),
                    onPick = { idx ->
                        if (idx in channels.indices) vm.tune(channels[idx].id)
                        surfOpen = false
                    }
                )
            }

            AnimatedVisibility(
                visible = trackMenuOpen,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = fadeIn() + slideInHorizontally { it / 2 },
                exit = fadeOut() + slideOutHorizontally { it / 2 }
            ) {
                TrackMenu(
                    formatLines = formatLines,
                    subs = subRows,
                    audio = audioRows,
                    transcoded = curTranscoded,
                    adjust = adjust,
                    onClose = { trackMenuOpen = false }
                )
            }
        }

        if (busy && channels.isNotEmpty()) {
            Pill(status.ifEmpty { "Syncing…" }, Modifier.align(Alignment.TopEnd).padding(24.dp))
        }
        val err = playError
        if (err != null) {
            Pill(err, Modifier.align(Alignment.TopCenter).padding(24.dp), Nova.danger)
        }

        if (backAsk && guideOpen) {
            BackChoiceDialog(
                channelLabel = ch?.let { "CH ${"%02d".format(it.number)} · ${it.name}" } ?: "Keep watching",
                firstFocus = backAskFocus,
                onReturn = {
                    backAsk = false
                    guideOpen = false
                },
                onExit = { (ctx as? android.app.Activity)?.finish() }
            )
        }

        if (exitAsk) {
            ExitDialog(
                noFocus = exitNoFocus,
                onYes = { (ctx as? android.app.Activity)?.finish() },
                onNo = { exitAsk = false }
            )
        }
    }
}

/**
 * Finds the ExoPlayer track group that belongs to server stream [st]. Positions match when the file
 * and the server list the same number of streams; otherwise we match by language and occurrence.
 */
private fun mapGroup(all: List<StreamInfo>, st: StreamInfo, groups: List<Tracks.Group>): Tracks.Group? {
    val pos = all.indexOfFirst { it.key == st.key }
    if (pos < 0 || groups.isEmpty()) return null
    if (groups.size == all.size) return groups[pos]
    val lang = Langs.canon(st.lang)
    val nth = all.take(pos).count { Langs.canon(it.lang) == lang }
    val same = groups.filter { Langs.canon(it.getTrackFormat(0).language) == lang }
    return same.getOrNull(nth)
}

/** Video details read from the decoder, used when the server gave none. */
private fun exoVideo(f: Format?): VideoInfo? {
    if (f == null || f.width <= 0) return null
    val mime = f.sampleMimeType ?: ""
    val transfer = f.colorInfo?.colorTransfer
    val hdr = when {
        mime.contains("dolby-vision") -> "Dolby Vision"
        transfer == C.COLOR_TRANSFER_ST2084 -> "HDR10"
        transfer == C.COLOR_TRANSFER_HLG -> "HLG"
        else -> "SDR"
    }
    return VideoInfo(
        width = f.width,
        height = f.height,
        fps = if (f.frameRate > 0f) f.frameRate else 0f,
        codec = mime.substringAfter('/'),
        hdr = hdr,
        bitDepth = 0,
        bitrateKbps = if (f.bitrate > 0) f.bitrate / 1000 else 0
    )
}

private fun subMimeName(mime: String?): String = when ((mime ?: "").lowercase()) {
    "application/pgs" -> "PGS"
    "application/x-subrip" -> "SRT"
    "text/vtt" -> "VTT"
    "text/x-ssa" -> "ASS"
    "application/dvbsubs" -> "DVB"
    "application/vobsub" -> "VobSub"
    "application/x-quicktime-tx3g" -> "TX3G"
    else -> ""
}
