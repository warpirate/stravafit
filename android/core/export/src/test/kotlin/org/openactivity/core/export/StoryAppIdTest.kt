// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.openactivity.core.export.share.ShareLauncher

class StoryAppIdTest {
    @Test
    fun `string and integer meta-data values are accepted`() {
        assertEquals("1234567890123456", ShareLauncher.storyAppIdFrom(" 1234567890123456 "))
        assertEquals("12345", ShareLauncher.storyAppIdFrom(12345))
    }

    @Test
    fun `float values that lost digits and blanks are rejected`() {
        assertNull(ShareLauncher.storyAppIdFrom(1.23456794E15f))
        assertNull(ShareLauncher.storyAppIdFrom(""))
        assertNull(ShareLauncher.storyAppIdFrom(null))
    }
}
