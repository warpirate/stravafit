// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.record

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.openactivity.app.model.Sport
import org.openactivity.app.model.SportGroup
import org.openactivity.app.ui.common.icon
import org.openactivity.core.designsystem.component.OverlineHeader
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

/** Sport picker sheet (core-screens 3.2): recent sports as chips, then every sport grouped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SportPickerSheet(
    selected: Sport,
    recent: List<Sport>,
    onSelect: (Sport) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            "Choose a sport",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = Spacing.gutter),
        )
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Spacing.x3)) {
            item {
                OverlineHeader("Recent", Modifier.padding(horizontal = Spacing.gutter))
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.gutter),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                ) {
                    recent.forEach { sport ->
                        RidgelineFilterChip(sport.label, selected = sport == selected, onClick = { onSelect(sport) }, leadingIcon = sport.icon)
                    }
                }
            }
            SportGroup.entries.forEach { group ->
                item(key = group) { OverlineHeader(group.label, Modifier.padding(horizontal = Spacing.gutter)) }
                items(Sport.entries.filter { it.group == group }, key = { it.name }) { sport ->
                    SportRow(sport, selected = sport == selected, onClick = { onSelect(sport) })
                }
            }
        }
    }
}

@Composable
private fun SportRow(sport: Sport, selected: Boolean, onClick: () -> Unit) {
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
            Text("Indoor", style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textMuted)
            Spacer(Modifier.width(Spacing.s))
        }
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(start = 4.dp))
    }
}
