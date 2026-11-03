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

/** A suggested loop near the athlete (route suggestions): map, profile, save/record/export. */
@Serializable data class SuggestedRouteRoute(val suggestionId: String)

/** Edit the local profile: name, location, photo. */
@Serializable data object EditProfileRoute

/** Open-source licences and map attribution. */
@Serializable data object LicensesRoute

/** The notifications inbox, from the bell on Home (notifications-search section 2). */
@Serializable data object NotificationsRoute

/**
 * Goals: the list with progress, plus the editor. [newGoal] opens the editor for a new goal straight away.
 */
@Serializable data class GoalsRoute(val newGoal: Boolean = false)

/** The weekly recap of the week starting [weekStart] (ISO date), from its notification or inbox entry. */
@Serializable data class RecapRoute(val weekStart: String)

/** "Share this month": the monthly card of [year]-[month] (1..12), from the You tab. */
@Serializable data class MonthCardRoute(val year: Int, val month: Int)

/** Settings, "Import from Strava": the whole Strava archive. [sharedUri] is a ZIP shared to the app. */
@Serializable data class StravaImportRoute(val sharedUri: String? = null)

/** Settings, Health Connect: write activities, read other apps' workouts. */
@Serializable data object HealthConnectRoute

/** The Privacy page: where data lives and who sees what (from Settings). */
@Serializable data object PrivacyRoute

/** The privacy zones list. */
@Serializable data object PrivacyZonesRoute

/** Add (`zoneId` 0) or edit a privacy zone. */
@Serializable data class PrivacyZoneEditorRoute(val zoneId: Long = 0)

/** Default audience for new activities, overall and per sport. */
@Serializable data object VisibilityRoute

/** "Create segment" from activity [activityId]: range picker, name and sport. */
@Serializable data class CreateSegmentRoute(val activityId: Long)

/** A personal segment: map, profile, your efforts; star, rename, re-match, delete. */
@Serializable data class SegmentDetailRoute(val segmentId: Long)

/** You, Segments: starred segments, all of them and your PRs. */
@Serializable data object SegmentsRoute
