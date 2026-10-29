// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.openactivity.core.activityextras.ActivityExtras
import org.openactivity.core.activityextras.ActivityExtrasRepository
import org.openactivity.core.activityextras.AddPhotosResult
import org.openactivity.core.data.repository.SettingsRepository
import org.openactivity.core.datastore.AppSettings
import org.openactivity.core.datastore.AudioCueSettings
import org.openactivity.core.datastore.PrivacySettings
import org.openactivity.core.datastore.ThemePreference
import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import org.openactivity.recording.Action
import org.openactivity.recording.EngineInput
import org.openactivity.recording.FinishedActivity
import org.openactivity.recording.RecordingConfig
import org.openactivity.recording.RecordingEngine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/** Recordings produced by the real engine from generated input (not real people's data). */
object Recordings {
    const val START_MILLIS: Long = 1_748_760_600_000L // 2025-06-01T06:50:00Z

    /**
     * A run over one long hill, recorded at 1 Hz with a manual lap halfway: a slower warm-up, slower on
     * the climb and quicker on the descent, small stride-to-stride wobble, and a heart rate that settles,
     * follows the effort and drifts up. About 1 km per 330 s at 2.7-3.4 m/s.
     */
    fun hillyRun(seconds: Int = 1500, sport: SportType = SportType.RUN, withHeartRate: Boolean = true): FinishedActivity {
        val engine = RecordingEngine(RecordingConfig(sport, unitSystem = UnitSystem.METRIC))
        val base = 5_000_000_000L
        var position = LatLng(47.3700, 8.5400)
        engine.process(EngineInput.UserAction(base, Action.START, START_MILLIS))
        for (s in 1..seconds) {
            val t = base + s * 1_000_000_000L
            val phase = 2 * PI * s / seconds
            val climbing = cos(phase) // altitude follows sin(phase), so its slope follows cos(phase)
            val warmUp = 0.25 * exp(-s / 150.0)
            val wobble = 0.06 * sin(2 * PI * s / 97.0) + 0.04 * sin(2 * PI * s / 41.0)
            val speed = 3.1 - 0.28 * climbing - warmUp + wobble
            position = Spherical.destination(position, 70.0 + 30 * sin(2 * PI * s / 900.0), speed)
            engine.process(
                EngineInput.Location(
                    monotonicNanos = t,
                    wallClockMillis = START_MILLIS + s * 1000L,
                    latitude = position.latitude,
                    longitude = position.longitude,
                    horizontalAccuracyMeters = 4.0,
                    altitudeMeters = 410.0 + 35.0 * sin(2 * PI * s / seconds),
                    verticalAccuracyMeters = 3.0,
                    speedMps = speed,
                ),
            )
            val heartRate = 118 + 24 * (1 - exp(-s / 120.0)) + 7 * sin(phase + PI / 6) + 6.0 * s / seconds
            if (withHeartRate) engine.process(EngineInput.HeartRate(t, heartRate.roundToInt()))
            if (s == seconds / 2) engine.process(EngineInput.UserAction(t, Action.LAP))
        }
        engine.process(EngineInput.UserAction(base + (seconds + 1) * 1_000_000_000L, Action.FINISH))
        return checkNotNull(engine.finishedActivity)
    }
}

/** An exporter whose content URIs work on the JVM host (FileProvider needs a real app manifest). */
fun testExporter(context: android.content.Context): org.openactivity.core.export.ActivityExporter =
    org.openactivity.core.export.ActivityExporter.create(
        org.openactivity.core.export.file.ExportFileStore(context) { ctx, file ->
            Uri.parse("content://${ctx.packageName}.export.fileprovider/exports/${file.name}")
        },
    )

/** In-memory settings. */
class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> get() = state
    override suspend fun setUnits(units: UnitSystem) { state.value = state.value.copy(units = units) }
    override suspend fun setTheme(theme: ThemePreference) { state.value = state.value.copy(theme = theme) }
    override suspend fun setAutoPause(sport: SportType, enabled: Boolean) = Unit
    override suspend fun setAudioCues(cues: AudioCueSettings) = Unit
    override suspend fun setDefaultSport(sport: SportType) = Unit
    override suspend fun setDefaultVisibility(visibility: Visibility) { state.value = state.value.copy(defaultVisibility = visibility) }
    override suspend fun setPrivacy(privacy: PrivacySettings) { state.value = state.value.copy(privacy = privacy) }
}

/** In-memory extras without photo files. */
class FakeExtrasRepository : ActivityExtrasRepository {
    val state = MutableStateFlow<Map<Long, ActivityExtras>>(emptyMap())
    val deleted = mutableListOf<Long>()
    override fun observe(activityId: Long): Flow<ActivityExtras> = state.map { it[activityId] ?: ActivityExtras.NONE }
    override suspend fun get(activityId: Long): ActivityExtras = state.value[activityId] ?: ActivityExtras.NONE
    override suspend fun setMuted(activityId: Long, muted: Boolean) {
        state.value = state.value + (activityId to get(activityId).copy(muted = muted))
    }
    override suspend fun addPhotos(activityId: Long, uris: List<Uri>): AddPhotosResult = AddPhotosResult(0, 0, uris.size)
    override suspend fun removePhoto(activityId: Long, photoId: String) = Unit
    override suspend fun deleteAll(activityId: Long) {
        deleted += activityId
        state.value = state.value - activityId
    }
}
