// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.openactivity.core.data.model.ActivityTitles
import org.openactivity.core.data.model.ResourceActivityTitles
import org.openactivity.core.data.repository.ActivityRepository
import org.openactivity.core.data.repository.DataStoreSettingsRepository
import org.openactivity.core.data.repository.GoalRepository
import org.openactivity.core.data.repository.LocalGoalRepository
import org.openactivity.core.data.repository.LocalNotificationRepository
import org.openactivity.core.data.repository.NotificationRepository
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
    abstract fun activityTitles(impl: ResourceActivityTitles): ActivityTitles

    @Binds
    abstract fun settingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    abstract fun goalRepository(impl: LocalGoalRepository): GoalRepository

    @Binds
    abstract fun notificationRepository(impl: LocalNotificationRepository): NotificationRepository

    companion object {
        @Provides
        @DataClock
        fun clock(): Clock = Clock.systemDefaultZone()
    }
}
