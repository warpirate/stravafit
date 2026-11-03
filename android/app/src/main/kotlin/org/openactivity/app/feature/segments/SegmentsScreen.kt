// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.segments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.format.UnitFormatter
import org.openactivity.app.ui.common.icon
import org.openactivity.core.data.model.SegmentEffort
import org.openactivity.core.data.model.SegmentSummary
import org.openactivity.core.data.repository.SegmentRepository
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.component.FrostedScaffold
import org.openactivity.core.designsystem.component.RankMedal
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject

/** One segment in a list: name, "0.52 km · 4.1% · 3 efforts", your best time and its star. */
data class SegmentRowUi(
    val id: Long,
    val name: String,
    val sport: SportType,
    val details: String,
    val best: String?,
    val starred: Boolean,
    val points: List<LatLng>,
)

/** A standing PR on one segment. */
data class SegmentPrUi(val segmentId: Long, val name: String, val time: String, val date: String, val details: String)

/** Which list the You, Segments screen shows. */
enum class SegmentsTab(val label: String) { STARRED("Starred"), ALL("All segments"), PRS("Your PRs") }

/** State of You, Segments. */
data class SegmentsUiState(
    val isLoading: Boolean = true,
    val tab: SegmentsTab = SegmentsTab.STARRED,
    val starred: List<SegmentRowUi> = emptyList(),
    val all: List<SegmentRowUi> = emptyList(),
    val prs: List<SegmentPrUi> = emptyList(),
)

/** Your segments: the starred ones, every one you created, and your PR on each. */
@HiltViewModel
class SegmentsViewModel @Inject constructor(
    private val segments: SegmentRepository,
    preferences: PreferencesRepository,
) : ViewModel() {
    private val tab = MutableStateFlow(SegmentsTab.STARRED)

    val uiState: StateFlow<SegmentsUiState> = combine(
        segments.observeSegments(),
        segments.observePersonalRecords(),
        preferences.preferences,
        tab,
    ) { list, prs, prefs, t ->
        val formatter = UnitFormatter(prefs.unitSystem, Locale.getDefault())
        val rows = list.map { rowOf(it, formatter) }
        SegmentsUiState(
            isLoading = false,
            tab = t,
            starred = rows.filter { it.starred },
            all = rows,
            prs = prs.map { prOf(it, formatter, Locale.getDefault()) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), SegmentsUiState())

    fun selectTab(t: SegmentsTab) {
        tab.value = t
    }

    fun toggleStar(id: Long, starred: Boolean) {
        viewModelScope.launch { segments.setStarred(id, !starred) }
    }

    internal companion object {
        fun rowOf(s: SegmentSummary, formatter: UnitFormatter): SegmentRowUi {
            val efforts = if (s.effortCount == 1) "1 effort" else "${s.effortCount} efforts"
            return SegmentRowUi(
                id = s.segment.id,
                name = s.segment.name,
                sport = s.segment.sport,
                details = "${SegmentFormat.lengthAndGrade(s.segment.distanceMeters, s.segment.averageGradePercent, formatter)} · $efforts",
                best = s.bestElapsed?.let { SegmentFormat.clock(it.inWholeSeconds) },
                starred = s.segment.starred,
                points = s.segment.points,
            )
        }

        fun prOf(e: SegmentEffort, formatter: UnitFormatter, locale: Locale) = SegmentPrUi(
            segmentId = e.segmentId,
            name = e.segmentName,
            time = SegmentFormat.clock(e.elapsed.inWholeSeconds),
            date = e.startTime.atZone(e.timeZone).toLocalDate().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)),
            details = SegmentFormat.lengthAndGrade(e.segmentDistanceMeters, e.segmentGradePercent, formatter),
        )
    }
}

/** You, Segments, connected to [SegmentsViewModel]. */
@Composable
fun SegmentsScreen(onNavigateUp: () -> Unit, onOpenSegment: (Long) -> Unit, viewModel: SegmentsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SegmentsContent(state, onNavigateUp, onOpenSegment, viewModel::selectTab, viewModel::toggleStar)
}

