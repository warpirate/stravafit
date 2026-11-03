// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app

import android.app.Application
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import org.openactivity.app.data.backup.AutoBackupStartup
import org.openactivity.app.data.backup.LocalDataBackup
import org.openactivity.app.data.backup.RestoreApplier
import org.openactivity.app.feature.healthconnect.HealthConnectStartup
import org.openactivity.app.notifications.InboxStartup
import org.openactivity.feature.widgets.WidgetUpdater
import javax.inject.Inject

/**
 * Application entry point and Hilt root. Applies a confirmed backup restore before anything opens the
 * database, then starts the on-device notification sources, the widget updates, the Health Connect
 * sync and the automatic backup job.
 */
@HiltAndroidApp
class OpenActivityApplication : Application() {
    @Inject lateinit var inboxStartup: InboxStartup

    @Inject lateinit var widgetUpdater: WidgetUpdater
    @Inject lateinit var healthConnectStartup: HealthConnectStartup
    @Inject lateinit var autoBackupStartup: AutoBackupStartup

    /** Runs before content providers, so WorkManager's startup cannot reach the database mid-restore. */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        RestoreApplier(LocalDataBackup.appDataLocations(this)).applyPending()
    }

    override fun onCreate() {
        super.onCreate()
        inboxStartup.start()
        widgetUpdater.start()
        healthConnectStartup.start()
        autoBackupStartup.start()
    }
}
