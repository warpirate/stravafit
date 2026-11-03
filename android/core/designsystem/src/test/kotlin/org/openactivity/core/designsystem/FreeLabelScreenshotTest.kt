// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.designsystem.component.INCLUDED_FREE_DESCRIPTION
import org.openactivity.core.designsystem.component.IncludedFreeLabel
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The "Free" chip beside section titles, as it sits in the app, in light and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h200dp-xxhdpi")
class FreeLabelScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun freeLabelLight() = capture(dark = false, name = "free_label_light")

    @Test
    fun freeLabelDark() = capture(dark = true, name = "free_label_dark")

    @Test
    fun readsAsIncludedFree() {
        compose.setContent { RidgelineTheme(darkTheme = false, reduceMotion = true) { IncludedFreeLabel() } }
        compose.onNodeWithContentDescription(INCLUDED_FREE_DESCRIPTION).assertContentDescriptionEquals(INCLUDED_FREE_DESCRIPTION)
    }

    private fun capture(dark: Boolean, name: String) {
        compose.setContent {
            RidgelineTheme(darkTheme = dark, reduceMotion = true) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(Spacing.gutter)) {
                        listOf("Goals", "Training log", "Suggested routes").forEach { title ->
                            Row(Modifier.padding(vertical = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                                Text(title, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.width(8.dp))
                                IncludedFreeLabel()
                            }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }
}
