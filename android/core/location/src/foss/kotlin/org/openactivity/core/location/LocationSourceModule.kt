// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.location

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** F-Droid flavour: the platform GPS provider only, no Google services (ADR-011). */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocationSourceModule {
    @Binds
    abstract fun locationSource(impl: PlatformLocationSource): LocationSource
}
