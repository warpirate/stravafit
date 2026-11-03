// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.fileimport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.openactivity.core.data.model.ActivityTitles
import org.openactivity.core.data.model.DefaultTitles
import org.openactivity.core.data.model.NewActivity
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.core.data.repository.SettingsRepository
import org.openactivity.core.fileimport.ImportedActivity
import org.openactivity.core.fileimport.ManualActivity
import org.openactivity.model.SportType
import org.openactivity.recording.FinishedActivity
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TreeMap
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
    private val titles: ActivityTitles = DefaultTitles,
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
        val title = imported.title ?: titles.forActivity(imported.sport, start, zone)
        val id = activities.saveFinished(
            finished,
            NewActivity(
                uuid = uuid,
                title = title,
                visibility = prefs.defaultVisibility(imported.sport),
                hideStartMeters = prefs.privacy.hideStartMeters,
                hideEndMeters = prefs.privacy.hideEndMeters,
                timeZone = zone,
            ),
            prefs.units,
        )
        return SaveOutcome.Saved(id, title)
    }

    private suspend fun titleOf(id: Long): String? = activities.observeActivity(id).first()?.title

    /**
     * Loads every stored activity's start into an [ExistingStarts] for a bulk import, so that each
     * activity of a thousand-activity import does not read the whole list again.
     */
    suspend fun existingStarts(): ExistingStarts = ExistingStarts(activities.observeActivities().first())

    /**
     * Saves an activity from another service (a Strava export, Health Connect) with its own [details],
     * unless it is a duplicate: the same [ExternalImport.uuid] was stored before, or [existing] has an
     * activity of the same kind of sport starting within [START_TOLERANCE_SECONDS] (the same workout,
     * recorded by this app or brought in another way). A saved activity is added to [existing].
     */
    suspend fun saveExternal(imported: ImportedActivity, details: ExternalImport, existing: ExistingStarts): SaveOutcome {
        val sport = details.sport ?: imported.sport
        val withSport = imported.copy(sport = sport)
        return store(details, sport, existing) { units ->
            withContext(Dispatchers.Default) { ImportedActivityConverter.convert(withSport, units) }
        }
    }

    /** Saves an activity known only by its summary, with the same duplicate checks as [saveExternal]. */
    suspend fun saveManual(manual: ManualActivity, details: ExternalImport, existing: ExistingStarts): SaveOutcome {
        val sport = details.sport ?: manual.sport
        return store(details, sport, existing) { ImportedActivityConverter.manual(manual.copy(sport = sport)) }
    }

    private suspend fun store(
        details: ExternalImport,
        sport: SportType,
        existing: ExistingStarts,
        convert: suspend (org.openactivity.model.UnitSystem) -> FinishedActivity,
    ): SaveOutcome {
        activities.findIdByUuid(details.uuid)?.let { id -> return SaveOutcome.Duplicate(id, titleOf(id) ?: details.title.orEmpty()) }
        val prefs = settings.settings.first()
        val finished = try {
            convert(prefs.units)
        } catch (e: UnusableActivityException) {
            return SaveOutcome.Rejected(e.message ?: "It cannot be imported")
        }
        val start = Instant.ofEpochMilli(finished.startEpochMillis ?: 0L)
        existing.find(start, sport)?.let { return SaveOutcome.Duplicate(it.id, it.title) }
        val title = details.title?.trim()?.ifEmpty { null } ?: titles.forActivity(sport, start, details.zone)
        val id = activities.saveFinished(
            finished,
            NewActivity(
                uuid = details.uuid,
                title = title,
                description = details.description.trim(),
                visibility = prefs.defaultVisibility(sport),
                hideStartMeters = prefs.privacy.hideStartMeters,
                hideEndMeters = prefs.privacy.hideEndMeters,
                timeZone = details.zone,
            ),
            prefs.units,
        )
        existing.add(id, title, start, sport)
        return SaveOutcome.Saved(id, title)
    }

    companion object {
        /** Start times this close count as the same recording. */
        const val START_TOLERANCE_SECONDS: Long = 60

        /** Relative distance difference allowed for the same recording. */
        const val DISTANCE_TOLERANCE: Double = 0.05

        /** Stable uuid of an imported activity, shared by every file of the same recording. */
        fun uuidFor(imported: ImportedActivity): String = "import:${imported.dedupeKey}"

        /**
         * Whether two activities are the same workout for imports from other services: they start within
         * [START_TOLERANCE_SECONDS] and are the same sport, or sports of the same family (a Strava "Trail
         * Run" and a watch's "Run"; nobody does two foot sports at once).
         */
        fun sameWorkout(aStart: Instant, aSport: SportType, bStart: Instant, bSport: SportType): Boolean =
            abs(aStart.epochSecond - bStart.epochSecond) <= START_TOLERANCE_SECONDS &&
                (aSport == bSport || aSport.category == bSport.category)

        /** The zone for a UTC offset read from a file, or the phone's zone when there is none. */
        fun zoneFor(utcOffsetSeconds: Int?): ZoneId =
            utcOffsetSeconds?.let { runCatching { ZoneOffset.ofTotalSeconds(it) }.getOrNull() } ?: ZoneId.systemDefault()

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

/**
 * Identity and words of an activity imported from another service.
 *
 * @property uuid stable id from the source (`strava:<activity id>`, `hc:<record id>`): importing the same
 *   activity again finds it.
 * @property title the source's name; null for the default ("Morning Run").
 * @property sport overrides the sport the track file declares (the athlete may have changed it).
 * @property zone the time zone it took place in, for its title and its displayed times.
 */
data class ExternalImport(
    val uuid: String,
    val title: String?,
    val description: String = "",
    val sport: SportType? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
)

/**
 * Starts of the stored activities, indexed by time, for duplicate checks during a bulk import (see
 * [ActivityImportSaver.sameWorkout]). Not thread-safe: one import uses one instance.
 */
class ExistingStarts(activities: List<SavedActivity>) {
    /** One stored activity as the duplicate check sees it. */
    data class Entry(val id: Long, val title: String, val start: Instant, val sport: SportType)

    private val bySecond = TreeMap<Long, MutableList<Entry>>()

    init {
        activities.forEach { add(it.id, it.title, it.startTime, it.sport) }
    }

    /** A stored activity that is the same workout as one of [sport] starting at [start], if any. */
    fun find(start: Instant, sport: SportType): Entry? {
        val s = start.epochSecond
        return bySecond.subMap(s - ActivityImportSaver.START_TOLERANCE_SECONDS, true, s + ActivityImportSaver.START_TOLERANCE_SECONDS, true)
            .values.flatten()
            .filter { ActivityImportSaver.sameWorkout(it.start, it.sport, start, sport) }
            .minByOrNull { abs(it.start.epochSecond - s) }
    }

    fun add(id: Long, title: String, start: Instant, sport: SportType) {
        bySecond.getOrPut(start.epochSecond) { ArrayList(1) } += Entry(id, title, start, sport)
    }
}
