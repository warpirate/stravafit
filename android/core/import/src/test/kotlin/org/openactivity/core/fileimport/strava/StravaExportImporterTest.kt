// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.fileimport.strava

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.openactivity.core.fileimport.ImportFormat
import org.openactivity.core.fileimport.ImportedActivity
import org.openactivity.core.fileimport.ManualActivity
import org.openactivity.model.SportType
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Imports `strava-export-sample.zip` (see make_fixture.py next to it) and hand-made ZIPs. */
class StravaExportImporterTest {
    @get:Rule val temp = TemporaryFolder()

    private val fixture: ByteArray = requireNotNull(javaClass.getResourceAsStream("/strava-export/strava-export-sample.zip")).use { it.readBytes() }

    /** Stands in for the database and photo storage: ids from 1, at most one activity per row. */
    private class RecordingSink(private val stopAfter: Int = Int.MAX_VALUE) : StravaImportSink {
        val tracks = LinkedHashMap<String, ImportedActivity>()
        val manual = LinkedHashMap<String, ManualActivity>()
        val photos = ArrayList<Pair<Long, String>>()
        val ids = LinkedHashMap<String, Long>()
        var saves = 0

        private fun store(row: StravaActivityRow): RowOutcome {
            ids[row.id]?.let { return RowOutcome.Duplicate(it) }
            // A process killed mid-import: the row in flight is lost with it.
            if (saves >= stopAfter) throw CancellationException("killed")
            saves++
            val id = ids.size + 1L
            ids[row.id] = id
            return RowOutcome.Imported(id)
        }

        override suspend fun saveTrack(row: StravaActivityRow, activity: ImportedActivity): RowOutcome =
            store(row).also { if (it is RowOutcome.Imported) tracks[row.id] = activity }

        override suspend fun saveManual(row: StravaActivityRow, activity: ManualActivity): RowOutcome =
            store(row).also { if (it is RowOutcome.Imported) manual[row.id] = activity }

        override suspend fun attachPhoto(activityId: Long, path: String, input: InputStream): Boolean {
            val bytes = input.readBytes()
            check(bytes.size > 100 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) { "not the JPEG" }
            photos += activityId to path
            return true
        }
    }

    private suspend fun import(bytes: ByteArray, journal: StravaImportJournal, sink: StravaImportSink, progress: MutableList<StravaImportProgress>? = null) =
        StravaExportImporter().run({ bytes.inputStream() }, journal, sink) { progress?.add(it) }

    @Test
    fun `imports every row of a real-shaped export`() = runTest {
        val sink = RecordingSink()
        val progress = ArrayList<StravaImportProgress>()
        val result = import(fixture, InMemoryStravaImportJournal(), sink, progress)

        assertEquals(6, result.total)
        // 1001-1003 have tracks, 1004 is a manual entry, 1005's file is missing but its summary is enough.
        assertEquals(5, result.imported)
        assertEquals(0, result.duplicates)
        val failed = result.failed.single()
        assertEquals("1006", failed.rowId)
        assertEquals("Broken upload", failed.name)
        assertTrue((failed.outcome as RowOutcome.Failed).reason, failed.outcome.reason.contains("activities/1006.gpx"))

        assertEquals(setOf("1001", "1002", "1003"), sink.tracks.keys)
        assertEquals(setOf("1004", "1005"), sink.manual.keys)
        assertEquals(ImportFormat.GPX, sink.tracks.getValue("1001").format)
        assertEquals(ImportFormat.TCX, sink.tracks.getValue("1003").format)
        assertEquals(FIXTURE_POINTS, sink.tracks.getValue("1001").points.size)
        assertEquals(140, sink.tracks.getValue("1001").points.first().heartRate)

        val yoga = sink.manual.getValue("1004")
        assertEquals(SportType.YOGA, yoga.sport)
        assertEquals(Instant.parse("2026-10-07T12:30:00Z"), yoga.startTime)
        assertEquals(3600L, yoga.elapsedSeconds)
        val tempo = sink.manual.getValue("1005")
        assertEquals(5103.2, tempo.distanceMeters, 1e-6)
        assertEquals(1795L, tempo.movingSeconds)
        assertEquals(171, tempo.maxHeartRate)

        // Photos go to their own activity; the unreferenced one is ignored.
        assertEquals(
            setOf(sink.ids.getValue("1001") to "media/0f3c2a9e-1001.jpg", sink.ids.getValue("1003") to "media/7b11-1003.jpg"),
            sink.photos.toSet(),
        )
        assertEquals(2, result.photosAttached)

        assertEquals(StravaImportProgress.Phase.READING_LIST, progress.first().phase)
        assertEquals(StravaImportProgress(StravaImportProgress.Phase.ACTIVITIES, 6, 6), progress.last())
    }

    @Test
    fun `gzipped tracks are read like plain ones`() = runTest {
        val sink = RecordingSink()
        import(fixture, InMemoryStravaImportJournal(), sink)
        val walk = sink.tracks.getValue("1002")
        assertEquals(ImportFormat.GPX, walk.format)
        assertEquals(FIXTURE_POINTS, walk.points.size)
        assertEquals("1002.gpx.gz", walk.sourceName)
    }

    @Test
    fun `a track stored before activities csv is found by a second pass`() = runTest {
        var opened = 0
        val sink = RecordingSink()
        StravaExportImporter().run({ opened++; fixture.inputStream() }, InMemoryStravaImportJournal(), sink)
        assertTrue("1003" in sink.tracks)
        assertEquals(2, opened)
    }

