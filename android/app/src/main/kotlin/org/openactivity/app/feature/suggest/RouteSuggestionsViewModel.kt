// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.suggest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.data.SuggestionSettings
import org.openactivity.app.data.SuggestionSettingsState
import org.openactivity.app.feature.explore.RouteEstimates
import org.openactivity.app.format.UnitFormatter
import org.openactivity.app.model.toSportType
import org.openactivity.app.ui.common.toStat
import org.openactivity.core.designsystem.component.Stat
import org.openactivity.core.location.CurrentLocationProvider
import org.openactivity.core.location.CurrentLocationResult
import org.openactivity.core.location.LocationAvailability
import org.openactivity.core.routing.RouteSuggestion
import org.openactivity.core.routing.RouteSuggestions
import org.openactivity.core.routing.SuggestionProgress
import org.openactivity.core.routing.SuggestionRequest
import org.openactivity.core.routing.SuggestionSport
import org.openactivity.core.routing.SurfaceMix
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import java.util.Locale
import javax.inject.Inject

/** What the suggestions section shows. */
sealed interface SuggestionsStatus {
    /** The athlete turned online route suggestions off. */
    data object Disabled : SuggestionsStatus

    /** On, but the intro saying the position is sent has not been accepted yet: nothing is requested. */
    data object AwaitingConsent : SuggestionsStatus

    /** Location is not allowed, switched off, or no fix came ([reason] is null then). */
    data class NoLocation(val reason: LocationAvailability?) : SuggestionsStatus

    /** Finding the position or routing the first loops. */
    data object Loading : SuggestionsStatus

    /** Loops (possibly still adding more: [SuggestionsUiState.isLoadingMore]). */
    data object Ready : SuggestionsStatus

    /** Routed fine but no loop passed the checks around here. */
    data object NoneFound : SuggestionsStatus

    /** The services could not be reached; cards hold cached suggestions, if any. */
    data object Offline : SuggestionsStatus
}

/** One suggestion card. */
data class SuggestionCardUi(
    val id: String,
    val name: String,
    val sport: SportType,
    /** "Run · 5 km loop · NE". */
    val subtitle: String,
    val points: List<LatLng>,
    /** Distance, Elev Gain and Est. Time. */
    val stats: List<Stat>,
    /** "Paved 96%", "Mixed · 30% dirt", "Checking surface…", or null when unknown. */
    val surface: String? = null,
)

/** Elevation filter (strava: Flat / Hilly / Any), by climb per kilometre. */
enum class ElevationFilter(val label: String) {
    ANY("Any"),
    FLAT("Flat"),
    HILLY("Hilly"),
    ;

    companion object {
        /** At most this climb per km counts as flat. */
        const val FLAT_MAX_M_PER_KM: Double = 10.0

        /** At least this climb per km counts as hilly. */
        const val HILLY_MIN_M_PER_KM: Double = 15.0
    }
}

/** Surface filter (strava: Paved / Dirt / Any), from OpenStreetMap surface tags along the loop. */
enum class SurfaceFilter(val label: String) {
    ANY("Any"),
    PAVED("Paved"),
    DIRT("Dirt"),
    ;

    companion object {
        /** A loop at least this paved counts as paved. */
        const val PAVED_MIN_SHARE: Double = 0.85

        /** A loop at least this unpaved counts as dirt. */
        const val DIRT_MIN_SHARE: Double = 0.4
    }
}

/**
 * The filters on the Maps sheet.
 *
 * @property lengthMeters the loop length range, or null for the sport's default lengths.
 */
data class SuggestionFilters(
    val lengthMeters: ClosedFloatingPointRange<Double>? = null,
    val elevation: ElevationFilter = ElevationFilter.ANY,
    val surface: SurfaceFilter = SurfaceFilter.ANY,
) {
    val isDefault: Boolean get() = lengthMeters == null && elevation == ElevationFilter.ANY && surface == SurfaceFilter.ANY
}

/**
 * State of the route suggestions on Home and Maps.
 *
 * @property showIntro the one-time explanation that suggestions send your position to routing services.
 * @property hiddenByFilters loops found but not shown because they do not match [filters].
 */
