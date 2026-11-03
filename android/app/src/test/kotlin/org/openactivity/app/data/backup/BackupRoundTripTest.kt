// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.database.OpenActivityDatabase
import org.openactivity.core.database.entity.GoalEntity
import org.openactivity.core.database.entity.RecordingJournalEntity
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupRoundTripTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val locations = LocalDataBackup.appDataLocations(context)
    private lateinit var database: OpenActivityDatabase

    @Before
    fun setUp() {
        database = OpenActivityDatabase.build(context)
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
        context.deleteDatabase(OpenActivityDatabase.NAME)
        locations.workDir.deleteRecursively()
        AppDataLocations.FILE_DIRS.forEach { File(context.filesDir, it).deleteRecursively() }
    }

    @Test
    fun `a backup restores goals, photos, profile and settings and drops the recording journal`() = runTest {
        database.goalDao().insert(goal(target = 50_000.0))
        database.recordingJournalDao().begin(journal())
        write("activity-extras/1/photo-a.jpg", "photo bytes")
        write("activity-extras/1/index.json", "{}")
        write("activity-extras/1/index.json.tmp", "half written")
        write("profile/avatar-1.jpg", "avatar")
        write("datastore/user_preferences.preferences_pb", "prefs")

        val archive = ByteArrayOutputStream()
        val manifest = withContext(Dispatchers.IO) { writer().write(archive, Instant.parse("2026-11-03T06:00:00Z")) }
        assertEquals(0, manifest.activityCount)
        assertEquals(OpenActivityDatabase.VERSION, manifest.databaseVersion)
        val names = entryNames(archive.toByteArray())
        assertTrue(BackupLayout.MANIFEST in names)
        assertTrue("files/activity-extras/1/photo-a.jpg" in names)
        assertFalse("a half-written temp file is left out", "files/activity-extras/1/index.json.tmp" in names)

        // Things change after the backup: a goal edit, a new photo, a lost avatar.
        database.goalDao().insert(goal(target = 1.0))
        write("activity-extras/2/photo-b.jpg", "later photo")
        File(context.filesDir, "profile/avatar-1.jpg").delete()

        val staged = BackupReader(locations, OpenActivityDatabase.VERSION).stage(ByteArrayInputStream(archive.toByteArray()))
        assertEquals(manifest, staged)
        database.close()
        locations.pendingMarker.writeText("test")
        assertTrue(RestoreApplier(locations).applyPending())

        database = OpenActivityDatabase.build(context)
        val goals = database.goalDao().observeAll().first()
        assertEquals(listOf(50_000.0), goals.map { it.target })
        assertNull("the recording in progress is not restored", database.recordingJournalDao().get())
        assertEquals("photo bytes", read("activity-extras/1/photo-a.jpg"))
        assertFalse(File(context.filesDir, "activity-extras/2").exists())
        assertEquals("avatar", read("profile/avatar-1.jpg"))
        assertEquals("prefs", read("datastore/user_preferences.preferences_pb"))
        assertFalse(locations.pendingMarker.exists())
        assertFalse(locations.stagingDir.exists())
        assertTrue("the replaced data is kept aside", File(locations.previousDir, "files/activity-extras/2/photo-b.jpg").isFile)
    }

    @Test
    fun `a swap killed part way finishes on the next start`() = runTest {
        database.goalDao().insert(goal(target = 7.0))
        write("profile/avatar-1.jpg", "backed up avatar")
        val archive = ByteArrayOutputStream().also { out -> withContext(Dispatchers.IO) { writer().write(out) } }
        database.goalDao().insert(goal(target = 8.0))
        write("profile/avatar-1.jpg", "newer avatar")
        reader().stage(ByteArrayInputStream(archive.toByteArray()))
        database.close()

        // Killed after the live data went aside and the database moved in, before the folders did.
        val previous = locations.previousDir
        File(previous, "database").mkdirs()
        File(previous, "files").mkdirs()
        locations.databaseFile.renameTo(File(previous, "database/${locations.databaseFile.name}"))
        File(locations.databaseFile.path + "-wal").let { if (it.exists()) it.renameTo(File(previous, "database/${it.name}")) }
        File(locations.databaseFile.path + "-shm").let { if (it.exists()) it.renameTo(File(previous, "database/${it.name}")) }
        File(context.filesDir, "profile").renameTo(File(previous, "files/profile"))
        File(locations.stagingDir, BackupLayout.DATABASE).renameTo(locations.databaseFile)
        locations.pendingMarker.writeText(RestoreApplier.STEP_ASIDE)

        assertTrue(RestoreApplier(locations).applyPending())
        database = OpenActivityDatabase.build(context)
        assertEquals(listOf(7.0), database.goalDao().observeAll().first().map { it.target })
        assertEquals("backed up avatar", read("profile/avatar-1.jpg"))
        assertEquals("newer avatar", File(previous, "files/profile/avatar-1.jpg").readText())
        assertFalse(locations.pendingMarker.exists())
    }

    @Test
    fun `a restart that cannot happen leaves nothing armed`() = runTest {
        database.goalDao().insert(goal(target = 7.0))
        val file = File(context.cacheDir, "backup.zip")
        val backup = LocalDataBackup(context, database) { false }
        backup.backUp(Uri.fromFile(file))
        backup.stageRestore(Uri.fromFile(file))
        val error = runCatching { backup.restoreAndRestart() }.exceptionOrNull()
        assertTrue(error is BackupException)
        assertFalse(locations.pendingMarker.exists())
        assertFalse(locations.stagingDir.exists())
        file.delete()
    }

    @Test
    fun `nothing happens at start without a confirmed restore`() = runTest {
        database.goalDao().insert(goal(target = 10.0))
        withContext(Dispatchers.IO) { writer().write(ByteArrayOutputStream()) }
        assertFalse(RestoreApplier(locations).applyPending())
        assertEquals(1, database.goalDao().observeAll().first().size)
    }

    @Test
    fun `a backup from a newer app is refused before anything changes`() {
        val archive = archiveOf(BackupLayout.MANIFEST to manifestJson(databaseVersion = OpenActivityDatabase.VERSION + 1))
        val error = assertThrows(BackupException::class.java) { reader().stage(ByteArrayInputStream(archive)) }
        assertTrue(error.message!!.contains("newer version"))
        assertFalse(locations.stagingDir.exists())
    }

    @Test
    fun `a zip that is not a backup is refused`() {
        val notBackup = archiveOf("Morning_Run.gpx" to "<gpx/>")
        assertThrows(BackupException::class.java) { reader().stage(ByteArrayInputStream(notBackup)) }
        assertThrows(BackupException::class.java) { reader().stage(ByteArrayInputStream("not a zip".toByteArray())) }
    }

    @Test
    fun `entries escaping their folder are refused`() {
        assertFalse(BackupLayout.isAllowed("files/profile/../../databases/x.db"))
        assertFalse(BackupLayout.isAllowed("/data/data/x"))
        assertFalse(BackupLayout.isAllowed("files/cache/tiles.bin"))
        assertTrue(BackupLayout.isAllowed("files/profile/avatar-1.jpg"))
        val evil = archiveOf(BackupLayout.MANIFEST to manifestJson(), "files/profile/../../evil" to "x")
        assertThrows(BackupException::class.java) { reader().stage(ByteArrayInputStream(evil)) }
    }

    @Test
    fun `a damaged database is refused`() {
        val archive = archiveOf(BackupLayout.MANIFEST to manifestJson(), BackupLayout.DATABASE to "not sqlite")
        assertThrows(BackupException::class.java) { reader().stage(ByteArrayInputStream(archive)) }
    }

    private fun writer() = BackupWriter(locations, "test", OpenActivityDatabase.VERSION) { target ->
        val live = locations.databaseFile
        database.runInTransaction {
            live.copyTo(target, overwrite = true)
            val wal = File(live.path + "-wal")
            if (wal.isFile) wal.copyTo(File(target.path + "-wal"), overwrite = true)
        }
    }

    private fun reader() = BackupReader(locations, OpenActivityDatabase.VERSION)

    private fun goal(target: Double) =
        GoalEntity(sport = null, period = "WEEK", metric = "DISTANCE", target = target, createdAtMillis = 1, updatedAtMillis = 1)

    private fun journal() = RecordingJournalEntity(
        recordingUuid = "rec-1",
        sport = "RUN",
        routeId = null,
        startedAtMillis = 1,
        snapshotFormat = 1,
        engineSnapshot = byteArrayOf(1, 2, 3),
        pointCount = 0,
        lastMonotonicNanos = 0,
        lastWallClockMillis = 0,
    )

    private fun write(path: String, text: String) = File(context.filesDir, path).apply { parentFile?.mkdirs() }.writeText(text)

    private fun read(path: String) = File(context.filesDir, path).readText()

    private fun manifestJson(databaseVersion: Int = OpenActivityDatabase.VERSION) = BackupLayout.json.encodeToString(
        BackupManifest.serializer(),
        BackupManifest(BackupLayout.FORMAT, "test", databaseVersion, "2026-11-03T06:00:00Z", 0, "/x"),
    )

    private fun archiveOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, text) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun entryNames(bytes: ByteArray): Set<String> = buildSet {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) add((zip.nextEntry ?: break).name)
        }
    }
}
