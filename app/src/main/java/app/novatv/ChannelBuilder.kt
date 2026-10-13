package app.novatv

import app.novatv.ui.IconData
import java.util.Arrays
import kotlin.random.Random

/** Maps raw genre strings from the servers onto our icon keys. */
object Genres {
    private val rules: List<Pair<String, List<String>>> = listOf(
        "scifi" to listOf("science fiction", "sci-fi", "scifi"),
        "animation" to listOf("animation", "anime", "cartoon"),
        "kids" to listOf("kids", "children"),
        "family" to listOf("family"),
        "action" to listOf("action"),
        "adventure" to listOf("adventure"),
        "comedy" to listOf("comedy", "sitcom"),
        "crime" to listOf("crime"),
        "documentary" to listOf("documentary", "docuseries"),
        "drama" to listOf("drama"),
        "fantasy" to listOf("fantasy"),
        "history" to listOf("history", "historical"),
        "horror" to listOf("horror"),
        "music" to listOf("music"),
        "mystery" to listOf("mystery"),
        "romance" to listOf("romance"),
        "thriller" to listOf("thriller", "suspense"),
        "war" to listOf("war"),
        "western" to listOf("western"),
        "sport" to listOf("sport"),
        "news" to listOf("news", "politics"),
        "reality" to listOf("reality", "talk show", "game show"),
        "biography" to listOf("biography")
    )

    fun key(raw: String): String? {
        val l = raw.lowercase()
        for ((k, words) in rules) {
            if (words.any { l.contains(it) }) return k
        }
        return null
    }

    fun label(key: String): String = IconData.icons[key]?.label ?: key.replaceFirstChar { it.uppercase() }
}

object ChannelBuilder {

    private class Spec(
        val id: String,
        val name: String,
        val kind: ChannelKind,
        val icon: String,
        val entries: List<MediaEntry>
    )

    private fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    /** The same movie often exists on Plex + Jellyfin + Emby; keep only the first copy. */
    fun dedupe(list: List<MediaEntry>): List<MediaEntry> {
        val seen = HashSet<String>()
        return list.filter { e ->
            val key = if (e.kind == EntryKind.MOVIE) {
                "m|${norm(e.title)}|${e.year}"
            } else {
                "e|${norm(e.series)}|${e.subtitle}|${if (e.subtitle.isEmpty()) norm(e.title) else ""}"
            }
            seen.add(key)
        }
    }

    private fun genreSpecs(
        list: List<MediaEntry>,
        prefix: String,
        suffix: String,
        kind: ChannelKind,
        minItems: Int
    ): List<Spec> {
        val groups = LinkedHashMap<String, MutableList<MediaEntry>>()
        for (e in list) {
            val keys = e.genres.mapNotNull { Genres.key(it) }.toSet()
            for (k in keys) groups.getOrPut(k) { ArrayList() }.add(e)
        }
        return groups.entries
            .filter { it.value.size >= minItems }
            .sortedByDescending { it.value.size }
            .map { Spec("$prefix-${it.key}", "${Genres.label(it.key)} $suffix", kind, it.key, it.value) }
    }

    fun build(all: List<MediaEntry>, s: ChannelSettings): List<Channel> {
        val usable = all.filter { it.durationMs >= 60_000L }
        val movies = usable.filter { it.kind == EntryKind.MOVIE }
        val eps = usable.filter { it.kind == EntryKind.EPISODE }

        val specs = ArrayList<Spec>()
        if (usable.isNotEmpty()) specs.add(Spec("mix", "Nova Mix", ChannelKind.MIX, "mix", usable))
        if (movies.isNotEmpty()) specs.add(Spec("movies", "All Movies", ChannelKind.MOVIES, "movies", movies))
        if (eps.isNotEmpty()) specs.add(Spec("series", "All Series", ChannelKind.SERIES, "tv", eps))

        if (s.movieGenres) specs.addAll(genreSpecs(movies, "mg", "Movies", ChannelKind.MOVIE_GENRE, s.minItems))
        if (s.seriesGenres) specs.addAll(genreSpecs(eps, "sg", "Series", ChannelKind.SERIES_GENRE, s.minItems))

        if (s.networks) {
            val byNetwork = LinkedHashMap<String, MutableList<MediaEntry>>()
            for (e in eps) {
                if (e.network.isNotEmpty()) byNetwork.getOrPut(e.network) { ArrayList() }.add(e)
            }
            byNetwork.entries
                .filter { it.value.size >= s.minItems }
                .sortedByDescending { it.value.size }
                .take(24)
                .forEach { specs.add(Spec("nw-${norm(it.key)}", it.key, ChannelKind.NETWORK, "network", it.value)) }
        }

        return specs.take(99).mapIndexed { i, sp ->
            Channel(
                id = sp.id,
                number = i + 1,
                name = sp.name,
                kind = sp.kind,
                iconKey = sp.icon,
                // fixed seed per channel => the "broadcast schedule" is stable between launches
                entries = sp.entries.shuffled(Random(sp.id.hashCode().toLong()))
            )
        }
    }
}

/** Turns a channel's looping playlist into a "what's on now / next" schedule. */
object Scheduler {

    /** Index of the entry on air at [nowMs] and its start time. */
    private fun locate(ch: Channel, nowMs: Long): Pair<Int, Long> {
        val total = ch.totalMs
        val cycleStart = nowMs - Math.floorMod(nowMs, total)
        val pos = nowMs - cycleStart
        var idx = Arrays.binarySearch(ch.cumulative, pos)
        idx = if (idx >= 0) idx + 1 else -(idx + 1)
        if (idx >= ch.entries.size) idx = ch.entries.size - 1
        val start = cycleStart + (if (idx == 0) 0L else ch.cumulative[idx - 1])
        return Pair(idx, start)
    }

    fun programAt(ch: Channel, nowMs: Long): Program? {
        if (ch.totalMs <= 0L) return null
        val (idx, start) = locate(ch, nowMs)
        val e = ch.entries[idx]
        return Program(e, start, start + e.durationMs)
    }

    /** What is on now plus everything starting within [horizonMs]. */
    fun upcoming(ch: Channel, nowMs: Long, horizonMs: Long, maxItems: Int = 12): List<Program> {
        if (ch.totalMs <= 0L) return emptyList()
        val (idx0, start0) = locate(ch, nowMs)
        val out = ArrayList<Program>()
        var idx = idx0
        var start = start0
        while (out.size < maxItems && start < nowMs + horizonMs) {
            val e = ch.entries[idx]
            out.add(Program(e, start, start + e.durationMs))
            start += e.durationMs
            idx = (idx + 1) % ch.entries.size
        }
        return out
    }
}
