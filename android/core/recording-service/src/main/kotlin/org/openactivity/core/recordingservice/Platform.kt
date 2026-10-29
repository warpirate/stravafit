// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject

/** Clocks the controller reads. Abstracted so tests can drive time. */
interface RecordingClock {
    /** Monotonic time since boot (`SystemClock.elapsedRealtimeNanos`), the same base location fixes use. */
    fun elapsedRealtimeNanos(): Long

    /** Wall-clock time; only labels the start and sizes gaps after a reboot. */
    fun wallClockMillis(): Long

    /** Zone the activity is recorded in. */
    fun zone(): ZoneId
}

/** The device clocks. */
class SystemRecordingClock @Inject constructor() : RecordingClock {
    override fun elapsedRealtimeNanos(): Long = SystemClock.elapsedRealtimeNanos()

    override fun wallClockMillis(): Long = System.currentTimeMillis()

    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/** Starts the foreground service that keeps a recording alive with the screen off. */
fun interface RecordingServiceLauncher {
    /** Starts (or re-pokes) the service. Must be called while the app is in the foreground. */
    fun start()
}

/** Starts [RecordingService] as a foreground service. */
class AndroidRecordingServiceLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) : RecordingServiceLauncher {
    override fun start() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_START),
        )
    }
}
