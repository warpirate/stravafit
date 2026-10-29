// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.shareexport

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.export.art.ArtAspect
import org.openactivity.core.export.art.ArtColorBy
import org.openactivity.core.export.art.ArtTemplate
import org.openactivity.core.export.art.ArtUnavailableReason
import org.openactivity.core.export.file.ExportFormat
import org.openactivity.core.export.file.FormatUnavailableReason
import org.openactivity.core.export.share.GallerySaver
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ShareExportViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(id: String, provider: ExportActivityProvider = FakeProvider(Fixtures.run(), Fixtures.indoor())) =
        ShareExportViewModel(id, provider, Fixtures.exporter(context), GallerySaver(context), instagramAvailable = false)

    private fun ShareExportViewModel.content(predicate: (ShareExportUiState.Content) -> Boolean = { true }) = runBlocking {
        withTimeout(TIMEOUT) {
            uiState.first { it is ShareExportUiState.Content && predicate(it) } as ShareExportUiState.Content
        }
    }

    @Test
    fun `unknown activity shows not found`() = runBlocking {
        val vm = viewModel("missing")
        assertEquals(ShareExportUiState.NotFound, withTimeout(TIMEOUT) { vm.uiState.first { it != ShareExportUiState.Loading } })
    }

    @Test
    fun `load failures can be retried`() = runBlocking {
        val provider = FakeProvider(Fixtures.run()).apply { failNext = IllegalStateException("disk unavailable") }
        val vm = viewModel("run-1", provider)
        val failed = withTimeout(TIMEOUT) { vm.uiState.first { it is ShareExportUiState.LoadFailed } }
        assertEquals("disk unavailable", (failed as ShareExportUiState.LoadFailed).message)
        vm.load()
        vm.content()
        Unit
    }

    @Test
    fun `renders a live preview at half resolution`() {
        val vm = viewModel("run-1")
        val content = vm.content { it.preview is PreviewState.Ready }
        val bitmap = (content.preview as PreviewState.Ready).bitmap
        assertEquals(540, bitmap.width)
        assertEquals(540, bitmap.height)
        assertEquals(ArtColorBy.PACE, content.spec.colorBy)
        assertTrue(content.formats.all { it.second == null })
        assertFalse(content.instagramAvailable)
    }

    @Test
    fun `changing template re-renders with the template's aspect`() {
        val vm = viewModel("run-1")
        vm.content { it.preview is PreviewState.Ready }
        vm.selectTemplate(ArtTemplate.POSTER)
        val content = vm.content { it.spec.template == ArtTemplate.POSTER && it.preview is PreviewState.Ready }
        assertEquals(ArtAspect.POSTER, content.spec.aspect)
        assertEquals(800, (content.preview as PreviewState.Ready).bitmap.height)

        vm.selectAspect(ArtAspect.STORY) // not offered by the poster: ignored
        assertEquals(ArtAspect.POSTER, vm.content().spec.aspect)
    }

    @Test
    fun `indoor activities explain the missing route and offer time-based files`() {
        val vm = viewModel("ride-1")
        val content = vm.content { it.preview is PreviewState.Unavailable }
        assertEquals(PreviewState.Unavailable(ArtUnavailableReason.NO_ROUTE), content.preview)
        assertEquals(ArtColorBy.SOLID, content.spec.colorBy)
        assertFalse(content.imageAvailable)
        val formats = content.formats.toMap()
        assertEquals(FormatUnavailableReason.NEEDS_ROUTE, formats[ExportFormat.GPX])
        assertEquals(FormatUnavailableReason.NEEDS_ROUTE, formats[ExportFormat.PNG])
        assertNull(formats[ExportFormat.TCX])
        assertNull(formats[ExportFormat.FIT])
        assertEquals(false, content.colorByOptions.toMap()[ArtColorBy.PACE])
    }

    @Test
    fun `exporting a file shares it or asks where to save it`() = runBlocking {
        val vm = viewModel("run-1")
        vm.content { it.preview is PreviewState.Ready }
        vm.setFormatSheetOpen(true)
        assertTrue(vm.content().formatSheetOpen)

        vm.exportFile(ExportFormat.GPX, FileDestination.SHARE)
        val shared = withTimeout(TIMEOUT) { vm.effects.first() } as ShareEffect.ShareFile
        assertTrue(shared.file.displayName.endsWith(".gpx"))
        assertEquals("application/gpx+xml", shared.file.mimeType)
        assertFalse(vm.content { it.busy == null }.formatSheetOpen)

        vm.exportFile(ExportFormat.FIT, FileDestination.SAVE_TO_DEVICE)
        val save = withTimeout(TIMEOUT) { vm.effects.first() } as ShareEffect.SaveDocument
        assertTrue(save.file.displayName.endsWith(".fit"))
    }

    @Test
    fun `share renders the full-size image`() = runBlocking {
        val vm = viewModel("run-1")
        vm.content { it.preview is PreviewState.Ready }
        vm.share()
        val effect = withTimeout(TIMEOUT) { vm.effects.first() } as ShareEffect.ShareFile
        assertEquals("image/png", effect.file.mimeType)
        assertTrue(effect.file.file.length() > 0)
    }

    private companion object {
        const val TIMEOUT = 20_000L
    }
}
