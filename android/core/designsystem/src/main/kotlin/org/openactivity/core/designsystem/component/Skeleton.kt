// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.PillShape
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

private const val SHIMMER_PERIOD_MS = 1200
private const val SHIMMER_HIGHLIGHT_ALPHA = 0.45f

/**
 * Sweeping highlight over a skeleton block, every 1.2 s. Static under reduced motion.
 */
fun Modifier.shimmer(): Modifier = composed {
    if (RidgelineTheme.motion.reduced) return@composed this
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = SHIMMER_HIGHLIGHT_ALPHA)
    val progress by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_PERIOD_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress",
    )
    drawWithContent {
        drawContent()
        val x = size.width * progress
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(x - size.width / 2, 0f),
                end = Offset(x + size.width / 2, size.height),
            ),
        )
    }
}

/** A single grey placeholder block. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp = 14.dp,
    shape: Shape = MaterialTheme.shapes.extraSmall,
) {
    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .background(RidgelineTheme.colors.skeleton, shape)
            .shimmer(),
    )
}

/**
 * Skeleton shaped like an activity card (header, title, stat row, map), shown on first load
 * instead of a full-screen spinner.
 */
@Composable
fun ActivityCardSkeleton(modifier: Modifier = Modifier) {
    RidgelineCard(modifier.clearAndSetSemantics { contentDescription = "Loading" }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(AvatarSize.m)
                    .background(RidgelineTheme.colors.skeleton, PillShape)
                    .shimmer(),
            )
            Spacer(Modifier.width(Spacing.m))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBlock(width = 140.dp)
                SkeletonBlock(width = 96.dp, height = 10.dp)
            }
        }
        Spacer(Modifier.height(Spacing.l))
        SkeletonBlock(width = 200.dp, height = 18.dp)
        Spacer(Modifier.height(Spacing.l))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.statGap)) {
            repeat(3) { SkeletonBlock(Modifier.weight(1f), height = 28.dp) }
        }
        Spacer(Modifier.height(Spacing.l))
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f)
                .background(RidgelineTheme.colors.skeleton, MaterialTheme.shapes.small)
                .shimmer(),
        )
    }
}

@PreviewLightDark
@Composable
private fun SkeletonPreview() {
    RidgelineTheme(reduceMotion = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            ActivityCardSkeleton(Modifier.padding(Spacing.l))
        }
    }
}
