package com.strata.tv.data.repo

import android.util.Log
import com.strata.tv.data.db.MovieDao
import com.strata.tv.data.db.SeriesDao
import com.strata.tv.data.settings.SettingsRepository
import com.strata.tv.domain.CategoryRegion
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Library-wide hide/ignore actions invoked from the long-press context
 * menu on movie and show cards.  Three flavours:
 *
 * - [hideItem] — hides one specific movie or show by `content_id` /
 *   `series_title`.  Cheap, immediate, no settings change.
 * - [ignoreGenre] — adds a TMDB genre token to [com.strata.tv.data.settings.AppSettings.excludedGenres]
 *   and immediately flips `hidden = 1` on every matching movie / series
 *   row so the rails update without waiting for a fresh sync.
 * - [ignoreLanguage] — adds a language code to
 *   [com.strata.tv.data.settings.AppSettings.excludedLanguages] and
 *   hides every matching row.  Solves the "weird foreign things still
 *   pop up" case where an item slipped past the
 *   [com.strata.tv.data.settings.AppSettings.wantedLanguages] whitelist
 *   at enrichment time.
 *
 * All three operations are persistent — the settings rules survive
 * across re-syncs so newly imported items in those genres / languages
 * also get hidden by the enrichment services.
 */
@Singleton
class LibraryFilterRepository @Inject constructor(
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val settingsRepo: SettingsRepository,
) {
    companion object {
        private const val TAG = "LibraryFilterRepo"
    }

    suspend fun hideMovie(contentId: String) {
        // setUserHidden (not setHidden): marks the sticky user_hidden flag
        // so the manual ignore survives re-sync and isn't un-hidden by the
        // post-sync filter recompute.
        movieDao.setUserHiddenByContentId(contentId, true)
    }

    suspend fun hideSeries(seriesTitle: String) {
        seriesDao.setUserHidden(seriesTitle, true)
    }

    /**
     * Hide everything matching [genre] (case-insensitive substring on
     * the comma-separated TMDB genre string) across both the movies
     * and series tables, and add the token to the persistent
     * excluded-genres list so future syncs respect it.
     */
    suspend fun ignoreGenre(genre: String) {
        val trimmed = genre.trim()
        if (trimmed.isEmpty()) return
        settingsRepo.addExcludedGenre(trimmed)
        val movies = movieDao.hideByGenre(trimmed)
        val series = seriesDao.hideByGenre(trimmed)
        Log.i(TAG, "Ignore genre '$trimmed': hid $movies movies, $series series")
    }

    /**
     * Hide everything in [languageCode] (TMDB ISO-639-1, e.g. `"ja"`)
     * and remember the exclusion for future enrichment passes.
     */
    suspend fun ignoreLanguage(languageCode: String) {
        val trimmed = languageCode.trim()
        if (trimmed.isEmpty()) return
        settingsRepo.addExcludedLanguage(trimmed)
        val movies = movieDao.hideByLanguage(trimmed)
        val series = seriesDao.hideByLanguage(trimmed)
        Log.i(TAG, "Ignore language '$trimmed': hid $movies movies, $series series")
    }

    /**
     * Re-evaluate the `hidden` flag on every enriched movie and series
     * against the *current* filter settings.  Called whenever the user
     * tweaks a filter (wanted languages, excluded languages, excluded
     * genres, minimum year) so existing hides un-hide and existing
     * visibles get re-hidden as needed.
     *
     * Operates on enriched rows only (`tmdb_id > 0`) because
     * unenriched rows have empty language / genre / year fields and
     * would all collapse to "visible" — they'll get their hidden
     * state set correctly by the next enrichment pass.
     *
     * Returns the number of rows that flipped state, useful for the
     * caller to decide whether to kick a re-enrichment pass.
     */
    suspend fun recomputeHiddenFlags(): Int {
        val s = settingsRepo.current()
        val wanted = s.wantedLanguages
        val excludedLangs = s.excludedLanguages
        val excludedGenres = s.excludedGenres
        val minYear = s.minimumYear
        val countryWhitelist = s.countryWhitelist
        val excludedCategories = s.excludedCategories

        // Presentation-only: never deletes rows. EN| packs stay visible
        // when UK/GB is selected via CategoryRegion affinity.
        fun regionOrCategoryHidden(groupTitle: String): Boolean {
            if (CategoryRegion.matchesExcludedCategory(groupTitle, excludedCategories)) {
                return true
            }
            return !CategoryRegion.matchesRegion(groupTitle, countryWhitelist)
        }

        fun shouldHideByFilters(language: String, genre: String, year: Int?): Boolean {
            val langWanted = wanted.isEmpty() || language in wanted
            val langExcluded = language in excludedLangs
            val genreExcluded = excludedGenres.any { g ->
                g.isNotEmpty() && genre.contains(g, ignoreCase = true)
            }
            val tooOld = minYear > 0 && year != null && year < minYear
            return !langWanted || langExcluded || genreExcluded || tooOld
        }

        var flipped = 0

        val allMovies = movieDao.allForFilterRecompute()
        val movieToHide = mutableListOf<Int>()
        val movieToShow = mutableListOf<Int>()
        for (m in allMovies) {
            val regionHidden = regionOrCategoryHidden(m.groupTitle)
            val tooOld = minYear > 0 && m.year != null && m.year < minYear
            val should = if (m.tmdbId == 0) {
                tooOld || regionHidden || m.userHidden
            } else {
                shouldHideByFilters(m.language, m.genre, m.year) ||
                    m.userHidden || regionHidden
            }
            if (should && !m.hidden) movieToHide += m.id
            else if (!should && m.hidden) movieToShow += m.id
        }
        for (chunk in movieToHide.chunked(500)) movieDao.hideByIds(chunk).also { flipped += chunk.size }
        for (chunk in movieToShow.chunked(500)) movieDao.showByIds(chunk).also { flipped += chunk.size }

        val allSeries = seriesDao.allIncludingHidden()
        val seriesToHide = mutableListOf<Int>()
        val seriesToShow = mutableListOf<Int>()
        for (s2 in allSeries) {
            val regionHidden = regionOrCategoryHidden(s2.groupTitle)
            val should = if (s2.tmdbId == 0) {
                regionHidden || s2.userHidden
            } else {
                shouldHideByFilters(s2.language, s2.genre, s2.firstAirYear) ||
                    s2.userHidden || regionHidden
            }
            if (should && !s2.hidden) seriesToHide += s2.id
            else if (!should && s2.hidden) seriesToShow += s2.id
        }
        for (chunk in seriesToHide.chunked(500)) seriesDao.hideByIds(chunk).also { flipped += chunk.size }
        for (chunk in seriesToShow.chunked(500)) seriesDao.showByIds(chunk).also { flipped += chunk.size }

        Log.i(
            TAG,
            "Recompute filters: ${movieToHide.size}/${movieToShow.size} movies hide/show, " +
                "${seriesToHide.size}/${seriesToShow.size} series hide/show. " +
                "Settings: region=$countryWhitelist wanted=$wanted " +
                "excludedCats=${excludedCategories.size} minYear=$minYear",
        )
        return flipped
    }
}
