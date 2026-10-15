package com.strata.tv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.strata.tv.ui.theme.StrataColors

/**
 * Bottom sheet for picking audio + subtitle tracks during playback.
 * Open with Menu (VOD) or the CC control; Back dismisses.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TrackSelectionOverlay(
    visible: Boolean,
    audioTracks: List<AudioTrack>,
    subtitleTracks: List<SubtitleTrack>,
    subtitlesEnabled: Boolean,
    onSelectAudio: (AudioTrack) -> Unit,
    onSelectSubtitle: (SubtitleTrack) -> Unit,
    onDisableSubtitles: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }

    AnimatedVisibility(
        visible = visible && (audioTracks.isNotEmpty() || subtitleTracks.isNotEmpty()),
        enter = fadeIn(tween(220)) + slideInVertically(
            animationSpec = tween(260),
            initialOffsetY = { it },
        ),
        exit = fadeOut(tween(180)) + slideOutVertically(
            animationSpec = tween(220),
            targetOffsetY = { it },
        ),
        modifier = modifier,
    ) {
        LaunchedEffect(visible) {
            if (visible) runCatching { firstFocus.requestFocus() }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xEE08090B)),
                    ),
                )
                .padding(horizontal = 32.dp, vertical = 28.dp),
        ) {
            Text(
                text = "Audio & subtitles",
                color = StrataColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))

            if (audioTracks.isNotEmpty()) {
                Text(
                    text = "AUDIO",
                    color = StrataColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(end = 24.dp),
                ) {
                    items(audioTracks, key = { "${it.groupIndex}-${it.trackIndex}" }) { track ->
                        TrackChip(
                            label = track.label,
                            selected = track.isSelected,
                            onClick = { onSelectAudio(track) },
                            modifier = if (track == audioTracks.first()) {
                                Modifier.focusRequester(firstFocus)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            if (subtitleTracks.isNotEmpty()) {
                Text(
                    text = "SUBTITLES",
                    color = StrataColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(end = 24.dp),
                ) {
                    item {
                        TrackChip(
                            label = "Off",
                            selected = !subtitlesEnabled,
                            onClick = onDisableSubtitles,
                            modifier = if (audioTracks.isEmpty()) {
                                Modifier.focusRequester(firstFocus)
                            } else {
                                Modifier
                            },
                        )
                    }
                    items(subtitleTracks, key = { "${it.groupIndex}-${it.trackIndex}" }) { track ->
                        TrackChip(
                            label = track.label,
                            selected = subtitlesEnabled && track.isSelected,
                            onClick = { onSelectSubtitle(track) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Menu toggles this panel · Back to close",
                color = StrataColors.TextTertiary,
                fontSize = 12.sp,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TrackChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(160.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) StrataColors.SelectedNav else StrataColors.SurfaceFloat,
            focusedContainerColor = StrataColors.SurfaceOverlay,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (selected) "●  " else "○  ",
                color = if (selected) StrataColors.TextPrimary else StrataColors.TextTertiary,
                fontSize = 12.sp,
            )
            Text(
                text = label,
                color = StrataColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}
