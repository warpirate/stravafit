// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.component.RidgelineCard
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

/** A card holding a group of settings rows. */
@Composable
internal fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    RidgelineCard(contentPadding = Spacing.none) {
        Column(Modifier.selectableGroup(), content = content)
    }
}

@Composable
internal fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = Spacing.l), color = RidgelineTheme.colors.hairline)
}

/** One choice of a radio group. */
@Composable
internal fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.listRowMin)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = null)
    }
}

/** A labelled switch; the whole row toggles. */
@Composable
internal fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.listRowMin)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textMuted) }
        }
        Spacer(Modifier.width(Spacing.m))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Read-only title and value. */
@Composable
internal fun InfoRow(title: String, value: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.listRowMin)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary)
    }
}

/** A tappable row with a chevron, or a spinner while [busy]. */
@Composable
internal fun ActionRow(title: String, subtitle: String?, onClick: () -> Unit, icon: ImageVector? = null, busy: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.listRowMin)
            .clickable(enabled = !busy, role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = RidgelineTheme.colors.textSecondary)
            Spacer(Modifier.width(Spacing.l))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary) }
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = RidgelineTheme.colors.textMuted)
        }
    }
}

/** A title over a horizontally scrolling row of single-choice chips. */
@Composable
internal fun <T> ChipChoiceRow(title: String, subtitle: String?, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.m)) {
        Column(Modifier.padding(horizontal = Spacing.l)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textMuted) }
        }
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            options.forEach { option ->
                RidgelineFilterChip(label(option), selected = option == selected, onClick = { onSelect(option) })
            }
        }
    }
}
