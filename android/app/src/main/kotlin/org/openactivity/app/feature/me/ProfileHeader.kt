// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.me

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import org.openactivity.app.ui.common.ProfileAvatar
import org.openactivity.core.designsystem.component.AvatarSize
import org.openactivity.core.designsystem.component.SecondaryButton
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

/**
 * Avatar, name, location, an all-time summary and Edit profile (profile-progress P1). Follower
 * counts appear once the profile lives on a server.
 */
@Composable
fun ProfileHeader(header: ProfileHeaderUi, onEditProfile: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(header.name, header.photoPath, size = AvatarSize.l + Spacing.s)
            Spacer(Modifier.width(Spacing.l))
            Column(Modifier.weight(1f)) {
                Text(header.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (header.location.isNotBlank()) {
                    Text(header.location, style = MaterialTheme.typography.bodyMedium, color = RidgelineTheme.colors.textSecondary)
                }
                Spacer(Modifier.height(Spacing.xxs))
                Text(header.summary, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textMuted)
            }
        }
        Spacer(Modifier.height(Spacing.l))
        SecondaryButton("Edit profile", onClick = onEditProfile, modifier = Modifier.fillMaxWidth())
    }
}
