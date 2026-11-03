// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutQuint
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.launch

/**
 * Bottom navigation (strava direction): Home · Maps · Record · Groups · You, five equal slots with a 24 dp
 * icon over an 11 dp label, in a capsule floating above the screen edge. The active tab shows a filled orange glyph and an orange label over a faint
 * orange pill. Record is an outlined record-circle glyph like the other tabs. In landscape the bar is
 * compact (icon beside nothing but its label, 48 dp) so it does not eat a quarter of the height.
 *
 * Motion follows Telegram's tab bar: the pill springs between tabs, icons dip on press, and a long press
 * lifts the bar slightly toward the finger so the pill can be dragged across tabs with a tick on each one;
 * letting go opens the tab under the finger. Dragging skips Record so a scrub never starts a recording.
 */
@Composable
fun AppBottomBar(
    selected: TopLevelDestination?,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val landscape = LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
    val tabs = TopLevelDestination.entries
    val selectedIndex = selected?.takeUnless { it.isAction }?.ordinal
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)

    // Pill centre in slot units (0 = first slot), so it needs no pixel widths to follow the selection.
    val pill = remember { Animatable(selectedIndex?.toFloat() ?: 0f) }
    var scrubIndex by remember { mutableStateOf<Int?>(null) }
    var lifted by remember { mutableStateOf(false) }
    var origin by remember { mutableStateOf(TransformOrigin.Center) }
    val lift by animateFloatAsState(if (lifted) LIFT_SCALE else 1f, tween(LIFT_MILLIS, easing = EaseOutQuint), label = "lift")
    val surface = MaterialTheme.colorScheme.surface
    val dark = surface.luminance() < 0.5f
    // Orange at low alpha turns brown on near-black, so the dark pill is a neutral lift like Telegram's.
    val pillColor = if (dark) Color.White.copy(alpha = PILL_ALPHA_DARK) else RidgelineTheme.colors.accent.copy(alpha = PILL_ALPHA)
    val frosted = hazeState?.let { state ->
        Modifier.hazeEffect(
            state,
            HazeStyle(
                backgroundColor = surface,
                tint = HazeTint(surface.copy(alpha = FROST_ALPHA)),
                blurRadius = FROST_BLUR,
                noiseFactor = 0f,
                fallbackTint = HazeTint(surface.copy(alpha = FROST_FALLBACK_ALPHA)),
            ),
        )
    } ?: Modifier

    LaunchedEffect(selectedIndex) {
        if (scrubIndex == null && selectedIndex != null) pill.animateTo(selectedIndex.toFloat(), PILL_SPRING)
    }

    // Floating capsule: inset from the screen edges, with the content scrolling behind it frosted (blurred
    // and tinted) on Android 12+, and a plain translucent fill below that. A hairline edge outlines it.
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = FLOAT_MARGIN, end = FLOAT_MARGIN, bottom = FLOAT_BOTTOM),
    ) {
        Surface(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = lift
                    scaleY = lift
                    transformOrigin = origin
                }
                .clip(CircleShape)
                .then(frosted),
            shape = CircleShape,
            color = if (hazeState == null) surface else Color.Transparent,
            border = BorderStroke(Dp.Hairline, RidgelineTheme.colors.hairline),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(if (landscape) COMPACT_HEIGHT else BAR_HEIGHT)
                    .drawBehind {
                        if (selectedIndex == null && scrubIndex == null) return@drawBehind
                        val slot = size.width / tabs.size
                        val inset = PILL_INSET.toPx()
                        val height = size.height - inset * 2
                        drawRoundRect(
                            color = pillColor,
                            topLeft = Offset(slot * pill.value + inset, inset),
                            size = Size(slot - inset * 2, height),
                            cornerRadius = CornerRadius(height / 2),
                        )
                    }
                    .pointerInput(tabs.size) {
                        val slot = { size.width.toFloat() / tabs.size }
                        // Finger position in slot units, clamped to the first and last slot centres.
                        val toSlot = { x: Float -> (x / slot() - 0.5f).coerceIn(0f, tabs.size - 1f) }
                        // Nearest tab under the finger, never the Record action.
                        val hovered = { x: Float -> toSlot(x).roundToInt().takeUnless { tabs[it].isAction } }
                        fun follow(x: Float) {
                            scope.launch { pill.animateTo(toSlot(x), FOLLOW_SPRING) }
                            val over = hovered(x) ?: return
                            if (over != scrubIndex) {
                                if (scrubIndex != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scrubIndex = over
                            }
                        }
                        fun settle(open: Boolean) {
                            val target = scrubIndex.takeIf { open } ?: currentSelectedIndex
                            scrubIndex = null
                            lifted = false
                            if (target != null) {
                                scope.launch { pill.animateTo(target.toFloat(), PILL_SPRING) }
                                if (target != currentSelectedIndex) currentOnSelect(tabs[target])
                            }
                        }
                        detectDragGesturesAfterLongPress(
                            onDragStart = { start ->
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                origin = liftOrigin(start, size.width.toFloat(), size.height.toFloat())
                                lifted = true
                                scrubIndex = hovered(start.x) ?: currentSelectedIndex
                                follow(start.x)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                origin = liftOrigin(change.position, size.width.toFloat(), size.height.toFloat())
                                follow(change.position.x)
                            },
                            onDragEnd = { settle(open = true) },
                            onDragCancel = { settle(open = false) },
                        )
                    },
            ) {
                tabs.forEach { destination ->
                    val isSelected = destination.ordinal == (scrubIndex ?: selectedIndex)
                    NavSlot(destination, isSelected, compact = landscape, onClick = { onSelect(destination) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Telegram's lift pivot: the touch offset from the centre is pushed outward (r → 1.5r / (r + 0.5)), so a
 * press near an edge grows the bar away from that edge, and vertically it is exaggerated threefold.
 */
private fun liftOrigin(touch: Offset, width: Float, height: Float): TransformOrigin {
    if (width <= 0f || height <= 0f) return TransformOrigin.Center
    val nx = (touch.x - width / 2) / (width / 2)
    val ny = (touch.y - height / 2) / (height / 2)
    val r = sqrt(nx * nx + ny * ny)
    val push = if (r > 1e-4f) 1.5f / (r + 0.5f) else 0f
    return TransformOrigin(0.5f + nx * push / 2, 0.5f + ny * push * 3 / 2)
}

@Composable
private fun NavSlot(
    destination: TopLevelDestination,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val tint by animateColorAsState(if (selected) RidgelineTheme.colors.accent else RidgelineTheme.colors.textMuted, label = "tint")
    val labelColor by animateColorAsState(
        if (selected) RidgelineTheme.colors.accentText else RidgelineTheme.colors.textMuted,
        label = "label",
    )
    val icon = if (selected) destination.selectedIcon else destination.unselectedIcon
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) PRESS_SCALE else 1f, PRESS_SPRING, label = "press")
    val content: @Composable () -> Unit = {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(ICON_SIZE).scale(press))
        if (compact) Spacer(Modifier.width(Spacing.s)) else Spacer(Modifier.height(2.dp))
        Text(destination.label, style = RidgelineTheme.text.nav, color = labelColor, maxLines = 1)
    }
    // The pill and the press dip replace the ripple, as in Telegram.
    val base = modifier
        .fillMaxHeight()
        .selectable(selected = selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
        .semantics { contentDescription = destination.label }
    if (compact) {
        Row(base, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { content() }
    } else {
        Column(base, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { content() }
    }
}

/**
 * "● Recording · 4:38" card floating above the bottom navigation while a recording runs in the background
 * (after Back or minimise), so the athlete always sees it and can get back to it with one tap.
 */
@Composable
fun ActiveRecordingBar(time: String, paused: Boolean, sport: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (paused) "Paused" else "Recording"
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(start = FLOAT_MARGIN, end = FLOAT_MARGIN, bottom = Spacing.s),
        shape = RoundedCornerShape(FLOAT_CARD_RADIUS),
        shadowElevation = FLOAT_ELEVATION,
        color = if (paused) RidgelineTheme.colors.paused else RidgelineTheme.colors.accent,
        contentColor = if (paused) RidgelineTheme.colors.onPaused else RidgelineTheme.colors.onAccent,
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(
                        if (paused) RidgelineTheme.colors.pausedDot else RidgelineTheme.colors.onAccent,
                        CircleShape,
                    ),
            )
            Spacer(Modifier.width(Spacing.s))
            Text("$label · $sport", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(time, style = RidgelineTheme.text.statS)
            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
        }
    }
}

/**
 * Bottom inset of the tab screens: the floating bar (and the recording card above it) covers this much of
 * the screen bottom. Tab screens draw behind the bar and add this to their scroll padding so the last item
 * can scroll clear of it.
 */
val LocalBottomBarPadding = compositionLocalOf { 0.dp }

private val BAR_HEIGHT = 64.dp
private val COMPACT_HEIGHT = 48.dp
private val ICON_SIZE = 26.dp
private val PILL_INSET = 5.dp
private val FLOAT_MARGIN = 16.dp
private val FLOAT_BOTTOM = 12.dp
private val FLOAT_ELEVATION = 8.dp
private val FLOAT_CARD_RADIUS = 20.dp
private const val PILL_ALPHA = 0.12f
private const val PILL_ALPHA_DARK = 0.10f

// Telegram tints its blurred bars at 85 % (76 % without liquid glass); 80 % keeps the labels crisp while
// the content behind still shows through.
private val FROST_BLUR = 20.dp
private const val FROST_ALPHA = 0.80f
private const val FROST_FALLBACK_ALPHA = 0.94f

// Telegram's values: SpringForce STIFFNESS_MEDIUM / DAMPING_RATIO_LOW_BOUNCY for the pill, a 1.9 % lift
// over 380 ms ease-out-quint while scrubbing.
private val PILL_SPRING = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)
private val FOLLOW_SPRING = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
private val PRESS_SPRING = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
private const val LIFT_SCALE = 1.019f
private const val LIFT_MILLIS = 380
private const val PRESS_SCALE = 0.86f
