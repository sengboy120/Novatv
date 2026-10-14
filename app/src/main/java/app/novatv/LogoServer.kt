package app.novatv

import fi.iki.elonen.NanoHTTPD
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Tiny web page served on the local network so channel logos can be uploaded from a phone or PC.
 * Only runs while Settings is open (see SettingsScreen); there is no password, so it is LAN-only and short-lived.
 */
class LogoServer(port: Int, private val channels: () -> List<Channel>) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            val uri = session.uri ?: "/"
            when {
                session.method == Method.GET && uri.startsWith("/logo/") -> logo(uri.removePrefix("/logo/"))
                session.method == Method.GET -> page()
                session.method == Method.POST && uri == "/upload" -> upload(session)
                session.method == Method.POST && uri == "/reset" -> reset(session)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
        } catch (e: Throwable) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: " + (e.message ?: e.javaClass.simpleName))
        }
    }

    private fun back(): Response {
        val r = newFixedLengthResponse(Response.Status.REDIRECT_SEE_OTHER, MIME_HTML, "")
        r.addHeader("Location", "/")
        return r
    }

    private fun logo(id: String): Response {
        val f = Logos.file(java.net.URLDecoder.decode(id, "UTF-8"))
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "none")
        val r = newChunkedResponse(Response.Status.OK, "image/png", f.inputStream())
        r.addHeader("Cache-Control", "no-store")
        return r
    }

    private fun upload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val id = session.parms["id"] ?: return back()
        val path = files["file"] ?: return back()
        val f = java.io.File(path)
        if (f.length() in 1..(8L * 1024 * 1024)) Logos.save(id, f.readBytes())
        return back()
    }

    private fun reset(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        session.parms["id"]?.let { Logos.remove(it) }
        return back()
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun page(): Response {
        val list = channels()
        // studio / network channels first, then the genre channels
        val ordered = list.filter { it.kind == ChannelKind.NETWORK } + list.filter { it.kind != ChannelKind.NETWORK }
        val sb = StringBuilder()
        sb.append(
            """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Bendit TV logos</title><style>
body{margin:0;background:#07070d;color:#eee;font-family:system-ui,sans-serif}
h1{font-size:20px;margin:0;padding:18px 16px 4px}p.s{margin:0;padding:0 16px 14px;color:#99a;font-size:14px}
.g{display:grid;grid-template-columns:repeat(auto-fill,minmax(250px,1fr));gap:12px;padding:0 16px 40px}
.c{background:#111119;border:1px solid #2a2a3a;border-radius:14px;padding:12px}
.t{display:flex;align-items:center;gap:12px;margin-bottom:10px}
.b{width:64px;height:64px;border-radius:14px;background:#1a1a26;border:1px solid #333;display:flex;align-items:center;justify-content:center;overflow:hidden;color:#778;font-size:11px;text-align:center}
.b img{max-width:90%;max-height:90%}
.n{font-weight:700;font-size:16px}.k{color:#889;font-size:12px}
input[type=file]{width:100%;color:#ccc;font-size:13px;margin-bottom:8px}
button{background:#2b2b44;color:#fff;border:0;border-radius:20px;padding:8px 16px;font-size:14px;margin-right:6px}
button.r{background:#3a1d26}
</style></head><body>
<h1>Bendit TV &mdash; channel logos</h1>
<p class="s">Pick an image (PNG / JPG / WebP, transparent PNG looks best) and press Upload. It shows up on the TV at once.</p>
<div class="g">"""
        )
        for (c in ordered) {
            val id = esc(c.id)
            val has = Logos.has(c.id)
            sb.append("<div class=\"c\" id=\"").append(id).append("\"><div class=\"t\"><div class=\"b\">")
            if (has) sb.append("<img src=\"/logo/").append(java.net.URLEncoder.encode(c.id, "UTF-8")).append("?v=").append(System.nanoTime()).append("\">")
            else sb.append("no logo")
            sb.append("</div><div><div class=\"n\">").append(esc(c.name)).append("</div><div class=\"k\">")
            sb.append(if (c.kind == ChannelKind.NETWORK) "studio channel" else "genre channel").append("</div></div></div>")
            sb.append("<form method=\"post\" action=\"/upload\" enctype=\"multipart/form-data\">")
            sb.append("<input type=\"hidden\" name=\"id\" value=\"").append(id).append("\">")
            sb.append("<input type=\"file\" name=\"file\" accept=\"image/*\" required>")
            sb.append("<button type=\"submit\">Upload</button></form>")
            if (has) {
                sb.append("<form method=\"post\" action=\"/reset\" style=\"margin-top:6px\"><input type=\"hidden\" name=\"id\" value=\"")
                    .append(id).append("\"><button class=\"r\" type=\"submit\">Remove logo</button></form>")
            }
            sb.append("</div>")
        }
        sb.append("</div></body></html>")
        val r = newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", sb.toString())
        r.addHeader("Cache-Control", "no-store")
        return r
    }

    companion object {
        /** This device's LAN address (e.g. 192.168.1.20), or null when offline. */
        fun localIp(): String? {
            try {
                for (ni in NetworkInterface.getNetworkInterfaces()) {
                    if (!ni.isUp || ni.isLoopback) continue
                    for (a in ni.inetAddresses) {
                        if (a is Inet4Address && !a.isLoopbackAddress && a.isSiteLocalAddress) return a.hostAddress
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
            return null
        }
    }
}
