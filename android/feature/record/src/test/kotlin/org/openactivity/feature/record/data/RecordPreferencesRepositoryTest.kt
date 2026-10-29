// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.openactivity.model.SportType

class RecordPreferencesRepositoryTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun storesEveryOptionAndKeepsFourRecentSports() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val store = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) {
            folder.newFile("record.preferences_pb")
        }
        val repo = DataStoreRecordPreferencesRepository(store)
        assertEquals(RecordPreferences(), repo.preferences.first())

        repo.setCountdown(true)
        repo.setKeepScreenOn(true)
        repo.setPaceMode(PaceMode.AVERAGE)
        listOf(SportType.RUN, SportType.RIDE, SportType.WALK, SportType.HIKE, SportType.SWIM, SportType.RUN)
            .forEach { repo.addRecentSport(it) }

        val p = repo.preferences.first()
        assertTrue(p.countdown)
        assertTrue(p.keepScreenOn)
        assertEquals(PaceMode.AVERAGE, p.paceMode)
        assertEquals(listOf(SportType.RUN, SportType.SWIM, SportType.HIKE, SportType.WALK), p.recentSports)
    }

    @Test fun paceModeCycles() {
        assertEquals(PaceMode.CURRENT, PaceMode.SPLIT.next())
        assertEquals(PaceMode.SPLIT, PaceMode.AVERAGE.next())
    }
}
