package com.strata.tv.ui.mylist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.strata.tv.ui.nav.AppNavState
import com.strata.tv.ui.nav.Destination
import com.strata.tv.ui.theme.StrataColors
import com.strata.tv.ui.widgets.PosterCard

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MyListScreen(
    onNavigate: AppNavState,
    viewModel: MyListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StrataColors.SurfaceVoid)
            .padding(start = 24.dp, top = 28.dp, end = 24.dp, bottom = 24.dp),
    ) {
        Text(
            text = "My List",
            color = StrataColors.TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Saved movies, series and channels",
            color = StrataColors.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 28.dp),
        )

        if (state.movies.isNotEmpty()) {
            Text(
                text = "Movies",
                color = StrataColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(end = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(bottom = 28.dp),
            ) {
                items(state.movies, key = { it.contentId }) { movie ->
                    PosterCard(
                        title = movie.title,
                        subtitle = movie.year,
                        posterUrl = movie.posterUrl,
                        onClick = { onNavigate.openMovieDetail(movie.contentId) },
                    )
                }
            }
        }

        if (state.shows.isNotEmpty()) {
            Text(
                text = "TV Series",
                color = StrataColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(end = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(bottom = 28.dp),
            ) {
                items(state.shows, key = { it.seriesTitle }) { show ->
                    PosterCard(
                        title = show.seriesTitle,
                        subtitle = show.year,
                        posterUrl = show.posterUrl,
                        onClick = { onNavigate.openShowDetail(show.seriesTitle) },
                    )
                }
            }
        }

        if (state.channels.isNotEmpty()) {
            Text(
                text = "Live TV",
                color = StrataColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(end = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.channels, key = { it.contentId }) { channel ->
                    PosterCard(
                        title = channel.displayName,
                        subtitle = "Live",
                        posterUrl = channel.logoUrl,
                        onClick = { onNavigate.navigate(Destination.Live) },
                    )
                }
            }
        }

        if (state.movies.isEmpty() && state.shows.isEmpty() && state.channels.isEmpty()) {
            Text(
                text = "Nothing saved yet. Add titles from Movies, TV Series or Live TV.",
                color = StrataColors.TextTertiary,
                fontSize = 15.sp,
            )
        }
    }
}
