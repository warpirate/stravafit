// Generated from design-system/tokens.json (space.*, elevation.*, motion.*).
package app.stravafit.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

@Immutable
object Spacing {
    val none = 0.dp; val xxs = 2.dp; val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp
    val xl = 20.dp; val xxl = 24.dp; val x3 = 32.dp; val x4 = 40.dp; val x5 = 48.dp; val x6 = 64.dp

    val gutter = 16.dp          // < 600dp width
    val gutterWide = 20.dp      // >= 600dp width
    val cardPadding = 16.dp
    val cardGap = 8.dp
    val statGap = 12.dp
    val listRowMin = 56.dp
    val touchMin = 48.dp
    val feedMaxWidth = 640.dp   // > 840dp width
}

/** M3 tonal elevation in dark mode; soft shadows in light. */
object Elevation {
    val level0 = 0.dp; val level1 = 1.dp; val level2 = 3.dp; val level3 = 6.dp; val level4 = 8.dp
}

@Immutable
data class FitMotion(
    val micro: Int = 100,
    val short: Int = 180,
    val medium: Int = 280,
    val long: Int = 420,
    val celebrateMax: Int = 1600,
    val reduced: Boolean = false,
) {
    /** Exit = 70% of enter, accelerate curve. Reduced motion: 0ms (or a 100ms fade at call site). */
    fun enter(ms: Int) = if (reduced) 0 else ms
    fun exit(ms: Int) = if (reduced) 0 else (ms * 0.7f).toInt()
}

object Easing {
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
}

object Springs {
    fun <T> pop() = spring<T>(dampingRatio = 0.55f, stiffness = 500f)   // high-five
    fun <T> sheet() = spring<T>(dampingRatio = 0.8f, stiffness = 380f)
}

val LocalFitMotion = staticCompositionLocalOf { FitMotion() }
