// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openactivity.core.data.model.NewActivity
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.core.data.repository.RouteRepository
import org.openactivity.core.data.repository.SettingsRepository
import org.openactivity.core.datastore.AppSettings
import org.openactivity.core.location.GpsStatus
import org.openactivity.core.location.LocationAvailability
import org.openactivity.core.location.LocationSource
import org.openactivity.core.location.PressureSource
import org.openactivity.core.recordingservice.audio.AudioCue
import org.openactivity.core.recordingservice.audio.AudioCuePlanner
import org.openactivity.core.recordingservice.audio.CueSpeaker
import org.openactivity.core.recordingservice.di.RecordingScope
import org.openactivity.core.recordingservice.journal.RecordingJournal
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.recording.Action
import org.openactivity.recording.EngineInput
import org.openactivity.recording.GpsQuality
import org.openactivity.recording.Interruption
import org.openactivity.recording.RecordingConfig
import org.openactivity.recording.RecordingEngine
import org.openactivity.recording.RecordingState
import org.openactivity.recording.RecordingStatus
import org.openactivity.recording.TrackPoint
import org.openactivity.recording.route.RouteEta
import org.openactivity.recording.route.RouteEvent
import org.openactivity.recording.route.RouteFollower
import org.openactivity.recording.route.RoutePath
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * [RecordingController] that runs a [RecordingEngine] on fixes from [LocationSource] and readings from
 * [PressureSource], ticks it once a second, journals it every [FLUSH_INTERVAL_MILLIS] (and on every
 * command) and saves the finished activity through [ActivityRepository].
 *
 * With a route chosen, a [RouteFollower] tracks progress along it and raises off-route alerts (spoken, and a
 * buzz through [AlertHaptics] when [AppSettings.offRouteAlerts] is on). Its state is not journalled: after
 * a restore it is rebuilt by replaying the recorded track, which the follower handles deterministically.
 *
 * The engine is not thread-safe, so every input goes through [engineLock]; journal writes are serialised by
 * [journalLock] and never run after the recording they belong to has ended.
 */
