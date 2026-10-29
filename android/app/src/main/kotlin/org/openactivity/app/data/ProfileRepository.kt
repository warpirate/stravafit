// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.openactivity.app.data.sample.SampleAthletes
import org.openactivity.app.model.Athlete
import org.openactivity.app.model.Sport
import javax.inject.Inject
import javax.inject.Singleton

/** Totals for one sport over a period. Distance and elevation are null for indoor sports. */
data class SportTotals(
    val count: Int,
    val distanceMeters: Double?,
    val movingTimeSeconds: Long,
    val elevationMeters: Double?,
)

/**
 * Progress for one sport.
 *
 * @property weeklyDistanceMeters the last 12 weeks, oldest first; the last entry is this week.
 */
data class SportProgress(
    val sport: Sport,
    val thisWeek: SportTotals,
    val weeklyDistanceMeters: List<Double>,
    val yearToDate: SportTotals,
    val allTime: SportTotals,
)

/** The viewer's own profile header and progress. */
data class Profile(
    val athlete: Athlete,
    val followers: Int,
    val following: Int,
    val progress: List<SportProgress>,
)

/** Source of the viewer's profile and training totals. */
interface ProfileRepository {
    /** The signed-in athlete's profile. */
    val profile: Flow<Profile>
}

/** Static profile until `:core:data` computes totals from stored activities. */
@Singleton
class SampleProfileRepository @Inject constructor() : ProfileRepository {
    override val profile: Flow<Profile> = flowOf(SAMPLE)

    private companion object {
        val SAMPLE = Profile(
            athlete = SampleAthletes.me,
            followers = 198,
            following = 210,
            progress = listOf(
                SportProgress(
                    sport = Sport.Run,
                    thisWeek = SportTotals(count = 3, distanceMeters = 24_100.0, movingTimeSeconds = 7_800, elevationMeters = 120.0),
                    weeklyDistanceMeters = listOf(18.2, 22.5, 15.0, 26.1, 28.4, 12.0, 24.9, 30.2, 27.5, 21.0, 33.8, 24.1).map { it * 1000 },
                    yearToDate = SportTotals(count = 142, distanceMeters = 1_204_000.0, movingTimeSeconds = 388_800, elevationMeters = 9_800.0),
                    allTime = SportTotals(count = 1_021, distanceMeters = 8_450_000.0, movingTimeSeconds = 2_790_000, elevationMeters = 71_200.0),
                ),
                SportProgress(
                    sport = Sport.Ride,
                    thisWeek = SportTotals(count = 1, distanceMeters = 12_400.0, movingTimeSeconds = 2_460, elevationMeters = 88.0),
                    weeklyDistanceMeters = listOf(42.0, 0.0, 64.3, 38.5, 12.4, 55.0, 0.0, 71.2, 24.8, 36.0, 48.9, 12.4).map { it * 1000 },
                    yearToDate = SportTotals(count = 38, distanceMeters = 1_480_000.0, movingTimeSeconds = 198_000, elevationMeters = 14_300.0),
                    allTime = SportTotals(count = 214, distanceMeters = 7_920_000.0, movingTimeSeconds = 1_090_000, elevationMeters = 82_400.0),
                ),
                SportProgress(
                    sport = Sport.Walk,
                    thisWeek = SportTotals(count = 0, distanceMeters = 0.0, movingTimeSeconds = 0, elevationMeters = 0.0),
                    weeklyDistanceMeters = listOf(4.0, 6.2, 3.1, 0.0, 5.5, 7.8, 2.4, 0.0, 3.3, 4.1, 6.0, 0.0).map { it * 1000 },
                    yearToDate = SportTotals(count = 61, distanceMeters = 248_000.0, movingTimeSeconds = 205_000, elevationMeters = 1_900.0),
                    allTime = SportTotals(count = 302, distanceMeters = 1_102_000.0, movingTimeSeconds = 910_000, elevationMeters = 8_800.0),
                ),
            ),
        )
    }
}
