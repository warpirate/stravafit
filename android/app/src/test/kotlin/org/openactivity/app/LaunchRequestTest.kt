// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.app.notifications.AndroidSystemNotifier
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

    @Test
    fun `open with a gpx file asks to import it, once`() {
        val file = Uri.parse("content://com.android.providers.downloads.documents/document/42")
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(file, "application/gpx+xml")
        assertEquals(LaunchRequest.ImportFiles(listOf(file)), LaunchRequest.consume(intent))
        assertNull(intent.data)
        assertNull(LaunchRequest.consume(intent))
    }

    @Test
    fun `share sheet single and multiple files are imported`() {
        val one = Uri.parse("content://watch.app/files/ride.fit")
        val two = Uri.parse("content://watch.app/files/run.fit.gz")
        val single = Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, one)
        assertEquals(LaunchRequest.ImportFiles(listOf(one)), LaunchRequest.consume(single))
        assertNull(LaunchRequest.consume(single))

        val multiple = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("application/octet-stream")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(one, two, one))
        assertEquals(LaunchRequest.ImportFiles(listOf(one, two)), LaunchRequest.consume(multiple))
    }

    @Test
    fun `shares without a file or with web urls are not imports`() {
        assertNull(LaunchRequest.consume(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "hi")))
        val web = Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, Uri.parse("https://evil.example/x.fit"))
        assertNull(LaunchRequest.consume(web))
    }

    @Test
    fun `links are left to the navigation graph on a fresh start and reported when running`() {
        val link = Uri.parse("openactivity://activity/12")
        assertNull(LaunchRequest.consume(Intent(Intent.ACTION_VIEW, link), includeLinks = false))
        assertEquals(LaunchRequest.DeepLink(link), LaunchRequest.consume(Intent(Intent.ACTION_VIEW, link), includeLinks = true))
        assertNull(LaunchRequest.consume(Intent(Intent.ACTION_VIEW, Uri.parse("https://elsewhere.example/a")), includeLinks = true))
    }

    @Test
    fun `reopening from recents does not replay a file or a link`() {
        val file = Intent(Intent.ACTION_VIEW, Uri.parse("content://x/run.gpx")).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(LaunchRequest.consume(file))
        assertNull(file.data)
        val link = Intent(Intent.ACTION_VIEW, Uri.parse("openactivity://record?sport=RUN")).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(LaunchRequest.consume(link, includeLinks = true))
        assertNull("the graph must not replay it either", link.data)
    }

    @Test
    fun `the notifications summary opens the inbox once, not from recents`() {
        val intent = Intent().putExtra(AndroidSystemNotifier.EXTRA_OPEN_INBOX, true)
        assertEquals(LaunchRequest.OpenInbox, LaunchRequest.consume(intent))
        assertNull(LaunchRequest.consume(intent))
        val replay = Intent().putExtra(AndroidSystemNotifier.EXTRA_OPEN_INBOX, true).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(LaunchRequest.consume(replay))
    }

    @Test
    fun `inbox notification opens its entry once, not from recents`() {
        val intent = Intent().putExtra(AndroidSystemNotifier.EXTRA_INBOX_ID, 7L)
        assertEquals(LaunchRequest.OpenInboxEntry(7L), LaunchRequest.consume(intent))
        assertNull(LaunchRequest.consume(intent))
        val replay = Intent().putExtra(AndroidSystemNotifier.EXTRA_INBOX_ID, 7L).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        assertNull(LaunchRequest.consume(replay))
        assertFalse(replay.hasExtra(AndroidSystemNotifier.EXTRA_INBOX_ID))
    }
}
