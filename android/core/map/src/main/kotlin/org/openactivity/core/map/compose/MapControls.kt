// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.compose

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.map.R
import org.openactivity.core.map.style.MapStyleDefinition
import org.openactivity.core.map.style.MapStyleKind

/**
 * Recentre button. Shows a filled arrow while following and a crosshair otherwise; tapping it
 * (re)starts follow mode.
 */
@Composable
fun RecenterButton(state: MapState, modifier: Modifier = Modifier) {
    val following = state.tracking == CameraTracking.Follow
    val description = stringResource(if (following) R.string.map_following else R.string.map_recenter)
    SmallFloatingActionButton(
        onClick = state::followUser,
        modifier = modifier.semantics {
            contentDescription = description
            selected = following
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = if (following) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    ) {
        Icon(if (following) Icons.Rounded.NearMe else Icons.Rounded.MyLocation, contentDescription = null)
    }
}

/** A row of chips to pick the base map from [available]. */
@Composable
fun MapStyleSwitcher(
    available: List<MapStyleKind>,
    selected: MapStyleKind,
    onSelect: (MapStyleKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .semantics { role = Role.Tab },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        available.forEach { kind ->
            RidgelineFilterChip(
                label = stringResource(kind.labelRes),
                selected = kind == selected,
                onClick = { onSelect(kind) },
            )
        }
    }
}

/**
 * The visible credit line for [definition]'s base map ("© OpenStreetMap contributors" and the
 * tile provider), each part linking to its licence page. [RidgelineMap] draws this itself; use it
 * directly next to static map images such as snapshots.
 */
@Composable
fun MapAttribution(definition: MapStyleDefinition, modifier: Modifier = Modifier) {
    val linkColor = MaterialTheme.colorScheme.onSurface
    val text = buildAnnotatedString {
        definition.attribution.forEachIndexed { i, part ->
            if (i > 0) append(" · ")
            withLink(LinkAnnotation.Url(part.url, TextLinkStyles(SpanStyle(color = linkColor)))) { append(part.text) }
        }
    }
    val label = stringResource(R.string.map_attribution_label, definition.attributionText)
    Surface(
        modifier = modifier.semantics { contentDescription = label },
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = RidgelineTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
