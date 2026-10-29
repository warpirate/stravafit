// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.location

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton

/** Play flavour: the fused provider when Play services work, otherwise the platform GPS provider. */
@Module
@InstallIn(SingletonComponent::class)
object LocationSourceModule {
    @Provides
    @Singleton
    fun locationSource(
        @ApplicationContext context: Context,
        fused: Provider<FusedLocationSource>,
        platform: Provider<PlatformLocationSource>,
    ): LocationSource {
        val playServices = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        return if (playServices == ConnectionResult.SUCCESS) fused.get() else platform.get()
    }
}
