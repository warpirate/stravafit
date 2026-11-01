// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.navigation

import android.net.Uri
import androidx.navigation.NavDeepLink
import androidx.navigation.navDeepLink
import org.openactivity.app.BuildConfig
import org.openactivity.feature.activitydetail.ActivityDetailRoute

/**
 * The links that open a screen directly, registered on the destinations in [AppNavHost] and in
 * the manifest:
 *
 * - `openactivity://activity/{id}`, `https://{host}/activities/{id}`: a saved activity
 * - `openactivity://route/{id}`, `https://{host}/routes/{id}`: a saved route
 * - `openactivity://record`, `https://{host}/record`: the recorder; `?sport=RUN` preselects a sport
 *   (the launcher shortcuts use this) and `?routeId=7` a saved route
 *
 * The https host is a placeholder until the project has a domain: set the Gradle property
 * `openactivity.linkHost` and both the manifest and these patterns follow ([BuildConfig.LINK_HOST]).
 * Verified App Links also need `/.well-known/assetlinks.json` on that host.
 */
object AppLinks {
    const val SCHEME = "openactivity"

    /** The https host of web links, from the build. */
    val host: String get() = BuildConfig.LINK_HOST

    fun activity(host: String = this.host): List<NavDeepLink> = listOf(
        navDeepLink<ActivityDetailRoute>(basePath = "$SCHEME://activity"),
        navDeepLink<ActivityDetailRoute>(basePath = "https://$host/activities"),
    )

    fun route(host: String = this.host): List<NavDeepLink> = listOf(
        navDeepLink<RouteDetailRoute>(basePath = "$SCHEME://route"),
        navDeepLink<RouteDetailRoute>(basePath = "https://$host/routes"),
    )

    fun record(host: String = this.host): List<NavDeepLink> = listOf(
        navDeepLink<RecordRoute>(basePath = "$SCHEME://record"),
        navDeepLink<RecordRoute>(basePath = "https://$host/record"),
    )

    /** True for a link this app opens itself: our scheme, or https on our host. */
    fun isAppLink(uri: Uri, host: String = this.host): Boolean =
        uri.scheme == SCHEME || (uri.scheme == "https" && uri.host.equals(host, ignoreCase = true))
}
