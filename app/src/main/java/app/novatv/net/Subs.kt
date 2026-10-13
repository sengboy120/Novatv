package app.novatv.net

/** One line of subtitle text with its on-screen time span. */
class Cue(val startMs: Long, val endMs: Long, val text: String)

/** Minimal SRT / WebVTT / ASS parser so the app can draw (and shift) text subtitles itself. */
object SubParser {

    private val timeRe = Regex("""(?:(\d{1,2}):)?(\d{1,2}):(\d{2})[.,](\d{1,3})""")
    private val tagRe = Regex("""<[^>]*>""")
    private val braceRe = Regex("""\{[^}]*\}""")

    private fun toMs(h: String, m: String, s: String, frac: String): Long {
        val hh = if (h.isEmpty()) 0L else h.toLong()
        val f = (frac + "00").substring(0, 3).toLong()
        return ((hh * 60L + m.toLong()) * 60L + s.toLong()) * 1000L + f
    }

    private fun clean(raw: String): String {
        var t = raw
        t = braceRe.replace(t, "")
        t = tagRe.replace(t, "")
        t = t.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&amp;", "&")
        return t.trim()
    }

    fun parse(input: String): List<Cue> {
        val text = input.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
        val cues = if (text.contains("[Events]")) parseAss(text) else parseTimed(text)
        return cues.sortedBy { it.startMs }
    }

    /** SRT and WebVTT share the "start --> end" block layout. */
    private fun parseTimed(text: String): List<Cue> {
        val out = ArrayList<Cue>()
        val blocks = text.split(Regex("\n\\s*\n"))
        for (b in blocks) {
            val lines = b.split('\n')
            val ti = lines.indexOfFirst { it.contains("-->") }
            if (ti < 0) continue
            val times = timeRe.findAll(lines[ti]).toList()
            if (times.size < 2) continue
            val a = times[0].groupValues
            val z = times[1].groupValues
            val start = toMs(a[1], a[2], a[3], a[4])
            val end = toMs(z[1], z[2], z[3], z[4])
            val body = lines.drop(ti + 1).map { clean(it) }.filter { it.isNotEmpty() }.joinToString("\n")
            if (body.isNotEmpty() && end > start) out.add(Cue(start, end, body))
        }
        return out
    }

    private fun assTime(s: String): Long {
        // h:mm:ss.cc
        val m = Regex("""(\d+):(\d{2}):(\d{2})[.](\d{1,3})""").find(s.trim()) ?: return -1L
        val g = m.groupValues
        val frac = g[4]
        val ms = when (frac.length) {
            1 -> frac.toLong() * 100L
            2 -> frac.toLong() * 10L
            else -> frac.toLong()
        }
        return ((g[1].toLong() * 60L + g[2].toLong()) * 60L + g[3].toLong()) * 1000L + ms
    }

    private fun parseAss(text: String): List<Cue> {
        val out = ArrayList<Cue>()
        for (line in text.split('\n')) {
            if (!line.startsWith("Dialogue:")) continue
            val f = line.removePrefix("Dialogue:").split(',', limit = 10)
            if (f.size < 10) continue
            val start = assTime(f[1])
            val end = assTime(f[2])
            if (start < 0L || end <= start) continue
            val body = clean(f[9].replace("\\N", "\n").replace("\\n", "\n"))
            if (body.isNotEmpty()) out.add(Cue(start, end, body))
        }
        return out
    }

    /** The text to show at [tMs] (several overlapping cues are joined by line breaks). */
    fun textAt(cues: List<Cue>, tMs: Long): String {
        if (cues.isEmpty() || tMs < 0L) return ""
        // binary search: last cue with start <= t
        var lo = 0
        var hi = cues.size - 1
        var idx = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (cues[mid].startMs <= tMs) {
                idx = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        if (idx < 0) return ""
        val active = ArrayList<String>()
        var i = idx
        var steps = 0
        while (i >= 0 && steps < 8) {
            val c = cues[i]
            if (c.endMs > tMs) active.add(0, c.text)
            i--
            steps++
        }
        return active.joinToString("\n")
    }
}
