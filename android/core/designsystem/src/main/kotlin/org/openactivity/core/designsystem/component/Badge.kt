// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openactivity.core.designsystem.theme.PillShape
import org.openactivity.core.designsystem.theme.RidgelineColors
import org.openactivity.core.designsystem.theme.RidgelineTheme

/** What a [Badge] marks; each tone has a fixed fill and text colour pair with enough contrast. */
enum class BadgeTone {
    /** Something new: an unseen feed item. Accent text on the light accent tint (small orange text rule). */
    Accent,

    /** Neutral information, e.g. "Private". Sunken fill. */
    Neutral,

    /** First place, a personal record. */
    Gold,

    /** Second best effort. */
    Silver,

    /** Third best effort. */
    Bronze,
}

/** Fill and content colour of [tone]. */
@Immutable
data class BadgeColors(val container: Color, val content: Color)

/** The colours a [tone] badge draws with, from [colors]. */
fun badgeColors(tone: BadgeTone, colors: RidgelineColors): BadgeColors = when (tone) {
    BadgeTone.Accent -> BadgeColors(colors.accentArea, colors.accentOnArea)
    BadgeTone.Neutral -> BadgeColors(colors.surfaceSunken, colors.textSecondary)
    BadgeTone.Gold -> BadgeColors(colors.medalGold, colors.onMedal)
    BadgeTone.Silver -> BadgeColors(colors.medalSilver, colors.onMedal)
    BadgeTone.Bronze -> BadgeColors(colors.medalBronze, colors.onMedal)
}

/**
 * A small pill label: "New" on unseen feed items, "PR" or a medal rank on achievements. Short text
 * only (one or two words); the text itself carries the meaning, never the colour alone.
 */
@Composable
fun Badge(
    text: String,
    modifier: Modifier = Modifier,
    tone: BadgeTone = BadgeTone.Accent,
    icon: ImageVector? = null,
) {
    val colors = badgeColors(tone, RidgelineTheme.colors)
    Row(
        modifier
            .heightIn(min = BadgeHeight)
            .background(colors.container, PillShape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = colors.content, modifier = Modifier.size(12.dp))
        Text(
            text,
            color = colors.content,
            style = RidgelineTheme.text.caption.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

private val BadgeHeight = 18.dp
