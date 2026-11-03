// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.stravaimport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.ui.LocalMessenger
import org.openactivity.app.ui.common.NO_FILE_PICKER_MESSAGE
import org.openactivity.app.ui.common.launchOrElse
import org.openactivity.core.designsystem.component.FrostedScaffold
import org.openactivity.core.designsystem.component.OverlineHeader
import org.openactivity.core.designsystem.component.PrimaryButton
import org.openactivity.core.designsystem.component.RidgelineCard
import org.openactivity.core.designsystem.component.RidgelineTextButton
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.component.SecondaryButton
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import java.text.NumberFormat

/** Settings, "Import from Strava": the whole Strava archive, in the background. */
@Composable
fun StravaImportScreen(
    onNavigateUp: () -> Unit,
    onOpenActivities: () -> Unit,
    viewModel: StravaImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messenger = LocalMessenger.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::choose) }
    StravaImportContent(
        state = state,
        actions = StravaImportActions(
            onNavigateUp = onNavigateUp,
            onChoose = { picker.launchOrElse(StravaImportViewModel.MIME_TYPES) { messenger.show(NO_FILE_PICKER_MESSAGE) } },
            onStartPending = viewModel::startPending,
            onDismissPending = viewModel::dismissPending,
            onCancel = viewModel::cancel,
            onResume = viewModel::resume,
            onOpenActivities = onOpenActivities,
        ),
    )
}

/** Callbacks of [StravaImportContent]. */
data class StravaImportActions(
    val onNavigateUp: () -> Unit = {},
    val onChoose: () -> Unit = {},
    val onStartPending: () -> Unit = {},
    val onDismissPending: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onResume: () -> Unit = {},
    val onOpenActivities: () -> Unit = {},
)

