// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.openactivity.app.format.UnitFormatter
import org.openactivity.app.ui.common.icon
import org.openactivity.core.data.model.GoalProgress
import org.openactivity.core.data.model.WeeklyStreak
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.model.SportType

/**
 * One goal as the cards show it.
 *
 * @property subtitle "Distance · this week".
 * @property ofTarget "24.1 / 30 km".
 * @property fraction bar fill, 0..1.
 * @property status "5.9 km to go" or "Goal reached".
 * @property pace "On pace" / "Behind pace", null once reached.
 */
data class GoalItemUi(
    val id: Long,
    val title: String,
    val subtitle: String,
    val sport: SportType?,
    val ofTarget: String,
    val fraction: Float,
    val percent: Int,
    val status: String,
    val daysLeft: String,
    val pace: String?,
    val onPace: Boolean,
    val reached: Boolean,
) {
    companion object {
        fun from(progress: GoalProgress, formatter: UnitFormatter): GoalItemUi {
            val goal = progress.goal
            return GoalItemUi(
                id = goal.id,
                title = GoalFormat.title(goal),
                subtitle = "${GoalFormat.metricLabel(goal.metric)} · ${GoalFormat.periodPhrase(goal.period)}",
                sport = goal.sport,
                ofTarget = GoalFormat.ofTarget(progress, formatter),
                fraction = progress.fraction.coerceIn(0.0, 1.0).toFloat(),
                percent = GoalFormat.percent(progress),
                status = GoalFormat.status(progress, formatter),
                daysLeft = GoalFormat.daysLeft(progress),
                pace = if (progress.reached) null else GoalFormat.pace(progress),
                onPace = progress.onPace,
                reached = progress.reached,
            )
        }
    }
}

/**
 * The weekly streak line.
 *
 * @property title "3 week streak", or "No streak yet".
 * @property subtitle whether this week already counts.
 */
data class StreakUi(val weeks: Int, val activeThisWeek: Boolean, val title: String, val subtitle: String) {
    companion object {
        fun from(streak: WeeklyStreak): StreakUi = StreakUi(
            weeks = streak.weeks,
            activeThisWeek = streak.activeThisWeek,
            title = if (streak.weeks == 0) "No streak yet" else "${streak.weeks} week streak",
            subtitle = when {
                streak.activeThisWeek -> "Active this week"
                streak.weeks == 0 -> "Record an activity this week to start one"
                else -> "Not active this week yet. Record an activity to keep it"
            },
        )
    }
}

/** A goal: title, orange progress bar, "24.1 / 30 km", status, days left and pace. Tap to edit. */
@Composable
internal fun GoalCardContent(goal: GoalItemUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Edit goal", role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.gutter, vertical = Spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                goal.sport?.icon ?: Icons.Rounded.Flag,
                contentDescription = null,
                tint = RidgelineTheme.colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(Spacing.s))
            Column(Modifier.weight(1f)) {
                Text(goal.title, style = MaterialTheme.typography.titleSmall)
                Text(goal.subtitle, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textMuted)
            }
            if (goal.reached) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = RidgelineTheme.colors.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(goal.ofTarget, style = RidgelineTheme.text.statS)
        }
        Spacer(Modifier.height(Spacing.s))
        GoalBar(goal.fraction, Modifier.semantics { contentDescription = "${goal.percent}% of goal" })
        Spacer(Modifier.height(Spacing.s))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(goal.status, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary, modifier = Modifier.weight(1f))
            Text(
                listOfNotNull(goal.pace, goal.daysLeft).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (goal.pace != null && !goal.onPace) RidgelineTheme.colors.warning else RidgelineTheme.colors.textMuted,
            )
        }
    }
}

/** Accent-orange progress bar on a sunken track. */
@Composable
internal fun GoalBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(RoundedCornerShape(BAR_HEIGHT / 2))
            .background(RidgelineTheme.colors.surfaceSunken),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(BAR_HEIGHT)
                    .clip(RoundedCornerShape(BAR_HEIGHT / 2))
                    .background(RidgelineTheme.colors.accent),
            )
        }
    }
}

/** The weekly streak: a flame disc, "3 week streak" and whether this week counts yet. */
@Composable
internal fun StreakRow(streak: StreakUi, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "${streak.title}. ${streak.subtitle}" }
            .padding(horizontal = Spacing.gutter, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        val active = streak.weeks > 0
        Box(
            Modifier
                .size(36.dp)
                .background(if (active) RidgelineTheme.colors.accent else RidgelineTheme.colors.surfaceSunken, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.LocalFireDepartment,
                contentDescription = null,
                tint = if (active) RidgelineTheme.colors.onAccent else RidgelineTheme.colors.textMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(streak.title, style = MaterialTheme.typography.titleSmall)
            Text(streak.subtitle, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary)
        }
    }
}

private val BAR_HEIGHT = 8.dp
