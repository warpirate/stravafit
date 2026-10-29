// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.SavedStateHandle
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.app.data.route.RouteExporter
import org.openactivity.app.data.route.RouteImportSummary
import org.openactivity.app.data.route.RouteImporter
import org.openactivity.app.distribution.Distribution
import org.openactivity.app.feature.explore.ExploreActions
import org.openactivity.app.feature.explore.ExploreContent
import org.openactivity.app.feature.explore.ExploreViewModel
import org.openactivity.app.feature.feed.FeedActions
import org.openactivity.app.feature.feed.FeedContent
import org.openactivity.app.feature.feed.FeedScope
import org.openactivity.app.feature.feed.FeedUiState
import org.openactivity.app.feature.feed.FeedViewModel
import org.openactivity.app.feature.groups.GroupsScreen
import org.openactivity.app.feature.me.MeActions
import org.openactivity.app.feature.me.MeContent
import org.openactivity.app.feature.me.MeTab
import org.openactivity.app.feature.me.MeViewModel
import org.openactivity.app.feature.onboarding.OnboardingActions
import org.openactivity.app.feature.onboarding.OnboardingContent
import org.openactivity.app.feature.onboarding.OnboardingStep
import org.openactivity.app.feature.onboarding.OnboardingUiState
import org.openactivity.app.feature.profile.EditProfileContent
import org.openactivity.app.feature.profile.EditProfileUiState
import org.openactivity.app.feature.route.RouteDetailActions
import org.openactivity.app.feature.route.RouteDetailContent
import org.openactivity.app.feature.route.RouteDetailViewModel
import org.openactivity.app.feature.settings.SettingsActions
import org.openactivity.app.feature.settings.SettingsContent
import org.openactivity.app.feature.settings.SettingsUiState
import org.openactivity.app.model.UserProfile
import org.openactivity.app.navigation.AppBottomBar
import org.openactivity.app.navigation.TopLevelDestination
import org.openactivity.app.testing.FakeActivityRepository
import org.openactivity.app.testing.FakePreferencesRepository
import org.openactivity.app.testing.FakeProfileRepository
import org.openactivity.app.testing.FakeRouteRepository
import org.openactivity.app.testing.TEST_CLOCK
import org.openactivity.app.testing.routeDetail
import org.openactivity.app.testing.savedActivity
import org.openactivity.app.testing.testTimeFormatter
import org.openactivity.core.data.model.PersonalBest
import org.openactivity.core.data.model.StatTotals
import org.openactivity.core.data.model.YearTotals
import org.openactivity.core.datastore.AppSettings
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.openactivity.model.Visibility
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.seconds

