// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.feature.shareexport.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing
import org.openactivity.core.export.art.ArtSpec
import org.openactivity.feature.shareexport.PreviewState
import org.openactivity.feature.shareexport.R

private val PreviewMaxHeight = 420.dp
private val CheckerCell = 12.dp

/**
 * The live preview, framed at the output's aspect ratio. Transparent images sit on a checkerboard so
 * the transparency is visible; reasons a template cannot draw this activity are shown in place.
 */
@Composable
internal fun PreviewPane(spec: ArtSpec, title: String, preview: PreviewState, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.share_export_preview_description, spec.template.label)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .heightIn(max = PreviewMaxHeight)
                .aspectRatio(spec.aspect.ratio, matchHeightConstraintsFirst = spec.aspect.ratio < 1f)
                .clip(MaterialTheme.shapes.large)
                .semantics { contentDescription = "$description: $title" },
            contentAlignment = Alignment.Center,
        ) {
            if (spec.transparent) Checkerboard() else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            when (preview) {
                is PreviewState.Ready -> PreviewImage(preview.bitmap, dimmed = false)
                is PreviewState.Rendering -> {
                    preview.previous?.let { PreviewImage(it, dimmed = true) }
                    LinearProgressIndicator(Modifier.align(Alignment.BottomCenter).fillMaxWidth())
                }
                is PreviewState.Unavailable -> PreviewMessage(stringResource(preview.reason.message))
                PreviewState.Failed -> PreviewMessage(stringResource(R.string.share_export_render_failed))
            }
        }
    }
}

@Composable
private fun PreviewImage(bitmap: android.graphics.Bitmap, dimmed: Boolean) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().alpha(if (dimmed) 0.6f else 1f),
    )
}

@Composable
private fun PreviewMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = RidgelineTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.widthIn(max = 320.dp).padding(Spacing.xxl),
    )
}

@Composable
private fun Checkerboard() {
    val light = MaterialTheme.colorScheme.surfaceVariant
    val dark = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxSize()) {
        drawRect(light)
        val cell = CheckerCell.toPx()
        val columns = (size.width / cell).toInt() + 1
        val rows = (size.height / cell).toInt() + 1
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                if ((row + column) % 2 == 0) continue
                drawRect(dark.copy(alpha = 0.6f), Offset(column * cell, row * cell), Size(cell, cell))
            }
        }
    }
}
