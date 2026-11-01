// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.fileimport

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
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
    fun `files shared to the app are imported once, as soon as the screen opens`(): Unit = runBlocking {
        val shared = file("shared.gpx", Gpx.run()).toString()
        val handle = SavedStateHandle(mapOf(ImportViewModel.ARG_URIS to arrayOf(shared)))
        val vm = ImportViewModel(ActivityImporter.create(context), ActivityImportSaver(activities, settings), handle)
        assertEquals(ImportStatus.IMPORTED, vm.finished().results.single().status)

        // Recreated with the same arguments (rotation, process restore): no second import.
        val again = ImportViewModel(ActivityImporter.create(context), ActivityImportSaver(activities, settings), handle)
        assertEquals(ImportUiState(), again.uiState.value)
        assertEquals(1, activities.observeActivities().first().size)
    }

    @Test
    fun `shared uri arguments arrive as an array or a list`() {
        assertEquals(listOf("content://a"), ImportViewModel.sharedUris(arrayOf("content://a", "")))
        assertEquals(listOf("content://b"), ImportViewModel.sharedUris(listOf("content://b")))
        assertTrue(ImportViewModel.sharedUris(null).isEmpty())
    }

    @Test
    fun `an empty pick does nothing`(): Unit = runBlocking {
        val vm = viewModel()
        vm.import(emptyList())
        assertEquals(ImportUiState(), vm.uiState.value)
    }
}