/** Stateless segments list with Starred / All / PRs chips. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SegmentsContent(
    state: SegmentsUiState,
    onNavigateUp: () -> Unit = {},
    onOpenSegment: (Long) -> Unit = {},
    onSelectTab: (SegmentsTab) -> Unit = {},
    onToggleStar: (Long, Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    FrostedScaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { RidgelineTopAppBar(title = "Segments", onNavigateUp = onNavigateUp) },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (state.isLoading) return@Box
            LazyColumn(
                Modifier.widthIn(max = Spacing.feedMaxWidth).fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = Spacing.xxl),
            ) {
                item(key = "tabs") {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.m),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        SegmentsTab.entries.forEach { t -> RidgelineFilterChip(t.label, selected = state.tab == t, onClick = { onSelectTab(t) }) }
                    }
                }
                when (state.tab) {
                    SegmentsTab.STARRED, SegmentsTab.ALL -> {
                        val rows = if (state.tab == SegmentsTab.STARRED) state.starred else state.all
                        if (rows.isEmpty()) {
                            item(key = "empty") { NoSegments(starredTab = state.tab == SegmentsTab.STARRED && state.all.isNotEmpty()) }
                        }
                        items(rows, key = { "s${it.id}" }) { row ->
                            SegmentListRow(row, onOpen = { onOpenSegment(row.id) }, onToggleStar = { onToggleStar(row.id, row.starred) })
                            HorizontalDivider(Modifier.padding(start = Spacing.gutter), color = RidgelineTheme.colors.hairline)
                        }
                    }
                    SegmentsTab.PRS -> {
                        if (state.prs.isEmpty()) item(key = "empty-prs") { NoSegments(prs = true) }
                        items(state.prs, key = { "p${it.segmentId}" }) { pr ->
                            PrRow(pr, onOpen = { onOpenSegment(pr.segmentId) })
                            HorizontalDivider(Modifier.padding(start = Spacing.gutter), color = RidgelineTheme.colors.hairline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SegmentListRow(row: SegmentRowUi, onOpen: () -> Unit, onToggleStar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClickLabel = "Open segment", onClick = onOpen)
            .padding(start = Spacing.gutter, end = Spacing.xs, top = Spacing.s, bottom = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(RidgelineTheme.colors.gap, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) { Icon(row.sport.icon, contentDescription = null, tint = RidgelineTheme.colors.accent) }
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.details, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary, maxLines = 1)
        }
        row.best?.let {
            Column(horizontalAlignment = Alignment.End) {
                Text(it, style = RidgelineTheme.text.statS)
                Text("PR", style = MaterialTheme.typography.labelSmall, color = RidgelineTheme.colors.textMuted)
            }
        }
        IconButton(onClick = onToggleStar, modifier = Modifier.semantics { contentDescription = if (row.starred) "Unstar ${row.name}" else "Star ${row.name}" }) {
            Icon(
                if (row.starred) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                contentDescription = null,
                tint = if (row.starred) RidgelineTheme.colors.accent else RidgelineTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun PrRow(pr: SegmentPrUi, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClickLabel = "Open segment", onClick = onOpen)
            .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RankMedal(1, description = "Personal record")
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Text(pr.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${pr.details} · ${pr.date}", style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary, maxLines = 1)
        }
        Text(pr.time, style = RidgelineTheme.text.statS)
    }
}

@Composable
private fun NoSegments(starredTab: Boolean = false, prs: Boolean = false) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = Icons.Rounded.Timeline,
            title = when {
                prs -> "No PRs yet"
                starredTab -> "No starred segments"
                else -> "No segments yet"
            },
            body = when {
                prs -> "Your fastest time on each segment shows up here once you have done it."
                starredTab -> "Star a segment to watch for it while you record, with your time against your PR."
                else -> "Open one of your activities, tap More and choose Create segment to race a stretch you do often."
            },
        )
    }
}
