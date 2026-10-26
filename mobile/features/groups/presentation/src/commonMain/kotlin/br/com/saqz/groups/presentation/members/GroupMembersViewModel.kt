package br.com.saqz.groups.presentation.members

import br.com.saqz.groups.presentation.photo.memberPhoto
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthleteRosterEntry
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
import br.com.saqz.groups.domain.membership.GroupEntryRequest
import br.com.saqz.groups.domain.membership.GroupEntryRequestGateway
import br.com.saqz.groups.domain.membership.GroupMembershipError
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import br.com.saqz.groups.domain.membership.AssignableGroupRole
import br.com.saqz.groups.domain.membership.ChangeMembershipRoleCommand
import br.com.saqz.groups.domain.membership.isExpectedRosterAccessFailure
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupTrialAccessPort
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.membership.EntryRequestError
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.entryrequest.MonthlyFeeDefaultUi
import br.com.saqz.groups.presentation.entryrequest.monthlyFeeDefault
import br.com.saqz.groups.presentation.entryrequest.sheetFor
import br.com.saqz.groups.presentation.moderation.BlockedPeopleRepository
import br.com.saqz.groups.presentation.toUiError
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

class GroupMembersViewModel(
    private val groupId: String,
    private val athleteGateway: AthleteGateway,
    private val membershipGateway: GroupMembershipGateway,
    private val groupGateway: GroupGateway,
    private val entryRequestGateway: GroupEntryRequestGateway,
    private val blocks: BlockedPeopleRepository,
    private val trialAccess: GroupTrialAccessPort? = null,
) : MviViewModel<GroupMembersState, GroupMembersIntent, GroupMembersEffect>(GroupMembersState()) {

    private var loadGeneration = 0
    private var roster: List<MemberUi> = emptyList()
    private var requests: List<JoinRequestUi> = emptyList()
    private var viewerCanManageAthletes = false
    private var viewerCanManageRoles = false
    private var feeDefault: MonthlyFeeDefaultUi? = null
    private var actionInFlight = false
    private var loadInFlight = false

    init {
        load()
        // Bloquear aqui ou no perfil do membro troca a linha do sheet sem recarregar a lista.
        viewModelScope.launch {
            blocks.blocked.collect { blocked ->
                roster = roster.map { it.copy(isBlocked = it.id in blocked) }
                project()
            }
        }
    }

    override fun handleIntent(intent: GroupMembersIntent) {
        when (intent) {
            GroupMembersIntent.Retry -> load()
            GroupMembersIntent.Refresh -> refresh()
            is GroupMembersIntent.UpdateQuery -> {
                update { it.copy(query = intent.value) }
                project()
            }
            is GroupMembersIntent.SelectFilter, is GroupMembersIntent.SelectTypeFilter -> {
                update {
                    when (intent) {
                        is GroupMembersIntent.SelectFilter -> it.copy(filter = intent.filter)
                        is GroupMembersIntent.SelectTypeFilter -> it.copy(typeFilter = intent.filter)
                        else -> it
                    }
                }
                project()
            }
            is GroupMembersIntent.OpenMember -> openMember(intent.memberId)
            GroupMembersIntent.DismissSheet -> update { it.copy(selected = null) }
            is GroupMembersIntent.PerformAction -> perform(intent.action)
            GroupMembersIntent.ConfirmRemove -> state.value.confirmRemove?.let { remove(it) }
            GroupMembersIntent.DismissRemove -> update { it.copy(confirmRemove = null) }
            GroupMembersIntent.DismissSubscriptionRequired -> update { it.copy(subscriptionRequired = false) }
            GroupMembersIntent.DismissActionFailed -> update { it.copy(actionFailed = false, actionDenied = false) }
            is GroupMembersIntent.AcceptRequest,
            is GroupMembersIntent.SelectAcceptAs,
            GroupMembersIntent.ConfirmAcceptAs,
            GroupMembersIntent.DismissAcceptAs,
            is GroupMembersIntent.DeclineRequest,
            GroupMembersIntent.ConfirmDecline,
            GroupMembersIntent.DismissDecline,
            GroupMembersIntent.ClearNotice,
            -> handleRequestIntent(intent)
            GroupMembersIntent.Invite -> emit(GroupMembersEffect.OpenInvite(groupId))
        }
    }

    private fun handleRequestIntent(intent: GroupMembersIntent) {
        when (intent) {
            is GroupMembersIntent.AcceptRequest -> accept(intent.requestId)
            is GroupMembersIntent.SelectAcceptAs -> update { current ->
                current.copy(acceptAs = current.acceptAs?.copy(selected = intent.type, feeRequired = false))
            }
            GroupMembersIntent.ConfirmAcceptAs -> state.value.acceptAs?.let { sheet ->
                requests.firstOrNull { it.id == sheet.requestId }?.let { approve(it, sheet.selected) }
            }
            GroupMembersIntent.DismissAcceptAs -> if (state.value.acceptAs?.requestId !in state.value.busyRequestIds) {
                update { it.copy(acceptAs = null) }
            }
            is GroupMembersIntent.DeclineRequest -> update { current ->
                current.copy(declineCandidate = requests.firstOrNull { it.id == intent.requestId })
            }
            GroupMembersIntent.ConfirmDecline -> state.value.declineCandidate?.let { decline(it) }
            GroupMembersIntent.DismissDecline -> update { it.copy(declineCandidate = null) }
            GroupMembersIntent.ClearNotice -> update { it.copy(notice = null) }
            else -> Unit
        }
    }

    /**
     * Recarga por baixo ([GroupMembersIntent.Refresh]): lista na tela fica, sem esqueleto e sem
     * tela de erro. Com carga em voo, ação em andamento ou ficha aberta, é ignorado.
     */
    private fun refresh() {
        if (isFirstAppearance()) return
        val busy = loadInFlight || actionInFlight || state.value.selected != null
        if (!busy) load(softRefresh = true)
    }

    private fun load(softRefresh: Boolean = false) {
        val generation = ++loadGeneration
        loadInFlight = true
        if (!softRefresh) update { it.copy(isLoading = true, loadFailed = false, error = null) }
        viewModelScope.launch { blocks.refresh() }
        val job = viewModelScope.launch {
            val group = when (val result = groupGateway.read(GroupId(groupId))) {
                is SaqzResult.Failure -> {
                    showFailure(generation, result.error.toUiError(), softRefresh)
                    return@launch
                }
                is SaqzResult.Success -> result.value.group
            }
            val viewerRole = group.role
            feeDefault = group.financeDefaults.monthlyFeeDefault()
            val ownProfile = when (val result = athleteGateway.ownProfile()) {
                is SaqzResult.Failure -> {
                    showFailure(generation, result.error.toUiError(), softRefresh)
                    return@launch
                }
                is SaqzResult.Success -> result.value
            }
            if (generation != loadGeneration) return@launch

            val canManageAthletes = viewerRole == GroupRole.OWNER || viewerRole == GroupRole.ADMIN
            val canManageRoles = viewerRole == GroupRole.OWNER
            viewerCanManageAthletes = canManageAthletes
            viewerCanManageRoles = canManageRoles

            // Os pedidos são de gestão (mesma regra do backend): quem não gerencia nem pergunta.
            val requestsResult = if (canManageAthletes) entryRequestGateway.list(GroupId(groupId)) else null
            if (generation != loadGeneration) return@launch
            val loadedRequests = (requestsResult as? SaqzResult.Success)?.value?.map { it.toUi(group.timeZone.id) }

            var rosterError: GroupUiError? = null
            val loadedRoster = when (
                val outcome = loadRoster(generation, viewerRole, ownProfile.userId, canManageAthletes, canManageRoles)
            ) {
                null -> return@launch
                is RosterOutcome.Loaded -> outcome.members
                is RosterOutcome.Failed -> {
                    // Cada lista falha sozinha: com pedidos na mão, eles aparecem mesmo sem o elenco.
                    if (loadedRequests.isNullOrEmpty() || softRefresh) {
                        showRosterFailure(generation, outcome.error, loadedRequests, canManageAthletes, softRefresh)
                        return@launch
                    }
                    rosterError = outcome.error
                    null
                }
            }
            if (loadedRoster != null) roster = loadedRoster
            if (loadedRequests != null) requests = loadedRequests
            val requestsFailed = requestsResult is SaqzResult.Failure
            update {
                it.copy(
                    isLoading = false,
                    loadFailed = false,
                    error = rosterError,
                    rosterFailed = loadedRoster == null,
                    requestsFailed = keepWhenSoft(softRefresh, requestsFailed, it.requestsFailed),
                    totalCount = roster.size,
                    adminCount = roster.count(MemberUi::isAdmin),
                ).withRequests(requests, canManageAthletes)
            }
            project()
        }
        job.invokeOnCompletion { if (generation == loadGeneration) loadInFlight = false }
    }

    private sealed interface RosterOutcome {
        data class Loaded(val members: List<MemberUi>) : RosterOutcome

        data class Failed(val error: GroupUiError) : RosterOutcome
    }

    /** Elenco com o papel de cada pessoa; `null` quando uma recarga mais nova tomou o lugar desta. */
    private suspend fun loadRoster(
        generation: Int,
        viewerRole: GroupRole,
        ownUserId: String,
        canManageAthletes: Boolean,
        canManageRoles: Boolean,
    ): RosterOutcome? {
        val rosterResult = athleteGateway.roster(GroupId(groupId), AthleteRosterFilter())
        val memberships = if (rosterResult is SaqzResult.Success) membershipGateway.listMemberships(GroupId(groupId)) else null
        if (generation != loadGeneration) return null
        if (rosterResult is SaqzResult.Failure) return RosterOutcome.Failed(rosterResult.error.toUiError())

        // O endpoint de memberships é de gestão: atleta comum recebe 403. Esse 403
        // esperado não esconde o roster desde o VUL-134. Papel de cada linha vem do
        // próprio roster (e do lookup, quando o dono/admin consegue lê-lo).
        val roles = when (memberships) {
            is SaqzResult.Success -> memberships.value.associate { it.userId to it.role }
            is SaqzResult.Failure -> {
                val membershipError = memberships.error
                if (membershipError is GroupMembershipError.DataFailure && membershipError.isExpectedRosterAccessFailure()) {
                    emptyMap()
                } else {
                    return RosterOutcome.Failed(membershipError.toUiError())
                }
            }
            null -> emptyMap()
        }
        val blocked = blocks.blocked.value
        return RosterOutcome.Loaded(
            (rosterResult as SaqzResult.Success).value.map { entry ->
                entry.toUi(
                    groupId = groupId,
                    role = roles[entry.userId]
                        ?: viewerRole.takeIf { entry.userId == ownUserId }
                        ?: entry.role,
                    ownUserId = ownUserId,
                    canManageAthletes = canManageAthletes,
                    canManageRoles = canManageRoles,
                ).copy(isBlocked = entry.userId in blocked)
            },
        )
    }

    /** Sem pedidos para mostrar a tela inteira falha; por baixo ([softRefresh]) o elenco fica, mas os pedidos novos entram. */
    private fun showRosterFailure(
        generation: Int,
        error: GroupUiError,
        loadedRequests: List<JoinRequestUi>?,
        canReview: Boolean,
        softRefresh: Boolean,
    ) {
        if (softRefresh && loadedRequests != null) {
            requests = loadedRequests
            update { it.withRequests(requests, canReview) }
            project()
        }
        showFailure(generation, error, softRefresh)
    }

    /** Por baixo ([softRefresh]) a falha não troca a lista por erro: ela fica e a próxima volta tenta de novo. */
    private fun showFailure(generation: Int, error: GroupUiError, softRefresh: Boolean = false) {
        if (generation != loadGeneration || softRefresh) return
        update { it.copy(isLoading = false, loadFailed = true, error = error) }
    }

    private fun openMember(memberId: String) {
        val member = roster.firstOrNull { it.id == memberId } ?: return
        update { it.copy(selected = member) }
    }

    private fun perform(action: GroupMemberAction) {
        val selected = state.value.selected ?: return
        if (action !in selected.sheetActions() || actionInFlight) return
        if (selected.isOwner && action in OwnerProtectedActions) return
        when (action) {
            GroupMemberAction.ViewProfile -> emit(GroupMembersEffect.OpenMemberProfile(selected.id))
            GroupMemberAction.EditMember -> emit(GroupMembersEffect.OpenMemberEditor(selected.id))
            GroupMemberAction.Promote -> changeRole(selected, AssignableGroupRole.ADMIN)
            GroupMemberAction.Demote -> changeRole(selected, AssignableGroupRole.ATHLETE)
            GroupMemberAction.Remove -> update { it.copy(selected = null, confirmRemove = selected) }
            GroupMemberAction.Report -> emit(GroupMembersEffect.ReportMember(groupId, selected.id, selected.name))
            GroupMemberAction.Block -> emit(GroupMembersEffect.BlockMember(groupId, selected.id, selected.name))
            GroupMemberAction.Unblock -> emit(GroupMembersEffect.UnblockMember(selected.id))
        }
        // Promover, rebaixar e remover fecham o sheet sozinhos, quando a chamada começa.
        if (action !in RemoteActions) {
            update { it.copy(selected = null) }
        }
    }

    private fun changeRole(member: MemberUi, role: AssignableGroupRole) {
        if (member.isOwner) return
        actionInFlight = true
        update { it.copy(selected = null) }
        viewModelScope.launch {
            when (val result = membershipGateway.changeRole(
                ChangeMembershipRoleCommand(GroupId(groupId), member.id, role),
            )) {
                is SaqzResult.Success -> load()
                is SaqzResult.Failure -> showActionFailure(result.error.toUiError())
            }
            actionInFlight = false
        }
    }

    /**
     * Trocar papel ou remover que falha não derruba a lista: plano pausado abre a folha da
     * assinatura (tentar de novo nunca resolve); o resto vira um aviso e a tela continua.
     */
    private suspend fun showActionFailure(error: GroupUiError) {
        if (error == GroupUiError.SubscriptionRequired) {
            val trialEnded = trialAccess?.read(GroupId(groupId))?.trialEnded == true
            update { it.copy(subscriptionRequired = true, trialEnded = trialEnded) }
        } else if (error == GroupUiError.AccessDenied) {
            update { it.copy(actionDenied = true) }
        } else {
            update { it.copy(actionFailed = true) }
        }
    }

    private fun remove(member: MemberUi) {
        if (member.isOwner || actionInFlight) return
        actionInFlight = true
        update { it.copy(selected = null, confirmRemove = null) }
        viewModelScope.launch {
            when (val result = athleteGateway.removeAthlete(GroupId(groupId), member.id)) {
                is SaqzResult.Success -> load()
                is SaqzResult.Failure -> showActionFailure(result.error.toUiError())
            }
            actionInFlight = false
        }
    }

    /** Sem mensalidade padrão, aceita em um toque como avulso; com ela, abre "Aceitar como" (DEB-112). */
    private fun accept(requestId: String) {
        val request = requests.firstOrNull { it.id == requestId } ?: return
        if (request.id in state.value.busyRequestIds) return
        val default = feeDefault
        if (default != null) {
            update { it.copy(acceptAs = default.sheetFor(request.id, request.name)) }
        } else {
            approve(request, AthleteMembershipType.AVULSO)
        }
    }

    private fun approve(request: JoinRequestUi, type: AthleteMembershipType) {
        if (!beginRequestAction(request.id)) return
        viewModelScope.launch {
            when (val result = entryRequestGateway.approve(GroupId(groupId), request.id, type)) {
                // O padrão sumiu depois de abrir a folha: a folha fica com o aviso e o pedido continua na lista.
                is SaqzResult.Failure -> if (result.error == EntryRequestError.MonthlyFeeRequired) {
                    update {
                        it.copy(
                            busyRequestIds = it.busyRequestIds - request.id,
                            acceptAs = it.acceptAs?.copy(feeRequired = true),
                        )
                    }
                } else {
                    update { it.copy(acceptAs = null) }
                    endRequestAction(request.id, failure = result.error.toUiError())
                }
                is SaqzResult.Success -> {
                    update { it.copy(acceptAs = null) }
                    // A pessoa entra no elenco na hora; a recarga logo abaixo traz o dado do servidor.
                    roster = roster.filterNot { it.id == request.id } + MemberUi(
                        id = request.id,
                        name = request.name,
                        meta = "",
                        isAdmin = false,
                        isSelf = false,
                        stats = "",
                        canManageAthletes = viewerCanManageAthletes,
                        canManageRoles = viewerCanManageRoles,
                    )
                    endRequestAction(request, GroupMembersNotice.RequestApproved(request.name))
                }
            }
        }
    }

    private fun decline(request: JoinRequestUi) {
        update { it.copy(declineCandidate = null) }
        if (!beginRequestAction(request.id)) return
        viewModelScope.launch {
            when (val result = entryRequestGateway.reject(GroupId(groupId), request.id)) {
                is SaqzResult.Failure -> endRequestAction(request.id, failure = result.error.toUiError())
                is SaqzResult.Success -> endRequestAction(request, GroupMembersNotice.RequestRejected)
            }
        }
    }

    /** Falso quando já há uma chamada em voo para este pedido: o segundo toque não chama a API. */
    private fun beginRequestAction(requestId: String): Boolean {
        if (requestId in state.value.busyRequestIds) return false
        update { it.copy(busyRequestIds = it.busyRequestIds + requestId, actionFailed = false) }
        return true
    }

    private suspend fun endRequestAction(requestId: String, failure: GroupUiError) {
        update { it.copy(busyRequestIds = it.busyRequestIds - requestId) }
        showActionFailure(failure)
    }

    /** Decidido: o pedido sai da lista, o aviso aparece e as duas listas recarregam por baixo. */
    private fun endRequestAction(request: JoinRequestUi, notice: GroupMembersNotice) {
        requests = requests - request
        update {
            it.copy(
                busyRequestIds = it.busyRequestIds - request.id,
                notice = notice,
                totalCount = roster.size,
                adminCount = roster.count(MemberUi::isAdmin),
            ).withRequests(requests, canReview = true)
        }
        project()
        load(softRefresh = true)
    }

    private fun project() = update { state ->
        val query = state.query.trim()
        val people = roster.filter { it.name.containsQuery(query) && it.matches(state.typeFilter) }
        val pending = requests.filter { it.name.containsQuery(query) }
        val admins = people.filter { it.isAdmin }
        val members = people.filterNot { it.isAdmin }
        val showsPeople = state.filter != GroupMembersFilter.Pending && state.filter != GroupMembersFilter.Invited
        state.copy(
            joinRequests = if (state.filter == GroupMembersFilter.Admins || state.filter == GroupMembersFilter.Invited) {
                emptyList()
            } else {
                pending
            },
            admins = if (showsPeople) admins else emptyList(),
            members = if (state.filter == GroupMembersFilter.All) members else emptyList(),
            shownCount = if (state.filter == GroupMembersFilter.All) members.size else 0,
            monthlyCount = roster.count { it.monthly },
            perGameCount = roster.count { !it.monthly },
        )
    }
}

