// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.core.data.repository.DataStoreSettingsRepository
import org.openactivity.core.data.repository.LocalActivityRepository
import org.openactivity.core.data.repository.LocalRouteRepository
import org.openactivity.core.data.repository.RouteRepository
import org.openactivity.core.data.repository.SettingsRepository
import java.time.Clock
import javax.inject.Qualifier

/** The wall clock the data layer stamps rows with and buckets stats by (qualified to avoid clashes). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DataClock

/** Binds the repositories of `:core:data`. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun activityRepository(impl: LocalActivityRepository): ActivityRepository

    @Binds
    abstract fun routeRepository(impl: LocalRouteRepository): RouteRepository

    @Binds
    abstract fun settingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    companion object {
        @Provides
        @DataClock
        fun clock(): Clock = Clock.systemDefaultZone()
    }
}
