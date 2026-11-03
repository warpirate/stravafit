// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.segments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openactivity.app.format.UnitFormatter
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.map.compose.RidgelineMap
import org.openactivity.core.map.compose.rememberMapState
import org.openactivity.core.map.overlay.ActivityRouteOverlay
import org.openactivity.core.map.overlay.MapOverlays
import org.openactivity.core.map.thumbnail.RouteMapThumbnail
import org.openactivity.model.LatLng
import java.util.Locale
import kotlin.math.roundToLong

/** Formatting shared by the segment screens. Pure. */
object SegmentFormat {
    /** "0:42", "4:05", "1:02:03". */
    fun clock(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, sec) else "%d:%02d".format(Locale.ROOT, m, sec)
    }

    /** Seconds rounded the way the times are shown. */
    fun seconds(millis: Long): Long = (millis / 1000.0).roundToLong()

    /** "4.1%", "-0.3%". */
    fun grade(percent: Double): String = String.format(Locale.getDefault(), "%.1f%%", percent)

    /** "0.52 km · 4.1%". */
    fun lengthAndGrade(meters: Double, gradePercent: Double, formatter: UnitFormatter): String {
        val d = formatter.distance(meters)
        return "${d.value} ${d.unit} · ${grade(gradePercent)}"
    }

    /** "PR", "2nd best", "Year's best"... for a medal place, or null. */
    fun medalLabel(rank: Int?, yearly: Boolean): String? = when {
        rank == null -> null
        !yearly && rank == 1 -> "PR"
        !yearly -> if (rank == 2) "2nd best" else "3rd best"
        rank == 1 -> "Year's best"
        else -> if (rank == 2) "2nd this year" else "3rd this year"
    }
}

/**
 * An elevation profile over distance, as fractions of the box: [xs] from 0 (start) to 1 (finish) and
 * [ys] from 0 (lowest) to 1 (highest). Without elevations [ys] is empty and only the distance axis exists.
 */
@Immutable
data class ProfileUi(
    val xs: List<Float>,
    val ys: List<Float>,
    val minLabel: String?,
    val maxLabel: String?,
) {
    val hasElevation: Boolean get() = ys.size >= 2 && ys.size == xs.size

    companion object {
        /** Up to [maxPoints] samples of [elevations] over [distances] (same size; nulls are gaps). */
        fun of(distances: List<Double>, elevations: List<Double?>, formatter: UnitFormatter, maxPoints: Int = 300): ProfileUi {
            val pairs = distances.indices.mapNotNull { i -> elevations.getOrNull(i)?.let { distances[i] to it } }
            val total = (distances.lastOrNull() ?: 0.0) - (distances.firstOrNull() ?: 0.0)
            if (pairs.size < 2 || total <= 0.0) return ProfileUi(emptyList(), emptyList(), null, null)
            val step = maxOf(1, pairs.size / maxPoints)
            val sampled = pairs.filterIndexed { i, _ -> i % step == 0 || i == pairs.lastIndex }
            val lo = sampled.minOf { it.second }
            val hi = sampled.maxOf { it.second }
            val span = (hi - lo).coerceAtLeast(MIN_SPAN_METERS)
            val start = distances.first()
            val low = formatter.elevation(lo)
            val high = formatter.elevation(hi)
            return ProfileUi(
                xs = sampled.map { ((it.first - start) / total).toFloat().coerceIn(0f, 1f) },
                ys = sampled.map { ((it.second - lo) / span).toFloat() },
                minLabel = "${low.value} ${low.unit}",
                maxLabel = "${high.value} ${high.unit}",
            )
        }

        /** Flat profiles are drawn as if they spanned this much, so GPS noise does not look like hills. */
        private const val MIN_SPAN_METERS = 10.0
    }
}

/**
 * The filled elevation profile. With [range] (fractions of the distance) the part outside it is muted and
 * the range is drawn in the accent colour; TalkBack reads [description].
 */
