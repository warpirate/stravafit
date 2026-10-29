// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice

import kotlinx.coroutines.flow.StateFlow
import org.openactivity.model.SportType

/**
 * The one place the app records activities through. A Hilt singleton; the UI observes [state] and calls
 * the commands, the foreground service hosts the work while the screen is off.
 *
 * Commands are safe to call from any thread. Starting requires precise location permission to be useful,
 * but a recording without it still runs (timer only) and [RecordingUiSnapshot.gpsStatus] says why.
 */
interface RecordingController {
    /** Live recording state; [RecordingUiSnapshot.idle] when nothing is being recorded. */
    val state: StateFlow<RecordingUiSnapshot>

    /**
     * A journalled recording that is not running (the process died and was not restarted), or null.
     * Offer Resume ([recoverInterrupted]), Save now ([finishInterrupted]) or Discard ([discardInterrupted]).
     */
    val interruptedRecording: StateFlow<InterruptedRecording?>

    /**
     * Starts recording [sport], optionally following saved route [routeId], and starts the foreground
     * service. Call it while the app is visible (Android 12+ forbids starting the service from the
     * background).
     *
     * @throws IllegalStateException when a recording is already in progress.
     */
    suspend fun start(sport: SportType, routeId: Long? = null)

    /** Pauses the timer. Auto-pause never resumes a manual pause. No-op when not recording. */
    suspend fun pause()

    /** Resumes after a manual pause. No-op otherwise. */
    suspend fun resume()

    /** Closes the current lap. No-op when not recording. */
    suspend fun lap()

    /**
     * Finishes the recording, saves it as an activity (splits, laps and best efforts included) and returns
     * the new activity id, or null when nothing was being recorded.
     */
    suspend fun finish(): Long?

    /** Throws the recording away. No-op when nothing is being recorded. */
    suspend fun discard()

    /**
     * Restores an interrupted recording from the journal and continues it; the time the app was dead
     * becomes a gap that adds no distance or moving time. Returns true when a recording is now running.
     */
    suspend fun recoverInterrupted(): Boolean

    /** Restores an interrupted recording and saves it at once. Returns the activity id, or null. */
    suspend fun finishInterrupted(): Long?

    /** Deletes an interrupted recording's journal. */
    suspend fun discardInterrupted()
}
