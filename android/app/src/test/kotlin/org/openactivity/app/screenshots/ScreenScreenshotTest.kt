// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.app.data.SampleCommunityRepository
import org.openactivity.app.data.SampleFeedRepository
import org.openactivity.app.data.SampleProfileRepository
import org.openactivity.app.data.SampleRouteRepository
import org.openactivity.app.distribution.Distribution
import org.openactivity.app.feature.explore.ExploreContent
import org.openactivity.app.feature.explore.ExploreViewModel
import org.openactivity.app.feature.feed.ActivityCardActions
import org.openactivity.app.feature.feed.FeedContent
import org.openactivity.app.feature.feed.FeedScope
import org.openactivity.app.feature.feed.FeedUiState
import org.openactivity.app.feature.feed.FeedViewModel
import org.openactivity.app.feature.groups.GroupsContent
import org.openactivity.app.feature.groups.GroupsViewModel
import org.openactivity.app.feature.me.MeActions
import org.openactivity.app.feature.me.MeContent
import org.openactivity.app.feature.me.MeTab
import org.openactivity.app.feature.me.MeViewModel
import org.openactivity.app.feature.record.GpsState
import org.openactivity.app.feature.record.RecordActions
import org.openactivity.app.feature.record.RecordContent
import org.openactivity.app.feature.record.RecordUiState
import org.openactivity.app.feature.settings.SettingsContent
import org.openactivity.app.feature.settings.SettingsUiState
import org.openactivity.app.model.Sport
import org.openactivity.app.model.UserPreferences
import org.openactivity.app.navigation.AppBottomBar
import org.openactivity.app.navigation.TopLevelDestination
import org.openactivity.app.testing.FakePreferencesRepository
import org.openactivity.app.testing.TEST_CLOCK
import org.openactivity.app.testing.testTimeFormatter
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots of the main screens in light and dark, rendered with Robolectric native graphics.
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

    @Test fun feedLight() = snap("feed_light", dark = false) { WithBottomBar(TopLevelDestination.Feed) { Feed() } }

    @Test fun feedDark() = snap("feed_dark", dark = true) { WithBottomBar(TopLevelDestination.Feed) { Feed() } }

    @Test fun feedEmpty() = snap("feed_empty_light", dark = false) {
        WithBottomBar(TopLevelDestination.Feed) {
            FeedContent(FeedUiState(isLoading = false), {}, ActivityCardActions(), {}, {}, {}, {})
        }
    }

    @Test fun feedLoading() = snap("feed_loading_dark", dark = true) {
        WithBottomBar(TopLevelDestination.Feed) {
            FeedContent(FeedUiState(scope = FeedScope.Following, isLoading = true), {}, ActivityCardActions(), {}, {}, {}, {})
        }
    }

    @Test fun recordAcquiringLight() = snap("record_acquiring_light", dark = false) {
        RecordContent(RecordUiState(sport = Sport.Run, gps = GpsState.Acquiring(38f), showSlowFixHint = true), RecordActions())
    }

    @Test fun recordReadyDark() = snap("record_ready_dark", dark = true) {
        RecordContent(RecordUiState(sport = Sport.Ride, gps = GpsState.Ready(8f), autoPause = true), RecordActions())
    }

    @Test fun recordIndoorLight() = snap("record_indoor_light", dark = false) {
        RecordContent(RecordUiState(sport = Sport.WeightTraining, gps = GpsState.Indoor), RecordActions())
    }

    @Test fun meProgressLight() = snap("me_progress_light", dark = false) { WithBottomBar(TopLevelDestination.Me) { Me(MeTab.Progress) } }

    @Test fun meProgressDark() = snap("me_progress_dark", dark = true) { WithBottomBar(TopLevelDestination.Me) { Me(MeTab.Progress) } }

    @Test fun meActivitiesLight() = snap("me_activities_light", dark = false) { WithBottomBar(TopLevelDestination.Me) { Me(MeTab.Activities) } }

    @Test fun exploreLight() = snap("explore_light", dark = false) { WithBottomBar(TopLevelDestination.Explore) { Explore() } }

    @Test fun exploreDark() = snap("explore_dark", dark = true) { WithBottomBar(TopLevelDestination.Explore) { Explore() } }

    @Test fun groupsLight() = snap("groups_light", dark = false) { WithBottomBar(TopLevelDestination.Groups) { Groups() } }

    @Test fun settingsLight() = snap("settings_light", dark = false) { Settings() }

    @Test fun settingsDark() = snap("settings_dark", dark = true) { Settings() }

    private fun snap(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent { RidgelineTheme(darkTheme = dark, reduceMotion = true, content = content) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }
}

@Composable
private fun WithBottomBar(selected: TopLevelDestination, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = { AppBottomBar(selected = selected, onSelect = {}) },
    ) { padding ->
        Box(Modifier.padding(padding)) { content() }
    }
}

@Composable
private fun Feed() {
    val vm = remember { FeedViewModel(SampleFeedRepository(TEST_CLOCK), FakePreferencesRepository(), testTimeFormatter()) }
    val state by vm.uiState.collectAsState()
    FeedContent(state, vm::selectScope, ActivityCardActions(), {}, {}, {}, {})
}

@Composable
private fun Me(tab: MeTab) {
    val vm = remember { MeViewModel(SampleProfileRepository(), SampleFeedRepository(TEST_CLOCK), FakePreferencesRepository(), testTimeFormatter()) }
    remember(tab) { vm.selectTab(tab) }
    val state by vm.uiState.collectAsState()
    MeContent(state, MeActions())
}

@Composable
private fun Explore() {
    val vm = remember { ExploreViewModel(SampleRouteRepository(), FakePreferencesRepository()) }
    val state by vm.uiState.collectAsState()
    ExploreContent(state, vm::selectFilter, {}, {})
}

@Composable
private fun Groups() {
    val vm = remember { GroupsViewModel(SampleCommunityRepository()) }
    val state by vm.uiState.collectAsState()
    GroupsContent(state, vm::selectTab, {})
}

@Composable
private fun Settings() {
    SettingsContent(
        state = SettingsUiState(
            preferences = UserPreferences(),
            distribution = Distribution("gms", "Play build", "Fused location provider", "Firebase Cloud Messaging"),
            versionName = "0.1.0",
        ),
        onNavigateUp = {},
        onUnitSystem = {},
        onThemeMode = {},
        onReduceMotion = {},
    )
}
