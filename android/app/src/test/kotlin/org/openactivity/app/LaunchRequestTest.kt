// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.recordingservice.notification.RecordingNotifications
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LaunchRequestTest {

    @Test
    fun `plain launch asks for nothing`() {
        assertNull(LaunchRequest.consume(Intent()))
        assertNull(LaunchRequest.consume(null))
    }

    @Test
    fun `recording notification opens the recorder once`() {
        val intent = Intent().putExtra(RecordingNotifications.EXTRA_OPEN_RECORDING, true)
        assertEquals(LaunchRequest.OpenRecorder, LaunchRequest.consume(intent))
        assertFalse(intent.hasExtra(RecordingNotifications.EXTRA_OPEN_RECORDING))
        assertNull(LaunchRequest.consume(intent))
    }

    @Test
    fun `saved notification opens that activity once`() {
        val intent = Intent().putExtra(RecordingNotifications.EXTRA_ACTIVITY_ID, 42L)
        assertEquals(LaunchRequest.OpenActivity(42L), LaunchRequest.consume(intent))
        assertNull(LaunchRequest.consume(intent))
    }

    @Test
    fun `reopening from recents does not replay the notification`() {
        val intent = Intent()
            .putExtra(RecordingNotifications.EXTRA_ACTIVITY_ID, 42L)
            .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(LaunchRequest.consume(intent))
        assertFalse(intent.hasExtra(RecordingNotifications.EXTRA_ACTIVITY_ID))
    }

    @Test
    fun `invalid activity id is ignored`() {
        val intent = Intent().putExtra(RecordingNotifications.EXTRA_ACTIVITY_ID, 0L)
        assertNull(LaunchRequest.consume(intent))
    }
}
