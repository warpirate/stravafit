// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.openactivity.core.data.repository.LocalActivityRepository
import org.openactivity.core.database.OpenActivityDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Base for tests over a real in-memory database and the real activity repository. */
abstract class RepositoryBackedTest {
    protected lateinit var context: Context
    protected lateinit var db: OpenActivityDatabase
    protected lateinit var activities: LocalActivityRepository
    protected val settings = FakeSettingsRepository()
    protected val extras = FakeExtrasRepository()
    protected val clock: Clock = Clock.fixed(Instant.ofEpochMilli(Recordings.START_MILLIS + 86_400_000L), ZoneOffset.UTC)

    @Before
    fun openDatabase() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, OpenActivityDatabase::class.java).allowMainThreadQueries().build()
        activities = LocalActivityRepository(db.activityDao(), clock)
    }

    @After
    fun closeDatabase() = db.close()
}
