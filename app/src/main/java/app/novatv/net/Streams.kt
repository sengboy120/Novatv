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
import app.novatv.VideoInfo
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

    private val textSubCodecs = setOf("srt", "subrip", "ass", "ssa", "webvtt", "vtt", "mov_text", "text", "subviewer", "microdvd")
    private val coverCodecs = setOf("mjpeg", "png", "bmp", "gif", "jpeg", "jpg")

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
        var video: VideoInfo? = null
        for (m in src?.optJSONArray("MediaStreams").objs()) {
            val idx = m.optInt("Index", -1)
            if (idx < 0) continue
            val lang = m.optString("Language", "")
            val codec = m.optString("Codec", "")
            val display = m.optString("DisplayTitle", "")
            val custom = m.optString("Title", "")
            val title = display.ifEmpty { custom }
            when (m.optString("Type")) {
                "Video" -> {
                    if (video == null && codec.lowercase() !in coverCodecs && m.optInt("Width", 0) > 0) {
                        var fps = m.optDouble("RealFrameRate", 0.0)
                        if (fps <= 0.0) fps = m.optDouble("AverageFrameRate", 0.0)
                        val hdrText = listOf(
                            m.optString("VideoRangeType", ""),
                            m.optString("VideoRange", ""),
                            m.optString("ExtendedVideoType", ""),
                            m.optString("ExtendedVideoSubType", ""),
                            m.optString("VideoDoViTitle", ""),
                            display
                        ).joinToString(" ")
                        video = VideoInfo(
                            width = m.optInt("Width", 0),
                            height = m.optInt("Height", 0),
                            fps = fps.toFloat(),
                            codec = codec,
                            hdr = Labels.hdr(hdrText, m.optString("ColorTransfer", ""), m.has("DvProfile")),
                            bitDepth = m.optInt("BitDepth", 0),
                            bitrateKbps = m.optInt("BitRate", 0) / 1000
                        )
                    }
                }
                "Audio" -> {
                    val spatial = m.optString("AudioSpatialFormat", "")
                    audio.add(
                        StreamInfo(
                            key = idx.toString(),
                            kind = TrackKind.AUDIO,
                            lang = lang,
                            title = title,
                            codec = codec,
                            external = m.optBoolean("IsExternal", false),
                            isText = false,
                            sidecarUrl = "",
                            mime = "",
                            customTitle = custom,
                            profile = (m.optString("Profile", "") + " " + (if (spatial.equals("none", true)) "" else spatial)).trim(),
                            channels = m.optInt("Channels", 0),
                            layout = m.optString("ChannelLayout", ""),
                            bitrateKbps = m.optInt("BitRate", 0) / 1000,
                            forced = m.optBoolean("IsForced", false),
                            isDefault = m.optBoolean("IsDefault", false),
                            atmos = spatial.contains("atmos", true) || display.contains("atmos", true)
                        )
                    )
                }
                "Subtitle" -> subs.add(
                    StreamInfo(
                        key = idx.toString(),
                        kind = TrackKind.SUBTITLE,
                        lang = lang,
                        title = title,
                        codec = codec,
                        external = m.optBoolean("IsExternal", false),
                        isText = m.optBoolean("IsTextSubtitleStream", codec.lowercase() in textSubCodecs),
                        // the server converts any text subtitle (embedded or external) to WebVTT for us
                        sidecarUrl = "${s.baseUrl}/Videos/${e.itemId}/$sourceId/Subtitles/$idx/Stream.vtt?api_key=$key",
                        mime = "text/vtt",
                        customTitle = custom,
                        forced = m.optBoolean("IsForced", false),
                        isDefault = m.optBoolean("IsDefault", false)
                    )
                )
            }
        }
        return StreamsInfo(sourceId, audio, subs, video)
    }

    fun pickAudio(info: StreamsInfo?, lang: String): String? {
        if (info == null || lang.isEmpty()) return null
        return info.audio.firstOrNull { Langs.matches(lang, it.lang, it.title) }?.key
    }

    fun pickSub(info: StreamsInfo?, lang: String): String? {
        if (info == null || lang.isEmpty()) return null
        val m = info.subs.filter { Langs.matches(lang, it.lang, it.title) }
        // prefer a full (non-forced) text track over image subs / forced-only tracks
        return (m.firstOrNull { !it.forced && it.isText } ?: m.firstOrNull { !it.forced } ?: m.firstOrNull())?.key
    }

    /**
     * @param audioKey explicit audio stream chosen from the menu (null = use the preferred language)
     * @param subKey explicit subtitle stream chosen from the menu ("" = off, null = use preferred language)
     */
    /** True for DTS-HD MA / HRA / DTS:X (not for the plain DTS core). */
    fun isDtsHd(a: StreamInfo): Boolean {
        val c = a.codec.lowercase()
        if (!(c.startsWith("dts") || c == "dca")) return false
        val f = Labels.dts(a.profile, a.title)
        return f == "DTS-HD MA" || f == "DTS-HD HRA" || f == "DTS:X"
    }

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
            // Text subtitles are fetched and drawn by the app itself (so position / delay can be adjusted);
            // image subtitles (PGS ...) are decoded by ExoPlayer from the file.
            return PlayRequest(e.streamUrl, null, emptyList(), offsetMs, false)
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
            sb.append("&X-Plex-Platform=Android&X-Plex-Product=BenditTV&X-Plex-Version=1.0")
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


