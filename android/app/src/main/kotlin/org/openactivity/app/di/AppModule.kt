// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.di

import org.openactivity.app.data.LocalSuggestionSettings

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
import org.openactivity.app.data.FeedSeenRepository
import org.openactivity.app.data.LocalFeedSeenRepository
import org.openactivity.app.data.LocalOnboardingRepository
import org.openactivity.app.data.LocalProfileRepository
import org.openactivity.app.data.OnboardingRepository
import org.openactivity.app.data.PreferencesRepository
import org.openactivity.app.data.ProfileRepository
import org.openactivity.app.data.SettingsBackedPreferencesRepository
import org.openactivity.app.data.backup.AppRestarter
import org.openactivity.app.data.backup.ControllerUnsavedRecordingCheck
import org.openactivity.app.data.backup.DataBackup
import org.openactivity.app.data.backup.LocalDataBackup
import org.openactivity.app.data.backup.ProcessAppRestarter
import org.openactivity.app.data.backup.UnsavedRecordingCheck
import org.openactivity.app.data.export.ActivityArchiveExporter
import org.openactivity.app.data.export.GpxZipArchiveExporter
import org.openactivity.app.data.local.FileProfilePhotoStore
import org.openactivity.app.data.local.ProfilePhotoStore
import org.openactivity.app.data.route.FileRouteImporter
import org.openactivity.app.data.route.GpxRouteExporter
import org.openactivity.app.data.route.RouteExporter
import org.openactivity.app.data.route.RouteImporter
import org.openactivity.app.format.RelativeTimeFormatter
import java.time.Clock
import java.util.Locale
import javax.inject.Singleton

private val Context.userPreferences: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

/**
 * Binds the app-shell data sources. Activities, routes and settings come from `:core:data`; this
 * module adds the local profile, onboarding state and file import/export.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds abstract fun preferencesRepository(impl: SettingsBackedPreferencesRepository): PreferencesRepository

    @Binds abstract fun profileRepository(impl: LocalProfileRepository): ProfileRepository

    @Binds abstract fun onboardingRepository(impl: LocalOnboardingRepository): OnboardingRepository

    @Binds abstract fun profilePhotoStore(impl: FileProfilePhotoStore): ProfilePhotoStore

    @Binds abstract fun routeImporter(impl: FileRouteImporter): RouteImporter

    @Binds abstract fun routeExporter(impl: GpxRouteExporter): RouteExporter

    @Binds abstract fun archiveExporter(impl: GpxZipArchiveExporter): ActivityArchiveExporter

    @Binds abstract fun dataBackup(impl: LocalDataBackup): DataBackup

    @Binds abstract fun appRestarter(impl: ProcessAppRestarter): AppRestarter

    @Binds abstract fun unsavedRecordingCheck(impl: ControllerUnsavedRecordingCheck): UnsavedRecordingCheck

    @Binds abstract fun feedSeenRepository(impl: LocalFeedSeenRepository): FeedSeenRepository
    @Binds abstract fun suggestionSettings(impl: LocalSuggestionSettings): org.openactivity.app.data.SuggestionSettings

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
