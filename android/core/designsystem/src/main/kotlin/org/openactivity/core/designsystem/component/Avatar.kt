// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openactivity.core.designsystem.theme.PillShape
import org.openactivity.core.designsystem.theme.Ref
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

/** Standard avatar sizes. */
object AvatarSize {
    val xs = 24.dp
    val s = 32.dp
    val m = 40.dp
    val l = 56.dp
    val xl = 96.dp
}

/**
 * Eight light tints for initials avatars. Each has at least 4.5:1 contrast with [Ref.Slate900]
 * text, in both themes.
 */
private val AvatarTints = listOf(
    Color(0xFFC2E3E6), // teal
    Color(0xFFDCEAB8), // lichen
    Color(0xFFE4DDF5), // violet
    Color(0xFFF5D9E1), // wine
    Color(0xFFD5E5F4), // blue
    Color(0xFFF2E2C4), // amber
    Color(0xFFD3EADB), // green
    Color(0xFFE3E8EA), // slate
)

/** Deterministic tint index for [name], stable across runs and devices. */
fun avatarTintIndex(name: String): Int {
    // FNV-1a spreads short names across tints better than String.hashCode.
    var hash = FNV_OFFSET
    name.forEach { hash = (hash xor it.code) * FNV_PRIME }
    return Math.floorMod(hash, AvatarTints.size)
}

private const val FNV_OFFSET = -2128831035 // 0x811C9DC5
private const val FNV_PRIME = 16777619

/** Up to two initials from [name], e.g. "Ana Ruiz" -> "AR". */
fun initialsOf(name: String): String =
    name.split(' ', '-').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

/**
 * Initials avatar on a deterministic tint. Photo avatars arrive with the image pipeline (Coil).
 */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = AvatarSize.m) {
    Box(
        modifier = modifier
            .size(size)
            .background(AvatarTints[avatarTintIndex(name)], PillShape)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialsOf(name).let { if (size < AvatarSize.s) it.take(1) else it },
            color = Ref.Slate900,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * INITIALS_SCALE).sp,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

private const val INITIALS_SCALE = 0.38f
private val FacepileOverlap = 8.dp
private val FacepileRing = 2.dp

/**
 * Up to three overlapping 24 dp avatars with a surface ring, then a "+N" pill for the rest.
 */
@Composable
fun Facepile(names: List<String>, modifier: Modifier = Modifier, totalCount: Int = names.size, max: Int = 3) {
    val shown = names.take(max)
    val extra = totalCount - shown.size
    val ringColor = MaterialTheme.colorScheme.surface
    Row(modifier.clearAndSetSemantics { contentDescription = "$totalCount people" }) {
        shown.forEachIndexed { index, name ->
            Avatar(
                name = name,
                size = AvatarSize.xs + FacepileRing * 2,
                modifier = Modifier
                    .offset(x = -FacepileOverlap * index)
                    .border(FacepileRing, ringColor, PillShape),
            )
        }
        if (extra > 0) {
            Box(
                Modifier
                    .offset(x = -FacepileOverlap * shown.size)
                    .border(FacepileRing, ringColor, PillShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer, PillShape)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text("+$extra", style = RidgelineTheme.text.caption, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun AvatarPreview() {
    RidgelineTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                    Avatar("Ana Ruiz", size = AvatarSize.xs)
                    Avatar("Dev Kulkarni", size = AvatarSize.s)
                    Avatar("Li Wei", size = AvatarSize.m)
                    Avatar("Sam Okafor", size = AvatarSize.l)
                    Avatar("Mira Holm", size = AvatarSize.xl)
                }
                Facepile(listOf("Ana Ruiz", "Dev Kulkarni", "Li Wei", "Sam Okafor"), totalCount = 12)
            }
        }
    }
}
