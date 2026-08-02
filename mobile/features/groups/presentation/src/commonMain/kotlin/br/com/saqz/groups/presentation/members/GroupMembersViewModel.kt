package br.com.saqz.groups.presentation.members

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthleteRosterEntry
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
import br.com.saqz.groups.domain.membership.GroupMembershipError
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.groups.domain.membership.AssignableGroupRole
import br.com.saqz.groups.domain.membership.ChangeMembershipRoleCommand
import br.com.saqz.groups.domain.membership.isExpectedRosterAccessFailure
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.toUiError
import kotlinx.coroutines.launch

class GroupMembersViewModel(
    private val groupId: String,
    private val athleteGateway: AthleteGateway,
    private val membershipGateway: GroupMembershipGateway,
    private val groupGateway: GroupGateway,
) : MviViewModel<GroupMembersState, GroupMembersIntent, GroupMembersEffect>(GroupMembersState()) {

    private var loadGeneration = 0
    private var roster: List<MemberUi> = emptyList()
    private var requests: List<JoinRequestUi> = emptyList()
    private var actionInFlight = false

    init {
        load()
    }

    override fun onIntent(intent: GroupMembersIntent) {
        when (intent) {
            GroupMembersIntent.Retry -> load()
            is GroupMembersIntent.UpdateQuery -> {
                update { it.copy(query = intent.value) }
                project()
            }
            is GroupMembersIntent.SelectFilter -> {
                update { it.copy(filter = intent.filter) }
                project()
            }
            is GroupMembersIntent.OpenMember -> openMember(intent.memberId)
            GroupMembersIntent.DismissSheet -> update { it.copy(selected = null) }
            is GroupMembersIntent.PerformAction -> perform(intent.action)
            is GroupMembersIntent.AcceptRequest -> accept(intent.requestId)
            is GroupMembersIntent.DeclineRequest -> decline(intent.requestId)
            GroupMembersIntent.Invite -> emit(GroupMembersEffect.OpenInvite(groupId))
        }
    }

    private fun load() {
        val generation = ++loadGeneration
        update { it.copy(isLoading = true, loadFailed = false, error = null) }
        viewModelScope.launch {
            val viewerRole = when (val result = groupGateway.read(GroupId(groupId))) {
                is SaqzResult.Failure -> {
                    showFailure(generation, result.error.toUiError())
                    return@launch
                }
                is SaqzResult.Success -> result.value.group.role
            }
            val ownProfile = when (val result = athleteGateway.ownProfile()) {
                is SaqzResult.Failure -> {
                    showFailure(generation, result.error.toUiError())
                    return@launch
                }
                is SaqzResult.Success -> result.value
            }
            if (generation != loadGeneration) return@launch

            val rosterResult = athleteGateway.roster(GroupId(groupId), AthleteRosterFilter())
            if (generation != loadGeneration) return@launch
            if (rosterResult is SaqzResult.Failure) {
                showFailure(generation, rosterResult.error.toUiError())
                return@launch
            }

            // O endpoint de memberships é de gestão: atleta comum recebe 403. Esse 403
            // esperado não esconde o roster desde o VUL-134; admins ainda ganham os papéis.
            val memberships = membershipGateway.listMemberships(GroupId(groupId))
            if (generation != loadGeneration) return@launch
            val roles = when (memberships) {
                is SaqzResult.Success -> memberships.value.associate { it.userId to it.role }
                is SaqzResult.Failure -> {
                    val membershipError = memberships.error
                    when (membershipError) {
                    is GroupMembershipError.DataFailure -> if (membershipError.isExpectedRosterAccessFailure()) {
                        emptyMap()
                    } else {
                        showFailure(generation, membershipError.toUiError())
                        return@launch
                    }
                    else -> {
                        showFailure(generation, membershipError.toUiError())
                        return@launch
                    }
                    }
                }
            }
            val canManageMembers = viewerRole == GroupRole.OWNER && memberships is SaqzResult.Success

            roster = (rosterResult as SaqzResult.Success).value.map {
                it.toUi(
                    role = roles[it.userId] ?: viewerRole.takeIf { role -> it.userId == ownProfile.userId },
                    ownUserId = ownProfile.userId,
                    canManageMembers = canManageMembers,
                )
            }
            requests = emptyList()
            update {
                it.copy(
                    isLoading = false,
                    loadFailed = false,
                    error = null,
                    totalCount = roster.size,
                    adminCount = roster.count(MemberUi::isAdmin),
                    pendingCount = 0,
                )
            }
            project()
        }
    }

    private fun showFailure(generation: Int, error: GroupUiError) {
        if (generation != loadGeneration) return
        update { it.copy(isLoading = false, loadFailed = true, error = error) }
    }

    private fun openMember(memberId: String) {
        val member = roster.firstOrNull { it.id == memberId } ?: return
        if (member.isSelf) return
        update { it.copy(selected = member) }
    }

    private fun perform(action: GroupMemberAction) {
        val selected = state.value.selected ?: return
        if (action !in selected.sheetActions() || actionInFlight) return
        when (action) {
            GroupMemberAction.ViewProfile -> emit(GroupMembersEffect.OpenMemberProfile(selected.id))
            GroupMemberAction.EditMember -> emit(GroupMembersEffect.OpenMemberEditor(selected.id))
            GroupMemberAction.Promote -> changeRole(selected, AssignableGroupRole.ADMIN)
            GroupMemberAction.Demote -> changeRole(selected, AssignableGroupRole.ATHLETE)
            GroupMemberAction.Remove -> remove(selected)
        }
        if (action == GroupMemberAction.ViewProfile || action == GroupMemberAction.EditMember) {
            update { it.copy(selected = null) }
        }
    }

    private fun changeRole(member: MemberUi, role: AssignableGroupRole) {
        actionInFlight = true
        update { it.copy(selected = null) }
        viewModelScope.launch {
            when (val result = membershipGateway.changeRole(
                ChangeMembershipRoleCommand(GroupId(groupId), member.id, role),
            )) {
                is SaqzResult.Success -> load()
                is SaqzResult.Failure -> showFailure(loadGeneration, result.error.toUiError())
            }
            actionInFlight = false
        }
    }

    private fun remove(member: MemberUi) {
        actionInFlight = true
        update { it.copy(selected = null) }
        viewModelScope.launch {
            when (val result = athleteGateway.removeAthlete(GroupId(groupId), member.id)) {
                is SaqzResult.Success -> load()
                is SaqzResult.Failure -> showFailure(loadGeneration, result.error.toUiError())
            }
            actionInFlight = false
        }
    }

    private fun accept(requestId: String) {
        val request = requests.firstOrNull { it.id == requestId }?.takeIf { !it.awaitingReview } ?: return
        requests = requests - request
        roster = roster + MemberUi(request.id, request.name, request.meta, false, false, "")
        update { it.copy(totalCount = it.totalCount + 1, pendingCount = it.pendingCount - 1) }
        project()
    }

    private fun decline(requestId: String) {
        val request = requests.firstOrNull { it.id == requestId }?.takeIf { !it.awaitingReview } ?: return
        requests = requests - request
        update { it.copy(pendingCount = it.pendingCount - 1) }
        project()
    }

    private fun project() = update { state ->
        val query = state.query.trim()
        val people = roster.filter { it.name.containsQuery(query) }
        val pending = requests.filter { it.name.containsQuery(query) }
        val admins = people.filter { it.isAdmin }
        val members = people.filterNot { it.isAdmin }
        val showsPeople = state.filter != GroupMembersFilter.Pending
        state.copy(
            joinRequests = if (state.filter == GroupMembersFilter.Admins) emptyList() else pending,
            admins = if (showsPeople) admins else emptyList(),
            members = if (state.filter == GroupMembersFilter.All) members else emptyList(),
            shownCount = if (state.filter == GroupMembersFilter.All) members.size else 0,
        )
    }
}

