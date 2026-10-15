package com.strata.tv.ui.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strata.tv.data.db.CatalogueCategory
import com.strata.tv.data.db.MovieDao
import com.strata.tv.data.db.MovieListItem
import com.strata.tv.data.db.WatchlistDao
import com.strata.tv.data.db.WatchlistEntity
import com.strata.tv.domain.CategoryRegion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

/**
 * Drives [MoviesScreen].
 *
 * Full catalogue is organised by provider category (`group-title`).
 * Each rail loads a small preview; "See all" pages the rest so Fire Stick
 * never holds the entire VOD library in memory.
 */
@HiltViewModel
class MoviesViewModel @Inject constructor(
    private val movieDao: MovieDao,
    private val watchlistDao: WatchlistDao,
    private val libraryFilter: com.strata.tv.data.repo.LibraryFilterRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MoviesUiState.Empty)
    val state: StateFlow<MoviesUiState> = _state.asStateFlow()

    val watchlistIds: StateFlow<Set<String>> = watchlistDao.watchAll()
        .map { list -> list.map { it.contentId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch(Dispatchers.IO) { loadCatalogue() }
    }

    private suspend fun loadCatalogue() {
        // Pull provider logos into empty poster_url rows so rails aren't
        // initials-only after a sync that stored art on content_items only.
        runCatching { movieDao.backfillPostersFromTvgLogo() }

        val raw = movieDao.movieCategories()
        val sorted = raw.sortedWith(
            compareByDescending<CatalogueCategory> {
                CategoryRegion.isPreferredEnglishUk(it.groupTitle)
            }
                .thenByDescending { it.count }
                .thenBy { CategoryRegion.displayName(it.groupTitle).lowercase() },
        )
        if (sorted.isEmpty()) {
            _state.value = MoviesUiState.Empty
            return
        }

        // Show category shells immediately so the screen isn't blank while
        // rail previews load.
        _state.value = MoviesUiState(
            hero = null,
            categoryRails = sorted.map { CategoryRail(it, emptyList()) },
            selectedCategory = null,
            categoryItems = emptyList(),
            categoryHasMore = false,
            loading = true,
        )

        val rails = loadRailPreviews(sorted)
        val hero = rails
            .asSequence()
            .flatMap { it.movies.asSequence() }
            .firstOrNull { it.posterUrl.isNotBlank() }
            ?: rails.firstOrNull()?.movies?.firstOrNull()

        _state.value = MoviesUiState(
            hero = hero,
            categoryRails = rails,
            selectedCategory = null,
            categoryItems = emptyList(),
            categoryHasMore = false,
            loading = false,
        )
    }

    private suspend fun loadRailPreviews(
        categories: List<CatalogueCategory>,
    ): List<CategoryRail> = coroutineScope {
        val gate = Semaphore(permits = 4)
        categories.map { cat ->
            async {
                gate.withPermit {
                    val items = movieDao.byGroupTitlePaged(
                        groupTitle = cat.groupTitle,
                        limit = RAIL_PREVIEW,
                        offset = 0,
                    )
                    CategoryRail(category = cat, movies = items)
                }
            }
        }.awaitAll()
    }

    fun openCategory(groupTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = movieDao.byGroupTitlePaged(groupTitle, CATEGORY_PAGE, 0)
            _state.update {
                it.copy(
                    selectedCategory = groupTitle,
                    categoryItems = items,
                    categoryHasMore = items.size >= CATEGORY_PAGE,
                )
            }
        }
    }

    fun loadMoreCategory() {
        val current = _state.value
        val groupTitle = current.selectedCategory ?: return
        if (!current.categoryHasMore) return
        viewModelScope.launch(Dispatchers.IO) {
            val more = movieDao.byGroupTitlePaged(
                groupTitle = groupTitle,
                limit = CATEGORY_PAGE,
                offset = current.categoryItems.size,
            )
            _state.update {
                if (it.selectedCategory != groupTitle) return@update it
                it.copy(
                    categoryItems = it.categoryItems + more,
                    categoryHasMore = more.size >= CATEGORY_PAGE,
                )
            }
        }
    }

    fun closeCategory() {
        _state.update {
            it.copy(
                selectedCategory = null,
                categoryItems = emptyList(),
                categoryHasMore = false,
            )
        }
    }

    fun addToWatchlist(contentId: String, title: String, artworkUrl: String) {
        viewModelScope.launch {
            watchlistDao.add(
                WatchlistEntity(
                    contentId = contentId,
                    contentType = "movie",
                    title = title,
                    artworkUrl = artworkUrl,
                ),
            )
        }
    }

    fun removeFromWatchlist(contentId: String) {
        viewModelScope.launch { watchlistDao.remove(contentId) }
    }

    fun hideMovie(contentId: String) {
        viewModelScope.launch { libraryFilter.hideMovie(contentId) }
    }

    fun ignoreGenre(genre: String) {
        viewModelScope.launch { libraryFilter.ignoreGenre(genre) }
    }

    fun ignoreLanguage(languageCode: String) {
        viewModelScope.launch { libraryFilter.ignoreLanguage(languageCode) }
    }

    companion object {
        /** Posters shown on each category rail before "See all". */
        const val RAIL_PREVIEW = 40

        /** Page size when browsing a full category. */
        const val CATEGORY_PAGE = 80
    }
}

data class CategoryRail(
    val category: CatalogueCategory,
    val movies: List<MovieListItem>,
) {
    val title: String get() = CategoryRegion.displayName(category.groupTitle)
    val totalCount: Int get() = category.count
    val hasMore: Boolean get() = totalCount > movies.size
}

data class MoviesUiState(
    val hero: MovieListItem?,
    val categoryRails: List<CategoryRail>,
    val selectedCategory: String?,
    val categoryItems: List<MovieListItem>,
    val categoryHasMore: Boolean,
    val loading: Boolean = false,
) {
    companion object {
        val Empty = MoviesUiState(
            hero = null,
            categoryRails = emptyList(),
            selectedCategory = null,
            categoryItems = emptyList(),
            categoryHasMore = false,
            loading = true,
        )
    }
}
