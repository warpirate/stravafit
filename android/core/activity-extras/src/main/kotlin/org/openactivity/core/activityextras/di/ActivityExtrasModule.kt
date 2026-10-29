// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.activityextras.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.openactivity.core.activityextras.ActivityExtrasRepository
import org.openactivity.core.activityextras.FileActivityExtrasRepository

/** Binds the file-backed [ActivityExtrasRepository]. */
@Module
@InstallIn(SingletonComponent::class)
public abstract class ActivityExtrasModule {
    /** The single extras store of the app. */
    @Binds
    public abstract fun extras(impl: FileActivityExtrasRepository): ActivityExtrasRepository
}
