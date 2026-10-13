package app.novatv.net

import app.novatv.EntryKind
import app.novatv.MediaEntry
import app.novatv.Server
import app.novatv.ServerType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Jellyfin and Emby share (almost) the same REST API, so one client handles both. */
object EmbyLike {

    private const val PAGE = 1000

    private fun authHeader(deviceId: String): String =
        "MediaBrowser Client=\"NovaTV\", Device=\"Android TV\", DeviceId=\"$deviceId\", Version=\"1.0\""

    fun login(type: ServerType, rawUrl: String, user: String, pass: String, deviceId: String): Server {
        val base = Http.normalizeUrl(rawUrl)
        val json = JSONObject().put("Username", user).put("Pw", pass).put("Password", pass)
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val auth = authHeader(deviceId)
        val resp = JSONObject(
            Http.post(
                "$base/Users/AuthenticateByName",
                body,
                mapOf(
                    "X-Emby-Authorization" to auth,
                    "Authorization" to auth,
                    "Accept" to "application/json"
                )
            )
        )
        val token = resp.getString("AccessToken")
        val uid = resp.getJSONObject("User").getString("Id")
        val name = try {
            JSONObject(Http.get("$base/System/Info/Public")).optString("ServerName", type.label)
        } catch (e: Exception) {
            type.label
        }
        val id = "${type.name.lowercase()}-${uid.take(8)}-${Math.abs(base.hashCode())}"
        return Server(id = id, type = type, name = name.ifEmpty { type.label }, baseUrl = base, token = token, userId = uid)
    }

    private fun items(s: Server, type: String, fields: String): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        val headers = mapOf("X-Emby-Token" to s.token, "Accept" to "application/json")
        var start = 0
        while (true) {
            val url = "${s.baseUrl}/Users/${s.userId}/Items?Recursive=true&IncludeItemTypes=$type" +
                "&Fields=$fields&EnableImages=false&EnableUserData=false" +
                "&SortBy=SortName&SortOrder=Ascending&StartIndex=$start&Limit=$PAGE&api_key=${Http.enc(s.token)}"
            val o = JSONObject(Http.get(url, headers))
            val page = o.optJSONArray("Items").objs()
            out.addAll(page)
            start += page.size
            val total = o.optInt("TotalRecordCount", start)
            if (page.isEmpty() || start >= total) break
        }
        return out
    }

    fun fetch(s: Server): List<MediaEntry> {
        val key = Http.enc(s.token)

        // series -> (genres, network) so episodes can inherit them
        val seriesInfo = HashMap<String, Pair<List<String>, String>>()
        for (o in items(s, "Series", "Genres,Studios")) {
            val genres = o.optJSONArray("Genres").strings()
            val studio = o.optJSONArray("Studios").objs()
                .map { it.optString("Name", "") }.filter { it.isNotEmpty() }.joinToString(" | ")
            seriesInfo[o.optString("Id")] = Pair(genres, studio)
        }

        val out = ArrayList<MediaEntry>()

        for (o in items(s, "Movie", "Genres,Studios,RunTimeTicks,ProductionYear,Overview,OfficialRating,CommunityRating")) {
            val id = o.optString("Id")
            if (id.isEmpty()) continue
            out.add(
                MediaEntry(
                    id = "${s.id}:$id",
                    serverId = s.id,
                    itemId = id,
                    kind = EntryKind.MOVIE,
                    title = o.optString("Name", "Untitled"),
                    subtitle = "",
                    series = "",
                    network = o.optJSONArray("Studios").objs()
                        .map { it.optString("Name", "") }.filter { it.isNotEmpty() }.joinToString(" | "),
                    genres = o.optJSONArray("Genres").strings(),
                    year = o.optInt("ProductionYear", 0),
                    durationMs = o.optLong("RunTimeTicks", 0L) / 10_000L,
                    thumbUrl = "${s.baseUrl}/Items/$id/Images/Primary?maxHeight=360&api_key=$key",
                    streamUrl = "${s.baseUrl}/Videos/$id/stream?static=true&api_key=$key",
                    overview = o.optString("Overview", "").take(420),
                    rating = o.optDouble("CommunityRating", 0.0).toFloat(),
                    mpaa = o.optString("OfficialRating", "")
                )
            )
        }

        for (o in items(s, "Episode", "RunTimeTicks,ProductionYear,Overview,OfficialRating,CommunityRating")) {
            val id = o.optString("Id")
            if (id.isEmpty()) continue
            val info = seriesInfo[o.optString("SeriesId")]
            val season = o.optInt("ParentIndexNumber", 0)
            val ep = o.optInt("IndexNumber", 0)
            val code = if (season > 0 || ep > 0) String.format("S%02dE%02d", season, ep) else ""
            out.add(
                MediaEntry(
                    id = "${s.id}:$id",
                    serverId = s.id,
                    itemId = id,
                    kind = EntryKind.EPISODE,
                    title = o.optString("Name", "Episode"),
                    subtitle = code,
                    series = o.optString("SeriesName", ""),
                    network = info?.second ?: "",
                    genres = info?.first ?: emptyList(),
                    year = o.optInt("ProductionYear", 0),
                    durationMs = o.optLong("RunTimeTicks", 0L) / 10_000L,
                    thumbUrl = "${s.baseUrl}/Items/$id/Images/Primary?maxHeight=360&api_key=$key",
                    streamUrl = "${s.baseUrl}/Videos/$id/stream?static=true&api_key=$key",
                    overview = o.optString("Overview", "").take(420),
                    rating = o.optDouble("CommunityRating", 0.0).toFloat(),
                    mpaa = o.optString("OfficialRating", "")
                )
            )
        }
        return out
    }
}
