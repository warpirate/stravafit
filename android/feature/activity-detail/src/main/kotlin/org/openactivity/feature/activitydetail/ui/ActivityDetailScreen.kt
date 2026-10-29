// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.ui

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
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.export.file.ExportFormat
import org.openactivity.core.export.file.ExportedFile
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
    var pendingDocument by remember { mutableStateOf<ExportedFile?>(null) }
    val createDocument = rememberLauncherForActivityResult(CreateDocumentForFile()) { uri ->
        val file = pendingDocument
        pendingDocument = null
        if (uri != null && file != null) viewModel.writeDocument(file, uri)
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                DetailEffect.Deleted -> navigation.onBack()
                is DetailEffect.Message -> snackbar.showSnackbar(effect.message)
                is DetailEffect.SaveDocument -> {
                    pendingDocument = effect.file
                    createDocument.launch(effect.file)
                }
                is DetailEffect.ShareFile -> if (!shareLauncher.share(context, effect.file)) viewModel.onShareUnavailable()
            }
        }
    }
    val id = (state as? ActivityDetailUiState.Content)?.activity?.id
    ActivityDetailContent(
        state = state,
        snackbar = snackbar,
        actions = DetailActions(
            onBack = navigation.onBack,
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

/** Stateless detail screen: top bar with overflow menu, the activity body, dialogs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailContent(
    state: ActivityDetailUiState,
    actions: DetailActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val content = state as? ActivityDetailUiState.Content
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            RidgelineTopAppBar(
                title = content?.activity?.sportName ?: "Activity",
                onNavigateUp = actions.onBack,
                actions = { if (content != null) DetailMenu(actions) },
            )
        },
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
        }
    }
}

@Composable
private fun DetailMenu(actions: DetailActions) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = actions.onShare) { Icon(Icons.Rounded.IosShare, contentDescription = "Share") }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More options") }
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
