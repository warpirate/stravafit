// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import org.openactivity.core.designsystem.theme.Elevation
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

/**
 * Standard Ridgeline card: `surface` fill, 16 dp radius, elevation level 1 (soft shadow in light
 * mode, tonal lift in dark mode).
 *
 * @param onClick makes the whole card clickable when not null.
 * @param contentPadding inner padding; pass 0 dp for edge-to-edge media.
 */
@Composable
fun RidgelineCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = Spacing.cardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val elevation = CardDefaults.elevatedCardElevation(defaultElevation = Elevation.level1, pressedElevation = Elevation.level2)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
    }
    if (onClick != null) {
        ElevatedCard(onClick, modifier, shape = MaterialTheme.shapes.large, colors = colors, elevation = elevation, content = inner)
    } else {
        ElevatedCard(modifier, shape = MaterialTheme.shapes.large, colors = colors, elevation = elevation, content = inner)
    }
}

@PreviewLightDark
@Composable
private fun CardPreview() {
    RidgelineTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            RidgelineCard(Modifier.padding(Spacing.l)) {
                Text("Easy morning shakeout", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Felt good after the rest day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RidgelineTheme.colors.textSecondary,
                )
            }
        }
    }
}
