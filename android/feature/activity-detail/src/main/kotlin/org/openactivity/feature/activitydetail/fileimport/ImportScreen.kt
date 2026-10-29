// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.fileimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import org.openactivity.core.designsystem.component.PrimaryButton
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.fileimport.ImportDocumentsContract

/** "Import activity file": picks GPX, TCX or FIT files and stores them as activities. */
@Composable
fun ImportScreen(onBack: () -> Unit, onOpenActivity: (Long) -> Unit, viewModel: ImportViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ImportDocumentsContract()) { uris -> viewModel.import(uris) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ImportEffect.OpenActivity -> onOpenActivity(effect.activityId)
            }
        }
    }
    ImportContent(
        state = state,
        onBack = dropUnlessResumed(block = onBack),
        onPick = { picker.launch(ImportDocumentsContract.MIME_TYPES) },
        onOpenActivity = onOpenActivity,
    )
}

/** Stateless import screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportContent(state: ImportUiState, onBack: () -> Unit, onPick: () -> Unit, onOpenActivity: (Long) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RidgelineTopAppBar(title = "Import activity file", onNavigateUp = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.gutter, vertical = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    Text(
                        "Bring in activities from a watch, bike computer or another app. GPX, TCX and FIT files work, also when zipped as .gz.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "Files are read on this phone and stored only here. Activities you already have are skipped.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RidgelineTheme.colors.textSecondary,
                    )
                    PrimaryButton(
                        text = if (state.results.isEmpty()) "Choose files" else "Choose more files",
                        onClick = onPick,
                        enabled = !state.running,
                        loading = state.running,
                        leadingIcon = Icons.Rounded.FileOpen,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            if (state.results.isNotEmpty() && !state.running) {
                item {
                    Text(
                        summary(state),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = Spacing.l),
                    )
                }
            }
            itemsIndexed(state.results) { i, row ->
                if (i > 0) HorizontalDivider(color = RidgelineTheme.colors.hairline)
                ResultRow(row, onOpenActivity)
            }
        }
    }
}

@Composable
private fun ResultRow(row: ImportRow, onOpen: (Long) -> Unit) {
    val (icon, tint) = when (row.status) {
        ImportStatus.IMPORTED -> Icons.Rounded.CheckCircle to RidgelineTheme.colors.success
        ImportStatus.ALREADY_THERE -> Icons.Rounded.Info to RidgelineTheme.colors.info
        ImportStatus.FAILED -> Icons.Rounded.ErrorOutline to MaterialTheme.colorScheme.error
    }
    val clickable = row.activityId != null
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (clickable) Modifier.clickable { onOpen(row.activityId!!) } else Modifier)
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(horizontal = Spacing.m)) {
            Text(row.title ?: row.fileName, style = MaterialTheme.typography.bodyLarge)
            val detail = listOfNotNull(row.fileName.takeIf { row.title != null && it.isNotBlank() }, row.message).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary)
            }
        }
        if (clickable) Icon(Icons.Rounded.ChevronRight, contentDescription = "Open", tint = RidgelineTheme.colors.textMuted)
    }
}

private fun summary(state: ImportUiState): String {
    val imported = state.importedCount
    val already = state.results.count { it.status == ImportStatus.ALREADY_THERE }
    val failed = state.results.count { it.status == ImportStatus.FAILED }
    return listOfNotNull(
        "$imported imported",
        "$already already there".takeIf { already > 0 },
        "$failed failed".takeIf { failed > 0 },
    ).joinToString(" · ")
}
