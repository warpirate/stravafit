// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.record

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.map.MapLocation
import org.openactivity.feature.record.data.PaceMode
import org.openactivity.feature.record.ui.RecordActions
import org.openactivity.feature.record.ui.RecordContent
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.openactivity.recording.RecordingStatus
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the Record screen in light and dark. The map renders as plain land in inspection mode
 * (the native renderer is not available on the JVM).
 *
 * Record goldens with `./gradlew :android:feature:record:recordRoborazziDebug` (written to
 * `android/feature/record/screenshots/`); a plain unit-test run only checks that every state composes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class RecordScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val here = MapLocation(LatLng(51.5007, -0.1246), accuracyMeters = 8f)

    private val preStart = RecordUiState(
        sport = SportType.RUN,
        recentSports = listOf(SportType.RUN, SportType.RIDE, SportType.WALK),
        gps = GpsUi(GpsIndicator.Ready, 8f),
        location = here,
        audioCues = true,
    )

    private fun recording(status: RecordingStatus = RecordingStatus.RECORDING, sport: SportType = SportType.RUN): RecordUiState {
        val snapshot = recordingSnapshot(sport = sport, status = status)
        return preStart.copy(
            phase = RecordPhase.Recording,
            sport = sport,
            gps = GpsUi(GpsIndicator.Ready, 6f),
            recording = RecordingMapping.toUi(snapshot, PaceMode.SPLIT),
        )
    }

    @Test fun preStartLight() = snap("record_prestart_light", dark = false) { RecordContent(preStart, RecordActions()) }

    @Test fun preStartDark() = snap("record_prestart_dark", dark = true) { RecordContent(preStart, RecordActions()) }

    @Test fun preStartAcquiringWithRoute() = snap("record_prestart_acquiring_route_light", dark = false) {
        val route = testRoute().toChoice()
        RecordContent(
            preStart.copy(gps = GpsUi(GpsIndicator.Acquiring), showSlowFixHint = true, route = route, countdown = true),
            RecordActions(),
        )
    }

    @Test fun preStartNoPermission() = snap("record_prestart_permission_dark", dark = true) {
        RecordContent(preStart.copy(gps = GpsUi(GpsIndicator.NoPermission), location = null), RecordActions())
    }

    @Test fun preStartIndoor() = snap("record_prestart_indoor_light", dark = false) {
        RecordContent(preStart.copy(sport = SportType.YOGA, gps = GpsUi(GpsIndicator.Indoor)), RecordActions())
    }

    @Test fun countdown() = snap("record_countdown_light", dark = false) {
        RecordContent(preStart.copy(phase = RecordPhase.Countdown(3)), RecordActions())
    }

    @Test fun recordingMapLight() = snap("record_recording_map_light", dark = false) { RecordContent(recording(), RecordActions()) }

    @Test fun recordingMapDark() = snap("record_recording_map_dark", dark = true) { RecordContent(recording(), RecordActions()) }

    @Test fun recordingStatsLight() = snap("record_recording_stats_light", dark = false) {
        RecordContent(recording(), RecordActions(), initialPage = 1)
    }

    @Test fun recordingStatsRideDark() = snap("record_recording_stats_ride_dark", dark = true) {
        RecordContent(recording(sport = SportType.RIDE), RecordActions(), initialPage = 1)
    }

    @Test fun pausedLight() = snap("record_paused_light", dark = false) {
        RecordContent(recording(RecordingStatus.PAUSED), RecordActions())
    }

    @Test fun pausedDark() = snap("record_paused_dark", dark = true) {
        RecordContent(recording(RecordingStatus.PAUSED), RecordActions())
    }

    @Test fun autoPausedLight() = snap("record_auto_paused_light", dark = false) {
        RecordContent(recording(RecordingStatus.AUTO_PAUSED), RecordActions())
    }

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
