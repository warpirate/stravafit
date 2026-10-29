// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.distribution

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Play Store flavour: may use Google Play services (fused location, FCM). */
@Module
@InstallIn(SingletonComponent::class)
object DistributionModule {
    @Provides
    @Singleton
    fun provideDistribution(): Distribution = Distribution(
        id = "gms",
        displayName = "Play build",
        locationBackend = "Fused location provider",
        pushBackend = "Firebase Cloud Messaging",
    )
}
