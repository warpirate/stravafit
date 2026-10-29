// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.me

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import org.openactivity.app.ui.common.ProfileAvatar
import org.openactivity.core.designsystem.component.StatValue
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.ui.common.icon
import org.openactivity.app.ui.common.label
import org.openactivity.core.designsystem.component.ActivityCardSkeleton
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.component.RidgelineCard
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.component.SectionHeader
import org.openactivity.core.designsystem.component.StatGrid
import org.openactivity.core.designsystem.component.TopBarAction
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.model.SportType

/** Me tab, connected to [MeViewModel]. */
@Composable
fun MeScreen(
    onOpenSettings: () -> Unit,
    onEditProfile: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    onRecord: () -> Unit,
    viewModel: MeViewModel = hiltViewModel(),
) {
    val me by viewModel.uiState.collectAsStateWithLifecycle()
    val recordingStatus: org.openactivity.app.ui.RecordingStatusViewModel = hiltViewModel()
    val recording by recordingStatus.active.collectAsStateWithLifecycle()
    val state = me.copy(recordingInProgress = recording != null)
    MeContent(
        state = state,
        actions = MeActions(
            onOpenSettings = onOpenSettings,
            onSelectTab = viewModel::selectTab,
            onSelectSport = viewModel::selectSport,
            onSearch = viewModel::search,
            onFilterActivities = viewModel::filterActivities,
            onOpenActivity = onOpenActivity,
            onEditProfile = onEditProfile,
            onRecord = onRecord,
        ),
    )
}

/** Callbacks for [MeContent]. */
data class MeActions(
    val onOpenSettings: () -> Unit = {},
    val onSelectTab: (MeTab) -> Unit = {},
    val onSelectSport: (SportType?) -> Unit = {},
    val onSearch: (String) -> Unit = {},
    val onFilterActivities: (SportType?) -> Unit = {},
    val onOpenActivity: (Long) -> Unit = {},
    val onEditProfile: () -> Unit = {},
    val onRecord: () -> Unit = {},
)

/**
 * Stateless You tab (strava direction): a small avatar, "You", search and settings in the top bar;
 * Progress / Activities tabs; then sections on the grey gap band.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeContent(state: MeUiState, actions: MeActions, modifier: Modifier = Modifier) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = RidgelineTheme.colors.gap,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            RidgelineTopAppBar(
                title = "You",
                scrollBehavior = scrollBehavior,
                hairline = false,
                leading = state.header?.let { header ->
                    {
                        Box(Modifier.clickable(onClickLabel = "Edit profile", onClick = actions.onEditProfile)) {
                            ProfileAvatar(header.name, header.photoPath, size = 36.dp)
                        }
                    }
                },
            ) {
                TopBarAction(Icons.Rounded.Search, "Search activities", onClick = { actions.onSelectTab(MeTab.Activities) })
                TopBarAction(Icons.Outlined.Settings, "Settings", actions.onOpenSettings)
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = Spacing.feedMaxWidth).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = Spacing.xxl),
            ) {
                val header = state.header
                if (state.isLoading || header == null) {
                    item { ActivityCardSkeleton(Modifier.padding(Spacing.gutter)) }
                    return@LazyColumn
                }
                item(key = "tabs") { MeTabs(state.tab, actions.onSelectTab) }
                when {
                    !state.hasAnyActivity -> item(key = "empty") { NoActivities(actions.onRecord, state.recordingInProgress) }
                    state.tab == MeTab.Progress -> progressItems(state, actions)
                    else -> activityItems(state, actions)
                }
            }
        }
    }
}

@Composable
private fun NoActivities(onRecord: () -> Unit, recording: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        EmptyState(
            icon = Icons.Rounded.DirectionsRun,
            title = if (recording) "Recording in progress" else "No activities yet",
            body = if (recording) {
                "Your progress, totals and best efforts update once you finish and save it."
            } else {
                "Your weekly progress, totals and best efforts build up from the activities you record."
            },
            primaryActionLabel = if (recording) "Back to recording" else "Record an activity",
            onPrimaryAction = onRecord,
        )
    }
}

@Composable
private fun MeTabs(selected: MeTab, onSelect: (MeTab) -> Unit) {
    TabRow(
        selectedTabIndex = selected.ordinal,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        indicator = { positions ->
            TabRowDefaults.PrimaryIndicator(
                Modifier.tabIndicatorOffset(positions[selected.ordinal]),
                width = positions[selected.ordinal].width,
                height = 3.dp,
                color = RidgelineTheme.colors.accent,
                shape = RectangleShape,
            )
        },
        divider = { HorizontalDivider(color = RidgelineTheme.colors.hairline) },
    ) {
        MeTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = { Text(tab.label, style = RidgelineTheme.text.tab) },
                unselectedContentColor = RidgelineTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun SportChips(sports: List<SportType>, selected: SportType?, allLabel: String, onSelect: (SportType?) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter, vertical = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        RidgelineFilterChip(allLabel, selected = selected == null, onClick = { onSelect(null) })
        sports.forEach { sport ->
            RidgelineFilterChip(sport.label, selected = sport == selected, onClick = { onSelect(sport) }, leadingIcon = sport.icon)
        }
    }
}

/**
 * Circular outlined sport buttons (run, ride, walk, then any other sport you did); tapping the selected
 * one again shows all sports. With a single sport recorded it is the one highlighted, since "all" and
 * that sport are the same numbers.
 */
