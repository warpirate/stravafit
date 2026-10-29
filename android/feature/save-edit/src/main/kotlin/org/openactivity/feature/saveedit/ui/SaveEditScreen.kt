// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.saveedit.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.core.activityextras.ActivityExtras
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.feature.saveedit.SaveEditDialog
import org.openactivity.feature.saveedit.SaveEditEffect
import org.openactivity.feature.saveedit.SaveEditNavigation
import org.openactivity.feature.saveedit.SaveEditUiState
import org.openactivity.feature.saveedit.SaveEditViewModel
import org.openactivity.model.SportType
import org.openactivity.model.Visibility

/** Callbacks of the stateless save / edit screen. */
data class SaveEditActions(
    val onBack: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onTitle: (String) -> Unit = {},
    val onDescription: (String) -> Unit = {},
    val onOpenSportPicker: () -> Unit = {},
    val onCloseSportPicker: () -> Unit = {},
    val onSport: (SportType) -> Unit = {},
    val onVisibility: (Visibility) -> Unit = {},
    val onExertion: (Int?) -> Unit = {},
    val onMuted: (Boolean) -> Unit = {},
    val onAddPhotos: () -> Unit = {},
    val onRemovePhoto: (String) -> Unit = {},
    val onDiscard: () -> Unit = {},
    val onConfirmDialog: () -> Unit = {},
    val onDismissDialog: () -> Unit = {},
)

/** The save / edit screen wired to its [SaveEditViewModel]. */
@Composable
fun SaveEditScreen(navigation: SaveEditNavigation, viewModel: SaveEditViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ActivityExtras.MAX_PHOTOS),
    ) { uris -> viewModel.addPhotos(uris) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SaveEditEffect.Saved -> navigation.onSaved(effect.activityId, effect.isNew)
                SaveEditEffect.Discarded -> navigation.onDiscarded()
                SaveEditEffect.Close -> navigation.onClose()
                is SaveEditEffect.Message -> snackbar.showSnackbar(effect.message)
            }
        }
    }
    BackHandler(enabled = state is SaveEditUiState.Editing) { viewModel.onBack() }
    SaveEditContent(
        state = state,
        snackbar = snackbar,
        actions = SaveEditActions(
            onBack = viewModel::onBack,
            onSave = viewModel::save,
            onTitle = viewModel::setTitle,
            onDescription = viewModel::setDescription,
            onOpenSportPicker = { viewModel.setSportPickerOpen(true) },
            onCloseSportPicker = { viewModel.setSportPickerOpen(false) },
            onSport = viewModel::setSport,
            onVisibility = viewModel::setVisibility,
            onExertion = viewModel::setPerceivedExertion,
            onMuted = viewModel::setMuted,
            onAddPhotos = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemovePhoto = viewModel::removePhoto,
            onDiscard = viewModel::requestDiscard,
            onConfirmDialog = viewModel::confirmDialog,
            onDismissDialog = viewModel::dismissDialog,
        ),
    )
}

/** Stateless save / edit screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveEditContent(
    state: SaveEditUiState,
    actions: SaveEditActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    val editing = state as? SaveEditUiState.Editing
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (editing?.isNew != false) "Save activity" else "Edit activity", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        if (editing?.isNew == true) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close")
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Navigate up")
                        }
                    }
                },
                actions = {
                    if (editing != null) {
                        TextButton(onClick = actions.onSave, enabled = !editing.saving && (editing.isNew || editing.dirty)) {
                            Text(if (editing.isNew) "Save" else "Update", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                SaveEditUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                SaveEditUiState.NotFound -> EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "Activity not found",
                    body = "It may have been deleted.",
                    primaryActionLabel = "Go back",
                    onPrimaryAction = actions.onBack,
                    modifier = Modifier.align(Alignment.Center),
                )
                is SaveEditUiState.Editing -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .imePadding()
                            .navigationBarsPadding()
                            .padding(horizontal = Spacing.gutter, vertical = Spacing.s),
                        verticalArrangement = Arrangement.spacedBy(Spacing.l),
                    ) {
                        SaveEditForm(state, actions)
                    }
                    if (state.sportPickerOpen) SportPickerSheet(state.form.sport, actions.onSport, actions.onCloseSportPicker)
                    state.dialog?.let { ConfirmDialog(it, actions.onConfirmDialog, actions.onDismissDialog) }
                    if (state.saving) CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(dialog: SaveEditDialog, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val (title, body, confirm) = when (dialog) {
        SaveEditDialog.DISCARD_ACTIVITY -> Triple(
            "Discard this activity?",
            "The recording is deleted from this device for good, with its route and photos.",
            "Discard",
        )
        SaveEditDialog.DISCARD_CHANGES -> Triple("Discard changes?", "Your edits haven’t been saved.", "Discard")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (dialog == SaveEditDialog.DISCARD_CHANGES) "Keep editing" else "Cancel") } },
        modifier = Modifier.fillMaxWidth(),
    )
}
