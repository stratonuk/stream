package com.strata.tv.domain

/**
 * Intelligent live-TV category normalisation for STREAM.
 *
 * Provider categories like "EN | BBC" or "UK | Sky Sports" are never shown
 * raw. We strip language prefixes, map channels into local display buckets,
 * and allow a single channel to appear in multiple categories
 * (e.g. BBC One → UK + BBC).
 *
 * Defaults: English preferred, UK-first, adult hidden at sync layer,
 * international hidden unless the user enables it in settings later.
 */
object ChannelCategorizer {

    /** Chip bar order — only buckets with real channels are shown. */
    val displayOrder: List<String> = listOf(
        "All",
        "Favourites",
        "UK",
        "BBC",
        "ITV",
        "Channel 4",
        "Channel 5",
        "Sky",
        "Football",
        "Sports",
        "Movies",
        "Kids",
        "News",
        "Documentaries",
        "Entertainment",
        "International",
    )

    /** Language / region prefixes commonly glued onto IPTV group titles. */
    private val languagePrefix = Regex(
        "^(EN|UK|US|FR|DE|ES|IT|PT|AR|TR|PL|NL|SE|NO|DK|FI|RU|IN|PK|BR|AU|CA|IE)\\s*[|:\\-]\\s*",
        RegexOption.IGNORE_CASE,
    )

    private val qualityPrefix = Regex(
        "^(new\\s+uk[hs]d\\s*:?\\s*|" +
            "uk\\s*\\+\\d+s?\\s*:?\\s*|" +
            "uk\\s+fhd\\s+:?\\s*|" +
            "uk:|ukhd|uksd|ukfhd|" +
            "hevc\\s+fhd|hevc\\s+hd|hevc|" +
            "fhd\\s*\\|?)\\s*",
        RegexOption.IGNORE_CASE,
    )

    private val adultHints = listOf(
        "xxx", "adult", "porn", "18+", "+18", "erotica", "onlyfans",
    )

    private val ukHints = listOf(
        "uk ", " uk", "|uk", "united kingdom", "britain", "british",
        "bbc", "itv", "channel 4", "channel 5", "sky ", "stv", "s4c",
        "london live", "talking pictures", "gb news", "dave", "gold",
        "alibi", "drama", "really", "e4", "more4", "film4", "cbeebies",
        "cbbc", "tnt sport", "bt sport", "premier sports",
    )

    private val bucketRules: Map<String, List<String>> = mapOf(
        "BBC" to listOf(
            "bbc1", "bbc 1", "bbc one", "bbc2", "bbc 2", "bbc two",
            "bbc three", "bbc four", "bbc news", "bbc red", "bbc parliament",
            "cbeebies", "cbbc", "bbc scotland", "bbc wales", "bbc alba",
        ),
        "ITV" to listOf(
            "itv1", "itv 1", "itv2", "itv 2", "itv3", "itv 3", "itv4", "itv 4",
            "itvx", "itv hub", "citv", "stv",
        ),
        "Channel 4" to listOf(
            "channel 4", "channel4", "e4", "more4", "4seven", "film4", "4music",
        ),
        "Channel 5" to listOf(
            "channel 5", "channel5", "5star", "5usa", "5select", "5spike", "5action",
        ),
        "Sky" to listOf(
            "sky one", "sky atlantic", "sky witness", "sky max", "sky comedy",
            "sky crime", "sky arts", "sky showcase", "sky news", "sky sports",
            "sky cinema", "sky kids", "sky sci-fi", "sky documentaries",
            "sky nature", "sky history",
        ),
        "Football" to listOf(
            "sky sports", "tnt sport", "bt sport", "premier league", "mutv",
            "lfctv", "lfc", "spurs", "arsenal", "chelsea tv", "football",
            "soccer", "dazn", "eurosport",
        ),
        "Sports" to listOf(
            "sky sports", "tnt sport", "bt sport", "eurosport", "espn",
            "premier sports", "racing", "at the races", "golf", "tennis",
            "cricket", "boxing", "ufc", "formula", " f1 ", "dazn", "viaplay",
            "supersport", "setanta",
        ),
        "Movies" to listOf(
            "sky cinema", "film4", "movies", "cinema", "great movies",
            "talking pictures", "sony movies", "horror channel",
        ),
        "Kids" to listOf(
            "cbeebies", "cbbc", "cartoon network", "cartoonito",
            "nickelodeon", "nick jr", "nicktoons", "baby tv", "boomerang",
            "pop", "sky kids", "disney jr", "disney junior",
        ),
        "News" to listOf(
            "bbc news", "sky news", "gb news", "cnn", "al jazeera",
            "fox news", "cnbc", "bloomberg", "euronews", "france 24",
            "nhk world", "news",
        ),
        "Documentaries" to listOf(
            "discovery", "national geo", "nat geo", "history", "eden",
            "quest", "smithsonian", "dmax", "yesterday", "crime inv",
            "true crime", "documentary", "documentaries",
        ),
        "Entertainment" to listOf(
            "comedy central", "tlc", "food network", "london live",
            "virgin", "peacock", "paramount", "hulu", "britbox",
        ),
        "International" to listOf(
            "arabic", "pakistan", "india", "turkish", "polish", "african",
            "asian", "zee", "colors", "geo news", "ary digital", "hum ",
            "ptv", "france ", "rtl", "rai ", "globo",
        ),
    )

