// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.openactivity.core.designsystem.component.BadgeTone
import org.openactivity.core.designsystem.component.Stat
import org.openactivity.core.designsystem.component.badgeColors
import org.openactivity.core.designsystem.theme.DarkRidgelineColors
import org.openactivity.core.designsystem.theme.LightRidgelineColors
import org.openactivity.core.designsystem.component.avatarTintIndex
import org.openactivity.core.designsystem.component.initialsOf
import org.openactivity.core.designsystem.theme.RidgelineMotion

class ComponentLogicTest {

    @Test
    fun `badge text is readable on its fill in light and dark`() {
        for ((colors, surface) in listOf(LightRidgelineColors to Color.White, DarkRidgelineColors to Color(0xFF1C1C1F))) {
            for (tone in BadgeTone.entries) {
                val c = badgeColors(tone, colors)
                val fill = c.container.compositeOver(surface)
                val ratio = contrast(c.content, fill)
                // Small bold text: 4.5:1 for the text badges; the medal fills match the existing medal rule (on-medal ink).
                val needed = if (tone == BadgeTone.Accent || tone == BadgeTone.Neutral) 4.5 else 3.0
                assertTrue("$tone on ${if (surface == Color.White) "light" else "dark"}: $ratio", ratio >= needed)
            }
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb).toDouble() / minOf(la, lb)
    }

    @Test
    fun `initials take the first letter of the first two words`() {
        assertEquals("AR", initialsOf("Ana Ruiz"))
        assertEquals("JM", initialsOf("jean-marc dubois"))
        assertEquals("L", initialsOf("Li"))
        assertEquals("?", initialsOf("  "))
    }

    @Test
    fun `avatar tint is deterministic and in range`() {
        val names = listOf("Ana Ruiz", "Dev Kulkarni", "Li Wei", "", "Zoë Ünal")
        names.forEach { name ->
            val index = avatarTintIndex(name)
            assertEquals(index, avatarTintIndex(name))
            assertTrue(index in 0 until 8)
        }
    }

    @Test
    fun `stat spoken text includes label value and spoken unit`() {
        assertEquals("Distance, 10.20 kilometres", Stat("Distance", "10.20", "km", "kilometres").spoken)
        assertEquals("Time, 53:04", Stat("Time", "53:04").spoken)
        assertEquals("Avg HR, not available", Stat("Avg HR", null, "bpm").spoken)
    }

    @Test
    fun `reduced motion collapses durations`() {
        val normal = RidgelineMotion()
        val reduced = RidgelineMotion(reduced = true)
        assertEquals(280, normal.enter(normal.medium))
        assertEquals(196, normal.exit(normal.medium))
        assertEquals(0, reduced.enter(reduced.medium))
        assertEquals(0, reduced.exit(reduced.medium))
    }
}
