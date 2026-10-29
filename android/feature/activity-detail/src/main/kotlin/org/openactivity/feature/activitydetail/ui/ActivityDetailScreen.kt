// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.export.file.ExportFormat
import org.openactivity.core.export.share.ShareLauncher
import org.openactivity.feature.activitydetail.ActivityDetailNavigation
import org.openactivity.feature.activitydetail.ActivityDetailUiState
import org.openactivity.feature.activitydetail.ActivityDetailViewModel
import org.openactivity.feature.activitydetail.DetailEffect

/** Actions of the stateless detail screen. */
data class DetailActions(
    val onBack: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onExportFile: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
    val onExport: (ExportFormat, Boolean) -> Unit = { _, _ -> },
    val onDismissExport: () -> Unit = {},
)

/** The activity detail screen wired to its [ActivityDetailViewModel]. */
@Composable
fun ActivityDetailScreen(
    navigation: ActivityDetailNavigation,
    viewModel: ActivityDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val shareLauncher = remember(context) { ShareLauncher(context.applicationContext) }
    val createDocument = rememberLauncherForActivityResult(CreateDocumentForFile(), viewModel::onDocumentCreated)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                DetailEffect.Deleted -> navigation.onBack()
                is DetailEffect.Message -> snackbar.showSnackbar(effect.message)
                is DetailEffect.SaveDocument -> createDocument.launch(effect.file)
                is DetailEffect.ShareFile -> if (!shareLauncher.share(context, effect.file)) viewModel.onShareUnavailable()
            }
        }
    }
    val id = (state as? ActivityDetailUiState.Content)?.activity?.id
    // A double tap on Up during the exit transition must not pop the screen below as well.
    val onBack = dropUnlessResumed(block = navigation.onBack)
    ActivityDetailContent(
        state = state,
        snackbar = snackbar,
        actions = DetailActions(
            onBack = onBack,
            onEdit = { id?.let(navigation.onEdit) },
            onShare = { id?.let(navigation.onShare) },
            onExportFile = viewModel::openExportSheet,
            onDelete = viewModel::requestDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onDismissDelete = viewModel::cancelDelete,
            onExport = viewModel::exportFile,
            onDismissExport = viewModel::closeExportSheet,
        ),
    )
}

/**
 * Stateless detail screen (strava direction): a full-bleed hero map with floating round back, share and
 * more buttons, the activity body, and a bottom bar with high-five and comment counts and share.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailContent(
    state: ActivityDetailUiState,
    actions: DetailActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val content = state as? ActivityDetailUiState.Content
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (content != null) SocialBar(actions.onShare) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                ActivityDetailUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                ActivityDetailUiState.NotFound -> EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "Activity not found",
                    body = "It may have been deleted.",
                    primaryActionLabel = "Go back",
                    onPrimaryAction = actions.onBack,
                    modifier = Modifier.align(Alignment.Center),
                )
                is ActivityDetailUiState.Content -> {
                    DetailBody(state.activity)
                    if (state.dialogs.confirmDelete) DeleteActivityDialog(actions.onConfirmDelete, actions.onDismissDelete)
                    state.dialogs.exportFormats?.let { options ->
                        ExportFileSheet(options, state.dialogs.exporting, actions.onExport, actions.onDismissExport)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FloatingRound(Icons.AutoMirrored.Rounded.ArrowBack, "Navigate up", actions.onBack)
                Spacer(Modifier.weight(1f))
                if (content != null) DetailMenu(actions)
            }
        }
    }
}

/** 44 dp round button floating over the hero map. */
@Composable
private fun FloatingRound(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = RidgelineTheme.colors.float,
        contentColor = RidgelineTheme.colors.onFloat,
        shadowElevation = 3.dp,
        modifier = Modifier.size(44.dp).semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null) }
    }
}

/** High-five count, comment count and share. Counts stay 0 until activities are shared to a server. */
@Composable
private fun SocialBar(onShare: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding()) {
            HorizontalDivider(color = RidgelineTheme.colors.hairline)
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PanTool, contentDescription = "High-fives", modifier = Modifier.size(22.dp))
                    Text("0", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = Spacing.s))
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Outlined.Comment, contentDescription = "Comments", modifier = Modifier.size(22.dp))
                    Text("0", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = Spacing.s))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IconButton(onClick = onShare) { Icon(Icons.Outlined.Share, contentDescription = "Share") }
                }
            }
        }
    }
}

@Composable
private fun DetailMenu(actions: DetailActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    FloatingRound(Icons.Outlined.Share, "Share", actions.onShare)
    Spacer(Modifier.width(Spacing.m))
    Box {
        FloatingRound(Icons.Rounded.MoreHoriz, "More options") { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuItem("Edit activity", Icons.Rounded.Edit) { open = false; actions.onEdit() }
            MenuItem("Share & export", Icons.Rounded.IosShare) { open = false; actions.onShare() }
            MenuItem("Export file", Icons.Rounded.FileDownload) { open = false; actions.onExportFile() }
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { open = false; actions.onDelete() },
            )
        }
    }
}

@Composable
private fun MenuItem(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = onClick)
}
