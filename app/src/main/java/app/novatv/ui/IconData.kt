package app.novatv.ui

// GENERATED from icons.py - neon line-art icons on a 48x48 grid

class IconDef(val label: String, val paths: List<String>, val c1: Long, val c2: Long)

object IconData {
    val icons: Map<String, IconDef> = mapOf(
        "action" to IconDef("Action", listOf("M28 4 L10 27 H22 L19 44 L38 20 H26 Z"), 0xFFFF4D5E, 0xFFFF9A3C),
        "adventure" to IconDef("Adventure", listOf("M6 24 A18 18 0 1 0 42 24 A18 18 0 1 0 6 24 Z", "M24 6 V10 M24 38 V42 M6 24 H10 M38 24 H42", "M31 17 L27 27 L17 31 L21 21 Z"), 0xFF33F5C0, 0xFF2AA8FF),
        "animation" to IconDef("Animation", listOf("M22 6 C23 16 26 20 36 22 C26 24 23 28 22 38 C21 28 18 24 8 22 C18 20 21 16 22 6 Z", "M38 30 C38.5 34 40 35.5 44 36 C40 36.5 38.5 38 38 42 C37.5 38 36 36.5 32 36 C36 35.5 37.5 34 38 30 Z"), 0xFFFF6BD6, 0xFF9B6BFF),
        "comedy" to IconDef("Comedy", listOf("M6 24 A18 18 0 1 0 42 24 A18 18 0 1 0 6 24 Z", "M17 18 V23 M31 18 V23", "M13 28 H35 C34 36 29 40 24 40 C19 40 14 36 13 28 Z"), 0xFFFFD93D, 0xFFFF8A3D),
        "crime" to IconDef("Crime", listOf("M24 5 L39 10 V23 C39 33 32 40 24 44 C16 40 9 33 9 23 V10 Z", "M24 15 L26.5 21 L33 21.5 L28 25.5 L29.5 32 L24 28.5 L18.5 32 L20 25.5 L15 21.5 L21.5 21 Z"), 0xFF5C7CFF, 0xFF2AD4FF),
        "documentary" to IconDef("Documentary", listOf("M6 24 A18 18 0 1 0 42 24 A18 18 0 1 0 6 24 Z", "M6 24 H42", "M24 6 C16 14 16 34 24 42 C32 34 32 14 24 6", "M10 15 Q24 20 38 15 M10 33 Q24 28 38 33"), 0xFF38E0A0, 0xFF1FA8D8),
        "drama" to IconDef("Drama", listOf("M9 8 H39 V24 C39 34 32 41 24 41 C16 41 9 34 9 24 Z", "M15 20 Q18.5 16 22 20 M26 20 Q29.5 16 33 20", "M17 34 Q24 27 31 34"), 0xFFB66BFF, 0xFFFF5CA8),
        "family" to IconDef("Family", listOf("M5 23 L24 7 L43 23 M10 20 V41 H38 V20", "M24 36 C17 31 16 26 19 24 C21.5 22.5 23.5 24 24 26 C24.5 24 26.5 22.5 29 24 C32 26 31 31 24 36 Z"), 0xFFFFB347, 0xFFFF6B9A),
        "fantasy" to IconDef("Fantasy", listOf("M15 7 H33 L42 17 L24 43 L6 17 Z", "M15 7 L19 17 L24 43 L29 17 L33 7 M19 17 H29"), 0xFFA06BFF, 0xFF47E6FF),
        "history" to IconDef("History", listOf("M13 5 H35 M13 43 H35", "M16 5 V12 L24 24 L32 12 V5 M16 43 V36 L24 24 L32 36 V43", "M21 38 H27"), 0xFFE0B36B, 0xFFFF8B5C),
        "horror" to IconDef("Horror", listOf("M10 42 V22 C10 12 16 6 24 6 C32 6 38 12 38 22 V42 L33 37 L28.5 42 L24 37 L19.5 42 L15 37 Z", "M18 21 V25 M30 21 V25", "M21 31 Q24 34 27 31"), 0xFFFF3355, 0xFF8B2BFF),
        "music" to IconDef("Music", listOf("M20 35 V11 L40 7 V31 M20 17 L40 13", "M10 35 A5 4 0 1 0 20 35 A5 4 0 1 0 10 35 Z", "M30 31 A5 4 0 1 0 40 31 A5 4 0 1 0 30 31 Z"), 0xFFFF5CD6, 0xFF5CC8FF),
        "mystery" to IconDef("Mystery", listOf("M3 24 C9 13 16 9 24 9 C32 9 39 13 45 24 C39 35 32 39 24 39 C16 39 9 35 3 24 Z", "M17 24 A7 7 0 1 0 31 24 A7 7 0 1 0 17 24 Z", "M22 24 A2 2 0 1 0 26 24 A2 2 0 1 0 22 24 Z"), 0xFF7B6BFF, 0xFF2AF0D0),
        "romance" to IconDef("Romance", listOf("M24 41 C8 29 6 18 12 12 C17 8 22 10 24 15 C26 10 31 8 36 12 C42 18 40 29 24 41 Z"), 0xFFFF4F8B, 0xFFFF8FB3),
        "scifi" to IconDef("Sci-Fi", listOf("M24 4 C32 11 34 21 32 33 H16 C14 21 16 11 24 4 Z", "M20.5 20 A3.5 3.5 0 1 0 27.5 20 A3.5 3.5 0 1 0 20.5 20 Z", "M16 27 L9 37 L16 35 M32 27 L39 37 L32 35", "M20 37 L24 45 L28 37"), 0xFF2AF5FF, 0xFF7A5CFF),
        "thriller" to IconDef("Thriller", listOf("M12 24 A12 12 0 1 0 36 24 A12 12 0 1 0 12 24 Z", "M24 4 V14 M24 34 V44 M4 24 H14 M34 24 H44", "M22 24 A2 2 0 1 0 26 24 A2 2 0 1 0 22 24 Z"), 0xFFFF5C5C, 0xFFFFC04D),
        "war" to IconDef("War", listOf("M9 9 L35 35 M27 35 L35 27 M35 35 L41 41", "M39 9 L13 35 M13 27 L21 35 M13 35 L7 41"), 0xFF9AA8B8, 0xFFFF6B4D),
        "western" to IconDef("Western", listOf("M20 42 V12 C20 5 28 5 28 12 V42", "M20 28 H14 C11 28 10 26 10 24 V18", "M28 24 H34 C37 24 38 22 38 20 V14", "M12 42 H36"), 0xFFFFB04D, 0xFFE0703A),
        "kids" to IconDef("Kids", listOf("M24 6 C33 6 38 13 36 21 C34 28 28 31 24 33 C20 31 14 28 12 21 C10 13 15 6 24 6 Z", "M22 33 L26 33 L24 36 Z", "M24 36 C20 40 28 42 24 46"), 0xFF5CF0FF, 0xFFFF6BD6),
        "sport" to IconDef("Sports", listOf("M7 24 A17 17 0 1 0 41 24 A17 17 0 1 0 7 24 Z", "M24 7 V41 M7 24 H41", "M11 11 C18 17 18 31 11 37 M37 11 C30 17 30 31 37 37"), 0xFF4DFF9A, 0xFF2AB8FF),
        "news" to IconDef("News", listOf("M21 24 A3 3 0 1 0 27 24 A3 3 0 1 0 21 24 Z", "M15 17 C11 21 11 27 15 31 M33 17 C37 21 37 27 33 31", "M10 12 C3 19 3 29 10 36 M38 12 C45 19 45 29 38 36"), 0xFF4DB8FF, 0xFF8A7BFF),
        "reality" to IconDef("Reality", listOf("M5 14 H31 V34 H5 Z", "M31 22 L43 15 V33 L31 26", "M10.0 19.5 A1.5 1.5 0 1 0 13.0 19.5 A1.5 1.5 0 1 0 10.0 19.5 Z"), 0xFFFF5C9A, 0xFFFFB04D),
        "biography" to IconDef("Biography", listOf("M16 15 A8 8 0 1 0 32 15 A8 8 0 1 0 16 15 Z", "M7 43 C7 32 14 28 24 28 C34 28 41 32 41 43"), 0xFF6BFFD0, 0xFF4D8BFF),
        "movies" to IconDef("Movies", listOf("M6 8 H42 V40 H6 Z", "M14 8 V40 M34 8 V40", "M6 16 H14 M6 24 H14 M6 32 H14 M34 16 H42 M34 24 H42 M34 32 H42", "M21 18 L29 24 L21 30 Z"), 0xFF5C8BFF, 0xFFB65CFF),
        "tv" to IconDef("Series", listOf("M6 12 H42 V34 H6 Z", "M18 41 H30 M24 34 V41", "M18 5 L24 12 L30 5"), 0xFF2AF5FF, 0xFFB65CFF),
        "mix" to IconDef("Mix", listOf("M6 14 H14 C24 14 24 34 34 34 H42 M36 28 L42 34 L36 40", "M6 34 H14 C18 34 20 31 21 29 M27 19 C28 17 30 14 34 14 H42 M36 8 L42 14 L36 20"), 0xFFFF5CD6, 0xFF2AF5FF),
        "network" to IconDef("Network", listOf("M24 5 L40 14 V34 L24 43 L8 34 V14 Z", "M20 24 A4 4 0 1 0 28 24 A4 4 0 1 0 20 24 Z", "M24 20 V5 M27.5 26 L40 34 M20.5 26 L8 34"), 0xFF4DE8FF, 0xFF6B7BFF)
    )
}
