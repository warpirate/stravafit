// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.theme.Spacing

/** A third-party component and its licence. */
data class LicenseEntry(val name: String, val license: String)

/** Components shipped in the app, with their licences. Kept in step with `libs.versions.toml`. */
val OpenSourceLicenses: List<LicenseEntry> = listOf(
    LicenseEntry("OpenStreetMap data", "© OpenStreetMap contributors, Open Database License (ODbL) 1.0"),
    LicenseEntry("OpenFreeMap tiles and styles", "Free map hosting; styles based on OpenMapTiles (BSD-3-Clause, CC-BY 4.0)"),
    LicenseEntry("MapLibre Native for Android", "BSD 2-Clause License"),
    LicenseEntry("AndroidX, Jetpack Compose, Material 3", "Apache License 2.0"),
    LicenseEntry("Material Symbols icons", "Apache License 2.0"),
    LicenseEntry("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache License 2.0"),
    LicenseEntry("Dagger and Hilt", "Apache License 2.0"),
    LicenseEntry("Room and DataStore", "Apache License 2.0"),
    LicenseEntry("AndroidSVG", "Apache License 2.0"),
    LicenseEntry("Google Play services location (Play build only)", "Android Software Development Kit License"),
    LicenseEntry("Manrope and Barlow Semi Condensed fonts", "SIL Open Font License 1.1"),
    LicenseEntry("Route art, GPX/TCX, GeoJSON and FIT libraries (this project)", "Apache License 2.0"),
    LicenseEntry("This app", "GNU Affero General Public License v3.0 or later"),
)

/** Open-source licences (settings 1.6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onNavigateUp: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { RidgelineTopAppBar(title = "Open-source licences", onNavigateUp = onNavigateUp) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = Spacing.feedMaxWidth).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.s),
            ) {
                item {
                    SettingsGroup {
                        OpenSourceLicenses.forEachIndexed { index, entry ->
                            if (index > 0) RowDivider()
                            InfoRow(entry.name, entry.license)
                        }
                    }
                }
            }
        }
    }
}
