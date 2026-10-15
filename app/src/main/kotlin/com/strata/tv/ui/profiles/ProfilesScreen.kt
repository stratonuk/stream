package com.strata.tv.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.strata.tv.ui.theme.StrataColors

/**
 * Local-only profiles. Active profile id is persisted in DataStore and
 * survives APK updates. Full per-profile library scoping can deepen later
 * without changing the stored id format.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ProfilesScreen(
    viewModel: ProfilesViewModel = hiltViewModel(),
) {
    val active by viewModel.activeProfileId.collectAsStateWithLifecycle()
    val profiles = listOf("profile_1" to "Profile 1", "profile_2" to "Profile 2")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StrataColors.SurfaceVoid)
            .padding(start = 24.dp, top = 28.dp, end = 24.dp),
    ) {
        Text(
            text = "Profiles",
            color = StrataColors.TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Local profiles on this device — no cloud account required",
            color = StrataColors.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 36.dp),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            profiles.forEach { (id, name) ->
                val selected = id == active
                Surface(
                    onClick = { viewModel.selectProfile(id) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = StrataColors.SurfaceRaised,
                        focusedContainerColor = StrataColors.SelectedNav,
                        contentColor = StrataColors.TextPrimary,
                        focusedContentColor = StrataColors.TextPrimary,
                    ),
                    modifier = Modifier.width(180.dp).height(200.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) StrataColors.TextPrimary
                                    else StrataColors.SurfaceOverlay,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = name.takeLast(1),
                                color = if (selected) StrataColors.SurfaceVoid else StrataColors.TextPrimary,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (selected) {
                            Text(
                                text = "Active",
                                color = StrataColors.TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
