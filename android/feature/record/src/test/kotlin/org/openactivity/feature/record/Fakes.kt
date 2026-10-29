// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import org.openactivity.core.data.model.RouteDetail
import org.openactivity.core.data.model.RoutePoint
import org.openactivity.core.data.model.RoutePointInput
import org.openactivity.core.data.model.SavedRoute
import org.openactivity.core.data.repository.RouteRepository
import org.openactivity.core.data.repository.SettingsRepository
import org.openactivity.core.datastore.AppSettings
import org.openactivity.core.datastore.AudioCueSettings
import org.openactivity.core.datastore.PrivacySettings
import org.openactivity.core.datastore.ThemePreference
import org.openactivity.core.location.GpsStatus
import org.openactivity.core.recordingservice.InterruptedRecording
import org.openactivity.core.recordingservice.RecordingController
import org.openactivity.core.recordingservice.RecordingUiSnapshot
import org.openactivity.core.recordingservice.SplitProgress
import org.openactivity.feature.record.data.PaceMode
import org.openactivity.feature.record.data.RecordPreferences
import org.openactivity.feature.record.data.RecordPreferencesRepository
import org.openactivity.feature.record.location.PreStartFix
import org.openactivity.feature.record.location.PreStartLocation
import org.openactivity.feature.record.system.BatteryOptimization
import org.openactivity.feature.record.system.SpeechAvailability
import org.openactivity.core.recordingservice.audio.SpeechStatus
import org.openactivity.geo.BoundingBox
import org.openactivity.model.Distance
import org.openactivity.model.Lap
import org.openactivity.model.LapTrigger
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import org.openactivity.recording.RecordingStatus
import org.openactivity.recording.TrackPoint
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** A [RecordingController] that records calls and lets the test drive [state]. */
class FakeRecordingController : RecordingController {
    val stateFlow = MutableStateFlow(RecordingUiSnapshot.idle())
    val interruptedFlow = MutableStateFlow<InterruptedRecording?>(null)
    override val state: StateFlow<RecordingUiSnapshot> = stateFlow.asStateFlow()
    override val interruptedRecording: StateFlow<InterruptedRecording?> = interruptedFlow.asStateFlow()

    val calls = mutableListOf<String>()
    var started: Pair<SportType, Long?>? = null
    var finishResult: Long? = 42L
    var startError: Exception? = null

    /** Thrown by [pause] and [finishInterrupted] when set, like a failing database write. */
    var commandError: Exception? = null

    override suspend fun start(sport: SportType, routeId: Long?) {
        startError?.let { throw it }
        calls += "start"
        started = sport to routeId
        stateFlow.value = recordingSnapshot(sport = sport, routeId = routeId)
    }

    override suspend fun pause() {
        commandError?.let { throw it }
        calls += "pause"
        stateFlow.update { it.copy(status = RecordingStatus.PAUSED) }
    }

    override suspend fun resume() {
        calls += "resume"
        stateFlow.update { it.copy(status = RecordingStatus.RECORDING) }
    }

    override suspend fun lap() {
        calls += "lap"
        stateFlow.update {
            it.copy(laps = it.laps + Lap(it.laps.size, Duration.ZERO, 5.minutes, 5.minutes, Distance(1020.0), Distance(0.0), LapTrigger.MANUAL))
        }
    }

    override suspend fun finish(): Long? {
        calls += "finish"
        stateFlow.value = RecordingUiSnapshot.idle()
        return finishResult
    }

    override suspend fun discard() {
        calls += "discard"
        stateFlow.value = RecordingUiSnapshot.idle()
    }

    override suspend fun recoverInterrupted(): Boolean {
        calls += "recover"
        val i = interruptedFlow.value ?: return false
        interruptedFlow.value = null
        stateFlow.value = recordingSnapshot(sport = i.sport, recovered = true)
        return true
    }

    override suspend fun finishInterrupted(): Long? {
        commandError?.let { throw it }
        calls += "finishInterrupted"
        interruptedFlow.value = null
        return finishResult
    }

    override suspend fun discardInterrupted() {
        calls += "discardInterrupted"
        interruptedFlow.value = null
    }
}

/** In-memory [SettingsRepository]. */
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state

    override suspend fun setUnits(units: UnitSystem) = state.update { it.copy(units = units) }

    override suspend fun setTheme(theme: ThemePreference) = state.update { it.copy(theme = theme) }

    override suspend fun setAutoPause(sport: SportType, enabled: Boolean) =
        state.update { it.copy(autoPauseOverrides = it.autoPauseOverrides + (sport to enabled)) }

    override suspend fun setAudioCues(cues: AudioCueSettings) = state.update { it.copy(audioCues = cues) }

    override suspend fun setDefaultSport(sport: SportType) = state.update { it.copy(defaultSport = sport) }

    override suspend fun setDefaultVisibility(visibility: Visibility) = state.update { it.copy(defaultVisibility = visibility) }

    override suspend fun setPrivacy(privacy: PrivacySettings) = state.update { it.copy(privacy = privacy) }
}

