package app.novatv.net

import app.novatv.EntryKind
import app.novatv.MediaEntry
import app.novatv.Server
import app.novatv.ServerType
import app.novatv.StreamInfo
import app.novatv.StreamsInfo
import app.novatv.TrackKind
import app.novatv.VideoInfo
import okhttp3.FormBody
import org.json.JSONArray
import org.json.JSONObject

object PlexApi {

    private fun headers(token: String, clientId: String): Map<String, String> = mapOf(
        "X-Plex-Token" to token,
        "Accept" to "application/json",
        "X-Plex-Client-Identifier" to clientId,
        "X-Plex-Product" to "BenditTV",
        "X-Plex-Version" to "1.0"
    )

    /** Manual setup: server URL + token. Returns a Server with its friendly name. */
    fun verify(rawUrl: String, token: String, clientId: String): Server {
        val base = Http.normalizeUrl(rawUrl)
        val root = JSONObject(Http.get("$base/", headers(token, clientId)))
        val name = root.optJSONObject("MediaContainer")?.optString("friendlyName", "Plex") ?: "Plex"
        val id = "plex-manual-${Math.abs(base.hashCode())}"
        return Server(id = id, type = ServerType.PLEX, name = name.ifEmpty { "Plex" }, baseUrl = base, token = token)
    }

