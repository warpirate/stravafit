// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.repository

import kotlinx.coroutines.flow.Flow
import org.openactivity.core.data.mapper.EffortKind
import org.openactivity.core.data.model.ActivityDetail
import org.openactivity.core.data.model.ActivityMetadataUpdate
import org.openactivity.core.data.model.NewActivity
import org.openactivity.core.data.model.PersonalBest
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.data.model.StatTotals
import org.openactivity.core.data.model.WeekTotals
import org.openactivity.core.data.model.YearTotals
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.recording.FinishedActivity
import java.time.DayOfWeek

/** The athlete's own activities, stored on the device (offline-first; the device is the source of truth). */
interface ActivityRepository {
    /** All activities, newest first. Emits an empty list when there are none. */
    fun observeActivities(): Flow<List<SavedActivity>>

    /** One activity without streams, or null once it is deleted. */
    fun observeActivity(id: Long): Flow<SavedActivity?>

    /** One activity with points, laps, splits and best efforts, or null when it does not exist. */
    fun observeActivityDetail(id: Long): Flow<ActivityDetail?>

    /**
     * Saves a finished recording and returns its id. Idempotent on [NewActivity.uuid]: saving the same
     * recording twice returns the existing id.
     *
     * @param recordedUnits the unit system the engine split by.
     */
    suspend fun saveFinished(finished: FinishedActivity, options: NewActivity, recordedUnits: UnitSystem): Long

    /** Updates the editable fields. Returns false when the activity does not exist. */
    suspend fun updateMetadata(id: Long, update: ActivityMetadataUpdate): Boolean

    /** Deletes an activity and all its streams. Returns false when it did not exist. */
    suspend fun delete(id: Long): Boolean

    /**
     * Totals of the last [weeks] weeks (oldest first, empty weeks included).
     *
     * @param sports only these sports, or all when null.
     */
    fun observeWeeklyTotals(
        weeks: Int,
        sports: Set<SportType>? = null,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ): Flow<List<WeekTotals>>

    /** Totals of calendar [year], overall and per sport. */
    fun observeYearTotals(year: Int): Flow<YearTotals>

    /** All-time totals per sport (only sports with at least one activity). */
    fun observeAllTimeTotals(): Flow<Map<SportType, StatTotals>>

    /**
     * The best result per target over activities of [sports]: fastest times for [EffortKind.DISTANCE],
     * furthest distances for [EffortKind.DURATION]. Ordered by target.
     */
    fun observePersonalBests(sports: Set<SportType>, kind: EffortKind = EffortKind.DISTANCE): Flow<List<PersonalBest>>
}
