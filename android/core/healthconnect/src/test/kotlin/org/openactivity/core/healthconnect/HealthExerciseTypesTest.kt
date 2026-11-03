// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.healthconnect

import androidx.health.connect.client.records.ExerciseSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import org.openactivity.model.SportType

class HealthExerciseTypesTest {

    @Test
    fun `our sports map onto health connect types`() {
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, HealthExerciseTypes.toHealth(SportType.RUN, true))
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, HealthExerciseTypes.toHealth(SportType.TRAIL_RUN, true))
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL, HealthExerciseTypes.toHealth(SportType.VIRTUAL_RUN, false))
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_BIKING, HealthExerciseTypes.toHealth(SportType.GRAVEL_RIDE, true))
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY, HealthExerciseTypes.toHealth(SportType.VIRTUAL_RIDE, false))
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER, HealthExerciseTypes.toHealth(SportType.SWIM, true))
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, HealthExerciseTypes.toHealth(SportType.SWIM, false))
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, HealthExerciseTypes.toHealth(SportType.WEIGHT_TRAINING, false))
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_PADDLING, HealthExerciseTypes.toHealth(SportType.STAND_UP_PADDLING, true))
        assertEquals(ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT, HealthExerciseTypes.toHealth(SportType.PICKLEBALL, false))
    }

    @Test
    fun `health connect types map back onto our sports`() {
        assertEquals(SportType.RUN, HealthExerciseTypes.fromHealth(ExerciseSessionRecord.EXERCISE_TYPE_RUNNING))
        assertEquals(SportType.RIDE, HealthExerciseTypes.fromHealth(ExerciseSessionRecord.EXERCISE_TYPE_BIKING))
        assertEquals(SportType.SWIM, HealthExerciseTypes.fromHealth(ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL))
        assertEquals(SportType.WEIGHT_TRAINING, HealthExerciseTypes.fromHealth(ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING))
        assertEquals(SportType.WORKOUT, HealthExerciseTypes.fromHealth(ExerciseSessionRecord.EXERCISE_TYPE_BOXING))
        assertEquals(SportType.WORKOUT, HealthExerciseTypes.fromHealth(9_999))
    }

    @Test
    fun `every sport survives the round trip as itself or a sport of the same family`() {
        for (sport in SportType.entries) {
            val type = HealthExerciseTypes.toHealth(sport, sport.usesGps)
            val back = HealthExerciseTypes.fromHealth(type)
            if (type == ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT) {
                assertEquals(sport.name, SportType.WORKOUT, back)
            } else {
                assertEquals(sport.name, sport.category, back.category)
                // Writing the sport read back gives the same Health Connect type: nothing drifts on a re-sync.
                assertEquals(sport.name, type, HealthExerciseTypes.toHealth(back, sport.usesGps))
            }
        }
    }
}
