// Generated from design-system/tokens.json (radius.*).
package app.stravafit.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object Radius {
    val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 28.dp
}

val FitShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs), // tags, small badges
    small = RoundedCornerShape(Radius.s),       // inputs, map thumbnails
    medium = RoundedCornerShape(Radius.m),      // buttons
    large = RoundedCornerShape(Radius.l),       // cards
    extraLarge = RoundedCornerShape(Radius.xl), // dialogs, sheets
)

val PillShape = CircleShape                     // chips, avatars, record button
val SheetTopShape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl)
val SmallButtonShape = RoundedCornerShape(10.dp)
val BarTopShape = RoundedCornerShape(topStart = Radius.xs, topEnd = Radius.xs) // weekly volume columns
