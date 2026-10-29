// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.shareexport.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.export.file.ExportFormat
import org.openactivity.core.export.file.FormatUnavailableReason
import org.openactivity.feature.shareexport.FileDestination
import org.openactivity.feature.shareexport.R

/**
 * Bottom sheet listing every export format. Each available format can be shared or saved to a location
 * the user picks; unavailable ones say what is missing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FormatSheet(
    formats: List<Pair<ExportFormat, FormatUnavailableReason?>>,
    privacyTrim: Boolean,
    enabled: Boolean,
    hiddenStartMeters: Int = 0,
    hiddenEndMeters: Int = 0,
    onExport: (ExportFormat, FileDestination) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = Spacing.l)) {
            Text(
                stringResource(R.string.share_export_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Spacing.l).semantics { heading() },
            )
            Text(
                if (privacyTrim) {
                    stringResource(R.string.share_export_sheet_privacy_on, hiddenStartMeters, hiddenEndMeters)
                } else {
                    stringResource(R.string.share_export_sheet_privacy_off)
                },
                style = MaterialTheme.typography.bodySmall,
                color = RidgelineTheme.colors.textSecondary,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
            )
            formats.forEachIndexed { index, (format, reason) ->
                if (index > 0) HorizontalDivider(Modifier.padding(start = Spacing.l))
                FormatRow(format, reason, enabled, onExport)
            }
        }
    }
}

@Composable
private fun FormatRow(
    format: ExportFormat,
    reason: FormatUnavailableReason?,
    enabled: Boolean,
    onExport: (ExportFormat, FileDestination) -> Unit,
) {
    val available = reason == null
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = Spacing.l, end = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = Spacing.s)) {
            Text(
                "${format.label}  ·  .${format.extension}",
                style = MaterialTheme.typography.bodyLarge,
                color = if (available) MaterialTheme.colorScheme.onSurface else RidgelineTheme.colors.textSecondary,
            )
            Text(
                stringResource(reason?.message ?: format.description),
                style = MaterialTheme.typography.bodySmall,
                color = RidgelineTheme.colors.textSecondary,
            )
        }
        IconButton(onClick = { onExport(format, FileDestination.SHARE) }, enabled = available && enabled) {
            Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.share_export_format_share, format.label))
        }
        IconButton(onClick = { onExport(format, FileDestination.SAVE_TO_DEVICE) }, enabled = available && enabled) {
            Icon(Icons.Rounded.SaveAlt, contentDescription = stringResource(R.string.share_export_format_save, format.label))
        }
    }
}
