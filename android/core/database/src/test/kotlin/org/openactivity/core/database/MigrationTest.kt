// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
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
 * version 1 survives.
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
    fun `room opens a migrated database`() = runTest {
        helper.createDatabase(dbName, 1).close()
        val room = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), OpenActivityDatabase::class.java, dbName)
            .addMigrations(*DatabaseMigrations.ALL)
            .allowMainThreadQueries()
            .build()
        assertEquals(0, room.activityDao().getPoints(1).size)
        room.close()
    }
}
