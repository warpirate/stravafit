// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.segments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.data.segments.SegmentRematchScheduler
import org.openactivity.app.format.UnitFormatter
import org.openactivity.core.data.model.ActivityPoint
import org.openactivity.core.data.model.SegmentRules
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.core.data.repository.EffortRanking
import org.openactivity.core.data.repository.SegmentMath
import org.openactivity.core.data.repository.SegmentRepository
import org.openactivity.core.designsystem.component.Stat
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import java.util.Locale
import javax.inject.Inject

/**
 * State of "Create segment".
 *
 * @property route the activity's track (moving points), drawn muted.
 * @property highlight the chosen range, drawn in orange.
 * @property range the chosen range as fractions of the activity's distance.
 * @property stats distance, elevation gain and average grade of the range.
 * @property tooShort the range is shorter than [SegmentRules.MIN_LENGTH_METERS]; [minLength] says how long.
 */
data class CreateSegmentUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val activityTitle: String = "",
    val route: List<LatLng> = emptyList(),
    val highlight: List<LatLng> = emptyList(),
    val profile: ProfileUi = ProfileUi(emptyList(), emptyList(), null, null),
    val range: ClosedFloatingPointRange<Float> = 0f..1f,
    val stats: List<Stat> = emptyList(),
    val name: String = "",
    val sport: SportType = SportType.RUN,
    val sportOptions: List<SportType> = emptyList(),
    val tooShort: Boolean = false,
    val minLength: String = "",
    val saving: Boolean = false,
    val error: String? = null,
) {
    /** The Save button is on. */
    val canSave: Boolean get() = !isLoading && !notFound && !tooShort && !saving && name.isNotBlank()
}

/** One-shot effects of the create screen. */
sealed interface CreateSegmentEffect {
    /** Saved as segment [segmentId]; open it. */
    data class Created(val segmentId: Long) : CreateSegmentEffect
}

/**
 * Cuts a segment out of an activity: two handles on its distance (over the elevation profile) choose the
 * range, the map highlights it, and saving stores it and matches it against every activity in the
 * background.
 */
@HiltViewModel
class CreateSegmentViewModel internal constructor(
    private val activityId: Long,
    private val activities: ActivityRepository,
    private val segments: SegmentRepository,
    private val preferences: PreferencesRepository,
    private val rematch: SegmentRematchScheduler,
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        activities: ActivityRepository,
        segments: SegmentRepository,
        preferences: PreferencesRepository,
        rematch: SegmentRematchScheduler,
    ) : this(checkNotNull(savedStateHandle.get<Long>(ARG_ACTIVITY_ID)) { "activityId missing" }, activities, segments, preferences, rematch)

    private val _state = MutableStateFlow(CreateSegmentUiState())
    val uiState: StateFlow<CreateSegmentUiState> = _state.asStateFlow()

    private val effects = Channel<CreateSegmentEffect>(Channel.BUFFERED)
    val effect: Flow<CreateSegmentEffect> = effects.receiveAsFlow()

    private var points: List<ActivityPoint> = emptyList()
    private var distances: DoubleArray = DoubleArray(0)
    private var formatter = UnitFormatter(UnitSystem.METRIC, Locale.getDefault())

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val detail = activities.observeActivityDetail(activityId).first()
        val units = preferences.preferences.first().unitSystem
        formatter = UnitFormatter(units, Locale.getDefault())
        if (detail == null || detail.points.size < 2) {
            _state.update { it.copy(isLoading = false, notFound = true) }
            return
        }
        points = detail.points
        val origin = points.first().distanceMeters
        distances = DoubleArray(points.size) { points[it].distanceMeters - origin }
        val profile = withContext(Dispatchers.Default) {
            ProfileUi.of(distances.toList(), points.map { it.elevationMeters }, formatter)
        }
        val sport = detail.activity.sport
        val min = formatter.distance(SegmentRules.MIN_LENGTH_METERS)
        _state.update {
            it.copy(
                isLoading = false,
                activityTitle = detail.activity.title,
                route = points.filterNot { p -> p.paused }.map { p -> LatLng(p.latitude, p.longitude) },
                profile = profile,
                sport = sport,
                sportOptions = EffortRanking.poolOf(sport).sortedBy { s -> s.ordinal },
                minLength = "${min.value} ${min.unit}",
            )
        }
        // Start on the middle third: most activities start and finish at home.
        setRange(1f / 3f, 2f / 3f)
    }

    /** Moves the handles to [start]..[end] (fractions of the activity's distance). */
    fun setRange(start: Float, end: Float) {
        if (points.size < 2) return
        val lo = minOf(start, end).coerceIn(0f, 1f)
        val hi = maxOf(start, end).coerceIn(0f, 1f)
        val draft = SegmentMath.draft(points, indexAt(lo), indexAt(hi))
        val gain = formatter.elevation(draft.elevationGainMeters)
        val distance = formatter.distance(draft.distanceMeters)
        _state.update {
            it.copy(
                range = lo..hi,
                highlight = draft.points,
                stats = buildList {
                    add(Stat("Distance", distance.value, distance.unit))
                    if (draft.hasElevation) {
                        add(Stat("Elev Gain", gain.value, gain.unit))
                        add(Stat("Avg Grade", SegmentFormat.grade(draft.averageGradePercent)))
                    }
                },
                tooShort = !draft.isLongEnough,
                error = null,
            )
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name.take(SegmentRules.MAX_NAME_LENGTH), error = null) }

    fun setSport(sport: SportType) = _state.update { it.copy(sport = sport) }

    /** Saves the segment, starts matching it against every activity, then opens it. */
    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val id = segments.create(activityId, indexAt(s.range.start), indexAt(s.range.endInclusive), s.name, s.sport)
                rematch.rematch(id)
                effects.send(CreateSegmentEffect.Created(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.message ?: "Could not save the segment") }
            }
        }
    }

    /** The point at [fraction] of the activity's distance (the first one at or past it). */
    internal fun indexAt(fraction: Float): Int {
        val target = fraction * distances.last()
        var lo = 0
        var hi = distances.lastIndex
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (distances[mid] < target) lo = mid + 1 else hi = mid
        }
        return lo
    }

    internal companion object {
        const val ARG_ACTIVITY_ID = "activityId"
    }
}
