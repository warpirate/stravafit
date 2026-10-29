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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.Elevation
import org.openactivity.core.designsystem.theme.PillShape
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.map.compose.MapState
import org.openactivity.feature.record.R
import org.openactivity.feature.record.RecordSheet
import org.openactivity.feature.record.RecordUiState
import org.openactivity.feature.record.RecordingUi

/**
 * The recording screen (strava direction, `record-live` and `record-paused`): the map on top with the
 * "Recording · Run" pill, then a panel with the huge Time, Split Avg | Distance, Avg Pace · Elev Gain ·
 * Heart Rate, the split progress bar and Lap · Stop · Lock (Resume · Finish while paused). The top area
 * swipes between the map and the splits list. In landscape the panel sits beside the map, so the map is
 * never squeezed into a strip. Indoor sports have no map: the panel fills the screen.
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
    var locked by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val landscape = maxWidth > maxHeight
        val panel: @Composable (Modifier) -> Unit = { m ->
            RecordingPanel(
                recording = recording,
                actions = actions,
                locked = locked,
                onLockChange = { locked = it },
                modifier = m,
                scrollable = landscape || !hasMap,
            )
        }
        when {
            !hasMap -> Column(Modifier.fillMaxSize()) {
                TopRow(recording, actions, Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(Spacing.gutter))
                RecordingNotices(recording, Modifier.padding(horizontal = Spacing.gutter))
                panel(Modifier.weight(1f).fillMaxWidth())
            }
            landscape -> Row(Modifier.fillMaxSize()) {
                TopArea(state, recording, actions, mapState, initialPage, Modifier.weight(1f).fillMaxHeight())
                panel(Modifier.width(LANDSCAPE_PANEL_WIDTH).fillMaxHeight())
            }
            else -> Column(Modifier.fillMaxSize()) {
                TopArea(state, recording, actions, mapState, initialPage, Modifier.weight(1f).fillMaxWidth())
                panel(Modifier.fillMaxWidth())
            }
        }
    }
}

/** Map and splits pages with the floating top row, notices and page dots. */
@Composable
private fun TopArea(
    state: RecordUiState,
    recording: RecordingUi,
    actions: RecordActions,
    mapState: MapState,
    initialPage: Int,
    modifier: Modifier,
) {
    val pager = rememberPagerState(initialPage = initialPage.coerceIn(0, 1)) { 2 }
    Box(modifier) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            if (page == MAP_PAGE) {
                MapPage(state, recording, mapState)
            } else {
                SplitsPage(recording)
            }
        }
        Column(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            TopRow(recording, actions)
            RecordingNotices(recording)
        }
        PageDots(pager, Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.s))
    }
}

/** Minimise chevron, "Recording · Run" pill, settings gear. */
@Composable
private fun TopRow(recording: RecordingUi, actions: RecordActions, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        MapIconButton(
            Icons.Rounded.KeyboardArrowDown,
            stringResource(R.string.record_minimise),
            actions.onClose,
            Modifier.align(Alignment.CenterStart),
        )
        StatusPillLive(recording, Modifier.align(Alignment.Center))
        MapIconButton(
            Icons.Rounded.Settings,
            stringResource(R.string.record_settings),
            { actions.onShowSheet(RecordSheet.Settings) },
            Modifier.align(Alignment.CenterEnd),
        )
    }
}

/** "● Recording · Run", "● Paused · Run" or "● Auto-paused · Run". */
@Composable
private fun StatusPillLive(recording: RecordingUi, modifier: Modifier = Modifier) {
    val label = stringResource(
        when {
            recording.isPaused -> R.string.record_status_paused
            recording.isAutoPaused -> R.string.record_status_auto_paused
            else -> R.string.record_status_recording
        },
    )
    val text = "$label · ${recording.sport.label}"
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$text, ${recording.metrics.time.value}"
        },
        shape = PillShape,
        color = RidgelineTheme.colors.float,
        contentColor = RidgelineTheme.colors.onFloat,
        shadowElevation = Elevation.float,
    ) {
        Row(Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
            RecordingDot(active = !recording.isPaused && !recording.isAutoPaused)
            Spacer(Modifier.width(Spacing.s))
            Text(text, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

/** Pulsing red dot while the timer runs, an amber dot while paused. */
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
            .background(if (active) RidgelineTheme.colors.recording else RidgelineTheme.colors.pausedDot, CircleShape),
    )
}

@Composable
private fun PageDots(pager: PagerState, modifier: Modifier = Modifier) {
    Row(
        modifier
            .background(RidgelineTheme.colors.float.copy(alpha = 0.85f), CircleShape)
            .padding(horizontal = Spacing.s, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(pager.pageCount) { index ->
            val selected = pager.currentPage == index
            Box(
                Modifier
                    .size(6.dp)
                    .background(if (selected) RidgelineTheme.colors.accent else RidgelineTheme.colors.borderStrong, CircleShape),
            )
        }
    }
}

internal const val MAP_PAGE = 0
internal const val STATS_PAGE = 1
private const val PULSE_MS = 700
private const val PULSE_MIN_ALPHA = 0.25f
private val LANDSCAPE_PANEL_WIDTH = 400.dp
