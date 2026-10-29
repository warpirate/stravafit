// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.PillShape
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

private val ChipIconSize = 18.dp

/**
 * Pill-shaped filter chip. Selected chips get a `primaryContainer` fill plus a check icon, so the
 * state never depends on colour alone.
 */
@Composable
fun RidgelineFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        modifier = modifier,
        shape = PillShape,
        leadingIcon = when {
            selected -> ({ Icon(Icons.Rounded.Check, contentDescription = null, Modifier.size(ChipIconSize)) })
            leadingIcon != null -> ({ Icon(leadingIcon, contentDescription = null, Modifier.size(ChipIconSize)) })
            else -> null
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            labelColor = RidgelineTheme.colors.textSecondary,
            iconColor = RidgelineTheme.colors.textSecondary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = Color.Transparent,
        ),
    )
}

/** Pill-shaped assist chip for a contextual action, e.g. "Add photo". */
@Composable
fun RidgelineAssistChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        modifier = modifier,
        shape = PillShape,
        leadingIcon = leadingIcon?.let { icon ->
            { Icon(icon, contentDescription = null, Modifier.size(ChipIconSize)) }
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            leadingIconContentColor = MaterialTheme.colorScheme.primary,
        ),
        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = MaterialTheme.colorScheme.outlineVariant),
    )
}

/**
 * Small non-interactive status pill (e.g. GPS status). The [icon] carries the meaning alongside
 * [containerColor], so colour is never the only signal.
 */
@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    Surface(modifier = modifier, shape = PillShape, color = containerColor, contentColor = contentColor) {
        Row(
            Modifier.padding(horizontal = Spacing.m, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon?.invoke()
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@PreviewLightDark
@Composable
private fun ChipsPreview() {
    RidgelineTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    RidgelineFilterChip("Following", selected = true, onClick = {})
                    RidgelineFilterChip("You", selected = false, onClick = {})
                    RidgelineFilterChip("Run", selected = false, onClick = {}, leadingIcon = Icons.Rounded.DirectionsRun)
                }
                RidgelineAssistChip("Add photo", onClick = {}, leadingIcon = Icons.Rounded.AddAPhoto)
                StatusPill(
                    text = "GPS ready",
                    containerColor = RidgelineTheme.colors.successContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    icon = { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = RidgelineTheme.colors.success) },
                )
            }
        }
    }
}
