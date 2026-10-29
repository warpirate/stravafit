// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.navigation

import kotlinx.serialization.Serializable

/** Type-safe Navigation Compose destinations. */
@Serializable data object FeedRoute

@Serializable data object ExploreRoute

@Serializable data object GroupsRoute

@Serializable data object MeRoute

/**
 * Full-screen recorder, outside the tab stacks; the bottom bar is hidden.
 *
 * @property routeId saved route to follow ("Record this route"), or null. The recorder reads it from
 *   its `SavedStateHandle` under the key `"routeId"`.
 */
@Serializable data class RecordRoute(val routeId: Long? = null)

@Serializable data object SettingsRoute

/** Saved route detail (maps-routes): map, elevation profile, record/export/delete. */
@Serializable data class RouteDetailRoute(val routeId: Long)

/** Edit the local profile: name, location, photo. */
@Serializable data object EditProfileRoute

/** Open-source licences and map attribution. */
@Serializable data object LicensesRoute

/**
 * Activity detail contract shared with the detail feature: a string route `activity/{id}` where
 * `id` is the `:core:data` activity id.
 */
object ActivityDetailContract {
    /** Route pattern the detail feature registers. */
    const val ROUTE: String = "activity/{id}"

    /** Argument name of the activity id. */
    const val ARG_ID: String = "id"

    /** Concrete route for activity [id]. */
    fun route(id: Long): String = "activity/$id"
}
