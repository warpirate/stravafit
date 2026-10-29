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
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import java.util.Locale
import javax.inject.Inject

/** What the suggestions section shows. */
sealed interface SuggestionsStatus {
    /** The athlete turned online route suggestions off. */
    data object Disabled : SuggestionsStatus

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
)

/**
 * State of the route suggestions on Home and Maps.
 *
 * @property distanceFilter the chosen loop length, or null for every default length of [sport].
 * @property showIntro the one-time explanation that suggestions send your position to routing services.
 */
data class SuggestionsUiState(
    val status: SuggestionsStatus = SuggestionsStatus.Loading,
    val sport: SuggestionSport = SuggestionSport.RUN,
    val distanceFilter: Double? = null,
    val cards: List<SuggestionCardUi> = emptyList(),
    val isLoadingMore: Boolean = false,
    val showIntro: Boolean = false,
) {
    /** Lengths offered as chips for [sport]. */
    val distanceOptions: List<Double> get() = sport.defaultDistancesMeters

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

    private data class Selection(val sport: SuggestionSport? = null, val distance: Double? = null, val refresh: Int = 0, val retry: Int = 0)

    private sealed interface Load {
        data object Disabled : Load
        data object Locating : Load
        data class NoLocation(val reason: LocationAvailability?) : Load
        data class Progress(val progress: SuggestionProgress) : Load
        data object Failed : Load
    }

    private val selection = MutableStateFlow(Selection())

    private val defaultSport = preferencesRepository.preferences
        .map { SuggestionSport.from(it.lastSport.toSportType()) }
        .distinctUntilChanged()

    private val enabled = settings.state.map { it.enabled }.distinctUntilChanged()

    private val load: Flow<Load> = combine(enabled, defaultSport, selection) { on, sport, sel -> Triple(on, sel.sport ?: sport, sel) }
        .distinctUntilChanged()
        .flatMapLatest { (on, sport, sel) -> if (!on) flowOf(Load.Disabled) else load(sport, sel) }

    val uiState: StateFlow<SuggestionsUiState> = combine(
        load,
        settings.state,
        preferencesRepository.preferences,
        defaultSport,
        selection,
    ) { load, settingsState, preferences, defaultSport, sel ->
        val formatter = UnitFormatter(preferences.unitSystem, Locale.getDefault())
        toUi(load, settingsState, sel.sport ?: defaultSport, sel.distance, formatter)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SuggestionsUiState())

    /** Suggests loops for [sport] (Maps sport chips). */
    fun selectSport(sport: SuggestionSport) = selection.update { it.copy(sport = sport, distance = null) }

    /** Shows only loops of [meters], or every default length for null (Maps distance chips). */
    fun selectDistance(meters: Double?) = selection.update { it.copy(distance = meters) }

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
                val distances = sel.distance?.let(::listOf) ?: sport.defaultDistancesMeters
                suggestions.suggest(SuggestionRequest(here.position, sport, distances), sel.refresh).collect { emit(Load.Progress(it)) }
            }
        }
    }.catch { e -> if (e is RuntimeException) emit(Load.Failed) else throw e }

    internal companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        private fun toUi(load: Load, settings: SuggestionSettingsState, sport: SuggestionSport, distance: Double?, formatter: UnitFormatter): SuggestionsUiState {
            val base = SuggestionsUiState(sport = sport, distanceFilter = distance, showIntro = settings.enabled && !settings.introSeen)
            return when (load) {
                Load.Disabled -> base.copy(status = SuggestionsStatus.Disabled, showIntro = false)
                Load.Locating -> base.copy(status = SuggestionsStatus.Loading)
                is Load.NoLocation -> base.copy(status = SuggestionsStatus.NoLocation(load.reason))
                Load.Failed -> base.copy(status = SuggestionsStatus.Offline)
                is Load.Progress -> {
                    val p = load.progress
                    val cards = p.routes.map { card(it, formatter) }
                    val status = when {
                        p.failed -> SuggestionsStatus.Offline
                        cards.isNotEmpty() -> SuggestionsStatus.Ready
                        p.done -> SuggestionsStatus.NoneFound
                        else -> SuggestionsStatus.Loading
                    }
                    base.copy(status = status, cards = cards, isLoadingMore = !p.done && cards.isNotEmpty())
                }
            }
        }

        /** A card in the athlete's units: distance, climb (a dash when unknown) and estimated time. */
        fun card(route: RouteSuggestion, formatter: UnitFormatter): SuggestionCardUi {
            val sport = route.sport.sportType
            val gain = route.elevationGainMeters
            return SuggestionCardUi(
                id = route.id,
                name = route.name,
                sport = sport,
                subtitle = listOf(route.sport.label, "${formatKm(route.targetDistanceMeters)} km loop", org.openactivity.core.routing.LoopGeometry.compass(route.bearingDegrees)).joinToString(" · "),
                points = route.points,
                stats = listOf(
                    formatter.distance(route.distanceMeters).toStat("Distance"),
                    gain?.let(formatter::elevation).toStat("Elev Gain"),
                    Stat("Est. Time", UnitFormatter.compactDuration(RouteEstimates.estimatedSeconds(route.distanceMeters, gain ?: 0.0, sport))),
                ),
            )
        }

        /** "5", "2.5". */
        fun formatKm(meters: Double): String {
            val km = meters / 1000.0
            return if (km % 1.0 == 0.0) km.toLong().toString() else String.format(Locale.ROOT, "%.1f", km)
        }
    }
}
