package app.novatv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.novatv.AppViewModel
import app.novatv.PlayMode
import app.novatv.Server
import app.novatv.ServerType
import app.novatv.net.Langs
import kotlinx.coroutines.delay

private val bitrates = listOf(2, 4, 8, 12, 20, 40)

private fun stepBitrate(cur: Int, dir: Int): Int {
    var i = bitrates.indexOfFirst { it >= cur }
    if (i < 0) i = bitrates.size - 1
    return bitrates[(i + dir).coerceIn(0, bitrates.size - 1)]
}

private fun nextLang(cur: String): String {
    val l = Langs.choices
    val i = l.indexOf(cur)
    return l[(i + 1) % l.size]
}

private fun langLabel(code: String, whenEmpty: String): String =
    if (code.isEmpty()) whenEmpty else Langs.name(code).uppercase()

private fun serverColor(t: ServerType): Color = when (t) {
    ServerType.PLEX -> Color(0xFFE5A00D)
    ServerType.JELLYFIN -> Color(0xFFAA5CC3)
    ServerType.EMBY -> Color(0xFF52B54B)
}

@Composable
private fun ScreenFrame(content: @Composable () -> Unit) {
    // phones held sideways are much narrower than a TV: tighter margins
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 1000
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Nova.bg, Color(0xFF130A2A))))
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (compact) 24.dp else 72.dp, vertical = if (compact) 16.dp else 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun Title(text: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = TextStyle(brush = Nova.neon, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 6.sp)
        )
        Spacer(Modifier.width(16.dp))
        Text(sub, color = Nova.textDim, fontSize = 14.sp, letterSpacing = 4.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Spacer(Modifier.height(10.dp))
    Text(text, color = Nova.cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
}

// ------------------------------------------------------------------------------------------------
// Settings
// ------------------------------------------------------------------------------------------------

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val servers by vm.servers.collectAsState()
    val settings by vm.settings.collectAsState()
    val playback by vm.playback.collectAsState()
    val status by vm.status.collectAsState()
    val busy by vm.busy.collectAsState()
    val channels by vm.channels.collectAsState()
    val first = remember { FocusRequester() }

    val logoUrl by vm.logoUrl.collectAsState()
    val logoMsg by vm.logoMsg.collectAsState()
    DisposableEffect(Unit) { onDispose { vm.stopLogoServer() } }

    BackHandler { vm.back() }
    LaunchedEffect(Unit) {
        delay(150L)
        try {
            first.requestFocus()
        } catch (e: Exception) {
            // ignore
        }
    }

    ScreenFrame {
        Title("SETTINGS", "BENDIT TV")

        SectionLabel("MEDIA SERVERS")
        if (servers.isEmpty()) {
            Text("No servers connected yet. Add Plex, Jellyfin or Emby to build your channels.", color = Nova.textDim, fontSize = 15.sp)
        }
        servers.forEach { s ->
            ServerRow(s, onToggle = { vm.setServerEnabled(s.id, !s.enabled) }, onRemove = { vm.removeServer(s.id) })
        }
        if (servers.size > 1) {
            Text(
                "Switch a server OFF to ignore it without removing it - e.g. Plex while you are away from home. Titles that exist on several servers are taken from the first server that is ON.",
                color = Nova.textDim,
                fontSize = 13.sp
            )
        }
        NeonButton("+  ADD SERVER", onClick = { vm.openAddServer() }, focusRequester = first)

        SectionLabel("CHANNELS")
        ToggleRow("Genre channels for movies (Action, Comedy, Drama…)", settings.movieGenres) {
            vm.updateSettings(settings.copy(movieGenres = it))
        }
        ToggleRow("Genre channels for series", settings.seriesGenres) {
            vm.updateSettings(settings.copy(seriesGenres = it))
        }
        ToggleRow("Studio / network channels (HBO, NBC, CBS, FOX, Disney+ …)", settings.networks) {
            vm.updateSettings(settings.copy(networks = it))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Only create a channel when it has at least this many titles",
                color = Nova.text,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            NeonButton("−", onClick = { vm.updateSettings(settings.copy(minItems = (settings.minItems - 2).coerceAtLeast(2))) })
            Text(
                settings.minItems.toString(),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(56.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            NeonButton("+", onClick = { vm.updateSettings(settings.copy(minItems = (settings.minItems + 2).coerceAtMost(60))) })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NeonButton("REORDER CHANNELS", onClick = { vm.openReorder() }, accent = Nova.magenta)
            Text(
                "Put the channels in your own order, e.g. ABC as channel 1.",
                color = Nova.textDim,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
        }

        SectionLabel("CHANNEL LOGOS")
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonButton(
                if (logoUrl == null) "UPLOAD LOGOS FROM PHONE / PC" else "STOP UPLOADER",
                onClick = { if (logoUrl == null) vm.startLogoServer() else vm.stopLogoServer() },
                accent = Color(0xFFFFB347)
            )
            if (logoUrl != null) {
                Spacer(Modifier.width(18.dp))
                Text(logoUrl ?: "", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            if (logoUrl != null) "Open this address in a browser on a phone or PC on the same Wi-Fi, pick a channel and upload an image. The page closes when you leave Settings."
            else if (logoMsg.isNotEmpty()) logoMsg
            else "Channels show their name until you give them a logo. Start the uploader, then open the address it shows from any device on your network.",
            color = Nova.textDim,
            fontSize = 13.sp
        )

        SectionLabel("PLAYBACK")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PlayMode.values().forEach { m ->
                NeonButton(
                    m.label,
                    onClick = { vm.updatePlayback(playback.copy(mode = m)) },
                    selected = playback.mode == m
                )
            }
        }
        Text(playback.mode.hint, color = Nova.textDim, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Max bitrate when the server transcodes",
                color = Nova.text,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            NeonButton("−", onClick = { vm.updatePlayback(playback.copy(maxMbps = stepBitrate(playback.maxMbps, -1))) })
            Text(
                "${playback.maxMbps} Mbps",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(120.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            NeonButton("+", onClick = { vm.updatePlayback(playback.copy(maxMbps = stepBitrate(playback.maxMbps, 1))) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NeonButton(
                playback.dtsMode.label,
                onClick = {
                    val all = app.novatv.DtsMode.values()
                    vm.updatePlayback(playback.copy(dtsMode = all[(playback.dtsMode.ordinal + 1) % all.size]))
                },
                accent = Nova.cyan
            )
            Text(playback.dtsMode.hint, color = Nova.textDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            NeonButton(
                "SUBTITLES: " + langLabel(playback.subLang, "OFF"),
                onClick = { vm.updatePlayback(playback.copy(subLang = nextLang(playback.subLang))) },
                accent = Nova.magenta
            )
            NeonButton(
                "AUDIO: " + langLabel(playback.audioLang, "FILE DEFAULT"),
                onClick = { vm.updatePlayback(playback.copy(audioLang = nextLang(playback.audioLang))) },
                accent = Nova.magenta
            )
        }
        Text(
            "Preferred languages are applied automatically. While watching, press ▶ on the remote to pick tracks.",
            color = Nova.textDim,
            fontSize = 13.sp
        )

        SectionLabel("LIBRARY")
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonButton(if (busy) "SYNCING…" else "SYNC NOW", onClick = { if (!busy) vm.refresh() }, accent = Nova.violet)
            Spacer(Modifier.width(18.dp))
            Text(
                if (status.isNotEmpty()) status else "${channels.size} channels",
                color = Nova.textDim,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(18.dp))
        NeonButton("◀  BACK TO TV", onClick = { vm.back() }, accent = Nova.magenta)
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun ServerRow(s: Server, onToggle: () -> Unit, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val c = serverColor(s.type)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Nova.panel)
            .border(1.dp, c.copy(alpha = if (s.enabled) 0.35f else 0.12f), shape)
            .alpha(if (s.enabled) 1f else 0.55f)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(c.copy(alpha = 0.2f))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(s.type.label.uppercase(), color = c, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(s.baseUrl, color = Nova.textDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        NeonButton(
            if (s.enabled) "ON" else "OFF",
            onClick = onToggle,
            accent = if (s.enabled) Color(0xFF52B54B) else Nova.textDim,
            selected = s.enabled
        )
        Spacer(Modifier.width(10.dp))
        NeonButton("REMOVE", onClick = onRemove, accent = Nova.danger)
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) Nova.panelHi else Nova.panel)
            .border(if (focused) 2.dp else 1.dp, if (focused) Nova.cyan else Color(0x331E2A5A), shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable { onChange(!value) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Nova.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .width(52.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(50))
                .background(if (value) Nova.cyan.copy(alpha = 0.35f) else Color(0x33FFFFFF))
                .padding(3.dp)
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .align(if (value) Alignment.CenterEnd else Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(if (value) Nova.cyan else Nova.textDim)
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Add server
// ------------------------------------------------------------------------------------------------

@Composable
fun AddServerScreen(vm: AppViewModel) {
    val addBusy by vm.addBusy.collectAsState()
    val addError by vm.addError.collectAsState()
    val plexCode by vm.plexCode.collectAsState()

    var type by remember { mutableStateOf(ServerType.JELLYFIN) }
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    BackHandler { vm.back() }

    ScreenFrame {
        Title("ADD SERVER", "CONNECT YOUR LIBRARY")

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ServerType.values().forEach { t ->
                NeonButton(t.label.uppercase(), onClick = { type = t }, accent = serverColor(t), selected = type == t)
            }
        }

        if (type == ServerType.PLEX) {
            NeonButton(
                if (plexCode == null) "LINK WITH PLEX.TV  (RECOMMENDED)" else "RESTART LINK",
                onClick = { if (!addBusy || plexCode != null) vm.startPlexLink() },
                accent = serverColor(ServerType.PLEX)
            )
            val code = plexCode
            if (code != null) CodeCard(code)
            Text("…or enter your server by hand", color = Nova.textDim, fontSize = 14.sp)
            Field("Server URL  (e.g. http://192.168.1.10:32400)", url, { url = it })
            Field("X-Plex-Token", token, { token = it })
            NeonButton("CONNECT", onClick = { if (!addBusy) vm.addPlexManual(url, token) })
        } else {
            Field("Server URL  (e.g. http://192.168.1.10:8096)", url, { url = it })
            Field("Username", user, { user = it })
            Field("Password", pass, { pass = it }, password = true)
            NeonButton("CONNECT", onClick = { if (!addBusy) vm.addEmbyLike(type, url, user, pass) })
        }

        if (addBusy && plexCode == null) {
            Text("Connecting…", color = Nova.cyan, fontSize = 15.sp)
        }
        val err = addError
        if (err != null) {
            Text(err, color = Nova.danger, fontSize = 15.sp)
        }

        Spacer(Modifier.height(10.dp))
        NeonButton("CANCEL", onClick = { vm.back() }, accent = Nova.magenta)
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun CodeCard(code: String) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFF0B0F24))
            .border(1.dp, Brush.horizontalGradient(listOf(Nova.cyan, Nova.magenta)), shape)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("On your phone or PC open  plex.tv/link  and enter this code", color = Nova.text, fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            code,
            style = TextStyle(
                brush = Nova.neon,
                fontSize = 54.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 14.sp,
                fontFamily = FontFamily.Monospace
            )
        )
        Spacer(Modifier.height(6.dp))
        Text("Waiting for you to confirm… servers are added automatically", color = Nova.textDim, fontSize = 13.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, password: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Nova.text,
            focusedBorderColor = Nova.cyan,
            unfocusedBorderColor = Color(0x552AF5FF),
            focusedLabelColor = Nova.cyan,
            unfocusedLabelColor = Nova.textDim,
            cursorColor = Nova.cyan
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
