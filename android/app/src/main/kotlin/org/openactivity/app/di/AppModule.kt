// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.di

import android.content.Context
import android.text.format.DateFormat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.openactivity.app.data.CommunityRepository
import org.openactivity.app.data.DataStorePreferencesRepository
import org.openactivity.app.data.FeedRepository
import org.openactivity.app.data.GpsStatusSource
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.data.ProfileRepository
import org.openactivity.app.data.RouteRepository
import org.openactivity.app.data.SampleCommunityRepository
import org.openactivity.app.data.SampleFeedRepository
import org.openactivity.app.data.SampleProfileRepository
import org.openactivity.app.data.SampleRouteRepository
import org.openactivity.app.data.SimulatedGpsStatusSource
import org.openactivity.app.format.RelativeTimeFormatter
import java.time.Clock
import java.util.Locale
import javax.inject.Singleton

private val Context.userPreferences: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/** Binds data sources. Sample implementations are swapped for `:core:data` ones as they land. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds abstract fun feedRepository(impl: SampleFeedRepository): FeedRepository

    @Binds abstract fun profileRepository(impl: SampleProfileRepository): ProfileRepository

    @Binds abstract fun communityRepository(impl: SampleCommunityRepository): CommunityRepository

    @Binds abstract fun routeRepository(impl: SampleRouteRepository): RouteRepository

    @Binds abstract fun preferencesRepository(impl: DataStorePreferencesRepository): PreferencesRepository

    @Binds abstract fun gpsStatusSource(impl: SimulatedGpsStatusSource): GpsStatusSource
}

/** Platform services. */
@Module
@InstallIn(SingletonComponent::class)
object PlatformModule {
    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.userPreferences

    @Provides
    fun relativeTimeFormatter(@ApplicationContext context: Context, clock: Clock): RelativeTimeFormatter =
        RelativeTimeFormatter(clock, Locale.getDefault(), DateFormat.is24HourFormat(context))
}
