// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.component.OverlineHeader
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.feature.record.R
import org.openactivity.feature.record.RouteSummary
import org.openactivity.feature.record.format.RecordFormat
import org.openactivity.model.SportCategory
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem

/** Sport picker (core-screens 3.2): search, recent chips, then every recordable sport by category. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SportPickerSheet(
    selected: SportType,
    recent: List<SportType>,
    onSelect: (SportType) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query) {
        recordableSports.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            stringResource(R.string.record_choose_sport),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Spacing.gutter),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.record_search_sports)) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter, vertical = Spacing.s),
        )
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = Spacing.x3)) {
            if (query.isBlank() && recent.isNotEmpty()) {
                item(key = "recent") {
                    OverlineHeader(stringResource(R.string.record_recent), Modifier.padding(horizontal = Spacing.gutter))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.gutter),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                    ) {
                        recent.forEach { sport ->
                            RidgelineFilterChip(sport.label, selected = sport == selected, onClick = { onSelect(sport) }, leadingIcon = sport.icon)
                        }
                    }
                }
            }
            SportCategory.entries.forEach { category ->
                val sports = filtered.filter { it.category == category }
                if (sports.isNotEmpty()) {
                    item(key = category.name) { OverlineHeader(category.label, Modifier.padding(horizontal = Spacing.gutter)) }
                    items(sports, key = { it.apiName }) { sport -> SportRow(sport, sport == selected) { onSelect(sport) } }
                }
            }
        }
    }
}

@Composable
private fun SportRow(sport: SportType, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.listRowMin)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = Spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(sport.icon, contentDescription = null, tint = RidgelineTheme.colors.textSecondary)
        Spacer(Modifier.width(Spacing.l))
        Text(sport.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (!sport.usesGps) {
            Text(stringResource(R.string.record_indoor), style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textMuted)
            Spacer(Modifier.width(Spacing.s))
        }
        RadioButton(selected = selected, onClick = null)
    }
}

/**
 * Saved routes to follow (record detail 2.6). Shows a spinner while loading and an honest empty state when
 * nothing has been saved yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoutePickerSheet(
    routes: List<RouteSummary>?,
    selectedId: Long?,
    units: UnitSystem,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.gutter), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.record_choose_route), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (selectedId != null) {
                TextButton(onClick = { onSelect(null) }) { Text(stringResource(R.string.record_remove_route)) }
            }
        }
        when {
            routes == null -> Column(Modifier.fillMaxWidth().padding(Spacing.x3), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
            }
            routes.isEmpty() -> EmptyState(
                title = stringResource(R.string.record_no_routes_title),
                body = stringResource(R.string.record_no_routes_body),
                icon = Icons.Rounded.Route,
                modifier = Modifier.padding(bottom = Spacing.x3),
            )
            else -> LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = Spacing.x3)) {
                items(routes, key = { it.id }) { route ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Spacing.listRowMin)
                            .selectable(selected = route.id == selectedId, role = Role.RadioButton) { onSelect(route.id) }
                            .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(route.sport?.icon ?: Icons.Rounded.Route, contentDescription = null, tint = RidgelineTheme.colors.info)
                        Spacer(Modifier.width(Spacing.l))
                        Column(Modifier.weight(1f)) {
                            Text(route.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${RecordFormat.distance(route.distanceMeters, units)} ${RecordFormat.distanceUnit(units)}",
                                style = RidgelineTheme.text.caption,
                                color = RidgelineTheme.colors.textSecondary,
                            )
                        }
                        RadioButton(selected = route.id == selectedId, onClick = null)
                    }
                }
            }
        }
    }
}
