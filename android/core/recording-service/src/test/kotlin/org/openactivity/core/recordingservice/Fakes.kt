// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package org.openactivity.core.recordingservice

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.openactivity.core.data.repository.SettingsRepository
import org.openactivity.core.datastore.AppSettings
import org.openactivity.core.datastore.AudioCueSettings
import org.openactivity.core.datastore.PrivacySettings
import org.openactivity.core.datastore.ThemePreference
import org.openactivity.core.location.LocationAvailability
import org.openactivity.core.location.LocationSource
import org.openactivity.core.location.PressureSource
import org.openactivity.core.recordingservice.audio.AudioCue
import org.openactivity.core.recordingservice.audio.CueSpeaker
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import org.openactivity.recording.EngineInput
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Device clocks driven by the test scheduler's virtual time: monotonic time starts at [baseNanos] and
 * wall time at [baseWallMillis] when virtual time is zero.
 */
internal class FakeClock(
    private val scheduler: TestCoroutineScheduler,
    private val baseNanos: Long,
    private val baseWallMillis: Long,
) : RecordingClock {
    override fun elapsedRealtimeNanos(): Long = baseNanos + scheduler.currentTime * 1_000_000L

    override fun wallClockMillis(): Long = baseWallMillis + scheduler.currentTime

    override fun zone(): ZoneId = ZoneOffset.UTC
}

/**
 * Replays recorded fixes in virtual time: each fix is emitted when the scheduler reaches its timestamp.
 * A new collection (after a simulated process death) continues from the current virtual time, so fixes
 * that fell into the dead interval are lost, as on a real phone.
 */
internal class ReplayLocationSource(
    private val scheduler: TestCoroutineScheduler,
    private val fixes: List<EngineInput.Location>,
    private val baseNanos: Long,
) : LocationSource {
    var availability = LocationAvailability.AVAILABLE

    override fun availability(): LocationAvailability = availability

    override fun locations(): Flow<EngineInput.Location> = flow {
        if (availability != LocationAvailability.AVAILABLE) return@flow
        for (fix in fixes) {
            val atMillis = ceilDiv(fix.monotonicNanos - baseNanos, 1_000_000L)
            val now = scheduler.currentTime
            if (atMillis < now) continue
            delay(atMillis - now)
            emit(fix)
        }
    }

    private fun ceilDiv(a: Long, b: Long) = -Math.floorDiv(-a, b)
}

internal object NoBarometer : PressureSource {
    override val isAvailable: Boolean = false

    override fun readings(): Flow<EngineInput.Pressure> = emptyFlow()
}

internal class RecordingSpeaker : CueSpeaker {
    val spoken = mutableListOf<AudioCue>()
    var shutdowns = 0

    override fun speak(cue: AudioCue, units: UnitSystem) {
        spoken += cue
    }

    override fun shutdown() {
        shutdowns++
    }
}

internal class CountingLauncher : RecordingServiceLauncher {
    var starts = 0

    override fun start() {
        starts++
    }
}

internal class InMemorySettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    override val settings = MutableStateFlow(initial)

    override suspend fun setUnits(units: UnitSystem) = settings.update { it.copy(units = units) }

    override suspend fun setTheme(theme: ThemePreference) = settings.update { it.copy(theme = theme) }

    override suspend fun setAutoPause(sport: SportType, enabled: Boolean) =
        settings.update { it.copy(autoPauseOverrides = it.autoPauseOverrides + (sport to enabled)) }

    override suspend fun setAudioCues(cues: AudioCueSettings) = settings.update { it.copy(audioCues = cues) }

    override suspend fun setDefaultSport(sport: SportType) = settings.update { it.copy(defaultSport = sport) }

    override suspend fun setDefaultVisibility(visibility: Visibility) =
        settings.update { it.copy(defaultVisibility = visibility) }

    override suspend fun setPrivacy(privacy: PrivacySettings) = settings.update { it.copy(privacy = privacy) }
}
