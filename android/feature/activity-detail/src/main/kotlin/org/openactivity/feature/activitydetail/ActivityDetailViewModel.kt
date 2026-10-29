// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openactivity.core.activityextras.ActivityExtras
import org.openactivity.core.activityextras.ActivityExtrasRepository
import org.openactivity.core.data.model.ActivityDetail
import org.openactivity.core.data.model.DefaultTitles
import org.openactivity.core.data.model.PersonalBest
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.core.data.repository.SettingsRepository
import org.openactivity.core.export.file.ExportFormat
import org.openactivity.core.export.file.ExportedFile
import org.openactivity.feature.activitydetail.chart.ChartMath
import org.openactivity.feature.activitydetail.export.ActivityFileExporter
import org.openactivity.feature.activitydetail.format.DetailFormat
import org.openactivity.model.LatLng
import org.openactivity.model.UnitSystem
import org.openactivity.recording.ActivityFlag
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Loads one activity with its streams, extras and personal bests and turns it into [DetailContent].
 * The heavy parts (chart samples, rows) are computed off the main thread whenever the activity, the
 * unit setting or the extras change.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActivityDetailViewModel internal constructor(
    private val activityId: Long,
    private val activities: ActivityRepository,
    private val extras: ActivityExtrasRepository,
    settings: SettingsRepository,
    private val fileExporter: ActivityFileExporter,
    private val clock: Clock,
) : ViewModel() {

    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        activities: ActivityRepository,
        extras: ActivityExtrasRepository,
        settings: SettingsRepository,
        fileExporter: ActivityFileExporter,
    ) : this(
        activityId = checkNotNull(savedStateHandle.get<Long>(ARG_ACTIVITY_ID)) { "activityId missing" },
        activities = activities,
        extras = extras,
        settings = settings,
        fileExporter = fileExporter,
        clock = Clock.systemDefaultZone(),
    )

    private val dialogs = MutableStateFlow(DetailDialogs())
    private val effectChannel = Channel<DetailEffect>(Channel.BUFFERED)

    /** One-shot effects; collect once from the screen. */
    val effects: Flow<DetailEffect> = effectChannel.receiveAsFlow()

    private val deleting = MutableStateFlow(false)

    private val content: Flow<DetailContent?> = activities.observeActivityDetail(activityId).flatMapLatest { detail ->
        if (detail == null) return@flatMapLatest flowOf(null)
        val bests = activities.observePersonalBests(setOf(detail.activity.sport))
        combine(
            settings.settings.map { it.units }.distinctUntilChanged(),
            extras.observe(activityId),
            bests,
        ) { units, ex, pbs -> buildContent(detail, units, ex, pbs, LocalDate.now(clock)) }
    }.flowOn(Dispatchers.Default)

    /** Current screen state. */
    val uiState: StateFlow<ActivityDetailUiState> = combine(content, dialogs, deleting) { c, d, gone ->
        when {
            // While leaving after a delete, keep a neutral state instead of flashing "not found".
            c == null && gone -> ActivityDetailUiState.Loading
            c == null -> ActivityDetailUiState.NotFound
            else -> ActivityDetailUiState.Content(c, d)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ActivityDetailUiState.Loading)

    /** Opens the delete confirmation. */
    fun requestDelete() = dialogs.update { it.copy(confirmDelete = true) }

    /** Closes the delete confirmation without deleting. */
    fun cancelDelete() = dialogs.update { it.copy(confirmDelete = false) }

    /** Deletes the activity, its streams, photos and extras. */
    fun confirmDelete() {
        if (deleting.value) return
        deleting.value = true
        dialogs.update { it.copy(confirmDelete = false) }
        viewModelScope.launch {
            try {
                activities.delete(activityId)
                extras.deleteAll(activityId)
                effectChannel.send(DetailEffect.Deleted)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                deleting.value = false
                effectChannel.send(DetailEffect.Message("Could not delete the activity: ${e.message ?: e.javaClass.simpleName}"))
            }
        }
    }

    /** Opens the export-file sheet with the formats this activity supports. */
    fun openExportSheet() {
        viewModelScope.launch {
            val options = fileExporter.options(activityId)
            if (options == null) {
                effectChannel.send(DetailEffect.Message("The activity no longer exists"))
            } else {
                dialogs.update { it.copy(exportFormats = options) }
            }
        }
    }

    /** Closes the export-file sheet. */
    fun closeExportSheet() = dialogs.update { it.copy(exportFormats = null) }

    /** Writes the activity as [format], then asks where to save it or opens the share sheet. */
    fun exportFile(format: ExportFormat, share: Boolean) {
        if (dialogs.value.exporting) return
        dialogs.update { it.copy(exporting = true) }
        viewModelScope.launch {
            try {
                val file = fileExporter.export(activityId, format)
                dialogs.update { it.copy(exportFormats = null) }
                effectChannel.send(if (share) DetailEffect.ShareFile(file) else DetailEffect.SaveDocument(file))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                effectChannel.send(DetailEffect.Message("Export failed: ${e.message ?: e.javaClass.simpleName}"))
            } finally {
                dialogs.update { it.copy(exporting = false) }
            }
        }
    }

    /** Copies an exported [file] to the document the user created. */
    fun writeDocument(file: ExportedFile, destination: Uri) {
        viewModelScope.launch {
            try {
                fileExporter.writeTo(file, destination)
                effectChannel.send(DetailEffect.Message("Saved ${file.displayName}"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                effectChannel.send(DetailEffect.Message("Could not save the file: ${e.message ?: e.javaClass.simpleName}"))
            }
        }
    }

    /** Reports that no app could receive a shared file. */
    fun onShareUnavailable() {
        effectChannel.trySend(DetailEffect.Message("No app can receive this file"))
    }

    internal companion object {
        const val ARG_ACTIVITY_ID = "activityId"
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        fun buildContent(
            detail: ActivityDetail,
            units: UnitSystem,
            extras: ActivityExtras,
            bests: List<PersonalBest>,
            today: LocalDate,
        ): DetailContent {
            val a = detail.activity
            val moving = detail.points.filterNot { it.paused }
            val hasElevation = detail.points.count { it.elevationMeters != null } >= 2
            val notes = buildList {
                DetailRows.elevationSource(a.altitudeSource)?.takeIf { hasElevation }?.let(::add)
                if (ActivityFlag.MOCK_LOCATION in a.flags) add("Recorded with simulated locations; kept out of records")
                if (ActivityFlag.SIGNIFICANT_GPS_GAP in a.flags) add("GPS dropped out for a while; the gap was bridged in a straight line")
                if (ActivityFlag.RECOVERED_AFTER_INTERRUPTION in a.flags) add("Recording was interrupted and recovered")
            }
            return DetailContent(
                id = a.id,
                title = a.title,
                sport = a.sport,
                sportName = DefaultTitles.sportName(a.sport),
                startedAt = DetailFormat.startedAt(a.startTime, a.timeZone, today),
                description = a.description,
                visibility = a.visibility,
                perceivedExertion = a.perceivedExertion,
                muted = extras.muted,
                photos = extras.photos,
                units = units,
                stats = DetailRows.stats(a, units, hasElevation),
                route = moving.map { LatLng(it.latitude, it.longitude) },
                routeSpeeds = moving.map { it.speedMps },
                routeDistances = moving.map { it.distanceMeters - (detail.points.firstOrNull()?.distanceMeters ?: 0.0) },
                chart = ChartMath.build(detail.points, a.sport),
                splits = DetailRows.splits(detail, units),
                splitUnit = if (units == UnitSystem.METRIC) "Km" else "Mi",
                laps = DetailRows.laps(detail, units),
                efforts = DetailRows.efforts(detail, bests, units),
                showEfforts = DetailRows.hasEfforts(a.sport),
                notes = notes,
            )
        }
    }
}
