// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.shareexport.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.export.art.ArtAspect
import org.openactivity.core.export.art.ArtColorBy
import org.openactivity.core.export.art.ArtTemplate
import org.openactivity.core.export.art.ArtTheme
import org.openactivity.core.export.art.RouteArtComposer

private val TemplateCardWidth = 112.dp
private val SwatchSize = 40.dp

/** Horizontally scrolling template cards; the selected one is outlined in the primary colour. */
@Composable
internal fun TemplateCarousel(selected: ArtTemplate, onSelect: (ArtTemplate) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Spacing.l),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(ArtTemplate.entries, key = { it.name }) { template ->
            val isSelected = template == selected
            OutlinedCard(
                modifier = Modifier
                    .width(TemplateCardWidth)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(template) }),
                border = BorderStroke(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(Spacing.m),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(template.icon, contentDescription = null)
                    Text(
                        template.label,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = Spacing.s),
                    )
                }
            }
        }
    }
}

/** Output sizes of the current template; hidden when the template has only one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AspectChips(aspects: List<ArtAspect>, selected: ArtAspect, onSelect: (ArtAspect) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        aspects.forEach { aspect ->
            RidgelineFilterChip(label = aspect.label, selected = aspect == selected, onClick = { onSelect(aspect) })
        }
    }
}

/** Colour-theme swatches: background with an accent dot, labelled for TalkBack. */
@Composable
internal fun ThemePicker(selected: ArtTheme, onSelect: (ArtTheme) -> Unit, modifier: Modifier = Modifier) {
    val composer = remember { RouteArtComposer() }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
        ArtTheme.entries.forEach { theme ->
            val isSelected = theme == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(theme) }),
            ) {
                Box(
                    Modifier
                        .size(SwatchSize)
                        .clip(CircleShape)
                        .background(Color(composer.backgroundColor(theme)))
                        .border(
                            if (isSelected) 3.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(composer.accentColor(theme))))
                }
                Text(
                    theme.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else RidgelineTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}

/** Route colour modes; modes without data in this activity are disabled. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColorByChips(
    options: List<Pair<ArtColorBy, Boolean>>,
    selected: ArtColorBy,
    enabled: Boolean,
    onSelect: (ArtColorBy) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        options.forEach { (colorBy, available) ->
            val chipEnabled = enabled && available
            FilterChip(
                selected = colorBy == selected && chipEnabled,
                onClick = { onSelect(colorBy) },
                enabled = chipEnabled,
                label = { Text(colorBy.label, style = MaterialTheme.typography.labelMedium) },
                shape = CircleShape,
            )
        }
    }
}

/** A labelled switch row with optional supporting text; the whole row toggles. */
@Composable
internal fun SwitchRow(
    title: String,
    body: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 56.dp)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.l)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) {
                Text(body, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
