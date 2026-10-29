// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** Spacing scale (4 dp base) and layout constants (`space.*` tokens). */
@Immutable
object Spacing {
    val none = 0.dp
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val x3 = 32.dp
    val x4 = 40.dp
    val x5 = 48.dp
    val x6 = 64.dp

    /** Horizontal screen gutter below 600 dp width. */
    val gutter = 16.dp

    /** Horizontal screen gutter at 600 dp and wider. */
    val gutterWide = 20.dp
    val cardPadding = 16.dp
    val cardGap = 8.dp
    val statGap = 12.dp
    val listRowMin = 56.dp
    val touchMin = 48.dp

    /** Maximum feed width above 840 dp. */
    val feedMaxWidth = 640.dp
}

/** Elevation levels. Light mode renders soft shadows; dark mode uses M3 tonal elevation. */
object Elevation {
    val level0 = 0.dp
    val level1 = 1.dp
    val level2 = 3.dp
    val level3 = 6.dp
    val level4 = 8.dp
}

/**
 * Motion durations in milliseconds. When [reduced] is true every animation collapses to an
 * instant change, honouring both the system animator scale and the in-app switch.
 */
@Immutable
data class RidgelineMotion(
    val micro: Int = 100,
    val short: Int = 180,
    val medium: Int = 280,
    val long: Int = 420,
    val celebrateMax: Int = 1600,
    val reduced: Boolean = false,
) {
    /** Enter duration for [ms], or 0 under reduced motion. */
    fun enter(ms: Int): Int = if (reduced) 0 else ms

    /** Exit duration: 70 % of the enter duration, or 0 under reduced motion. */
    fun exit(ms: Int): Int = if (reduced) 0 else (ms * EXIT_RATIO).toInt()

    private companion object {
        const val EXIT_RATIO = 0.7f
    }
}

/** Easing curves (`motion.easing.*`). */
object Easing {
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

/** Spring specs (`motion.spring.*`). */
object Springs {
    /** Pop used by the high-five button. */
    fun <T> pop(): SpringSpec<T> = spring(dampingRatio = 0.55f, stiffness = 500f)

    /** Sheet settle. */
    fun <T> sheet(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
}

/** Composition local backing [RidgelineTheme.motion]. */
val LocalRidgelineMotion = staticCompositionLocalOf { RidgelineMotion() }
