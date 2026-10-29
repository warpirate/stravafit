// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.model.SportType

/**
 * Shown instead of an empty map for activities without GPS (home-feed 2.5): the sport glyph on a
 * sunken tile, with the sport name when [showLabel] is true.
 */
@Composable
fun SportTile(
    sport: SportType,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 4f,
    showLabel: Boolean = true,
    iconSize: Dp = 28.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(RidgelineTheme.colors.surfaceSunken, MaterialTheme.shapes.small)
            .semantics { contentDescription = "${sport.label}, no map" },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Icon(sport.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(iconSize))
            if (showLabel) {
                Text(sport.label, style = MaterialTheme.typography.titleSmall, color = RidgelineTheme.colors.textSecondary)
            }
        }
    }
}
