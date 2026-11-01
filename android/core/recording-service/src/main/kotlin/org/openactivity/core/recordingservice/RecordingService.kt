// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.core.recordingservice.audio.CueSpeaker
import org.openactivity.core.recordingservice.notification.RecordingNotifications
import org.openactivity.model.SportType
import org.openactivity.recording.RecordingStatus
import javax.inject.Inject

/**
 * Foreground service (type `location`) that keeps a recording alive with the screen off.
 *
 * The [RecordingController] does the work; this service holds the process in the foreground, shows the
 * ongoing notification with Pause/Resume and Finish, applies the [WakeLockPolicy] and stops itself when
 * the recording ends. It is `START_STICKY`: if the system kills the process mid-recording and restarts
 * the service, it restores the recording from the journal. If the system refuses to bring it back to the
 * foreground (a background start on Android 12+, or location access no longer allowed on 14+), it posts
 * "Recording stopped unexpectedly – tap to resume" and the app offers recovery when opened.
 */
@AndroidEntryPoint
class RecordingService : Service() {
    @Inject lateinit var controller: RecordingController

    @Inject lateinit var notifications: RecordingNotifications

    @Inject lateinit var speaker: CueSpeaker

    @Inject lateinit var activities: ActivityRepository

    @Inject lateinit var countdown: StartCountdown

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wakeLock: WakeLockHolder
    private var inForeground = false
    private var handledStart = false
    private var busy = 0 // recovery or finish in progress: do not stop while the state is briefly idle
    private var lastNotification: NotificationKey? = null
    private var countdownJob: Job? = null

    private data class NotificationKey(val status: RecordingStatus, val seconds: Long, val tensOfMeters: Long)

    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
        wakeLock = WakeLockHolder(getSystemService(PowerManager::class.java)) { SystemClock.elapsedRealtime() }
        scope.launch { controller.state.collect(::render) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handledStart = true
        // Anything but a fresh start from the app (a sticky restart, or a notification action reaching a
        // process that died) is about a recording that may only live in the journal now.
        val needsRecovery = intent?.action !in FRESH_ACTIONS
        if (!enterForeground()) {
            // Tell the athlete instead of failing silently; the app offers recovery when opened.
            if (needsRecovery && !controller.state.value.isActive) notifications.showInterrupted()
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_PAUSE -> command { controller.pause() }
            ACTION_RESUME -> command { controller.resume() }
            ACTION_LAP -> command { controller.lap() }
            ACTION_FINISH -> finishFromNotification()
            ACTION_START -> Unit
            ACTION_WIDGET_START -> startAfterCountdown(intent)
            ACTION_CANCEL_START -> countdownJob?.cancel()
            else -> recover()
        }
        render(controller.state.value)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away must not end the recording: the foreground service keeps running.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handledStart = false // cancelled work must not render (and stop) a destroyed service
        scope.cancel()
        countdown.set(null)
        if (::wakeLock.isInitialized) wakeLock.release()
        speaker.shutdown()
        super.onDestroy()
    }

    private fun recover() = whileBusy { controller.recoverInterrupted() }

