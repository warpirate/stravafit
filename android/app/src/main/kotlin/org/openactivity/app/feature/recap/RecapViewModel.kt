// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.recap

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openactivity.app.feature.goals.GoalFormat
import org.openactivity.app.format.UnitFormatter
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.core.data.repository.GoalRepository
import org.openactivity.core.data.repository.SettingsRepository
import org.openactivity.core.designsystem.component.Stat
import org.openactivity.core.export.file.ExportedFile
import org.openactivity.model.SportType
import routeexport.CardStyle
import routeexport.PeriodCard
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/** One line comparing the week with the week before: "Distance 24.2 km, up 12% (21.6 km)". */
data class RecapComparison(val label: String, val now: String, val before: String, val changePercent: Int?)

/** The week's longest activity. */
data class RecapLongest(val id: Long, val title: String, val sport: SportType, val day: String, val size: String)

/** One weekly goal's result: "Weekly run goal", "32.4 km of 30 km". */
data class RecapGoal(val title: String, val result: String, val reached: Boolean)

/** State of the weekly recap screen. [card] is what the Share button draws. */
data class RecapUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val stats: List<Stat> = emptyList(),
    val comparisons: List<RecapComparison> = emptyList(),
    val note: String = "",
    val longest: RecapLongest? = null,
    val goals: List<RecapGoal> = emptyList(),
    val isEmpty: Boolean = false,
    val isSharing: Boolean = false,
    val card: PeriodCard? = null,
)

/** What the recap screen does once: open the share sheet, or say why it could not. */
sealed interface RecapEffect {
    data class Share(val file: ExportedFile) : RecapEffect

    data class Message(val text: String) : RecapEffect
}

/** The weekly recap of the week in [RecapRoute.weekStart], with "Share" drawing its card. */
@HiltViewModel
class RecapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    activities: ActivityRepository,
    goals: GoalRepository,
    settings: SettingsRepository,
    private val renderer: PeriodCardRenderer,
) : ViewModel() {
    // The route always names the week; a garbled link falls back to last (ISO) week rather than failing.
    private val weekStart: LocalDate = savedStateHandle.get<String>(ARG_WEEK_START)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: RecapMath.lastCompletedWeek(ZonedDateTime.now(), DayOfWeek.MONDAY)

    private val sharing = MutableStateFlow(false)
    private val effects = Channel<RecapEffect>(Channel.BUFFERED)
    val effect: Flow<RecapEffect> = effects.receiveAsFlow()

    val uiState: StateFlow<RecapUiState> = combine(
        activities.observeActivities(),
        goals.observeGoals(),
        settings.settings,
        sharing,
    ) { all, goalList, appSettings, isSharing ->
        val formatter = UnitFormatter(appSettings.units, Locale.getDefault())
        recapUi(RecapMath.recap(all, goalList, weekStart), formatter, Locale.getDefault()).copy(isSharing = isSharing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RecapUiState())

    /** Draws the recap card ([dark] follows the app theme) and opens the share sheet with it. */
    fun share(dark: Boolean) {
        val card = uiState.value.card ?: return
        if (sharing.value) return
        sharing.value = true
        viewModelScope.launch {
            try {
                val file = renderer.export(card, if (dark) CardStyle.DARK else CardStyle.LIGHT, "Weekly recap $weekStart")
                effects.send(RecapEffect.Share(file))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                effects.send(RecapEffect.Message("Could not make the recap image. Try again."))
            } finally {
                sharing.value = false
            }
        }
    }

    companion object {
        const val ARG_WEEK_START = "weekStart"
        private const val STOP_TIMEOUT_MS = 5_000L

        /** Maps a [WeeklyRecap] to what the screen shows. Pure, so it is unit-tested. */
        fun recapUi(recap: WeeklyRecap, formatter: UnitFormatter, locale: Locale): RecapUiState {
            val totals = recap.totals
            val before = recap.previous
            fun distance(m: Double) = formatter.distance(m).let { "${it.value} ${it.unit}" }
            fun time(s: Long) = UnitFormatter.compactDuration(s)
            val comparisons = buildList {
                if (totals.distanceMeters > 0 || before.distanceMeters > 0) {
                    add(RecapComparison("Distance", distance(totals.distanceMeters), distance(before.distanceMeters), recap.distanceChange))
                }
                add(RecapComparison("Time", time(totals.movingTime.inWholeSeconds), time(before.movingTime.inWholeSeconds), recap.timeChange))
                add(RecapComparison("Activities", formatter.count(totals.count), formatter.count(before.count), RecapMath.percentChange(totals.count.toDouble(), before.count.toDouble())))
            }
            val dayFormat = DateTimeFormatter.ofPattern("EEEE", locale)
            return RecapUiState(
                isLoading = false,
                title = PeriodCards.weekRange(recap.weekStart, locale),
                stats = PeriodCards.stats(totals, formatter).map { Stat(it.label, it.value, it.unit.ifEmpty { null }) },
                comparisons = comparisons,
                note = if (totals.count > 0) PeriodCards.comparison(recap) else "",
                longest = recap.longest?.let { a ->
                    RecapLongest(
                        id = a.id,
                        title = a.title,
                        sport = a.sport,
                        day = RecapMath.localDate(a).format(dayFormat),
                        size = if (a.distanceMeters > 0) distance(a.distanceMeters) else time(a.movingTime.inWholeSeconds),
                    )
                },
                goals = recap.goals.map { r ->
                    RecapGoal(
                        title = GoalFormat.title(r.goal),
                        result = "${GoalFormat.number(r.goal.metric, r.achieved, formatter)} of ${GoalFormat.amount(r.goal.metric, r.goal.target, formatter)}",
                        reached = r.reached,
                    )
                },
                isEmpty = totals.count == 0,
                card = if (totals.count > 0) PeriodCards.week(recap, formatter, locale) else null,
            )
        }
    }
}