@Composable
fun SegmentProfile(profile: ProfileUi, description: String, modifier: Modifier = Modifier, range: ClosedFloatingPointRange<Float>? = null, height: Dp = 96.dp) {
    val accent = RidgelineTheme.colors.accent
    val fill = MaterialTheme.colorScheme.primaryContainer
    val muted = RidgelineTheme.colors.hairline
    val mutedLine = RidgelineTheme.colors.textMuted
    Column(modifier.clearAndSetSemantics { contentDescription = description }) {
        if (profile.minLabel != null && profile.maxLabel != null) {
            Row(Modifier.fillMaxWidth()) {
                Text("Elevation", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("Low ${profile.minLabel} · High ${profile.maxLabel}", style = RidgelineTheme.text.caption, color = RidgelineTheme.colors.textMuted)
            }
            Spacer(Modifier.height(Spacing.xs))
        }
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val inset = 2.dp.toPx()
            val h = size.height - inset * 2
            if (!profile.hasElevation) {
                // No elevation: a distance bar, with the range highlighted.
                val y = size.height / 2
                drawLine(muted, Offset(0f, y), Offset(size.width, y), strokeWidth = 6.dp.toPx())
                range?.let { drawLine(accent, Offset(it.start * size.width, y), Offset(it.endInclusive * size.width, y), strokeWidth = 6.dp.toPx()) }
                return@Canvas
            }
            fun at(i: Int) = Offset(profile.xs[i] * size.width, inset + (1f - profile.ys[i]) * h)
            val outline = Path().apply {
                moveTo(at(0).x, at(0).y)
                for (i in 1 until profile.xs.size) lineTo(at(i).x, at(i).y)
            }
            val area = Path().apply {
                addPath(outline)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            if (range == null) {
                drawPath(area, fill)
                drawPath(outline, accent, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
            } else {
                drawPath(area, muted)
                drawPath(outline, mutedLine, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
                val left = range.start * size.width
                val right = range.endInclusive * size.width
                clipRect(left = left, right = right) {
                    drawPath(area, fill)
                    drawPath(outline, accent, style = Stroke(2.5.dp.toPx(), join = StrokeJoin.Round))
                }
                drawLine(accent, Offset(left, 0f), Offset(left, size.height), strokeWidth = 2.dp.toPx())
                drawLine(accent, Offset(right, 0f), Offset(right, size.height), strokeWidth = 2.dp.toPx())
                drawRect(accent.copy(alpha = 0.08f), topLeft = Offset(left, 0f), size = Size(right - left, size.height))
            }
        }
    }
}

/**
 * A live map with [highlight] in the route orange over [context] drawn muted (the rest of the activity),
 * framed to the highlight. Previews and screenshot tests draw the offline route picture instead.
 */
@Composable
fun SegmentMap(
    highlight: List<LatLng>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    context: List<LatLng> = emptyList(),
    aspectRatio: Float = 16f / 10f,
    shape: Shape = MaterialTheme.shapes.large,
) {
    val shaped = modifier.fillMaxWidth().clip(shape)
    if (LocalInspectionMode.current) {
        BoxWithConstraints(shaped.aspectRatio(aspectRatio)) {
            RouteMapThumbnail(highlight.takeIf { it.size >= 2 } ?: context, contentDescription, aspectRatio = aspectRatio, loader = null, shape = shape)
        }
        return
    }
    val state = rememberMapState()
    val overlays = remember(highlight, context) {
        MapOverlays(
            activityRoute = if (highlight.size >= 2) ActivityRouteOverlay(highlight, colorByPace = false) else null,
            alternateRoutes = if (context.size >= 2) listOf(context) else emptyList(),
        )
    }
    LaunchedEffect(highlight) { if (highlight.size >= 2) state.fitTo(highlight, animate = context.isNotEmpty()) }
    RidgelineMap(
        modifier = shaped.aspectRatio(aspectRatio).clearAndSetSemantics { this.contentDescription = contentDescription },
        state = state,
        overlays = overlays,
        textureMode = true,
    )
}