/** Stateless Strava import screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StravaImportContent(state: StravaImportUiState, actions: StravaImportActions, modifier: Modifier = Modifier) {
    FrostedScaffold(
        modifier = modifier,
        topBar = { RidgelineTopAppBar(title = "Import from Strava", onNavigateUp = actions.onNavigateUp) },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = Spacing.feedMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = padding.calculateTopPadding())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.l),
            ) {
                val job = state.job
                when (state.mode) {
                    StravaImportUiState.Mode.INTRO -> Intro(actions)
                    StravaImportUiState.Mode.READY -> Ready(state.pending!!, actions)
                    StravaImportUiState.Mode.RUNNING -> Running(job!!, actions)
                    StravaImportUiState.Mode.CANCELLED -> Stopped(job!!, actions)
                    StravaImportUiState.Mode.FAILED -> Failed(job!!, actions)
                    StravaImportUiState.Mode.DONE -> Done(job!!, actions)
                }
            }
        }
    }
}

@Composable
private fun Intro(actions: StravaImportActions) {
    Text("Bring your whole Strava history", style = MaterialTheme.typography.headlineSmall)
    Text(
        "Every activity with its map, name, description, sport and photos. Activities without a GPS file, " +
            "like manual entries, come over with their time and distance.",
        style = MaterialTheme.typography.bodyLarge,
    )
    RidgelineCard {
        Text("Get your archive from Strava", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.padding(top = Spacing.m), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Step(1, "On strava.com, open Settings, then My Account.")
            Step(2, "Under \"Download or delete your account\", choose Get started, then Request your archive.")
            Step(3, "Strava emails you a link, usually within a few hours. Download the ZIP to this phone.")
            Step(4, "Choose that ZIP here. Leave it zipped.")
        }
    }
    PrimaryButton(text = "Choose Strava ZIP", onClick = actions.onChoose, leadingIcon = Icons.Rounded.FileOpen, modifier = Modifier.fillMaxWidth())
    Text(
        "The ZIP is read on this phone; nothing is uploaded. Activities you already have are skipped, so it is safe " +
            "to import again. A big archive takes a while: the import keeps going in the background.",
        style = MaterialTheme.typography.bodyMedium,
        color = RidgelineTheme.colors.textSecondary,
    )
}

@Composable
private fun Step(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            "$number",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(min = 24.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Ready(pending: PendingExport, actions: StravaImportActions) {
    Text("Import this Strava archive?", style = MaterialTheme.typography.headlineSmall)
    RidgelineCard {
        Text(pending.name, style = MaterialTheme.typography.titleMedium)
        Text(
            "StravaFit copies it in first, then adds every activity in it. Activities you already have are skipped.",
            style = MaterialTheme.typography.bodyMedium,
            color = RidgelineTheme.colors.textSecondary,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
    PrimaryButton(text = "Start import", onClick = actions.onStartPending, modifier = Modifier.fillMaxWidth())
    RidgelineTextButton(text = "Choose a different file", onClick = actions.onChoose, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Running(job: StravaImportJob, actions: StravaImportActions) {
    Text("Importing from Strava", style = MaterialTheme.typography.headlineSmall)
    RidgelineCard {
        Text(
            StravaImportText.progressLine(job),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text(job.sourceName, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary)
        val determinate = job.total > 0 && job.phase == StravaImportPhase.ACTIVITIES
        if (determinate) {
            LinearProgressIndicator(
                progress = { (job.done.toFloat() / job.total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.m),
            )
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = Spacing.m))
        }
        if (job.photos > 0) {
            Text(
                "${count(job.photos)} ${if (job.photos == 1) "photo" else "photos"} added",
                style = MaterialTheme.typography.bodySmall,
                color = RidgelineTheme.colors.textSecondary,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }
    }
    Text(
        "You can leave StravaFit. The import keeps going in the background and shows its progress in a notification. " +
            "If the phone stops it, it carries on later where it stopped.",
        style = MaterialTheme.typography.bodyMedium,
        color = RidgelineTheme.colors.textSecondary,
    )
    SecondaryButton(text = "Cancel import", onClick = actions.onCancel, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Stopped(job: StravaImportJob, actions: StravaImportActions) {
    Text("Import stopped", style = MaterialTheme.typography.headlineSmall)
    Text(
        if (job.total > 0) "${count(job.done)} of ${count(job.total)} activities were handled before you stopped it. " +
            "Activities already imported stay." else "Nothing was imported yet.",
        style = MaterialTheme.typography.bodyLarge,
    )
    PrimaryButton(text = "Continue import", onClick = actions.onResume, modifier = Modifier.fillMaxWidth())
    RidgelineTextButton(text = "Choose another ZIP", onClick = actions.onChoose, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Failed(job: StravaImportJob, actions: StravaImportActions) {
    Text("The import stopped", style = MaterialTheme.typography.headlineSmall)
    RidgelineCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
            Text(
                job.error ?: "The ZIP could not be read.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = Spacing.m),
            )
        }
        if (job.done > 0) {
            Text(
                "${count(job.done)} activities were handled before it stopped; they stay.",
                style = MaterialTheme.typography.bodyMedium,
                color = RidgelineTheme.colors.textSecondary,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }
    }
    PrimaryButton(text = "Try again", onClick = actions.onResume, modifier = Modifier.fillMaxWidth())
    RidgelineTextButton(text = "Choose another ZIP", onClick = actions.onChoose, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Done(job: StravaImportJob, actions: StravaImportActions) {
    Text("Strava import finished", style = MaterialTheme.typography.headlineSmall)
    RidgelineCard(contentPadding = Spacing.none) {
        SummaryRow(Icons.Rounded.CheckCircle, RidgelineTheme.colors.success, count(job.imported), if (job.imported == 1) "activity imported" else "activities imported")
        HorizontalDivider(Modifier.padding(start = Spacing.l), color = RidgelineTheme.colors.hairline)
        SummaryRow(Icons.Rounded.Info, RidgelineTheme.colors.info, count(job.duplicates), "skipped, already on this phone")
        if (job.photos > 0) {
            HorizontalDivider(Modifier.padding(start = Spacing.l), color = RidgelineTheme.colors.hairline)
            SummaryRow(Icons.Rounded.PhotoLibrary, RidgelineTheme.colors.textSecondary, count(job.photos), if (job.photos == 1) "photo added" else "photos added")
        }
        if (job.failed > 0) {
            HorizontalDivider(Modifier.padding(start = Spacing.l), color = RidgelineTheme.colors.hairline)
            SummaryRow(Icons.Rounded.ErrorOutline, MaterialTheme.colorScheme.error, count(job.failed), "couldn't be imported")
        }
    }
    if (job.imported > 0) {
        PrimaryButton(text = "See your activities", onClick = actions.onOpenActivities, modifier = Modifier.fillMaxWidth())
    }
    if (job.failures.isNotEmpty()) {
        OverlineHeader("Not imported")
        RidgelineCard(contentPadding = Spacing.none) {
            job.failures.forEachIndexed { i, failure ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = Spacing.l), color = RidgelineTheme.colors.hairline)
                Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m)) {
                    Text(failure.name, style = MaterialTheme.typography.bodyLarge)
                    Text(failure.reason, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textSecondary)
                }
            }
            if (job.failed > job.failures.size) {
                Text(
                    "and ${count(job.failed - job.failures.size)} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = RidgelineTheme.colors.textMuted,
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
                )
            }
        }
    }
    RidgelineTextButton(text = "Import another archive", onClick = actions.onChoose, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun SummaryRow(icon: ImageVector, tint: Color, value: String, label: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.listRowMin).padding(horizontal = Spacing.l, vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Spacing.m))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.s))
    }
}

private fun count(n: Int): String = NumberFormat.getIntegerInstance().format(n)
