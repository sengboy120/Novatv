package app.novatv.net

import app.novatv.MediaEntry
import app.novatv.PlayMode
import app.novatv.PlayRequest
import app.novatv.PlaybackSettings
import app.novatv.Server
import app.novatv.ServerType
import app.novatv.StreamInfo
import app.novatv.StreamsInfo
import app.novatv.TrackKind
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/** Language helpers: servers report "tha", "th", "Thai", ... we normalise to 2-letter codes. */
object Langs {
    private val aliases: Map<String, List<String>> = mapOf(
        "th" to listOf("th", "tha", "thai"),
        "en" to listOf("en", "eng", "english"),
        "ja" to listOf("ja", "jpn", "japanese"),
        "ko" to listOf("ko", "kor", "korean"),
        "zh" to listOf("zh", "zho", "chi", "chinese", "mandarin", "cantonese")
    )

    private val names: Map<String, String> = mapOf(
        "th" to "Thai",
        "en" to "English",
        "ja" to "Japanese",
        "ko" to "Korean",
        "zh" to "Chinese"
    )

    /** Languages offered in Settings. "" means off / file default. */
    val choices: List<String> = listOf("", "th", "en", "ja", "ko", "zh")

    /** 2-letter code for a known language, "" otherwise. */
    fun canon(raw: String?): String {
        if (raw == null) return ""
        val l = raw.lowercase().trim()
        for ((k, v) in aliases) {
            if (l in v) return k
        }
        val head = l.substringBefore('-').substringBefore('_')
        for ((k, v) in aliases) {
            if (head in v) return k
        }
        return ""
    }

    fun name(code: String): String = names[code] ?: code

    /** Human readable language name, or "" if unknown / undetermined. */
    fun display(raw: String?): String {
        val c = canon(raw)
        if (c.isNotEmpty()) return name(c)
        if (raw.isNullOrBlank() || raw.lowercase() == "und") return ""
        val d = Locale(raw).displayLanguage
        return if (d.isBlank()) raw else d
    }

    fun matches(code2: String, lang: String, title: String): Boolean {
        if (code2.isEmpty()) return false
        if (canon(lang) == code2) return true
        val t = title.lowercase()
        return (aliases[code2] ?: emptyList()).any { it.length >= 4 && t.contains(it) }
    }
}

object Streams {

    fun fetchInfo(s: Server, e: MediaEntry, clientId: String): StreamsInfo =
        when (s.type) {
            ServerType.PLEX -> PlexApi.info(s, e.itemId, clientId)
            ServerType.JELLYFIN, ServerType.EMBY -> embyInfo(s, e)
        }

    private fun embyInfo(s: Server, e: MediaEntry): StreamsInfo {
        val key = Http.enc(s.token)
        val o = JSONObject(
            Http.get(
                "${s.baseUrl}/Users/${s.userId}/Items/${e.itemId}?api_key=$key",
                mapOf("X-Emby-Token" to s.token, "Accept" to "application/json")
            )
        )
        val src = o.optJSONArray("MediaSources").objs().firstOrNull()
        val rawId = src?.optString("Id", "") ?: ""
        val sourceId = if (rawId.isEmpty()) e.itemId else rawId

        val audio = ArrayList<StreamInfo>()
        val subs = ArrayList<StreamInfo>()
        for (m in src?.optJSONArray("MediaStreams").objs()) {
            val idx = m.optInt("Index", -1)
            if (idx < 0) continue
            val lang = m.optString("Language", "")
            val codec = m.optString("Codec", "")
            val title = m.optString("DisplayTitle", "").ifEmpty { m.optString("Title", "") }
            when (m.optString("Type")) {
                "Audio" -> audio.add(
                    StreamInfo(idx.toString(), TrackKind.AUDIO, lang, title, codec, false, false, "", "")
                )
                "Subtitle" -> subs.add(
                    StreamInfo(
                        key = idx.toString(),
                        kind = TrackKind.SUBTITLE,
                        lang = lang,
                        title = title,
                        codec = codec,
                        external = m.optBoolean("IsExternal", false),
                        isText = m.optBoolean("IsTextSubtitleStream", false),
                        // the server converts any text subtitle (embedded or external) to WebVTT for us
                        sidecarUrl = "${s.baseUrl}/Videos/${e.itemId}/$sourceId/Subtitles/$idx/Stream.vtt?api_key=$key",
                        mime = "text/vtt"
                    )
                )
            }
        }
        return StreamsInfo(sourceId, audio, subs)
    }

