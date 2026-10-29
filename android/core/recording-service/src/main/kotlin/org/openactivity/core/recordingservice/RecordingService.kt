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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.core.recordingservice.audio.CueSpeaker
import org.openactivity.core.recordingservice.notification.RecordingNotifications
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wakeLock: WakeLockHolder
    private var inForeground = false
    private var handledStart = false
    private var busy = 0 // recovery or finish in progress: do not stop while the state is briefly idle
    private var lastNotification: NotificationKey? = null

    private data class NotificationKey(val status: RecordingStatus, val seconds: Long, val tensOfMeters: Long)

    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
        wakeLock = WakeLockHolder(getSystemService(PowerManager::class.java)) { SystemClock.elapsedRealtime() }
        scope.launch { controller.state.collect(::render) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handledStart = true
        val restartedBySystem = intent == null || intent.action == ACTION_RECOVER
        if (!enterForeground()) {
            if (restartedBySystem) notifications.showInterrupted()
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_PAUSE -> command { controller.pause() }
            ACTION_RESUME -> command { controller.resume() }
            ACTION_LAP -> command { controller.lap() }
            ACTION_FINISH -> finishFromNotification()
            ACTION_START -> Unit
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
        if (::wakeLock.isInitialized) wakeLock.release()
        speaker.shutdown()
        super.onDestroy()
    }

    private fun recover() = whileBusy { controller.recoverInterrupted() }

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
            val key = NotificationKey(snapshot.status, snapshot.elapsed.inWholeSeconds, snapshot.distanceMeters.toLong() / 10)
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
    }
}
