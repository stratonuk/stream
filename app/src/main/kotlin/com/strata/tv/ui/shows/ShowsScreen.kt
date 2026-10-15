package com.strata.tv.ui.shows

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
import com.strata.tv.data.db.SeriesListItem
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
 * Box Sets — full catalogue organised by provider category.
 */
@OptIn(
    ExperimentalTvMaterial3Api::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)
@Composable
fun ShowsScreen(
    modifier: Modifier = Modifier,
    onNavigate: AppNavState? = null,
    viewModel: ShowsViewModel = hiltViewModel(),
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
            ShowCategoryBrowser(
                title = CategoryRegion.displayName(state.selectedCategory!!),
                totalHint = state.categoryRails
                    .firstOrNull { it.category.groupTitle == state.selectedCategory }
                    ?.totalCount,
                shows = state.categoryItems,
                hasMore = state.categoryHasMore,
                onLoadMore = viewModel::loadMoreCategory,
                onBack = viewModel::closeCategory,
                itemContent = { show ->
                    ShowPosterWithMenu(
                        show = show,
                        watchlistIds = watchlistIds,
                        viewModel = viewModel,
                        onClick = { onNavigate?.openShowDetail(show.seriesTitle) },
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
                    ShowHero(
                        show = hero,
                        onClick = { onNavigate?.openShowDetail(hero.seriesTitle) },
                    )
                } else if (state.loading || state.categoryRails.isEmpty()) {
                    ShimmerHero()
                }

                Spacer(Modifier.height(8.dp))

                if (state.categoryRails.isNotEmpty()) {
                    val colors = listOf(
                        StrataColors.AccentSecondary,
                        StrataColors.AccentPrimary,
                        StrataColors.StatusLive,
                        StrataColors.AccentPrimaryBright,
                    )
                    state.categoryRails.forEachIndexed { index, rail ->
                        if (rail.shows.isEmpty() && state.loading) {
                            ShimmerRail()
                        } else if (rail.shows.isNotEmpty()) {
                            Rail(
                                title = "${rail.title}  ·  ${rail.totalCount}",
                                accentColor = colors[index % colors.size],
                                items = rail.shows +
                                    if (rail.hasMore) listOf(SEE_ALL_SHOW) else emptyList(),
                            ) { _, show ->
                                if (show.seriesTitle == SEE_ALL_SHOW.seriesTitle) {
                                    SeeAllShowCard(
                                        count = rail.totalCount - rail.shows.size,
                                        onClick = {
                                            viewModel.openCategory(rail.category.groupTitle)
                                        },
                                    )
                                } else {
                                    ShowPosterWithMenu(
                                        show = show,
                                        watchlistIds = watchlistIds,
                                        viewModel = viewModel,
                                        onClick = {
                                            onNavigate?.openShowDetail(show.seriesTitle)
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

private val SEE_ALL_SHOW = SeriesListItem(
    id = -1,
    seriesTitle = "__see_all__",
    posterUrl = "",
    backdropUrl = "",
    genre = "",
    language = "",
    totalSeasons = 0,
    totalEpisodes = 0,
    lastSeenTotalEpisodes = 0,
    hidden = false,
)

@Composable
private fun SeeAllShowCard(count: Int, onClick: () -> Unit) {
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
private fun ShowCategoryBrowser(
    title: String,
    totalHint: Int?,
    shows: List<SeriesListItem>,
    hasMore: Boolean,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
    itemContent: @Composable (SeriesListItem) -> Unit,
) {
    val rows = remember(shows) { shows.chunked(6) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "← Back",
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
            Column {
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
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { show -> itemContent(show) }
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
private fun ShowPosterWithMenu(
    show: SeriesListItem,
    watchlistIds: Set<String>,
    viewModel: ShowsViewModel,
    onClick: () -> Unit,
    onMenuShow: (List<ContextMenuAction>) -> Unit,
) {
    val inWatchlist = show.seriesTitle in watchlistIds
    Box(
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU
            ) {
                val actions = mutableListOf<ContextMenuAction>()
                if (inWatchlist) {
                    actions += ContextMenuAction("Remove from Watchlist") {
                        viewModel.removeFromWatchlist(show.seriesTitle)
                    }
                } else {
                    actions += ContextMenuAction("Add to Watchlist") {
                        viewModel.addToWatchlist(
                            seriesTitle = show.seriesTitle,
                            artworkUrl = show.posterUrl,
                        )
                    }
                }
                actions += ContextMenuAction("Hide this show") {
                    viewModel.hideSeries(show.seriesTitle)
                }
                show.genre
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .forEach { g ->
                        actions += ContextMenuAction("Ignore genre: $g") {
                            viewModel.ignoreGenre(g)
                        }
                    }
                if (show.language.isNotBlank()) {
                    val name = com.strata.tv.data.settings.AppSettings
                        .languageDisplayName(show.language)
                    actions += ContextMenuAction("Ignore language: $name") {
                        viewModel.ignoreLanguage(show.language)
                    }
                }
                onMenuShow(actions)
                true
            } else false
        },
    ) {
        PosterCard(
            title = show.seriesTitle,
            subtitle = if (show.totalSeasons > 0 || show.totalEpisodes > 0) {
                "${show.totalSeasons}S · ${show.totalEpisodes}E"
            } else null,
            posterUrl = show.posterUrl.takeIf { it.isNotBlank() },
            badge = if (show.hasNewEpisodes) "NEW" else null,
            onClick = onClick,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ShowHero(
    show: SeriesListItem,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .focusable()
            .clickable(onClick = onClick),
    ) {
        val backdropUrl = show.backdropUrl.takeIf { it.isNotBlank() }
            ?: show.posterUrl.takeIf { it.isNotBlank() }
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
                text = "BOX SETS",
                color = StrataColors.AccentSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = show.seriesTitle,
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 42.sp,
                letterSpacing = (-0.5).sp,
            )
            if (show.totalSeasons > 0 || show.genre.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (show.totalSeasons > 0) {
                        Text(
                            text = "${show.totalSeasons} Season${if (show.totalSeasons > 1) "s" else ""}",
                            color = StrataColors.TextSecondary,
                            fontSize = 14.sp,
                        )
                    }
                    if (show.totalSeasons > 0 && show.genre.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text("·", color = StrataColors.TextTertiary, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                    }
                    if (show.genre.isNotBlank()) {
                        Text(
                            text = show.genre.split(",", "|").take(3).joinToString(", ") { it.trim() },
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
