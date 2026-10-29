// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.map.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.map.style.ConfigurableMapStyleProvider
import org.openactivity.core.map.style.MapStyleConfig
import org.openactivity.core.map.style.MapStyleKind
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class MapControlsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun recenterStartsFollowing() {
        val state = MapState()
        compose.setContent { RidgelineTheme { RecenterButton(state) } }
        compose.onNodeWithContentDescription("Centre on my location").performClick()
        assertEquals(CameraTracking.Follow, state.tracking)
        compose.onNodeWithContentDescription("Following your location").assertIsSelected()
    }

    @Test
    fun styleSwitcherReportsTheChosenStyle() {
        var selected by mutableStateOf(MapStyleKind.Standard)
        compose.setContent {
            RidgelineTheme {
                MapStyleSwitcher(MapStyleKind.entries, selected, onSelect = { selected = it })
            }
        }
        compose.onNodeWithText("Dark").performClick()
        assertEquals(MapStyleKind.Dark, selected)
        compose.onNodeWithText("Outdoor").performClick()
        assertEquals(MapStyleKind.Outdoor, selected)
    }

    @Test
    fun attributionIsVisibleText() {
        val definition = ConfigurableMapStyleProvider(MapStyleConfig.OpenFreeMap).style(MapStyleKind.Standard)
        compose.setContent { RidgelineTheme { MapAttribution(definition) } }
        compose.onNodeWithText("© OpenStreetMap contributors", substring = true).assertExists()
    }
}
