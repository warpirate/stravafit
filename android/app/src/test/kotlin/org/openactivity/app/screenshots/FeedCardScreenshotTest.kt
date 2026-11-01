// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.app.feature.feed.ActivityCard
import org.openactivity.app.feature.feed.ActivityCardActions
import org.openactivity.app.feature.feed.ActivityCardUi
import org.openactivity.app.feature.feed.Achievements
import org.openactivity.app.feature.feed.FeedActions
import org.openactivity.app.feature.feed.FeedContent
import org.openactivity.app.feature.feed.FeedViewModel
import org.openactivity.app.model.UserProfile
import org.openactivity.app.testing.FakeActivityExtrasRepository
import org.openactivity.app.testing.FakeActivityRepository
import org.openactivity.app.testing.FakeFeedSeenRepository
import org.openactivity.app.testing.FakePreferencesRepository
import org.openactivity.app.testing.FakeProfileRepository
import org.openactivity.app.testing.TEST_CLOCK
import org.openactivity.app.testing.savedActivity
import org.openactivity.app.testing.testTimeFormatter
import org.openactivity.core.activityextras.ActivityExtras
import org.openactivity.core.activityextras.ActivityPhoto
import org.openactivity.core.designsystem.component.Stat
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.map.thumbnail.CanvasThumbnailRenderer
import org.openactivity.core.map.thumbnail.LocalRouteThumbnailLoader
import org.openactivity.core.map.thumbnail.RouteThumbnailLoader
import org.openactivity.core.map.thumbnail.ThumbnailCache
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The feed card's map in each loading state, light and dark, plus the photo carousel and the
 * unseen-first feed with its "You're all caught up" divider.
 *
 * Robolectric cannot run MapLibre, so the "snapshot" here is a stand-in base map drawn in the test
 * (test-only fixture): what matters is the card's behaviour around it, the cross-fade from the
 * plain drawing, the OSM credit that only real snapshots carry, and the plain drawing that stays
 * while a snapshot is pending or the device is offline.
 *
 * Record with `./gradlew :android:app:recordRoborazziGmsDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class FeedCardScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test fun cardMapSnapshotLight() = snap("feed_card_map_snapshot_light", dark = false, loader = snapshotLoader()) { Card(card()) }

    @Test fun cardMapSnapshotDark() = snap("feed_card_map_snapshot_dark", dark = true, loader = snapshotLoader()) { Card(card()) }

    @Test fun cardMapPendingLight() = snap("feed_card_map_pending_light", dark = false, loader = pendingLoader()) { Card(card()) }

    @Test fun cardMapOfflineDark() = snap("feed_card_map_offline_dark", dark = true, loader = offlineLoader()) { Card(card()) }

    @Test fun cardCarouselLight() = snap("feed_card_carousel_light", dark = false, loader = snapshotLoader()) {
        Card(card(photos = listOf(photo("a"), photo("b"))))
    }

    @Test fun cardCarouselDark() = snap("feed_card_carousel_dark", dark = true, loader = snapshotLoader()) {
        Card(card(photos = listOf(photo("a"), photo("b"), photo("c"))))
    }

    @Test fun feedUnseenLight() = snap("feed_unseen_light", dark = false, loader = snapshotLoader()) { UnseenFeed() }

    @Test fun feedUnseenDark() = snap("feed_unseen_dark", dark = true, loader = snapshotLoader()) { UnseenFeed() }

    private fun snap(name: String, dark: Boolean, loader: RouteThumbnailLoader, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalRouteThumbnailLoader provides loader) {
                RidgelineTheme(darkTheme = dark, reduceMotion = true) {
                    Column(Modifier.fillMaxSize().background(RidgelineTheme.colors.gap)) { content() }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Composable
    private fun Card(card: ActivityCardUi) = ActivityCard(card, ActivityCardActions())

    @Composable
    private fun UnseenFeed() {
        val vm = remember {
            val now = TEST_CLOCK.instant()
            val repo = FakeActivityRepository(
                listOf(
                    savedActivity(1, "Riverside tempo", start = now.minusSeconds(2_400)),
                    // Imported after the last visit: unseen, so above the divider despite its older date.
                    savedActivity(2, "Sunday long run", start = now.minusSeconds(90_000), distanceMeters = 21_100.0, movingSeconds = 6_900, track = LOOP),
                ),
            )
            FeedViewModel(
                repo,
                FakeProfileRepository(UserProfile(name = "Asha Rao", location = "Pune")),
                FakePreferencesRepository(),
                testTimeFormatter(),
                FakeActivityExtrasRepository(),
                FakeFeedSeenRepository(initial = 1L),
                org.openactivity.app.testing.FakeNotificationRepository(),
            )
        }
        val state by vm.uiState.collectAsState()
        FeedContent(state, FeedActions())
    }

    private fun card(photos: List<ActivityPhoto> = emptyList()) = ActivityCardUi(
        id = 7,
        athleteName = "Asha Rao",
        athletePhotoPath = null,
        meta = "Today at 06:12 · Pune",
        visibilityLabel = null,
        sport = SportType.RUN,
        title = "Riverside tempo",
        description = null,
        stats = listOf(Stat("Distance", "8.02", "km"), Stat("Pace", "5:12", "/km"), Stat("Time", "41:40")),
        route = LOOP,
        highFiveCount = 0,
        achievements = Achievements(2, "1K"),
        photos = photos,
        isNew = true,
    )

    /** Photo entries for the carousel pages; only the map page is on screen, so no file is decoded. */
    private fun photo(id: String) = ActivityPhoto(id, File("missing-$id.jpg"), 1600, 1200)

    private fun snapshotLoader() = RouteThumbnailLoader(
        cache = NoCache,
        snapshot = { request -> stubBaseMap(drawing.renderNow(request)) },
        fallback = drawing,
        network = { true },
    )

    private fun pendingLoader() = RouteThumbnailLoader(NoCache, { awaitCancellation() }, drawing, { true })

    private fun offlineLoader() = RouteThumbnailLoader(NoCache, { error("offline") }, drawing, { false })

    private object NoCache : ThumbnailCache {
        override suspend fun get(key: String): Bitmap? = null
        override suspend fun put(key: String, bitmap: Bitmap) = Unit
    }

    private companion object {
        /** The plain drawing, rendered on the calling thread so the screenshot does not race it. */
        val drawing = CanvasThumbnailRenderer(Dispatchers.Unconfined)

        val LOOP = listOf(
            LatLng(18.5300, 73.8400), LatLng(18.5420, 73.8480), LatLng(18.5510, 73.8650), LatLng(18.5480, 73.8820),
            LatLng(18.5350, 73.8900), LatLng(18.5230, 73.8780), LatLng(18.5300, 73.8400),
        )

        /** Test-only stand-in for map tiles: a river and a park washed over the drawing. */
        fun stubBaseMap(drawn: Bitmap?): Bitmap? {
            drawn ?: return null
            val out = drawn.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(out)
            val w = out.width.toFloat()
            val h = out.height.toFloat()
            canvas.drawRect(0f, h * 0.55f, w, h * 0.68f, Paint().apply { color = 0x664A90D9 })
            canvas.drawRect(w * 0.06f, h * 0.08f, w * 0.3f, h * 0.36f, Paint().apply { color = 0x5546A35A })
            return out
        }
    }
}
