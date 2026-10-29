// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.data.sample

import org.openactivity.app.model.Activity
import org.openactivity.app.model.Athlete
import org.openactivity.app.model.LatLng
import org.openactivity.app.model.Sport
import org.openactivity.app.model.Visibility
import java.time.Duration
import java.time.Instant

/** Static sample people. Fictional names; any resemblance is coincidental. */
object SampleAthletes {
    val me = Athlete(id = "me", name = "Kavya Rao", location = "Pune, India")
    val ana = Athlete(id = "a1", name = "Ana Ruiz", location = "Pune")
    val dev = Athlete(id = "a2", name = "Dev Kulkarni", location = "Lonavala")
    val li = Athlete(id = "a3", name = "Li Wei", location = "Pune")
    val sam = Athlete(id = "a4", name = "Sam Okafor", location = "Mumbai")
    val mira = Athlete(id = "a5", name = "Mira Holm", location = "Pune")
}

/** Sample activities, timestamped relative to [now] so relative labels stay realistic. */
object SampleActivities {

    private val pune = LatLng(18.5204, 73.8567)
    private val lonavala = LatLng(18.7546, 73.4062)
    private val sinhagad = LatLng(18.3663, 73.7559)

    /** Feed from people you follow plus your own activities, newest first. */
    fun feed(now: Instant): List<Activity> = listOf(
        Activity(
            id = "act-101",
            athlete = SampleAthletes.ana,
            sport = Sport.Run,
            title = "Easy morning shakeout",
            description = "Felt good after the rest day. Legs finally fresh again.",
            startTime = now - Duration.ofMinutes(48),
            distanceMeters = 8_020.0,
            movingTimeSeconds = 2_576,
            elevationGainMeters = 46.0,
            route = SampleRoutes.loop(pune, radiusKm = 1.1, seed = 11),
            achievementCount = 2,
            topAchievement = "2nd best 5K",
            highFivers = listOf("Dev Kulkarni", "Li Wei", "Sam Okafor", "Mira Holm") + List(8) { "Runner $it" },
            commentCount = 3,
        ),
        Activity(
            id = "act-102",
            athlete = SampleAthletes.me,
            sport = Sport.Run,
            title = "Tempo Tuesday",
            startTime = now - Duration.ofHours(5),
            distanceMeters = 10_200.0,
            movingTimeSeconds = 3_184,
            elevationGainMeters = 64.0,
            route = SampleRoutes.outAndBack(pune, lengthKm = 4.9, bearingDeg = 35.0, seed = 21),
            achievementCount = 1,
            topAchievement = "Best 10K this year",
            highFivers = listOf("Ana Ruiz", "Mira Holm", "Li Wei"),
            commentCount = 1,
            isOwn = true,
            visibility = Visibility.Followers,
        ),
        Activity(
            id = "act-103",
            athlete = SampleAthletes.dev,
            sport = Sport.Ride,
            title = "Sunday hill loop",
            startTime = now - Duration.ofHours(29),
            distanceMeters = 64_300.0,
            movingTimeSeconds = 9_060,
            elevationGainMeters = 812.0,
            route = SampleRoutes.loop(lonavala, radiusKm = 8.5, seed = 31, lobes = 2),
            highFivers = listOf("Sam Okafor", "Ana Ruiz"),
            highFiveGiven = false,
            commentCount = 0,
            withAthletes = listOf("Sam Okafor", "Li Wei", "Mira Holm"),
        ),
        Activity(
            id = "act-104",
            athlete = SampleAthletes.mira,
            sport = Sport.WeightTraining,
            title = "Lower body strength",
            startTime = now - Duration.ofHours(52),
            distanceMeters = null,
            movingTimeSeconds = 2_820,
            averageHeartRate = 118,
            calories = 310,
            highFivers = listOf("Ana Ruiz"),
            commentCount = 0,
        ),
        Activity(
            id = "act-105",
            athlete = SampleAthletes.li,
            sport = Sport.Hike,
            title = "Fort trail at sunrise",
            description = "Foggy at the top, worth the early start.",
            startTime = now - Duration.ofDays(4) - Duration.ofHours(3),
            distanceMeters = 14_200.0,
            movingTimeSeconds = 7_500,
            elevationGainMeters = 640.0,
            route = SampleRoutes.pointToPoint(sinhagad, lengthKm = 6.0, bearingDeg = 20.0, seed = 41),
            achievementCount = 0,
            highFivers = listOf("Kavya Rao", "Dev Kulkarni", "Ana Ruiz", "Mira Holm", "Sam Okafor"),
            highFiveGiven = true,
            commentCount = 6,
        ),
        Activity(
            id = "act-106",
            athlete = SampleAthletes.me,
            sport = Sport.Ride,
            title = "Commute home",
            startTime = now - Duration.ofDays(9),
            distanceMeters = 12_400.0,
            movingTimeSeconds = 2_460,
            elevationGainMeters = 88.0,
            route = SampleRoutes.pointToPoint(pune, lengthKm = 9.0, bearingDeg = 110.0, seed = 51),
            highFivers = emptyList(),
            isOwn = true,
            visibility = Visibility.OnlyMe,
        ),
        Activity(
            id = "act-107",
            athlete = SampleAthletes.me,
            sport = Sport.Run,
            title = "Long run by the river",
            startTime = now - Duration.ofDays(12),
            distanceMeters = 18_600.0,
            movingTimeSeconds = 6_240,
            elevationGainMeters = 120.0,
            route = SampleRoutes.loop(pune, radiusKm = 2.6, seed = 61, lobes = 4),
            achievementCount = 3,
            topAchievement = "Longest run this month",
            highFivers = listOf("Ana Ruiz", "Dev Kulkarni", "Li Wei", "Sam Okafor"),
            commentCount = 2,
            isOwn = true,
        ),
    )
}
