// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.ui

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import org.openactivity.core.designsystem.component.AthleteAvatar
import org.openactivity.core.designsystem.component.LocalAthlete
import org.openactivity.core.map.camera.MapPadding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.openactivity.core.designsystem.component.Stat
import org.openactivity.core.designsystem.component.StatValue
import org.openactivity.feature.activitydetail.EffortRow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.openactivity.core.activityextras.ui.PhotoThumbnail
import org.openactivity.core.designsystem.component.StatGrid
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.feature.activitydetail.DetailContent
import org.openactivity.feature.activitydetail.format.DetailFormat

/** The scrolling body of the detail screen. Chart scrubbing and split selection drive the map. */
@Composable
internal fun DetailBody(content: DetailContent, listState: LazyListState = rememberLazyListState()) {
    var scrubIndex by remember(content.id) { mutableStateOf<Int?>(null) }
    var selectedSplit by rememberSaveable(content.id) { mutableStateOf<Int?>(null) }
    val marker = scrubIndex?.let { content.chart?.positions?.getOrNull(it) }
    val focus = selectedSplit?.let { content.splits.getOrNull(it) }?.let { content.routeBetween(it.startMeters, it.endMeters) }

    // The route is framed clear of the status bar and the floating back / share / more buttons.
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val fitPadding = MapPadding(
        HERO_SIDE_PADDING_DP,
        statusBar.value + TOP_BUTTONS_CLEARANCE.value,
        HERO_SIDE_PADDING_DP,
        HERO_SIDE_PADDING_DP,
    )
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.x4),
    ) {
        item(key = "map") {
            if (content.hasRoute) {
                // Full-bleed hero map; the back, share and more buttons float over it.
                DetailMap(
                    route = content.route,
                    speeds = content.routeSpeeds,
                    marker = marker,
                    focus = focus,
                    height = HERO_MAP_HEIGHT,
                    shape = RectangleShape,
                    fitPadding = fitPadding,
                )
            } else {
                Spacer(Modifier.statusBarsPadding().height(TOP_BUTTONS_CLEARANCE))
                NoRouteNote(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s))
            }
        }
        item(key = "header") { Header(content) }
        item(key = "stats") {
            Column(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s)) {
                StatTable(content.stats.filter { it.value != null })
                content.efforts.firstOrNull { it.isPersonalBest }?.let { pr ->
                    PrRow(pr, more = content.efforts.count { it.isPersonalBest } - 1)
                }
                content.notes.forEach { note ->
                    Text(
                        note,
                        style = RidgelineTheme.text.caption,
                        color = RidgelineTheme.colors.textMuted,
                        modifier = Modifier.padding(top = Spacing.s),
                    )
                }
            }
        }
        if (content.photos.isNotEmpty()) {
            item(key = "photos") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.gutter),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    modifier = Modifier.padding(vertical = Spacing.s),
                ) {
                    items(content.photos, key = { it.id }) { photo ->
                        PhotoThumbnail(
                            photo = photo,
                            contentDescription = "Activity photo",
                            targetSize = 120.dp,
                            placeholder = RidgelineTheme.colors.skeleton,
                            modifier = Modifier.size(120.dp).clip(MaterialTheme.shapes.small),
                        )
                    }
                }
            }
        }
        content.chart?.let { chart ->
            item(key = "analysis") {
                Section("Analysis") {
                    AnalysisSection(chart, content.sport, content.units, scrubIndex, onScrub = { scrubIndex = it }, averageSpeedMps = content.averageSpeedMps)
                }
            }
        }
        if (content.splits.isNotEmpty()) {
            item(key = "splits") {
                Section("Splits") {
                    SplitsTable(
                        rows = content.splits,
                        unitHeader = content.splitUnit,
                        speedHeader = DetailFormat.speedLabel(content.sport),
                        selected = selectedSplit,
                        onSelect = { selectedSplit = it },
                    )
                }
            }
        }
        if (content.laps.isNotEmpty()) {
            item(key = "laps") {
                Section("Laps") { LapsTable(content.laps, DetailFormat.speedLabel(content.sport)) }
            }
        }
        if (content.showEfforts) {
            item(key = "efforts") {
                Section("Best efforts") {
                    if (content.efforts.isEmpty()) {
                        Text(
                            "No standard distances covered in this activity.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RidgelineTheme.colors.textSecondary,
                        )
                    } else {
                        EffortsList(content.efforts)
                    }
                }
            }
        }
        item(key = "footer") { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(content: DetailContent) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter).padding(top = Spacing.l, bottom = Spacing.s)) {
        // Author row (strava direction): avatar, name, "date · place" with the sport icon, and the device.
        val athlete = LocalAthlete.current
        Row(verticalAlignment = Alignment.CenterVertically) {
            AthleteAvatar(athlete, size = AUTHOR_AVATAR)
            Column(Modifier.padding(start = Spacing.m)) {
                Text(athlete.name, style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(sportIcon(content.sport), content.sportName, tint = RidgelineTheme.colors.textMuted, modifier = Modifier.size(14.dp))
                    Text(
                        listOf(content.startedAt, athlete.location).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RidgelineTheme.colors.textMuted,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
                if (athlete.device.isNotBlank()) {
                    Text(athlete.device, style = MaterialTheme.typography.bodyMedium, color = RidgelineTheme.colors.textMuted)
                }
            }
        }
        Spacer(Modifier.height(Spacing.m))
        Text(content.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        if (content.description.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(content.description, style = MaterialTheme.typography.bodyLarge, color = RidgelineTheme.colors.textSecondary)
        }
        Spacer(Modifier.height(Spacing.s))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Tag(visibilityIcon(content.visibility), visibilityLabel(content.visibility))
            content.perceivedExertion?.let { Tag(null, "Effort $it/10 · ${exertionBand(it)}") }
            if (content.muted) Tag(Icons.Rounded.VolumeOff, "Muted from feeds")
        }
    }
}

/** Stats three to a row, rows separated by horizontal hairlines, Title Case labels, no vertical dividers. */
@Composable
private fun StatTable(stats: List<Stat>) {
    Column(Modifier.fillMaxWidth()) {
        stats.chunked(STAT_COLUMNS).forEach { row ->
            HorizontalDivider(color = RidgelineTheme.colors.hairline)
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.m)) {
                row.forEach { StatValue(it, Modifier.weight(1f), RidgelineTheme.text.statM) }
                repeat(STAT_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        HorizontalDivider(color = RidgelineTheme.colors.hairline)
    }
}

/** "PR · Half marathon   1:56:12   +3 more" with a gold medal. */
@Composable
private fun PrRow(effort: EffortRow, more: Int) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).background(RidgelineTheme.colors.medalGold, CircleShape), contentAlignment = Alignment.Center) {
                Text("1", style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.onMedal, fontWeight = FontWeight.Bold)
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("PR") }
                    append(" · ${effort.name}")
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(start = Spacing.m),
            )
            Text(effort.result, style = RidgelineTheme.text.statS)
            if (more > 0) {
                Text("+$more more", style = MaterialTheme.typography.bodyLarge, color = RidgelineTheme.colors.accentText, modifier = Modifier.padding(start = Spacing.m))
            }
        }
        HorizontalDivider(color = RidgelineTheme.colors.hairline)
    }
}

