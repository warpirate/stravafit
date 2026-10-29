// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice

import android.os.PowerManager
import org.openactivity.recording.RecordingStatus

/**
 * When the recording service holds a partial wake lock.
 *
 * Location updates in a foreground service keep arriving with the screen off, but the one-second engine
 * tick and text-to-speech cues need the CPU awake on some OEM builds (recording design §5.1). The lock is
 * held while the timer runs or auto-pause waits for movement, and released during a manual pause, when
 * nothing time-critical happens until the athlete taps Resume.
 */
object WakeLockPolicy {
    /** Whether a recording in [status] needs the CPU awake. */
    fun shouldHold(status: RecordingStatus): Boolean =
        status == RecordingStatus.RECORDING || status == RecordingStatus.AUTO_PAUSED

    /** Each acquisition times out after this long, so a bug can never drain the battery indefinitely. */
    const val TIMEOUT_MILLIS: Long = 30 * 60 * 1000L

    /** Re-acquire this long before the timeout runs out. */
    const val RENEW_AFTER_MILLIS: Long = 20 * 60 * 1000L
}

/** Applies [WakeLockPolicy] to a partial wake lock. Main thread only. */
internal class WakeLockHolder(powerManager: PowerManager, private val now: () -> Long) {
    private val lock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG).apply { setReferenceCounted(false) }
    private var acquiredAt = Long.MIN_VALUE

    /** Holds or releases the lock for [status], renewing a held lock before it times out. */
    fun update(status: RecordingStatus) {
        if (WakeLockPolicy.shouldHold(status)) {
            val t = now()
            if (!lock.isHeld || t - acquiredAt >= WakeLockPolicy.RENEW_AFTER_MILLIS) {
                lock.acquire(WakeLockPolicy.TIMEOUT_MILLIS)
                acquiredAt = t
            }
        } else {
            release()
        }
    }

    fun release() {
        if (lock.isHeld) lock.release()
        acquiredAt = Long.MIN_VALUE
    }

    private companion object {
        const val TAG = "OpenActivity:recording"
    }
}
