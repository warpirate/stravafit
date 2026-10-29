// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.openactivity.core.database.entity.RouteEntity
import org.openactivity.core.database.entity.RoutePointEntity

/** Saved routes and their points. */
@Dao
abstract class RouteDao {

    /** Inserts a route and its points in one transaction and returns the new route id. */
    @Transaction
    open suspend fun insertRouteWithPoints(route: RouteEntity, points: List<RoutePointEntity>): Long {
        val id = insertRoute(route)
        insertPoints(points.map { it.copy(routeId = id) })
        return id
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertRoute(route: RouteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPoints(points: List<RoutePointEntity>)

    /** All routes, newest first. */
    @Query("SELECT * FROM routes ORDER BY created_at_millis DESC, id DESC")
    abstract fun observeAll(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :id")
    abstract fun observeById(id: Long): Flow<RouteEntity?>

    @Query("SELECT * FROM routes WHERE id = :id")
    abstract suspend fun getById(id: Long): RouteEntity?

    @Query("SELECT * FROM route_points WHERE route_id = :routeId ORDER BY seq")
    abstract suspend fun getPoints(routeId: Long): List<RoutePointEntity>

    @Query("SELECT * FROM route_points WHERE route_id = :routeId ORDER BY seq")
    abstract fun observePoints(routeId: Long): Flow<List<RoutePointEntity>>

    /** Deletes a route and (by cascade) its points. Returns the number of routes removed. */
    @Query("DELETE FROM routes WHERE id = :id")
    abstract suspend fun delete(id: Long): Int
}
