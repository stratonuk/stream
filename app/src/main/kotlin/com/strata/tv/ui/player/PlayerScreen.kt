package com.strata.tv.ui.player

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.List
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.strata.tv.ui.nav.ChannelPlayInfo
import com.strata.tv.ui.theme.StrataColors

/**
 * Full-screen ExoPlayer video surface backed by Media3.
 *
 * Phase 9: polished controls overlay with VOD progress bar,
 * elapsed/remaining time labels, smooth bottom gradient,
 * and fade animation for the controls.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    streamUrl: String,
    title: String,
    isLive: Boolean,
    resumePositionMs: Long,
    contentType: String,
    artworkUrl: String,
    contentId: String = "",
    channelList: List<ChannelPlayInfo> = emptyList(),
    currentChannelIndex: Int = 0,
    seriesTitle: String? = null,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null,
    onExit: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val focusRequester = remember { FocusRequester() }
    // Dedicated requester for the error overlay's Retry button so that,
    // when a stream fails, D-pad focus lands on Retry/Go-Back instead of
    // being swallowed by the player transport controls underneath.
    val retryFocusRequester = remember { FocusRequester() }

    // Track the current display title for live channel switching.
    var displayTitle by remember { mutableStateOf(title) }

    LaunchedEffect(Unit) {
        viewModel.initialize(
            streamUrl = streamUrl,
            title = title,
            isLive = isLive,
            resumePositionMs = resumePositionMs,
            contentType = contentType,
            artworkUrl = artworkUrl,
            contentId = contentId,
            seriesTitle = seriesTitle,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
        )
        if (channelList.isNotEmpty()) {
            viewModel.setChannelList(channelList, currentChannelIndex)
        }
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val exitHandler = remember(onExit) {
        {
            viewModel.saveOnExit()
            onExit()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                    return@onPreviewKeyEvent false
                }
                // While the error overlay is up, DON'T intercept D-pad /
                // center as seek / play-pause — let them reach the
                // Retry / Go Back buttons so the user can move between
                // and activate them.  Only Back exits the player.
                if (state.errorMessage != null) {
                    return@onPreviewKeyEvent when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                            exitHandler()
                            true
                        }
                        else -> false
                    }
                }
                // Track / episode overlays: Back dismisses; other keys
                // reach the overlay's focusable chips/cards.
                if (state.trackPanelVisible) {
                    return@onPreviewKeyEvent when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                            viewModel.closeTrackPanel()
                            true
                        }
                        else -> false
                    }
                }
                if (state.episodeOverlayVisible) {
                    return@onPreviewKeyEvent when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                            viewModel.hideEpisodeOverlay()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            viewModel.hideEpisodeOverlay()
                            true
                        }
                        else -> false
                    }
                }
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                        when {
                            state.nextEpisode != null -> viewModel.cancelAutoplay()
                            else -> exitHandler()
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        viewModel.togglePlayPause()
                        viewModel.showControls()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_DPAD_LEFT -> {
                        viewModel.seekRelative(-10_000)
                        viewModel.showControls()
                        true
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        viewModel.seekRelative(10_000)
                        viewModel.showControls()
                        true
                    }
                    // Live: Menu toggles fav zapping. VOD: audio/subs panel.
                    KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_BOOKMARK -> {
                        when {
                            isLive -> {
                                viewModel.toggleFavMode()
                                true
                            }
                            else -> {
                                viewModel.toggleTrackPanel()
                                true
                            }
                        }
                    }
                    // D-pad Up: zap favourites if fav mode on, else normal channel switch.
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (state.favModeEnabled) {
                            viewModel.zapFavourite(-1)
                            true
                        } else if (isLive && channelList.isNotEmpty()) {
                            val ch = viewModel.switchChannel(-1)
                            if (ch != null) displayTitle = ch.displayName
                            true
                        } else {
                            viewModel.showControls()
                            false
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        when {
                            state.favModeEnabled -> {
                                viewModel.zapFavourite(+1)
                                true
                            }
                            isLive && channelList.isNotEmpty() -> {
                                val ch = viewModel.switchChannel(+1)
                                if (ch != null) displayTitle = ch.displayName
                                true
                            }
                            // Show episode list overlay when watching a series
                            // and the player controls are visible.  Letting
                            // the player swallow D-pad Down here means we don't
                            // double-trigger focus traversal into the surface.
                            !isLive && contentType == "show" && seriesTitle != null
                                && state.controlsVisible && !state.episodeOverlayVisible -> {
                                viewModel.toggleEpisodeOverlay()
                                true
                            }
                            else -> {
                                viewModel.showControls()
                                false
                            }
                        }
                    }
                    else -> {
                        viewModel.showControls()
                        false
                    }
                }
            },
    ) {
        // -- Video surface -----------------------------------------------
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.player
                    useController = false
                    // Keep Media3's built-in cue renderer so selected
                    // subtitle tracks actually paint on screen.
                    subtitleView?.apply {
                        setUserDefaultStyle()
                        setUserDefaultTextSize()
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // -- Buffering spinner -------------------------------------------
        if (state.isBuffering) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                SpinningArc(
                    color = StrataColors.AccentPrimary,
                    modifier = Modifier.size(52.dp),
                )
            }
        }

        // -- Error overlay -----------------------------------------------
        state.errorMessage?.let { errorMsg ->
            // Pull D-pad focus onto the Retry button the moment an error
            // is shown — otherwise the player controls underneath keep
            // focus and the user can't reach Retry / Go Back.
            LaunchedEffect(errorMsg) {
                runCatching { retryFocusRequester.requestFocus() }
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .padding(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(StrataColors.SurfaceRaised.copy(alpha = 0.92f))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = "Error",
                        tint = StrataColors.StatusError,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Stream unavailable",
                        color = StrataColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMsg,
                        color = StrataColors.TextSecondary,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // Retry button
                        androidx.tv.material3.Surface(
                            onClick = { viewModel.retryNow() },
                            modifier = Modifier.focusRequester(retryFocusRequester),
                            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(
                                shape = RoundedCornerShape(8.dp),
                            ),
                            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                                containerColor = StrataColors.AccentPrimary,
                                focusedContainerColor = StrataColors.AccentPrimaryBright,
                            ),
                        ) {
                            Text(
                                text = "Retry Now",
                                color = StrataColors.SurfaceVoid,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            )
                        }
                        // Back button
                        androidx.tv.material3.Surface(
                            onClick = { exitHandler() },
                            shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(
                                shape = RoundedCornerShape(8.dp),
                            ),
                            colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
                                containerColor = StrataColors.SurfaceFloat,
                                focusedContainerColor = StrataColors.SurfaceOverlay,
                            ),
                        ) {
                            Text(
                                text = "Go Back",
                                color = StrataColors.TextPrimary,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }

        // -- Netflix-style controls overlay ------------------------------
        val subs by viewModel.subtitleTracks.collectAsState()
        val audios by viewModel.audioTracks.collectAsState()
        AnimatedVisibility(
            visible = state.controlsVisible && !state.trackPanelVisible,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(320)),
        ) {
            NetflixPlayerChrome(
                title = state.channelDisplayName ?: displayTitle,
                subtitle = buildPlayerSubtitle(
                    isLive = isLive,
                    contentType = contentType,
                    seriesTitle = seriesTitle,
                    seasonNumber = state.currentSeasonNumber ?: seasonNumber,
                    episodeNumber = state.currentEpisodeNumber ?: episodeNumber,
                ),
                isLive = isLive,
                isPlaying = state.isPlaying,
                favMode = state.favModeEnabled,
                hasSubtitles = subs.isNotEmpty(),
                hasMultiAudio = audios.size > 1,
                subtitlesOn = state.subtitlesEnabled,
                showEpisodes = !isLive && contentType == "show" && seriesTitle != null,
                player = viewModel.player,
                controlsVisible = state.controlsVisible,
                onOpenTracks = { viewModel.openTrackPanel() },
                onOpenEpisodes = { viewModel.toggleEpisodeOverlay() },
            )
        }

        // -- Channel switch overlay (top banner) -------------------------
        if (isLive && channelList.isNotEmpty()) {
            ChannelOverlay(
                visible = state.channelOverlayVisible,
                channel = viewModel.currentChannel(),
                // Reactive index from UI state so the "Channel X of Y"
                // counter updates after D-pad Up/Down switches channels
                // (the composable `currentChannelIndex` param is fixed
                // to the initial navigation value).
                channelIndex = state.currentChannelIndex,
                channelCount = channelList.size,
            )
        }

        // -- Next episode autoplay overlay (bottom-right) ---------------
        state.nextEpisode?.let { next ->
            NextEpisodeOverlay(next = next)
        }

        // -- Episode list overlay (bottom rail, for shows) --------------
        if (contentType == "show" && seriesTitle != null) {
            val episodes by viewModel.episodes.collectAsState()
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                EpisodeListOverlay(
                    visible = state.episodeOverlayVisible,
                    episodes = episodes,
                    // Read current S/E from UI state so the "NOW PLAYING"
                    // badge moves when the user jumps to a new episode
                    // (the composable params are fixed to the initial
                    // nav args).
                    currentSeason = state.currentSeasonNumber ?: seasonNumber,
                    currentEpisode = state.currentEpisodeNumber ?: episodeNumber,
                    onEpisodeSelected = { episode ->
                        // Update local title so the top bar reflects the
                        // new episode immediately; ViewModel.playEpisode
                        // will swap the stream and re-arm continue-watching.
                        displayTitle = buildString {
                            append(seriesTitle)
                            append(" S${episode.seasonNumber}E${episode.episodeNumber}")
                        }
                        viewModel.playEpisode(episode)
                    },
                )
            }
        }

        // -- Audio / subtitle track picker ------------------------------
        val subtitleTracks by viewModel.subtitleTracks.collectAsState()
        val audioTracks by viewModel.audioTracks.collectAsState()
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            TrackSelectionOverlay(
                visible = state.trackPanelVisible,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                subtitlesEnabled = state.subtitlesEnabled,
                onSelectAudio = { viewModel.selectAudioTrack(it.groupIndex, it.trackIndex) },
                onSelectSubtitle = { viewModel.selectSubtitleTrack(it.groupIndex, it.trackIndex) },
                onDisableSubtitles = { viewModel.disableSubtitles() },
            )
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

// -- Netflix-style chrome ------------------------------------------------

private fun buildPlayerSubtitle(
    isLive: Boolean,
    contentType: String,
    seriesTitle: String?,
    seasonNumber: Int?,
    episodeNumber: Int?,
): String? {
    if (isLive) return "Live TV"
    if (contentType == "show" && seasonNumber != null && episodeNumber != null) {
        return "S${seasonNumber.toString().padStart(2, '0')}  E${episodeNumber.toString().padStart(2, '0')}"
    }
    return null
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NetflixPlayerChrome(
    title: String,
    subtitle: String?,
    isLive: Boolean,
    isPlaying: Boolean,
    favMode: Boolean,
    hasSubtitles: Boolean,
    hasMultiAudio: Boolean,
    subtitlesOn: Boolean,
    showEpisodes: Boolean,
    player: Player,
    controlsVisible: Boolean,
    onOpenTracks: () -> Unit,
    onOpenEpisodes: () -> Unit,
) {
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }
    LaunchedEffect(isPlaying, controlsVisible) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.coerceAtLeast(1)
            kotlinx.coroutines.delay(400)
        }
    }
    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else 0f
    val remaining = (durationMs - positionMs).coerceAtLeast(0)

    Box(modifier = Modifier.fillMaxSize()) {
        // Tall Netflix-style edge fades
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xE6000000), Color.Transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xF2000000)),
                    ),
                ),
        )

        // Top: back only + status chips (title lives in the bottom stack)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, top = 28.dp, end = 28.dp)
                .align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.weight(1f))
            if (favMode) {
                StatusChip(text = "FAV")
                Spacer(Modifier.width(8.dp))
            }
            if (isLive) {
                StatusChip(text = "LIVE")
                Spacer(Modifier.width(8.dp))
            }
            ClockDisplay()
        }

        // Center transport — large when paused (Netflix pause state)
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (!isLive) {
                Icon(
                    imageVector = Icons.Filled.Replay10,
                    contentDescription = "Rewind 10 seconds",
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(if (isPlaying) 44.dp else 52.dp),
                )
                Spacer(Modifier.width(36.dp))
            }
            Box(
                modifier = Modifier
                    .size(if (isPlaying) 72.dp else 88.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.White.copy(alpha = if (isPlaying) 0.12f else 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(if (isPlaying) 40.dp else 48.dp),
                )
            }
            if (!isLive) {
                Spacer(Modifier.width(36.dp))
                Icon(
                    imageVector = Icons.Filled.Forward10,
                    contentDescription = "Forward 10 seconds",
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(if (isPlaying) 44.dp else 52.dp),
                )
            }
        }

        // Bottom stack: title → scrubber → secondary actions
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 36.dp, vertical = 28.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                letterSpacing = (-0.3).sp,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            if (!isLive) {
                Spacer(Modifier.height(18.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Scrubber with playhead knob
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.28f)),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(20.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(Color.White),
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = formatTime(remaining),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (hasSubtitles || hasMultiAudio) {
                    ActionPill(
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.ClosedCaption,
                                contentDescription = null,
                                tint = if (subtitlesOn) StrataColors.SurfaceVoid else Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        label = "Audio & Subtitles",
                        selected = subtitlesOn,
                        onClick = onOpenTracks,
                    )
                }
                if (showEpisodes) {
                    ActionPill(
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.List,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        label = "Episodes",
                        selected = false,
                        onClick = onOpenEpisodes,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ActionPill(
    icon: @Composable () -> Unit,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = if (selected) StrataColors.SurfaceVoid else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// -- Helpers -------------------------------------------------------------

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

// -- Clock display -------------------------------------------------------

/**
 * Small day-of-week + date + time pill rendered in the top-right
 * of the player controls overlay.  Refreshes once per minute via
 * a LaunchedEffect — no need for a per-second tick at this granularity.
 *
 * Format e.g. "Mon 5 May  21:34".
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ClockDisplay() {
    val formatter = remember {
        DateTimeFormatter.ofPattern("EEE d MMM  HH:mm", Locale.getDefault())
    }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        // Sleep until the next minute boundary so the displayed minute
        // never lags behind the wall clock by an arbitrary amount.
        while (true) {
            now = LocalDateTime.now()
            val msUntilNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L)
            kotlinx.coroutines.delay(msUntilNextMinute)
        }
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x66000000))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text = now.format(formatter),
            color = StrataColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// -- Next episode autoplay overlay ----------------------------------------

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NextEpisodeOverlay(next: NextEpisodeInfo) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(StrataColors.SurfaceRaised.copy(alpha = 0.92f))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Next Episode",
                color = StrataColors.TextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = next.seriesTitle,
                color = StrataColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append("S${next.seasonNumber}E${next.episodeNumber}")
                    if (next.episodeTitle.isNotBlank()) {
                        append(" \u2013 ${next.episodeTitle}")
                    }
                },
                color = StrataColors.TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(12.dp))
            CountdownRing(
                seconds = next.countdown,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Press BACK to cancel",
                color = StrataColors.TextTertiary,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun CountdownRing(
    seconds: Int,
    modifier: Modifier = Modifier,
) {
    val sweepAngle = (seconds / 10f) * 360f
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            // Background ring
            drawArc(
                color = Color.White.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
            // Countdown arc
            drawArc(
                color = StrataColors.AccentPrimary,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Text(
            text = "$seconds",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// -- Buffering spinner ---------------------------------------------------

@Composable
private fun SpinningArc(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    Canvas(modifier = modifier) {
        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