    fun pickAudio(info: StreamsInfo?, lang: String): String? {
        if (info == null || lang.isEmpty()) return null
        return info.audio.firstOrNull { Langs.matches(lang, it.lang, it.title) }?.key
    }

    fun pickSub(info: StreamsInfo?, lang: String): String? {
        if (info == null || lang.isEmpty()) return null
        return info.subs.firstOrNull { Langs.matches(lang, it.lang, it.title) }?.key
    }

    /**
     * @param audioKey explicit audio stream chosen from the menu (null = use the preferred language)
     * @param subKey explicit subtitle stream chosen from the menu ("" = off, null = use preferred language)
     */
    fun build(
        s: Server,
        e: MediaEntry,
        info: StreamsInfo?,
        p: PlaybackSettings,
        offsetMs: Long,
        clientId: String,
        forceDirect: Boolean,
        audioKey: String?,
        subKey: String?
    ): PlayRequest {
        if (forceDirect || p.mode == PlayMode.DIRECT) {
            // Embedded tracks are handled by ExoPlayer itself; only external subtitle files need to be side-loaded.
            val side = info?.subs.orEmpty().filter { it.external && it.isText && it.sidecarUrl.isNotEmpty() }
            return PlayRequest(e.streamUrl, null, side, offsetMs, false)
        }

        val aKey: String? = audioKey ?: pickAudio(info, p.audioLang)
        val sKey: String? = when {
            subKey == null -> pickSub(info, p.subLang)
            subKey.isEmpty() -> null
            else -> subKey
        }
        val always = p.mode == PlayMode.TRANSCODE

        return if (s.type == ServerType.PLEX) {
            val sb = StringBuilder("${s.baseUrl}/video/:/transcode/universal/start.m3u8?")
            sb.append("path=").append(Http.enc("/library/metadata/${e.itemId}"))
            sb.append("&mediaIndex=0&partIndex=0&protocol=hls&fastSeek=1&directPlay=0")
            sb.append("&directStream=").append(if (always) "0" else "1")
            sb.append("&maxVideoBitrate=").append(p.maxMbps * 1000)
            if (always) sb.append("&videoResolution=1920x1080&videoQuality=100")
            sb.append("&subtitleSize=100&audioBoost=100&copyts=1")
            sb.append("&offset=").append(offsetMs / 1000L)
            sb.append("&session=").append(UUID.randomUUID())
            sb.append("&X-Plex-Platform=Android&X-Plex-Product=NovaTV&X-Plex-Version=1.0")
            sb.append("&X-Plex-Client-Identifier=").append(Http.enc(clientId))
            if (aKey != null) sb.append("&audioStreamID=").append(aKey)
            if (sKey != null) sb.append("&subtitleStreamID=").append(sKey).append("&subtitles=burn")
            sb.append("&X-Plex-Token=").append(Http.enc(s.token))
            // Plex starts the playlist at "offset", so the player must not seek again.
            PlayRequest(sb.toString(), "application/x-mpegURL", emptyList(), 0L, true)
        } else {
            val src = info?.sourceId ?: e.itemId
            val sb = StringBuilder("${s.baseUrl}/Videos/${e.itemId}/master.m3u8?")
            sb.append("MediaSourceId=").append(Http.enc(src))
            sb.append("&DeviceId=").append(Http.enc(clientId))
            sb.append("&PlaySessionId=").append(UUID.randomUUID())
            sb.append("&VideoCodec=").append(if (always) "h264" else "h264,hevc")
            sb.append("&AudioCodec=").append(if (always) "aac" else "aac,ac3,eac3")
            sb.append("&MaxStreamingBitrate=").append(p.maxMbps * 1_000_000L)
            sb.append("&TranscodingMaxAudioChannels=").append(if (always) 2 else 6)
            sb.append("&SegmentContainer=ts&MinSegments=1&BreakOnNonKeyFrames=true")
            if (always) {
                sb.append("&AllowVideoStreamCopy=false&AllowAudioStreamCopy=false&EnableAutoStreamCopy=false")
            }
            if (aKey != null) sb.append("&AudioStreamIndex=").append(aKey)
            if (sKey != null) sb.append("&SubtitleStreamIndex=").append(sKey).append("&SubtitleMethod=Encode")
            sb.append("&api_key=").append(Http.enc(s.token))
            PlayRequest(sb.toString(), "application/x-mpegURL", emptyList(), offsetMs, true)
        }
    }
}
