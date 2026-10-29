// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.activityextras.ActivityExtras
import org.openactivity.core.data.model.NewActivity
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.feature.activitydetail.fileimport.ImportContent
import org.openactivity.feature.activitydetail.fileimport.ImportRow
import org.openactivity.feature.activitydetail.fileimport.ImportStatus
import org.openactivity.feature.activitydetail.fileimport.ImportUiState
import org.openactivity.feature.activitydetail.ui.ActivityDetailContent
import org.openactivity.feature.activitydetail.ui.AnalysisSection
import org.openactivity.feature.activitydetail.ui.DetailActions
import org.openactivity.feature.activitydetail.ui.EffortsList
import org.openactivity.feature.activitydetail.ui.SplitsTable
import org.openactivity.model.UnitSystem
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.util.Locale

/**
 * Screenshots of the activity detail and import screens in light and dark, from an activity the real
 * recording engine produced and the real repository stored. Record with
 * `./gradlew :android:feature:activity-detail:recordRoborazziDebug` (written to `screenshots/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class DetailScreenshotTest : RepositoryBackedTest() {

    @get:Rule
    val compose = createComposeRule()

    private fun content(): DetailContent = runBlocking {
        Locale.setDefault(Locale.US)
        val id = activities.saveFinished(
            Recordings.hillyRun(seconds = 1800),
            NewActivity("shot", title = "Sunday hill loop", description = "Easy start, pushed the climbs.", perceivedExertion = 6),
            UnitSystem.METRIC,
        )
        val detail = activities.observeActivityDetail(id).first()!!
        val bests = activities.observePersonalBests(setOf(detail.activity.sport)).first()
        ActivityDetailViewModel.buildContent(detail, UnitSystem.METRIC, ActivityExtras.NONE, bests, LocalDate.of(2025, 6, 10))
    }

    private fun snap(name: String, dark: Boolean, body: @Composable () -> Unit) {
        // Inspection mode swaps the native MapLibre view for the offline route drawing.
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) { RidgelineTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.background) { body() } } }
        }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test
    fun detailLight() {
        val c = content()
        snap("detail_light", dark = false) { ActivityDetailContent(ActivityDetailUiState.Content(c), DetailActions()) }
    }

    @Test
    fun detailDark() {
        val c = content()
        snap("detail_dark", dark = true) { ActivityDetailContent(ActivityDetailUiState.Content(c), DetailActions()) }
    }

    @Test
    fun analysisLight() {
        val c = content()
        snap("detail_analysis_light", dark = false) { Sections(c, scrub = c.chart!!.x.size / 3) }
    }

    @Test
    fun analysisDark() {
        val c = content()
        snap("detail_analysis_dark", dark = true) { Sections(c, scrub = null) }
    }

    @Test
    fun notFound() = snap("detail_not_found_light", dark = false) {
        ActivityDetailContent(ActivityDetailUiState.NotFound, DetailActions())
    }

    @Test
    fun importResults() = snap("import_light", dark = false) {
        ImportContent(
            state = ImportUiState(
                results = listOf(
                    ImportRow("2025-05-04-run.fit", ImportStatus.IMPORTED, "Morning Run", null, 1),
                    ImportRow("export.gpx", ImportStatus.ALREADY_THERE, "Evening Ride", "Already in your activities", 2),
                    ImportRow("plan.gpx", ImportStatus.FAILED, null, "The file has no timestamps, so it looks like a planned route", null),
                ),
            ),
            onBack = {},
            onPick = {},
            onOpenActivity = {},
        )
    }

    @Test
    fun importEmptyDark() = snap("import_empty_dark", dark = true) {
        ImportContent(ImportUiState(), onBack = {}, onPick = {}, onOpenActivity = {})
    }

    @Composable
    private fun Sections(c: DetailContent, scrub: Int?) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            AnalysisSection(c.chart!!, c.sport, c.units, scrubIndex = scrub, onScrub = {})
            SplitsTable(c.splits, c.splitUnit, "Pace", selected = 2, onSelect = {})
            EffortsList(c.efforts)
        }
    }
}
