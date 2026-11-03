// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Checks the exported schemas against the code: every historic version can be created from its exported
 * JSON, each migration path reaches the current version with the schema Room expects, and data written at
 * version 1 survives (version 2 adds goals and the notifications inbox, version 3 privacy zones, version 4
 * personal segments and their efforts).
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OpenActivityDatabase::class.java,
    )

    @Test
    fun `every exported schema exists up to the current version`() {
        val dir = File("schemas/${OpenActivityDatabase::class.java.name}")
        val versions = dir.listFiles().orEmpty().mapNotNull { it.nameWithoutExtension.toIntOrNull() }.sorted()
        assertEquals((1..OpenActivityDatabase.VERSION).toList(), versions)
    }

    @Test
    fun `all migrations reach the current schema`() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO routes (uuid, name, description, sport, distance_meters, elevation_gain_meters, " +
                    "elevation_loss_meters, polyline, bbox_south, bbox_west, bbox_north, bbox_east, created_at_millis) " +
                    "VALUES ('r', 'Loop', '', NULL, 1000.0, 5.0, 5.0, '', 0, 0, 0, 0, 1)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, OpenActivityDatabase.VERSION, true, *DatabaseMigrations.ALL)
        db.query("SELECT name FROM routes").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Loop", c.getString(0))
        }
        db.close()
    }

    @Test
    fun `migration 1 to 2 keeps activities and adds goals and the inbox`() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO activities (id, uuid, sport, title, description, visibility, start_epoch_millis, " +
                    "time_zone_id, elapsed_millis, moving_millis, distance_meters, elevation_gain_meters, " +
                    "elevation_loss_meters, altitude_source, average_speed_mps, max_speed_mps, auto_pause_count, " +
                    "privacy_hide_start_meters, privacy_hide_end_meters, flags, created_at_millis, updated_at_millis) " +
                    "VALUES (7, 'u7', 'RUN', 'Morning Run', '', 'EVERYONE', 1000, 'UTC', 60000, 60000, 1000.0, 0.0, " +
                    "0.0, 'GPS', 3.0, 4.0, 0, 200.0, 200.0, '', 1000, 1000)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 2, true, DatabaseMigrations.MIGRATION_1_2)
        db.query("SELECT title FROM activities WHERE id = 7").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Morning Run", c.getString(0))
        }
        db.execSQL(
            "INSERT INTO goals (sport, period, metric, target, created_at_millis, updated_at_millis) " +
                "VALUES ('RUN', 'WEEK', 'DISTANCE', 30000.0, 1, 1)",
        )
        db.execSQL(
            "INSERT INTO notifications (type, title, body, target, activity_id, created_at_millis, is_read, dedupe_key) " +
                "VALUES ('PERSONAL_RECORD', 'New PR', '', 'ACTIVITY', 7, 2, 0, 'k')",
        )
        db.query("SELECT COUNT(*) FROM goals").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        // Deleting the activity takes its notifications with it.
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM activities WHERE id = 7")
        db.query("SELECT COUNT(*) FROM notifications").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        db.close()
    }

    @Test
    fun `migration 2 to 3 keeps activities and adds privacy zones`() {
        helper.createDatabase(dbName, 2).apply {
            execSQL(
                "INSERT INTO activities (id, uuid, sport, title, description, visibility, start_epoch_millis, " +
                    "time_zone_id, elapsed_millis, moving_millis, distance_meters, elevation_gain_meters, " +
                    "elevation_loss_meters, altitude_source, average_speed_mps, max_speed_mps, auto_pause_count, " +
                    "privacy_hide_start_meters, privacy_hide_end_meters, flags, created_at_millis, updated_at_millis) " +
                    "VALUES (3, 'u3', 'RIDE', 'Evening Ride', '', 'FOLLOWERS', 1000, 'UTC', 60000, 60000, 1000.0, 0.0, " +
                    "0.0, 'GPS', 3.0, 4.0, 0, 0.0, 0.0, '', 1000, 1000)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 3, true, DatabaseMigrations.MIGRATION_2_3)
        db.query("SELECT title FROM activities WHERE id = 3").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Evening Ride", c.getString(0))
        }
        db.execSQL(
            "INSERT INTO privacy_zones (uuid, label, center_latitude, center_longitude, radius_meters, " +
                "created_at_millis, updated_at_millis) VALUES ('z1', 'Home', 18.52, 73.85, 400, 1, 1)",
        )
        db.query("SELECT label, radius_meters FROM privacy_zones").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Home", c.getString(0))
            assertEquals(400, c.getInt(1))
        }
        db.close()
    }

    @Test
    fun `migration 3 to 4 keeps activities and adds segments and efforts`() {
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                "INSERT INTO activities (id, uuid, sport, title, description, visibility, start_epoch_millis, " +
                    "time_zone_id, elapsed_millis, moving_millis, distance_meters, elevation_gain_meters, " +
                    "elevation_loss_meters, altitude_source, average_speed_mps, max_speed_mps, auto_pause_count, " +
                    "privacy_hide_start_meters, privacy_hide_end_meters, flags, created_at_millis, updated_at_millis) " +
                    "VALUES (5, 'u5', 'RUN', 'Hill Repeats', '', 'EVERYONE', 1000, 'UTC', 60000, 60000, 1000.0, 0.0, " +
                    "0.0, 'GPS', 3.0, 4.0, 0, 0.0, 0.0, '', 1000, 1000)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 4, true, DatabaseMigrations.MIGRATION_3_4)
        db.query("SELECT title FROM activities WHERE id = 5").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Hill Repeats", c.getString(0))
        }
        db.execSQL(
            "INSERT INTO segments (id, uuid, name, sport, polyline, elevations, distance_meters, elevation_gain_meters, " +
                "average_grade_percent, start_latitude, start_longitude, end_latitude, end_longitude, bbox_south, bbox_west, " +
                "bbox_north, bbox_east, source_activity_id, starred, created_at_millis) VALUES (1, 's1', 'The Climb', 'RUN', " +
                "'_p~iF~ps|U_ulLnnqC', '', 400.0, 20.0, 5.0, 1, 2, 3, 4, 1, 2, 3, 4, 5, 1, 1)",
        )
        db.execSQL(
            "INSERT INTO segment_efforts (segment_id, activity_id, start_index, end_index, start_offset_millis, " +
                "start_epoch_millis, year, elapsed_millis, moving_millis, distance_meters, average_speed_mps, " +
                "is_personal_record, created_at_millis) VALUES (1, 5, 3, 40, 3000, 4000, 2026, 120000, 118000, 402.0, 3.3, 0, 1)",
        )
        // Deleting the activity takes its efforts with it; the segment stays.
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM activities WHERE id = 5")
        db.query("SELECT COUNT(*) FROM segment_efforts").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM segments").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.close()
    }

    @Test
    fun `room opens a migrated database`() = runTest {
        helper.createDatabase(dbName, 1).close()
        val room = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), OpenActivityDatabase::class.java, dbName)
            .addMigrations(*DatabaseMigrations.ALL)
            .allowMainThreadQueries()
            .build()
        assertEquals(0, room.activityDao().getPoints(1).size)
        assertEquals(0, room.notificationDao().observeUnreadCount().first())
        assertEquals(0, room.privacyZoneDao().count())
        assertEquals(0, room.segmentDao().count())
        room.close()
    }
}
