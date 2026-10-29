// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.shareexport

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.export.art.ArtColorBy
import org.openactivity.core.export.art.ArtSpec
import org.openactivity.core.export.art.ArtTemplate
import org.openactivity.core.export.art.ArtUnavailableReason
import org.openactivity.core.export.file.ExportFormat
import org.openactivity.feature.shareexport.ui.ShareExportActions
import org.openactivity.feature.shareexport.ui.ShareExportScreen
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ShareExportScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun content(instagram: Boolean, preview: PreviewState = PreviewState.Rendering(null)) = ShareExportUiState.Content(
        title = "Lake loop",
        spec = ArtSpec(),
        preview = preview,
        colorByOptions = ArtColorBy.entries.map { it to true },
        formats = ExportFormat.entries.map { it to null },
        instagramAvailable = instagram,
    )

    @Test
    fun `shows every action and the story button only when instagram is installed`() {
        val events = mutableListOf<String>()
        compose.setContent {
            RidgelineTheme {
                ShareExportScreen(
                    state = content(instagram = false),
                    actions = ShareExportActions(
                        onShare = { events += "share" },
                        onTemplate = { events += "template:$it" },
                        onFormatSheet = { events += "sheet:$it" },
                    ),
                )
            }
        }
        compose.onNodeWithText("Share").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save image").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Instagram story").assertDoesNotExist()
        compose.onNodeWithText("Export file").performScrollTo().performClick()
        compose.onNodeWithText("Poster").performScrollTo().performClick()
        compose.onNodeWithText("Hide start and finish").performScrollTo().assertIsDisplayed()
        assertEquals(listOf("sheet:true", "template:${ArtTemplate.POSTER}"), events)
    }

    @Test
    fun `story button appears when instagram is available`() {
        compose.setContent { RidgelineTheme { ShareExportScreen(content(instagram = true), ShareExportActions()) } }
        compose.onNodeWithText("Instagram story").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `missing route disables image actions and explains why`() {
        compose.setContent {
            RidgelineTheme {
                ShareExportScreen(content(false, PreviewState.Unavailable(ArtUnavailableReason.NO_ROUTE)), ShareExportActions())
            }
        }
        compose.onNodeWithText("Save image").assertIsNotEnabled()
        compose.onNodeWithText("This activity has no GPS route", substring = true).assertIsDisplayed()
    }

    @Test
    fun `unknown activity shows an empty state`() {
        compose.setContent { RidgelineTheme { ShareExportScreen(ShareExportUiState.NotFound, ShareExportActions()) } }
        compose.onNodeWithText("Activity not found").assertIsDisplayed()
    }
}
