// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.openactivity.app.notifications.InboxStartup
import org.openactivity.feature.widgets.WidgetUpdater
import javax.inject.Inject

/** Application entry point and Hilt root. Starts the on-device notification sources and the widget updates. */
@HiltAndroidApp
class OpenActivityApplication : Application() {
    @Inject lateinit var inboxStartup: InboxStartup

    @Inject lateinit var widgetUpdater: WidgetUpdater

    override fun onCreate() {
        super.onCreate()
        inboxStartup.start()
        widgetUpdater.start()
    }
}
