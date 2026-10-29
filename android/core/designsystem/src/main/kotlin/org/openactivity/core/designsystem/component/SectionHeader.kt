// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

/**
 * Section title with an optional trailing text action ("See all"). Exposed as a heading to TalkBack.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (actionLabel != null) RidgelineTextButton(actionLabel, onAction)
    }
}

/** Uppercase overline header for grouped lists such as Settings ("PREFERENCES"). */
@Composable
fun OverlineHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = RidgelineTheme.text.overline,
        color = RidgelineTheme.colors.textMuted,
        modifier = modifier
            .padding(top = Spacing.l, bottom = Spacing.s)
            .semantics { heading() },
    )
}

@PreviewLightDark
@Composable
private fun SectionHeaderPreview() {
    RidgelineTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(horizontal = Spacing.l)) {
                SectionHeader("Your clubs", actionLabel = "See all")
                OverlineHeader("Preferences")
            }
        }
    }
}
