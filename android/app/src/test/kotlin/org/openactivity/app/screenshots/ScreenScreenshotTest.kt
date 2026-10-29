// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.app.feature.record.GpsState
import org.openactivity.app.feature.record.RecordActions
import org.openactivity.app.feature.record.RecordContent
import org.openactivity.app.feature.record.RecordUiState
import org.openactivity.app.model.Sport
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the Record screen in light and dark (the tabs are in `HomeScreenshotTest`), rendered with Robolectric native graphics.
 *
 * Record goldens with `./gradlew :android:app:recordRoborazziGmsDebug` (written to
 * `android/app/screenshots/`); compare with `compareRoborazziGmsDebug`. A plain unit-test run
 * only checks that every screen composes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test fun recordAcquiringLight() = snap("record_acquiring_light", dark = false) {
        RecordContent(RecordUiState(sport = Sport.Run, gps = GpsState.Acquiring(38f), showSlowFixHint = true), RecordActions())
    }

    @Test fun recordReadyDark() = snap("record_ready_dark", dark = true) {
        RecordContent(RecordUiState(sport = Sport.Ride, gps = GpsState.Ready(8f), autoPause = true), RecordActions())
    }

    @Test fun recordIndoorLight() = snap("record_indoor_light", dark = false) {
        RecordContent(RecordUiState(sport = Sport.WeightTraining, gps = GpsState.Indoor), RecordActions())
    }

    private fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { RidgelineTheme(darkTheme = dark, reduceMotion = true, content = content) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }
}
