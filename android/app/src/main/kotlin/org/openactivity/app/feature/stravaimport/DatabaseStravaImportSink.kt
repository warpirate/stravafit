// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.stravaimport

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openactivity.core.activityextras.ActivityExtrasRepository
import org.openactivity.core.fileimport.ImportedActivity
import org.openactivity.core.fileimport.ManualActivity
import org.openactivity.core.fileimport.strava.RowOutcome
import org.openactivity.core.fileimport.strava.StravaActivityRow
import org.openactivity.core.fileimport.strava.StravaImportSink
import org.openactivity.feature.activitydetail.fileimport.ActivityImportSaver
import org.openactivity.feature.activitydetail.fileimport.ExistingStarts
import org.openactivity.feature.activitydetail.fileimport.ExternalImport
import org.openactivity.feature.activitydetail.fileimport.SaveOutcome
import org.openactivity.model.SportType
import java.io.File
import java.io.InputStream

/**
 * Stores a Strava export's activities in the database (through [ActivityImportSaver], which skips
 * duplicates) and its photos with the other activity photos (re-encoded without location metadata).
 * Each activity is stored as `strava:<activity id>`, so importing the same export again finds it.
 */
class DatabaseStravaImportSink(
    private val saver: ActivityImportSaver,
    private val extras: ActivityExtrasRepository,
    private val existing: ExistingStarts,
    private val scratchDir: File,
) : StravaImportSink {

    override suspend fun saveTrack(row: StravaActivityRow, activity: ImportedActivity): RowOutcome =
        saver.saveExternal(activity, details(row, sportFor(row, activity), activity.utcOffsetSeconds), existing).toRow()

    override suspend fun saveManual(row: StravaActivityRow, activity: ManualActivity): RowOutcome =
        saver.saveManual(activity, details(row, row.sport, activity.utcOffsetSeconds), existing).toRow()

    override suspend fun attachPhoto(activityId: Long, path: String, input: InputStream): Boolean {
        val tmp = withContext(Dispatchers.IO) {
            scratchDir.mkdirs()
            File(scratchDir, "photo-${System.nanoTime()}.${path.substringAfterLast('.', "jpg").take(EXTENSION_MAX)}").also { file ->
                file.outputStream().use { out -> input.copyTo(out) }
            }
        }
        return try {
            extras.addPhotos(activityId, listOf(Uri.fromFile(tmp))).added > 0
        } finally {
            withContext(Dispatchers.IO) { tmp.delete() }
        }
    }

    private fun details(row: StravaActivityRow, sport: SportType, utcOffsetSeconds: Int?) = ExternalImport(
        uuid = uuidFor(row),
        title = row.name.ifBlank { null },
        description = row.description,
        sport = sport,
        zone = ActivityImportSaver.zoneFor(utcOffsetSeconds),
    )

    private fun SaveOutcome.toRow(): RowOutcome = when (this) {
        is SaveOutcome.Saved -> RowOutcome.Imported(activityId)
        is SaveOutcome.Duplicate -> RowOutcome.Duplicate(activityId)
        is SaveOutcome.Rejected -> RowOutcome.Failed(reason)
    }

    companion object {
        private const val EXTENSION_MAX = 5

        /** The stored uuid of a Strava activity. */
        fun uuidFor(row: StravaActivityRow): String = "strava:${row.id}"

        /**
         * The athlete's type from `activities.csv` wins over what the file says (they may have changed it on
         * Strava), except when Strava's type is one we do not know and the file names a sport.
         */
        fun sportFor(row: StravaActivityRow, activity: ImportedActivity): SportType {
            val unknownType = row.sport == SportType.WORKOUT && !row.typeName.equals("Workout", ignoreCase = true)
            return if (unknownType && activity.sportDeclared) activity.sport else row.sport
        }
    }
}
