package com.strata.tv.ui.nav

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LiveTv
import com.strata.tv.ui.theme.StrataColors

/**
 * Compact icon rail that expands to show labels when focused.
 * Selected / focused items use charcoal background with explicit white text
 * so labels stay readable and vertically centred.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun Sidebar(
    selected: Destination,
    onSelected: (Destination) -> Unit,
    sidebarFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    enrichmentProgress: Float = 0f,
    enrichmentRunning: Boolean = false,
) {
    val itemRequesters = remember {
        Destination.entries.associateWith { FocusRequester() }
    }
    var railFocused by remember { mutableStateOf(false) }
    val railWidth by animateDpAsState(
        targetValue = if (railFocused) 176.dp else 64.dp,
        animationSpec = tween(durationMillis = 180),
        label = "sidebarWidth",
    )

    LaunchedEffect(selected) {
        runCatching { itemRequesters.getValue(selected).requestFocus() }
    }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
        modifier = modifier
            .fillMaxHeight()
            .width(railWidth)
            .background(StrataColors.SurfaceVoid)
            .onFocusChanged { railFocused = it.hasFocus }
            .padding(vertical = 16.dp, horizontal = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 6.dp, bottom = 20.dp)
                .height(24.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.LiveTv,
                contentDescription = "STREAM",
                tint = StrataColors.TextPrimary,
                modifier = Modifier.size(18.dp),
            )
            if (railFocused) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "STREAM",
                    color = StrataColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                )
            }
        }

        for (destination in Destination.entries) {
            SidebarItem(
                destination = destination,
                isSelected = destination == selected,
                expanded = railFocused,
                focusRequester = itemRequesters.getValue(destination),
                sidebarFocusRequester = sidebarFocusRequester.takeIf { destination == selected },
                onClick = { onSelected(destination) },
            )
            Spacer(Modifier.height(4.dp))
        }

        if (enrichmentRunning) {
            Spacer(Modifier.height(12.dp))
            EnrichmentRing(progress = enrichmentProgress)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SidebarItem(
    destination: Destination,
    isSelected: Boolean,
    expanded: Boolean,
    focusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val highlighted = isSelected || isFocused
    val shape = RoundedCornerShape(10.dp)
    // Explicit colours — TV Material contentColor is unreliable for labels
    // sitting on charcoal selected backgrounds.
    val labelColor = when {
        highlighted -> StrataColors.TextPrimary
        else -> StrataColors.TextSecondary
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .focusRequester(focusRequester)
            .then(
                if (sidebarFocusRequester != null) {
                    Modifier.focusRequester(sidebarFocusRequester)
                } else {
                    Modifier
                },
            )
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) StrataColors.SelectedNav else Color.Transparent,
            focusedContainerColor = StrataColors.SelectedNav,
            pressedContainerColor = StrataColors.SurfaceOverlay,
            contentColor = StrataColors.TextPrimary,
            focusedContentColor = StrataColors.TextPrimary,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            Icon(
                imageVector = if (highlighted) destination.selectedIcon else destination.icon,
                contentDescription = destination.label,
                tint = labelColor,
                modifier = Modifier.size(20.dp),
            )
            if (expanded) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = destination.label,
                    color = labelColor,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun EnrichmentRing(progress: Float) {
    val percent = (progress * 100).toInt().coerceIn(0, 100)
    val sweepAngle = progress * 360f
    val trackColor = StrataColors.SurfaceRaised
    val ringColor = StrataColors.Silver

    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .size(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(36.dp)) {
            val strokeWidth = 3.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
        Text(
            text = "$percent%",
            color = StrataColors.TextSecondary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
