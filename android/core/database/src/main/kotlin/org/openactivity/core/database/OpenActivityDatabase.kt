// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.openactivity.core.database.dao.GoalDao
import org.openactivity.core.database.dao.NotificationDao
import org.openactivity.core.database.dao.PrivacyZoneDao
import org.openactivity.core.database.dao.ActivityDao
import org.openactivity.core.database.dao.RecordingJournalDao
import org.openactivity.core.database.dao.RouteDao
import org.openactivity.core.database.dao.SegmentDao
import org.openactivity.core.database.entity.ActivityEntity
import org.openactivity.core.database.entity.ActivityPointEntity
import org.openactivity.core.database.entity.BestEffortEntity
import org.openactivity.core.database.entity.GoalEntity
import org.openactivity.core.database.entity.JournalPointEntity
import org.openactivity.core.database.entity.LapEntity
import org.openactivity.core.database.entity.NotificationEntity
import org.openactivity.core.database.entity.PrivacyZoneEntity
import org.openactivity.core.database.entity.RecordingJournalEntity
import org.openactivity.core.database.entity.RouteEntity
import org.openactivity.core.database.entity.RoutePointEntity
import org.openactivity.core.database.entity.SegmentEffortEntity
import org.openactivity.core.database.entity.SegmentEntity
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
        GoalEntity::class,
        NotificationEntity::class,
        PrivacyZoneEntity::class,
        SegmentEntity::class,
        SegmentEffortEntity::class,
    ],
    version = OpenActivityDatabase.VERSION,
    exportSchema = true,
)
abstract class OpenActivityDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao

    abstract fun recordingJournalDao(): RecordingJournalDao

    abstract fun routeDao(): RouteDao

    abstract fun goalDao(): GoalDao

    abstract fun notificationDao(): NotificationDao

    abstract fun privacyZoneDao(): PrivacyZoneDao

    abstract fun segmentDao(): SegmentDao

    companion object {
        /** Current schema version. */
        const val VERSION: Int = 4

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
    /** Version 2 adds training goals and the on-device notifications inbox; existing rows are untouched. */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sport` TEXT, " +
                    "`period` TEXT NOT NULL, `metric` TEXT NOT NULL, `target` REAL NOT NULL, " +
                    "`created_at_millis` INTEGER NOT NULL, `updated_at_millis` INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `notifications` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`type` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `target` TEXT NOT NULL, " +
                    "`activity_id` INTEGER, `created_at_millis` INTEGER NOT NULL, `is_read` INTEGER NOT NULL, " +
                    "`dedupe_key` TEXT NOT NULL, FOREIGN KEY(`activity_id`) REFERENCES `activities`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_notifications_dedupe_key` ON `notifications` (`dedupe_key`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_activity_id` ON `notifications` (`activity_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_created_at_millis` ON `notifications` (`created_at_millis`)")
        }
    }

    /** Version 3 adds privacy zones (hidden places); existing rows are untouched. */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `privacy_zones` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`uuid` TEXT NOT NULL, `label` TEXT, `center_latitude` REAL NOT NULL, `center_longitude` REAL NOT NULL, " +
                    "`radius_meters` INTEGER NOT NULL, `created_at_millis` INTEGER NOT NULL, `updated_at_millis` INTEGER NOT NULL)",
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_privacy_zones_uuid` ON `privacy_zones` (`uuid`)")
        }
    }

    /** Version 4 adds personal segments and their efforts; existing rows are untouched. */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MIGRATION_3_4_SQL.forEach(db::execSQL)
        }
    }

    /** Every migration the database knows, oldest first. */
    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}

/** The statements of [DatabaseMigrations.MIGRATION_3_4], as Room generated them for schema 4. */
private val MIGRATION_3_4_SQL: List<String> = listOf(
    "CREATE TABLE IF NOT EXISTS `segments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `sport` TEXT NOT NULL, `polyline` TEXT NOT NULL, " +
        "`elevations` TEXT NOT NULL, `distance_meters` REAL NOT NULL, `elevation_gain_meters` REAL NOT NULL, " +
        "`average_grade_percent` REAL NOT NULL, `start_latitude` REAL NOT NULL, " +
        "`start_longitude` REAL NOT NULL, `end_latitude` REAL NOT NULL, `end_longitude` REAL NOT NULL, " +
        "`bbox_south` REAL NOT NULL, `bbox_west` REAL NOT NULL, `bbox_north` REAL NOT NULL, " +
        "`bbox_east` REAL NOT NULL, `source_activity_id` INTEGER, `starred` INTEGER NOT NULL, " +
        "`created_at_millis` INTEGER NOT NULL)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_segments_uuid` ON `segments` (`uuid`)",
    "CREATE INDEX IF NOT EXISTS `index_segments_starred` ON `segments` (`starred`)",
    "CREATE TABLE IF NOT EXISTS `segment_efforts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`segment_id` INTEGER NOT NULL, `activity_id` INTEGER NOT NULL, `start_index` INTEGER NOT NULL, " +
        "`end_index` INTEGER NOT NULL, `start_offset_millis` INTEGER NOT NULL, " +
        "`start_epoch_millis` INTEGER NOT NULL, `year` INTEGER NOT NULL, `elapsed_millis` INTEGER NOT NULL, " +
        "`moving_millis` INTEGER NOT NULL, `distance_meters` REAL NOT NULL, " +
        "`average_speed_mps` REAL NOT NULL, `average_heart_rate` REAL, `max_heart_rate` INTEGER, " +
        "`average_power` REAL, `average_cadence` REAL, `rank_all_time` INTEGER, `rank_year` INTEGER, " +
        "`is_personal_record` INTEGER NOT NULL, `created_at_millis` INTEGER NOT NULL, " +
        "FOREIGN KEY(`segment_id`) REFERENCES `segments`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
        "FOREIGN KEY(`activity_id`) REFERENCES `activities`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_segment_efforts_segment_id_activity_id_start_index` ON `segment_efforts` (`segment_id`, `activity_id`, `start_index`)",
    "CREATE INDEX IF NOT EXISTS `index_segment_efforts_activity_id` ON `segment_efforts` (`activity_id`)",
    "CREATE INDEX IF NOT EXISTS `index_segment_efforts_segment_id_elapsed_millis` ON `segment_efforts` (`segment_id`, `elapsed_millis`)",
)
