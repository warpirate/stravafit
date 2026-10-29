// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.shareexport.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.openactivity.core.designsystem.component.EmptyState
import org.openactivity.core.designsystem.component.OverlineHeader
import org.openactivity.core.designsystem.component.PrimaryButton
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.component.SecondaryButton
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.export.art.ArtAspect
import org.openactivity.core.export.art.ArtColorBy
import org.openactivity.core.export.art.ArtTemplate
import org.openactivity.core.export.art.ArtTheme
import org.openactivity.core.export.file.ExportFormat
import org.openactivity.core.export.file.ExportedFile
import org.openactivity.core.export.share.ShareLauncher
import org.openactivity.feature.shareexport.FileDestination
import org.openactivity.feature.shareexport.R
import org.openactivity.feature.shareexport.ShareAction
import org.openactivity.feature.shareexport.ShareEffect
import org.openactivity.feature.shareexport.ShareExportUiState
import org.openactivity.feature.shareexport.ShareExportViewModel
import java.io.File

/** User actions of the stateless [ShareExportScreen]. */
internal data class ShareExportActions(
    val onClose: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onTemplate: (ArtTemplate) -> Unit = {},
    val onAspect: (ArtAspect) -> Unit = {},
    val onTheme: (ArtTheme) -> Unit = {},
    val onColorBy: (ArtColorBy) -> Unit = {},
    val onPrivacyTrim: (Boolean) -> Unit = {},
    val onTransparent: (Boolean) -> Unit = {},
    val onShare: () -> Unit = {},
    val onStory: () -> Unit = {},
    val onSaveImage: () -> Unit = {},
    val onFormatSheet: (Boolean) -> Unit = {},
    val onExport: (ExportFormat, FileDestination) -> Unit = { _, _ -> },
)

/**
 * Stateful share screen: collects [viewModel] state and carries out its effects with the Activity
 * context (share sheet, Instagram, SAF save dialog, legacy storage permission, snackbars).
 */
@Composable
public fun ShareExportScreenHost(viewModel: ShareExportViewModel, onClose: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = remember(context) { ShareLauncher(context.applicationContext) }
    val snackbar = remember { SnackbarHostState() }
    // The save dialog is another Activity: this one may be recreated (rotation, process death) before
    // the result arrives, so the file waiting for its destination is kept in saved state.
    var pendingDocument by rememberSaveable(stateSaver = PendingFileSaver) { mutableStateOf<ExportedFile?>(null) }

    val createDocument = rememberLauncherForActivityResult(CreateDocumentForFile()) { uri ->
        val file = pendingDocument
        pendingDocument = null
        if (uri != null && file != null) viewModel.writeDocument(file, uri)
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onStoragePermissionResult(granted)
    }
    val chooserTitle = stringResource(R.string.share_export_chooser)

    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel, lifecycleOwner) {
        // Only while started: activities launched from the background are blocked (Android 10+), so an
        // effect that arrives while the user is elsewhere waits in the channel until they come back.
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is ShareEffect.ShareFile ->
                        if (!launcher.share(context, effect.file, chooserTitle)) viewModel.onShareFailed(story = false)
                    is ShareEffect.ShareStory -> {
                        val a = effect.assets
                        val sent = launcher.shareToInstagramStory(context, a.sticker, a.storyImage, a.topColor, a.bottomColor)
                        if (!sent && !launcher.share(context, a.storyImage, chooserTitle)) viewModel.onShareFailed(story = true)
                    }
                    is ShareEffect.SaveDocument -> {
                        pendingDocument = effect.file
                        createDocument.launch(effect.file)
                    }
                    ShareEffect.RequestStoragePermission -> storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    // showSnackbar suspends until the message is dismissed; run it aside so later effects
                    // (a share started right after "Saved") are not held back for seconds.
                    is ShareEffect.Message -> {
                        val text = context.getString(effect.text, *effect.args.toTypedArray())
                        scope.launch { snackbar.showSnackbar(text) }
                    }
                }
            }
        }
    }

    ShareExportScreen(
        state = state,
        snackbarHostState = snackbar,
        actions = ShareExportActions(
            onClose = onClose,
            onRetry = viewModel::load,
            onTemplate = viewModel::selectTemplate,
            onAspect = viewModel::selectAspect,
            onTheme = viewModel::selectTheme,
            onColorBy = viewModel::selectColorBy,
            onPrivacyTrim = viewModel::setPrivacyTrim,
            onTransparent = viewModel::setTransparent,
            onShare = viewModel::share,
            onStory = viewModel::shareToStory,
            onSaveImage = viewModel::saveImage,
            onFormatSheet = viewModel::setFormatSheetOpen,
            onExport = viewModel::exportFile,
        ),
    )
}

