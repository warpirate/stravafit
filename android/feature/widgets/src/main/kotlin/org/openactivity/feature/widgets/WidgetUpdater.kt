// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.widgets

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.openactivity.core.recordingservice.di.RecordingScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes [WidgetRepository.state] to every placed widget. Started once from the Application; it does
 * nothing while no widget is placed. The recording service keeps the process alive during a run, so
 * updates keep flowing with the app closed.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: WidgetRepository,
    @RecordingScope private val scope: CoroutineScope,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            repository.state.collect {
                try {
                    if (GlanceAppWidgetManager(context).getGlanceIds(StravaFitWidget::class.java).isNotEmpty()) {
                        StravaFitWidget().updateAll(context)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A launcher refusing an update must not take the recording scope down; the next state retries.
                }
            }
        }
    }
}

/** Glance creates the widget and its callbacks itself, so they reach Hilt through this entry point. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun repository(): WidgetRepository
    fun updater(): WidgetUpdater
}
