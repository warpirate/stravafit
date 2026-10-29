// SPDX-License-Identifier: AGPL-3.0-or-later
package org.openactivity.app.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.openactivity.app.data.Challenge
import org.openactivity.app.data.Club
import org.openactivity.app.data.CommunityRepository
import javax.inject.Inject

/** Sub-tabs of the Groups tab. */
enum class GroupsTab(val label: String) {
    Clubs("Clubs"),
    Challenges("Challenges"),
}

/** State of the Groups tab. */
data class GroupsUiState(
    val tab: GroupsTab = GroupsTab.Clubs,
    val myClubs: List<Club> = emptyList(),
    val suggestedClubs: List<Club> = emptyList(),
    val challenges: List<Challenge> = emptyList(),
)

/** Groups placeholder: clubs and challenges from sample data (blueprint phase 5 feature). */
@HiltViewModel
class GroupsViewModel @Inject constructor(repository: CommunityRepository) : ViewModel() {

    private val tab = MutableStateFlow(GroupsTab.Clubs)

    val uiState: StateFlow<GroupsUiState> = combine(repository.clubs, repository.challenges, tab) { clubs, challenges, selected ->
        GroupsUiState(
            tab = selected,
            myClubs = clubs.filter { it.joined },
            suggestedClubs = clubs.filterNot { it.joined },
            challenges = challenges,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GroupsUiState())

    fun selectTab(newTab: GroupsTab) {
        tab.value = newTab
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
