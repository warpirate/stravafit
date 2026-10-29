// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import org.openactivity.core.designsystem.theme.RidgelineTheme

/**
 * Ridgeline top app bar: left-aligned title on the canvas colour, tinting to `surface` when
 * content scrolls underneath.
 *
 * @param onNavigateUp shows a back arrow when not null.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RidgelineTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onNavigateUp: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        modifier = modifier,
        navigationIcon = {
            if (onNavigateUp != null) {
                IconButton(onClick = onNavigateUp) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Navigate up")
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = RidgelineTheme.colors.textSecondary,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

/**
 * Icon action for a top app bar, with an optional unread [badgeCount] (error fill, white numerals).
 * The count is included in the content description.
 */
@Composable
fun TopBarAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
) {
    val description = if (badgeCount > 0) "$contentDescription, $badgeCount unread" else contentDescription
    IconButton(onClick = onClick, modifier = modifier.semantics { this.contentDescription = description }) {
        if (badgeCount > 0) {
            BadgedBox(badge = { Badge { Text(if (badgeCount > MAX_BADGE) "$MAX_BADGE+" else "$badgeCount") } }) {
                Icon(icon, contentDescription = null)
            }
        } else {
            Icon(icon, contentDescription = null)
        }
    }
}

private const val MAX_BADGE = 99

@OptIn(ExperimentalMaterial3Api::class)
@PreviewLightDark
@Composable
private fun TopAppBarPreview() {
    RidgelineTheme {
        Column {
            RidgelineTopAppBar(title = "Feed") {
                TopBarAction(Icons.Rounded.Search, "Search", onClick = {})
                TopBarAction(Icons.Rounded.Notifications, "Notifications", onClick = {}, badgeCount = 3)
            }
            RidgelineTopAppBar(title = "Settings", onNavigateUp = {})
        }
    }
}
