// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.RidgelineTheme

/**
 * Our own ribbon medal for a best-effort place: a short accent ribbon over a disc holding the [rank].
 * Gold, silver and bronze for 1 to 3. An all-time place is a solid disc; a place within the year only
 * ([yearly]) is an outlined ring, so the two never rely on colour alone (activity-detail 6.2).
 *
 * @param description spoken instead of the number, e.g. "Personal record".
 */
@Composable
fun RankMedal(rank: Int, yearly: Boolean = false, size: Dp = 22.dp, description: String? = null, modifier: Modifier = Modifier) {
    val color = medalColor(rank)
    val semantics = if (description != null) Modifier.clearAndSetSemantics { contentDescription = description } else Modifier
    Column(modifier.then(semantics), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(width = size * RIBBON_WIDTH, height = size * RIBBON_HEIGHT).background(RidgelineTheme.colors.accent))
        val disc = if (yearly) {
            Modifier.size(size).border(BORDER, color, CircleShape)
        } else {
            Modifier.size(size).background(color, CircleShape)
        }
        Box(disc, contentAlignment = Alignment.Center) {
            Text(
                "$rank",
                style = RidgelineTheme.text.caption,
                color = if (yearly) RidgelineTheme.colors.textSecondary else RidgelineTheme.colors.onMedal,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Gold, silver or bronze for places 1, 2 and 3. */
@Composable
fun medalColor(rank: Int): Color = when (rank) {
    1 -> RidgelineTheme.colors.medalGold
    2 -> RidgelineTheme.colors.medalSilver
    else -> RidgelineTheme.colors.medalBronze
}

private const val RIBBON_WIDTH = 0.45f
private const val RIBBON_HEIGHT = 0.27f
private val BORDER = 2.dp

@PreviewLightDark
@Composable
private fun RankMedalPreview() {
    RidgelineTheme {
        Surface {
            Row {
                RankMedal(1)
                RankMedal(2)
                RankMedal(3)
                RankMedal(1, yearly = true)
                RankMedal(2, yearly = true)
            }
        }
    }
}
