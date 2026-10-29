// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openactivity.app.ui.common.RouteThumbnail
import org.openactivity.app.ui.common.icon
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

private val ThumbWidth = 88.dp

/** Activities list row: route thumbnail (or sport tile), title, date, key stats, visibility. */
@Composable
fun ActivityRow(row: ActivityRowUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(ThumbWidth)) {
            if (row.route != null) {
                RouteThumbnail(row.route, aspectRatio = THUMB_RATIO, contentDescription = "Route of ${row.title}")
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = ThumbWidth / THUMB_RATIO)
                        .background(RidgelineTheme.colors.surfaceSunken, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(row.sport.icon, contentDescription = row.sport.label, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.width(Spacing.m))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(row.sport.icon, contentDescription = row.sport.label, Modifier.size(16.dp), tint = RidgelineTheme.colors.textMuted)
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    row.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Text(row.statsLine, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.date, style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textMuted)
                row.visibilityLabel?.let {
                    Text("  ·  ", style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textMuted)
                    Icon(Icons.Rounded.Lock, contentDescription = null, Modifier.size(12.dp), tint = RidgelineTheme.colors.textMuted)
                    Spacer(Modifier.width(2.dp))
                    Text(it, style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textMuted)
                }
            }
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = RidgelineTheme.colors.textMuted)
    }
}

private const val THUMB_RATIO = 1.4f