    /**
     * Start from the widget: count down (cancellable with [ACTION_CANCEL_START]), then record. The service is
     * already in the foreground, so GPS can warm up and the countdown survives the launcher going away.
     * A second tap while counting down, or a tap while already recording, does nothing.
     */
    private fun startAfterCountdown(intent: Intent) {
        if (controller.state.value.isActive || countdownJob?.isActive == true) return
        val sport = intent.getStringExtra(EXTRA_SPORT)?.let { name -> SportType.entries.firstOrNull { it.name == name } } ?: return
        val routeId = intent.getLongExtra(EXTRA_ROUTE_ID, -1L).takeIf { it >= 0 }
        val seconds = intent.getIntExtra(EXTRA_COUNTDOWN_SECONDS, RecordingIntents.DEFAULT_COUNTDOWN_SECONDS).coerceIn(0, MAX_COUNTDOWN_SECONDS)
        busy++
        countdown.set(PendingStart(sport, routeId, SystemClock.elapsedRealtime() + seconds * 1000L))
        countdownJob = scope.launch {
            try {
                delay(seconds * 1000L)
                countdown.set(null)
                controller.start(sport, routeId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Already recording (a race with the app) or the journal failed: nothing was started.
            } finally {
                countdown.set(null)
                busy--
                render(controller.state.value)
            }
        }
    }

    /**
     * Applies a notification action. The action can arrive after the process died without a sticky
     * restart (the notification outlives it), so bring the recording back from the journal first;
     * otherwise the tap would silently do nothing.
     */
    private fun command(action: suspend () -> Unit) = whileBusy {
        if (!controller.state.value.isActive) controller.recoverInterrupted()
        action()
    }

    private fun finishFromNotification() = whileBusy {
        val id = if (controller.state.value.isActive) controller.finish() else controller.finishInterrupted()
        if (id != null) {
            val title = activities.observeActivity(id).first()?.title.orEmpty()
            notifications.showSaved(id, title)
        }
    }

    /**
     * Runs [work] while keeping the service up: the controller is briefly idle during recovery and while
     * saving, and the service must not stop then. Failures leave the journal in place, so the recording
     * shows up as interrupted rather than being lost.
     */
    private fun whileBusy(work: suspend () -> Unit) {
        busy++
        scope.launch {
            try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Journal or database failure: nothing more to do here (see above).
            } finally {
                busy--
                render(controller.state.value)
            }
        }
    }

    private fun render(snapshot: RecordingUiSnapshot) {
        if (!handledStart) return
        if (snapshot.isActive) {
            wakeLock.update(snapshot.status)
            // State changes several times a second; the notification only needs whole seconds.
            val key = NotificationKey(snapshot.status, snapshot.timerTime.inWholeSeconds, snapshot.distanceMeters.toLong() / 10)
            if (inForeground && key != lastNotification) {
                lastNotification = key
                notifications.update(snapshot)
            }
        } else if (busy == 0) {
            wakeLock.release()
            stop()
        }
    }

    private fun enterForeground(): Boolean {
        if (inForeground) return true
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        return try {
            ServiceCompat.startForeground(
                this,
                RecordingNotifications.ID_ONGOING,
                notifications.ongoing(controller.state.value),
                type,
            )
            inForeground = true
            true
        } catch (e: IllegalStateException) {
            false // ForegroundServiceStartNotAllowedException: started from the background (Android 12+)
        } catch (e: SecurityException) {
            false // location permission missing or not usable from the background (Android 14+)
        }
    }

    private fun stop() {
        if (inForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            inForeground = false
        }
        stopSelf()
    }

    companion object {
        /** Sent by [RecordingController.start]: enter the foreground for the new recording. */
        const val ACTION_START: String = "org.openactivity.recording.START"

        /** Restore an interrupted recording from the journal (also what a sticky restart does). */
        const val ACTION_RECOVER: String = "org.openactivity.recording.RECOVER"
        const val ACTION_PAUSE: String = "org.openactivity.recording.PAUSE"
        const val ACTION_RESUME: String = "org.openactivity.recording.RESUME"
        const val ACTION_LAP: String = "org.openactivity.recording.LAP"
        const val ACTION_FINISH: String = "org.openactivity.recording.FINISH"

        /** From the widget: count down, then start recording [EXTRA_SPORT] (see [RecordingIntents.start]). */
        const val ACTION_WIDGET_START: String = "org.openactivity.recording.WIDGET_START"

        /** Cancels a widget countdown before anything is recorded. */
        const val ACTION_CANCEL_START: String = "org.openactivity.recording.CANCEL_START"

        const val EXTRA_SPORT: String = "org.openactivity.recording.extra.SPORT"
        const val EXTRA_ROUTE_ID: String = "org.openactivity.recording.extra.ROUTE_ID"
        const val EXTRA_COUNTDOWN_SECONDS: String = "org.openactivity.recording.extra.COUNTDOWN_SECONDS"

        private const val MAX_COUNTDOWN_SECONDS = 10

        /** Actions that begin something new rather than being about a recording that may need recovery. */
        private val FRESH_ACTIONS = setOf(ACTION_START, ACTION_WIDGET_START, ACTION_CANCEL_START)
    }
}
