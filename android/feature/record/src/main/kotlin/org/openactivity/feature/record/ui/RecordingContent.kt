// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.map.compose.MapState
import org.openactivity.feature.record.R
import org.openactivity.feature.record.RecordUiState
import org.openactivity.feature.record.RecordingUi

/**
 * The recording screens (core-screens 3.4 to 3.6): a status bar, then the map page and the stats page side
 * by side (swipe, or the Map/Stats button), and the controls. Indoor sports only have the stats page.
 */
@Composable
internal fun RecordingContent(
    state: RecordUiState,
    recording: RecordingUi,
    actions: RecordActions,
    mapState: MapState,
    modifier: Modifier = Modifier,
    initialPage: Int = 0,
) {
    val hasMap = recording.sport.usesGps
    val pager = rememberPagerState(initialPage = if (hasMap) initialPage else 0) { if (hasMap) 2 else 1 }
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        StatusBar(recording, onMinimise = actions.onClose)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                if (hasMap && page == MAP_PAGE) {
                    MapPage(state, recording, mapState, onCyclePace = actions.onCyclePaceMode)
                } else {
                    StatsPage(recording, onCyclePace = actions.onCyclePaceMode)
                }
            }
            if (hasMap) PageDots(pager, Modifier.align(Alignment.TopCenter).padding(top = Spacing.s))
        }
        RecordingControls(
            recording = recording,
            actions = actions,
            showingStats = !hasMap || pager.currentPage == STATS_PAGE,
            onTogglePage = if (hasMap) {
                { scope.launch { pager.animateScrollToPage(if (pager.currentPage == MAP_PAGE) STATS_PAGE else MAP_PAGE) } }
            } else {
                null
            },
        )
    }
}

/** "● REC 12:47", "‖ PAUSED 12:47" or "‖ AUTO-PAUSED 12:47", with the minimise chevron. */
@Composable
private fun StatusBar(recording: RecordingUi, onMinimise: () -> Unit) {
    val colors = RidgelineTheme.colors
    val (label, container, content) = when {
        recording.isPaused -> Triple(stringResource(R.string.record_status_paused), colors.surfaceSunken, MaterialTheme.colorScheme.onSurface)
        recording.isAutoPaused -> Triple(stringResource(R.string.record_status_auto_paused), colors.warningContainer, colors.onWarningContainer)
        else -> Triple(stringResource(R.string.record_status_recording), MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.onSurface)
    }
    Surface(color = container, contentColor = content, shadowElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMinimise) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.record_minimise))
            }
            Row(
                Modifier.weight(1f).semantics(mergeDescendants = true) {
                    contentDescription = "$label, ${recording.metrics.time.value}"
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RecordingDot(active = !recording.isPaused && !recording.isAutoPaused)
                Spacer(Modifier.width(Spacing.s))
                Text(label.uppercase(), style = RidgelineTheme.text.overline)
                Spacer(Modifier.width(Spacing.s))
                Text(recording.metrics.time.value.orEmpty(), style = RidgelineTheme.text.statS)
            }
            Text(
                recording.sport.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (content == MaterialTheme.colorScheme.onSurface) colors.textSecondary else content,
                modifier = Modifier.padding(end = Spacing.m),
            )
        }
    }
}

/** Pulsing red dot while the timer runs, a still muted dot while paused. */
@Composable
private fun RecordingDot(active: Boolean) {
    val reduced = RidgelineTheme.motion.reduced
    val alpha = if (active && !reduced) {
        val transition = rememberInfiniteTransition(label = "rec")
        val a by transition.animateFloat(
            initialValue = 1f,
            targetValue = PULSE_MIN_ALPHA,
            animationSpec = infiniteRepeatable(tween(PULSE_MS), RepeatMode.Reverse),
            label = "recAlpha",
        )
        a
    } else {
        1f
    }
    Box(
        Modifier
            .size(10.dp)
            .alpha(alpha)
            .background(if (active) MaterialTheme.colorScheme.error else RidgelineTheme.colors.textMuted, CircleShape),
    )
}

@Composable
private fun PageDots(pager: PagerState, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
            .padding(horizontal = Spacing.s, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(pager.pageCount) { index ->
            val selected = pager.currentPage == index
            Box(
                Modifier
                    .size(6.dp)
                    .background(if (selected) MaterialTheme.colorScheme.primary else RidgelineTheme.colors.borderStrong, CircleShape),
            )
        }
    }
}

internal const val MAP_PAGE = 0
internal const val STATS_PAGE = 1
private const val PULSE_MS = 700
private const val PULSE_MIN_ALPHA = 0.25f