    private fun paged(s: Server, path: String, h: Map<String, String>): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        var start = 0
        val size = 500
        val sep = if (path.contains('?')) "&" else "?"
        while (true) {
            val url = "${s.baseUrl}$path${sep}X-Plex-Container-Start=$start&X-Plex-Container-Size=$size"
            val mc = JSONObject(Http.get(url, h)).getJSONObject("MediaContainer")
            val page = mc.optJSONArray("Metadata").objs()
            out.addAll(page)
            start += page.size
            val total = mc.optInt("totalSize", mc.optInt("size", start))
            if (page.isEmpty() || start >= total) break
        }
        return out
    }

    /** Video, audio and subtitle streams of one item (track menus, subtitle files, transcode picks). */
    fun info(s: Server, itemId: String, clientId: String): StreamsInfo {
        val tokenQ = "X-Plex-Token=${Http.enc(s.token)}"
        val root = JSONObject(Http.get("${s.baseUrl}/library/metadata/$itemId", headers(s.token, clientId)))
        val md = root.getJSONObject("MediaContainer").optJSONArray("Metadata").objs().firstOrNull()
        val media = md?.optJSONArray("Media").objs().firstOrNull()
        val part = media?.optJSONArray("Part").objs().firstOrNull()
        val textCodecs = setOf("srt", "subrip", "ass", "ssa", "webvtt", "vtt", "mov_text", "text")
        val audio = ArrayList<StreamInfo>()
        val subs = ArrayList<StreamInfo>()
        var video: VideoInfo? = null
        for (st in part?.optJSONArray("Stream").objs()) {
            val id = st.optInt("id", -1)
            if (id < 0) continue
            val lang = st.optString("languageCode", "").ifEmpty { st.optString("language", "") }
            val display = st.optString("displayTitle", "")
            val extended = st.optString("extendedDisplayTitle", "")
            val custom = st.optString("title", "")
            val title = display.ifEmpty { custom }
            val codec = st.optString("codec", "")
            when (st.optInt("streamType", 0)) {
                1 -> if (video == null) {
                    var fps = st.optDouble("frameRate", 0.0)
                    if (fps <= 0.0) fps = parseFrameRate(media?.optString("videoFrameRate", "") ?: "")
                    val hdrText = display + " " + extended + " " + st.optString("colorSpace", "")
                    video = VideoInfo(
                        width = st.optInt("width", media?.optInt("width", 0) ?: 0),
                        height = st.optInt("height", media?.optInt("height", 0) ?: 0),
                        fps = fps.toFloat(),
                        codec = codec,
                        hdr = Labels.hdr(hdrText, st.optString("colorTrc", ""), st.optBoolean("DOVIPresent", false)),
                        bitDepth = st.optInt("bitDepth", 0),
                        bitrateKbps = st.optInt("bitrate", 0)
                    )
                }
                2 -> audio.add(
                    StreamInfo(
                        key = id.toString(),
                        kind = TrackKind.AUDIO,
                        lang = lang,
                        title = (display + " " + extended).trim(),
                        codec = codec,
                        external = false,
                        isText = false,
                        sidecarUrl = "",
                        mime = "",
                        customTitle = custom,
                        profile = st.optString("profile", ""),
                        channels = st.optInt("channels", 0),
                        layout = st.optString("audioChannelLayout", ""),
                        bitrateKbps = st.optInt("bitrate", 0),
                        forced = st.optBoolean("forced", false),
                        isDefault = st.optBoolean("default", false),
                        atmos = display.contains("atmos", true) || extended.contains("atmos", true)
                    )
                )
                3 -> {
                    val key = st.optString("key", "")
                    val isText = codec.lowercase() in textCodecs
                    val mime = when (codec.lowercase()) {
                        "ass", "ssa" -> "text/x-ssa"
                        "webvtt", "vtt" -> "text/vtt"
                        else -> "application/x-subrip"
                    }
                    // external files have a key; embedded text subtitles can usually be pulled as SRT too
                    val url = when {
                        key.isNotEmpty() -> "${s.baseUrl}$key" + (if (key.contains('?')) "&" else "?") + tokenQ
                        isText -> "${s.baseUrl}/library/streams/$id?encoding=utf-8&format=srt&$tokenQ"
                        else -> ""
                    }
                    subs.add(
                        StreamInfo(
                            key = id.toString(),
                            kind = TrackKind.SUBTITLE,
                            lang = lang,
                            title = title,
                            codec = codec,
                            external = key.isNotEmpty(),
                            isText = isText,
                            sidecarUrl = url,
                            mime = mime,
                            customTitle = custom,
                            forced = st.optBoolean("forced", false),
                            isDefault = st.optBoolean("default", false)
                        )
                    )
                }
            }
        }
        return StreamsInfo(itemId, audio, subs, video)
    }

    private fun parseFrameRate(raw: String): Double {
        val r = raw.lowercase().trim()
        return when {
            r.isEmpty() -> 0.0
            r == "ntsc" -> 29.97
            r == "pal" -> 25.0
            else -> r.trimEnd('p', 'i').toDoubleOrNull() ?: 0.0
        }
    }

    private fun bestRating(m: JSONObject): Float {
        val a = m.optDouble("audienceRating", 0.0)
        val r = m.optDouble("rating", 0.0)
        return (if (a > 0.0) a else r).toFloat()
    }

    private fun tags(arr: JSONArray?): List<String> =
        arr.objs().map { it.optString("tag", "") }.filter { it.isNotEmpty() }

    private fun partKey(m: JSONObject): String =
        m.optJSONArray("Media").objs().firstOrNull()
            ?.optJSONArray("Part").objs().firstOrNull()
            ?.optString("key", "") ?: ""

    fun fetch(s: Server, clientId: String): List<MediaEntry> {
        val h = headers(s.token, clientId)
        val tokenQ = "X-Plex-Token=${Http.enc(s.token)}"
        // the raw-file request identifies the client the same way the transcoder request does
        val fileQ = tokenQ + "&X-Plex-Client-Identifier=" + Http.enc(clientId) +
            "&X-Plex-Product=BenditTV&X-Plex-Platform=Android&X-Plex-Version=1.0"
        val sections = JSONObject(Http.get("${s.baseUrl}/library/sections", h))
            .getJSONObject("MediaContainer").optJSONArray("Directory").objs()

        val out = ArrayList<MediaEntry>()

        for (sec in sections) {
            val key = sec.optString("key")
            if (key.isEmpty()) continue
            when (sec.optString("type")) {
                "movie" -> {
                    for (m in paged(s, "/library/sections/$key/all?type=1", h)) {
                        val part = partKey(m)
                        if (part.isEmpty()) continue
                        val thumb = m.optString("thumb", "")
                        out.add(
                            MediaEntry(
                                id = "${s.id}:${m.optString("ratingKey")}",
                                serverId = s.id,
                                itemId = m.optString("ratingKey"),
                                kind = EntryKind.MOVIE,
                                title = m.optString("title", "Untitled"),
                                subtitle = "",
                                series = "",
                                network = m.optString("studio", ""),
                                genres = tags(m.optJSONArray("Genre")),
                                year = m.optInt("year", 0),
                                durationMs = m.optLong("duration", 0L),
                                thumbUrl = if (thumb.isEmpty()) "" else "${s.baseUrl}$thumb?$tokenQ",
                                streamUrl = "${s.baseUrl}$part?$fileQ",
                                overview = m.optString("summary", "").take(420),
                                rating = bestRating(m),
                                mpaa = m.optString("contentRating", "")
                            )
                        )
                    }
                }
                "show" -> {
                    val showInfo = HashMap<String, Pair<List<String>, String>>()
                    for (sh in paged(s, "/library/sections/$key/all?type=2", h)) {
                        showInfo[sh.optString("ratingKey")] =
                            Pair(tags(sh.optJSONArray("Genre")), sh.optString("studio", ""))
                    }
                    for (e in paged(s, "/library/sections/$key/all?type=4", h)) {
                        val part = partKey(e)
                        if (part.isEmpty()) continue
                        val info = showInfo[e.optString("grandparentRatingKey")]
                        val season = e.optInt("parentIndex", 0)
                        val ep = e.optInt("index", 0)
                        val code = if (season > 0 || ep > 0) String.format("S%02dE%02d", season, ep) else ""
                        val thumb = e.optString("thumb", "").ifEmpty { e.optString("grandparentThumb", "") }
                        out.add(
                            MediaEntry(
                                id = "${s.id}:${e.optString("ratingKey")}",
                                serverId = s.id,
                                itemId = e.optString("ratingKey"),
                                kind = EntryKind.EPISODE,
                                title = e.optString("title", "Episode"),
                                subtitle = code,
                                series = e.optString("grandparentTitle", ""),
                                network = info?.second ?: "",
                                genres = info?.first ?: emptyList(),
                                year = e.optInt("year", 0),
                                durationMs = e.optLong("duration", 0L),
                                thumbUrl = if (thumb.isEmpty()) "" else "${s.baseUrl}$thumb?$tokenQ",
                                streamUrl = "${s.baseUrl}$part?$fileQ",
                                overview = e.optString("summary", "").take(420),
                                rating = bestRating(e),
                                mpaa = e.optString("contentRating", "")
                            )
                        )
                    }
                }
            }
        }
        return out
    }

    /**
     * Plex's transcoder takes the audio / subtitle choice from the part's saved selection (the same call Plex Web makes),
     * so set it before the transcode session starts. [subKey] null = no subtitles.
     */
    fun selectStreams(s: Server, itemId: String, partId: String, audioKey: String?, subKey: String?, clientId: String): String {
        val q = StringBuilder("${s.baseUrl}/library/parts/$partId?allParts=1")
        if (audioKey != null) q.append("&audioStreamID=").append(Http.enc(audioKey))
        q.append("&subtitleStreamID=").append(Http.enc(subKey ?: "0"))
        q.append("&X-Plex-Token=").append(Http.enc(s.token))
        try {
            Http.put(q.toString(), headers(s.token, clientId))
        } catch (ex: Exception) {
            return "selection NOT sent | " + (ex.message ?: "error")
        }
        // read it back so a refusal is visible instead of silent
        return try {
            val root = JSONObject(Http.get("${s.baseUrl}/library/metadata/$itemId", headers(s.token, clientId)))
            val md = root.getJSONObject("MediaContainer").optJSONArray("Metadata").objs().firstOrNull()
            val part = md?.optJSONArray("Media").objs().firstOrNull()?.optJSONArray("Part").objs().firstOrNull()
            var selA: String? = null
            var selS: String? = null
            for (st in part?.optJSONArray("Stream").objs()) {
                if (!st.optBoolean("selected", false)) continue
                when (st.optInt("streamType", 0)) {
                    2 -> selA = st.optInt("id", -1).toString()
                    3 -> selS = st.optInt("id", -1).toString()
                }
            }
            val okA = audioKey == null || selA == audioKey
            val okS = if (subKey == null) selS == null else selS == subKey
            if (okA && okS) "selection saved OK | audio $audioKey  sub ${subKey ?: "off"}"
            else "server kept other | has audio $selA  sub ${selS ?: "off"} | wanted audio $audioKey  sub ${subKey ?: "off"}"
        } catch (ex: Exception) {
            "selection sent (not verified)"
        }
    }

    /** What the server says the running transcode of this client is using (from /status/sessions). */
    fun sessionInfo(s: Server, clientId: String): String {
        val root = JSONObject(Http.get("${s.baseUrl}/status/sessions", headers(s.token, clientId)))
        val list = root.getJSONObject("MediaContainer").optJSONArray("Metadata").objs()
        if (list.isEmpty()) return "no running session on server"
        val mine = list.firstOrNull { it.optJSONObject("Player")?.optString("machineIdentifier", "") == clientId } ?: list.first()
        val ts = mine.optJSONObject("TranscodeSession")
        val part = mine.optJSONArray("Media").objs().firstOrNull()?.optJSONArray("Part").objs().firstOrNull()
        var a = "?"
        var sub = "off"
        for (st in part?.optJSONArray("Stream").objs()) {
            if (!st.optBoolean("selected", false)) continue
            val tag = st.optInt("id", -1).toString() + " " + st.optString("languageCode", st.optString("language", ""))
            when (st.optInt("streamType", 0)) {
                2 -> a = tag
                3 -> sub = tag
            }
        }
        val dec = if (ts == null) "no transcode info"
        else "audio " + ts.optString("audioDecision", "?") + "  sub " + ts.optString("subtitleDecision", "?") + "  video " + ts.optString("videoDecision", "?")
        return "server session uses audio $a | sub $sub | $dec"
    }
}