/** Human readable names for codecs, resolutions, HDR types ... used by the track menu and info panel. */
object Labels {

    fun resolution(w: Int, h: Int): String = when {
        w <= 0 && h <= 0 -> ""
        w >= 7000 || h >= 4000 -> "8K"
        w >= 3000 || h >= 1800 -> "4K"
        w >= 1800 || h >= 1000 -> "Full HD"
        w >= 1200 || h >= 700 -> "HD"
        else -> "SD"
    }

    fun fps(f: Float): String {
        if (f <= 0f) return ""
        val r = Math.round(f)
        if (Math.abs(f - r) < 0.005f) return "$r fps"
        val t = String.format(Locale.US, "%.3f", f).trimEnd('0').trimEnd('.')
        return "$t fps"
    }

    /** "Dolby Vision", "HDR10", "HLG", "SDR" ... from whatever descriptive text the server gave us. */
    fun hdr(text: String, transfer: String, dolby: Boolean): String {
        val t = text.lowercase()
        val tr = transfer.lowercase()
        val dv = dolby || t.contains("dovi") || t.contains("dolby vision") || t.contains("dolbyvision")
        val plus = t.contains("hdr10plus") || t.contains("hdr10+")
        val h10Text = t.contains("hdr10")
        val pq = tr.contains("smpte2084") || tr.contains("st2084") || tr == "pq"
        val hlg = t.contains("hlg") || tr.contains("arib") || tr.contains("hlg")
        return when {
            dv && plus -> "Dolby Vision + HDR10+"
            dv && h10Text -> "Dolby Vision + HDR10"
            dv -> "Dolby Vision"
            plus -> "HDR10+"
            h10Text || pq -> "HDR10"
            hlg -> "HLG"
            t.contains("hdr") -> "HDR"
            else -> "SDR"
        }
    }

    fun videoCodec(codec: String): String = when (codec.lowercase()) {
        "hevc", "h265" -> "HEVC"
        "h264", "avc" -> "H.264"
        "av01" -> "AV1"
        "av1" -> "AV1"
        "vp9" -> "VP9"
        "mpeg2video" -> "MPEG-2"
        "mpeg4" -> "MPEG-4"
        "vc1" -> "VC-1"
        "" -> ""
        else -> codec.uppercase()
    }

    /** "4K · 3840x2160 · HDR10 · 23.976 fps" */
    fun videoLine(v: VideoInfo): String {
        val parts = ArrayList<String>()
        val r = resolution(v.width, v.height)
        if (r.isNotEmpty()) parts.add(r)
        if (v.width > 0 && v.height > 0) parts.add("${v.width}\u00D7${v.height}")
        parts.add(v.hdr)
        val f = fps(v.fps)
        if (f.isNotEmpty()) parts.add(f)
        return parts.joinToString("  \u00B7  ")
    }

    /** "HEVC · 10-bit · 34.5 Mbps" */
    fun videoDetail(v: VideoInfo): String {
        val parts = ArrayList<String>()
        val c = videoCodec(v.codec)
        if (c.isNotEmpty()) parts.add(c)
        if (v.bitDepth > 0) parts.add("${v.bitDepth}-bit")
        if (v.bitrateKbps > 0) parts.add(mbps(v.bitrateKbps))
        return parts.joinToString("  \u00B7  ")
    }

    fun mbps(kbps: Int): String =
        if (kbps >= 1000) String.format(Locale.US, "%.1f Mbps", kbps / 1000f) else "$kbps kbps"

    fun dts(profile: String, title: String): String {
        val p = profile.lowercase().trim()
        val all = (profile + " " + title).lowercase()
        return when {
            all.contains("dts:x") || all.contains("dts-x") || all.contains("dtsx") -> "DTS:X"
            p == "ma" || all.contains("dts-hd ma") || all.contains("dts-hd master") || all.contains("hd ma") -> "DTS-HD MA"
            p == "hra" || all.contains("dts-hd hra") || all.contains("high res") -> "DTS-HD HRA"
            Regex("""\bes\b""").containsMatchIn(p) || all.contains("dts-es") -> "DTS-ES"
            all.contains("express") -> "DTS Express"
            else -> "DTS"
        }
    }

