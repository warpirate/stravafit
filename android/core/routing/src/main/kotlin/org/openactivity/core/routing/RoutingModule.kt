// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.routing

import android.content.Context
import android.content.pm.PackageManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

/** One suggester per process, so the one-request-per-second limit holds across screens. */
@Module
@InstallIn(SingletonComponent::class)
object RoutingModule {
    private const val OSRM_MIN_INTERVAL_MILLIS = 1_000L
    private const val ELEVATION_MIN_INTERVAL_MILLIS = 500L

    @Provides
    @Singleton
    fun provideRouteSuggestions(@ApplicationContext context: Context): RouteSuggestions {
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } ?: "dev"
        val transport = UrlConnectionTransport(UrlConnectionTransport.userAgent(version))
        return RouteSuggester(
            osrm = OsrmClient(transport, RateLimiter(OSRM_MIN_INTERVAL_MILLIS)),
            elevation = ElevationClient(transport, RateLimiter(ELEVATION_MIN_INTERVAL_MILLIS)),
            cache = SuggestionCache(File(context.cacheDir, "route-suggestions")),
        )
    }
}
