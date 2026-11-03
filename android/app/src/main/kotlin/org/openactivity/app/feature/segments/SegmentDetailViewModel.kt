// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.segments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.data.segments.SegmentRematchScheduler
import org.openactivity.app.format.UnitFormatter
import org.openactivity.core.data.model.Segment
import org.openactivity.core.data.model.SegmentEffort
import org.openactivity.core.data.repository.SegmentRepository
import org.openactivity.core.designsystem.component.Stat
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject

/**
 * One effort in a segment's history.
 *
 * @property rank the medal frozen when it was saved (1 to 3), or null.
 * @property pace pace or speed over the segment ("4:12 /km", "28.4 km/h").
 */
data class EffortHistoryRow(
    val id: Long,
    val activityId: Long,
    val date: String,
    val activityTitle: String,
    val time: String,
    val pace: String?,
    val heartRate: String?,
    val rank: Int?,
    val yearly: Boolean,
    val isPersonalRecord: Boolean,
) {
    val medalLabel: String? get() = SegmentFormat.medalLabel(rank, yearly)
}

/** A point of the "times over the date" chart: x is the date (epoch day), y the time in seconds. */
data class EffortPoint(val epochDay: Long, val seconds: Long, val isBest: Boolean)

/**
 * State of a segment's detail screen.
 *
 * @property best your three fastest efforts, fastest first.
 * @property recent every effort, newest first.
 */
data class SegmentDetailUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val id: Long = 0,
    val name: String = "",
    val sport: SportType = SportType.RUN,
    val starred: Boolean = false,
    val points: List<LatLng> = emptyList(),
    val subtitle: String = "",
    val stats: List<Stat> = emptyList(),
    val profile: ProfileUi = ProfileUi(emptyList(), emptyList(), null, null),
    val prTime: String? = null,
    val prDate: String? = null,
    val prActivityId: Long? = null,
    val best: List<EffortHistoryRow> = emptyList(),
    val recent: List<EffortHistoryRow> = emptyList(),
    val chart: List<EffortPoint> = emptyList(),
    val rematching: Boolean = false,
    val deleted: Boolean = false,
    val message: String? = null,
)

/** A segment: its map, profile, stats and your efforts, with star, rename, re-match and delete. */
@HiltViewModel
class SegmentDetailViewModel internal constructor(
    private val segmentId: Long,
    private val segments: SegmentRepository,
    preferences: PreferencesRepository,
    private val rematchScheduler: SegmentRematchScheduler,
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        segments: SegmentRepository,
        preferences: PreferencesRepository,
        rematchScheduler: SegmentRematchScheduler,
    ) : this(checkNotNull(savedStateHandle.get<Long>(ARG_SEGMENT_ID)) { "segmentId missing" }, segments, preferences, rematchScheduler)

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<SegmentDetailUiState> = combine(
        segments.observeSegment(segmentId),
        segments.observeEffortsOfSegment(segmentId),
        preferences.preferences,
        local,
    ) { segment, efforts, prefs, l ->
        when {
            l.deleted -> SegmentDetailUiState(isLoading = false, deleted = true)
            segment == null -> SegmentDetailUiState(isLoading = false, notFound = true)
            else -> build(segment, efforts, prefs.unitSystem, Locale.getDefault()).copy(rematching = l.rematching, message = l.message)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SegmentDetailUiState())

    fun toggleStar() {
        val starred = uiState.value.starred
        viewModelScope.launch { segments.setStarred(segmentId, !starred) }
    }

    fun rename(name: String) {
        viewModelScope.launch {
            if (!segments.rename(segmentId, name)) local.update { it.copy(message = "A segment needs a name") }
        }
    }

    /** Matches the segment against every activity again (in the background). */
    fun rematch() {
        rematchScheduler.rematch(segmentId)
        local.update { it.copy(message = "Checking every activity for this segment…") }
    }

    fun delete() {
        viewModelScope.launch {
            try {
                segments.delete(segmentId)
                local.update { it.copy(deleted = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(message = "Could not delete the segment") }
            }
        }
    }

    fun messageShown() = local.update { it.copy(message = null) }

    private data class Local(val rematching: Boolean = false, val deleted: Boolean = false, val message: String? = null)

    internal companion object {
        const val ARG_SEGMENT_ID = "segmentId"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val BEST_SHOWN = 3

        fun build(segment: Segment, efforts: List<SegmentEffort>, units: UnitSystem, locale: Locale): SegmentDetailUiState {
            val formatter = UnitFormatter(units, locale)
            val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
            fun row(e: SegmentEffort) = EffortHistoryRow(
                id = e.id,
                activityId = e.activityId,
                date = e.startTime.atZone(e.timeZone).toLocalDate().format(dates),
                activityTitle = e.activityTitle,
                time = SegmentFormat.clock(e.elapsed.inWholeSeconds),
                pace = (if (segment.sport.isRideLike) formatter.speed(e.averageSpeedMps) else formatter.pace(e.distanceMeters, e.elapsed.inWholeSeconds))
                    ?.let { "${it.value} ${it.unit.orEmpty()}".trim() },
                heartRate = e.averageHeartRate?.let { "${it.toInt()} bpm" },
                rank = e.medalRank,
                yearly = e.isYearlyMedal,
                isPersonalRecord = e.isPersonalRecord,
            )
            val fastest = efforts.sortedWith(compareBy<SegmentEffort> { it.elapsed }.thenBy { it.startTime })
            val pr = fastest.firstOrNull()
            val distance = formatter.distance(segment.distanceMeters)
            val gain = formatter.elevation(segment.elevationGainMeters)
            val distances = cumulative(segment.points)
            return SegmentDetailUiState(
                isLoading = false,
                id = segment.id,
                name = segment.name,
                sport = segment.sport,
                starred = segment.starred,
                points = segment.points,
                subtitle = SegmentFormat.lengthAndGrade(segment.distanceMeters, segment.averageGradePercent, formatter),
                stats = buildList {
                    add(Stat("Distance", distance.value, distance.unit))
                    add(Stat("Avg Grade", SegmentFormat.grade(segment.averageGradePercent)))
                    if (segment.elevations != null) add(Stat("Elev Gain", gain.value, gain.unit))
                    add(Stat("Efforts", efforts.size.toString()))
                },
                profile = segment.elevations?.let { ProfileUi.of(distances, it, formatter) } ?: ProfileUi(emptyList(), emptyList(), null, null),
                prTime = pr?.let { SegmentFormat.clock(it.elapsed.inWholeSeconds) },
                prDate = pr?.let { it.startTime.atZone(it.timeZone).toLocalDate().format(dates) },
                prActivityId = pr?.activityId,
                best = fastest.take(BEST_SHOWN).map(::row),
                recent = efforts.sortedByDescending { it.startTime }.map(::row),
                chart = efforts.sortedBy { it.startTime }.map { e ->
                    EffortPoint(e.startTime.atZone(e.timeZone).toLocalDate().toEpochDay(), e.elapsed.inWholeSeconds, e.id == pr?.id)
                },
            )
        }

        private fun cumulative(points: List<LatLng>): List<Double> {
            val out = ArrayList<Double>(points.size)
            var total = 0.0
            points.forEachIndexed { i, p ->
                if (i > 0) total += org.openactivity.geo.Spherical.distance(points[i - 1], p)
                out += total
            }
            return out
        }
    }
}
