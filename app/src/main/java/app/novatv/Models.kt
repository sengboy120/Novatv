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
    val userId: String = ""
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
    val network: String,
    val genres: List<String>,
    val year: Int,
    val durationMs: Long,
    val thumbUrl: String,
    val streamUrl: String
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
    val entries: List<MediaEntry>
) {
    /** cumulative[i] = end offset (ms) of entry i inside one full cycle of the playlist. */
    val cumulative: LongArray = LongArray(entries.size).also { arr ->
        var t = 0L
        entries.forEachIndexed { i, e ->
            t += e.durationMs
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
    val durationMs: Long get() = endMs - startMs
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

data class PlaybackSettings(
    val mode: PlayMode = PlayMode.DIRECT,
    val maxMbps: Int = 12,
    /** 2-letter code ("th", "en", ...) or "" = no subtitles. */
    val subLang: String = "",
    /** 2-letter code or "" = use the file's default audio track. */
    val audioLang: String = ""
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
    val mime: String
)

data class StreamsInfo(
    val sourceId: String,
    val audio: List<StreamInfo>,
    val subs: List<StreamInfo>
)

/** Everything the player needs to start an item. */
class PlayRequest(
    val uri: String,
    val mime: String?,
    val sidecars: List<StreamInfo>,
    val startMs: Long,
    val transcoded: Boolean
)

class Prepared(val req: PlayRequest, val info: StreamsInfo?)
