// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.app.feature.healthconnect.HealthConnectActions
import org.openactivity.app.feature.healthconnect.HealthConnectContent
import org.openactivity.app.feature.healthconnect.HealthConnectUiState
import org.openactivity.app.feature.stravaimport.PendingExport
import org.openactivity.app.feature.stravaimport.StravaImportActions
import org.openactivity.app.feature.stravaimport.StravaImportContent
import org.openactivity.app.feature.stravaimport.StravaImportFailure
import org.openactivity.app.feature.stravaimport.StravaImportJob
import org.openactivity.app.feature.stravaimport.StravaImportPhase
import org.openactivity.app.feature.stravaimport.StravaImportState
import org.openactivity.app.feature.stravaimport.StravaImportUiState
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.healthconnect.HealthConnectAvailability
import org.openactivity.core.healthconnect.HealthConnectStatus
import org.openactivity.core.healthconnect.HealthPermissions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of Settings, "Import from Strava" (intro, a shared ZIP, running, finished) and Settings,
 * Health Connect (connected, permission revoked, not installed).
 *
 * Record goldens with `./gradlew :android:app:recordRoborazziFossDebug --tests '*ImportHealthScreenshotTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ImportHealthScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val export = StravaImportJob(sourceUri = "content://downloads/export_48213377.zip", sourceName = "export_48213377.zip")

    @Test fun stravaIntroLight() = snap("strava_import_intro_light", dark = false) { Strava(StravaImportUiState()) }

    @Test fun stravaIntroDark() = snap("strava_import_intro_dark", dark = true) { Strava(StravaImportUiState()) }

    @Test fun stravaReadyLight() = snap("strava_import_ready_light", dark = false) {
        Strava(StravaImportUiState(pending = PendingExport("content://x/export_48213377.zip", "export_48213377.zip")))
    }

    @Test fun stravaRunningLight() = snap("strava_import_running_light", dark = false) {
        Strava(StravaImportUiState(export.copy(state = StravaImportState.RUNNING, phase = StravaImportPhase.ACTIVITIES, done = 312, total = 1_204, photos = 41)))
    }

    @Test fun stravaRunningDark() = snap("strava_import_running_dark", dark = true) {
        Strava(StravaImportUiState(export.copy(state = StravaImportState.RUNNING, phase = StravaImportPhase.ACTIVITIES, done = 312, total = 1_204, photos = 41)))
    }

    @Test fun stravaDoneLight() = snap("strava_import_done_light", dark = false) {
        Strava(
            StravaImportUiState(
                export.copy(
                    state = StravaImportState.DONE, done = 1_204, total = 1_204, imported = 1_180, duplicates = 20, failed = 4, photos = 212,
                    failures = listOf(
                        StravaImportFailure("Broken upload", "The export has no file activities/9182736455.gpx"),
                        StravaImportFailure("Morning Swim", "Its file activities/7766554433.fit.gz could not be read (The FIT file is damaged)"),
                        StravaImportFailure("Activity 5512", "activities.csv has no date or duration for it"),
                        StravaImportFailure("Evening Ride", "It could not be saved: disk full"),
                    ),
                ),
            ),
        )
    }

    @Test fun stravaStoppedLight() = snap("strava_import_stopped_light", dark = false) {
        Strava(StravaImportUiState(export.copy(state = StravaImportState.CANCELLED, done = 312, total = 1_204)))
    }

    @Test fun healthConnectedLight() = snap("health_connect_light", dark = false) {
        Health(
            HealthConnectUiState(
                status = HealthConnectStatus(HealthConnectAvailability.AVAILABLE, HealthPermissions.forWriting + HealthPermissions.forReading(false)),
                writeEnabled = true,
                readEnabled = true,
            ),
        )
    }

    @Test fun healthConnectedDark() = snap("health_connect_dark", dark = true) {
        Health(
            HealthConnectUiState(
                status = HealthConnectStatus(HealthConnectAvailability.AVAILABLE, HealthPermissions.forWriting + HealthPermissions.forReading(false)),
                writeEnabled = true,
                readEnabled = true,
            ),
        )
    }

    @Test fun healthRevokedLight() = snap("health_connect_revoked_light", dark = false) {
        Health(
            HealthConnectUiState(
                status = HealthConnectStatus(HealthConnectAvailability.AVAILABLE, setOf(HealthPermissions.READ_EXERCISE)),
                writeEnabled = true,
                readEnabled = true,
            ),
        )
    }

    @Test fun healthNotInstalledLight() = snap("health_connect_not_installed_light", dark = false) {
        Health(HealthConnectUiState(status = HealthConnectStatus(HealthConnectAvailability.NOT_INSTALLED)))
    }

    @Composable
    private fun Strava(state: StravaImportUiState) = StravaImportContent(state, StravaImportActions())

    @Composable
    private fun Health(state: HealthConnectUiState) = HealthConnectContent(state, HealthConnectActions())

    private fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                RidgelineTheme(darkTheme = dark, reduceMotion = true, content = content)
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }
}
