// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.activitydetail.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.export.file.ExportFormat
import org.openactivity.core.export.file.FormatUnavailableReason
import org.openactivity.feature.activitydetail.export.FileFormatOption

/** Asks before deleting; deleting is permanent and removes photos too. */
@Composable
internal fun DeleteActivityDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this activity?") },
        text = { Text("The activity, its route, splits and photos are removed from this device. This can’t be undone.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Picks a track-data format and whether to share the file or save it to the device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExportFileSheet(
    options: List<FileFormatOption>,
    exporting: Boolean,
    onExport: (ExportFormat, share: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Spacing.gutter, vertical = Spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Export file", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (exporting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            Text(
                "Your full recording, for other apps and devices. Use Share & export to trim the start and end for other people.",
                style = MaterialTheme.typography.bodyMedium,
                color = RidgelineTheme.colors.textSecondary,
                modifier = Modifier.padding(top = Spacing.xs, bottom = Spacing.m),
            )
            options.forEach { option ->
                val enabled = option.unavailable == null && !exporting
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(option.format.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            option.unavailable?.let(::reasonText) ?: formatHint(option.format),
                            style = RidgelineTheme.text.caption,
                            color = RidgelineTheme.colors.textMuted,
                        )
                    }
                    IconButton(onClick = { onExport(option.format, true) }, enabled = enabled) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share ${option.format.label} file")
                    }
                    IconButton(onClick = { onExport(option.format, false) }, enabled = enabled) {
                        Icon(Icons.Rounded.SaveAlt, contentDescription = "Save ${option.format.label} file to device")
                    }
                }
            }
        }
    }
}

private fun formatHint(format: ExportFormat): String = when (format) {
    ExportFormat.GPX -> "Route with times, elevation and heart rate"
    ExportFormat.TCX -> "Laps and sensor data for training apps"
    ExportFormat.FIT -> "Compact format for watches and bike computers"
    ExportFormat.GEOJSON -> "Route for maps and GIS tools"
    else -> format.extension
}

private fun reasonText(reason: FormatUnavailableReason): String = when (reason) {
    FormatUnavailableReason.NEEDS_ROUTE -> "Needs a GPS route"
    FormatUnavailableReason.NEEDS_TIMES -> "Needs timestamps on every sample"
    FormatUnavailableReason.ROUTE_HIDDEN -> "Route hidden by privacy settings"
    FormatUnavailableReason.NEEDS_ELEVATION -> "Needs elevation data"
}
