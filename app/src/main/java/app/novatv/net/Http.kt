package app.novatv.net

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val quickClient: OkHttpClient = client.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val b = Request.Builder().url(url)
        headers.forEach { (k, v) -> b.header(k, v) }
        return client.newCall(b.build()).execute().use { r ->
            val text = r.body?.string() ?: ""
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} from ${url.substringBefore('?')}")
            text
        }
    }

    fun post(url: String, body: RequestBody, headers: Map<String, String> = emptyMap()): String {
        val b = Request.Builder().url(url).post(body)
        headers.forEach { (k, v) -> b.header(k, v) }
        return client.newCall(b.build()).execute().use { r ->
            val text = r.body?.string() ?: ""
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} from ${url.substringBefore('?')}")
            text
        }
    }

    fun put(url: String, headers: Map<String, String> = emptyMap()) {
        val b = Request.Builder().url(url).put(ByteArray(0).toRequestBody(null))
        headers.forEach { (k, v) -> b.header(k, v) }
        client.newCall(b.build()).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code} from ${url.substringBefore('?')}")
        }
    }

    /** Short-timeout reachability check. */
    fun ping(url: String, headers: Map<String, String> = emptyMap()): Boolean {
        return try {
            val b = Request.Builder().url(url)
            headers.forEach { (k, v) -> b.header(k, v) }
            quickClient.newCall(b.build()).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    fun normalizeUrl(input: String): String {
        var u = input.trim()
        if (!u.contains("://")) u = "http://$u"
        return u.trimEnd('/')
    }
}

// ---- small JSON helpers ----------------------------------------------------------------------

fun JSONArray?.objs(): List<JSONObject> {
    if (this == null) return emptyList()
    val out = ArrayList<JSONObject>(length())
    for (i in 0 until length()) {
        val o = optJSONObject(i)
        if (o != null) out.add(o)
    }
    return out
}

fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    val out = ArrayList<String>(length())
    for (i in 0 until length()) {
        val s = optString(i, "")
        if (s.isNotEmpty()) out.add(s)
    }
    return out
}
