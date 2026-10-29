// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openactivity.core.designsystem.component.Stat
import org.openactivity.core.designsystem.component.avatarTintIndex
import org.openactivity.core.designsystem.component.initialsOf
import org.openactivity.core.designsystem.theme.RidgelineMotion

class ComponentLogicTest {

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
