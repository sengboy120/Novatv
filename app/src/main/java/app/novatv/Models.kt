package app.novatv

enum class ServerType(val label: String) {
    PLEX("Plex"),
    JELLYFIN("Jellyfin"),
    EMBY("Emby")
}

data class Server(
    val id: String,
    val type: ServerType,
    val name: String,
    val baseUrl: String,
    val token: String,
    val userId: String = "",
    /** Switched off servers are kept (login and all) but ignored: no sync, no channels. */
    val enabled: Boolean = true
)

enum class EntryKind { MOVIE, EPISODE }

/** One playable item (a movie or an episode) from any of the connected servers. */
data class MediaEntry(
    val id: String,
    val serverId: String,
    /** The item's id on its own server (Jellyfin/Emby Id, Plex ratingKey). */
    val itemId: String,
    val kind: EntryKind,
    val title: String,
    /** For episodes: "S01E02". Empty for movies. */
    val subtitle: String,
    val series: String,
    /** Network of an episode; for movies the studios joined with " | ". */
    val network: String,
    val genres: List<String>,
    val year: Int,
    val durationMs: Long,
    val thumbUrl: String,
    val streamUrl: String,
    /** Plot summary (may be empty; filled in on library sync). */
    val overview: String = "",
    /** Community / audience rating 0-10, 0 = unknown. */
    val rating: Float = 0f,
    /** Content rating such as "PG-13" or "TV-MA". */
    val mpaa: String = ""
) {
    /** Title as shown on screen. */
    val displayTitle: String
        get() = if (kind == EntryKind.EPISODE && series.isNotEmpty()) series else title

    /** Second line as shown on screen. */
    val displaySub: String
        get() = if (kind == EntryKind.EPISODE) {
            listOf(subtitle, title).filter { it.isNotEmpty() }.joinToString(" · ")
        } else {
            if (year > 0) year.toString() else ""
        }
}

enum class ChannelKind(val tag: String) {
    MIX("MIX"),
    MOVIES("MOVIES"),
    SERIES("SERIES"),
    MOVIE_GENRE("MOVIES"),
    SERIES_GENRE("SERIES"),
    NETWORK("NETWORK")
}

class Channel(
    val id: String,
    val number: Int,
    val name: String,
    val kind: ChannelKind,
    val iconKey: String,
    val entries: List<MediaEntry>,
    /** > 0 for "real" network channels: every programme starts on a multiple of this many ms. */
    val slotMs: Long = 0L
) {
    /** Airtime reserved for [e]: its length, rounded up to a whole number of slots on slotted channels. */
    fun spanOf(e: MediaEntry): Long =
        if (slotMs > 0L) ((e.durationMs + slotMs - 1L) / slotMs).coerceAtLeast(1L) * slotMs else e.durationMs

    /** cumulative[i] = end offset (ms) of entry i inside one full cycle of the playlist. */
    val cumulative: LongArray = LongArray(entries.size).also { arr ->
        var t = 0L
        entries.forEachIndexed { i, e ->
            t += spanOf(e)
            arr[i] = t
        }
    }
    val totalMs: Long = if (cumulative.isEmpty()) 0L else cumulative[cumulative.size - 1]
}

data class Program(
    val entry: MediaEntry,
    val startMs: Long,
    val endMs: Long
) {
    /** Airtime of the whole slot (what the guide draws). */
    val spanMs: Long get() = endMs - startMs

    /** Real length of the film / episode (shorter than [spanMs] on slotted channels). */
    val durationMs: Long get() = if (entry.durationMs in 1L until spanMs) entry.durationMs else spanMs

    /** When the picture itself ends (the rest of the slot, if any, is a "next up" slate). */
    val contentEndMs: Long get() = startMs + durationMs
}

data class ChannelSettings(
    val movieGenres: Boolean = true,
    val seriesGenres: Boolean = true,
    val networks: Boolean = true,
    val minItems: Int = 8
)

