// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.openactivity.app.data.backup.LocalDataBackup
import org.openactivity.app.data.backup.RestoreApplier
import org.openactivity.app.notifications.InboxStartup
import javax.inject.Inject

/**
 * Application entry point and Hilt root. Applies a confirmed backup restore before anything opens the
 * database, then starts the on-device notification sources.
 */
@HiltAndroidApp
class OpenActivityApplication : Application() {
    @Inject lateinit var inboxStartup: InboxStartup

    override fun onCreate() {
        RestoreApplier(LocalDataBackup.appDataLocations(this)).applyPending()
        super.onCreate()
        inboxStartup.start()
    }
}
