// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.PillShape
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

private val EmptyIconCircle = 64.dp
private val EmptyIconSize = 32.dp
private val EmptyMaxWidth = 360.dp

/**
 * Empty state: a 32 dp icon in a 64 dp `primaryContainer` circle, a title, muted body copy,
 * one filled call to action and an optional secondary action.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    primaryActionLabel: String? = null,
    onPrimaryAction: () -> Unit = {},
    secondaryActionLabel: String? = null,
    onSecondaryAction: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .widthIn(max = EmptyMaxWidth)
            .padding(horizontal = Spacing.xxl, vertical = Spacing.x3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(EmptyIconCircle)
                .background(MaterialTheme.colorScheme.primaryContainer, PillShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(EmptyIconSize),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(Spacing.l))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(Spacing.s))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = RidgelineTheme.colors.textMuted,
            textAlign = TextAlign.Center,
        )
        if (primaryActionLabel != null || secondaryActionLabel != null) {
            Spacer(Modifier.height(Spacing.xxl))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s), horizontalAlignment = Alignment.CenterHorizontally) {
                primaryActionLabel?.let { PrimaryButton(it, onPrimaryAction, Modifier.fillMaxWidth()) }
                secondaryActionLabel?.let { RidgelineTextButton(it, onSecondaryAction) }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun EmptyStatePreview() {
    RidgelineTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            EmptyState(
                icon = Icons.Rounded.DynamicFeed,
                title = "Your feed is quiet",
                body = "Follow people or record your first activity to see it here.",
                primaryActionLabel = "Find people",
                secondaryActionLabel = "Record an activity",
            )
        }
    }
}
