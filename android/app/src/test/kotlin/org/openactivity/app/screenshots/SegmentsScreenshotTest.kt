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
import org.openactivity.app.feature.segments.CreateSegmentActions
import org.openactivity.app.feature.segments.CreateSegmentContent
import org.openactivity.app.feature.segments.CreateSegmentUiState
import org.openactivity.app.feature.segments.ProfileUi
import org.openactivity.app.feature.segments.SegmentDetailActions
import org.openactivity.app.feature.segments.SegmentDetailContent
import org.openactivity.app.feature.segments.SegmentDetailViewModel
import org.openactivity.app.feature.segments.SegmentRowUi
import org.openactivity.app.feature.segments.SegmentPrUi
import org.openactivity.app.feature.segments.SegmentsContent
import org.openactivity.app.feature.segments.SegmentsTab
import org.openactivity.app.feature.segments.SegmentsUiState
import org.openactivity.core.data.model.Segment
import org.openactivity.core.data.model.SegmentEffort
import org.openactivity.core.designsystem.component.Stat
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.geo.Spherical
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds

/**
 * Screenshots of Create segment, a segment's detail and You, Segments, light and dark. Maps render as the
 * offline drawing (inspection mode), since Robolectric cannot load MapLibre. The data is built in the test
 * only, to look at the screens.
 *
 * Record goldens with `./gradlew :android:app:recordRoborazziFossDebug --tests '*SegmentsScreenshotTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class SegmentsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val origin = LatLng(47.3700, 8.5400)

    /** A wiggly 3 km line north-east of the origin, as a test track. */
    private val route: List<LatLng> = (0..150).map { i ->
        Spherical.destination(Spherical.destination(origin, 30.0, i * 20.0), 120.0, 60 * sin(i / 9.0))
    }

    private val profile = ProfileUi(
        xs = (0..60).map { it / 60f },
        ys = (0..60).map { (0.5 + 0.45 * sin(it / 8.0)).toFloat() },
        minLabel = "402 m",
        maxLabel = "461 m",
    )

    private val create = CreateSegmentUiState(
        isLoading = false,
        activityTitle = "Morning Run",
        route = route,
        highlight = route.subList(50, 100),
        profile = profile,
        range = 0.33f..0.66f,
        stats = listOf(Stat("Distance", "1.00", "km"), Stat("Elev Gain", "31", "m"), Stat("Avg Grade", "2.9%")),
        name = "Riverside climb",
        sport = SportType.RUN,
        sportOptions = listOf(SportType.RUN, SportType.TRAIL_RUN, SportType.VIRTUAL_RUN),
        minLength = "0.20 km",
    )

    private fun detail(units: UnitSystem): org.openactivity.app.feature.segments.SegmentDetailUiState {
        Locale.setDefault(Locale.US)
        val segment = Segment(
            id = 7, name = "Riverside climb", sport = SportType.RUN, points = route.subList(50, 100),
            elevations = (50 until 100).map { 400.0 + 30 * sin(it / 9.0) }, distanceMeters = 1003.0, elevationGainMeters = 31.0,
            averageGradePercent = 2.9, starred = true, createdAt = Instant.parse("2026-09-01T07:00:00Z"), sourceActivityId = 1,
        )
        val efforts = listOf(
            effort(1, "2026-09-01T07:10:00Z", 312, rankAll = null, rankYear = null, pr = false, title = "Morning Run"),
            effort(2, "2026-09-15T07:10:00Z", 298, rankAll = 1, rankYear = 1, pr = true, title = "Tempo Tuesday"),
            effort(3, "2026-10-02T18:40:00Z", 305, rankAll = 2, rankYear = 2, pr = false, title = "Evening Run"),
            effort(4, "2026-10-20T07:05:00Z", 286, rankAll = 1, rankYear = 1, pr = true, title = "Race prep"),
            effort(5, "2026-10-29T07:05:00Z", 301, rankAll = 4, rankYear = 4, pr = false, title = "Easy loop"),
        )
        return SegmentDetailViewModel.build(segment, efforts, units, Locale.US)
    }

    private fun effort(id: Long, at: String, seconds: Long, rankAll: Int?, rankYear: Int?, pr: Boolean, title: String) = SegmentEffort(
        id = id, segmentId = 7, segmentName = "Riverside climb", segmentSport = SportType.RUN, segmentDistanceMeters = 1003.0,
        segmentGradePercent = 2.9, activityId = id, activityTitle = title, activitySport = SportType.RUN,
        startTime = Instant.parse(at), timeZone = ZoneOffset.UTC, year = 2026, startIndex = 0, endIndex = 10,
        elapsed = seconds.seconds, moving = seconds.seconds, distanceMeters = 1008.0, averageSpeedMps = 1008.0 / seconds,
        averageHeartRate = 158.0, maxHeartRate = 171, averagePower = null, averageCadence = 172.0,
        rankAllTime = rankAll, rankYear = rankYear, isPersonalRecord = pr,
    )

    private val rows = listOf(
        SegmentRowUi(7, "Riverside climb", SportType.RUN, "1.00 km · 2.9% · 5 efforts", "4:46", starred = true, points = route),
        SegmentRowUi(8, "Bridge sprint", SportType.RUN, "0.31 km · -0.4% · 12 efforts", "1:02", starred = true, points = route),
        SegmentRowUi(9, "The long drag up to the water tower and the old gate", SportType.RIDE, "4.20 km · 4.8% · 1 effort", "14:31", starred = false, points = route),
    )

    private val list = SegmentsUiState(
        isLoading = false,
        starred = rows.filter { it.starred },
        all = rows,
        prs = listOf(
            SegmentPrUi(8, "Bridge sprint", "1:02", "Oct 20, 2026", "0.31 km · -0.4%"),
            SegmentPrUi(7, "Riverside climb", "4:46", "Oct 20, 2026", "1.00 km · 2.9%"),
        ),
    )

    @Test fun createLight() = snap("segment_create_light", dark = false) { CreateSegmentContent(create, CreateSegmentActions()) }

    @Test fun createDark() = snap("segment_create_dark", dark = true) {
        CreateSegmentContent(create.copy(range = 0.4f..0.43f, tooShort = true, stats = listOf(Stat("Distance", "0.09", "km"))), CreateSegmentActions())
    }

    @Test fun detailLight() = snap("segment_detail_light", dark = false) { SegmentDetailContent(detail(UnitSystem.METRIC), SegmentDetailActions()) }

    @Test fun detailDark() = snap("segment_detail_dark", dark = true) { SegmentDetailContent(detail(UnitSystem.IMPERIAL), SegmentDetailActions()) }

    @Test fun listLight() = snap("segments_list_light", dark = false) { SegmentsContent(list) }

    @Test fun listDark() = snap("segments_list_dark", dark = true) { SegmentsContent(list.copy(tab = SegmentsTab.PRS)) }

    @Test fun listEmpty() = snap("segments_list_empty_light", dark = false) { SegmentsContent(SegmentsUiState(isLoading = false)) }

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
