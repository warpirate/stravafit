// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.fileimport

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openactivity.core.fileimport.ActivityImporter
import org.openactivity.core.fileimport.FileImportResult
import javax.inject.Inject

/** Outcome of one activity (or one failed file) in an import batch. */
enum class ImportStatus { IMPORTED, ALREADY_THERE, FAILED }

/**
 * One line of the import results list.
 *
 * @property activityId the stored activity to open, for imported and already-present activities.
 */
@Immutable
data class ImportRow(
    val fileName: String,
    val status: ImportStatus,
    val title: String?,
    val message: String?,
    val activityId: Long?,
)

/**
 * State of the import screen.
 *
 * @property running true while files are read and saved.
 * @property results rows of the last batch, in file order.
 */
@Immutable
data class ImportUiState(
    val running: Boolean = false,
    val results: List<ImportRow> = emptyList(),
) {
    /** Number of activities stored by the last batch. */
    val importedCount: Int get() = results.count { it.status == ImportStatus.IMPORTED }
}

/** One-shot effects of the import screen. */
sealed interface ImportEffect {
    /** Exactly one activity came out of the batch: open it. */
    data class OpenActivity(val activityId: Long) : ImportEffect
}

/**
 * Imports GPX, TCX and FIT files the user picked: parses them with [ActivityImporter], saves each
 * activity with [ActivityImportSaver] (which skips duplicates) and reports per-file results. When the
 * batch yields a single activity and nothing failed, the screen opens it straight away.
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    private val importer: ActivityImporter,
    private val saver: ActivityImportSaver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())

    /** Current screen state. */
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<ImportEffect>(Channel.BUFFERED)

    /** One-shot effects; collect once from the screen. */
    val effects: Flow<ImportEffect> = effectChannel.receiveAsFlow()

    private var job: Job? = null

    /** Imports [uris]; ignored while a batch is running or when nothing was picked. */
    fun import(uris: List<Uri>) {
        if (uris.isEmpty() || job?.isActive == true) return
        _uiState.value = ImportUiState(running = true)
        job = viewModelScope.launch {
            val rows = ArrayList<ImportRow>()
            try {
                for (result in importer.importAll(uris)) {
                    when (result) {
                        is FileImportResult.Failure -> rows += ImportRow(result.displayName, ImportStatus.FAILED, null, result.error.message, null)
                        is FileImportResult.Success -> result.activities.forEach { activity ->
                            rows += when (val outcome = saver.save(activity)) {
                                is SaveOutcome.Saved -> ImportRow(result.displayName, ImportStatus.IMPORTED, outcome.title, null, outcome.activityId)
                                is SaveOutcome.Duplicate -> ImportRow(result.displayName, ImportStatus.ALREADY_THERE, outcome.title, "Already in your activities", outcome.activityId)
                                is SaveOutcome.Rejected -> ImportRow(result.displayName, ImportStatus.FAILED, activity.title, outcome.reason, null)
                            }
                            _uiState.update { it.copy(results = rows.toList()) }
                        }
                    }
                    _uiState.update { it.copy(results = rows.toList()) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rows += ImportRow("", ImportStatus.FAILED, null, "Import stopped: ${e.message ?: e.javaClass.simpleName}", null)
            }
            _uiState.value = ImportUiState(running = false, results = rows.toList())
            val single = rows.singleOrNull()
            if (single?.activityId != null) effectChannel.send(ImportEffect.OpenActivity(single.activityId))
        }
    }
}
