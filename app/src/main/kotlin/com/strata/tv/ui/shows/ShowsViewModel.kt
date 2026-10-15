package com.strata.tv.ui.shows

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strata.tv.data.db.CatalogueCategory
import com.strata.tv.data.db.SeriesDao
import com.strata.tv.data.db.SeriesListItem
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
 * Drives [ShowsScreen].
 *
 * Full show catalogue organised by provider category (`group-title`),
 * with paged "See all" so large libraries stay Fire Stick-safe.
 */
@HiltViewModel
class ShowsViewModel @Inject constructor(
    private val seriesDao: SeriesDao,
    private val watchlistDao: WatchlistDao,
    private val libraryFilter: com.strata.tv.data.repo.LibraryFilterRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ShowsUiState.Empty)
    val state: StateFlow<ShowsUiState> = _state.asStateFlow()

    val watchlistIds: StateFlow<Set<String>> = watchlistDao.watchAll()
        .map { list -> list.map { it.contentId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        viewModelScope.launch(Dispatchers.IO) { loadCatalogue() }
    }

    private suspend fun loadCatalogue() {
        val raw = seriesDao.seriesCategories()
        val sorted = raw.sortedWith(
            compareByDescending<CatalogueCategory> {
                CategoryRegion.isPreferredEnglishUk(it.groupTitle)
            }
                .thenByDescending { it.count }
                .thenBy { CategoryRegion.displayName(it.groupTitle).lowercase() },
        )
        if (sorted.isEmpty()) {
            _state.value = ShowsUiState.Empty.copy(loading = false)
            return
        }

        _state.value = ShowsUiState(
            hero = null,
            categoryRails = sorted.map { ShowCategoryRail(it, emptyList()) },
            selectedCategory = null,
            categoryItems = emptyList(),
            categoryHasMore = false,
            loading = true,
        )

        val rails = loadRailPreviews(sorted)
        val hero = rails
            .asSequence()
            .flatMap { it.shows.asSequence() }
            .firstOrNull { it.posterUrl.isNotBlank() || it.backdropUrl.isNotBlank() }
            ?: rails.firstOrNull()?.shows?.firstOrNull()

        _state.value = ShowsUiState(
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
    ): List<ShowCategoryRail> = coroutineScope {
        val gate = Semaphore(permits = 4)
        categories.map { cat ->
            async {
                gate.withPermit {
                    val items = seriesDao.byGroupTitlePaged(
                        groupTitle = cat.groupTitle,
                        limit = RAIL_PREVIEW,
                        offset = 0,
                    )
                    ShowCategoryRail(category = cat, shows = items)
                }
            }
        }.awaitAll()
    }

    fun openCategory(groupTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = seriesDao.byGroupTitlePaged(groupTitle, CATEGORY_PAGE, 0)
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
            val more = seriesDao.byGroupTitlePaged(
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

    fun addToWatchlist(seriesTitle: String, artworkUrl: String) {
        viewModelScope.launch {
            watchlistDao.add(
                WatchlistEntity(
                    contentId = seriesTitle,
                    contentType = "show",
                    title = seriesTitle,
                    artworkUrl = artworkUrl,
                ),
            )
        }
    }

    fun removeFromWatchlist(seriesTitle: String) {
        viewModelScope.launch { watchlistDao.remove(seriesTitle) }
    }

    fun hideSeries(seriesTitle: String) {
        viewModelScope.launch { libraryFilter.hideSeries(seriesTitle) }
    }

    fun ignoreGenre(genre: String) {
        viewModelScope.launch { libraryFilter.ignoreGenre(genre) }
    }

    fun ignoreLanguage(languageCode: String) {
        viewModelScope.launch { libraryFilter.ignoreLanguage(languageCode) }
    }

    companion object {
        const val RAIL_PREVIEW = 40
        const val CATEGORY_PAGE = 80
    }
}

data class ShowCategoryRail(
    val category: CatalogueCategory,
    val shows: List<SeriesListItem>,
) {
    val title: String get() = CategoryRegion.displayName(category.groupTitle)
    val totalCount: Int get() = category.count
    val hasMore: Boolean get() = totalCount > shows.size
}

data class ShowsUiState(
    val hero: SeriesListItem?,
    val categoryRails: List<ShowCategoryRail>,
    val selectedCategory: String?,
    val categoryItems: List<SeriesListItem>,
    val categoryHasMore: Boolean,
    val loading: Boolean = false,
) {
    companion object {
        val Empty = ShowsUiState(
            hero = null,
            categoryRails = emptyList(),
            selectedCategory = null,
            categoryItems = emptyList(),
            categoryHasMore = false,
            loading = true,
        )
    }
}
