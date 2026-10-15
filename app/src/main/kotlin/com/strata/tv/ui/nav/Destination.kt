package com.strata.tv.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Top-level destinations for the STREAM sidebar.
 * Order matches the approved design reference.
 */
enum class Destination(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Home("Home", Icons.Outlined.Home, Icons.Rounded.Home),
    Live("Live TV", Icons.Outlined.LiveTv, Icons.Rounded.LiveTv),
    Movies("Movies", Icons.Outlined.Movie, Icons.Rounded.Movie),
    Shows("TV Series", Icons.Outlined.VideoLibrary, Icons.Rounded.VideoLibrary),
    MyList("My List", Icons.Outlined.FavoriteBorder, Icons.Rounded.Favorite),
    Search("Search", Icons.Outlined.Search, Icons.Rounded.Search),
    Profiles("Profiles", Icons.Outlined.Person, Icons.Rounded.Person),
    Settings("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
}
