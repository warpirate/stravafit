// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.distribution

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** F-Droid flavour: no Google dependencies at all. */
@Module
@InstallIn(SingletonComponent::class)
object DistributionModule {
    @Provides
    @Singleton
    fun provideDistribution(): Distribution = Distribution(
        id = "foss",
        displayName = "F-Droid build (no Google services)",
        locationBackend = "Android LocationManager",
        pushBackend = "UnifiedPush",
    )
}
