package br.com.saqz.groups.presentation.details

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupProfile
import br.com.saqz.groups.domain.group.GroupProfileGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.toUiError
import kotlinx.coroutines.launch

class GroupDetailsViewModel(
    private val groupId: String,
    private val groupGateway: GroupGateway,
    private val profileGateway: GroupProfileGateway,
) : MviViewModel<GroupDetailsState, GroupDetailsIntent, GroupDetailsEffect>(GroupDetailsState()) {

    private var loadGeneration = 0

    init {
        load()
    }

    override fun onIntent(intent: GroupDetailsIntent) {
        when (intent) {
            GroupDetailsIntent.Retry -> load()
            GroupDetailsIntent.CreateNextGame -> emit(GroupDetailsEffect.OpenCreateGame(groupId))
            GroupDetailsIntent.EditGroup -> emit(GroupDetailsEffect.OpenEdit(groupId))
            GroupDetailsIntent.EditVenue -> emit(GroupDetailsEffect.OpenEdit(groupId))
            GroupDetailsIntent.ManageMembers,
            GroupDetailsIntent.ViewAllMembers,
            -> emit(GroupDetailsEffect.OpenMembers(groupId))
            GroupDetailsIntent.ManageSchedule,
            GroupDetailsIntent.OpenSchedule,
            -> emit(GroupDetailsEffect.OpenSchedule(groupId))
            GroupDetailsIntent.InviteByLink,
            GroupDetailsIntent.Invite,
            -> emit(GroupDetailsEffect.OpenInviteLink(groupId))
            GroupDetailsIntent.OpenCashbox -> emit(GroupDetailsEffect.OpenCashbox(groupId))
            GroupDetailsIntent.OpenVenueMap -> emit(GroupDetailsEffect.OpenMap)
            GroupDetailsIntent.Leave -> emit(GroupDetailsEffect.Left)
            GroupDetailsIntent.ConfirmAttendance,
            GroupDetailsIntent.ViewGame,
            GroupDetailsIntent.NotifyPending,
            GroupDetailsIntent.OpenNotices,
            GroupDetailsIntent.OpenChat,
            -> Unit
        }
    }

    private fun load() {
        val generation = ++loadGeneration
        update { it.copy(isLoading = true, loadFailed = false, error = null) }
        viewModelScope.launch {
            val groupResult = groupGateway.read(GroupId(groupId))
            val profileResult = profileGateway.readProfile(GroupId(groupId))
            if (generation != loadGeneration) return@launch

            val failure = when {
                groupResult is SaqzResult.Failure -> groupResult.error.toUiError()
                profileResult is SaqzResult.Failure -> profileResult.error.toUiError()
                else -> null
            }
            if (failure != null) {
                showFailure(generation, failure)
                return@launch
            }

            val group = (groupResult as SaqzResult.Success).value.group
            val profileGroup = (profileResult as SaqzResult.Success).value.group
            update { it.copy(isLoading = false, loadFailed = false, error = null).from(group, profileGroup) }
        }
    }

    private fun showFailure(generation: Int, error: GroupUiError) {
        if (generation != loadGeneration) return
        update { it.copy(isLoading = false, loadFailed = true, error = error) }
    }
}

private fun GroupDetailsState.from(group: Group, profileGroup: Group): GroupDetailsState {
    val profile = profileGroup.profile ?: group.profile
    return copy(
        isAdmin = group.role != br.com.saqz.groups.domain.group.GroupRole.ATHLETE,
        header = GroupHeaderUi(
            name = group.name,
            subtitle = listOfNotNull(
                profile?.composition?.label(),
                profile?.level?.label(),
            ).joinToString(" · ").ifBlank { group.timeZone.id },
            summaryChips = profile.toSummaryChips(),
        ),
        venue = profile?.defaultVenue?.let { VenueUi(it.name, it.address) },
    )
}

private fun GroupProfile?.toSummaryChips(): List<GroupSummaryChipUi> {
    if (this == null) return emptyList()
    return listOfNotNull(
        city?.takeIf(String::isNotBlank)?.let(::GroupSummaryChipUi),
        modality?.label()?.let(::GroupSummaryChipUi),
        regularSlots.map { it.weekday.label() }.distinct().takeIf { it.isNotEmpty() }
            ?.joinToString(" e ")
            ?.let { GroupSummaryChipUi(it, highlighted = true) },
    )
}

private fun br.com.saqz.groups.domain.group.GroupComposition.label(): String = when (this) {
    br.com.saqz.groups.domain.group.GroupComposition.WOMEN -> "Feminino"
    br.com.saqz.groups.domain.group.GroupComposition.MEN -> "Masculino"
    br.com.saqz.groups.domain.group.GroupComposition.MIXED -> "Misto"
}

private fun br.com.saqz.groups.domain.group.GroupLevel.label(): String = when (this) {
    br.com.saqz.groups.domain.group.GroupLevel.BEGINNER -> "Iniciante"
    br.com.saqz.groups.domain.group.GroupLevel.INTERMEDIATE -> "Intermediário"
    br.com.saqz.groups.domain.group.GroupLevel.ADVANCED -> "Avançado"
    br.com.saqz.groups.domain.group.GroupLevel.MIXED_LEVELS -> "Níveis mistos"
    br.com.saqz.groups.domain.group.GroupLevel.CUSTOM -> "Personalizado"
}

private fun br.com.saqz.groups.domain.group.GroupModality.label(): String = when (this) {
    br.com.saqz.groups.domain.group.GroupModality.COURT_VOLLEYBALL -> "Vôlei de quadra"
    br.com.saqz.groups.domain.group.GroupModality.BEACH_VOLLEYBALL -> "Vôlei de areia"
    br.com.saqz.groups.domain.group.GroupModality.FOOTVOLLEY -> "Futevôlei"
}

private fun br.com.saqz.groups.domain.group.GroupWeekday.label(): String = when (this) {
    br.com.saqz.groups.domain.group.GroupWeekday.MONDAY -> "Segunda"
    br.com.saqz.groups.domain.group.GroupWeekday.TUESDAY -> "Terça"
    br.com.saqz.groups.domain.group.GroupWeekday.WEDNESDAY -> "Quarta"
    br.com.saqz.groups.domain.group.GroupWeekday.THURSDAY -> "Quinta"
    br.com.saqz.groups.domain.group.GroupWeekday.FRIDAY -> "Sexta"
    br.com.saqz.groups.domain.group.GroupWeekday.SATURDAY -> "Sábado"
    br.com.saqz.groups.domain.group.GroupWeekday.SUNDAY -> "Domingo"
}