/** O que muda o papel ou tira do grupo: nunca sobre o dono. Editar jogador vale para ele; denunciar e bloquear também. */
private val OwnerProtectedActions = setOf(
    GroupMemberAction.Promote,
    GroupMemberAction.Demote,
    GroupMemberAction.Remove,
)

private val RemoteActions = setOf(GroupMemberAction.Promote, GroupMemberAction.Demote, GroupMemberAction.Remove)

/** Por baixo uma falha nova não acende o aviso: vale o que já estava na tela. */
private fun keepWhenSoft(softRefresh: Boolean, failed: Boolean, previous: Boolean) =
    if (softRefresh && failed) previous else failed

private fun GroupMembersState.withRequests(requests: List<JoinRequestUi>, canReview: Boolean) =
    copy(canReviewRequests = canReview, pendingCount = if (canReview) requests.size else 0)

private fun GroupEntryRequest.toUi(zoneId: String) = JoinRequestUi(userId, displayName, "Pediu ${requestedAt.agoLabel(zoneId)}")

/** "há 5 min", "há 3 h", "ontem", "há 4 dias"; o dia conta no fuso do grupo (ou do aparelho, se faltar). */
private fun String.agoLabel(zoneId: String): String {
    val asked = runCatching { Instant.parse(this) }.getOrNull() ?: return "há pouco"
    val now = Clock.System.now()
    val minutes = (now - asked).inWholeMinutes.coerceAtLeast(0)
    val zone = runCatching { TimeZone.of(zoneId) }.getOrElse { TimeZone.currentSystemDefault() }
    val days = asked.toLocalDateTime(zone).date.daysUntil(now.toLocalDateTime(zone).date)
    return when {
        minutes < 1 -> "agora"
        minutes < 60 -> "há $minutes min"
        minutes < 24 * 60 && days == 0 -> "há ${minutes / 60} h"
        days <= 1 -> "ontem"
        else -> "há $days dias"
    }
}

