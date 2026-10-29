// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.data.model.NewActivity
import org.openactivity.core.export.file.ExportFormat
import org.openactivity.core.export.share.GallerySaver
import org.openactivity.feature.activitydetail.export.ActivityFileExporter
import org.openactivity.feature.activitydetail.export.RepositoryExportActivityProvider
import org.openactivity.model.UnitSystem
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ActivityDetailViewModelTest : RepositoryBackedTest() {

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun viewModel(id: Long) = ActivityDetailViewModel(
        activityId = id,
        activities = activities,
        extras = extras,
        settings = settings,
        fileExporter = ActivityFileExporter(RepositoryExportActivityProvider(activities, settings), testExporter(context), GallerySaver(context)),
        clock = clock,
    )

    private suspend fun ActivityDetailViewModel.content(): DetailContent = withTimeout(10_000) {
        (uiState.first { it is ActivityDetailUiState.Content } as ActivityDetailUiState.Content).activity
    }

    @Test
    fun `a stored run shows stats, chart, splits, laps and best efforts`(): Unit = runBlocking {
        val id = activities.saveFinished(Recordings.hillyRun(), NewActivity("rec-1", title = "Hill repeats", perceivedExertion = 7), UnitSystem.METRIC)
        extras.setMuted(id, true)
        val vm = viewModel(id)
        val job = launch { vm.uiState.collect {} }
        val c = vm.content()
        assertEquals("Hill repeats", c.title)
        assertEquals("Run", c.sportName)
        assertTrue(c.startedAt.startsWith("Yesterday at"))
        assertTrue(c.muted)
        assertEquals(7, c.perceivedExertion)
        assertEquals(listOf("Distance", "Moving time", "Avg pace", "Elev gain", "Elapsed time", "Avg HR", "Max HR"), c.stats.map { it.label })
        assertTrue(c.hasRoute)
        assertNotNull(c.chart?.seriesFor(org.openactivity.feature.activitydetail.chart.ChartMetric.HEART_RATE))
        assertTrue(c.splits.size >= 4)
        assertEquals(2, c.laps.size)
        assertTrue(c.showEfforts)
        assertTrue(c.efforts.any { it.name == "1K" && it.isPersonalBest })
        assertTrue(c.notes.contains("Elevation from GPS"))
        job.cancel()
    }

    @Test
    fun `switching units re-renders splits in miles`(): Unit = runBlocking {
        val id = activities.saveFinished(Recordings.hillyRun(), NewActivity("rec-2"), UnitSystem.METRIC)
        val vm = viewModel(id)
        val job = launch { vm.uiState.collect {} }
        assertEquals("Km", vm.content().splitUnit)
        settings.setUnits(UnitSystem.IMPERIAL)
        val imperial = withTimeout(10_000) {
            vm.uiState.first { (it as? ActivityDetailUiState.Content)?.activity?.splitUnit == "Mi" } as ActivityDetailUiState.Content
        }
        assertEquals("mi", imperial.activity.stats.first().unit)
        job.cancel()
    }

    @Test
    fun `deleting removes the activity and its extras and leaves the screen`(): Unit = runBlocking {
        val id = activities.saveFinished(Recordings.hillyRun(seconds = 300), NewActivity("rec-3"), UnitSystem.METRIC)
        val vm = viewModel(id)
        val job = launch { vm.uiState.collect {} }
        vm.content()
        vm.requestDelete()
        assertTrue((vm.uiState.value as ActivityDetailUiState.Content).dialogs.confirmDelete)
        vm.cancelDelete()
        assertFalse((vm.uiState.value as ActivityDetailUiState.Content).dialogs.confirmDelete)
        vm.requestDelete()
        vm.confirmDelete()
        assertEquals(DetailEffect.Deleted, withTimeout(10_000) { vm.effects.first() })
        assertNull(activities.observeActivity(id).first())
        assertEquals(listOf(id), extras.deleted)
        // Once the row is gone the screen shows a neutral state, never "not found", while leaving.
        val settled = withTimeout(10_000) { vm.uiState.first { it !is ActivityDetailUiState.Content } }
        assertEquals(ActivityDetailUiState.Loading, settled)
        job.cancel()
    }

    @Test
    fun `a missing activity shows not found`(): Unit = runBlocking {
        val vm = viewModel(99)
        val job = launch { vm.uiState.collect {} }
        assertEquals(ActivityDetailUiState.NotFound, withTimeout(10_000) { vm.uiState.first { it != ActivityDetailUiState.Loading } })
        job.cancel()
    }

    @Test
    fun `export file writes the format and asks where to save it`(): Unit = runBlocking {
        val id = activities.saveFinished(Recordings.hillyRun(seconds = 300), NewActivity("rec-4"), UnitSystem.METRIC)
        val vm = viewModel(id)
        val job = launch { vm.uiState.collect {} }
        vm.content()
        vm.openExportSheet()
        withTimeout(10_000) { vm.uiState.first { (it as? ActivityDetailUiState.Content)?.dialogs?.exportFormats != null } }
        vm.exportFile(ExportFormat.GPX, share = false)
        val effect = withTimeout(10_000) { vm.effects.first() }
        assertTrue(effect is DetailEffect.SaveDocument)
        assertTrue((effect as DetailEffect.SaveDocument).file.displayName.endsWith(".gpx"))
        withTimeout(10_000) { vm.uiState.first { (it as? ActivityDetailUiState.Content)?.dialogs?.exportFormats == null } }

        // The picker's answer goes back to the view model, which still holds the file (it survives recreation).
        val destination = java.io.File(context.cacheDir, "picked.gpx").also { it.delete() }
        vm.onDocumentCreated(android.net.Uri.fromFile(destination))
        val saved = withTimeout(10_000) { vm.effects.first() }
        assertEquals(DetailEffect.Message("Saved ${effect.file.displayName}"), saved)
        assertTrue(destination.length() > 0)
        job.cancel()
    }
}