private fun AthleteRosterEntry.toUi(
    role: GroupRole?,
    ownUserId: String,
    canManageMembers: Boolean,
) = MemberUi(
    id = userId,
    name = displayName,
    meta = listOfNotNull(position?.label(), membershipType.name.lowercase().replaceFirstChar { it.uppercase() })
        .joinToString(" · "),
    isAdmin = role == GroupRole.ADMIN || role == GroupRole.OWNER,
    isSelf = userId == ownUserId,
    canManageMembers = canManageMembers,
    // DESCONHECIDO é o contrato para atleta comum: não há filtro financeiro na tela e ele
    // deliberadamente não é convertido em status visual.
    stats = "",
)

private fun br.com.saqz.groups.domain.athlete.AthletePosition.label(): String = when (this) {
    br.com.saqz.groups.domain.athlete.AthletePosition.LIBERO -> "Líbero"
    br.com.saqz.groups.domain.athlete.AthletePosition.PONTA -> "Ponta"
    br.com.saqz.groups.domain.athlete.AthletePosition.CENTRAL -> "Central"
    br.com.saqz.groups.domain.athlete.AthletePosition.OPOSTO -> "Oposto"
    br.com.saqz.groups.domain.athlete.AthletePosition.LEVANTADOR -> "Levantador"
}

private fun String.containsQuery(query: String): Boolean =
    query.isEmpty() || contains(query, ignoreCase = true)
