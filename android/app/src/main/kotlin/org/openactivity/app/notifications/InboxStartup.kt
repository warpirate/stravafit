// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.notifications

import android.content.Context
import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts the on-device notification sources when the app process starts: the channels, the watcher that
 * turns new activities into inbox entries, and the daily weekly-summary check.
 */
@Singleton
class InboxStartup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifier: AndroidSystemNotifier,
    private val processor: ActivityEventProcessor,
    private val clock: Clock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var started = false

    fun start() {
        // Host-side tests (Robolectric) build the real Application; they must not start background work.
        if (started || Build.FINGERPRINT == ROBOLECTRIC) return
        started = true
        notifier.createChannels()
        processor.start(scope)
        WeeklySummaryWorker.schedule(context, clock)
    }

    private companion object {
        const val ROBOLECTRIC = "robolectric"
    }
}

/** Binds the notification sources. */
@Module
@InstallIn(SingletonComponent::class)
abstract class InboxModule {
    @Binds
    abstract fun systemNotifier(impl: AndroidSystemNotifier): SystemNotifier

    @Binds
    abstract fun inboxWatermark(impl: PreferencesInboxWatermark): InboxWatermark
}
