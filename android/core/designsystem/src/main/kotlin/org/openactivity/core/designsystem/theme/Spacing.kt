// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** Spacing scale (4 dp grid: 4 8 12 16 20 24 32) and layout constants (`space.*` tokens). */
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
    // Above the strava scale; kept for existing layouts.
    val x4 = 40.dp
    val x5 = 48.dp
    val x6 = 64.dp

    /** Screen gutter. */
    val gutter = 16.dp

    /** Gutter at 600 dp and wider (not in the strava spec, which is phone-only). */
    val gutterWide = 20.dp

    /** Card horizontal padding. */
    val cardPadding = 16.dp

    /** `gap` band between edge-to-edge feed cards. */
    val cardGap = 8.dp

    /** Space between stat columns in a stat row; no dividers. */
    val statGap = 34.dp

    /** Feed card header to title. */
    val titleGap = 10.dp

    /** Map labels keep this far from the frame side edges. */
    val mapLabelInset = 14.dp
    val listRowMin = 56.dp

    /** Minimum touch target. */
    val touchMin = 44.dp

    /** Maximum feed width on large screens. */
    val feedMaxWidth = 640.dp
}

/** Fixed component sizes (`size.*` tokens). */
@Immutable
object Sizes {
    val statusBar = 30.dp
    val topAppBar = 52.dp
    /** Plus [gestureArea] below it. */
    val bottomNav = 60.dp
    val gestureArea = 20.dp
    val actionRow = 48.dp
    val splitsRow = 25.dp
    val tabRow = 46.dp
    val tabIndicator = 3.dp
    val icon = 24.dp
    /** High-five, comment and share in the action row, drawn at 1.6 stroke. */
    val iconAction = 22.dp
    val avatarFeed = 40.dp
    /** Facepile: overlap −7 dp with a 2 dp ring in the surface colour. */
    val avatarFacepile = 24.dp
    val facepileOverlap = 7.dp
    /** Feed carousel and activity map hero. */
    val feedMedia = 236.dp
    val pageDot = 6.dp
    val notificationDot = 9.dp
    val chip = 34.dp
    val selectorPill = 40.dp
    val floatButton = 44.dp
    val sportCircle = 48.dp
    val recordSecondary = 56.dp
    /** Start, Resume, Finish. */
    val recordStart = 96.dp
    val recordStop = 84.dp
    val pausedStrip = 34.dp
    val routeThumbWidth = 252.dp
    val routeThumbHeight = 88.dp
    val hairline = 1.dp
    val activeRing = 2.dp
}

/**
 * Elevation. Surfaces are flat; only things floating over a map get a shadow
 * (light: 0 1 2 / .16 + 0 3 10 / .10; dark: heavier black). No tonal tint.
 */
object Elevation {
    val level0 = 0.dp
    /** Cards: flat. */
    val level1 = 0.dp
    /** Start / Stop / Finish: a 1 dp neutral shadow, never an orange glow. */
    val level2 = 1.dp
    /** Chips, round buttons, search pill over maps. */
    val level3 = 3.dp
    /** Sheets and the record panel over a map. */
    val level4 = 6.dp

    val flat = level0
    val recordButton = level2
    val float = level3
    val sheet = level4
}

/**
 * Motion durations in milliseconds. The strava mockups are static and define no motion; these are
 * carried over unchanged. When [reduced] is true every animation collapses to an instant change.
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
