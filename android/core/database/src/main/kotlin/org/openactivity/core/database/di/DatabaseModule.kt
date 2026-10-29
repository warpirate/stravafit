// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.database.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.openactivity.core.database.OpenActivityDatabase
import org.openactivity.core.database.dao.ActivityDao
import org.openactivity.core.database.dao.RecordingJournalDao
import org.openactivity.core.database.dao.RouteDao
import javax.inject.Singleton

/** Provides the Room database and its DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): OpenActivityDatabase = OpenActivityDatabase.build(context)

    @Provides
    fun activityDao(db: OpenActivityDatabase): ActivityDao = db.activityDao()

    @Provides
    fun recordingJournalDao(db: OpenActivityDatabase): RecordingJournalDao = db.recordingJournalDao()

    @Provides
    fun routeDao(db: OpenActivityDatabase): RouteDao = db.routeDao()
}
