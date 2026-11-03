// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.stravaimport

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** Starts and stops the background import; an interface so the screen is tested without WorkManager. */
interface StravaImportRunner {
    /** Keeps read access to a picked document across restarts; false when the provider does not allow it. */
    fun keepAccess(uri: Uri): Boolean

    /** The file's name as the user knows it. */
    fun displayName(uri: Uri): String

    /** Runs or continues the current job; [restart] replaces a worker still around. */
    fun enqueue(restart: Boolean)

    fun cancel()
}

/** [StravaImportRunner] over WorkManager and the content resolver. */
class WorkManagerStravaImportRunner @Inject constructor(@ApplicationContext private val context: Context) : StravaImportRunner {
    override fun keepAccess(uri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (e: SecurityException) {
        false
    }

    override fun displayName(uri: Uri): String {
        val queried = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: RuntimeException) {
            null
        }
        return queried?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Strava export"
    }

    override fun enqueue(restart: Boolean) = StravaImportWorker.enqueue(context, restart)

    override fun cancel() = StravaImportWorker.cancel(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class StravaImportModule {
    @Binds abstract fun runner(impl: WorkManagerStravaImportRunner): StravaImportRunner
}

/** A ZIP shared to the app, waiting for the user to start the import. */
@Immutable
data class PendingExport(val uri: String, val name: String)

/**
 * State of the Strava import screen.
 *
 * @property job the current or last import, null before the first one.
 * @property pending a shared ZIP not started yet.
 */
@Immutable
data class StravaImportUiState(
    val job: StravaImportJob? = null,
    val pending: PendingExport? = null,
) {
    /** What the screen shows. */
    val mode: Mode
        get() = when {
            job?.isActive == true -> Mode.RUNNING
            pending != null -> Mode.READY
            job == null -> Mode.INTRO
            job.state == StravaImportState.CANCELLED -> Mode.CANCELLED
            job.state == StravaImportState.FAILED -> Mode.FAILED
            else -> Mode.DONE
        }

    enum class Mode { INTRO, READY, RUNNING, CANCELLED, FAILED, DONE }
}

/**
 * "Import from Strava": picks the export ZIP (or takes one shared to the app), runs [StravaImportWorker]
 * and shows its progress and summary. The job outlives the screen and the app process; this view model
 * only reads [StravaImportStore].
 */
@HiltViewModel
class StravaImportViewModel @Inject constructor(
    private val store: StravaImportStore,
    private val runner: StravaImportRunner,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val pending = MutableStateFlow<PendingExport?>(null)

    val uiState: StateFlow<StravaImportUiState> = combine(store.job, pending) { job, shared ->
        StravaImportUiState(job, shared)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StravaImportUiState(store.job.value, null))

    init {
        savedStateHandle.get<String>(ARG_SHARED_URI)?.takeIf { it.isNotBlank() }?.let { shared ->
            if (store.job.value?.isActive != true) pending.value = PendingExport(shared, runner.displayName(Uri.parse(shared)))
        }
        // An import left running when the app died picks up again (WorkManager usually has, already).
        if (store.job.value?.isActive == true) runner.enqueue(restart = false)
    }

    /** Starts importing the ZIP the user picked. */
    fun choose(uri: Uri) {
        if (store.job.value?.isActive == true) return
        pending.value = null
        start(uri.toString(), runner.displayName(uri), needsCopy = !runner.keepAccess(uri))
    }

    /** Starts importing the shared ZIP. Shared files are copied in first: their access ends with the share. */
    fun startPending() {
        val shared = pending.value ?: return
        if (store.job.value?.isActive == true) return
        pending.value = null
        val uri = Uri.parse(shared.uri)
        start(shared.uri, shared.name, needsCopy = !runner.keepAccess(uri))
    }

    /** Forgets the shared ZIP. */
    fun dismissPending() {
        pending.value = null
    }

    fun cancel() {
        if (store.job.value?.isActive != true) return
        store.update { it.copy(state = StravaImportState.CANCELLED) }
        runner.cancel()
    }

    /** Continues a cancelled or failed import; rows already done are not done again. */
    fun resume() {
        val job = store.job.value ?: return
        if (job.state != StravaImportState.CANCELLED && job.state != StravaImportState.FAILED) return
        store.update { it.copy(state = StravaImportState.QUEUED, error = null, finishedAtMillis = null) }
        runner.enqueue(restart = true)
    }

    private fun start(uri: String, name: String, needsCopy: Boolean) {
        viewModelScope.launch {
            store.begin(StravaImportJob(sourceUri = uri, sourceName = name, needsCopy = needsCopy, startedAtMillis = clock.millis()))
            runner.enqueue(restart = true)
        }
    }

    companion object {
        /** The name of [org.openactivity.app.navigation.StravaImportRoute.sharedUri]. */
        const val ARG_SHARED_URI = "sharedUri"

        /** What the picker offers: ZIPs as providers label them. */
        val MIME_TYPES: Array<String> = arrayOf("application/zip", "application/x-zip-compressed", "application/x-zip", "application/octet-stream")
    }
}