    /** "DTS-HD MA", "Dolby TrueHD Atmos", "Dolby Digital", "AAC" ... */
    fun audioFormat(codec: String, profile: String, title: String, atmos: Boolean): String {
        val c = codec.lowercase()
        val atm = atmos || profile.contains("atmos", true) || title.contains("atmos", true)
        return when {
            c == "truehd" || c == "mlp" -> if (atm) "Dolby TrueHD Atmos" else "Dolby TrueHD"
            c == "eac3" || c == "ec3" || c == "e-ac-3" -> if (atm) "Dolby Digital Plus Atmos" else "Dolby Digital Plus"
            c == "ac3" || c == "a52" -> "Dolby Digital"
            c == "dts" || c == "dca" || c == "dtshd" || c.startsWith("dts") -> dts(profile, title)
            c == "aac" || c == "aac_latm" || c == "mp4a" -> "AAC"
            c == "mp3" -> "MP3"
            c == "mp2" -> "MP2"
            c == "flac" -> "FLAC"
            c == "opus" -> "Opus"
            c == "vorbis" -> "Vorbis"
            c == "alac" -> "ALAC"
            c.startsWith("pcm") || c == "lpcm" -> "LPCM"
            c.isEmpty() -> ""
            else -> codec.uppercase()
        }
    }

    fun layout(channels: Int, layout: String): String {
        val m = Regex("""\d\.\d""").find(layout)
        if (m != null) return m.value
        return when (channels) {
            0 -> ""
            1 -> "1.0"
            2 -> "2.0"
            3 -> "2.1"
            6 -> "5.1"
            7 -> "6.1"
            8 -> "7.1"
            else -> "${channels}ch"
        }
    }

    fun subFormat(codec: String): String = when (codec.lowercase()) {
        "subrip", "srt" -> "SRT"
        "ass" -> "ASS"
        "ssa" -> "SSA"
        "webvtt", "vtt" -> "VTT"
        "hdmv_pgs_subtitle", "pgssub", "pgs" -> "PGS"
        "dvd_subtitle", "dvdsub", "vobsub" -> "VobSub"
        "dvb_subtitle", "dvbsub" -> "DVB"
        "mov_text", "tx3g" -> "TX3G"
        "sub", "microdvd" -> "SUB"
        "eia_608", "cc_dec", "eia608" -> "CC"
        "" -> ""
        else -> codec.uppercase()
    }

    private fun langName(st: StreamInfo): String {
        val l = Langs.display(st.lang)
        if (l.isNotEmpty()) return l
        val t = st.customTitle.ifEmpty { st.title }
        return if (t.isNotEmpty()) t else "Track ${st.key}"
    }

    /** Main line of an audio row: "Thai  ·  DTS-HD MA 5.1" */
    fun audioLabel(st: StreamInfo): String {
        val fmt = audioFormat(st.codec, st.profile, st.title, st.atmos)
        val lay = layout(st.channels, st.layout)
        val tech = listOf(fmt, lay).filter { it.isNotEmpty() }.joinToString(" ")
        return if (tech.isEmpty()) langName(st) else langName(st) + "  \u00B7  " + tech
    }

    /** Second line of an audio row: custom title, bitrate, default flag. */
    fun audioDetail(st: StreamInfo): String {
        val parts = ArrayList<String>()
        val ct = st.customTitle
        if (ct.isNotEmpty() && !ct.equals(Langs.display(st.lang), true)) parts.add(ct)
        if (st.bitrateKbps > 0) parts.add(mbps(st.bitrateKbps))
        if (st.isDefault) parts.add("Default")
        if (st.forced) parts.add("Forced")
        return parts.joinToString("  \u00B7  ")
    }

    /** Main line of a subtitle row: "Thai (SRT)" */
    fun subLabel(st: StreamInfo): String {
        val f = subFormat(st.codec)
        return if (f.isEmpty()) langName(st) else langName(st) + " (" + f + ")"
    }

    /** Second line of a subtitle row. */
    fun subDetail(st: StreamInfo): String {
        val parts = ArrayList<String>()
        val ct = st.customTitle
        if (ct.isNotEmpty() && !ct.equals(Langs.display(st.lang), true)) parts.add(ct)
        if (st.external) parts.add("External file")
        if (st.forced) parts.add("Forced")
        if (st.isDefault) parts.add("Default")
        if (!st.isText) parts.add("Image subtitle")
        return parts.joinToString("  \u00B7  ")
    }

    /** Name for a stream decoded by ExoPlayer when the server gave us no details. */
    fun mimeToAudio(mime: String?, codecs: String?): String {
        val m = (mime ?: "").lowercase()
        val c = (codecs ?: "").lowercase()
        return when {
            m == "audio/true-hd" -> "Dolby TrueHD"
            m == "audio/eac3-joc" -> "Dolby Digital Plus Atmos"
            m == "audio/eac3" -> "Dolby Digital Plus"
            m == "audio/ac3" || m == "audio/ac4" -> "Dolby Digital"
            m.startsWith("audio/vnd.dts.hd") -> "DTS-HD"
            m.startsWith("audio/vnd.dts") -> "DTS"
            m == "audio/mp4a-latm" || c.startsWith("mp4a") -> "AAC"
            m == "audio/opus" -> "Opus"
            m == "audio/flac" -> "FLAC"
            m == "audio/mpeg" -> "MP3"
            m.startsWith("audio/raw") -> "LPCM"
            else -> ""
        }
    }
}
