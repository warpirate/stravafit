// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data.backup

import android.database.sqlite.SQLiteException
import android.net.Uri
import androidx.work.ListenableWorker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.app.testing.FakeAutoBackupStore
import org.openactivity.app.testing.FakeNotificationRepository
import org.openactivity.core.data.model.NotificationTarget
import org.openactivity.core.data.model.NotificationType
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The automatic backup: dated files, retention among its own files only, and honest failures. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AutoBackupTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-03T08:12:05Z"), ZoneOffset.UTC)
    private val manifest = BackupManifest(1, "0.2.0", 3, "2026-11-03T08:12:05Z", 7, "/files")

    /** A folder in memory; [failCreate] / [failWrite] simulate a provider that throws. */
    private class MemoryFolder(names: List<String> = emptyList()) : BackupFolder {
        val files = linkedMapOf<String, ByteArrayOutputStream>().apply { names.forEach { put(it, ByteArrayOutputStream()) } }
        var failCreate: Exception? = null
        var failWrite: IOException? = null

        override fun list() = files.keys.map { BackupFolderFile(it, it) }

        override fun create(name: String): BackupFolderFile {
            failCreate?.let { throw it }
            files[name] = ByteArrayOutputStream()
            return BackupFolderFile(name, name)
        }

        override fun openOutput(file: BackupFolderFile): OutputStream {
            val target = files.getValue(file.id)
            return object : OutputStream() {
                override fun write(b: Int) {
                    failWrite?.let { throw it }
                    target.write(b)
                }
            }
        }

        override fun delete(file: BackupFolderFile): Boolean = files.remove(file.id) != null
    }

    /** Writes a few bytes, or throws [error], or waits for [gate]. */
    private inner class FakeBackup : DataBackup {
        var error: Exception? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun backUp(destination: Uri): BackupManifest = error("not used")

        override suspend fun backUp(output: OutputStream): BackupManifest {
            gate?.await()
            error?.let { throw it }
            output.write("PK".toByteArray())
            return manifest
        }

        override suspend fun stageRestore(source: Uri): BackupManifest = error("not used")

        override suspend fun discardRestore() = Unit

        override suspend fun restoreAndRestart() = Unit
    }

    private val folderUri = "content://tree/backups"
    private val store = FakeAutoBackupStore(AutoBackupConfig(enabled = true, folderUri = folderUri, folderName = "Backups"))
    private val backup = FakeBackup()
    private var room = true

    private fun runner(folder: () -> BackupFolder) = AutoBackupRunner(store, { folder() }, backup, { room }, clock)

    @Test
    fun `a backup is a dated file and the status records it`() = runTest {
        val folder = MemoryFolder()
        val outcome = runner { folder }.run() as AutoBackupOutcome.Success
        assertEquals("stravafit-auto-backup-2026-11-03-081205.zip", outcome.fileName)
        assertEquals("PK", folder.files.getValue(outcome.fileName).toString())
        val status = store.status.first()
        assertEquals(clock.instant(), status.lastSuccessAt)
        assertEquals(7, status.lastActivityCount)
        assertNull(status.lastFailure)
    }

    @Test
    fun `the newest four automatic backups are kept and nothing else is touched`() = runTest {
        val others = listOf(
            "stravafit-backup-2026-10-01.zip",
            "stravafit-auto-backup-2026-10-01-080000 (1).zip",
            "notes.txt",
            "stravafit-auto-backup-2026-13-45-999999.zip",
        )
        val old = listOf(
            "stravafit-auto-backup-2026-10-06-080000.zip",
            "stravafit-auto-backup-2026-10-13-080000.zip",
            "stravafit-auto-backup-2026-10-20-080000.zip",
            "stravafit-auto-backup-2026-10-27-080000.zip",
            "stravafit-auto-backup-2026-09-29-080000.zip",
        )
        val folder = MemoryFolder(others + old.shuffled())
        val outcome = runner { folder }.run() as AutoBackupOutcome.Success
        assertEquals(2, outcome.deleted)
        val autos = folder.files.keys.filter { AutoBackupNames.timeOf(it) != null }.sorted()
        assertEquals(
            listOf(
                "stravafit-auto-backup-2026-10-13-080000.zip",
                "stravafit-auto-backup-2026-10-20-080000.zip",
                "stravafit-auto-backup-2026-10-27-080000.zip",
                "stravafit-auto-backup-2026-11-03-081205.zip",
            ),
            autos,
        )
        assertTrue(folder.files.keys.containsAll(others))
    }

    @Test
    fun `retention keeps everything while there are four or fewer`() {
        val files = (1..4).map { BackupFolderFile("$it", "stravafit-auto-backup-2026-10-0$it-080000.zip") }
        assertTrue(AutoBackupNames.toDelete(files).isEmpty())
        assertNull(AutoBackupNames.timeOf("stravafit-backup-2026-11-03.zip"))
        assertNull(AutoBackupNames.timeOf("stravafit-auto-backup-2026-11-03-081205.zip.partial"))
    }

    @Test
    fun `a failed write leaves no half-written file and keeps older backups`() = runTest {
        val folder = MemoryFolder(listOf("stravafit-auto-backup-2026-10-27-080000.zip"))
        folder.failWrite = IOException("write failed: EIO (I/O error)")
        val outcome = runner { folder }.run()
        assertEquals(AutoBackupOutcome.Failed(AutoBackupFailure.WRITE_FAILED), outcome)
        assertEquals(listOf("stravafit-auto-backup-2026-10-27-080000.zip"), folder.files.keys.toList())
        assertEquals(AutoBackupFailure.WRITE_FAILED, store.status.first().lastFailure)
    }

    @Test
    fun `a full disk, a revoked folder and a deleted folder are told apart`() = runTest {
        val full = MemoryFolder().apply { failWrite = IOException("write failed: ENOSPC (No space left on device)") }
        assertEquals(AutoBackupOutcome.Failed(AutoBackupFailure.NO_SPACE), runner { full }.run())

        assertEquals(
            AutoBackupOutcome.Failed(AutoBackupFailure.FOLDER_REVOKED),
            runner { throw AutoBackupFolderException(AutoBackupFailure.FOLDER_REVOKED) }.run(),
        )
        val gone = MemoryFolder().apply { failCreate = FileNotFoundException("Missing file for primary:Backups") }
        assertEquals(AutoBackupOutcome.Failed(AutoBackupFailure.FOLDER_MISSING), runner { gone }.run())

        room = false
        assertEquals(AutoBackupOutcome.Failed(AutoBackupFailure.NO_SPACE), runner { MemoryFolder() }.run())
    }

    @Test
    fun `without a folder nothing is written`() = runTest {
        store.configState.value = AutoBackupConfig()
        assertEquals(AutoBackupOutcome.Failed(AutoBackupFailure.NO_FOLDER), runner { error("must not open") }.run())
    }

    @Test
    fun `a second run while one is writing does nothing`() = runTest {
        val folder = MemoryFolder()
        backup.gate = CompletableDeferred()
        val runner = runner { folder }
        val first = async { runner.run() }
        advanceUntilIdle()
        assertTrue(runner.running.value)
        assertEquals(AutoBackupOutcome.AlreadyRunning, runner.run())
        backup.gate?.complete(Unit)
        assertTrue(first.await() is AutoBackupOutcome.Success)
        assertFalse(runner.running.value)
        assertEquals(1, folder.files.size)
    }

    @Test
    fun `failures map from what the platform throws`() {
        assertEquals(AutoBackupFailure.FOLDER_REVOKED, AutoBackupFailure.of(SecurityException("Permission Denial")))
        assertEquals(AutoBackupFailure.FOLDER_MISSING, AutoBackupFailure.of(FileNotFoundException("gone")))
        assertEquals(AutoBackupFailure.FOLDER_MISSING, AutoBackupFailure.of(IllegalArgumentException("Invalid document")))
        assertEquals(AutoBackupFailure.NO_SPACE, AutoBackupFailure.of(IOException("copy failed", IOException("write failed: ENOSPC (No space left on device)"))))
        assertEquals(AutoBackupFailure.NO_SPACE, AutoBackupFailure.of(AutoBackupNoSpaceException()))
        assertEquals(AutoBackupFailure.DATABASE_UNREADABLE, AutoBackupFailure.of(SQLiteException("disk image is malformed")))
        assertEquals(AutoBackupFailure.WRITE_FAILED, AutoBackupFailure.of(IOException("Connection reset")))
        assertEquals(AutoBackupFailure.FOLDER_MISSING, AutoBackupFailure.of(AutoBackupFolderException(AutoBackupFailure.FOLDER_MISSING)))
    }

    @Test
    fun `scheduled runs retry a transient error before reporting, everything else is reported at once`() = runTest {
        val reported = mutableListOf<AutoBackupFailure>()
        val report: suspend (AutoBackupFailure) -> Unit = { reported += it }
        assertTrue(AutoBackupWorker.decide(AutoBackupFailure.WRITE_FAILED, manual = false, attempt = 0, report) is ListenableWorker.Result.Retry)
        assertTrue(reported.isEmpty())
        assertTrue(AutoBackupWorker.decide(AutoBackupFailure.WRITE_FAILED, manual = false, attempt = AutoBackupWorker.MAX_RETRIES, report) is ListenableWorker.Result.Success)
        assertTrue(AutoBackupWorker.decide(AutoBackupFailure.WRITE_FAILED, manual = true, attempt = 0, report) is ListenableWorker.Result.Success)
        assertTrue(AutoBackupWorker.decide(AutoBackupFailure.FOLDER_REVOKED, manual = false, attempt = 0, report) is ListenableWorker.Result.Success)
        assertEquals(listOf(AutoBackupFailure.WRITE_FAILED, AutoBackupFailure.WRITE_FAILED, AutoBackupFailure.FOLDER_REVOKED), reported)
    }

    @Test
    fun `a failure goes to the inbox once a day and opens Settings`() = runTest {
        val inbox = FakeNotificationRepository()
        val reporter = AutoBackupFailureReporter(inbox, clock)
        reporter.report(AutoBackupFailure.FOLDER_REVOKED)
        reporter.report(AutoBackupFailure.FOLDER_REVOKED)
        reporter.report(AutoBackupFailure.NO_SPACE)
        val entries = inbox.entries.value
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.type == NotificationType.BACKUP_FAILED && it.target == NotificationTarget.SETTINGS })
        assertEquals(AutoBackupFailure.FOLDER_REVOKED.message, entries.last().body)
    }
}
