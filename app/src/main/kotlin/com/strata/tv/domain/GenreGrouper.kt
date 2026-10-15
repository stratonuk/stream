package com.strata.tv.domain

/**
 * Shared genre-rail grouping helper (used by Home and any screen that
 * still wants TMDB-genre buckets).  Movies / Shows catalogue screens
 * now organise by provider category instead.
 */
object GenreGrouper {

    private const val MIN_RAIL_SIZE = 3
    private const val MAX_RAILS = 15
    private val SPLIT_DELIMITERS = charArrayOf(',', '|', '/')

    fun <T> groupByPrimaryGenre(
        items: List<T>,
        genreOf: (T) -> String,
    ): List<Pair<String, List<T>>> {
        val byGenre = HashMap<String, MutableList<T>>()
        for (item in items) {
            val genre = genreOf(item).splitToSequence(*SPLIT_DELIMITERS)
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() }
                ?: "Uncategorised"
            byGenre.getOrPut(genre) { ArrayList() }.add(item)
        }
        return byGenre.entries
            .asSequence()
            .filter { it.value.size >= MIN_RAIL_SIZE }
            .sortedWith(
                compareBy<Map.Entry<String, MutableList<T>>> { it.key == "Uncategorised" }
                    .thenByDescending { it.value.size },
            )
            .take(MAX_RAILS)
            .map { it.key to it.value }
            .toList()
    }
}
