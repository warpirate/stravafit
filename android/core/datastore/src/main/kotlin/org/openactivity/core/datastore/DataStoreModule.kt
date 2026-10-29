// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides [SettingsDataSource] over its own `settings` DataStore file. The DataStore itself is not put in
 * the graph, so it cannot collide with other modules' DataStore bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    private const val FILE_NAME = "settings"

    @Provides
    @Singleton
    fun settingsDataSource(@ApplicationContext context: Context): SettingsDataSource =
        SettingsDataSource(
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile(FILE_NAME) }),
        )
}
