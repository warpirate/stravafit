// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.feed

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Springs

private const val POP_SCALE = 1.2f

/**
 * High-five toggle (design-system 4.7). Outline in secondary text when off, filled primary when
 * on. A light haptic fires on tap, and a quick spring pop (under 300 ms, skipped under reduced
 * motion) confirms the change without blocking the next tap. Tapping again removes it.
 */
@Composable
fun RowScope.HighFiveButton(given: Boolean, count: Int, onToggle: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    val reduced = RidgelineTheme.motion.reduced
    LaunchedEffect(given) {
        if (given && !reduced && scale.value == 1f) {
            scale.animateTo(POP_SCALE, Springs.pop())
            scale.animateTo(1f, Springs.pop())
        }
    }
    val tint = if (given) MaterialTheme.colorScheme.primary else RidgelineTheme.colors.textSecondary
    val description = (if (given) "Remove high-five" else "Give high-five") + ", $count high-fives"
    val toggle = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onToggle()
    }
    ActionBarButton(
        onClick = toggle,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = description
            role = Role.Button
            onClick { toggle(); true }
        },
    ) {
        Icon(
            if (given) Icons.Rounded.PanTool else Icons.Outlined.PanTool,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(20.dp)
                .scale(scale.value),
        )
        Text(if (given) "High-fived" else "High-five", style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

/** One equal-width slot of the card action bar. */
@Composable
fun RowScope.ActionBarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .weight(1f)
            .heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}
