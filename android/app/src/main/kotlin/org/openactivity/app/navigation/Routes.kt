// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.navigation

import kotlinx.serialization.Serializable

/** Type-safe Navigation Compose destinations. */
@Serializable data object FeedRoute

@Serializable data object ExploreRoute

@Serializable data object GroupsRoute

@Serializable data object MeRoute

/** Full-screen recorder from `:android:feature:record`, outside the tab stacks; the bottom bar is hidden. */
typealias RecordRoute = org.openactivity.feature.record.navigation.RecordDestination

@Serializable data object SettingsRoute