private const val STAT_COLUMNS = 3
internal val HERO_MAP_HEIGHT = 300.dp
private val TOP_BUTTONS_CLEARANCE = 64.dp
private const val HERO_SIDE_PADDING_DP = 36f
private val AUTHOR_AVATAR = 48.dp

@Composable
private fun Tag(icon: ImageVector?, text: String) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(RidgelineTheme.colors.surfaceSunken)
            .padding(horizontal = Spacing.s, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(14.dp), tint = RidgelineTheme.colors.textSecondary)
            Spacer(Modifier.size(Spacing.xs))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = RidgelineTheme.colors.textSecondary)
    }
}

@Composable
private fun NoRouteNote(modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(RidgelineTheme.colors.surfaceSunken)
            .padding(Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.LocationOff, null, tint = RidgelineTheme.colors.textMuted)
        Text(
            "No GPS route for this activity",
            style = MaterialTheme.typography.bodyMedium,
            color = RidgelineTheme.colors.textSecondary,
            modifier = Modifier.padding(start = Spacing.m),
        )
    }
}

@Composable
private fun Section(title: String, body: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = RidgelineTheme.colors.hairline, modifier = Modifier.padding(vertical = Spacing.s))
        Column(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }.padding(bottom = Spacing.s))
            body()
        }
    }
}
