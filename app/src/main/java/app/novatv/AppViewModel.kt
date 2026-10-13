package app.novatv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.novatv.net.EmbyLike
import app.novatv.net.PlexApi
import app.novatv.net.PlexAuth
import app.novatv.net.Streams
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = Store(app)

    init {
        Logos.init(app)
    }

    // ---- channel-logo uploader (LAN web page) ----------------------------------------------------

    private var logoServer: LogoServer? = null
    private val _logoUrl = MutableStateFlow<String?>(null)
    /** "http://192.168.x.y:8080" while the uploader is running, else null. */
    val logoUrl: StateFlow<String?> = _logoUrl.asStateFlow()
    private val _logoMsg = MutableStateFlow("")
    val logoMsg: StateFlow<String> = _logoMsg.asStateFlow()

    fun startLogoServer() {
        if (logoServer != null) return
        val ip = LogoServer.localIp()
        if (ip == null) {
            _logoMsg.value = "No Wi-Fi / Ethernet address found - connect the TV to your network first."
            return
        }
        for (port in 8080..8089) {
            try {
                val srv = LogoServer(port) { _channels.value }
                srv.start(5000, false)
                logoServer = srv
                _logoUrl.value = "http://$ip:$port"
                _logoMsg.value = ""
                return
            } catch (e: Exception) {
                // port busy - try the next one
            }
        }
        _logoMsg.value = "Could not start the uploader (ports 8080-8089 are busy)."
    }

    fun stopLogoServer() {
        try {
            logoServer?.stop()
        } catch (e: Exception) {
            // ignore
        }
        logoServer = null
        _logoUrl.value = null
    }

    override fun onCleared() {
        stopLogoServer()
        super.onCleared()
    }

    private val _servers = MutableStateFlow(store.loadServers())
    val servers: StateFlow<List<Server>> = _servers.asStateFlow()

    private val _settings = MutableStateFlow(store.loadSettings())
    val settings: StateFlow<ChannelSettings> = _settings.asStateFlow()

    private val _playback = MutableStateFlow(store.loadPlayback())
    val playback: StateFlow<PlaybackSettings> = _playback.asStateFlow()

    private val infoCache = HashMap<String, StreamsInfo>()

    private val _entries = MutableStateFlow<List<MediaEntry>>(emptyList())

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    private val _currentChannelId = MutableStateFlow(store.lastChannelId)
    val currentChannelId: StateFlow<String?> = _currentChannelId.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _screen = MutableStateFlow(if (store.loadServers().isEmpty()) Screen.SETTINGS else Screen.PLAYER)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    /** Bumped whenever the player should (re)tune to whatever is on air now. */
    private val _tuneNonce = MutableStateFlow(0)
    val tuneNonce: StateFlow<Int> = _tuneNonce.asStateFlow()

    private val _plexCode = MutableStateFlow<String?>(null)
    val plexCode: StateFlow<String?> = _plexCode.asStateFlow()

    private val _addError = MutableStateFlow<String?>(null)
    val addError: StateFlow<String?> = _addError.asStateFlow()

    private val _addBusy = MutableStateFlow(false)
    val addBusy: StateFlow<Boolean> = _addBusy.asStateFlow()

    /** Used to jump past a file that failed to play. */
    var skewMs: Long = 0L
        private set

    private var plexJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val cached = store.loadEntries()
            if (cached.isNotEmpty()) {
                _entries.value = cached
                rebuild(retune = true)
            }
            if (_servers.value.isNotEmpty()) refreshInternal()
        }
    }

    // ---- time / channels -----------------------------------------------------------------------

    fun now(): Long = System.currentTimeMillis() + skewMs

    fun currentChannel(): Channel? {
        val id = _currentChannelId.value
        return _channels.value.firstOrNull { it.id == id }
    }

    private fun rebuild(retune: Boolean) {
        val chs = ChannelBuilder.build(_entries.value, _settings.value)
        _channels.value = chs
        val cur = _currentChannelId.value
        if (chs.isNotEmpty() && (cur == null || chs.none { it.id == cur })) {
            _currentChannelId.value = chs[0].id
        }
        if (retune) bump()
    }

    private fun bump() {
        _tuneNonce.value = _tuneNonce.value + 1
    }

    fun tune(channelId: String) {
        _currentChannelId.value = channelId
        store.lastChannelId = channelId
        bump()
    }

    fun channelStep(delta: Int) {
        val chs = _channels.value
        if (chs.isEmpty()) return
        val cur = _currentChannelId.value
        var i = chs.indexOfFirst { it.id == cur }
        if (i < 0) i = 0
        tune(chs[Math.floorMod(i + delta, chs.size)].id)
    }

    /** Called when playback of the current program ends: just look at the schedule again. */
    fun retune() = bump()

    /** Called when a file can't be played: jump to whatever comes next on this channel. */
    fun skipCurrentProgram() {
        val ch = currentChannel() ?: return
        val p = Scheduler.programAt(ch, now()) ?: return
        skewMs += (p.endMs - now()) + 1000L
        bump()
    }

    // ---- screens -------------------------------------------------------------------------------

    fun openSettings() {
        _screen.value = Screen.SETTINGS
    }

    fun openAddServer() {
        _addError.value = null
        _screen.value = Screen.ADD_SERVER
    }

    fun back() {
        when (_screen.value) {
            Screen.ADD_SERVER -> {
                cancelPlexLink()
                _screen.value = Screen.SETTINGS
            }
            Screen.SETTINGS -> _screen.value = Screen.PLAYER
            Screen.PLAYER -> Unit
        }
    }

    // ---- library -------------------------------------------------------------------------------

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { refreshInternal() }
    }

    private fun refreshInternal() {
        if (_busy.value) return
        _busy.value = true
        try {
            val wasEmpty = _channels.value.isEmpty()
            val all = ArrayList<MediaEntry>()
            val failed = HashSet<String>()
            val errors = ArrayList<String>()
            val clientId = store.clientId

            for (s in _servers.value) {
                _status.value = "Syncing ${s.name}…"
                try {
                    val list = when (s.type) {
                        ServerType.PLEX -> PlexApi.fetch(s, clientId)
                        ServerType.JELLYFIN, ServerType.EMBY -> EmbyLike.fetch(s)
                    }
                    all.addAll(list)
                } catch (e: Exception) {
                    failed.add(s.id)
                    errors.add("${s.name}: ${e.message}")
                }
            }

            // keep the cached titles of any server that was unreachable this time
            if (failed.isNotEmpty()) all.addAll(_entries.value.filter { it.serverId in failed })

            if (all.isNotEmpty()) {
                val d = ChannelBuilder.dedupe(all)
                _entries.value = d
                store.saveEntries(d)
                rebuild(retune = wasEmpty)
                _status.value = "${d.size} titles · ${_channels.value.size} channels" +
                    (if (errors.isNotEmpty()) " · ${errors.size} server(s) unreachable" else "")
            } else {
                _status.value = if (errors.isNotEmpty()) errors.joinToString("; ") else "No media found on your servers"
            }
        } finally {
            _busy.value = false
        }
    }

    fun updateSettings(s: ChannelSettings) {
        _settings.value = s
        store.saveSettings(s)
        viewModelScope.launch(Dispatchers.Default) { rebuild(retune = false) }
    }

    // ---- playback / tracks ---------------------------------------------------------------------

    fun updatePlayback(p: PlaybackSettings) {
        _playback.value = p
        store.savePlayback(p)
    }

    /** Remember the language the viewer just picked in the track menu ("" = off / default). */
    fun rememberLang(kind: TrackKind, code: String) {
        val cur = _playback.value
        val next = if (kind == TrackKind.SUBTITLE) cur.copy(subLang = code) else cur.copy(audioLang = code)
        if (next != cur) updatePlayback(next)
    }

    /**
     * Works out how to play [e]: direct file, or a server-side HLS transcode, plus any
     * external subtitle files. Asks the server for the item's audio/subtitle streams first.
     */
    suspend fun prepare(
        e: MediaEntry,
        offsetMs: Long,
        forceDirect: Boolean,
        audioKey: String?,
        subKey: String?,
        forceTranscode: Boolean = false
    ): Prepared = withContext(Dispatchers.IO) {
        val saved = _playback.value
        // direct play failed for this title: let the server convert whatever the Shield couldn't handle
        val p = if (forceTranscode && saved.mode == PlayMode.DIRECT) saved.copy(mode = PlayMode.SMART) else saved
        val server = _servers.value.firstOrNull { it.id == e.serverId }
        if (server == null) {
            return@withContext Prepared(PlayRequest(e.streamUrl, null, emptyList(), offsetMs, false), null)
        }
        var info: StreamsInfo? = synchronized(infoCache) { infoCache[e.id] }
        if (info == null) {
            info = try {
                Streams.fetchInfo(server, e, store.clientId)
            } catch (ex: Exception) {
                null
            }
            val fetched = info
            if (fetched != null) {
                synchronized(infoCache) {
                    if (infoCache.size > 300) infoCache.clear()
                    infoCache[e.id] = fetched
                }
            }
        }
        Prepared(Streams.build(server, e, info, p, offsetMs, store.clientId, forceDirect, audioKey, subKey), info)
    }

    // ---- servers -------------------------------------------------------------------------------

    private fun putServer(s: Server) {
        val list = _servers.value.filter { it.id != s.id } + s
        _servers.value = list
        store.saveServers(list)
    }

    fun removeServer(id: String) {
        val list = _servers.value.filter { it.id != id }
        _servers.value = list
        store.saveServers(list)
        viewModelScope.launch(Dispatchers.Default) {
            val remaining = _entries.value.filter { it.serverId != id }
            _entries.value = remaining
            store.saveEntries(remaining)
            rebuild(retune = true)
        }
    }

    private fun launchAdd(block: () -> Server) {
        _addBusy.value = true
        _addError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = block()
                putServer(s)
                _screen.value = Screen.SETTINGS
                refreshInternal()
            } catch (e: Exception) {
                _addError.value = e.message ?: "Could not connect"
            } finally {
                _addBusy.value = false
            }
        }
    }

    fun addEmbyLike(type: ServerType, url: String, user: String, pass: String) {
        launchAdd { EmbyLike.login(type, url, user, pass, store.clientId) }
    }

    fun addPlexManual(url: String, token: String) {
        launchAdd { PlexApi.verify(url, token.trim(), store.clientId) }
    }

    fun startPlexLink() {
        plexJob?.cancel()
        _addError.value = null
        plexJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val cid = store.clientId
                val pin = PlexAuth.createPin(cid)
                _plexCode.value = pin.code
                val deadline = System.currentTimeMillis() + 5 * 60_000L
                var token: String? = null
                while (token == null && System.currentTimeMillis() < deadline) {
                    delay(2000)
                    token = PlexAuth.pollPin(pin, cid)
                }
                if (token == null) {
                    _addError.value = "Code expired. Please try again."
                    return@launch
                }
                _addBusy.value = true
                val found = PlexAuth.discoverServers(token, cid)
                if (found.isEmpty()) {
                    _addError.value = "No reachable Plex server was found on this account."
                } else {
                    found.forEach { putServer(it) }
                    _screen.value = Screen.SETTINGS
                    refreshInternal()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _addError.value = e.message ?: "Plex sign-in failed"
            } finally {
                _plexCode.value = null
                _addBusy.value = false
            }
        }
    }

    fun cancelPlexLink() {
        plexJob?.cancel()
        _plexCode.value = null
        _addBusy.value = false
    }
}
