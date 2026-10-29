// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import org.openactivity.core.database.dao.ActivityDao
import org.openactivity.core.database.dao.RecordingJournalDao
import org.openactivity.core.database.dao.RouteDao
import org.openactivity.core.database.entity.ActivityEntity
import org.openactivity.core.database.entity.ActivityPointEntity
import org.openactivity.core.database.entity.BestEffortEntity
import org.openactivity.core.database.entity.JournalPointEntity
import org.openactivity.core.database.entity.LapEntity
import org.openactivity.core.database.entity.RecordingJournalEntity
import org.openactivity.core.database.entity.RouteEntity
import org.openactivity.core.database.entity.RoutePointEntity
import org.openactivity.core.database.entity.SplitEntity

/**
 * The app's local database. Schemas are exported to `schemas/` and every version bump must ship a
 * [Migration] in [DatabaseMigrations.ALL]; `MigrationTest` checks each one against the exported schemas.
 */
@Database(
    entities = [
        ActivityEntity::class,
        ActivityPointEntity::class,
        LapEntity::class,
        SplitEntity::class,
        BestEffortEntity::class,
        RecordingJournalEntity::class,
        JournalPointEntity::class,
        RouteEntity::class,
        RoutePointEntity::class,
    ],
    version = OpenActivityDatabase.VERSION,
    exportSchema = true,
)
abstract class OpenActivityDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao

    abstract fun recordingJournalDao(): RecordingJournalDao

    abstract fun routeDao(): RouteDao

    companion object {
        /** Current schema version. */
        const val VERSION: Int = 1

        /** File name of the on-device database. */
        const val NAME: String = "openactivity.db"

        /**
         * Builds the on-device database. Write-ahead logging keeps journal flushes cheap and lets the UI
         * read while the recording service writes. No destructive fallback: losing activities is never
         * an acceptable migration strategy.
         */
        fun build(context: Context): OpenActivityDatabase =
            Room.databaseBuilder(context.applicationContext, OpenActivityDatabase::class.java, NAME)
                .addMigrations(*DatabaseMigrations.ALL)
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}

/** All schema migrations, oldest first. */
object DatabaseMigrations {
    /** Every migration the database knows; empty while only version 1 exists. */
    val ALL: Array<Migration> = emptyArray()
}
