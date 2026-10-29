// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.recordingservice.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.openactivity.core.recordingservice.AndroidRecordingServiceLauncher
import org.openactivity.core.recordingservice.DefaultRecordingController
import org.openactivity.core.recordingservice.RecordingClock
import org.openactivity.core.recordingservice.RecordingController
import org.openactivity.core.recordingservice.RecordingServiceLauncher
import org.openactivity.core.recordingservice.SystemRecordingClock
import org.openactivity.core.recordingservice.audio.CueSpeaker
import org.openactivity.core.recordingservice.audio.TextToSpeechCueSpeaker
import javax.inject.Qualifier
import javax.inject.Singleton

/** The application-lifetime scope recordings run in (outlives screens; cancelled only with the process). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RecordingScope

/** Wires the recording controller and its platform pieces. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RecordingServiceModule {
    @Binds
    abstract fun controller(impl: DefaultRecordingController): RecordingController

    @Binds
    abstract fun clock(impl: SystemRecordingClock): RecordingClock

    @Binds
    abstract fun launcher(impl: AndroidRecordingServiceLauncher): RecordingServiceLauncher

    @Binds
    abstract fun speaker(impl: TextToSpeechCueSpeaker): CueSpeaker

    companion object {
        @Provides
        @Singleton
        @RecordingScope
        fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
