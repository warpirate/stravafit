// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.openactivity.core.map.style.ConfigurableMapStyleProvider
import org.openactivity.core.map.style.MapStyleConfig
import org.openactivity.core.map.style.MapStyleProvider
import org.openactivity.core.map.thumbnail.CanvasThumbnailRenderer
import org.openactivity.core.map.thumbnail.ConnectivityNetworkMonitor
import org.openactivity.core.map.thumbnail.DiskThumbnailCache
import org.openactivity.core.map.thumbnail.RouteThumbnailLoader
import org.openactivity.core.map.thumbnail.SnapshotThumbnailRenderer
import java.io.File
import javax.inject.Singleton

/**
 * Map bindings. The style configuration defaults to keyless OpenFreeMap; once instances can
 * publish their own tile endpoints (ADR-012, Protomaps), only [provideStyleConfig] changes.
 */
@Module
@InstallIn(SingletonComponent::class)
object MapModule {
    @Provides
    @Singleton
    fun provideStyleConfig(): MapStyleConfig = MapStyleConfig.OpenFreeMap

    @Provides
    @Singleton
    fun provideStyleProvider(config: MapStyleConfig): MapStyleProvider = ConfigurableMapStyleProvider(config)

    @Provides
    @Singleton
    fun provideThumbnailLoader(
        @ApplicationContext context: Context,
        styles: MapStyleProvider,
    ): RouteThumbnailLoader = RouteThumbnailLoader(
        cache = DiskThumbnailCache(File(context.cacheDir, THUMBNAIL_DIR)),
        snapshot = SnapshotThumbnailRenderer(context, styles),
        fallback = CanvasThumbnailRenderer(),
        network = ConnectivityNetworkMonitor(context),
    )

    private const val THUMBNAIL_DIR = "route-thumbnails"
}

/** Access to map singletons from composables and other non-injected code. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface MapEntryPoint {
    fun routeThumbnailLoader(): RouteThumbnailLoader
    fun mapStyleProvider(): MapStyleProvider
}
