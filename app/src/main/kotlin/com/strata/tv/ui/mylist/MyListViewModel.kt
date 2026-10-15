package com.strata.tv.ui.mylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strata.tv.data.db.ChannelDao
import com.strata.tv.data.db.ContentDao
import com.strata.tv.data.db.WatchlistDao
import com.strata.tv.data.db.WatchlistEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class MyListMovie(val contentId: String, val title: String, val year: String?, val posterUrl: String?)
data class MyListShow(val seriesTitle: String, val year: String?, val posterUrl: String?)
data class MyListChannel(val contentId: String, val displayName: String, val logoUrl: String?)

data class MyListUiState(
    val movies: List<MyListMovie> = emptyList(),
    val shows: List<MyListShow> = emptyList(),
    val channels: List<MyListChannel> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyListViewModel @Inject constructor(
    watchlistDao: WatchlistDao,
    channelDao: ChannelDao,
    contentDao: ContentDao,
) : ViewModel() {

    val state: StateFlow<MyListUiState> = combine(
        watchlistDao.watchAll(),
        channelDao.watchFavourites(),
    ) { watchlist, favChannels ->
        watchlist to favChannels
    }.flatMapLatest { (watchlist, favChannels) ->
        flow {
            val liveById = runCatching { contentDao.byType("live") }
                .getOrDefault(emptyList())
                .associateBy { it.contentId }

            val movies = watchlist
                .filter { it.contentType.equals("movie", ignoreCase = true) }
                .map { it.toMovie() }
            val shows = watchlist
                .filter {
                    it.contentType.equals("show", ignoreCase = true) ||
                        it.contentType.equals("series", ignoreCase = true)
                }
                .map { it.toShow() }
            val channels = favChannels.map { ch ->
                MyListChannel(
                    contentId = ch.contentId,
                    displayName = liveById[ch.contentId]?.displayName ?: ch.contentId,
                    logoUrl = ch.logoUrl.ifBlank { null },
                )
            }
            emit(MyListUiState(movies = movies, shows = shows, channels = channels))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyListUiState())
}

private fun WatchlistEntity.toMovie() = MyListMovie(
    contentId = contentId,
    title = title,
    year = null,
    posterUrl = artworkUrl.ifBlank { null },
)

private fun WatchlistEntity.toShow() = MyListShow(
    seriesTitle = title,
    year = null,
    posterUrl = artworkUrl.ifBlank { null },
)
