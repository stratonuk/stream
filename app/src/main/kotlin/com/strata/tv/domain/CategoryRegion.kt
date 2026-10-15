package com.strata.tv.domain

/**
 * Region / language prefixes on Xtream `group-title` / category names.
 *
 * Providers disagree on labelling English packs:
 * - `UK | BBC`, `GB | Sky`
 * - `EN | Movies`, `EN | Series`  ← common on panels like likane.cc
 *
 * These are **not** the same token, but a UK-first preference must keep
 * EN-labelled English catalogue rows.  Matching expands the user's
 * region whitelist with that affinity; empty whitelist means "everywhere".
 */
object CategoryRegion {

    /** Prefixes treated as preferred English / UK when any of UK/GB/EN is selected. */
    val ENGLISH_UK_AFFINITY: Set<String> = setOf(
        "UK", "GB", "EN", "ENG", "ENGLISH", "UNITED KINGDOM",
    )

    private val prefixSplit = Regex("""\s*[|:\-]\s*""")

    /** Uppercase region token before the first `|` / `:` / `-`, or empty. */
    fun prefixOf(groupTitle: String): String {
        val trimmed = groupTitle.trim()
        if (trimmed.isEmpty()) return ""
        // Only treat short leading tokens as region codes (EN, UK, US, EX-YU…).
        val parts = trimmed.split(prefixSplit, limit = 2)
        if (parts.size < 2) return ""
        val candidate = parts[0].trim().uppercase()
        if (candidate.isEmpty()) return ""
        // Prefer known affinity tokens even when they contain spaces
        // (e.g. "United Kingdom | News").
        if (candidate in ENGLISH_UK_AFFINITY) return candidate
        // Reject long / sentence-like leading segments that aren't region codes.
        if (candidate.length > 12) return ""
        if (candidate.any { it.isWhitespace() }) return ""
        return candidate
    }

    /**
     * Display label with region prefix stripped: `EN | BBC` → `BBC`.
     * Original category id / raw group title stays in the database.
     */
    fun displayName(groupTitle: String): String {
        val trimmed = groupTitle.trim()
        if (trimmed.isEmpty()) return trimmed
        val prefix = prefixOf(trimmed)
        if (prefix.isEmpty()) return trimmed
        val stripped = trimmed
            .replace(Regex("""^${Regex.escape(prefix)}\s*[|:\-]\s*""", RegexOption.IGNORE_CASE), "")
            .trim()
        return stripped.ifEmpty { trimmed }
    }

    /**
     * Expand a user region whitelist so UK/GB selection also accepts EN
     * (and vice versa).  Empty input stays empty (= accept all regions).
     */
    fun expandWhitelist(whitelist: Set<String>): Set<String> {
        if (whitelist.isEmpty()) return emptySet()
        val upper = whitelist.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.toMutableSet()
        if (upper.any { it in ENGLISH_UK_AFFINITY }) {
            upper.addAll(ENGLISH_UK_AFFINITY)
        }
        return upper
    }

    /**
     * Whether [groupTitle] should be **visible** under the current region
     * whitelist.  Never used to delete rows — presentation / `hidden` only.
     *
     * - Empty whitelist → keep everything (region-wise).
     * - Prefixed title → prefix must be in the expanded whitelist.
     * - Unprefixed title → keep unless [ForeignContentFilter] positively
     *   identifies a non-whitelisted country/language.
     */
    fun matchesRegion(groupTitle: String, countryWhitelist: Set<String>): Boolean {
        val expanded = expandWhitelist(countryWhitelist)
        if (expanded.isEmpty()) return true
        val prefix = prefixOf(groupTitle)
        if (prefix.isNotEmpty()) {
            return prefix in expanded
        }
        return !ForeignContentFilter.shouldExclude(groupTitle, expanded)
    }

    /** Preferred English/UK pack for sort-to-front. */
    fun isPreferredEnglishUk(groupTitle: String): Boolean {
        val prefix = prefixOf(groupTitle)
        if (prefix.isNotEmpty()) return prefix in ENGLISH_UK_AFFINITY
        // Neutral names (Drama, Entertainment) count as preferred when
        // they don't positively identify as foreign.
        return ForeignContentFilter.countryFor(groupTitle) == null
    }

    fun matchesExcludedCategory(groupTitle: String, excludedCategories: Set<String>): Boolean {
        if (excludedCategories.isEmpty()) return false
        val g = groupTitle.lowercase()
        return excludedCategories.any { needle ->
            needle.isNotBlank() && g.contains(needle.lowercase())
        }
    }
}
