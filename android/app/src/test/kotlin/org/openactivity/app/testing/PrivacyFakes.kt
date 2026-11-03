// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.testing

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import org.openactivity.app.data.backup.AutoBackupConfig
import org.openactivity.app.data.backup.AutoBackupFailure
import org.openactivity.app.data.backup.AutoBackupFrequency
import org.openactivity.app.data.backup.AutoBackupSettings
import org.openactivity.app.data.backup.AutoBackupState
import org.openactivity.app.data.backup.AutoBackupStatus
import org.openactivity.app.data.backup.AutoBackupStore
import org.openactivity.core.data.model.PrivacyZoneError
import org.openactivity.core.data.model.PrivacyZones
import org.openactivity.core.data.model.SavedPrivacyZone
import org.openactivity.core.data.repository.PrivacyZoneException
import org.openactivity.core.data.repository.PrivacyZoneRepository
import org.openactivity.model.LatLng
import java.time.Instant

/** In-memory privacy zones, numbered from 1. */
class FakePrivacyZoneRepository(initial: List<SavedPrivacyZone> = emptyList()) : PrivacyZoneRepository {
    val state = MutableStateFlow(initial)

    override fun observeZones(): Flow<List<SavedPrivacyZone>> = state

    override suspend fun zones(): List<SavedPrivacyZone> = state.value

    override suspend fun get(id: Long): SavedPrivacyZone? = state.value.firstOrNull { it.id == id }

    override suspend fun add(name: String, center: LatLng, radiusMeters: Int): Result<Long> {
        if (state.value.size >= PrivacyZones.MAX_ZONES) return Result.failure(PrivacyZoneException(PrivacyZoneError.LIMIT_REACHED))
        val id = (state.value.maxOfOrNull { it.id } ?: 0L) + 1
        state.update { it + zone(id, name.trim(), center, radiusMeters) }
        return Result.success(id)
    }

    override suspend fun update(id: Long, name: String, center: LatLng, radiusMeters: Int): Result<Long> {
        if (get(id) == null) return Result.failure(PrivacyZoneException(PrivacyZoneError.NOT_FOUND))
        state.update { list -> list.map { if (it.id == id) it.copy(name = name.trim(), center = center, radiusMeters = radiusMeters) else it } }
        return Result.success(id)
    }

    override suspend fun delete(id: Long): Boolean {
        val had = get(id) != null
        state.update { list -> list.filterNot { it.id == id } }
        return had
    }

    companion object {
        fun zone(id: Long, name: String, center: LatLng = LatLng(18.5204, 73.8567), radiusMeters: Int = 400) =
            SavedPrivacyZone(id, "zone-$id", name, center, radiusMeters, Instant.EPOCH, Instant.EPOCH)
    }
}

/** In-memory [AutoBackupStore]. */
class FakeAutoBackupStore(config: AutoBackupConfig = AutoBackupConfig(), status: AutoBackupStatus = AutoBackupStatus()) : AutoBackupStore {
    val configState = MutableStateFlow(config)
    val statusState = MutableStateFlow(status)

    override val config: Flow<AutoBackupConfig> = configState
    override val status: Flow<AutoBackupStatus> = statusState

    override suspend fun updateConfig(change: (AutoBackupConfig) -> AutoBackupConfig) = configState.update(change)

    override suspend fun recordSuccess(at: Instant, fileName: String, activityCount: Int) =
        statusState.update { it.copy(lastSuccessAt = at, lastSuccessFile = fileName, lastActivityCount = activityCount, lastFailure = null, lastFailureAt = null) }

    override suspend fun recordFailure(at: Instant, failure: AutoBackupFailure) =
        statusState.update { it.copy(lastFailureAt = at, lastFailure = failure) }
}

/** [AutoBackupSettings] that records what Settings asked for. */
class FakeAutoBackupSettings(val store: FakeAutoBackupStore = FakeAutoBackupStore()) : AutoBackupSettings {
    val running = MutableStateFlow(false)
    val calls = mutableListOf<String>()

    override val state: Flow<AutoBackupState> =
        combine(store.configState, store.statusState, running) { c, s, r -> AutoBackupState(c, s, r) }

    override suspend fun setEnabled(enabled: Boolean) {
        calls += "enabled:$enabled"
        if (enabled && store.configState.value.folderUri == null) return
        store.updateConfig { it.copy(enabled = enabled) }
    }

    override suspend fun chooseFolder(tree: Uri, enable: Boolean) {
        calls += "folder:$tree:$enable"
        store.updateConfig { it.copy(folderUri = tree.toString(), folderName = "Backups", enabled = it.enabled || enable) }
    }

    override suspend fun setFrequency(frequency: AutoBackupFrequency) {
        calls += "frequency:$frequency"
        store.updateConfig { it.copy(frequency = frequency) }
    }

    override fun backUpNow() {
        calls += "now"
    }
}
