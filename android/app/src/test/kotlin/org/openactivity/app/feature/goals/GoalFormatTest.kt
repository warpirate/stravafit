// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.goals

import org.junit.Assert.assertEquals
import org.junit.Test

class GoalFormatTest {
    @Test
    fun `round targets lose their zeros, thousands groups stay`() {
        assertEquals("30", GoalFormat.trimZeros("30.00"))
        assertEquals("24.1", GoalFormat.trimZeros("24.10"))
        assertEquals("24,15", GoalFormat.trimZeros("24,15"))
        assertEquals("2,000", GoalFormat.trimZeros("2,000"))
        assertEquals("2.000", GoalFormat.trimZeros("2.000"))
        assertEquals("1,500", GoalFormat.trimZeros("1,500.00"))
        assertEquals("12", GoalFormat.trimZeros("12"))
    }
}
