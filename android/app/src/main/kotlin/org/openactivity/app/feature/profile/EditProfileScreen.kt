// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.ui.LocalMessenger
import org.openactivity.app.ui.common.ProfileAvatar
import org.openactivity.core.designsystem.component.AvatarSize
import org.openactivity.core.designsystem.component.PrimaryButton
import org.openactivity.core.designsystem.component.RidgelineTextButton
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

/** Edit profile, connected to [EditProfileViewModel]. Closes itself after saving. */
@Composable
fun EditProfileScreen(onNavigateUp: () -> Unit, viewModel: EditProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::setPhoto)
    }
    LaunchedEffect(state.saved) { if (state.saved) onNavigateUp() }
    LaunchedEffect(state.error) {
        state.error?.let {
            messenger.show(it)
            viewModel.errorShown()
        }
    }
    EditProfileContent(
        state = state,
        onNavigateUp = onNavigateUp,
        onNameChange = viewModel::setName,
        onLocationChange = viewModel::setLocation,
        onPickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onRemovePhoto = viewModel::removePhoto,
        onSave = viewModel::save,
    )
}

/** Stateless Edit profile: photo, name and location, and Save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileContent(
    state: EditProfileUiState,
    onNavigateUp: () -> Unit,
    onNameChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { RidgelineTopAppBar(title = "Edit profile", onNavigateUp = onNavigateUp) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = Spacing.feedMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.l),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProfileAvatar(state.avatarName, state.photoPath, size = AvatarSize.xl)
                Spacer(Modifier.height(Spacing.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    RidgelineTextButton(if (state.photoPath == null) "Add photo" else "Change photo", onClick = onPickPhoto)
                    if (state.photoPath != null) RidgelineTextButton("Remove", onClick = onRemovePhoto)
                }
                Spacer(Modifier.height(Spacing.l))
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.m))
                OutlinedTextField(
                    value = state.location,
                    onValueChange = onLocationChange,
                    label = { Text("Location (optional)") },
                    placeholder = { Text("City or region") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.s))
                Text(
                    "Your profile stays on this device. It is shown on your activities and is only shared if you sign in to a server later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RidgelineTheme.colors.textMuted,
                )
                Spacer(Modifier.height(Spacing.xxl))
                PrimaryButton("Save", onClick = onSave, enabled = state.canSave, loading = state.isSaving, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
