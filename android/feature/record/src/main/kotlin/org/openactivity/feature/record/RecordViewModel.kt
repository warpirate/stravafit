// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openactivity.core.data.repository.RouteRepository
import org.openactivity.core.data.repository.SettingsRepository
import org.openactivity.core.datastore.AppSettings
import org.openactivity.core.recordingservice.InterruptedRecording
import org.openactivity.core.recordingservice.RecordingController
import org.openactivity.core.recordingservice.RecordingUiSnapshot
import org.openactivity.core.recordingservice.audio.SpeechStatus
import org.openactivity.core.recordingservice.di.RecordingScope
import org.openactivity.feature.record.data.RecordPreferences
import org.openactivity.feature.record.data.RecordPreferencesRepository
import org.openactivity.feature.record.format.RecordFormat
import org.openactivity.feature.record.location.PreStartLocation
import org.openactivity.feature.record.system.BatteryOptimization
import org.openactivity.feature.record.system.SpeechAvailability
import org.openactivity.model.SportType
import javax.inject.Inject

/**
 * The Record screen: pre-start (sport, GPS, route, quick settings), the optional countdown, and the live
 * recording through [RecordingController]. All state comes out of [uiState]; one-shot effects out of [events].
 *
 * Location is only watched while the screen is collected, a GPS sport is selected and nothing is being
 * recorded (the recording service has its own subscription then).
 *
 * Discard can be undone for [UNDO_WINDOW_MS] (record detail 4.4): the recording is hidden at once but only
 * thrown away when the window closes. That last step runs in the application-lifetime `appScope`, so it
 * still happens if the screen is closed meanwhile; if the process dies first, the journal keeps the
 * recording and it is offered as interrupted on the next launch, so nothing is lost by accident.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecordViewModel @Inject constructor(
    private val controller: RecordingController,
    private val settingsRepository: SettingsRepository,
    private val routeRepository: RouteRepository,
    private val preferences: RecordPreferencesRepository,
    private val location: PreStartLocation,
    private val battery: BatteryOptimization,
    speech: SpeechAvailability,
    @RecordingScope private val appScope: CoroutineScope,
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    /** State owned by this screen rather than by a repository or the recording. */
    private data class Local(
        val phase: RecordPhase = RecordPhase.PreStart,
        val chosenSport: SportType? = null,
        val chosenRouteId: Long? = null,
        val sheet: RecordSheet = RecordSheet.None,
        /** Discard was confirmed and can still be undone; the recording is hidden meanwhile. */
        val discardPending: Boolean = false,
        /** The app is exempt from battery optimisation; re-read whenever the screen resumes. */
        val batteryExempt: Boolean = true,
    )

    private val local = MutableStateFlow(Local(chosenRouteId = savedStateHandle.get<Long>(ARG_ROUTE_ID)))
    private val eventChannel = Channel<RecordEvent>(Channel.BUFFERED)
    private var countdownJob: Job? = null
    private var discardJob: Job? = null

    /** One-shot effects: navigation after save, lap toasts, errors. */
    val events: Flow<RecordEvent> = eventChannel.receiveAsFlow()

    private val settings = settingsRepository.settings.onStart { emit(AppSettings()) }.distinctUntilChanged()
    private val prefs = preferences.preferences.onStart { emit(RecordPreferences()) }.distinctUntilChanged()

    /** The controller's recording, or idle while a discard is pending (as far as the screen goes, it is gone). */
    private val recordingState: Flow<RecordingUiSnapshot> =
        combine(controller.state, local.map { it.discardPending }.distinctUntilChanged()) { rec, pending ->
            if (pending) RecordingUiSnapshot.idle(rec.units) else rec
        }

    private val sport: Flow<SportType> =
        combine(local, settings, recordingState) { l, s, rec -> effectiveSport(l, s, rec) }.distinctUntilChanged()

    private val gpsReading: Flow<GpsReading?> =
        combine(sport, recordingState.map { it.isActive }) { s, active -> s.usesGps && !active }
            .distinctUntilChanged()
            .flatMapLatest { wanted -> if (wanted) GpsReading.watch(location.fixes(), SLOW_FIX_HINT_MS) else flowOf(null) }

    private val routeChoice: Flow<RouteChoice?> =
        combine(local.map { it.chosenRouteId }, recordingState.map { it.routeId }) { chosen, recording -> recording ?: chosen }
            .distinctUntilChanged()
            .flatMapLatest { id ->
                if (id == null) flowOf(null) else routeRepository.observeRoute(id).map { it?.toChoice() }
            }

    private val routes: Flow<List<RouteSummary>?> = routeRepository.observeRoutes()
        .map<_, List<RouteSummary>?> { list -> list.map { RouteSummary(it.id, it.name, it.distanceMeters, it.sport) } }
        .onStart { emit(null) }

    private val base: Flow<RecordUiState> = combine(
        local,
        settings,
        prefs,
        recordingState,
        controller.interruptedRecording,
    ) { l, s, p, rec, interrupted -> baseState(l, s, p, rec, interrupted) }
        .combine(speech.status) { state, status ->
            // Only warn while cues are on: a missing voice does not matter otherwise.
            state.copy(speechWarning = state.audioCues && status in SPEECH_PROBLEMS)
        }

    /** The screen state. Collect it with the lifecycle so location stops when the screen does. */
    val uiState: StateFlow<RecordUiState> = combine(base, gpsReading, routes, routeChoice) { state, reading, list, route ->
        val withGps = if (state.recording == null) {
            state.copy(
                gps = GpsMapping.preStart(state.sport, reading?.fix?.status),
                location = reading?.fix?.location,
                showSlowFixHint = reading?.slowFixHint == true,
            )
        } else {
            state
        }
        withGps.copy(routes = list, route = route)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RecordUiState())

    // region pre-start

    /** Shows [sheet], or hides every sheet with [RecordSheet.None]. */
    fun showSheet(sheet: RecordSheet) = local.update { it.copy(sheet = sheet) }

    /** Selects [sport], closes the picker and makes it the default for next time (record detail 2.2). */
    fun selectSport(sport: SportType) {
        local.update { it.copy(chosenSport = sport, sheet = RecordSheet.None) }
        launchSafely(SETTINGS_ERROR) {
            settingsRepository.setDefaultSport(sport)
            preferences.addRecentSport(sport)
        }
    }

    /** Draws route [id] on the map and follows it once recording; null removes it. */
    fun selectRoute(id: Long?) = local.update { it.copy(chosenRouteId = id, sheet = RecordSheet.None) }

    /**
     * Flips auto-pause for the selected sport (stored per sport). Turning it on for a walk or hike explains
     * that slow speeds can pause and restart the timer often (record detail 6.5).
     */
    fun toggleAutoPause() {
        launchSafely(SETTINGS_ERROR) {
            val sport = currentSport()
            val current = settingsRepository.settings.first().autoPauseEnabled(sport)
            settingsRepository.setAutoPause(sport, !current)
            if (!current && sport in SLOW_SPORTS) eventChannel.send(RecordEvent.Message(SLOW_SPORT_AUTO_PAUSE_HINT))
        }
    }

    /** Re-reads the battery optimisation exemption; call when the screen resumes (it may have changed). */
    fun refreshBatteryExemption() {
        val exempt = battery.isExempt()
        local.update { it.copy(batteryExempt = exempt) }
    }

    /**
     * "Allow" on the battery prompt: the system dialog takes over, and the prompt is not offered again
     * whatever the athlete answers there (it used to come back on every visit after a refusal).
     */
    fun allowBackground() {
        launchSafely(SETTINGS_ERROR) { preferences.dismissBatteryPrompt() }
    }

    /** "Not now" on the battery prompt: it is not offered again on this device. */
    fun dismissBatteryPrompt() {
        launchSafely(SETTINGS_ERROR) { preferences.dismissBatteryPrompt() }
    }

    /** Flips spoken audio cues. */
    fun toggleAudioCues() {
        launchSafely(SETTINGS_ERROR) {
            val cues = settingsRepository.settings.first().audioCues
            settingsRepository.setAudioCues(cues.copy(enabled = !cues.enabled))
        }
    }

    /** Turns the 3-2-1 countdown before the timer on or off. */
    fun setCountdown(enabled: Boolean) {
        launchSafely(SETTINGS_ERROR) { preferences.setCountdown(enabled) }
    }

    /** Turns "Screen display: always on" on or off. */
    fun setKeepScreenOn(enabled: Boolean) {
        launchSafely(SETTINGS_ERROR) { preferences.setKeepScreenOn(enabled) }
    }

    /** Moves the pace field to its next mode (split, current, average). */
    fun cyclePaceMode() {
        launchSafely(SETTINGS_ERROR) { preferences.setPaceMode(preferences.preferences.first().paceMode.next()) }
    }

    /**
     * Starts recording. Call once permissions are settled, while the screen is visible: with the countdown
     * on, the timer starts after 3 seconds unless [cancelCountdown] runs first.
     */
    fun start() {
        val phase = local.value.phase
        if (phase == RecordPhase.WaitingForGps) {
            // A second tap while waiting means "start anyway, without a fix".
            countdownJob?.cancel()
            countdownJob = viewModelScope.launch { startSequence() }
            return
        }
        if (phase != RecordPhase.PreStart) return
        if (controller.state.value.isActive && !local.value.discardPending) return
        countdownJob = viewModelScope.launch {
            if (needsFix(uiState.value)) {
                // Starting without a fix records the first seconds without any position: wait for GPS.
                local.update { it.copy(phase = RecordPhase.WaitingForGps, sheet = RecordSheet.None) }
                uiState.first { !needsFix(it) }
            }
            startSequence()
        }
    }

    private fun needsFix(state: RecordUiState): Boolean =
        state.sport.usesGps && state.gps.indicator != GpsIndicator.Ready && state.gps.indicator != GpsIndicator.Weak

    private suspend fun startSequence() {
        // A new start closes the undo window: the discarded recording goes for good first.
        commitPendingDiscard()
        if (preferences.preferences.first().countdown) {
            for (left in COUNTDOWN_SECONDS downTo 1) {
                local.update { it.copy(phase = RecordPhase.Countdown(left), sheet = RecordSheet.None) }
                delay(ONE_SECOND_MS)
            }
        }
        beginRecording()
    }

    /** Stops a running countdown or the wait for GPS (tap, back, or the screen leaving the foreground). */
    fun cancelCountdown() {
        if (local.value.phase !is RecordPhase.Countdown && local.value.phase != RecordPhase.WaitingForGps) return
        countdownJob?.cancel()
        countdownJob = null
        local.update { it.copy(phase = RecordPhase.PreStart) }
    }

    private suspend fun beginRecording() {
        val state = local.value
        val sport = currentSport()
        local.update { it.copy(phase = RecordPhase.Starting, sheet = RecordSheet.None) }
        try {
            controller.start(sport, state.chosenRouteId)
            preferences.addRecentSport(sport)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            // Already recording (for example started from the notification): that recording shows instead.
            if (!controller.state.value.isActive) eventChannel.send(RecordEvent.Message(startError(e)))
        } catch (e: Exception) {
            eventChannel.send(RecordEvent.Message(startError(e)))
        } finally {
            local.update { it.copy(phase = RecordPhase.PreStart) }
        }
    }

    // endregion

    // region recording

    /** Pauses the timer. */
    fun pause() {
        launchSafely(COMMAND_ERROR) { controller.pause() }
    }

    /** Resumes after a pause and closes the finish sheet. */
    fun resume() {
        local.update { it.copy(sheet = RecordSheet.None) }
        launchSafely(COMMAND_ERROR) { controller.resume() }
    }

    /** Closes the current lap and announces it. */
    fun lap() {
        launchSafely(COMMAND_ERROR) {
            controller.lap()
            val snapshot = controller.state.value
            val lap = snapshot.laps.lastOrNull() ?: return@launchSafely
            val distance = RecordFormat.distance(lap.distance.meters, snapshot.units) + " " + RecordFormat.distanceUnit(snapshot.units)
            eventChannel.send(RecordEvent.Message("Lap ${lap.index + 1} · $distance · ${RecordFormat.duration(lap.elapsedTime)}"))
        }
    }

    /** Opens the finish sheet (Save, Resume or Discard). */
    fun requestFinish() {
        if (controller.state.value.isActive) local.update { it.copy(sheet = RecordSheet.Finish) }
    }

    /**
     * Hold-to-finish: the hold already confirmed the intent, so the activity is saved straight away and the
     * Save screen opens (it offers Discard). No second confirmation sheet.
     */
    fun finish() {
        if (controller.state.value.isActive) save()
    }

    /** Saves the recording and opens the new activity. */
    fun save() {
        local.update { it.copy(phase = RecordPhase.Saving, sheet = RecordSheet.None) }
        viewModelScope.launch {
            try {
                val id = controller.finish()
                if (id != null) eventChannel.send(RecordEvent.ActivitySaved(id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                eventChannel.send(RecordEvent.Message("Couldn't save the activity. It is still recording."))
            } finally {
                local.update { it.copy(phase = RecordPhase.PreStart) }
            }
        }
    }

    /** Asks to confirm discarding. */
    fun requestDiscard() = local.update { it.copy(sheet = RecordSheet.DiscardConfirm) }

    /**
     * Discards the recording: it disappears at once and is deleted when the undo window closes, unless
     * [undoDiscard] runs first.
     */
    fun confirmDiscard() {
        if (local.value.discardPending || !controller.state.value.isActive) {
            local.update { it.copy(sheet = RecordSheet.None) }
            return
        }
        local.update { it.copy(sheet = RecordSheet.None, discardPending = true) }
        discardJob = appScope.launch {
            delay(UNDO_WINDOW_MS)
            discardNow()
        }
        viewModelScope.launch { eventChannel.send(RecordEvent.Discarded) }
    }

    /** Brings back a recording discarded less than [UNDO_WINDOW_MS] ago, still paused. */
    fun undoDiscard() {
        if (!local.value.discardPending) return
        discardJob?.cancel()
        discardJob = null
        local.update { it.copy(discardPending = false) }
    }

    private suspend fun commitPendingDiscard() {
        if (!local.value.discardPending) return
        discardJob?.cancel()
        discardJob = null
        discardNow()
    }

    /**
     * Closing the screen closes the undo window: the snackbar that offered Undo is gone, and a later Record
     * screen (another view model) would otherwise show the recording as paused and let it be resumed, only
     * for this view model's timer to delete it seconds later.
     */
    override fun onCleared() {
        if (!local.value.discardPending) return
        discardJob?.cancel()
        discardJob = appScope.launch { discardNow() }
    }

    private suspend fun discardNow() {
        try {
            // Never leave a discard half done because the start that triggered it was cancelled.
            withContext(NonCancellable) { controller.discard() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The journal still holds it, so it comes back as an interrupted recording that can be discarded.
            eventChannel.trySend(RecordEvent.Message("Couldn't discard the activity."))
        } finally {
            local.update { it.copy(discardPending = false, chosenRouteId = null) }
        }
    }

    // endregion

    // region interrupted recording

    /** Continues a recording that was interrupted when the app process died. */
    fun resumeInterrupted() {
        launchSafely(RESUME_ERROR) {
            if (!controller.recoverInterrupted()) eventChannel.send(RecordEvent.Message("That recording can't be resumed"))
        }
    }

    /** Saves an interrupted recording as it is. */
    fun saveInterrupted() {
        viewModelScope.launch {
            local.update { it.copy(phase = RecordPhase.Saving) }
            try {
                controller.finishInterrupted()?.let { eventChannel.send(RecordEvent.ActivitySaved(it)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                eventChannel.send(RecordEvent.Message("Couldn't save the interrupted recording."))
            } finally {
                local.update { it.copy(phase = RecordPhase.PreStart) }
            }
        }
    }

    /** Deletes an interrupted recording. */
    fun discardInterrupted() {
        launchSafely("Couldn't discard the interrupted recording.") { controller.discardInterrupted() }
    }

    // endregion

    /**
     * Runs [block] in the view model scope; a failure (storage full, a database error) becomes a message
     * instead of crashing the app, since an uncaught exception in [viewModelScope] kills the process and,
     * with it, the recording.
     */
    private fun launchSafely(failure: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                eventChannel.send(RecordEvent.Message(failure))
            }
        }
    }

    /** The sport Start would record, read from the sources rather than the (possibly unsubscribed) [uiState]. */
    private suspend fun currentSport(): SportType =
        effectiveSport(local.value, settingsRepository.settings.first(), controller.state.value)

    private fun startError(e: Exception): String = "Couldn't start recording. ${e.message.orEmpty()}".trim()

    private fun baseState(
        l: Local,
        s: AppSettings,
        p: RecordPreferences,
        rec: RecordingUiSnapshot,
        interrupted: InterruptedRecording?,
    ): RecordUiState {
        val sport = effectiveSport(l, s, rec)
        val recording = if (rec.isActive) RecordingMapping.toUi(rec, p.paceMode) else null
        return RecordUiState(
            phase = if (recording != null && l.phase != RecordPhase.Saving) RecordPhase.Recording else l.phase,
            sport = sport,
            recentSports = (listOf(sport) + (p.recentSports - sport)).take(RecordPreferences.MAX_RECENT),
            units = if (rec.isActive) rec.units else s.units,
            autoPause = s.autoPauseEnabled(sport),
            audioCues = s.audioCues.enabled,
            countdown = p.countdown,
            keepScreenOn = p.keepScreenOn,
            paceMode = p.paceMode,
            gps = if (recording != null) GpsMapping.recording(sport, rec.gpsStatus) else GpsUi(GpsIndicator.Acquiring),
            location = recording?.location,
            sheet = if (recording == null && l.sheet in RECORDING_SHEETS) RecordSheet.None else l.sheet,
            recording = recording,
            interrupted = interrupted.takeIf { !rec.isActive },
            showBatteryPrompt = recording == null && sport.usesGps && !l.batteryExempt && !p.batteryPromptDismissed,
        )
    }

    private fun effectiveSport(l: Local, s: AppSettings, rec: RecordingUiSnapshot): SportType =
        if (rec.isActive) rec.sport ?: s.defaultSport else l.chosenSport ?: s.defaultSport

    internal companion object {
        /** Saved route preselected by "Record this route"; the name of [navigation.RecordDestination.routeId]. */
        const val ARG_ROUTE_ID = "routeId"
        /** Record checklist 2.5: hint after 20 s without a fix. */
        const val SLOW_FIX_HINT_MS = 20_000L
        const val COUNTDOWN_SECONDS = 3
        private const val ONE_SECOND_MS = 1_000L
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val SETTINGS_ERROR = "Couldn't save that setting."
        private const val COMMAND_ERROR = "That didn't work. The recording carries on."
        private const val RESUME_ERROR = "Couldn't resume the interrupted recording."
        private val RECORDING_SHEETS = setOf(RecordSheet.Finish, RecordSheet.DiscardConfirm)

        /** Record detail 4.4 asks for Undo during at least 5 s; this matches a long snackbar. */
        const val UNDO_WINDOW_MS = 10_000L
        private val SPEECH_PROBLEMS = setOf(SpeechStatus.LANGUAGE_MISSING, SpeechStatus.UNAVAILABLE)
        private val SLOW_SPORTS = setOf(SportType.WALK, SportType.HIKE)
        private const val SLOW_SPORT_AUTO_PAUSE_HINT =
            "At walking pace, auto-pause can stop and restart the timer often. Pause by hand if it gets in the way."
    }
}
