// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openactivity.core.designsystem.theme.PillShape
import org.openactivity.core.designsystem.theme.RidgelineTheme

/** What TalkBack reads for [IncludedFreeLabel]: the word alone would not say what is free. */
const val INCLUDED_FREE_DESCRIPTION: String = "Included free, no subscription"

/**
 * A quiet "Free" chip for features Strava keeps behind its subscription (route suggestions, goals, the
 * training log). Shown once per feature, next to its title or setting: an outlined pill in small accent
 * text, never a banner and never a fill that competes with the screen's one primary action.
 */
@Composable
fun IncludedFreeLabel(modifier: Modifier = Modifier) {
    val colors = RidgelineTheme.colors
    Text(
        text = "Free",
        color = colors.accentText,
        style = RidgelineTheme.text.caption.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
        modifier = modifier
            .heightIn(min = FreeLabelHeight)
            .border(1.dp, colors.accent.copy(alpha = BORDER_ALPHA), PillShape)
            .padding(horizontal = 7.dp, vertical = 1.dp)
            .clearAndSetSemantics { contentDescription = INCLUDED_FREE_DESCRIPTION },
    )
}

private val FreeLabelHeight = 18.dp
private const val BORDER_ALPHA = 0.5f
