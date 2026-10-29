// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.openactivity.feature.activitydetail.export.RepositoryExportActivityProvider
import org.openactivity.feature.shareexport.ExportActivityProvider

/** Binds the database-backed [ExportActivityProvider] used by the share screen and file export. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ActivityDetailModule {
    @Binds
    abstract fun exportActivityProvider(impl: RepositoryExportActivityProvider): ExportActivityProvider
}
