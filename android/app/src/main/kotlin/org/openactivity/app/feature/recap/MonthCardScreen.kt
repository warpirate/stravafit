// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.recap

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openactivity.app.ui.LocalMessenger
import org.openactivity.core.designsystem.component.FrostedScaffold
import org.openactivity.core.designsystem.component.PrimaryButton
import org.openactivity.core.designsystem.component.RidgelineFilterChip
import org.openactivity.core.designsystem.component.RidgelineTopAppBar
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.export.share.ShareLauncher
import routeexport.CardStyle
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "Share this month", connected to [MonthCardViewModel]. */
@Composable
fun MonthCardScreen(onNavigateUp: () -> Unit, viewModel: MonthCardViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val messenger = LocalMessenger.current
    val launcher = remember(context) { ShareLauncher(context.applicationContext) }
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is RecapEffect.Share -> if (!launcher.share(context, effect.file)) messenger.show("No app on this phone can take an image.")
                is RecapEffect.Message -> messenger.show(effect.text)
            }
        }
    }
    MonthCardContent(
        state,
        MonthCardActions(
            onNavigateUp = onNavigateUp,
            onPrevious = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
            onStyle = viewModel::selectStyle,
            onShare = viewModel::share,
        ),
    )
}

/** Callbacks for [MonthCardContent]. */
data class MonthCardActions(
    val onNavigateUp: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onStyle: (CardStyle) -> Unit = {},
    val onShare: () -> Unit = {},
)

/** Stateless monthly card screen: month stepper, the live preview, Light / Dark / Sticker and Share. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthCardContent(state: MonthCardUiState, actions: MonthCardActions, modifier: Modifier = Modifier) {
    val monthName = state.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    FrostedScaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RidgelineTopAppBar(title = "Share this month", onNavigateUp = actions.onNavigateUp) },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier
                    .widthIn(max = Spacing.feedMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = padding.calculateTopPadding())
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.m),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = actions.onPrevious) { Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous month") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(monthName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Text(
                            when (state.activityCount) {
                                0 -> "No activities"
                                1 -> "1 activity"
                                else -> "${state.activityCount} activities"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = RidgelineTheme.colors.textMuted,
                        )
                    }
                    IconButton(onClick = actions.onNext, enabled = state.canGoNext) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Next month")
                    }
                }
                Spacer(Modifier.height(Spacing.m))
                Preview(state, monthName)
                Spacer(Modifier.height(Spacing.l))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    CardStyle.entries.forEach { style ->
                        RidgelineFilterChip(style.label, selected = style == state.style, onClick = { actions.onStyle(style) })
                    }
                }
                if (state.style == CardStyle.STICKER) {
                    Spacer(Modifier.height(Spacing.s))
                    Text(
                        "Transparent PNG, to lay over a photo or a story",
                        style = MaterialTheme.typography.bodySmall,
                        color = RidgelineTheme.colors.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }
                PrimaryButton(
                    text = "Share",
                    onClick = actions.onShare,
                    loading = state.isSharing,
                    enabled = state.preview != null,
                    leadingIcon = Icons.Rounded.IosShare,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.l),
                )
            }
        }
    }
}

@Composable
private fun Preview(state: MonthCardUiState, monthName: String) {
    val shape = RoundedCornerShape(12.dp)
    // A sticker has no background: show it over a dark photo-like backdrop, as it will be used.
    val backdrop = if (state.style == CardStyle.STICKER) {
        Modifier.background(Brush.verticalGradient(listOf(Color(0xFF3A4A55), Color(0xFF1B2326))), shape)
    } else {
        Modifier
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(PeriodCardRenderer.WIDTH.toFloat() / PeriodCardRenderer.HEIGHT)
            .clip(shape)
            .then(backdrop)
            .border(1.dp, RidgelineTheme.colors.hairline, shape),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = state.preview
        if (bitmap == null) {
            CircularProgressIndicator(color = RidgelineTheme.colors.accent)
        } else {
            Image(bitmap.asImageBitmap(), contentDescription = "$monthName card preview", modifier = Modifier.fillMaxSize())
        }
    }
}

private val CardStyle.label: String
    get() = when (this) {
        CardStyle.LIGHT -> "Light"
        CardStyle.DARK -> "Dark"
        CardStyle.STICKER -> "Sticker"
    }
