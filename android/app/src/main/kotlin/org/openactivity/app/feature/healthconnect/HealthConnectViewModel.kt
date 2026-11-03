// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.healthconnect

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openactivity.app.data.local.LocalPreferences
import org.openactivity.core.healthconnect.HealthConnectAvailability
import org.openactivity.core.healthconnect.HealthConnectGateway
import org.openactivity.core.healthconnect.HealthConnectStatus
import org.openactivity.core.healthconnect.PermissionRequestOutcome
import org.openactivity.core.healthconnect.SyncDirection
import javax.inject.Inject

/** Schedules the daily Health Connect read; an interface so the screen is tested without WorkManager. */
interface HealthConnectJobs {
    fun scheduleDaily()

    fun cancelDaily()
}

/** [HealthConnectJobs] over [HealthConnectSyncWorker]. */
class WorkManagerHealthConnectJobs @Inject constructor(@ApplicationContext private val context: Context) : HealthConnectJobs {
    override fun scheduleDaily() = HealthConnectSyncWorker.scheduleDaily(context)

    override fun cancelDaily() = HealthConnectSyncWorker.cancelDaily(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class HealthConnectJobsModule {
    @Binds abstract fun jobs(impl: WorkManagerHealthConnectJobs): HealthConnectJobs
}

/** Work the screen is doing. */
sealed interface HealthBusy {
    data object Syncing : HealthBusy

    data class WritingPast(val done: Int, val total: Int) : HealthBusy
}

/**
 * State of the Health Connect screen.
 *
 * @property status null until Health Connect has been asked.
 * @property writeEnabled the user's "write" switch; it can be on while Health Connect no longer allows it.
 */
@Immutable
data class HealthConnectUiState(
    val status: HealthConnectStatus? = null,
    val writeEnabled: Boolean = false,
    val readEnabled: Boolean = false,
    val lastSyncMillis: Long? = null,
    val busy: HealthBusy? = null,
    val message: String? = null,
)

/** One-shot requests to the screen. */
sealed interface HealthConnectEffect {
    /** Show Health Connect's permission dialog for [permissions], then report with [HealthConnectViewModel.onPermissionsResult]. */
    data class RequestPermissions(val direction: SyncDirection, val permissions: Set<String>) : HealthConnectEffect
}

/**
 * Settings, Health Connect: availability, the write and read switches with their permissions, "Sync
 * now" and "Write past activities". Every change of permission happens in Health Connect, so the
 * status is asked again whenever the screen comes back ([refresh]).
 */
@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    private val gateway: HealthConnectGateway,
    private val preferences: LocalPreferences,
    private val writer: HealthConnectWriter,
    private val sync: HealthConnectSync,
    private val jobs: HealthConnectJobs,
) : ViewModel() {

    private val status = MutableStateFlow<HealthConnectStatus?>(null)
    private val transient = MutableStateFlow(Transient())

    private data class Transient(val busy: HealthBusy? = null, val message: String? = null)

    val uiState: StateFlow<HealthConnectUiState> = combine(
        status,
        preferences.healthWriteEnabled,
        preferences.healthReadEnabled,
        preferences.healthLastSyncMillis,
        transient,
    ) { status, write, read, last, t ->
        HealthConnectUiState(status, write, read, last, t.busy, t.message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HealthConnectUiState())

    private val effectChannel = Channel<HealthConnectEffect>(Channel.BUFFERED)

    /** One-shot effects; collect once from the screen. */
    val effects: Flow<HealthConnectEffect> = effectChannel.receiveAsFlow()

    init {
        refresh()
    }

    /** Asks Health Connect again (it may have been installed, updated or had permissions changed meanwhile). */
    fun refresh() {
        viewModelScope.launch { status.value = currentStatus() }
    }

    fun setWriteEnabled(enabled: Boolean) = setEnabled(SyncDirection.WRITE, enabled)

    fun setReadEnabled(enabled: Boolean) = setEnabled(SyncDirection.READ, enabled)

    /** "Allow again" on a switch Health Connect no longer allows. */
    fun requestAgain(direction: SyncDirection) = setEnabled(direction, true)

    private fun setEnabled(direction: SyncDirection, enabled: Boolean) {
        viewModelScope.launch {
            if (!enabled) {
                store(direction, false)
                return@launch
            }
            val current = currentStatus().also { status.value = it }
            if (!current.isAvailable) return@launch
            val missing = current.missing(direction)
            val allowed = if (direction == SyncDirection.WRITE) current.canWrite else current.canRead
            // Ask while anything is missing, but only insist on the essential permission.
            if (missing.isNotEmpty() && !allowed) {
                effectChannel.send(HealthConnectEffect.RequestPermissions(direction, missing))
            } else {
                switchOn(direction)
            }
        }
    }

    /** Health Connect's permission dialog closed with [granted] (everything the app now holds). */
    fun onPermissionsResult(direction: SyncDirection, @Suppress("UNUSED_PARAMETER") granted: Set<String>) {
        viewModelScope.launch {
            val before = status.value ?: currentStatus()
            // Health Connect itself is the truth: [granted] lists only what this dialog asked for.
            val after = currentStatus()
            status.value = after
            when (before.outcomeOf(direction, after.granted)) {
                PermissionRequestOutcome.DENIED -> {
                    store(direction, false)
                    say(
                        "Health Connect didn't allow it. If it no longer asks, open Health Connect, find StravaFit " +
                            "under App permissions and allow it there.",
                    )
                }
                PermissionRequestOutcome.PARTLY_GRANTED -> {
                    switchOn(direction)
                    val leftOut = after.leftOut(direction)
                    if (leftOut.isNotEmpty()) say("On, without ${leftOut.joinToString(" and ")}.")
                }
                PermissionRequestOutcome.GRANTED -> switchOn(direction)
            }
        }
    }

    /** Reads new workouts now. */
    fun syncNow() {
        if (transient.value.busy != null) return
        viewModelScope.launch {
            transient.update { it.copy(busy = HealthBusy.Syncing) }
            val text = try {
                syncMessage(sync.sync(inBackground = false))
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                status.value = currentStatus()
                "Health Connect no longer allows reading. Allow it again to sync."
            } catch (e: Exception) {
                "Couldn't read from Health Connect: ${e.message ?: e.javaClass.simpleName}"
            }
            transient.update { it.copy(busy = null, message = text) }
        }
    }

    /** Writes every activity already on the phone (Health Connect keeps one copy of each). */
    fun writePast() {
        if (transient.value.busy != null) return
        viewModelScope.launch {
            transient.update { it.copy(busy = HealthBusy.WritingPast(0, 0)) }
            val text = try {
                val summary = writer.writeAll { done, total -> transient.update { it.copy(busy = HealthBusy.WritingPast(done, total)) } }
                when {
                    summary.written == 0 && summary.failed == 0 -> "There are no activities to write yet."
                    summary.failed == 0 -> "Wrote ${activities(summary.written)} to Health Connect."
                    else -> "Wrote ${activities(summary.written)}; ${summary.failed} could not be written."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                status.value = currentStatus()
                "Health Connect no longer allows writing. Allow it again first."
            } catch (e: Exception) {
                "Couldn't write to Health Connect: ${e.message ?: e.javaClass.simpleName}"
            }
            transient.update { it.copy(busy = null, message = text) }
        }
    }

    fun messageShown() {
        transient.update { it.copy(message = null) }
    }

    private suspend fun switchOn(direction: SyncDirection) {
        when (direction) {
            SyncDirection.WRITE -> {
                // From now on: earlier activities go only with "Write past activities".
                writer.markCurrent()
                store(direction, true)
            }
            SyncDirection.READ -> {
                store(direction, true)
                syncNow()
            }
        }
    }

    private suspend fun store(direction: SyncDirection, enabled: Boolean) {
        when (direction) {
            SyncDirection.WRITE -> preferences.setHealthWriteEnabled(enabled)
            SyncDirection.READ -> {
                preferences.setHealthReadEnabled(enabled)
                if (enabled) jobs.scheduleDaily() else jobs.cancelDaily()
            }
        }
    }

    private fun say(text: String) = transient.update { it.copy(message = text) }

    private suspend fun currentStatus(): HealthConnectStatus {
        val availability = gateway.availability()
        if (availability != HealthConnectAvailability.AVAILABLE) return HealthConnectStatus(availability)
        return HealthConnectStatus(availability, gateway.grantedPermissions(), gateway.backgroundReadAvailable())
    }

    companion object {
        private fun activities(n: Int) = if (n == 1) "1 activity" else "$n activities"

        /** The snackbar after a read. */
        fun syncMessage(result: SyncResult): String = when (result.skipped) {
            SyncResult.Skipped.SWITCHED_OFF -> "Turn on reading from Health Connect first."
            SyncResult.Skipped.NOT_AVAILABLE -> "Health Connect isn't available on this phone."
            SyncResult.Skipped.NOT_ALLOWED -> "Health Connect no longer allows reading. Allow it again to sync."
            SyncResult.Skipped.BACKGROUND_NOT_ALLOWED -> "Reading in the background isn't allowed; StravaFit reads when you open it."
            null -> buildString {
                append(if (result.imported == 0) "No new workouts in Health Connect." else "Imported ${activities(result.imported)}.")
                if (result.duplicates > 0) append(" ${result.duplicates} already here.")
                if (result.failed > 0) append(" ${result.failed} couldn't be imported.")
                if (result.withoutMap > 0) {
                    append(" ${result.withoutMap} came without a map: Health Connect only shares another app's routes one workout at a time.")
                }
            }
        }
    }
}
