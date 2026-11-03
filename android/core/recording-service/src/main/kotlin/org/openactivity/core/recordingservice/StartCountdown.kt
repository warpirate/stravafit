// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.openactivity.core.location.GpsStatus
import org.openactivity.model.SportType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A start that is counting down before the recording begins (started from the home-screen widget).
 *
 * @property sport the sport that will be recorded.
 * @property routeId saved route to follow, or null.
 * @property endsAtElapsedRealtime `SystemClock.elapsedRealtime()` when the countdown ends; the widget hands
 *   it to a count-down `Chronometer`, so the digits tick without updates from us.
 * @property gps the GPS status since the countdown began (GPS warms up during it), or null before the first reading.
 * @property waitingForGps the countdown is over but there is no fix yet: like the record screen, the
 *   recording only starts once GPS has a fix, or when the athlete taps "Start anyway"
 *   ([RecordingIntents.startNow]).
 */
data class PendingStart(
    val sport: SportType,
    val routeId: Long?,
    val endsAtElapsedRealtime: Long,
    val gps: GpsStatus? = null,
    val waitingForGps: Boolean = false,
)

/**
 * Whether a start of [sport] has to wait for a fix: GPS sports start only with a fix (weak counts), the
 * same rule as the record screen. Starting without one would record the first minutes without a position.
 */
fun needsGpsFix(sport: SportType, gps: GpsStatus?): Boolean = sport.usesGps && gps?.hasFix != true

/** The countdown in progress, if any. Set and cleared by [RecordingService]; observed by the widget. */
@Singleton
class StartCountdown @Inject constructor() {
    private val _pending = MutableStateFlow<PendingStart?>(null)
    val pending: StateFlow<PendingStart?> = _pending.asStateFlow()

    internal fun set(value: PendingStart?) {
        _pending.value = value
    }
}

/**
 * Intents that drive [RecordingService] from outside the app's screens (the home-screen widget).
 *
 * Send them with `startForegroundService` from a direct user tap: Android exempts a service started by
 * interacting with an app widget from the background-start and while-in-use location restrictions, so
 * recording can start without opening the app. Going through a broadcast or a coroutine first may lose
 * that exemption.
 */
object RecordingIntents {
    /** Seconds between the tap and the recording starting; the tap can still be cancelled. */
    const val DEFAULT_COUNTDOWN_SECONDS: Int = 3

    fun start(context: Context, sport: SportType, routeId: Long? = null, countdownSeconds: Int = DEFAULT_COUNTDOWN_SECONDS): Intent =
        service(context, RecordingService.ACTION_WIDGET_START)
            .putExtra(RecordingService.EXTRA_SPORT, sport.name)
            .putExtra(RecordingService.EXTRA_COUNTDOWN_SECONDS, countdownSeconds)
            .apply { if (routeId != null) putExtra(RecordingService.EXTRA_ROUTE_ID, routeId) }

    fun cancelStart(context: Context): Intent = service(context, RecordingService.ACTION_CANCEL_START)

    /** "Start anyway": ends the countdown or the wait for GPS and starts recording without a fix. */
    fun startNow(context: Context): Intent = service(context, RecordingService.ACTION_START_NOW)
    fun pause(context: Context): Intent = service(context, RecordingService.ACTION_PAUSE)
    fun resume(context: Context): Intent = service(context, RecordingService.ACTION_RESUME)
    fun lap(context: Context): Intent = service(context, RecordingService.ACTION_LAP)
    fun finish(context: Context): Intent = service(context, RecordingService.ACTION_FINISH)
    fun recover(context: Context): Intent = service(context, RecordingService.ACTION_RECOVER)

    private fun service(context: Context, action: String): Intent =
        Intent(context, RecordingService::class.java).setAction(action)
}
