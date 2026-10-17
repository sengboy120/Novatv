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

/**
 * "Real" TV channels (ABC, HBO, Disney+ ...). A programme joins a channel when its network (series)
 * or one of its studios (movies) matches one of the channel's aliases.
 */
class RealChannel(
    val key: String,
    val name: String,
    /** Short text drawn on the channel badge. */
    val badge: String,
    val c1: Long,
    val c2: Long,
    /** Lower-case, letters+digits only. A network matches when it equals or starts with an alias. */
    val aliases: List<String>
)

object RealChannels {
    /**
     * 0 = programmes simply follow each other (like every other channel).
     * Set to 30 * 60_000L to make them start on the hour / half hour with an "up next" slate in between.
     */
    const val SLOT_MS = 0L

    /** Channels need at least this many titles. */
    const val MIN_ITEMS = 4

    val all: List<RealChannel> = listOf(
        RealChannel("abc", "ABC", "ABC", 0xFFFFD43B, 0xFFFF9F1C, listOf("abc", "americanbroadcasting")),
        RealChannel("cbs", "CBS", "CBS", 0xFF4DA3FF, 0xFF2D6BFF, listOf("cbs", "paramount")),
        RealChannel("nbc", "NBC", "NBC", 0xFFFF6B6B, 0xFFFFB84D, listOf("nbc", "universal", "peacock")),
        RealChannel("fox", "FOX", "FOX", 0xFFFF8A3D, 0xFFFFC04D, listOf("fox", "20thcentury")),
        RealChannel("fx", "FX", "FX", 0xFFFF4D8B, 0xFFFF8A5C, listOf("fx", "fxx", "fxnetworks")),
        RealChannel("hbo", "HBO", "HBO", 0xFFB66BFF, 0xFF6B7BFF, listOf("hbo", "max", "warnerbros", "newline", "dccomics")),
        RealChannel("disney", "Disney+", "Disney+", 0xFF4DE8FF, 0xFF4D8BFF, listOf("disney", "waltdisney", "pixar", "marvel", "lucasfilm")),
        RealChannel("sony", "Sony", "SONY", 0xFFE0E6F5, 0xFF6BA8FF, listOf("sony", "columbia", "screengems")),
        RealChannel("netflix", "Netflix", "Netflix", 0xFFFF4D5E, 0xFFFF2D55, listOf("netflix")),
        RealChannel("apple", "Apple TV+", "Apple TV+", 0xFFD0D6E0, 0xFF8E9AC4, listOf("apple")),
        RealChannel("prime", "Prime Video", "Prime", 0xFF33C8FF, 0xFF2AF5C8, listOf("amazon", "primevideo")),
        RealChannel("lionsgate", "Lionsgate", "LIONS", 0xFF8AB4F8, 0xFFB388FF, listOf("lionsgate", "summitentertainment")),
        RealChannel("mgm", "MGM", "MGM", 0xFFFFC857, 0xFFD4A017, listOf("mgm", "metrogoldwyn", "unitedartists")),
        RealChannel("showtime", "Showtime", "Showtime", 0xFFFF3355, 0xFFFF7A3D, listOf("showtime")),
        RealChannel("amc", "AMC", "AMC", 0xFF4DFF9A, 0xFF2AB8FF, listOf("amc")),
        RealChannel("starz", "Starz", "Starz", 0xFFFFD93D, 0xFFFF8A3D, listOf("starz")),
        RealChannel("cw", "The CW", "CW", 0xFF6BFFD0, 0xFF4D8BFF, listOf("thecw", "cw")),
        RealChannel("comedycentral", "Comedy Central", "CC", 0xFFFFE14D, 0xFFFFA63D, listOf("comedycentral")),
        RealChannel("adultswim", "Adult Swim", "AS", 0xFF9AA8B8, 0xFF6BFFD0, listOf("adultswim")),
        RealChannel("cartoon", "Cartoon Network", "CN", 0xFFFF5CD6, 0xFF5CC8FF, listOf("cartoonnetwork")),
        RealChannel("nick", "Nickelodeon", "Nick", 0xFFFFA63D, 0xFFFF5C9A, listOf("nickelodeon", "nick")),
        RealChannel("usa", "USA Network", "USA", 0xFF5C8BFF, 0xFFFF5C7A, listOf("usanetwork")),
        RealChannel("tnt", "TNT", "TNT", 0xFFFFB04D, 0xFFFF6B4D, listOf("tnt", "tbs")),
        RealChannel("syfy", "Syfy", "Syfy", 0xFFA06BFF, 0xFF47E6FF, listOf("syfy")),
        RealChannel("bbc", "BBC", "BBC", 0xFFFF5C7A, 0xFFB65CFF, listOf("bbc")),
        RealChannel("itv", "ITV", "ITV", 0xFF4DDBFF, 0xFF8A7BFF, listOf("itv")),
        RealChannel("pbs", "PBS", "PBS", 0xFF38E0A0, 0xFF1FA8D8, listOf("pbs"))
    )

