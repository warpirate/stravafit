// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.me

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import org.openactivity.core.designsystem.component.Avatar
import org.openactivity.core.designsystem.component.AvatarSize
import org.openactivity.core.designsystem.component.SecondaryButton
import org.openactivity.core.designsystem.theme.RidgelineTheme
import org.openactivity.core.designsystem.theme.Spacing

/** Avatar, name, location, follow counts and Edit profile (profile-progress P1). */
@Composable
fun ProfileHeader(
    header: ProfileHeaderUi,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(header.name, size = AvatarSize.l + Spacing.s)
            Spacer(Modifier.width(Spacing.l))
            Column(Modifier.weight(1f)) {
                Text(header.name, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(header.location, style = MaterialTheme.typography.bodyMedium, color = RidgelineTheme.colors.textSecondary)
                Spacer(Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
                    FollowCount(header.following, "Following", onOpenFollowing)
                    FollowCount(header.followers, "Followers", onOpenFollowers)
                }
            }
        }
        Spacer(Modifier.height(Spacing.l))
        SecondaryButton("Edit profile", onClick = onEditProfile, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun FollowCount(value: String, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs)
            .clearAndSetSemantics {
                contentDescription = "$value $label"
                role = Role.Button
            },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(value, style = RidgelineTheme.text.statS, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(Spacing.xs))
        Text(label, style = MaterialTheme.typography.bodySmall, color = RidgelineTheme.colors.textMuted)
    }
}
