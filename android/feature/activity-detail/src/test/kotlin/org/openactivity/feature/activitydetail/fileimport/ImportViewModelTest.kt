// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.fileimport

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.fileimport.ActivityImporter
import org.openactivity.feature.activitydetail.RepositoryBackedTest
import org.robolectric.RobolectricTestRunner
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ImportViewModelTest : RepositoryBackedTest() {

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMainDispatcher() = Dispatchers.resetMain()

    private fun viewModel() = ImportViewModel(ActivityImporter.create(context), ActivityImportSaver(activities, settings))

    private fun file(name: String, text: String): Uri = Uri.fromFile(File(context.cacheDir, name).apply { writeText(text) })

    private suspend fun ImportViewModel.finished(): ImportUiState =
        withTimeout(20_000) { uiState.first { !it.running && it.results.isNotEmpty() } }

    @Test
    fun `a single new activity is stored and opened`(): Unit = runBlocking {
        val vm = viewModel()
        vm.import(listOf(file("morning.gpx", Gpx.run())))
        val state = vm.finished()
        val row = state.results.single()
        assertEquals(ImportStatus.IMPORTED, row.status)
        // Default title from the local start time in the phone's zone, e.g. "Morning Run".
        assertTrue(row.title!!.endsWith(" Run"))
        assertEquals(ImportEffect.OpenActivity(row.activityId!!), withTimeout(10_000) { vm.effects.first() })
        assertEquals(1, state.importedCount)
    }

    @Test
    fun `a batch reports duplicates and broken files without stopping`(): Unit = runBlocking {
        val gpx = Gpx.run()
        viewModel().apply { import(listOf(file("first.gpx", gpx))) }.finished()

        val vm = viewModel()
        vm.import(listOf(file("again.gpx", gpx), file("notes.txt", "hello"), file("plan.gpx", Gpx.route())))
        val rows = vm.finished().results
        assertEquals(listOf(ImportStatus.ALREADY_THERE, ImportStatus.FAILED, ImportStatus.FAILED), rows.map { it.status })
        assertTrue(rows[0].activityId != null)
        assertTrue(rows[1].message!!.isNotBlank())
        assertEquals(1, activities.observeActivities().first().size)
    }

    @Test
    fun `an empty pick does nothing`(): Unit = runBlocking {
        val vm = viewModel()
        vm.import(emptyList())
        assertEquals(ImportUiState(), vm.uiState.value)
    }
}