    @Test
    fun `one pass is enough when the list comes first`() = runTest {
        val zip = zipOf("activities.csv" to CSV_ONE_GPX.toByteArray(), "activities/1.gpx" to TINY_GPX.toByteArray())
        var opened = 0
        val sink = RecordingSink()
        StravaExportImporter().run({ opened++; zip.inputStream() }, InMemoryStravaImportJournal(), sink)
        assertEquals(1, opened)
        assertEquals(setOf("1"), sink.tracks.keys)
    }

    @Test
    fun `an export inside a folder is found`() = runTest {
        val zip = zipOf("export_9001/activities.csv" to CSV_ONE_GPX.toByteArray(), "export_9001/activities/1.gpx" to TINY_GPX.toByteArray())
        val sink = RecordingSink()
        val result = import(zip, InMemoryStravaImportJournal(), sink)
        assertEquals(1, result.imported)
        assertEquals(setOf("1"), sink.tracks.keys)
    }

    @Test
    fun `a zip without activities csv is not an export`() {
        val zip = zipOf("manifest.json" to "{}".toByteArray(), "db/activities.db" to ByteArray(10))
        val e = assertThrows(StravaExportException::class.java) {
            runBlocking { import(zip, InMemoryStravaImportJournal(), RecordingSink()) }
        }
        assertTrue(e.message!!.contains("activities.csv"))
    }

    @Test
    fun `a file that is not a zip is reported, not crashed on`() {
        val e = assertThrows(StravaExportException::class.java) {
            runBlocking { import(TINY_GPX.toByteArray(), InMemoryStravaImportJournal(), RecordingSink()) }
        }
        assertNotNull(e.message)
    }

    @Test
    fun `resumes after the process dies without importing anything twice`() = runBlocking {
        val journal = FileStravaImportJournal(temp.newFile("journal.txt"))
        // The "database" outlives the process; the journal file does too.
        val firstRun = RecordingSink(stopAfter = 2)
        try {
            import(fixture, journal, firstRun)
            fail("the first run should have been stopped")
        } catch (_: CancellationException) {
            // the process died
        }
        assertEquals(2, journal.entries().size)

        val resumed = RecordingSink().also { it.ids.putAll(firstRun.ids) }
        val result = import(fixture, journal, resumed)

        assertEquals(6, result.entries.size)
        assertEquals(6, result.entries.map { it.rowId }.toSet().size)
        assertEquals(5, result.imported)
        assertEquals(0, result.duplicates)
        // Rows done before the crash were not handed to the sink again.
        assertTrue(firstRun.ids.keys.none { it in resumed.tracks || it in resumed.manual })
        assertEquals(5, resumed.ids.size)
        // The photo of a row done before the crash is still attached.
        assertTrue(resumed.photos.any { it.first == firstRun.ids["1001"] })
    }

    @Test
    fun `a row lost in flight is caught by the duplicate check`() = runTest {
        val journal = InMemoryStravaImportJournal()
        val sink = RecordingSink()
        import(fixture, journal, sink)
        // The journal is lost (or the same export is imported again): the sink reports duplicates.
        journal.clear()
        val again = import(fixture, journal, sink)
        assertEquals(0, again.imported)
        assertEquals(5, again.duplicates)
        assertEquals(1, again.failed.size)
        // Photos are not attached twice either: duplicates keep the photos they have.
        assertEquals(2, sink.photos.size)
    }

    @Test
    fun `photos are not attached twice when resuming`() = runTest {
        val journal = FileStravaImportJournal(temp.newFile("journal.txt"))
        val sink = RecordingSink()
        import(fixture, journal, sink)
        val resumed = import(fixture, journal, sink)
        assertEquals(2, sink.photos.size)
        assertEquals(0, resumed.photosAttached)
        assertEquals(5, resumed.imported)
    }

    @Test
    fun `the journal survives a torn last line`() = runTest {
        val file = temp.newFile("journal.txt")
        val journal = FileStravaImportJournal(file)
        journal.record(JournalEntry("1", "Morning\tRun", RowOutcome.Imported(7)))
        journal.record(JournalEntry("2", "Walk", RowOutcome.Failed("Bad file:\nline 2")))
        journal.recordPhoto("media/a.jpg")
        file.appendText("R\t3\timported\t9") // cut off before its end mark
        val entries = FileStravaImportJournal(file).entries()
        assertEquals(
            listOf(
                JournalEntry("1", "Morning\tRun", RowOutcome.Imported(7)),
                JournalEntry("2", "Walk", RowOutcome.Failed("Bad file:\nline 2")),
            ),
            entries,
        )
        assertEquals(setOf("media/a.jpg"), FileStravaImportJournal(file).photos())
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private companion object {
        /** Samples per track in the fixture (every 8th point of the 2 km Pune run). */
        const val FIXTURE_POINTS = 19

        const val CSV_ONE_GPX = "Activity ID,Activity Date,Activity Name,Activity Type,Filename\n" +
            "1,\"Oct 3, 2026, 1:15:00 AM\",Morning Run,Run,activities/1.gpx\n"

        const val TINY_GPX = """<?xml version="1.0"?><gpx version="1.1" creator="t" xmlns="http://www.topografix.com/GPX/1/1"><trk><trkseg>
<trkpt lat="18.5368030" lon="73.8791830"><time>2026-10-03T01:15:00Z</time></trkpt>
<trkpt lat="18.5370064" lon="73.8798625"><time>2026-10-03T01:15:10Z</time></trkpt>
<trkpt lat="18.5375625" lon="73.8798177"><time>2026-10-03T01:15:20Z</time></trkpt>
</trkseg></trk></gpx>"""
    }
}
