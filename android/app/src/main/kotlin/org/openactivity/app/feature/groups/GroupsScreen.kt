// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.groups

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.component.RidgelineTopAppBar

/**
 * Groups tab. Clubs and challenges are shared between people, so they need a server account;
 * until sign-in exists the tab says so plainly instead of showing invented clubs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(onShowYourActivities: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { RidgelineTopAppBar(title = "Groups") },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            EmptyState(
                icon = Icons.Rounded.Groups,
                title = "Sign in to a server to join clubs",
                body = "Clubs, group challenges and club posts are shared with other people, so they need a server " +
                    "account. That is coming soon, free like everything else. Recording, routes and your stats work " +
                    "without one.",
                primaryActionLabel = "See your activities",
                onPrimaryAction = onShowYourActivities,
            )
        }
    }
}
