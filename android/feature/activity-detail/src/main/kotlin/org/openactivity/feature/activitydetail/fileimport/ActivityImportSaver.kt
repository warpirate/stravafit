// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.fileimport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.openactivity.core.data.model.DefaultTitles
import org.openactivity.core.data.model.NewActivity
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.core.data.repository.SettingsRepository
import org.openactivity.core.fileimport.ImportedActivity
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.abs

/** What happened to one imported activity. */
sealed interface SaveOutcome {
    /** Stored as a new activity with [activityId]. */
    data class Saved(val activityId: Long, val title: String) : SaveOutcome

    /** Already stored (same file imported before, or the same recording) as [activityId]. */
    data class Duplicate(val activityId: Long, val title: String) : SaveOutcome

    /** Could not be stored, with a short [reason]. */
    data class Rejected(val reason: String) : SaveOutcome
}

/**
 * Stores imported activities in the local database, skipping ones that are already there.
 *
 * Duplicates are found two ways: the importer's cross-format [ImportedActivity.dedupeKey] is used as
 * the activity uuid (so re-importing a file, or the GPX export of an imported FIT, is caught), and an
 * existing activity of the same sport that starts within [START_TOLERANCE_SECONDS] with a distance
 * within [DISTANCE_TOLERANCE] is treated as the same recording (catches files exported from this app).
 */
class ActivityImportSaver @Inject constructor(
    private val activities: ActivityRepository,
    private val settings: SettingsRepository,
) {
    /** Saves [imported] (in time zone [zone]) unless it is a duplicate. */
    suspend fun save(imported: ImportedActivity, zone: ZoneId = ZoneId.systemDefault()): SaveOutcome {
        val uuid = uuidFor(imported)
        activities.findIdByUuid(uuid)?.let { id -> return SaveOutcome.Duplicate(id, titleOf(id) ?: imported.sourceName) }
        val prefs = settings.settings.first()
        val finished = try {
            // Metrics over a long FIT file take a while: keep them off the caller's (main) thread.
            withContext(Dispatchers.Default) { ImportedActivityConverter.convert(imported, prefs.units) }
        } catch (e: UnusableActivityException) {
            return SaveOutcome.Rejected(e.message ?: "The file cannot be imported")
        }
        val start = Instant.ofEpochMilli(finished.startEpochMillis ?: 0L)
        sameRecording(activities.observeActivities().first(), imported, start, finished.distanceMeters)?.let {
            return SaveOutcome.Duplicate(it.id, it.title)
        }
        val title = imported.title ?: DefaultTitles.forActivity(imported.sport, start.atZone(zone).toLocalTime())
        val id = activities.saveFinished(
            finished,
            NewActivity(
                uuid = uuid,
                title = title,
                visibility = prefs.defaultVisibility,
                hideStartMeters = prefs.privacy.hideStartMeters,
                hideEndMeters = prefs.privacy.hideEndMeters,
                timeZone = zone,
            ),
            prefs.units,
        )
        return SaveOutcome.Saved(id, title)
    }

    private suspend fun titleOf(id: Long): String? = activities.observeActivity(id).first()?.title

    companion object {
        /** Start times this close count as the same recording. */
        const val START_TOLERANCE_SECONDS: Long = 60

        /** Relative distance difference allowed for the same recording. */
        const val DISTANCE_TOLERANCE: Double = 0.05

        /** Stable uuid of an imported activity, shared by every file of the same recording. */
        fun uuidFor(imported: ImportedActivity): String = "import:${imported.dedupeKey}"

        internal fun sameRecording(
            existing: List<SavedActivity>,
            imported: ImportedActivity,
            start: Instant,
            distanceMeters: Double,
        ): SavedActivity? = existing.firstOrNull { a ->
            a.sport == imported.sport &&
                abs(a.startTime.epochSecond - start.epochSecond) <= START_TOLERANCE_SECONDS &&
                abs(a.distanceMeters - distanceMeters) <= DISTANCE_TOLERANCE * maxOf(a.distanceMeters, distanceMeters, 1.0)
        }
    }
}
