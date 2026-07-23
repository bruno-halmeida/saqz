package br.com.saqz.groups.presentation.athlete

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteFinancialStatus
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.athlete.AthleteRosterEntry
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
import br.com.saqz.groups.domain.athlete.UpdateAthleteCommand
import br.com.saqz.groups.presentation.GroupSelectionState
import br.com.saqz.groups.presentation.GroupSelectionStateMachine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@Immutable
data class AthleteEditDraft(
    val userId: String,
    val displayName: String,
    val position: AthletePosition?,
    val membershipType: AthleteMembershipType,
    val active: Boolean,
    val saving: Boolean = false,
    val failed: Boolean = false,
)

@Immutable
data class AthleteRemovalDraft(
    val userId: String,
    val displayName: String,
    val removing: Boolean = false,
    val failed: Boolean = false,
)

@Immutable
data class AthleteRosterState(
    val groupId: String? = null,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val athletes: List<AthleteRosterEntry> = emptyList(),
    val search: String = "",
    val typeFilter: AthleteMembershipType? = null,
    val positionFilter: AthletePosition? = null,
    val financialFilter: AthleteFinancialStatus? = null,
    val includeInactive: Boolean = false,
    val edit: AthleteEditDraft? = null,
    val removal: AthleteRemovalDraft? = null,
)

sealed interface AthleteRosterIntent {
    data object Reload : AthleteRosterIntent
    data class Search(val text: String) : AthleteRosterIntent
    data class FilterType(val type: AthleteMembershipType?) : AthleteRosterIntent
    data class FilterPosition(val position: AthletePosition?) : AthleteRosterIntent
    data class FilterFinancial(val status: AthleteFinancialStatus?) : AthleteRosterIntent
    data class ShowInactive(val include: Boolean) : AthleteRosterIntent
    data class OpenEdit(val userId: String) : AthleteRosterIntent
    data object CloseEdit : AthleteRosterIntent
    data class EditPosition(val position: AthletePosition?) : AthleteRosterIntent
    data class EditType(val type: AthleteMembershipType) : AthleteRosterIntent
    data class EditActive(val active: Boolean) : AthleteRosterIntent
    data object SaveEdit : AthleteRosterIntent
    data class RequestRemoval(val userId: String) : AthleteRosterIntent
    data object CancelRemoval : AthleteRosterIntent
    data object ConfirmRemoval : AthleteRosterIntent
}

sealed interface AthleteRosterEffect

class AthleteRosterViewModel(
    selection: GroupSelectionStateMachine,
    private val athletes: AthleteGateway,
) : MviViewModel<AthleteRosterState, AthleteRosterIntent, AthleteRosterEffect>(AthleteRosterState()) {
    private var loadJob: Job? = null

    init {
        selection.state
            .map { (it as? GroupSelectionState.Selected)?.group?.group?.id?.value }
            .distinctUntilChanged()
            .onEach { groupId ->
                update { AthleteRosterState(groupId = groupId) }
                if (groupId != null) load()
            }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: AthleteRosterIntent) {
        when (intent) {
            AthleteRosterIntent.Reload -> load()
            is AthleteRosterIntent.Search -> reloadWith { it.copy(search = intent.text) }
            is AthleteRosterIntent.FilterType -> reloadWith { it.copy(typeFilter = intent.type) }
            is AthleteRosterIntent.FilterPosition -> reloadWith { it.copy(positionFilter = intent.position) }
            is AthleteRosterIntent.FilterFinancial -> reloadWith { it.copy(financialFilter = intent.status) }
            is AthleteRosterIntent.ShowInactive -> reloadWith { it.copy(includeInactive = intent.include) }
            is AthleteRosterIntent.OpenEdit -> openEdit(intent.userId)
            AthleteRosterIntent.CloseEdit -> update { it.copy(edit = null) }
            is AthleteRosterIntent.EditPosition -> updateEdit { it.copy(position = intent.position) }
            is AthleteRosterIntent.EditType -> updateEdit { it.copy(membershipType = intent.type) }
            is AthleteRosterIntent.EditActive -> updateEdit { it.copy(active = intent.active) }
            AthleteRosterIntent.SaveEdit -> saveEdit()
            is AthleteRosterIntent.RequestRemoval -> requestRemoval(intent.userId)
            AthleteRosterIntent.CancelRemoval -> update { it.copy(removal = null) }
            AthleteRosterIntent.ConfirmRemoval -> confirmRemoval()
        }
    }

    private fun reloadWith(change: (AthleteRosterState) -> AthleteRosterState) {
        update(change)
        load()
    }

    private fun load() {
        val current = state.value
        val groupId = current.groupId ?: return
        loadJob?.cancel()
        update { it.copy(loading = true, failed = false) }
        loadJob = viewModelScope.launch {
            val filter = AthleteRosterFilter(
                search = current.search.takeIf(String::isNotBlank),
                membershipType = current.typeFilter,
                position = current.positionFilter,
                financialStatus = current.financialFilter,
                includeInactive = current.includeInactive,
            )
            when (val result = athletes.roster(GroupId(groupId), filter)) {
                is SaqzResult.Success -> update { it.copy(loading = false, athletes = result.value) }
                is SaqzResult.Failure -> update { it.copy(loading = false, failed = true) }
            }
        }
    }

    private fun openEdit(userId: String) {
        val entry = state.value.athletes.firstOrNull { it.userId == userId } ?: return
        update {
            it.copy(
                edit = AthleteEditDraft(
                    userId = entry.userId,
                    displayName = entry.displayName,
                    position = entry.position,
                    membershipType = entry.membershipType,
                    active = entry.active,
                ),
            )
        }
    }

    private fun updateEdit(change: (AthleteEditDraft) -> AthleteEditDraft) {
        update { current ->
            val edit = current.edit
            if (edit == null || edit.saving) current else current.copy(edit = change(edit))
        }
    }

    private fun saveEdit() {
        val current = state.value
        val edit = current.edit ?: return
        val groupId = current.groupId ?: return
        if (edit.saving) return
        update { it.copy(edit = edit.copy(saving = true, failed = false)) }
        viewModelScope.launch {
            val command = UpdateAthleteCommand(
                groupId = GroupId(groupId),
                userId = edit.userId,
                position = edit.position,
                membershipType = edit.membershipType,
                active = edit.active,
            )
            when (athletes.updateAthlete(command)) {
                is SaqzResult.Success -> {
                    update { it.copy(edit = null) }
                    load()
                }
                is SaqzResult.Failure -> update {
                    it.copy(edit = it.edit?.copy(saving = false, failed = true))
                }
            }
        }
    }

    private fun requestRemoval(userId: String) {
        val entry = state.value.athletes.firstOrNull { it.userId == userId } ?: return
        update { it.copy(removal = AthleteRemovalDraft(entry.userId, entry.displayName)) }
    }

    private fun confirmRemoval() {
        val current = state.value
        val removal = current.removal ?: return
        val groupId = current.groupId ?: return
        if (removal.removing) return
        update { it.copy(removal = removal.copy(removing = true, failed = false)) }
        viewModelScope.launch {
            when (athletes.removeAthlete(GroupId(groupId), removal.userId)) {
                is SaqzResult.Success -> {
                    update { it.copy(removal = null) }
                    load()
                }
                is SaqzResult.Failure -> update {
                    it.copy(removal = it.removal?.copy(removing = false, failed = true))
                }
            }
        }
    }
}