/** plex.tv/link style sign-in so nobody has to type a token on a TV. */
object PlexAuth {
    private const val PLEX_TV = "https://plex.tv"

    class Pin(val id: Long, val code: String)

    private fun h(clientId: String, token: String? = null): Map<String, String> {
        val m = mutableMapOf(
            "Accept" to "application/json",
            "X-Plex-Client-Identifier" to clientId,
            "X-Plex-Product" to "BenditTV",
            "X-Plex-Version" to "1.0",
            "X-Plex-Device-Name" to "Bendit TV (Android TV)",
            "X-Plex-Platform" to "Android"
        )
        if (token != null) m["X-Plex-Token"] = token
        return m
    }

    fun createPin(clientId: String): Pin {
        val body = FormBody.Builder().add("strong", "false").build()
        val o = JSONObject(Http.post("$PLEX_TV/api/v2/pins", body, h(clientId)))
        return Pin(o.getLong("id"), o.getString("code"))
    }

    /** Returns the auth token once the user has entered the code at plex.tv/link, else null. */
    fun pollPin(pin: Pin, clientId: String): String? {
        val o = JSONObject(Http.get("$PLEX_TV/api/v2/pins/${pin.id}?code=${pin.code}", h(clientId)))
        return if (o.isNull("authToken")) null else o.getString("authToken")
    }
    /** Finds every Plex server the account can reach and picks a working address for each. */
    fun discoverServers(token: String, clientId: String): List<Server> {
        val arr = JSONArray(Http.get("$PLEX_TV/api/v2/resources?includeHttps=1&includeRelay=1", h(clientId, token)))
        val result = ArrayList<Server>()
        for (r in arr.objs()) {
            if (!r.optString("provides").contains("server")) continue
            val access = if (r.isNull("accessToken")) token else r.getString("accessToken")
            val ordered = r.optJSONArray("connections").objs().sortedWith(
                compareBy<JSONObject>(
                    { if (it.optBoolean("local")) 0 else 1 },
                    { if (it.optBoolean("relay")) 1 else 0 }
                )
            )
            var chosen: String? = null
            for (c in ordered) {
                val uri = c.optString("uri").trimEnd('/')
                if (uri.isEmpty()) continue
                if (Http.ping("$uri/identity", h(clientId, access))) {
                    chosen = uri
                    break
                }
            }
            if (chosen == null) continue
            val name = r.optString("name", "Plex")
            val cid = r.optString("clientIdentifier", name)
            result.add(
                Server(
                    id = "plex-$cid",
                    type = ServerType.PLEX,
                    name = name,
                    baseUrl = chosen,
                    token = access
                )
            )
        }
        return result
    }
}
