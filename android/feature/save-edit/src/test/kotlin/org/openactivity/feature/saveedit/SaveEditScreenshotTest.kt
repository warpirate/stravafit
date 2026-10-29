// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.saveedit

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.feature.saveedit.ui.SaveEditActions
import org.openactivity.feature.saveedit.ui.SaveEditContent
import org.openactivity.model.SportType
import org.openactivity.model.Visibility
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner

/**
 * Screenshots of the Save and Edit screens in light and dark. Record with
 * `./gradlew :android:feature:save-edit:recordRoborazziDebug` (written to `screenshots/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h1500dp-xxhdpi")
class SaveEditScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun editing(isNew: Boolean, rpe: Int?, title: String, dirty: Boolean = false) = SaveEditUiState.Editing(
        isNew = isNew,
        form = SaveEditForm(
            title = title,
            description = if (isNew) "" else "Easy start, pushed the climbs.",
            sport = SportType.RUN,
            visibility = if (isNew) Visibility.FOLLOWERS else Visibility.EVERYONE,
            perceivedExertion = rpe,
            muted = !isNew,
        ),
        facts = SaveEditFacts(
            defaultTitle = "Morning Run",
            summary = "5.39 km · 29:59",
            hideStartMeters = 200.0,
            hideEndMeters = 200.0,
            hasRoute = true,
        ),
        photos = emptyList(),
        dirty = dirty,
    )

    private fun snap(name: String, dark: Boolean, body: @Composable () -> Unit) {
        compose.setContent { RidgelineTheme(darkTheme = dark) { Surface(color = MaterialTheme.colorScheme.background) { body() } } }
        compose.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test
    fun saveLight() = snap("save_light", dark = false) {
        SaveEditContent(editing(isNew = true, rpe = null, title = "Morning Run"), SaveEditActions())
    }

    @Test
    fun saveDark() = snap("save_dark", dark = true) {
        SaveEditContent(editing(isNew = true, rpe = 7, title = ""), SaveEditActions())
    }

    @Test
    fun editLight() = snap("edit_light", dark = false) {
        SaveEditContent(editing(isNew = false, rpe = 4, title = "Sunday hill loop", dirty = true), SaveEditActions())
    }

    @Test
    fun editDark() = snap("edit_dark", dark = true) {
        SaveEditContent(editing(isNew = false, rpe = 9, title = "Sunday hill loop", dirty = true), SaveEditActions())
    }
}
