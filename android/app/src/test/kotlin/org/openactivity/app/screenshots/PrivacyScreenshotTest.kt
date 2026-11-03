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
import org.openactivity.app.feature.privacy.PrivacyContent
import org.openactivity.app.feature.privacy.PrivacyUiState
import org.openactivity.app.feature.privacy.PrivacyZoneEditorActions
import org.openactivity.app.feature.privacy.PrivacyZoneEditorContent
import org.openactivity.app.feature.privacy.PrivacyZoneEditorUiState
import org.openactivity.app.feature.privacy.PrivacyZonesContent
import org.openactivity.app.feature.privacy.PrivacyZonesUiState
import org.openactivity.app.feature.privacy.VisibilityContent
import org.openactivity.app.testing.FakePrivacyZoneRepository
import org.openactivity.core.datastore.AppSettings
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.openactivity.model.Visibility
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the privacy zones list and editor, the Privacy page and the audience settings, light and
 * dark. Maps render as the offline drawing (inspection mode), since Robolectric cannot load MapLibre.
 *
 * Record goldens with `./gradlew :android:app:recordRoborazziFossDebug --tests '*PrivacyScreenshotTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PrivacyScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val zones = PrivacyZonesUiState(
        isLoading = false,
        zones = listOf(
            FakePrivacyZoneRepository.zone(1, "Home", LatLng(18.5204, 73.8567), 400),
            FakePrivacyZoneRepository.zone(2, "Office", LatLng(18.5590, 73.7868), 800),
            FakePrivacyZoneRepository.zone(3, "", LatLng(18.4529, 73.8652), 200),
        ),
    )

    private val editor = PrivacyZoneEditorUiState(
        isLoading = false,
        isNew = false,
        name = "Home",
        center = LatLng(18.5204, 73.8567),
        radiusMeters = 400,
    )

    private val privacy = PrivacyUiState(
        defaultVisibility = Visibility.FOLLOWERS,
        sportOverrides = 1,
        zoneCount = 2,
        hideStartMeters = 200.0,
        hideEndMeters = 200.0,
        routeSuggestions = true,
        autoBackup = "Weekly to Backups",
    )

    @Test fun zonesLight() = snap("privacy_zones_light", dark = false) { PrivacyZonesContent(zones) }

    @Test fun zonesDark() = snap("privacy_zones_dark", dark = true) { PrivacyZonesContent(zones.copy(units = UnitSystem.IMPERIAL)) }

    @Test fun zonesEmpty() = snap("privacy_zones_empty_light", dark = false) { PrivacyZonesContent(PrivacyZonesUiState(isLoading = false)) }

    @Test fun editorLight() = snap("privacy_zone_editor_light", dark = false) { PrivacyZoneEditorContent(editor, PrivacyZoneEditorActions()) }

    @Test fun editorDark() = snap("privacy_zone_editor_dark", dark = true) { PrivacyZoneEditorContent(editor, PrivacyZoneEditorActions()) }

    @Test fun editorNewLight() = snap("privacy_zone_editor_new_light", dark = false) {
        PrivacyZoneEditorContent(PrivacyZoneEditorUiState(isLoading = false), PrivacyZoneEditorActions())
    }

    @Test fun privacyLight() = snap("privacy_page_light", dark = false) { PrivacyContent(privacy) }

    @Test fun privacyDark() = snap("privacy_page_dark", dark = true) { PrivacyContent(privacy.copy(routeSuggestions = false, autoBackup = null)) }

    @Test fun visibilityLight() = snap("visibility_light", dark = false) {
        VisibilityContent(AppSettings(visibilityOverrides = mapOf(SportType.RIDE to Visibility.ONLY_ME)))
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