/** Stateless share screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareExportScreen(
    state: ShareExportUiState,
    actions: ShareExportActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = { RidgelineTopAppBar(title = stringResource(R.string.share_export_title), onNavigateUp = actions.onClose) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                ShareExportUiState.Loading -> {
                    val label = stringResource(R.string.share_export_loading)
                    CircularProgressIndicator(Modifier.align(Alignment.Center).semantics { contentDescription = label })
                }
                ShareExportUiState.NotFound -> EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = stringResource(R.string.share_export_not_found_title),
                    body = stringResource(R.string.share_export_not_found_body),
                    modifier = Modifier.align(Alignment.Center),
                )
                is ShareExportUiState.LoadFailed -> EmptyState(
                    icon = Icons.Rounded.ErrorOutline,
                    title = stringResource(R.string.share_export_load_failed_title),
                    body = state.message,
                    primaryActionLabel = stringResource(R.string.share_export_retry),
                    onPrimaryAction = actions.onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )
                is ShareExportUiState.Content -> Content(state, actions)
            }
        }
    }
}

@Composable
private fun Content(state: ShareExportUiState.Content, actions: ShareExportActions) {
    val spec = state.spec
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PreviewPane(
            spec = spec,
            title = state.title,
            preview = state.preview,
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
        )
        ActionButtons(state, actions)

        OverlineHeader(stringResource(R.string.share_export_templates), Modifier.padding(horizontal = Spacing.l))
        TemplateCarousel(selected = spec.template, onSelect = actions.onTemplate)

        if (spec.template.aspects.size > 1) {
            OverlineHeader(stringResource(R.string.share_export_aspect), Modifier.padding(horizontal = Spacing.l))
            AspectChips(spec.template.aspects, spec.aspect, actions.onAspect, Modifier.padding(horizontal = Spacing.l))
        }

        OverlineHeader(stringResource(R.string.share_export_theme), Modifier.padding(horizontal = Spacing.l))
        ThemePicker(spec.theme, actions.onTheme, Modifier.padding(horizontal = Spacing.l))

        if (spec.template.supportsColorBy) {
            OverlineHeader(stringResource(R.string.share_export_color_by), Modifier.padding(horizontal = Spacing.l))
            ColorByChips(
                options = state.colorByOptions,
                selected = spec.colorBy,
                enabled = true,
                onSelect = actions.onColorBy,
                modifier = Modifier.padding(horizontal = Spacing.l),
            )
        }

        OverlineHeader(stringResource(R.string.share_export_options), Modifier.padding(horizontal = Spacing.l))
        SwitchRow(
            title = stringResource(R.string.share_export_privacy_trim),
            body = stringResource(
                if (spec.privacyTrim) R.string.share_export_privacy_trim_body else R.string.share_export_privacy_off_warning,
            ),
            checked = spec.privacyTrim,
            onCheckedChange = actions.onPrivacyTrim,
        )
        if (spec.template != ArtTemplate.POSTER && spec.template != ArtTemplate.ELEVATION) {
            SwitchRow(
                title = stringResource(R.string.share_export_transparent),
                body = stringResource(R.string.share_export_transparent_body),
                checked = spec.transparent,
                onCheckedChange = actions.onTransparent,
            )
        }
        Box(Modifier.padding(bottom = Spacing.x3))
    }

    if (state.formatSheetOpen) {
        FormatSheet(
            formats = state.formats,
            privacyTrim = spec.privacyTrim,
            enabled = state.busy == null,
            onExport = actions.onExport,
            onDismiss = { actions.onFormatSheet(false) },
        )
    }
}

@Composable
private fun ActionButtons(state: ShareExportUiState.Content, actions: ShareExportActions) {
    val idle = state.busy == null
    val image = state.imageAvailable
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        PrimaryButton(
            text = stringResource(R.string.share_export_share),
            onClick = actions.onShare,
            enabled = idle && image,
            loading = state.busy == ShareAction.SHARE,
            leadingIcon = Icons.Rounded.Share,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            if (state.instagramAvailable) {
                SecondaryButton(
                    text = stringResource(R.string.share_export_story),
                    onClick = actions.onStory,
                    enabled = idle && image,
                    loading = state.busy == ShareAction.STORY,
                    leadingIcon = Icons.Rounded.CameraAlt,
                    modifier = Modifier.weight(1f),
                )
            }
            SecondaryButton(
                text = stringResource(R.string.share_export_save_image),
                onClick = actions.onSaveImage,
                enabled = idle && image,
                loading = state.busy == ShareAction.SAVE_IMAGE,
                leadingIcon = Icons.Rounded.Download,
                modifier = Modifier.weight(1f),
            )
        }
        SecondaryButton(
            text = stringResource(R.string.share_export_export_file),
            onClick = { actions.onFormatSheet(true) },
            enabled = idle,
            loading = state.busy == ShareAction.EXPORT_FILE,
            leadingIcon = Icons.AutoMirrored.Rounded.InsertDriveFile,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Saves an [ExportedFile] as plain strings so it survives Activity recreation. */
private val PendingFileSaver: Saver<ExportedFile?, Any> = Saver(
    save = { file -> file?.let { arrayListOf(it.file.path, it.uri.toString(), it.displayName, it.mimeType) } ?: arrayListOf<String>() },
    restore = { saved ->
        @Suppress("UNCHECKED_CAST")
        val parts = saved as List<String>
        if (parts.size == 4) ExportedFile(File(parts[0]), Uri.parse(parts[1]), parts[2], parts[3]) else null
    },
)
