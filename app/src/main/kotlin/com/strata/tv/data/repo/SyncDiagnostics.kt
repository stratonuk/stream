package com.strata.tv.data.repo

import java.time.Instant

/**
 * Counts from the last catalogue sync — provider → parse → Room → UI.
 * Never includes credentials or authenticated URLs.
 */
data class SyncDiagnostics(
    val lastSyncAt: Instant? = null,
    val lastError: String? = null,
    val syncPath: String = "",
    val liveCategories: Int = 0,
    val vodCategories: Int = 0,
    val seriesCategories: Int = 0,
    val totalCategories: Int = 0,
    val liveProvider: Int = 0,
    val liveParsed: Int = 0,
    val liveStored: Int = 0,
    val liveVisible: Int = 0,
    val moviesProvider: Int = 0,
    val moviesParsed: Int = 0,
    val moviesStored: Int = 0,
    val moviesVisible: Int = 0,
    val seriesProvider: Int = 0,
    val seriesParsed: Int = 0,
    val seriesStored: Int = 0,
    val seriesVisible: Int = 0,
    val hiddenByRegion: Int = 0,
    val hiddenByCategory: Int = 0,
    val hiddenByLanguage: Int = 0,
    val hiddenCategoriesEstimate: Int = 0,
) {
    companion object {
        val Empty = SyncDiagnostics()
    }
}
