// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.widgets

import android.content.Context
import android.content.SharedPreferences
import android.os.PowerManager
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.openactivity.core.data.model.SavedActivity
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.core.recordingservice.RecordingController
import org.openactivity.core.recordingservice.StartCountdown
import org.openactivity.core.recordingservice.di.RecordingScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

/** Small widget-only settings that outlive the process: the Finish confirm window and the dismissed Done. */
@Singleton
class WidgetPrefs @Inject constructor(@ApplicationContext context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("stravafit_widget", Context.MODE_PRIVATE)

    data class Values(val finishArmedUntilMillis: Long?, val dismissedActivityId: Long?)

    val values: Flow<Values> = callbackFlow {
        trySend(read())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(read()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun read(): Values = Values(
        finishArmedUntilMillis = prefs.getLong(KEY_ARMED, 0L).takeIf { it > 0 },
        dismissedActivityId = prefs.getLong(KEY_DISMISSED, -1L).takeIf { it >= 0 },
    )

    fun armFinish(untilMillis: Long) = prefs.edit().putLong(KEY_ARMED, untilMillis).apply()
    fun disarmFinish() = prefs.edit().remove(KEY_ARMED).apply()
    fun dismiss(activityId: Long) = prefs.edit().putLong(KEY_DISMISSED, activityId).apply()

    private companion object {
        const val KEY_ARMED = "finish_armed_until"
        const val KEY_DISMISSED = "dismissed_activity"
    }
}

/**
 * The widget's state for every placed widget, throttled for the home screen (widgets-spec.md, H1):
 * the clock ticks by itself, so a new state is only published when something visible changes: status,
 * lap, the Finish confirm, every 10 m of distance, or every 5 s of time (for the live pace). With the screen
 * off, only status changes go through.
 */
@Singleton
class WidgetRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    controller: RecordingController,
    countdown: StartCountdown,
    activities: ActivityRepository,
    private val prefs: WidgetPrefs,
    @RecordingScope scope: CoroutineScope,
) {
    private val power = context.getSystemService(PowerManager::class.java)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val prefsWithExpiry: Flow<WidgetPrefs.Values> = prefs.values.flatMapLatest { values ->
        flow {
            emit(values)
            // Re-evaluate when the Finish confirm window closes, so "Tap again to save" goes away by itself.
            val wait = (values.finishArmedUntilMillis ?: 0L) - System.currentTimeMillis()
            if (wait > 0) {
                delay(wait + 50)
                emit(values.copy(finishArmedUntilMillis = null))
            }
        }
    }

    private var lastKey: RenderKey? = null

    val state: StateFlow<WidgetState> = combine(
        controller.state,
        countdown.pending,
        controller.interruptedRecording,
        activities.observeLatestActivity().map { it?.toSummary() },
        prefsWithExpiry,
    ) { snapshot, pending, interrupted, latest, values ->
        resolveWidgetState(
            snapshot = snapshot,
            pending = pending,
            interrupted = interrupted != null,
            lastSaved = latest,
            dismissedActivityId = values.dismissedActivityId,
            finishArmedUntilMillis = values.finishArmedUntilMillis,
            nowMillis = System.currentTimeMillis(),
            elapsedRealtimeNow = SystemClock.elapsedRealtime(),
        )
    }
        .filter { state -> shouldPublish(state) }
        .distinctUntilChanged { a, b -> renderKey(a) == renderKey(b) }
        .stateIn(scope, SharingStarted.Eagerly, WidgetState.Ready(DEFAULT_SPORTS))

    /** Drops in-between states while the screen is off; a change of state always goes through. */
    private fun shouldPublish(state: WidgetState): Boolean {
        val key = renderKey(state)
        val stateChanged = lastKey?.kind != key.kind || lastKey?.status != key.status
        val publish = stateChanged || power?.isInteractive != false
        if (publish) lastKey = key
        return publish
    }

    fun armFinish() = prefs.armFinish(System.currentTimeMillis() + FINISH_CONFIRM_WINDOW.inWholeMilliseconds)
    fun dismissDone(activityId: Long) = prefs.dismiss(activityId)
}

/** What has to differ for a new widget update to be worth sending. */
internal data class RenderKey(val kind: String, val status: String, val detail: List<Any?>)

internal fun renderKey(state: WidgetState): RenderKey = when (state) {
    is WidgetState.Ready -> RenderKey("ready", "", state.sports)
    is WidgetState.Countdown -> RenderKey("countdown", state.sport.name, listOf(state.endsAtElapsedRealtime / 1000))
    is WidgetState.Recording -> RenderKey(
        kind = "recording",
        status = if (state.paused) "paused" else "running",
        detail = listOf(
            state.sport, state.lapNumber, state.finishArmed, state.units,
            // The Chronometer base only moves on pause/resume; 1 s of jitter is not a change. While paused the
            // base drifts with the wall clock but the frozen time is what shows, so it is left out.
            if (state.paused) null else state.clockBaseElapsedRealtime / 1000,
            floor(state.distanceMeters / DISTANCE_STEP_METERS).toLong(),
            state.timer.inWholeSeconds / TIME_STEP_SECONDS,
        ),
    )
    is WidgetState.Done -> RenderKey("done", state.activity.id.toString(), emptyList())
    WidgetState.Interrupted -> RenderKey("interrupted", "", emptyList())
}

private const val DISTANCE_STEP_METERS = 10.0
private const val TIME_STEP_SECONDS = 5L

internal fun SavedActivity.toSummary() = SavedSummary(
    id = id,
    sport = sport,
    title = title,
    endTime = startTime.plusMillis(elapsedTime.inWholeMilliseconds),
    zone = timeZone,
    movingTime = movingTime,
    distanceMeters = distanceMeters,
    averageSpeedMps = averageSpeedMps,
)