private fun AthleteRosterEntry.toUi(
    groupId: String,
    role: GroupRole?,
    ownUserId: String,
    canManageAthletes: Boolean,
    canManageRoles: Boolean,
) = MemberUi(
    id = userId,
    name = displayName,
    meta = listOfNotNull(
        position?.label(),
        membershipType.name.lowercase().replaceFirstChar { it.uppercase() },
        // Isenção da mensalidade (DEB-115) é dado financeiro: só quem administra vê.
        "com isenção".takeIf { monthlyFeeExempt && canManageAthletes && membershipType == AthleteMembershipType.MENSALISTA },
    ).joinToString(" · "),
    isAdmin = role == GroupRole.ADMIN || role == GroupRole.OWNER,
    isSelf = userId == ownUserId,
    canManageAthletes = canManageAthletes,
    canManageRoles = canManageRoles,
    isOwner = role == GroupRole.OWNER,
    // DESCONHECIDO é o contrato para atleta comum: não há filtro financeiro na tela e ele
    // deliberadamente não é convertido em status visual.
    stats = "",
    photo = memberPhoto(groupId, userId, photoTag),
    monthly = membershipType == AthleteMembershipType.MENSALISTA,
    exempt = monthlyFeeExempt && membershipType == AthleteMembershipType.MENSALISTA,
)

private fun br.com.saqz.groups.domain.athlete.AthletePosition.label(): String = when (this) {
    br.com.saqz.groups.domain.athlete.AthletePosition.LIBERO -> "Líbero"
    br.com.saqz.groups.domain.athlete.AthletePosition.PONTA -> "Ponteiro"
    br.com.saqz.groups.domain.athlete.AthletePosition.CENTRAL -> "Central"
    br.com.saqz.groups.domain.athlete.AthletePosition.OPOSTO -> "Oposto"
    br.com.saqz.groups.domain.athlete.AthletePosition.LEVANTADOR -> "Levantador"
}

private fun MemberUi.matches(filter: MemberTypeFilter) = when (filter) {
    MemberTypeFilter.All -> true
    MemberTypeFilter.Monthly -> monthly
    MemberTypeFilter.PerGame -> !monthly
}

private fun String.containsQuery(query: String): Boolean =
    query.isEmpty() || contains(query, ignoreCase = true)
