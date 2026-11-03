// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.testing

import org.openactivity.app.feature.healthconnect.HealthConnectJobs
import org.openactivity.core.healthconnect.HealthConnectAvailability
import org.openactivity.core.healthconnect.HealthConnectGateway
import org.openactivity.core.healthconnect.HealthPermissions
import org.openactivity.core.healthconnect.HealthWorkout
import org.openactivity.core.healthconnect.HealthWorkoutRead
import java.time.Instant

/**
 * Health Connect in memory: tests set what is installed and allowed, what other apps wrote, and read
 * back what the app wrote. Revoking is just changing [granted].
 */
class FakeHealthConnectGateway(
    var availability: HealthConnectAvailability = HealthConnectAvailability.AVAILABLE,
    var granted: Set<String> = emptySet(),
    var backgroundAvailable: Boolean = false,
) : HealthConnectGateway {
    /** Workouts in Health Connect, as other apps (or this one) wrote them. */
    val stored = ArrayList<HealthWorkoutRead>()

    /** What the app wrote, by client id: writing again replaces, like Health Connect's client record ids. */
    val written = LinkedHashMap<String, HealthWorkout>()
    val reads = ArrayList<Pair<Instant, Instant>>()

    /** What the next permission dialog grants (the test plays the user). */
    var grantOnRequest: Set<String> = emptySet()

    fun allowAll() {
        granted = HealthPermissions.forWriting + HealthPermissions.forReading(backgroundAvailable)
    }

    override fun availability(): HealthConnectAvailability = availability

    override fun backgroundReadAvailable(): Boolean = backgroundAvailable && availability == HealthConnectAvailability.AVAILABLE

    override suspend fun grantedPermissions(): Set<String> = if (availability == HealthConnectAvailability.AVAILABLE) granted else emptySet()

    override suspend fun writeWorkout(workout: HealthWorkout, granted: Set<String>) {
        if (HealthPermissions.WRITE_EXERCISE !in this.granted) throw SecurityException("not allowed")
        written[workout.clientId] = workout
    }

    override suspend fun readWorkouts(from: Instant, to: Instant, granted: Set<String>): List<HealthWorkoutRead> {
        if (HealthPermissions.READ_EXERCISE !in this.granted) throw SecurityException("not allowed")
        reads += from to to
        return stored.filter { !it.start.isBefore(from) && it.start.isBefore(to) }.sortedBy { it.start }
    }
}

/** Counts the daily-read scheduling instead of using WorkManager. */
class FakeHealthConnectJobs : HealthConnectJobs {
    var scheduled = false

    override fun scheduleDaily() {
        scheduled = true
    }

    override fun cancelDaily() {
        scheduled = false
    }
}
