// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

private val ButtonHeight = 48.dp
private val TextButtonHeight = 40.dp
private val ButtonIconSize = 18.dp
private val SpinnerSize = 20.dp
private val ButtonPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)

/**
 * Filled primary button. Use at most one per screen for the single most important action.
 *
 * @param loading swaps the label for a spinner while keeping the button width and announces "Loading".
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = ButtonHeight)
            .loadingSemantics(loading),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.medium,
        contentPadding = ButtonPadding,
    ) {
        ButtonContent(text = text, loading = loading, leadingIcon = leadingIcon)
    }
}

/** Tonal secondary button (`primaryContainer` fill) for secondary emphasis. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = ButtonHeight)
            .loadingSemantics(loading),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.medium,
        contentPadding = ButtonPadding,
    ) {
        ButtonContent(text = text, loading = loading, leadingIcon = leadingIcon)
    }
}

/** Text button for inline actions such as "See all". Keeps a 48 dp touch target. */
@Composable
fun RidgelineTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = TextButtonHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.s),
    ) {
        ButtonContent(text = text, loading = false, leadingIcon = leadingIcon)
    }
}

@Composable
private fun RowScope.ButtonContent(text: String, loading: Boolean, leadingIcon: ImageVector?) {
    if (loading) {
        CircularProgressIndicator(
            modifier = Modifier.size(SpinnerSize),
            color = LocalContentColor.current,
            strokeWidth = 2.dp,
        )
        return
    }
    if (leadingIcon != null) {
        Icon(leadingIcon, contentDescription = null, modifier = Modifier.size(ButtonIconSize))
        Spacer(Modifier.width(Spacing.s))
    }
    Text(text = text, style = MaterialTheme.typography.labelLarge)
}

private fun Modifier.loadingSemantics(loading: Boolean): Modifier =
    if (loading) semantics { stateDescription = "Loading" } else this

@PreviewLightDark
@Composable
private fun ButtonsPreview() {
    RidgelineTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                PrimaryButton(text = "Find friends", onClick = {}, leadingIcon = Icons.Rounded.PersonAdd)
                PrimaryButton(text = "Saving", onClick = {}, loading = true)
                SecondaryButton(text = "Record activity", onClick = {}, leadingIcon = Icons.Rounded.Add)
                PrimaryButton(text = "Disabled", onClick = {}, enabled = false)
                RidgelineTextButton(text = "See all", onClick = {})
            }
        }
    }
}
