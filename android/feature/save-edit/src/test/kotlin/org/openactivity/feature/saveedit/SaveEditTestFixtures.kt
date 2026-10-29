// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.saveedit

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.openactivity.core.activityextras.ActivityExtras
import org.openactivity.core.activityextras.ActivityExtrasRepository
import org.openactivity.core.activityextras.ActivityPhoto
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
import java.io.File

/** A steady run produced by the real engine from generated input. Starts 07:10 UTC. */
fun steadyRun(seconds: Int = 900, sport: SportType = SportType.RUN): FinishedActivity {
    val start = 1_748_761_800_000L // 2025-06-01T07:10:00Z
    val engine = RecordingEngine(RecordingConfig(sport))
    val base = 1_000_000_000L
    var p = LatLng(47.37, 8.54)
    engine.process(EngineInput.UserAction(base, Action.START, start))
    for (s in 1..seconds) {
        p = Spherical.destination(p, 90.0, 3.0)
        engine.process(
            EngineInput.Location(base + s * 1_000_000_000L, start + s * 1000L, p.latitude, p.longitude, 4.0, 400.0, 3.0, 3.0),
        )
    }
    engine.process(EngineInput.UserAction(base + (seconds + 1) * 1_000_000_000L, Action.FINISH))
    return checkNotNull(engine.finishedActivity)
}

/** In-memory settings. */
class FakeSettings(initial: AppSettings = AppSettings(units = UnitSystem.METRIC)) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> get() = state
    override suspend fun setUnits(units: UnitSystem) { state.value = state.value.copy(units = units) }
    override suspend fun setTheme(theme: ThemePreference) = Unit
    override suspend fun setAutoPause(sport: SportType, enabled: Boolean) = Unit
    override suspend fun setAudioCues(cues: AudioCueSettings) = Unit
    override suspend fun setDefaultSport(sport: SportType) = Unit
    override suspend fun setDefaultVisibility(visibility: Visibility) = Unit
    override suspend fun setPrivacy(privacy: PrivacySettings) = Unit
}

/** In-memory extras; added photos point at placeholder files. */
class FakeExtras : ActivityExtrasRepository {
    val state = MutableStateFlow<Map<Long, ActivityExtras>>(emptyMap())
    val deleted = mutableListOf<Long>()
    override fun observe(activityId: Long): Flow<ActivityExtras> = state.map { it[activityId] ?: ActivityExtras.NONE }
    override suspend fun get(activityId: Long): ActivityExtras = state.value[activityId] ?: ActivityExtras.NONE
    override suspend fun setMuted(activityId: Long, muted: Boolean) {
        state.value = state.value + (activityId to get(activityId).copy(muted = muted))
    }
    override suspend fun addPhotos(activityId: Long, uris: List<Uri>): AddPhotosResult {
        val current = get(activityId)
        val room = ActivityExtras.MAX_PHOTOS - current.photos.size
        val added = uris.take(room).mapIndexed { i, _ -> ActivityPhoto("p${current.photos.size + i}", File("p$i.jpg"), 400, 300) }
        state.value = state.value + (activityId to current.copy(photos = current.photos + added))
        return AddPhotosResult(added.size, uris.size - added.size, 0)
    }
    override suspend fun removePhoto(activityId: Long, photoId: String) {
        val current = get(activityId)
        state.value = state.value + (activityId to current.copy(photos = current.photos.filterNot { it.id == photoId }))
    }
    override suspend fun deleteAll(activityId: Long) {
        deleted += activityId
        state.value = state.value - activityId
    }
}
