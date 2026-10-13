package app.novatv

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Persists servers, settings and a cached copy of the library so the app starts instantly. */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("novatv", Context.MODE_PRIVATE)
    private val cacheFile = File(context.filesDir, "library.json")

    // ---- servers -----------------------------------------------------------------------------

    fun loadServers(): List<Server> {
        val raw = prefs.getString("servers", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Server(
                    id = o.getString("id"),
                    type = ServerType.valueOf(o.getString("type")),
                    name = o.getString("name"),
                    baseUrl = o.getString("baseUrl"),
                    token = o.getString("token"),
                    userId = o.optString("userId", "")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveServers(list: List<Server>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(
                JSONObject()
                    .put("id", s.id)
                    .put("type", s.type.name)
                    .put("name", s.name)
                    .put("baseUrl", s.baseUrl)
                    .put("token", s.token)
                    .put("userId", s.userId)
            )
        }
        prefs.edit().putString("servers", arr.toString()).apply()
    }

    // ---- settings ----------------------------------------------------------------------------

    fun loadSettings(): ChannelSettings = ChannelSettings(
        movieGenres = prefs.getBoolean("movieGenres", true),
        seriesGenres = prefs.getBoolean("seriesGenres", true),
        networks = prefs.getBoolean("networks", true),
        minItems = prefs.getInt("minItems", 8)
    )

    fun saveSettings(s: ChannelSettings) {
        prefs.edit()
            .putBoolean("movieGenres", s.movieGenres)
            .putBoolean("seriesGenres", s.seriesGenres)
            .putBoolean("networks", s.networks)
            .putInt("minItems", s.minItems)
            .apply()
    }

    fun loadPlayback(): PlaybackSettings = PlaybackSettings(
        mode = try {
            PlayMode.valueOf(prefs.getString("playMode", PlayMode.DIRECT.name) ?: PlayMode.DIRECT.name)
        } catch (e: Exception) {
            PlayMode.DIRECT
        },
        maxMbps = prefs.getInt("maxMbps", 12),
        subLang = prefs.getString("subLang", "") ?: "",
        audioLang = prefs.getString("audioLang", "") ?: ""
    )

    fun savePlayback(p: PlaybackSettings) {
        prefs.edit()
            .putString("playMode", p.mode.name)
            .putInt("maxMbps", p.maxMbps)
            .putString("subLang", p.subLang)
            .putString("audioLang", p.audioLang)
            .apply()
    }

    var lastChannelId: String?
        get() = prefs.getString("lastChannel", null)
        set(value) {
            prefs.edit().putString("lastChannel", value).apply()
        }

    val clientId: String
        get() {
            var id = prefs.getString("clientId", null)
            if (id == null) {
                id = java.util.UUID.randomUUID().toString()
                prefs.edit().putString("clientId", id).apply()
            }
            return id
        }

    // ---- library cache -----------------------------------------------------------------------

    fun loadEntries(): List<MediaEntry> {
        if (!cacheFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(cacheFile.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val g = o.getJSONArray("genres")
                MediaEntry(
                    id = o.getString("id"),
                    serverId = o.getString("serverId"),
                    itemId = o.getString("itemId"),
                    kind = EntryKind.valueOf(o.getString("kind")),
                    title = o.getString("title"),
                    subtitle = o.getString("subtitle"),
                    series = o.getString("series"),
                    network = o.getString("network"),
                    genres = (0 until g.length()).map { g.getString(it) },
                    year = o.getInt("year"),
                    durationMs = o.getLong("durationMs"),
                    thumbUrl = o.getString("thumbUrl"),
                    streamUrl = o.getString("streamUrl")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveEntries(list: List<MediaEntry>) {
        try {
            val arr = JSONArray()
            list.forEach { e ->
                arr.put(
                    JSONObject()
                        .put("id", e.id)
                        .put("serverId", e.serverId)
                        .put("itemId", e.itemId)
                        .put("kind", e.kind.name)
                        .put("title", e.title)
                        .put("subtitle", e.subtitle)
                        .put("series", e.series)
                        .put("network", e.network)
                        .put("genres", JSONArray(e.genres))
                        .put("year", e.year)
                        .put("durationMs", e.durationMs)
                        .put("thumbUrl", e.thumbUrl)
                        .put("streamUrl", e.streamUrl)
                )
            }
            cacheFile.writeText(arr.toString())
        } catch (e: Exception) {
            // cache is best-effort
        }
    }
}
