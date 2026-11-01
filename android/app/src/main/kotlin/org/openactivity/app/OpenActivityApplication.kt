// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.openactivity.app.notifications.InboxStartup
import javax.inject.Inject

/** Application entry point and Hilt root. Starts the on-device notification sources. */
@HiltAndroidApp
class OpenActivityApplication : Application() {
    @Inject lateinit var inboxStartup: InboxStartup

    override fun onCreate() {
        super.onCreate()
        inboxStartup.start()
    }
}
