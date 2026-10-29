// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(ExperimentalCoroutinesApi::class)

package org.openactivity.feature.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openactivity.core.datastore.AppSettings
import org.openactivity.core.location.GpsState
import org.openactivity.core.location.GpsStatus
import org.openactivity.core.map.MapLocation
import org.openactivity.core.recordingservice.InterruptedRecording
import org.openactivity.core.recordingservice.audio.SpeechStatus
import org.openactivity.feature.record.data.PaceMode
import org.openactivity.feature.record.data.RecordPreferences
import org.openactivity.feature.record.location.PreStartFix
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.openactivity.recording.RecordingStatus
import java.time.Instant

class RecordViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val controller = FakeRecordingController()
    private val settings = FakeSettingsRepository(AppSettings(defaultSport = SportType.RIDE))
    private val prefs = FakeRecordPreferences()
    private val routes = FakeRouteRepository(listOf(testRoute()))
    private val location = FakePreStartLocation()
    private val battery = FakeBatteryOptimization()
    private val speech = FakeSpeechAvailability()

    private fun TestScope.newViewModel() =
        RecordViewModel(controller, settings, routes, prefs, location, battery, speech, backgroundScope)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): RecordViewModel {
        val vm = newViewModel()
        backgroundScope.launch(dispatcher) { vm.uiState.collect {} }
        runCurrent()
        return vm
    }

    private fun TestScope.events(vm: RecordViewModel): MutableList<RecordEvent> {
        val list = mutableListOf<RecordEvent>()
        backgroundScope.launch(dispatcher) { vm.events.collect { list += it } }
        return list
    }

    @Test fun preselectsTheLastRecordedSportAndItsAutoPauseDefault() = runTest(dispatcher) {
        val vm = viewModel()
        val state = vm.uiState.value
        assertEquals(SportType.RIDE, state.sport)
        assertTrue("rides default to auto-pause on", state.autoPause)
        assertEquals(RecordPhase.PreStart, state.phase)
        assertNull(state.recording)
    }

    @Test fun choosingASportPersistsItAsDefaultAndRecent() = runTest(dispatcher) {
        val vm = viewModel()
        vm.showSheet(RecordSheet.SportPicker)
        vm.selectSport(SportType.HIKE)
        advanceUntilIdle()
        val state = vm.uiState.value
        assertEquals(SportType.HIKE, state.sport)
        assertEquals(RecordSheet.None, state.sheet)
        assertEquals(SportType.HIKE, settings.state.value.defaultSport)
        assertEquals(SportType.HIKE, prefs.state.value.recentSports.first())
        assertEquals(SportType.HIKE, state.recentSports.first())
    }

    @Test fun gpsPillFollowsAccuracyAndTheMapGetsTheFix() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(GpsIndicator.Acquiring, vm.uiState.value.gps.indicator)

        val here = MapLocation(LatLng(51.5, -0.12), accuracyMeters = 35f)
        location.fixes.value = PreStartFix(GpsStatus.forAccuracy(35.0), here)
        runCurrent()
        assertEquals(GpsIndicator.Weak, vm.uiState.value.gps.indicator)
        assertEquals(here, vm.uiState.value.location)

        location.fixes.value = PreStartFix(GpsStatus.forAccuracy(8.0), here.copy(accuracyMeters = 8f))
        runCurrent()
        assertEquals(GpsUi(GpsIndicator.Ready, 8f), vm.uiState.value.gps)
        assertEquals(org.openactivity.core.designsystem.component.RecordButtonState.Ready, vm.uiState.value.startButtonState)
    }

    @Test fun missingPermissionShowsAFixAction() = runTest(dispatcher) {
        val vm = viewModel()
        location.fixes.value = PreStartFix(GpsStatus(GpsState.NO_PERMISSION), null)
        runCurrent()
        assertTrue(vm.uiState.value.gps.needsAction)
        assertEquals(GpsIndicator.NoPermission, vm.uiState.value.gps.indicator)
    }

    @Test fun slowFixHintAppearsAfter20sOfSearching() = runTest(dispatcher) {
        val vm = viewModel()
        advanceTimeBy(RecordViewModel.SLOW_FIX_HINT_MS - 1)
        assertFalse(vm.uiState.value.showSlowFixHint)
        advanceTimeBy(2)
        assertTrue(vm.uiState.value.showSlowFixHint)

        location.fixes.value = PreStartFix(GpsStatus.forAccuracy(9.0), null)
        runCurrent()
        assertFalse(vm.uiState.value.showSlowFixHint)
    }

    @Test fun indoorSportsDoNotWatchLocation() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(1, location.subscribers)
        vm.selectSport(SportType.YOGA)
        advanceUntilIdle()
        assertEquals(GpsIndicator.Indoor, vm.uiState.value.gps.indicator)
        assertEquals(0, location.subscribers)
    }

    @Test fun startBeginsRecordingWithTheChosenRoute() = runTest(dispatcher) {
        val vm = viewModel()
        vm.showSheet(RecordSheet.RoutePicker)
        advanceUntilIdle()
        assertEquals(listOf("River loop"), vm.uiState.value.routes?.map { it.name })
        vm.selectRoute(7L)
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.route?.points?.size)

        vm.start()
        advanceUntilIdle()
        assertEquals(SportType.RIDE to 7L, controller.started)
        val state = vm.uiState.value
        assertEquals(RecordPhase.Recording, state.phase)
        assertNotNull(state.recording)
        assertEquals("River loop", state.route?.name)
        assertEquals(0, location.subscribers)
        assertEquals(SportType.RIDE, prefs.state.value.recentSports.first())
    }

    @Test fun countdownRunsThreeTwoOneBeforeStarting() = runTest(dispatcher) {
        prefs.state.value = RecordPreferences(countdown = true)
        val vm = viewModel()
        vm.start()
        runCurrent()
        assertEquals(RecordPhase.Countdown(3), vm.uiState.value.phase)
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(RecordPhase.Countdown(2), vm.uiState.value.phase)
        assertNull(controller.started)
        advanceTimeBy(2_000)
        runCurrent()
        assertNotNull(controller.started)
        assertEquals(RecordPhase.Recording, vm.uiState.value.phase)
    }

    @Test fun cancellingTheCountdownNeverStarts() = runTest(dispatcher) {
        prefs.state.value = RecordPreferences(countdown = true)
        val vm = viewModel()
        vm.start()
        advanceTimeBy(1_500)
        vm.cancelCountdown()
        advanceUntilIdle()
        assertNull(controller.started)
        assertEquals(RecordPhase.PreStart, vm.uiState.value.phase)
    }

    @Test fun startFailureIsReportedAndTheScreenStaysUsable() = runTest(dispatcher) {
        controller.startError = RuntimeException("no service")
        val vm = viewModel()
        val events = events(vm)
        vm.start()
        advanceUntilIdle()
        assertEquals(RecordPhase.PreStart, vm.uiState.value.phase)
        assertTrue(events.single() is RecordEvent.Message)
    }

    @Test fun pauseResumeAndLapReachTheController() = runTest(dispatcher) {
        controller.stateFlow.value = recordingSnapshot()
        val vm = viewModel()
        val events = events(vm)
        vm.pause()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.recording!!.isPaused)
        vm.resume()
        vm.lap()
        advanceUntilIdle()
        assertEquals(listOf("pause", "resume", "lap"), controller.calls)
        assertEquals("Lap 1 · 1.02 km · 5:00", (events.single() as RecordEvent.Message).text)
    }

    @Test fun saveFinishesAndOpensTheNewActivity() = runTest(dispatcher) {
        controller.stateFlow.value = recordingSnapshot(status = RecordingStatus.PAUSED)
        val vm = viewModel()
        val events = events(vm)
        vm.requestFinish()
        runCurrent()
        assertEquals(RecordSheet.Finish, vm.uiState.value.sheet)
        vm.save()
        advanceUntilIdle()
        assertEquals(listOf<RecordEvent>(RecordEvent.ActivitySaved(42L)), events)
        assertNull(vm.uiState.value.recording)
        assertEquals(RecordSheet.None, vm.uiState.value.sheet)
    }

    @Test fun discardNeedsConfirmation() = runTest(dispatcher) {
        controller.stateFlow.value = recordingSnapshot(status = RecordingStatus.PAUSED)
        val vm = viewModel()
        vm.requestFinish()
        vm.requestDiscard()
        runCurrent()
        assertEquals(RecordSheet.DiscardConfirm, vm.uiState.value.sheet)
        assertTrue("nothing discarded before confirming", controller.calls.isEmpty())
        vm.confirmDiscard()
        advanceUntilIdle()
        assertEquals(listOf("discard"), controller.calls)
        assertNull(vm.uiState.value.recording)
    }

    @Test fun discardCanBeUndoneWithinTheWindow() = runTest(dispatcher) {
        controller.stateFlow.value = recordingSnapshot(status = RecordingStatus.PAUSED)
        val vm = viewModel()
        val events = events(vm)
        vm.requestDiscard()
        vm.confirmDiscard()
        runCurrent()
        assertNull("hidden at once", vm.uiState.value.recording)
        assertEquals(RecordPhase.PreStart, vm.uiState.value.phase)
        assertEquals(listOf<RecordEvent>(RecordEvent.Discarded), events)
        advanceTimeBy(RecordViewModel.UNDO_WINDOW_MS - 1)
        assertTrue("not deleted during the undo window", controller.calls.isEmpty())
        vm.undoDiscard()
        advanceUntilIdle()
        assertTrue("never deleted", controller.calls.isEmpty())
        assertTrue(vm.uiState.value.recording!!.isPaused)
    }

    @Test fun discardIsCommittedWhenTheWindowCloses() = runTest(dispatcher) {
        controller.stateFlow.value = recordingSnapshot(status = RecordingStatus.PAUSED)
        val vm = viewModel()
        vm.confirmDiscard()
        advanceTimeBy(RecordViewModel.UNDO_WINDOW_MS + 1)
        runCurrent()
        assertEquals(listOf("discard"), controller.calls)
        vm.undoDiscard()
        runCurrent()
        assertNull("too late to undo", vm.uiState.value.recording)
    }

    @Test fun startingAgainCommitsAPendingDiscardFirst() = runTest(dispatcher) {
        controller.stateFlow.value = recordingSnapshot(status = RecordingStatus.PAUSED)
        val vm = viewModel()
        vm.confirmDiscard()
        runCurrent()
        vm.start()
        runCurrent()
        assertEquals(listOf("discard", "start"), controller.calls)
        assertNotNull(vm.uiState.value.recording)
    }

    @Test fun closingTheScreenCommitsAPendingDiscardAtOnce() = runTest(dispatcher) {
        controller.stateFlow.value = recordingSnapshot(status = RecordingStatus.PAUSED)
        val store = ViewModelStore()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = newViewModel() as T
        }
        val vm = ViewModelProvider(store, factory)[RecordViewModel::class.java]
        vm.confirmDiscard()
        runCurrent()
        assertTrue(controller.calls.isEmpty())
        store.clear()
        runCurrent()
        // A later Record screen must not show a recording that a stale timer deletes seconds afterwards.
        assertEquals(listOf("discard"), controller.calls)
        advanceUntilIdle()
        assertEquals("deleted once", listOf("discard"), controller.calls)
    }

    @Test fun batteryPromptShowsUntilExemptOrDismissed() = runTest(dispatcher) {
        battery.exempt = false
        val vm = viewModel()
        vm.refreshBatteryExemption()
        runCurrent()
        assertTrue(vm.uiState.value.showBatteryPrompt)
        battery.exempt = true
        vm.refreshBatteryExemption()
        runCurrent()
        assertFalse("granted: never asked again", vm.uiState.value.showBatteryPrompt)
        battery.exempt = false
        vm.refreshBatteryExemption()
        vm.dismissBatteryPrompt()
        advanceUntilIdle()
        assertFalse("dismissed for good", vm.uiState.value.showBatteryPrompt)
        assertTrue(prefs.state.value.batteryPromptDismissed)
    }

    @Test fun missingVoiceWarnsOnlyWhileCuesAreOn() = runTest(dispatcher) {
        val vm = viewModel()
        speech.status.value = SpeechStatus.LANGUAGE_MISSING
        runCurrent()
        assertTrue(vm.uiState.value.speechWarning)
        vm.toggleAudioCues()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.speechWarning)
    }

    @Test fun turningOnAutoPauseForAWalkExplainsTheCatch() = runTest(dispatcher) {
        val vm = viewModel()
        val events = events(vm)
        vm.selectSport(SportType.WALK)
        advanceUntilIdle()
        vm.toggleAutoPause()
        advanceUntilIdle()
        runCurrent()
        assertTrue(settings.state.value.autoPauseEnabled(SportType.WALK))
        assertTrue(events.single() is RecordEvent.Message)
    }

    @Test fun interruptedRecordingCanBeResumed() = runTest(dispatcher) {
        controller.interruptedFlow.value = InterruptedRecording(SportType.RUN, Instant.EPOCH, Instant.EPOCH)
        val vm = viewModel()
        assertNotNull(vm.uiState.value.interrupted)
        vm.resumeInterrupted()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertNull(state.interrupted)
        assertTrue(state.recording!!.recovered)
        assertEquals(SportType.RUN, state.sport)
    }

    @Test fun interruptedRecordingCanBeSavedAsIs() = runTest(dispatcher) {
        controller.interruptedFlow.value = InterruptedRecording(SportType.RUN, Instant.EPOCH, Instant.EPOCH)
        val vm = viewModel()
        val events = events(vm)
        vm.saveInterrupted()
        advanceUntilIdle()
        assertEquals(listOf<RecordEvent>(RecordEvent.ActivitySaved(42L)), events)
    }

    @Test fun quickTogglesWriteSettings() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggleAutoPause()
        vm.toggleAudioCues()
        vm.setCountdown(true)
        vm.setKeepScreenOn(true)
        vm.cyclePaceMode()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertFalse(settings.state.first().autoPauseEnabled(SportType.RIDE))
        assertFalse(state.autoPause)
        assertFalse(state.audioCues)
        assertTrue(state.countdown)
        assertTrue(state.keepScreenOn)
        assertEquals(PaceMode.CURRENT, state.paceMode)
    }

    @Test fun aFailingCommandIsReportedInsteadOfCrashing() = runTest(dispatcher) {
        controller.stateFlow.value = recordingSnapshot()
        controller.commandError = java.io.IOException("disk full")
        val vm = viewModel()
        val events = events(vm)
        vm.pause()
        advanceUntilIdle()
        runCurrent() // the event collector lives in backgroundScope, which advanceUntilIdle does not wait for
        assertTrue(events.single() is RecordEvent.Message)
        assertFalse(vm.uiState.value.recording!!.isPaused)
    }

    @Test fun failingToSaveAnInterruptedRecordingKeepsTheScreenUsable() = runTest(dispatcher) {
        controller.interruptedFlow.value = InterruptedRecording(SportType.RUN, Instant.EPOCH, Instant.EPOCH)
        controller.commandError = IllegalStateException("database locked")
        val vm = viewModel()
        val events = events(vm)
        vm.saveInterrupted()
        advanceUntilIdle()
        assertTrue(events.single() is RecordEvent.Message)
        assertEquals(RecordPhase.PreStart, vm.uiState.value.phase)
        assertNotNull("still offered again", vm.uiState.value.interrupted)
    }

    @Test fun startUsesTheChosenSportEvenWhenTheStateIsNotCollected() = runTest(dispatcher) {
        val vm = newViewModel()
        vm.selectSport(SportType.WALK)
        vm.start()
        advanceUntilIdle()
        assertEquals(SportType.WALK, controller.started?.first)
    }
}
