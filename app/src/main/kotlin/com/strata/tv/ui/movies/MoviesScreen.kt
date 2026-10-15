package com.strata.tv.ui.movies

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.strata.tv.data.db.MovieListItem
import com.strata.tv.domain.CategoryRegion
import com.strata.tv.ui.nav.AppNavState
import com.strata.tv.ui.theme.StrataColors
import com.strata.tv.ui.widgets.CardContextMenu
import com.strata.tv.ui.widgets.ContextMenuAction
import com.strata.tv.ui.widgets.PosterCard
import com.strata.tv.ui.widgets.Rail
import com.strata.tv.ui.widgets.ShimmerHero
import com.strata.tv.ui.widgets.ShimmerRail

/**
 * Movies screen — full catalogue organised by provider category.
 * Rails show a preview; "See all" pages the rest of that category.
 */
@OptIn(
    ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)
@Composable
fun MoviesScreen(
    modifier: Modifier = Modifier,
    onNavigate: AppNavState? = null,
    viewModel: MoviesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val watchlistIds by viewModel.watchlistIds.collectAsState()
    var contextMenuVisible by remember { mutableStateOf(false) }
    var contextMenuActions by remember { mutableStateOf<List<ContextMenuAction>>(emptyList()) }

    val sidebarRequester = onNavigate?.sidebarRequester
    val inCategory = state.selectedCategory != null

    BackHandler(enabled = inCategory) { viewModel.closeCategory() }

    Box(modifier = modifier.fillMaxSize()) {
        if (inCategory) {
            CategoryBrowser(
                title = CategoryRegion.displayName(state.selectedCategory!!),
                totalHint = state.categoryRails
                    .firstOrNull { it.category.groupTitle == state.selectedCategory }
                    ?.totalCount,
                movies = state.categoryItems,
                hasMore = state.categoryHasMore,
                onLoadMore = viewModel::loadMoreCategory,
                onBack = viewModel::closeCategory,
                renderItem = { movie ->
                    MoviePosterWithMenu(
                        movie = movie,
                        watchlistIds = watchlistIds,
                        viewModel = viewModel,
                        onClick = { onNavigate?.openMovieDetail(movie.contentId) },
                        onMenuShow = { actions ->
                            contextMenuActions = actions
                            contextMenuVisible = true
                        },
                    )
                },
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .focusGroup()
                    .focusProperties {
                        exit = { dir ->
                            if (dir == FocusDirection.Up && sidebarRequester != null) {
                                sidebarRequester
                            } else {
                                FocusRequester.Default
                            }
                        }
                    }
                    .verticalScroll(rememberScrollState()),
            ) {
                val hero = state.hero
                if (hero != null) {
                    MovieHero(
                        movie = hero,
                        onClick = { onNavigate?.openMovieDetail(hero.contentId) },
                    )
                } else if (state.loading || state.categoryRails.isEmpty()) {
                    ShimmerHero()
                }

                Spacer(Modifier.height(8.dp))

                if (state.categoryRails.isNotEmpty()) {
                    val colors = listOf(
                        StrataColors.AccentPrimary,
                        StrataColors.AccentSecondary,
                        StrataColors.StatusLive,
                        StrataColors.AccentPrimaryBright,
                    )
                    state.categoryRails.forEachIndexed { index, rail ->
                        if (rail.movies.isEmpty() && state.loading) {
                            ShimmerRail()
                        } else if (rail.movies.isNotEmpty()) {
                            Rail(
                                title = "${rail.title}  ·  ${rail.totalCount}",
                                accentColor = colors[index % colors.size],
                                items = rail.movies +
                                    if (rail.hasMore) listOf(SEE_ALL_SENTINEL) else emptyList(),
                            ) { _, movie ->
                                if (movie.contentId == SEE_ALL_SENTINEL.contentId) {
                                    SeeAllCard(
                                        count = rail.totalCount - rail.movies.size,
                                        onClick = {
                                            viewModel.openCategory(rail.category.groupTitle)
                                        },
                                    )
                                } else {
                                    MoviePosterWithMenu(
                                        movie = movie,
                                        watchlistIds = watchlistIds,
                                        viewModel = viewModel,
                                        onClick = {
                                            onNavigate?.openMovieDetail(movie.contentId)
                                        },
                                        onMenuShow = { actions ->
                                            contextMenuActions = actions
                                            contextMenuVisible = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else if (state.loading) {
                    ShimmerRail()
                    ShimmerRail()
                }

                Spacer(Modifier.height(48.dp))
            }
        }

        CardContextMenu(
            visible = contextMenuVisible,
            actions = contextMenuActions,
            onDismiss = { contextMenuVisible = false },
        )
    }
}

/** Sentinel movie used as the trailing "See all" tile in a rail. */
private val SEE_ALL_SENTINEL = MovieListItem(
    id = -1,
    contentId = "__see_all__",
    movieTitle = "See all",
    year = null,
    runtime = null,
    genre = "",
    posterUrl = "",
    resumePositionMs = 0,
    watched = false,
    isFavourite = false,
    language = "",
    rating = 0.0,
    provider = "",
    tmdbId = 0,
    hidden = false,
)

@Composable
private fun SeeAllCard(count: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(120.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(StrataColors.SurfaceFloat)
            .clickable(onClick = onClick)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "See all",
                color = StrataColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            if (count > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "+$count more",
                    color = StrataColors.TextSecondary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CategoryBrowser(
    renderItem: @Composable (MovieListItem) -> Unit,
    title: String,
    totalHint: Int?,
    movies: List<MovieListItem>,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
) {
    val rows = remember(movies) { movies.chunked(6) }
    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Back",
                color = StrataColors.AccentPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onBack)
                    .focusable()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Spacer(Modifier.width(20.dp))
            androidx.compose.foundation.layout.Column {
                Text(
                    text = title,
                    color = StrataColors.TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (totalHint != null) {
                    Text(
                        text = "$totalHint titles",
                        color = StrataColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        TvLazyColumn(
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(rows) { row ->
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    row.forEach { movie -> renderItem(movie) }
                }
            }
            if (hasMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(StrataColors.SurfaceFloat)
                            .clickable(onClick = onLoadMore)
                            .focusable()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Load more",
                            color = StrataColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoviePosterWithMenu(
    movie: MovieListItem,
    watchlistIds: Set<String>,
    viewModel: MoviesViewModel,
    onClick: () -> Unit,
    onMenuShow: (List<ContextMenuAction>) -> Unit,
) {
    val inWatchlist = movie.contentId in watchlistIds
    Box(
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU
            ) {
                val actions = mutableListOf<ContextMenuAction>()
                if (inWatchlist) {
                    actions += ContextMenuAction("Remove from Watchlist") {
                        viewModel.removeFromWatchlist(movie.contentId)
                    }
                } else {
                    actions += ContextMenuAction("Add to Watchlist") {
                        viewModel.addToWatchlist(
                            contentId = movie.contentId,
                            title = movie.movieTitle,
                            artworkUrl = movie.posterUrl,
                        )
                    }
                }
                actions += ContextMenuAction("Hide this film") {
                    viewModel.hideMovie(movie.contentId)
                }
                movie.genre
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .forEach { g ->
                        actions += ContextMenuAction("Ignore genre: $g") {
                            viewModel.ignoreGenre(g)
                        }
                    }
                if (movie.language.isNotBlank()) {
                    val name = com.strata.tv.data.settings.AppSettings
                        .languageDisplayName(movie.language)
                    actions += ContextMenuAction("Ignore language: $name") {
                        viewModel.ignoreLanguage(movie.language)
                    }
                }
                onMenuShow(actions)
                true
            } else false
        },
    ) {
        PosterCard(
            title = movie.movieTitle,
            subtitle = movie.year?.toString(),
            posterUrl = movie.posterUrl.takeIf { it.isNotBlank() },
            onClick = onClick,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MovieHero(
    movie: MovieListItem,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .focusable()
            .clickable(onClick = onClick),
    ) {
        val backdropUrl = movie.posterUrl.takeIf { it.isNotBlank() }
        if (backdropUrl != null) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF191B1F), StrataColors.SurfaceVoid),
                        ),
                    ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.15f),
                            0.5f to Color.Black.copy(alpha = 0.5f),
                            1.0f to StrataColors.SurfaceVoid,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.6f),
                            0.5f to Color.Black.copy(alpha = 0.15f),
                            1.0f to Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 32.dp, bottom = 24.dp, end = 200.dp),
        ) {
            Text(
                text = "MOVIES",
                color = StrataColors.AccentPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = movie.movieTitle,
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 42.sp,
                letterSpacing = (-0.5).sp,
            )
            if (movie.year != null || movie.genre.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (movie.year != null) {
                        Text(
                            text = movie.year.toString(),
                            color = StrataColors.TextSecondary,
                            fontSize = 14.sp,
                        )
                    }
                    if (movie.year != null && movie.genre.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text("·", color = StrataColors.TextTertiary, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                    }
                    if (movie.genre.isNotBlank()) {
                        Text(
                            text = movie.genre.split(",", "|").take(3).joinToString(", ") { it.trim() },
                            color = StrataColors.TextSecondary,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
