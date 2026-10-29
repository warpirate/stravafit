// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice.audio

import org.openactivity.core.datastore.AudioCueSettings
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.recording.RecordingState
import org.openactivity.recording.RecordingStatus
import kotlin.math.floor
import kotlin.time.Duration

/** Something worth saying out loud during a recording. */
sealed interface AudioCue {
    data class Started(val sport: SportType) : AudioCue

    data object Paused : AudioCue

    data object Resumed : AudioCue

    data object AutoPaused : AudioCue

    data object AutoResumed : AudioCue

    data object Saved : AudioCue

    /**
     * A distance milestone.
     *
     * @property distanceMeters the milestone reached (a multiple of the interval).
     * @property movingTime total moving time at the milestone.
     * @property intervalPaceSecondsPerUnit average pace over the last interval in seconds per km or mile.
     */
    data class Milestone(
        val distanceMeters: Double,
        val movingTime: Duration,
        val intervalPaceSecondsPerUnit: Double?,
    ) : AudioCue
}

/**
 * Decides which cues to speak from successive engine states (record detail 7.1-7.4).
 *
 * Milestones fire every [AudioCueSettings.distanceInterval] kilometres or miles. Pause and resume cues fire
 * on status changes; auto-pause cues only when [AudioCueSettings.announceAutoPause] is on. A planner
 * created for a restored recording starts from its current distance, so nothing is announced twice.
 */
class AudioCuePlanner(
    private val settings: AudioCueSettings,
    private val units: UnitSystem,
    initialState: RecordingState? = null,
) {
    private val intervalMeters = settings.distanceInterval * units.splitLengthMeters
    private var lastStatus: RecordingStatus = initialState?.status ?: RecordingStatus.IDLE
    private var nextMilestone: Int = initialState?.let { floor(it.distanceMeters / intervalMeters).toInt() + 1 } ?: 1
    private var lastMilestoneMoving: Duration = initialState?.movingTime ?: Duration.ZERO

    /** Cues triggered by moving to [state]. Empty when cues are switched off. */
    fun onState(state: RecordingState): List<AudioCue> {
        val previous = lastStatus
        lastStatus = state.status
        if (!settings.enabled) return emptyList()
        val cues = mutableListOf<AudioCue>()
        statusCue(previous, state.status)?.let(cues::add)
        if (state.status.isActive && state.distanceMeters >= nextMilestone * intervalMeters) {
            val reached = floor(state.distanceMeters / intervalMeters).toInt()
            val intervals = reached - nextMilestone + 1
            val moving = state.movingTime - lastMilestoneMoving
            val units = intervals * settings.distanceInterval
            cues += AudioCue.Milestone(
                distanceMeters = reached * intervalMeters,
                movingTime = state.movingTime,
                intervalPaceSecondsPerUnit = if (moving.isPositive()) moving.inWholeMilliseconds / 1000.0 / units else null,
            )
            nextMilestone = reached + 1
            lastMilestoneMoving = state.movingTime
        }
        return cues
    }

    private fun statusCue(from: RecordingStatus, to: RecordingStatus): AudioCue? = when {
        from == to -> null
        to == RecordingStatus.PAUSED && from.isActive -> AudioCue.Paused
        from == RecordingStatus.PAUSED && to == RecordingStatus.RECORDING -> AudioCue.Resumed
        from == RecordingStatus.RECORDING && to == RecordingStatus.AUTO_PAUSED ->
            AudioCue.AutoPaused.takeIf { settings.announceAutoPause }
        from == RecordingStatus.AUTO_PAUSED && to == RecordingStatus.RECORDING ->
            AudioCue.AutoResumed.takeIf { settings.announceAutoPause }
        else -> null
    }
}

/** Speaks cues. Implementations must never block the caller. */
interface CueSpeaker {
    fun speak(cue: AudioCue, units: UnitSystem)

    /** Releases the speech engine (called when the recording service stops). */
    fun shutdown()
}
