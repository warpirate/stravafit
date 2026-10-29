// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.designsystem.component.ActivityCardSkeleton
import org.openactivity.core.designsystem.component.Avatar
import org.openactivity.core.designsystem.component.AvatarSize
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.component.Facepile
import org.openactivity.core.designsystem.component.PrimaryButton
import org.openactivity.core.designsystem.component.RecordButton
import org.openactivity.core.designsystem.component.RecordButtonState
import org.openactivity.core.designsystem.component.RidgelineCard
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.component.RidgelineTextButton
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.component.SecondaryButton
import org.openactivity.core.designsystem.component.SectionHeader
import org.openactivity.core.designsystem.component.Stat
import org.openactivity.core.designsystem.component.StatGrid
import org.openactivity.core.designsystem.component.StatRow
import org.openactivity.core.designsystem.component.TopBarAction
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every Ridgeline component in light and dark mode. Run
 * `./gradlew :android:core:designsystem:recordRoborazziDebug` to write the PNGs to `screenshots/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1400dp-xxhdpi")
class ComponentGalleryScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun galleryLight() = capture(dark = false, name = "gallery_light")

    @Test
    fun galleryDark() = capture(dark = true, name = "gallery_dark")

    private fun capture(dark: Boolean, name: String) {
        compose.setContent {
            RidgelineTheme(darkTheme = dark, reduceMotion = true) {
                Surface(color = MaterialTheme.colorScheme.background) { Gallery() }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Gallery() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
        RidgelineTopAppBar(title = "Components") {
            TopBarAction(Icons.Rounded.Search, "Search", onClick = {})
            TopBarAction(Icons.Rounded.Notifications, "Notifications", onClick = {}, badgeCount = 3)
        }
        Column(Modifier.padding(horizontal = Spacing.gutter), verticalArrangement = Arrangement.spacedBy(Spacing.l)) {
            SectionHeader("Buttons", actionLabel = "See all")
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                PrimaryButton("Save", onClick = {})
                SecondaryButton("Add", onClick = {}, leadingIcon = Icons.Rounded.Add)
                RidgelineTextButton("Cancel", onClick = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                RecordButton(RecordButtonState.Acquiring, onClick = {})
                RecordButton(RecordButtonState.Ready, onClick = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                RidgelineFilterChip("Following", selected = true, onClick = {})
                RidgelineFilterChip("Run", selected = false, onClick = {}, leadingIcon = Icons.Rounded.DirectionsRun)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Avatar("Ana Ruiz", size = AvatarSize.l)
                Avatar("Dev Kulkarni", size = AvatarSize.m)
                Facepile(listOf("Ana Ruiz", "Dev Kulkarni", "Li Wei"), totalCount = 12)
            }
            RidgelineCard(Modifier.fillMaxWidth()) {
                Text("Card title", style = MaterialTheme.typography.titleMedium)
                StatRow(listOf(Stat("Distance", "10.20", "km"), Stat("Pace", "5:12", "/km"), Stat("Time", "53:04")))
            }
            StatGrid(listOf(Stat("Activities", "142"), Stat("Distance", "1,204", "km"), Stat("Avg HR", null)))
            ActivityCardSkeleton()
            EmptyState(
                icon = Icons.Rounded.DynamicFeed,
                title = "Nothing here yet",
                body = "Record an activity to get started.",
                primaryActionLabel = "Record",
            )
        }
    }
}