data class SuggestionsUiState(
    val status: SuggestionsStatus = SuggestionsStatus.Loading,
    val sport: SuggestionSport = SuggestionSport.RUN,
    val filters: SuggestionFilters = SuggestionFilters(),
    val cards: List<SuggestionCardUi> = emptyList(),
    val isLoadingMore: Boolean = false,
    val showIntro: Boolean = false,
    val hiddenByFilters: Int = 0,
    /** Lengths are labelled in the athlete's units. */
    val units: UnitSystem = UnitSystem.METRIC,
) {
    /** The loop lengths asked for: the filter's range, or the sport's default lengths. */
    val lengthRange: ClosedFloatingPointRange<Double>
        get() = filters.lengthMeters ?: sport.defaultDistancesMeters.let { it.first()..it.last() }

    /** The range the length slider offers for [sport]. */
    val lengthBounds: ClosedFloatingPointRange<Double> get() = RouteSuggestionsViewModel.lengthBounds(sport)

    /** True while (re)loading, for pull-to-refresh. */
    val isRefreshing: Boolean get() = status == SuggestionsStatus.Loading || isLoadingMore
}

/**
 * Route suggestions near the athlete's current position, for Home's carousel and the Maps sheet. Routes come
 * from [RouteSuggestions] (FOSSGIS OSRM + Open-Meteo, cached a day); nothing runs while suggestions are
 * turned off or the screen is not visible.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RouteSuggestionsViewModel @Inject constructor(
    private val suggestions: RouteSuggestions,
    private val location: CurrentLocationProvider,
    private val settings: SuggestionSettings,
    preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private data class Selection(
        val sport: SuggestionSport? = null,
        val filters: SuggestionFilters = SuggestionFilters(),
        val refresh: Int = 0,
        val retry: Int = 0,
    )

    private sealed interface Load {
        data object Disabled : Load
        data object AwaitingConsent : Load
        data object Locating : Load
        data class NoLocation(val reason: LocationAvailability?) : Load
        data class Progress(val progress: SuggestionProgress) : Load
        data object Failed : Load
    }

    private val selection = MutableStateFlow(Selection())

    private val defaultSport = preferencesRepository.preferences
        .map { SuggestionSport.from(it.lastSport.toSportType()) }
        .distinctUntilChanged()

    /** Off, on but waiting for the intro to be accepted, or on. */
    private val access = settings.state
        .map { if (!it.enabled) Load.Disabled else if (!it.introSeen) Load.AwaitingConsent else null }
        .distinctUntilChanged()

    /** The [Selection.refresh] count whose load already bypassed the cache. */
    private var consumedRefresh = 0

    // Elevation and surface only filter what was routed, so changing them does not route again.
    private val load: Flow<Load> = combine(access, defaultSport, selection) { blocked, sport, sel ->
        Triple(blocked, sel.sport ?: sport, sel.copy(filters = SuggestionFilters(lengthMeters = sel.filters.lengthMeters)))
    }
        .distinctUntilChanged()
        .flatMapLatest { (blocked, sport, sel) -> if (blocked != null) flowOf(blocked) else load(sport, sel) }

    val uiState: StateFlow<SuggestionsUiState> = combine(
        load,
        settings.state,
        preferencesRepository.preferences,
        defaultSport,
        selection,
    ) { load, settingsState, preferences, defaultSport, sel ->
        val formatter = UnitFormatter(preferences.unitSystem, Locale.getDefault())
        toUi(load, settingsState, sel.sport ?: defaultSport, sel.filters, formatter, preferences.unitSystem)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SuggestionsUiState())

    /** Suggests loops for [sport] (Maps sport chips); the length range is per sport, so it resets. */
    fun selectSport(sport: SuggestionSport) = selection.update { it.copy(sport = sport, filters = it.filters.copy(lengthMeters = null)) }

    /** Routes loops between the two lengths, or the sport's default lengths for null. */
    fun setLength(range: ClosedFloatingPointRange<Double>?) = selection.update { it.copy(filters = it.filters.copy(lengthMeters = range)) }

    fun setElevation(filter: ElevationFilter) = selection.update { it.copy(filters = it.filters.copy(elevation = filter)) }

    fun setSurface(filter: SurfaceFilter) = selection.update { it.copy(filters = it.filters.copy(surface = filter)) }

    fun clearFilters() = selection.update { it.copy(filters = SuggestionFilters()) }

    /** Routes new loops with other start bearings (pull-to-refresh, refresh icon). */
    fun refresh() = selection.update { it.copy(refresh = it.refresh + 1) }

    /** Tries again, from the cache when it is fresh (after granting location, or coming back online). */
    fun retry() = selection.update { it.copy(retry = it.retry + 1) }

    /** Back on screen: a location prompt may have been answered in system settings. */
    fun onResume() {
        if (uiState.value.status is SuggestionsStatus.NoLocation) retry()
    }

    fun dismissIntro() {
        viewModelScope.launch { settings.markIntroSeen() }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setEnabled(enabled) }
    }

    private fun load(sport: SuggestionSport, sel: Selection): Flow<Load> = flow {
        emit(Load.Locating)
        when (val here = location.current()) {
            is CurrentLocationResult.Unavailable -> emit(Load.NoLocation(here.reason))
            CurrentLocationResult.NoFix -> emit(Load.NoLocation(null))
            is CurrentLocationResult.Found -> {
                val distances = sel.filters.lengthMeters?.let(::targets) ?: sport.defaultDistancesMeters
                // Only the load a refresh tap starts routes anew; later loads (chips, retry, resume) use the cache.
                val bypassCache = sel.refresh != consumedRefresh
                consumedRefresh = sel.refresh
                suggestions.suggest(SuggestionRequest(here.position, sport, distances), sel.refresh, bypassCache)
                    .collect { emit(Load.Progress(it)) }
            }
        }
    }.catch { e -> if (e is RuntimeException) emit(Load.Failed) else throw e }

    internal companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        private fun toUi(
            load: Load,
            settings: SuggestionSettingsState,
            sport: SuggestionSport,
            filters: SuggestionFilters,
            formatter: UnitFormatter,
            units: UnitSystem,
        ): SuggestionsUiState {
            val base = SuggestionsUiState(sport = sport, filters = filters, showIntro = settings.enabled && !settings.introSeen, units = units)
            return when (load) {
                Load.Disabled -> base.copy(status = SuggestionsStatus.Disabled, showIntro = false)
                Load.AwaitingConsent -> base.copy(status = SuggestionsStatus.AwaitingConsent, showIntro = true)
                Load.Locating -> base.copy(status = SuggestionsStatus.Loading)
                is Load.NoLocation -> base.copy(status = SuggestionsStatus.NoLocation(load.reason))
                Load.Failed -> base.copy(status = SuggestionsStatus.Offline)
                is Load.Progress -> {
                    val p = load.progress
                    val shown = p.routes.filter { matches(it, filters) }
                    val cards = shown.map { card(it, formatter, units, surfacePending = !p.done) }
                    val status = when {
                        p.failed -> SuggestionsStatus.Offline
                        cards.isNotEmpty() -> SuggestionsStatus.Ready
                        p.done -> SuggestionsStatus.NoneFound
                        else -> SuggestionsStatus.Loading
                    }
                    base.copy(
                        status = status,
                        cards = cards,
                        isLoadingMore = !p.done && p.routes.isNotEmpty(),
                        hiddenByFilters = p.routes.size - shown.size,
                    )
                }
            }
        }

        /** Slider range per sport, in metres. */
        fun lengthBounds(sport: SuggestionSport): ClosedFloatingPointRange<Double> = when (sport) {
            SuggestionSport.RUN -> 1_000.0..30_000.0
            SuggestionSport.RIDE -> 5_000.0..150_000.0
            SuggestionSport.WALK -> 1_000.0..20_000.0
            SuggestionSport.HIKE -> 2_000.0..30_000.0
        }

        /** Lengths to route for [range]: its ends and middle, rounded to 500 m, fewer when it is narrow. */
        fun targets(range: ClosedFloatingPointRange<Double>): List<Double> {
            fun round(m: Double) = (Math.round(m / 500.0) * 500.0).coerceAtLeast(500.0)
            return listOf(range.start, (range.start + range.endInclusive) / 2, range.endInclusive).map(::round).distinct()
        }

        /**
         * Whether [route] passes [filters]. Length allows the routing tolerance; a loop whose climb or surface is
         * not known (yet) only passes "Any".
         */
        fun matches(route: RouteSuggestion, filters: SuggestionFilters): Boolean {
            filters.lengthMeters?.let { range ->
                if (route.distanceMeters < range.start * (1 - LENGTH_SLACK) || route.distanceMeters > range.endInclusive * (1 + LENGTH_SLACK)) return false
            }
            if (filters.elevation != ElevationFilter.ANY) {
                val gain = route.elevationGainMeters ?: return false
                val perKm = gain / (route.distanceMeters / 1000.0)
                when (filters.elevation) {
                    ElevationFilter.FLAT -> if (perKm > ElevationFilter.FLAT_MAX_M_PER_KM) return false
                    ElevationFilter.HILLY -> if (perKm < ElevationFilter.HILLY_MIN_M_PER_KM) return false
                    ElevationFilter.ANY -> Unit
                }
            }
            if (filters.surface != SurfaceFilter.ANY) {
                val surface = route.surface ?: return false
                when (filters.surface) {
                    SurfaceFilter.PAVED -> if (surface.pavedShare < SurfaceFilter.PAVED_MIN_SHARE) return false
                    SurfaceFilter.DIRT -> if (surface.unpavedShare < SurfaceFilter.DIRT_MIN_SHARE) return false
                    SurfaceFilter.ANY -> Unit
                }
            }
            return true
        }

        /** "Paved 96%", "Mixed · 30% dirt", "Mostly dirt · 70%". */
        fun surfaceLabel(surface: SurfaceMix): String {
            val paved = Math.round(surface.pavedShare * 100)
            val dirt = Math.round(surface.unpavedShare * 100)
            return when {
                surface.pavedShare >= SurfaceFilter.PAVED_MIN_SHARE -> "Paved $paved%"
                surface.unpavedShare >= MOSTLY_DIRT_SHARE -> "Mostly dirt · $dirt%"
                dirt > 0 -> "Mixed · $dirt% dirt"
                else -> "Paved $paved% · rest unknown"
            }
        }

        private const val LENGTH_SLACK = 0.15
        private const val MOSTLY_DIRT_SHARE = 0.6

        /** A card in the athlete's units: distance, climb (a dash when unknown) and estimated time. */
        fun card(route: RouteSuggestion, formatter: UnitFormatter, units: UnitSystem = UnitSystem.METRIC, surfacePending: Boolean = false): SuggestionCardUi {
            val sport = route.sport.sportType
            val gain = route.elevationGainMeters
            return SuggestionCardUi(
                id = route.id,
                name = route.name,
                sport = sport,
                subtitle = listOf(route.sport.label, "${formatLength(route.targetDistanceMeters, units)} loop", org.openactivity.core.routing.LoopGeometry.compass(route.bearingDegrees)).joinToString(" · "),
                points = route.points,
                stats = listOf(
                    formatter.distance(route.distanceMeters).toStat("Distance"),
                    gain?.let(formatter::elevation).toStat("Elev Gain"),
                    Stat("Est. Time", UnitFormatter.compactDuration(RouteEstimates.estimatedSeconds(route.distanceMeters, gain ?: 0.0, sport))),
                ),
                surface = route.surface?.let(::surfaceLabel) ?: if (surfacePending) "Checking surface…" else null,
            )
        }

        /** "5 km", "2.5 km"; "3.1 mi" in imperial units. */
        fun formatLength(meters: Double, units: UnitSystem): String = when (units) {
            UnitSystem.METRIC -> "${formatKm(meters)} km"
            UnitSystem.IMPERIAL -> {
                val miles = meters / METERS_PER_MILE
                val text = String.format(Locale.ROOT, "%.1f", miles).removeSuffix(".0")
                "$text mi"
            }
        }

        private const val METERS_PER_MILE = 1_609.344

        /** "5", "2.5". */
        fun formatKm(meters: Double): String {
            val km = meters / 1000.0
            return if (km % 1.0 == 0.0) km.toLong().toString() else String.format(Locale.ROOT, "%.1f", km)
        }
    }
}