    fun byKey(key: String): RealChannel? = all.firstOrNull { it.key == key }

    private fun clean(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    /** The channel a programme belongs to, or null when it isn't on any of them. */
    fun match(e: MediaEntry): RealChannel? {
        if (e.network.isEmpty()) return null
        val names = e.network.split('|').map { clean(it) }.filter { it.isNotEmpty() }
        // the first studio / network listed decides (a Sony film co-produced by Marvel stays on Sony)
        for (n in names) {
            for (ch in all) {
                if (ch.aliases.any { a -> n == a || (a.length >= 3 && n.startsWith(a)) }) return ch
            }
        }
        return null
    }
}

object ChannelBuilder {

    /** Upper limit of the whole channel list. */
    private const val MAX_CHANNELS = 48

    private class Spec(
        val id: String,
        val name: String,
        val kind: ChannelKind,
        val icon: String,
        val entries: List<MediaEntry>,
        val slotMs: Long = 0L
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
        // "Ben latest": only films from 2025 or 2026
        val latest = movies.filter { it.year in 2025..2026 }
        if (latest.isNotEmpty()) specs.add(Spec("mix", "Ben latest", ChannelKind.MOVIES, "mix", latest))
        if (movies.isNotEmpty()) specs.add(Spec("movies", "All Movies", ChannelKind.MOVIES, "movies", movies))
        if (eps.isNotEmpty()) specs.add(Spec("series", "All Series", ChannelKind.SERIES, "tv", eps))

        // real channels (ABC, HBO, Disney+, Sony ...) come right after the three "all" channels
        if (s.networks) {
            val byReal = LinkedHashMap<String, MutableList<MediaEntry>>()
            for (e in usable) {
                val rc = RealChannels.match(e) ?: continue
                byReal.getOrPut(rc.key) { ArrayList() }.add(e)
            }
            for (rc in RealChannels.all) {
                val list = byReal[rc.key] ?: continue
                if (list.size < RealChannels.MIN_ITEMS) continue
                specs.add(Spec("rc-${rc.key}", rc.name, ChannelKind.NETWORK, "rc:${rc.key}", list, RealChannels.SLOT_MS))
            }
        }

        // genre channels fill the rest of the line-up, biggest first, up to MAX_CHANNELS in total
        val genres = ArrayList<Spec>()
        if (s.movieGenres) genres.addAll(genreSpecs(movies, "mg", "Movies", ChannelKind.MOVIE_GENRE, s.minItems))
        if (s.seriesGenres) genres.addAll(genreSpecs(eps, "sg", "Series", ChannelKind.SERIES_GENRE, s.minItems))
        val room = (MAX_CHANNELS - specs.size).coerceAtLeast(0)
        // the two genre channels with the fewest titles are left out
        val bySize = genres.sortedByDescending { it.entries.size }
        val trimmed = if (bySize.size > 2) bySize.dropLast(2) else bySize
        val keep = trimmed.take(room).map { it.id }.toHashSet()
        specs.addAll(genres.filter { it.id in keep })

        return specs.take(99).mapIndexed { i, sp ->
            Channel(
                id = sp.id,
                number = i + 1,
                name = sp.name,
                kind = sp.kind,
                iconKey = sp.icon,
                // fixed seed per channel => the "broadcast schedule" is stable between launches
                entries = sp.entries.shuffled(Random(sp.id.hashCode().toLong())),
                slotMs = sp.slotMs
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
        return Program(e, start, start + ch.spanOf(e))
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
            val span = ch.spanOf(e)
            out.add(Program(e, start, start + span))
            start += span
            idx = (idx + 1) % ch.entries.size
        }
        return out
    }
}