@Singleton
class DefaultRecordingController @Inject constructor(
    private val locationSource: LocationSource,
    private val pressureSource: PressureSource,
    private val activities: ActivityRepository,
    private val routes: RouteRepository,
    private val settings: SettingsRepository,
    private val journal: RecordingJournal,
    private val clock: RecordingClock,
    private val launcher: RecordingServiceLauncher,
    private val speaker: CueSpeaker,
    private val haptics: AlertHaptics,
    @RecordingScope private val scope: CoroutineScope,
) : RecordingController {

    private class Session(
        val uuid: String,
        val sport: SportType,
        val routeId: Long?,
        val startedAtMillis: Long,
        val engine: RecordingEngine,
        val units: UnitSystem,
        val settings: AppSettings,
        val cues: AudioCuePlanner,
        val recovered: Boolean,
        val follower: RouteFollower?,
        val job: Job,
    ) {
        var availability: LocationAvailability = LocationAvailability.AVAILABLE
        var track: List<TrackPoint> = emptyList()
    }

    private val engineLock = Mutex()
    private val journalLock = Mutex()

    @Volatile
    private var session: Session? = null

    private val _state = MutableStateFlow(RecordingUiSnapshot.idle())
    override val state: StateFlow<RecordingUiSnapshot> = _state.asStateFlow()

    private val _interrupted = MutableStateFlow<InterruptedRecording?>(null)
    override val interruptedRecording: StateFlow<InterruptedRecording?> = _interrupted.asStateFlow()

    init {
        scope.launch {
            journal.header.collect { header ->
                _interrupted.value = if (header != null && session == null) {
                    InterruptedRecording(
                        sport = header.sport,
                        startedAt = Instant.ofEpochMilli(header.startedAtMillis),
                        lastUpdate = Instant.ofEpochMilli(header.lastWallClockMillis),
                    )
                } else {
                    null
                }
            }
        }
    }

    override suspend fun start(sport: SportType, routeId: Long?) {
        val appSettings = settings.settings.first()
        val units = appSettings.units
        val follower = routeId?.let { loadFollower(it) }
        engineLock.withLock {
            check(session == null) { "a recording is already in progress" }
            val engine = RecordingEngine(
                RecordingConfig(
                    sport,
                    autoPauseEnabled = appSettings.autoPauseEnabled(sport),
                    unitSystem = units,
                    autoLapMeters = appSettings.autoLapMeters,
                ),
            )
            val now = clock.elapsedRealtimeNanos()
            val wall = clock.wallClockMillis()
            val state = engine.process(EngineInput.UserAction(now, Action.START, wall))
            val s = newSession(
                UUID.randomUUID().toString(), sport, routeId, wall, engine, appSettings, units, recovered = false, follower,
            )
            s.cues.onState(state)
            session = s
            publish(s, state)
            journalLock.withLock {
                journal.begin(s.uuid, sport, routeId, engine.snapshot(), now, wall)
            }
            launchWork(s)
        }
        _interrupted.value = null
        launcher.start()
        if (session?.cues?.announcesStatus == true) speaker.speak(AudioCue.Started(sport), units)
        settings.setDefaultSport(sport)
    }

    override suspend fun pause() = command(Action.PAUSE)

    override suspend fun resume() = command(Action.RESUME)

    override suspend fun lap() = command(Action.LAP)

    override suspend fun finish(): Long? {
        val (s, finished) = engineLock.withLock {
            val s = session ?: return null
            s.engine.process(EngineInput.UserAction(clock.elapsedRealtimeNanos(), Action.FINISH))
            val finished = s.engine.finishedActivity ?: return null
            end(s)
            s to finished
        }
        val id = try {
            activities.saveFinished(
                finished,
                NewActivity(
                    uuid = s.uuid,
                    visibility = s.settings.defaultVisibility(finished.sport),
                    routeId = s.routeId,
                    hideStartMeters = s.settings.privacy.hideStartMeters,
                    hideEndMeters = s.settings.privacy.hideEndMeters,
                    timeZone = clock.zone(),
                ),
                s.units,
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // The journal still holds the recording, so it shows up as interrupted and nothing is lost.
            _state.value = RecordingUiSnapshot.idle(s.units)
            _interrupted.value = journal.header.first()?.let {
                InterruptedRecording(it.sport, Instant.ofEpochMilli(it.startedAtMillis), Instant.ofEpochMilli(it.lastWallClockMillis))
            }
            throw e
        }
        journalLock.withLock { journal.clear() }
        // Queue the cue before going idle: going idle stops the service, which releases the speech engine
        // once queued cues have played.
        if (s.cues.announcesStatus) speaker.speak(AudioCue.Saved, s.units)
        _state.value = RecordingUiSnapshot.idle(s.units)
        return id
    }

    override suspend fun discard() {
        val s = engineLock.withLock {
            val s = session ?: return
            s.engine.process(EngineInput.UserAction(clock.elapsedRealtimeNanos(), Action.DISCARD))
            end(s)
            s
        }
        journalLock.withLock { journal.clear() }
        _state.value = RecordingUiSnapshot.idle(s.units)
    }

    override suspend fun recoverInterrupted(): Boolean {
        if (session != null) return true
        val contents = journal.load() ?: return false
        if (activities.findIdByUuid(contents.recordingUuid) != null || !contents.snapshot.status.isActive) {
            // Already saved (the process died between saving and clearing) or nothing left to continue.
            journalLock.withLock { journal.clear() }
            return false
        }
        val appSettings = settings.settings.first()
        val follower = contents.routeId?.let { loadFollower(it) }?.also { replay(it, contents.snapshot.points) }
        val engine = RecordingEngine.restore(
            contents.snapshot,
            Interruption(clock.elapsedRealtimeNanos(), clock.wallClockMillis()),
        )
        engineLock.withLock {
            if (session != null) return true
            val units = contents.snapshot.config.unitSystem
            val s = newSession(
                contents.recordingUuid, contents.sport, contents.routeId, contents.startedAtMillis, engine,
                appSettings, units, recovered = true, follower,
            )
            session = s
            s.track = contents.snapshot.points
            publish(s, engine.state)
            journal.resume(contents.snapshot.points.size)
            launchWork(s)
        }
        _interrupted.value = null
        session?.let { flush(it) }
        launcher.start()
        return true
    }

    override suspend fun finishInterrupted(): Long? = if (recoverInterrupted()) finish() else null

    override suspend fun discardInterrupted() {
        if (session != null) return
        journalLock.withLock { journal.clear() }
    }

    // ------------------------------------------------------------------------------------------ internals

    private fun newSession(
        uuid: String,
        sport: SportType,
        routeId: Long?,
        startedAtMillis: Long,
        engine: RecordingEngine,
        appSettings: AppSettings,
        units: UnitSystem,
        recovered: Boolean,
        follower: RouteFollower?,
    ) = Session(
        uuid = uuid,
        sport = sport,
        routeId = routeId,
        startedAtMillis = startedAtMillis,
        engine = engine,
        units = units,
        settings = appSettings,
        cues = AudioCuePlanner(appSettings.audioCues, units, sport, engine.state.takeIf { recovered }),
        recovered = recovered,
        follower = follower,
        job = SupervisorJob(scope.coroutineContext[Job]),
    )

    /** Starts the location, barometer, clock and journal loops of [s]. */
    private fun launchWork(s: Session) {
        val work = CoroutineScope(scope.coroutineContext + s.job)
        work.launch {
            while (isActive) {
                val availability = locationSource.availability()
                updateAvailability(s, availability)
                if (availability == LocationAvailability.AVAILABLE) {
                    locationSource.locations().collect { input(s, it) }
                }
                // The stream ended or cannot start: check again shortly (permission granted, GPS on).
                delay(LOCATION_RETRY_MILLIS)
            }
        }
        if (pressureSource.isAvailable) {
            work.launch { pressureSource.readings().collect { input(s, it) } }
        }
        work.launch {
            while (isActive) {
                delay(TICK_MILLIS)
                input(s, EngineInput.Tick(clock.elapsedRealtimeNanos()))
            }
        }
        work.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL_MILLIS)
                flush(s)
            }
        }
    }

    private suspend fun command(action: Action) {
        val s = engineLock.withLock {
            val s = session ?: return
            processLocked(s, EngineInput.UserAction(clock.elapsedRealtimeNanos(), action))
            s
        }
        flush(s)
    }

    private suspend fun input(s: Session, input: EngineInput) = engineLock.withLock {
        if (session === s) processLocked(s, input)
    }

    private fun processLocked(s: Session, input: EngineInput) {
        val state = s.engine.process(input)
        var routeEvents = emptyList<RouteEvent>()
        if (input is EngineInput.Location || input is EngineInput.UserAction) {
            val before = s.track.size
            s.track = s.engine.snapshot().points
            val follower = s.follower
            if (follower != null && state.status == RecordingStatus.RECORDING && s.track.size > before) {
                val p = s.track.last()
                routeEvents = follower.onFix(
                    LatLng(p.latitude, p.longitude), clock.elapsedRealtimeNanos(), p.distanceMeters, p.accuracyMeters,
                ).events
            }
        }
        publish(s, state)
        for (cue in s.cues.onState(state)) speaker.speak(cue, s.units)
        if (routeEvents.isNotEmpty()) {
            val alerts = s.settings.offRouteAlerts
            if (alerts && routeEvents.any { it is RouteEvent.OffRoute }) haptics.offRoute()
            val spoken = if (alerts) routeEvents else routeEvents.filter { it is RouteEvent.Completed }
            for (cue in s.cues.onRoute(spoken)) speaker.speak(cue, s.units)
        }
    }

    /** A follower for saved route [id], or null when it is gone or too short to follow. */
    private suspend fun loadFollower(id: Long): RouteFollower? {
        val points = try {
            routes.getRoute(id)?.points
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        } ?: return null
        val latLngs = points.map { LatLng(it.latitude, it.longitude) }
        if (latLngs.size < 2) return null
        return RouteFollower(RoutePath(latLngs))
    }

    /**
     * Rebuilds [follower] from a restored track, silently. Recorded fixes are on another clock than live
     * ones, so an off-route timer they started is dropped afterwards.
     */
    private fun replay(follower: RouteFollower, points: List<TrackPoint>) {
        for (p in points) {
            if (!p.paused) follower.onFix(LatLng(p.latitude, p.longitude), p.offsetNanos, p.distanceMeters, p.accuracyMeters)
        }
        follower.resetOffRouteTimer()
    }

    private suspend fun updateAvailability(s: Session, availability: LocationAvailability) = engineLock.withLock {
        if (session !== s || s.availability == availability) return@withLock
        s.availability = availability
        publish(s, s.engine.state)
    }

    private fun publish(s: Session, state: RecordingState) {
        _state.value = RecordingUiSnapshot.from(
            state = state,
            routeId = s.routeId,
            startedAt = Instant.ofEpochMilli(s.startedAtMillis),
            units = s.units,
            gpsStatus = gpsStatus(s, state),
            track = s.track,
            recovered = s.recovered,
            route = s.follower?.let { routeSnapshot(it, state) },
        )
    }

    private fun routeSnapshot(follower: RouteFollower, state: RecordingState): RouteSnapshot {
        val progress = follower.progress
        return RouteSnapshot(
            progress = progress,
            remainingTime = RouteEta.remaining(progress.remainingMeters, state.distanceMeters, state.movingTime),
        )
    }

    private fun gpsStatus(s: Session, state: RecordingState): GpsStatus = when {
        s.availability != LocationAvailability.AVAILABLE -> GpsStatus.unavailable(s.availability)
        state.gpsQuality == GpsQuality.NONE -> GpsStatus.SEARCHING
        else -> GpsStatus.forAccuracy(state.horizontalAccuracyMeters)
    }

    /** Writes the journal for [s] unless the recording has ended meanwhile. */
    private suspend fun flush(s: Session) {
        val snapshot = engineLock.withLock { if (session === s) s.engine.snapshot() else null } ?: return
        journalLock.withLock {
            if (session !== s) return
            journal.flush(
                s.uuid, s.sport, s.routeId, s.startedAtMillis, snapshot,
                clock.elapsedRealtimeNanos(), clock.wallClockMillis(),
            )
        }
    }

    /** Ends [s]: stops its loops. Called with [engineLock] held. */
    private fun end(s: Session) {
        session = null
        s.job.cancel()
    }

    companion object {
        /** How often the engine clock advances without GPS. */
        const val TICK_MILLIS: Long = 1_000L

        /** Journal cadence (recording design §5.2: flush every 5 s). */
        const val FLUSH_INTERVAL_MILLIS: Long = 5_000L

        /** Delay before retrying location after it was unavailable or its stream ended. */
        const val LOCATION_RETRY_MILLIS: Long = 2_000L
    }
}
