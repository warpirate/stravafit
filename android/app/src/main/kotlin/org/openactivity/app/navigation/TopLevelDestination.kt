// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.reflect.KClass

/**
 * Bottom navigation items (design-system 4.3: Feed · Explore · Record · Groups · Me).
 *
 * [Record] is an action, not a tab: it opens the full-screen recorder.
 */
enum class TopLevelDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: Any,
    val routeClass: KClass<*>,
) {
    Feed("Feed", Icons.Rounded.Home, Icons.Outlined.Home, FeedRoute, FeedRoute::class),
    Explore("Explore", Icons.Rounded.Explore, Icons.Outlined.Explore, ExploreRoute, ExploreRoute::class),
    Record("Record", Icons.Rounded.FiberManualRecord, Icons.Rounded.FiberManualRecord, RecordRoute, RecordRoute::class),
    Groups("Groups", Icons.Rounded.Groups, Icons.Outlined.Groups, GroupsRoute, GroupsRoute::class),
    Me("Me", Icons.Rounded.Person, Icons.Outlined.Person, MeRoute, MeRoute::class),
    ;

    /** True for the Record action, which is drawn emphasised and opens a modal flow. */
    val isAction: Boolean get() = this == Record
}
