// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.notifications

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import org.openactivity.core.data.repository.NotificationRepository
import javax.inject.Inject

/** Opens a system notification's inbox entry: marks it read and says where it leads. */
@HiltViewModel
class InboxLinkViewModel @Inject constructor(
    private val repository: NotificationRepository,
) : ViewModel() {
    /** Where entry [id] leads, or the inbox itself once the entry was pruned. */
    suspend fun open(id: Long): InboxLink {
        val entry = repository.get(id) ?: return InboxLink.Inbox
        if (!entry.isRead) repository.markRead(id)
        return InboxLink.To(InboxDestination.of(entry))
    }
}

/** The result of opening a notification. */
sealed interface InboxLink {
    data object Inbox : InboxLink

    data class To(val destination: InboxDestination) : InboxLink
}
