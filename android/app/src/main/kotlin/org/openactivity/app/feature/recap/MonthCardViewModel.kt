// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.recap

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openactivity.app.format.UnitFormatter
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.core.data.repository.SettingsRepository
import routeexport.CardStyle
import routeexport.PeriodCard
import java.time.Clock
import java.time.YearMonth
import java.util.Locale
import javax.inject.Inject

/**
 * State of "Share this month".
 *
 * @property preview the rendered card, null while it renders.
 * @property activityCount activities in [month]; with none the card still renders ("No routes") but says so.
 * @property canGoNext false on the current month: there is nothing after it yet.
 */
data class MonthCardUiState(
    val month: YearMonth,
    val style: CardStyle = CardStyle.LIGHT,
    val preview: Bitmap? = null,
    val activityCount: Int = 0,
    val canGoNext: Boolean = false,
    val isSharing: Boolean = false,
)

/** The monthly card: the month's totals and route shapes, in light, dark or as a transparent sticker. */
@HiltViewModel
class MonthCardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    activities: ActivityRepository,
    settings: SettingsRepository,
    private val renderer: PeriodCardRenderer,
    private val clock: Clock,
) : ViewModel() {
    private val current = YearMonth.now(clock)
    private val selection = MutableStateFlow(
        Selection(
            month = run {
                val year = savedStateHandle.get<Int>(ARG_YEAR)
                val month = savedStateHandle.get<Int>(ARG_MONTH)
                if (year != null && month != null && month in 1..12) YearMonth.of(year, month).coerceAtMost(current) else current
            },
            style = CardStyle.LIGHT,
        ),
    )
    private val sharing = MutableStateFlow(false)
    private val effects = Channel<RecapEffect>(Channel.BUFFERED)
    val effect: Flow<RecapEffect> = effects.receiveAsFlow()

    private var card: PeriodCard? = null

    private data class Selection(val month: YearMonth, val style: CardStyle)

    private data class Rendered(val selection: Selection, val count: Int, val preview: Bitmap?)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val rendered: Flow<Rendered?> = combine(activities.observeActivities(), settings.settings, selection) { all, appSettings, sel ->
        val formatter = UnitFormatter(appSettings.units, Locale.getDefault())
        Triple(sel, RecapMath.inMonth(all, sel.month).size, PeriodCards.month(sel.month, all, formatter))
    }
        .distinctUntilChanged()
        .mapLatest { (sel, count, newCard) ->
            card = newCard
            val preview = try {
                renderer.preview(newCard, sel.style)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            Rendered(sel, count, preview)
        }

    val uiState: StateFlow<MonthCardUiState> = combine(selection, rendered, sharing) { sel, r, isSharing ->
        val ready = r?.takeIf { it.selection == sel }
        MonthCardUiState(
            month = sel.month,
            style = sel.style,
            preview = ready?.preview,
            activityCount = ready?.count ?: r?.count ?: 0,
            canGoNext = sel.month < current,
            isSharing = isSharing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MonthCardUiState(selection.value.month))

    fun previousMonth() = selection.update { it.copy(month = it.month.minusMonths(1)) }

    fun nextMonth() = selection.update { if (it.month < current) it.copy(month = it.month.plusMonths(1)) else it }

    fun selectStyle(style: CardStyle) = selection.update { it.copy(style = style) }

    /** Writes the full-size card and opens the share sheet. */
    fun share() {
        val toShare = card ?: return
        if (sharing.value) return
        sharing.value = true
        val sel = selection.value
        viewModelScope.launch {
            try {
                effects.send(RecapEffect.Share(renderer.export(toShare, sel.style, "${sel.month} month")))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                effects.send(RecapEffect.Message("Could not make the image. Try again."))
            } finally {
                sharing.value = false
            }
        }
    }

    companion object {
        const val ARG_YEAR = "year"
        const val ARG_MONTH = "month"
        private const val STOP_TIMEOUT_MS = 5_000L

        private fun YearMonth.coerceAtMost(max: YearMonth): YearMonth = if (this > max) max else this
    }
}
