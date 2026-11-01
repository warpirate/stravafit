// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.routing

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RateLimiterTest {

    @Test
    fun `calls are at least a second apart, even when launched together`() = runTest {
        val limiter = RateLimiter(1_000) { currentTime }
        val starts = mutableListOf<Long>()
        repeat(3) { launch { limiter.run { starts += currentTime } } }
        advanceUntilIdle()
        assertEquals(listOf(0L, 1_000L, 2_000L), starts)
    }

    @Test
    fun `a call after a pause does not wait`() = runTest {
        val limiter = RateLimiter(1_000) { currentTime }
        limiter.run { }
        kotlinx.coroutines.delay(5_000)
        val before = currentTime
        limiter.run { }
        assertEquals(before, currentTime)
    }
}
