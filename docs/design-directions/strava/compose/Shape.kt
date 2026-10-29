// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Corner radii (`radius.*` tokens). */
object Radius {
    /** Cards, maps and photos in the feed: square, edge to edge. */
    val none = 0.dp
    /** Buttons and inputs. */
    val xs = 4.dp
    /** Route-card thumbnails. */
    val s = 8.dp
    /** Kept for source compatibility; the strava scale has no 12, so it resolves to 8. */
    val m = 8.dp
    /** Top of sheets and record panels. */
    val l = 16.dp
    /** Kept for source compatibility; sheets and dialogs use 16. */
    val xl = 16.dp
}

/**
 * Material 3 shape slots.
 * Card reads `medium`, so feed cards come out square; bottom sheets and dialogs read `extraLarge`.
 */
val RidgelineShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs), // menus, tooltips, text fields
    small = RoundedCornerShape(Radius.xs), // buttons, inputs
    medium = RoundedCornerShape(Radius.none), // cards (edge-to-edge feed): square
    large = RoundedCornerShape(Radius.s), // route thumbnails, large FAB
    extraLarge = RoundedCornerShape(Radius.l), // sheets, dialogs, record panel
)

/** Avatars, map chips, search pill, round buttons and the record button. */
val PillShape: Shape = CircleShape

/** Top corners of bottom sheets and the record panel (16). */
val SheetTopShape: Shape = RoundedCornerShape(topStart = Radius.l, topEnd = Radius.l)

/** Rectangular buttons and inputs (4). */
val SmallButtonShape: Shape = RoundedCornerShape(Radius.xs)

/** Feed cards, maps, photos. */
val CardShape: Shape = RectangleShape

/** Route-card map thumbnails (8). */
val ThumbnailShape: Shape = RoundedCornerShape(Radius.s)

/** Bar ends in charts (split bars, weekly bars). */
val BarTopShape: Shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
