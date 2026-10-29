// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Corner radii (`radius.*` tokens). */
object Radius {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 28.dp
}

/** Material 3 shape slots mapped to Ridgeline radii. */
val RidgelineShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs), // tags, small badges
    small = RoundedCornerShape(Radius.s), // inputs, map thumbnails
    medium = RoundedCornerShape(Radius.m), // buttons
    large = RoundedCornerShape(Radius.l), // cards
    extraLarge = RoundedCornerShape(Radius.xl), // dialogs, sheets
)

/** Chips, avatars and the record button. */
val PillShape: Shape = CircleShape

/** Top corners of modal sheets. */
val SheetTopShape: Shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl)

/** Small (36 dp) buttons inside cards. */
val SmallButtonShape: Shape = RoundedCornerShape(10.dp)

/** Rounded top of weekly volume bars. */
val BarTopShape: Shape = RoundedCornerShape(topStart = Radius.xs, topEnd = Radius.xs)
