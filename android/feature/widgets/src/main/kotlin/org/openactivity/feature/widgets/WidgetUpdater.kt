// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.widgets

import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.openactivity.core.recordingservice.di.RecordingScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes new data to every placed widget: [WidgetRepository.state] to the recording widget, and this
 * week's totals and the latest activity to the "This week" and "Last activity" widgets. Started once from
 * the Application; it does nothing for a widget that is not placed. The recording service keeps the process
 * alive during a run, so updates keep flowing with the app closed.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    // Lazy: the repository starts observing the recording as soon as it exists, which only start() may cause.
    private val repository: dagger.Lazy<WidgetRepository>,
    private val summaries: SummaryRepository,
    @RecordingScope private val scope: CoroutineScope,
) {
    private var started = false

    fun start() {
        // Host-side tests (Robolectric) build the real Application; they must not start background work,
        // which would keep the database open across tests.
        if (started || Build.FINGERPRINT == ROBOLECTRIC) return
        started = true
        scope.launch { repository.get().state.collect { update(StravaFitWidget::class.java) { StravaFitWidget() } } }
        // The first value of each is what the widget already shows when it starts; only changes are pushed.
        scope.launch { summaries.week().drop(1).collect { update(WeekWidget::class.java) { WeekWidget() } } }
        scope.launch { summaries.last().drop(1).collect { update(LastActivityWidget::class.java) { LastActivityWidget() } } }
    }

    private suspend fun <T : GlanceAppWidget> update(type: Class<T>, widget: () -> T) {
        try {
            if (GlanceAppWidgetManager(context).getGlanceIds(type).isNotEmpty()) widget().updateAll(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A launcher refusing an update must not take the recording scope down; the next change retries.
        }
    }

    private companion object {
        const val ROBOLECTRIC = "robolectric"
    }
}

/** Glance creates the widget and its callbacks itself, so they reach Hilt through this entry point. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun repository(): WidgetRepository
    fun updater(): WidgetUpdater
    fun summaries(): SummaryRepository
}
