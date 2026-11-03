// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.app.feature.recap.MonthCardActions
import org.openactivity.app.feature.recap.MonthCardContent
import org.openactivity.app.feature.recap.MonthCardUiState
import org.openactivity.app.feature.recap.PeriodCardRenderer
import org.openactivity.app.feature.recap.PeriodCards
import org.openactivity.app.feature.recap.RecapActions
import org.openactivity.app.feature.recap.RecapContent
import org.openactivity.app.feature.recap.RecapMath
import org.openactivity.app.feature.recap.RecapUiState
import org.openactivity.app.feature.recap.RecapViewModel
import org.openactivity.app.format.UnitFormatter
import org.openactivity.app.testing.savedActivity
import org.openactivity.core.data.model.Goal
import org.openactivity.core.data.model.GoalMetric
import org.openactivity.core.data.model.GoalPeriod
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.model.LatLng
import org.openactivity.model.SportType
import org.openactivity.model.UnitSystem
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import routeexport.CardStyle
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * The weekly recap screen and the monthly share card (light, dark and the transparent sticker), the card
 * drawn by the real route-art and SVG pipeline. Record with
 * `./gradlew :android:app:recordRoborazziFossDebug --tests '*RecapScreenshotTest*'`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class RecapScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val formatter = UnitFormatter(UnitSystem.METRIC, Locale.UK)

    @Test fun recapLight() = snap("recap_light", dark = false) { RecapContent(recapState(), RecapActions()) }

    @Test fun recapDark() = snap("recap_dark", dark = true) { RecapContent(recapState(), RecapActions()) }

    @Test fun recapEmpty() = snap("recap_empty_light", dark = false) {
        RecapContent(RecapViewModel.recapUi(RecapMath.recap(emptyList(), emptyList(), WEEK), formatter, Locale.UK), RecapActions())
    }

    @Test fun monthCardLight() = month("month_card_light", CardStyle.LIGHT, dark = false)

    @Test fun monthCardDark() = month("month_card_dark", CardStyle.DARK, dark = true)

    @Test fun monthCardSticker() = month("month_card_sticker", CardStyle.STICKER, dark = false)

    private fun month(name: String, style: CardStyle, dark: Boolean) {
        val card = PeriodCards.month(OCTOBER, monthActivities(), formatter, Locale.UK)
        val preview = runBlocking { PeriodCardRenderer.forPreview().preview(card, style) }
        snap(name, dark) {
            MonthCardContent(
                MonthCardUiState(month = OCTOBER, style = style, preview = preview, activityCount = monthActivities().size, canGoNext = true),
                MonthCardActions(),
            )
        }
    }

    private fun recapState(): RecapUiState {
        val goals = listOf(
            Goal(1, SportType.RUN, GoalPeriod.WEEK, GoalMetric.DISTANCE, 25_000.0),
            Goal(2, null, GoalPeriod.WEEK, GoalMetric.COUNT, 5.0),
        )
        return RecapViewModel.recapUi(RecapMath.recap(monthActivities(), goals, WEEK), formatter, Locale.UK)
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

    private companion object {
        val OCTOBER: YearMonth = YearMonth.of(2026, 10)
        val WEEK: LocalDate = LocalDate.of(2026, 10, 26)

        /** A loop of [n] points around a centre, stretched and turned, so each card cell has its own shape. */
        fun loop(lat: Double, lon: Double, rx: Double, ry: Double, turn: Double, wobble: Double, n: Int = 40): List<LatLng> =
            (0..n).map { i ->
                val t = 2 * Math.PI * i / n
                val r = 1 + wobble * sin(3 * t)
                val x = rx * r * cos(t)
                val y = ry * r * sin(t)
                LatLng(lat + x * cos(turn) - y * sin(turn), lon + x * sin(turn) + y * cos(turn))
            }

        fun out(lat: Double, lon: Double, dLat: Double, dLon: Double, n: Int = 30): List<LatLng> =
            (0..n).map { i -> LatLng(lat + dLat * i / n + 0.002 * sin(i / 3.0), lon + dLon * i / n) }

        fun monthActivities() = listOf(
            savedActivity(1, "Morning Run", start = Instant.parse("2026-10-02T01:00:00Z"), distanceMeters = 8_100.0, movingSeconds = 2_640, track = loop(18.52, 73.85, 0.012, 0.008, 0.3, 0.15)),
            savedActivity(2, "Hill Repeats", start = Instant.parse("2026-10-05T01:00:00Z"), distanceMeters = 6_400.0, movingSeconds = 2_300, elevationGain = 180.0, track = out(18.50, 73.80, 0.02, 0.01)),
            savedActivity(3, "River Ride", SportType.RIDE, start = Instant.parse("2026-10-09T02:00:00Z"), distanceMeters = 42_300.0, movingSeconds = 5_400, track = loop(18.55, 73.90, 0.05, 0.02, 1.1, 0.25)),
            savedActivity(4, "Easy Run", start = Instant.parse("2026-10-14T01:00:00Z"), distanceMeters = 5_000.0, movingSeconds = 1_800, track = loop(18.51, 73.86, 0.007, 0.007, 0.0, 0.05)),
            savedActivity(5, "Yoga", SportType.YOGA, start = Instant.parse("2026-10-18T12:00:00Z"), distanceMeters = 0.0, movingSeconds = 3_600, elevationGain = 0.0, track = null),
            savedActivity(6, "Tempo", start = Instant.parse("2026-10-21T01:00:00Z"), distanceMeters = 10_000.0, movingSeconds = 2_900, track = loop(18.53, 73.84, 0.015, 0.006, 2.0, 0.3)),
            savedActivity(7, "Commute", SportType.RIDE, start = Instant.parse("2026-10-27T03:00:00Z"), distanceMeters = 12_800.0, movingSeconds = 2_700, track = out(18.52, 73.85, -0.03, 0.04)),
            savedActivity(8, "Long Run", start = Instant.parse("2026-10-31T00:30:00Z"), distanceMeters = 21_100.0, movingSeconds = 7_200, elevationGain = 120.0, track = loop(18.56, 73.82, 0.03, 0.018, 0.7, 0.2)),
            savedActivity(9, "Track Session", start = Instant.parse("2026-10-29T12:30:00Z"), distanceMeters = 7_200.0, movingSeconds = 2_100, track = loop(18.515, 73.855, 0.002, 0.001, 0.0, 0.0)),
        )
    }
}
