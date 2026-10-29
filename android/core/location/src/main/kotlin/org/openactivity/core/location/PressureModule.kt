// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.location

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the barometer source (the same in both flavours). */
@Module
@InstallIn(SingletonComponent::class)
abstract class PressureModule {
    @Binds
    abstract fun pressureSource(impl: SensorPressureSource): PressureSource
}
