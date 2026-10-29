// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.ui

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
internal fun DetailBody(content: DetailContent) {
    var scrubIndex by remember(content.id) { mutableStateOf<Int?>(null) }
    var selectedSplit by rememberSaveable(content.id) { mutableStateOf<Int?>(null) }
    val marker = scrubIndex?.let { content.chart?.positions?.getOrNull(it) }
    val focus = selectedSplit?.let { content.splits.getOrNull(it) }?.let { content.routeBetween(it.startMeters, it.endMeters) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.x4),
    ) {
        item(key = "header") { Header(content) }
        item(key = "map") {
            if (content.hasRoute) {
                DetailMap(
                    route = content.route,
                    speeds = content.routeSpeeds,
                    marker = marker,
                    focus = focus,
                    modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                )
            } else {
                NoRouteNote(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.s))
            }
        }
        item(key = "stats") {
            Column(Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.m)) {
                StatGrid(content.stats, columns = 3)
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
                    AnalysisSection(chart, content.sport, content.units, scrubIndex, onScrub = { scrubIndex = it })
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
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(sportIcon(content.sport), null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
            }
            Text(
                content.startedAt,
                style = MaterialTheme.typography.bodyMedium,
                color = RidgelineTheme.colors.textSecondary,
                modifier = Modifier.padding(start = Spacing.s),
            )
        }
        Spacer(Modifier.height(Spacing.s))
        Text(content.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        if (content.description.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(content.description, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(Spacing.s))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Tag(visibilityIcon(content.visibility), visibilityLabel(content.visibility))
            content.perceivedExertion?.let { Tag(null, "Effort $it/10 · ${exertionBand(it)}") }
            if (content.muted) Tag(Icons.Rounded.VolumeOff, "Muted from feeds")
        }
    }
}

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