@Composable
private fun SportCircles(sports: List<SportType>, selected: SportType?, onSelect: (SportType?) -> Unit) {
    val shown = (sports + CORE_SPORTS).distinct().take(MAX_SPORT_CIRCLES)
    val highlighted = selected ?: sports.singleOrNull()
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter, vertical = Spacing.l),
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        shown.forEach { sport ->
            val isSelected = sport == highlighted
            Surface(
                onClick = { onSelect(if (isSelected) null else sport) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                contentColor = if (isSelected) RidgelineTheme.colors.accent else MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(if (isSelected) 2.dp else 1.5.dp, if (isSelected) RidgelineTheme.colors.accent else RidgelineTheme.colors.borderStrong),
                modifier = Modifier.size(SPORT_CIRCLE).semantics {
                    contentDescription = sport.label
                    this.selected = isSelected
                },
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(sport.icon, contentDescription = null) }
            }
        }
    }
}

private val CORE_SPORTS = listOf(SportType.RUN, SportType.RIDE, SportType.WALK)
private const val BEST_EFFORTS_SHOWN = 3

/** A white section on the gap band. */
@Composable
private fun Section(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(top = Spacing.s).background(MaterialTheme.colorScheme.surface),
        content = content,
    )
}

@Composable
private fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = Spacing.gutter, end = Spacing.xs, top = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action, color = RidgelineTheme.colors.accentText) }
    }
}

private fun LazyListScope.progressItems(state: MeUiState, actions: MeActions) {
    item(key = "week") {
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            SportCircles(state.sports, state.selectedSport, actions.onSelectSport)
            Column(Modifier.padding(horizontal = Spacing.gutter).padding(bottom = Spacing.l)) {
                Text(
                    listOf("This week", state.weekRange).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RidgelineTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(Spacing.s))
                val big = state.thisWeek.filter { it.label != "Activities" }.take(3)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.x3)) {
                    big.forEach { StatValue(it, valueStyle = RidgelineTheme.text.statL) }
                }
                state.weeklyChart?.let {
                    Spacer(Modifier.height(Spacing.m))
                    WeeklyChart(it)
                }
            }
        }
    }
    if (state.weekDays.size == DAYS) {
        item(key = "log") {
            Section {
                SectionTitle("Training log")
                TrainingLog(state.weekDays, state.todayIndex)
            }
        }
    }
    state.bestEffortsTitle?.let { title ->
        item(key = "bests") {
            var all by rememberSaveable { mutableStateOf(false) }
            val more = state.bestEfforts.size > BEST_EFFORTS_SHOWN
            Section {
                SectionTitle(title, action = if (more) (if (all) "Less" else "All") else null, onAction = { all = !all })
                BestEffortsTable(if (all) state.bestEfforts else state.bestEfforts.take(BEST_EFFORTS_SHOWN), actions.onOpenActivity)
            }
        }
    }
    item(key = "stats") {
        Section {
            SectionTitle("${state.selectedSport?.label ?: "All sports"} totals")
            Column(Modifier.padding(Spacing.gutter)) {
                Text("${state.year}", style = MaterialTheme.typography.titleSmall, color = RidgelineTheme.colors.textSecondary)
                Spacer(Modifier.height(Spacing.m))
                StatGrid(state.yearToDate, columns = 2)
                Spacer(Modifier.height(Spacing.l))
                HorizontalDivider(color = RidgelineTheme.colors.hairline)
                Spacer(Modifier.height(Spacing.l))
                Text("All time", style = MaterialTheme.typography.titleSmall, color = RidgelineTheme.colors.textSecondary)
                Spacer(Modifier.height(Spacing.m))
                StatGrid(state.allTime, columns = 2)
            }
        }
    }
}

