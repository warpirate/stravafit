// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.saveedit

/** The 1..10 perceived-exertion scale with our own cue text for each level (save-edit-activity P1). */
object PerceivedExertion {
    /** Lowest rating. */
    const val MIN: Int = 1

    /** Highest rating. */
    const val MAX: Int = 10

    /** Rating offered when the athlete first opens the slider. */
    const val DEFAULT: Int = 5

    private val cues = listOf(
        "Very easy" to "Could keep going all day",
        "Easy" to "Relaxed, chatting freely",
        "Easy" to "Steady and comfortable",
        "Moderate" to "Breathing deeper, still talking",
        "Moderate" to "Working, short sentences only",
        "Moderately hard" to "Focused, a few words at a time",
        "Hard" to "Breathing heavily, could barely talk",
        "Very hard" to "Holding on, counting the minutes",
        "Very hard" to "Right at your limit",
        "Max effort" to "Nothing left at the end",
    )

    /** Short band name of [rating], e.g. "Hard". */
    fun band(rating: Int): String = cues[rating.coerceIn(MIN, MAX) - 1].first

    /** One-line description of how [rating] feels. */
    fun cue(rating: Int): String = cues[rating.coerceIn(MIN, MAX) - 1].second
}