/**
 * Screenshots of Feed, Me, Explore, route detail, Groups, Settings, Edit profile and onboarding,
 * driven by the real view models over in-memory repositories. Maps render as the offline drawing
 * (inspection mode), since Robolectric cannot load the native MapLibre renderer.
 *
 * Record goldens with `./gradlew :android:app:recordRoborazziGmsDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class HomeScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test fun feedLight() = snap("feed_light", dark = false) { Tab(TopLevelDestination.Feed) { Feed() } }

    @Test fun feedDark() = snap("feed_dark", dark = true) { Tab(TopLevelDestination.Feed) { Feed() } }

    @Test fun feedEmpty() = snap("feed_empty_light", dark = false) {
        Tab(TopLevelDestination.Feed) { FeedContent(FeedUiState(isLoading = false), FeedActions()) }
    }

    @Test fun feedFollowing() = snap("feed_following_light", dark = false) {
        Tab(TopLevelDestination.Feed) { FeedContent(FeedUiState(scope = FeedScope.Following, isLoading = false, hasAnyActivity = true), FeedActions()) }
    }

    @Test fun feedLoading() = snap("feed_loading_dark", dark = true) {
        Tab(TopLevelDestination.Feed) { FeedContent(FeedUiState(isLoading = true), FeedActions()) }
    }

    @Test fun meProgressLight() = snap("me_progress_light", dark = false) { Tab(TopLevelDestination.Me) { Me(MeTab.Progress) } }

    @Test fun meProgressDark() = snap("me_progress_dark", dark = true) { Tab(TopLevelDestination.Me) { Me(MeTab.Progress) } }

    @Test fun meActivitiesLight() = snap("me_activities_light", dark = false) { Tab(TopLevelDestination.Me) { Me(MeTab.Activities) } }

    @Test fun meEmpty() = snap("me_empty_light", dark = false) { Tab(TopLevelDestination.Me) { Me(MeTab.Progress, empty = true) } }

    @Test fun exploreLight() = snap("explore_light", dark = false) { Tab(TopLevelDestination.Explore) { Explore() } }

    @Test fun exploreDark() = snap("explore_dark", dark = true) { Tab(TopLevelDestination.Explore) { Explore() } }

    @Test fun exploreEmpty() = snap("explore_empty_light", dark = false) { Tab(TopLevelDestination.Explore) { Explore(empty = true) } }

    @Test fun routeDetailLight() = snap("route_detail_light", dark = false) { RouteDetail() }

    @Test fun routeDetailDark() = snap("route_detail_dark", dark = true) { RouteDetail() }

    @Test fun groupsLight() = snap("groups_light", dark = false) { Tab(TopLevelDestination.Groups) { GroupsScreen(onShowYourActivities = {}) } }

    @Test fun settingsLight() = snap("settings_light", dark = false) { Settings() }

    @Test fun settingsDark() = snap("settings_dark", dark = true) { Settings() }

    @Test fun editProfileLight() = snap("edit_profile_light", dark = false) {
        EditProfileContent(EditProfileUiState(isLoading = false, name = "Asha Rao", location = "Pune"), {}, {}, {}, {}, {}, {})
    }

    @Test fun onboardingNameLight() = snap("onboarding_name_light", dark = false) {
        OnboardingContent(OnboardingUiState(name = "Asha"), OnboardingActions())
    }

    @Test fun onboardingPrivacyDark() = snap("onboarding_privacy_dark", dark = true) {
        OnboardingContent(OnboardingUiState(step = OnboardingStep.Privacy), OnboardingActions())
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

private val profile = FakeProfileRepository(UserProfile(name = "Asha Rao", location = "Pune"))

private val RIDE_TRACK = listOf(
    LatLng(18.5300, 73.8400), LatLng(18.5420, 73.8480), LatLng(18.5510, 73.8650), LatLng(18.5480, 73.8820),
    LatLng(18.5350, 73.8900), LatLng(18.5230, 73.8780),
)

private fun sampleActivities(): FakeActivityRepository {
    val now = TEST_CLOCK.instant()
    val repo = FakeActivityRepository(
        listOf(
            savedActivity(1, "Riverside tempo", start = now.minusSeconds(2_400), description = "Felt strong on the last 3 km."),
            savedActivity(2, "Evening Ride", sport = SportType.RIDE, start = now.minusSeconds(90_000), distanceMeters = 32_400.0, movingSeconds = 4_380, elevationGain = 210.0, visibility = Visibility.FOLLOWERS, track = RIDE_TRACK),
            savedActivity(3, "Strength", sport = SportType.WEIGHT_TRAINING, start = now.minusSeconds(260_000), distanceMeters = 0.0, movingSeconds = 2_700, track = null, visibility = Visibility.ONLY_ME),
        ),
    )
    fun t(count: Int, km: Double, s: Long, gain: Double) = StatTotals(count, km * 1000, s.seconds, s.seconds, gain)
    val weeks = listOf(18.2, 22.5, 15.0, 26.1, 0.0, 12.0, 24.9, 30.2, 27.5, 21.0, 33.8, 24.1)
    repo.weekly.value = repo.weekly.value.mapIndexed { i, w -> w.copy(totals = t(3, weeks[i], (weeks[i] * 330).toLong(), weeks[i] * 4)) }
    repo.year.value = YearTotals(2026, t(142, 1_204.0, 388_800, 9_800.0), mapOf(SportType.RUN to t(120, 1_004.0, 330_000, 7_100.0)))
    repo.allTime.value = mapOf(
        SportType.RUN to t(310, 2_450.0, 790_000, 18_000.0),
        SportType.RIDE to t(42, 1_380.0, 190_000, 12_400.0),
        SportType.WEIGHT_TRAINING to t(25, 0.0, 67_000, 0.0),
    )
    repo.bests.value = listOf(
        PersonalBest(1_000.0, 228.0, 1, "Riverside tempo", SportType.RUN, now.minusSeconds(2_400)),
        PersonalBest(5_000.0, 1_262.0, 1, "Riverside tempo", SportType.RUN, now.minusSeconds(2_400)),
        PersonalBest(10_000.0, 2_651.0, 1, "Riverside tempo", SportType.RUN, now.minusSeconds(900_000)),
        PersonalBest(21_097.5, 5_902.0, 1, "Riverside tempo", SportType.RUN, now.minusSeconds(9_000_000)),
    )
    return repo
}

@Composable
private fun Tab(selected: TopLevelDestination, content: @Composable () -> Unit) {
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
    val vm = remember { FeedViewModel(sampleActivities(), profile, FakePreferencesRepository(), testTimeFormatter()) }
    val state by vm.uiState.collectAsState()
    FeedContent(state, FeedActions())
}

@Composable
private fun Me(tab: MeTab, empty: Boolean = false) {
    val vm = remember(tab) {
        val repo = if (empty) FakeActivityRepository() else sampleActivities()
        MeViewModel(repo, profile, FakePreferencesRepository(), testTimeFormatter(), TEST_CLOCK).also { it.selectTab(tab) }
    }
    val state by vm.uiState.collectAsState()
    MeContent(state, MeActions())
}

private val noImports = object : RouteImporter {
    override suspend fun import(uris: List<android.net.Uri>) = RouteImportSummary(emptyList(), emptyList())
}

@Composable
private fun Explore(empty: Boolean = false) {
    val vm = remember {
        val routes = if (empty) FakeRouteRepository() else FakeRouteRepository(
            listOf(
                routeDetail(1, "Riverside Loop"),
                routeDetail(2, "Sinhagad Climb", points = RIDE_TRACK, sport = SportType.RIDE, distanceMeters = 38_400.0, elevationGain = 640.0),
                routeDetail(3, "Lake Walk", sport = SportType.WALK, distanceMeters = 4_100.0, elevationGain = 12.0),
            ),
        )
        ExploreViewModel(routes, FakePreferencesRepository(), noImports)
    }
    val state by vm.uiState.collectAsState()
    ExploreContent(state, ExploreActions())
}

@Composable
private fun RouteDetail() {
    val vm = remember {
        val exporter = object : RouteExporter {
            override suspend fun exportGpx(routeId: Long, destination: android.net.Uri) = Unit
        }
        RouteDetailViewModel(
            SavedStateHandle(mapOf("routeId" to 2L)),
            FakeRouteRepository(listOf(routeDetail(2, "Sinhagad Climb", points = RIDE_TRACK, sport = SportType.RIDE, distanceMeters = 38_400.0, elevationGain = 640.0))),
            FakePreferencesRepository(),
            exporter,
        )
    }
    val state by vm.uiState.collectAsState()
    RouteDetailContent(state, RouteDetailActions())
}

@Composable
private fun Settings() {
    SettingsContent(
        state = SettingsUiState(
            settings = AppSettings(),
            distribution = Distribution("gms", "Play build", "Fused location provider", "Firebase Cloud Messaging"),
            versionName = "0.1.0",
        ),
        actions = SettingsActions(),
    )
}