/** Monday to Sunday: a dot per day sized by volume (a tiny grey dot on rest days); today in bold. */
@Composable
private fun TrainingLog(days: List<Float>, todayIndex: Int) {
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s, vertical = Spacing.l)) {
        days.forEachIndexed { i, f ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(LOG_DOT_MAX), contentAlignment = Alignment.Center) {
                    val d = if (f > 0f) LOG_DOT_MIN + (LOG_DOT_MAX - LOG_DOT_MIN) * f else 6.dp
                    Box(
                        Modifier
                            .size(d)
                            .background(if (f > 0f) RidgelineTheme.colors.accent else RidgelineTheme.colors.borderStrong, CircleShape),
                    )
                }
                Spacer(Modifier.height(Spacing.s))
                Text(
                    labels[i],
                    style = MaterialTheme.typography.labelLarge,
                    color = if (i == todayIndex) MaterialTheme.colorScheme.onSurface else RidgelineTheme.colors.textMuted,
                    fontWeight = if (i == todayIndex) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

private const val DAYS = 7
private const val MAX_SPORT_CIRCLES = 6
private val SPORT_CIRCLE = 48.dp
private val LOG_DOT_MIN = 18.dp
private val LOG_DOT_MAX = 40.dp

@Composable
private fun BestEffortsTable(efforts: List<BestEffortUi>, onOpenActivity: (Long) -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.gutter).padding(bottom = Spacing.s)) {
        efforts.forEach { effort ->
            HorizontalDivider(color = RidgelineTheme.colors.hairline)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.listRowMin)
                    .clickable { onOpenActivity(effort.activityId) }
                    .padding(vertical = Spacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Medal(rank = 1)
                Spacer(Modifier.width(Spacing.l))
                Text(effort.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(effort.time, style = RidgelineTheme.text.statS)
                Spacer(Modifier.width(Spacing.l))
                Text(effort.date, style = MaterialTheme.typography.bodyMedium, color = RidgelineTheme.colors.textMuted)
            }
        }
    }
}

/** Our own ribbon-medal glyph: a gold disc with the rank (every row here is a personal best, rank 1). */
@Composable
private fun Medal(rank: Int) {
    val color = when (rank) {
        1 -> RidgelineTheme.colors.medalGold
        2 -> RidgelineTheme.colors.medalSilver
        else -> RidgelineTheme.colors.medalBronze
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = 10.dp, height = 6.dp).background(RidgelineTheme.colors.accent))
        Box(Modifier.size(22.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Text("$rank", style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.onMedal, fontWeight = FontWeight.Bold)
        }
    }
}

private fun LazyListScope.activityItems(state: MeUiState, actions: MeActions) {
    item(key = "search") {
        OutlinedTextField(
            value = state.query,
            onValueChange = actions.onSearch,
            placeholder = { Text("Search your activities") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { actions.onSearch("") }) { Icon(Icons.Rounded.Clear, contentDescription = "Clear search") }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.gutter, end = Spacing.gutter, top = Spacing.m),
        )
    }
    if (state.sports.size > 1) {
        item(key = "activity-sports") { SportChips(state.sports, state.activitySport, "All", actions.onFilterActivities) }
    }
    if (state.activities.isEmpty()) {
        item(key = "no-match") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "No matching activities",
                    body = "Try another word or sport.",
                    primaryActionLabel = "Clear filters",
                    onPrimaryAction = {
                        actions.onSearch("")
                        actions.onFilterActivities(null)
                    },
                )
            }
        }
        return
    }
    items(state.activities, key = { it.id }) { row ->
        Column(Modifier.padding(horizontal = Spacing.gutter)) {
            ActivityRow(row, onClick = { actions.onOpenActivity(row.id) })
            HorizontalDivider(color = RidgelineTheme.colors.hairline)
        }
    }
}
