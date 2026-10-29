// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.openactivity.feature.record.data.DataStoreRecordPreferencesRepository
import org.openactivity.feature.record.data.RecordDataStore
import org.openactivity.feature.record.data.RecordPreferencesRepository
import org.openactivity.feature.record.location.DevicePreStartLocation
import org.openactivity.feature.record.location.PreStartLocation
import org.openactivity.feature.record.system.BatteryOptimization
import org.openactivity.feature.record.system.CueSpeakerAvailability
import org.openactivity.feature.record.system.SpeechAvailability
import org.openactivity.feature.record.system.SystemBatteryOptimization
import javax.inject.Singleton

/** Bindings of the Record feature. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RecordModule {
    @Binds
    abstract fun preferences(impl: DataStoreRecordPreferencesRepository): RecordPreferencesRepository

    @Binds
    abstract fun preStartLocation(impl: DevicePreStartLocation): PreStartLocation

    @Binds
    abstract fun batteryOptimization(impl: SystemBatteryOptimization): BatteryOptimization

    @Binds
    abstract fun speechAvailability(impl: CueSpeakerAvailability): SpeechAvailability

    companion object {
        private const val FILE_NAME = "record_preferences"

        @Provides
        @Singleton
        @RecordDataStore
        fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile(FILE_NAME) })
    }
}