enum class Screen { PLAYER, SETTINGS, ADD_SERVER }

// ---- playback / tracks -------------------------------------------------------------------------

enum class PlayMode(val label: String, val hint: String) {
    DIRECT("DIRECT PLAY", "Plays the original file. Lowest latency, but some audio (e.g. DTS) may be silent on the Shield."),
    SMART("SMART", "The server only converts what the Shield can't play (audio, subtitles, too-high bitrate)."),
    TRANSCODE("ALWAYS TRANSCODE", "The server always converts to H.264 / AAC at the bitrate below. Best for slow or remote connections.")
}

/** What to do with DTS-HD / DTS:X audio (some receivers can't decode the HD part). */
enum class DtsMode(val label: String, val hint: String) {
    PASS("DTS-HD: PASSTHROUGH", "Sends the full DTS-HD MA / DTS:X stream to your receiver (it should show DTS-HD MA, not just DTS)."),
    CORE("DTS-HD: CORE ONLY", "Sends only the plain DTS 5.1 core of DTS-HD tracks. Use this if the receiver shows nothing or noise on DTS-HD."),
    CONVERT("DTS-HD: CONVERT", "The server converts DTS-HD tracks to Dolby Digital. Always plays, but the server has to work and subtitles may be burned in.")
}

data class PlaybackSettings(
    val mode: PlayMode = PlayMode.DIRECT,
    val maxMbps: Int = 12,
    /** 2-letter code ("th", "en", ...) or "" = no subtitles. */
    val subLang: String = "",
    /** 2-letter code or "" = use the file's default audio track. */
    val audioLang: String = "",
    /** Subtitle height: percent of the screen height between the bottom edge and the text. */
    val subPos: Int = 8,
    /** Subtitle text size in percent of the normal size (text subtitles only). */
    val subSize: Int = 100,
    val dtsMode: DtsMode = DtsMode.PASS
)

enum class TrackKind { AUDIO, SUBTITLE }

/** An audio or subtitle stream of one item, as reported by the server. */
data class StreamInfo(
    /** Jellyfin/Emby stream Index, or Plex stream id. */
    val key: String,
    val kind: TrackKind,
    val lang: String,
    val title: String,
    val codec: String,
    val external: Boolean,
    val isText: Boolean,
    val sidecarUrl: String,
    val mime: String,
    /** The stream's own title tag (e.g. "Commentary"), without any language / codec text. */
    val customTitle: String = "",
    /** Codec profile as reported by the server, e.g. "DTS-HD MA". */
    val profile: String = "",
    val channels: Int = 0,
    /** Channel layout text from the server, e.g. "5.1" or "5.1(side)". */
    val layout: String = "",
    val bitrateKbps: Int = 0,
    val forced: Boolean = false,
    val isDefault: Boolean = false,
    /** Object-based / spatial audio flag (Dolby Atmos). */
    val atmos: Boolean = false
)

/** The main video stream of an item. */
data class VideoInfo(
    val width: Int = 0,
    val height: Int = 0,
    val fps: Float = 0f,
    val codec: String = "",
    /** "SDR", "HDR10", "HDR10+", "HLG", "Dolby Vision", ... */
    val hdr: String = "SDR",
    val bitDepth: Int = 0,
    val bitrateKbps: Int = 0
)

data class StreamsInfo(
    val sourceId: String,
    val audio: List<StreamInfo>,
    val subs: List<StreamInfo>,
    val video: VideoInfo? = null
)

/** Everything the player needs to start an item. */
class PlayRequest(
    val uri: String,
    val mime: String?,
    val sidecars: List<StreamInfo>,
    val startMs: Long,
    val transcoded: Boolean,
    /**
     * Jellyfin / Emby transcode that does NOT burn text subtitles into the picture: the app draws them itself
     * (sharp at any size, position / delay adjustable, and the server has less to do).
     */
    val softCapable: Boolean = false
)

class Prepared(val req: PlayRequest, val info: StreamsInfo?)