/** In-memory [RecordPreferencesRepository]. */
class FakeRecordPreferences(initial: RecordPreferences = RecordPreferences()) : RecordPreferencesRepository {
    val state = MutableStateFlow(initial)
    override val preferences: Flow<RecordPreferences> = state

    override suspend fun setCountdown(enabled: Boolean) = state.update { it.copy(countdown = enabled) }

    override suspend fun setKeepScreenOn(enabled: Boolean) = state.update { it.copy(keepScreenOn = enabled) }

    override suspend fun setPaceMode(mode: PaceMode) = state.update { it.copy(paceMode = mode) }

    override suspend fun dismissBatteryPrompt() = state.update { it.copy(batteryPromptDismissed = true) }

    override suspend fun addRecentSport(sport: SportType) =
        state.update { it.copy(recentSports = (listOf(sport) + it.recentSports).distinct().take(RecordPreferences.MAX_RECENT)) }
}

/** Saved routes held in memory. */
class FakeRouteRepository(routes: List<RouteDetail> = emptyList()) : RouteRepository {
    val details = MutableStateFlow(routes)

    override fun observeRoutes(): Flow<List<SavedRoute>> = details.map { list -> list.map { it.route } }

    override fun observeRoute(id: Long): Flow<RouteDetail?> = details.map { list -> list.firstOrNull { it.route.id == id } }

    override suspend fun getRoute(id: Long): RouteDetail? = details.value.firstOrNull { it.route.id == id }

    override suspend fun importRoute(name: String, points: List<RoutePointInput>, description: String, sport: SportType?): Long =
        error("not used")

    override suspend fun delete(id: Long): Boolean = error("not used")
}

/** Location fixes driven by the test; counts active subscriptions. */
class FakePreStartLocation : PreStartLocation {
    val fixes = MutableStateFlow(PreStartFix(GpsStatus.SEARCHING, null))
    var subscribers = 0
        private set

    override fun fixes(): Flow<PreStartFix> = fixes
        .onStart { subscribers++ }
        .onCompletion { subscribers-- }
}

/** Battery optimisation exemption driven by the test. */
class FakeBatteryOptimization(var exempt: Boolean = true) : BatteryOptimization {
    override fun isExempt(): Boolean = exempt
}

/** Speech engine status driven by the test. */
class FakeSpeechAvailability : SpeechAvailability {
    override val status = MutableStateFlow(SpeechStatus.NOT_STARTED)
}

/** A route with two points. */
fun testRoute(id: Long = 7L, name: String = "River loop"): RouteDetail = RouteDetail(
    route = SavedRoute(
        id = id,
        uuid = "route-$id",
        name = name,
        description = "",
        sport = SportType.RUN,
        distanceMeters = 5200.0,
        elevationGainMeters = 40.0,
        elevationLossMeters = 40.0,
        polyline = "",
        bounds = checkNotNull(BoundingBox.of(listOf(LatLng(51.50, -0.12), LatLng(51.52, -0.10)))),
        createdAt = Instant.EPOCH,
    ),
    points = listOf(RoutePoint(51.50, -0.12, null, 0.0), RoutePoint(51.52, -0.10, null, 5200.0)),
)

/** A snapshot of a recording in progress, 12:47 in and 2.41 km along. */
fun recordingSnapshot(
    sport: SportType = SportType.RUN,
    status: RecordingStatus = RecordingStatus.RECORDING,
    routeId: Long? = null,
    units: UnitSystem = UnitSystem.METRIC,
    recovered: Boolean = false,
    gps: GpsStatus = GpsStatus.forAccuracy(6.0),
    track: List<TrackPoint> = sampleTrack(),
): RecordingUiSnapshot = RecordingUiSnapshot.idle(units).copy(
    status = status,
    sport = sport,
    routeId = routeId,
    startedAt = Instant.parse("2026-10-29T07:00:00Z"),
    elapsed = 12.minutes + 47.seconds,
    movingTime = 12.minutes + 40.seconds,
    distanceMeters = 2410.0,
    currentSpeedMps = 3.15,
    averageSpeedMps = 3.17,
    elevationGainMeters = 24.0,
    altitudeMeters = 38.0,
    currentSplit = SplitProgress(2, 410.0, 2.minutes + 10.seconds),
    gpsStatus = gps,
    track = track,
    recoveredAfterInterruption = recovered,
)

/** A short walk along a street, as accepted track points. */
fun sampleTrack(): List<TrackPoint> = (0 until 20).map { i ->
    val lat = 51.5007 + i * 0.0004
    val lon = -0.1246 + i * 0.0002
    TrackPoint(
        offsetNanos = i * 1_000_000_000L,
        epochMillis = 1_700_000_000_000L + i * 1000L,
        latitude = lat,
        longitude = lon,
        rawLatitude = lat,
        rawLongitude = lon,
        accuracyMeters = 5.0,
        altitudeMeters = 30.0,
        speedMps = 3.0,
        distanceMeters = i * 48.0,
        paused = false,
        segment = 0,
        heartRateBpm = null,
        cadence = null,
        powerWatts = null,
    )
}