    /**
     * Strip recognised language / quality prefixes for display mapping.
     * Does not mutate provider data — local display only.
     */
    fun normalizeGroupTitle(raw: String): String {
        var s = raw.trim()
        s = languagePrefix.replace(s, "")
        s = qualityPrefix.replace(s, "")
        return s.trim().ifBlank { raw.trim() }
    }

    fun isAdult(displayName: String, groupTitle: String): Boolean {
        val hay = "$displayName $groupTitle".lowercase()
        return adultHints.any { hay.contains(it) }
    }

    fun isLikelyUk(displayName: String, groupTitle: String): Boolean {
        val hay = "$displayName $groupTitle".lowercase()
        // Explicit UK region in group title
        if (Regex("(^|[|:\\-\\s])uk([|:\\-\\s]|$)", RegexOption.IGNORE_CASE).containsMatchIn(groupTitle)) {
            return true
        }
        // EN alone is language, not region — only treat as UK with extra hints
        return ukHints.any { hay.contains(it) }
    }

    /**
     * Primary category kept for DB compatibility / sync writes.
     */
    fun categorise(displayName: String, groupTitle: String): String {
        val all = categoriesFor(displayName, groupTitle)
        // Prefer specific broadcaster buckets over generic UK/Sports
        val preferred = listOf(
            "BBC", "ITV", "Channel 4", "Channel 5", "Sky",
            "Football", "Sports", "Movies", "Kids", "News",
            "Documentaries", "Entertainment", "UK", "International",
        )
        return preferred.firstOrNull { it in all } ?: "Entertainment"
    }

    /**
     * All local display categories a channel belongs to.
     */
    fun categoriesFor(displayName: String, groupTitle: String): Set<String> {
        if (isAdult(displayName, groupTitle)) return emptySet()

        val strippedName = displayName.lowercase().replace(qualityPrefix, "").trim()
        val strippedGroup = normalizeGroupTitle(groupTitle).lowercase()
        val hay = "$strippedName $strippedGroup"

        val hits = linkedSetOf<String>()
        for ((bucket, patterns) in bucketRules) {
            if (patterns.any { hay.contains(it) }) {
                hits += bucket
            }
        }

        if (isLikelyUk(displayName, groupTitle)) {
            hits += "UK"
        }

        // International only when clearly non-UK and no UK broadcaster match
        val ukLocal = hits.intersect(
            setOf("BBC", "ITV", "Channel 4", "Channel 5", "Sky", "UK"),
        )
        if (ukLocal.isEmpty() && bucketRules.getValue("International").any { hay.contains(it) }) {
            hits += "International"
        }

        if (hits.isEmpty()) {
            hits += if (isLikelyUk(displayName, groupTitle)) "UK" else "Entertainment"
        }

        return hits
    }
}
