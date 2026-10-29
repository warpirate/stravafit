// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.navigation

import kotlinx.serialization.Serializable

/** Type-safe Navigation Compose destinations. */
@Serializable data object FeedRoute

@Serializable data object ExploreRoute

@Serializable data object GroupsRoute

@Serializable data object MeRoute

/**
 * Full-screen recorder from `:android:feature:record`, outside the tab stacks; the bottom bar is hidden.
 * `RecordRoute(routeId)` preselects a saved route ("Record this route").
 */
typealias RecordRoute = org.openactivity.feature.record.navigation.RecordDestination

@Serializable data object SettingsRoute

/** Saved route detail (maps-routes): map, elevation profile, record/export/delete. */
@Serializable data class RouteDetailRoute(val routeId: Long)

/** Edit the local profile: name, location, photo. */
@Serializable data object EditProfileRoute

/** Open-source licences and map attribution. */
@Serializable data object LicensesRoute
