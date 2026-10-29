// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.data

import org.openactivity.model.SportType

/** What the run pace field shows (record detail 3.4). */
enum class PaceMode {
    /** Average pace of the split in progress (the default). */
    SPLIT,

    /** Smoothed instantaneous pace. */
    CURRENT,

    /** Average pace of the whole activity. */
    AVERAGE,
    ;

    /** The next mode when the pace field is tapped. */
    fun next(): PaceMode = entries[(ordinal + 1) % entries.size]
}

/**
 * Record-screen options that the shared settings do not cover. Kept on the device.
 *
 * @property countdown show a 3-2-1 countdown before the timer starts.
 * @property keepScreenOn keep the display awake while the Record screen shows a recording ("Screen display").
 * @property recentSports most recent first, at most [MAX_RECENT].
 * @property paceMode what the pace field shows for pace sports.
 * @property batteryPromptDismissed the athlete said "Not now" to the battery optimisation prompt.
 */
data class RecordPreferences(
    val countdown: Boolean = false,
    val keepScreenOn: Boolean = false,
    val recentSports: List<SportType> = emptyList(),
    val paceMode: PaceMode = PaceMode.SPLIT,
    val batteryPromptDismissed: Boolean = false,
) {
    companion object {
        /** Recent sports kept for the picker's chip row. */
        const val MAX_RECENT: Int = 4
    }
}
