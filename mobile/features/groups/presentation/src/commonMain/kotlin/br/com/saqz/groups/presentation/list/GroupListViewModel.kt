package br.com.saqz.groups.presentation.list

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.OwnAthleteMembership
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.SelfAttendanceCommand
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupCreationEntitlement
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.home.InviteLinkParser
import br.com.saqz.groups.presentation.isOffline
import br.com.saqz.groups.presentation.isPlanPaused
import br.com.saqz.groups.presentation.photo.groupPhotoUrl
import br.com.saqz.groups.presentation.toUiError
import kotlinx.coroutines.launch
import br.com.saqz.groups.domain.game.GameGateway
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class GroupListViewModel(
    private val athleteGateway: AthleteGateway,
    private val groupGateway: GroupGateway,
    private val entitlement: GroupCreationEntitlement,
    private val gameGateway: GameGateway,
    private val attendanceGateway: AttendanceGateway,
    private val now: () -> Instant = { Clock.System.now() },
) : MviViewModel<GroupListState, GroupListIntent, GroupListEffect>(GroupListState()) {

    private var loadGeneration = 0
    private var appeared = false

    // O segundo toque enquanto a consulta do plano está em voo é descartado — rotear duas
    // vezes para a mesma decisão só piscaria a tela.
    private var checkingPlan = false

    init {
        load()
    }

    override fun handleIntent(intent: GroupListIntent) {
        when (intent) {
            is GroupListIntent.OpenGroup -> emit(GroupListEffect.OpenGroup(intent.id))
            GroupListIntent.CreateGroup -> checkCreateGroup()
            GroupListIntent.Retry -> load()
            GroupListIntent.Refresh -> refreshSilently()
            GroupListIntent.PullRefresh -> pullRefresh()
            GroupListIntent.DismissRefreshFailed -> update { it.copy(refreshFailed = false) }
            GroupListIntent.Appeared -> if (appeared) refreshSilently() else appeared = true
            is GroupListInviteSheetIntent -> handleInviteSheet(intent)
            is GroupListConfirmIntent -> handleConfirm(intent)
        }
    }

    private fun handleConfirm(intent: GroupListConfirmIntent) {
        when (intent) {
            is GroupListIntent.OpenConfirm -> {
                val game = state.value.groups.firstOrNull { it.id == intent.groupId }?.nextGame
                if (game?.needsConfirmation != true) return
                update { it.copy(confirmGroupId = intent.groupId, responseFailed = false, responsePlanPaused = false, responseOffline = false) }
            }
            // Com a resposta em voo a folha não fecha: o resultado ainda vai trocar a etiqueta.
            GroupListIntent.CloseConfirm -> if (!state.value.responding) update { it.copy(confirmGroupId = null) }
            is GroupListIntent.Respond -> respond(intent.intent)
        }
    }

    /**
     * Vou / Não vou da folha, como o cartão da Início (`HomeViewModel.respond`): "Vou" lê a presença antes e, em jogo com
     * vagas por posição, abre a escolha da posição sem enviar nada. [GroupListState.responding] liga antes da leitura,
     * então o segundo toque não manda outra resposta (GER-001).
     */
    private fun respond(intent: AttendanceIntent) {
        val current = state.value
        if (current.responding) return
        val group = current.confirmGroup ?: return
        val game = group.nextGame?.takeIf { it.needsConfirmation } ?: return
        if (now().epochSeconds >= game.startsAtEpochSeconds) return
        update { it.copy(responding = true, responseFailed = false, responsePlanPaused = false, responseOffline = false) }
        viewModelScope.launch {
            val groupId = GroupId(group.id)
            if (intent == AttendanceIntent.Confirm) {
                when (val read = attendanceGateway.read(groupId, game.gameId)) {
                    is SaqzResult.Failure -> {
                        showResponseFailure(read.error)
                        return@launch
                    }
                    is SaqzResult.Success -> if (read.value.hasPositions) {
                        update { it.copy(responding = false, confirmGroupId = null) }
                        emit(GroupListEffect.PickPosition(group.id, game.gameId))
                        return@launch
                    }
                }
            }
            val result = attendanceGateway.respond(
                groupId,
                game.gameId,
                SelfAttendanceCommand(Uuid.random().toString(), intent),
            )
            when (result) {
                is SaqzResult.Failure -> showResponseFailure(result.error)
                is SaqzResult.Success -> {
                    SaqzAnalytics.attendanceAnswered("app", intent == AttendanceIntent.Confirm)
                    val answer = result.value.value.attendance.status.toCardAttendance()
                    update { state ->
                        state.copy(
                            responding = false,
                            confirmGroupId = null,
                            groups = state.groups.map { card ->
                                if (card.id == group.id && card.nextGame?.gameId == game.gameId) {
                                    card.copy(nextGame = card.nextGame.copy(attendance = answer))
                                } else {
                                    card
                                }
                            }.sortedForList(),
                        )
                    }
                    load(softRefresh = true)
                }
            }
        }
    }

    // Os mesmos avisos da Início: sem conexão, grupo congelado (plano do organizador parado) e o erro geral. A folha fica
    // aberta e a etiqueta continua "Confirmar".
    private fun showResponseFailure(error: AttendanceError) {
        val paused = error.isPlanPaused()
        update {
            it.copy(
                responding = false,
                responseFailed = !paused,
                responsePlanPaused = paused,
                responseOffline = error.isOffline(),
            )
        }
    }

    private fun handleInviteSheet(intent: GroupListInviteSheetIntent) {
        when (intent) {
            GroupListIntent.OpenInviteSheet -> update { it.copy(inviteSheetOpen = true, inviteLinkInvalid = false) }
            GroupListIntent.CloseInviteSheet -> update {
                it.copy(inviteSheetOpen = false, inviteLink = "", inviteLinkInvalid = false)
            }
            is GroupListIntent.InviteLinkChanged -> update { it.copy(inviteLink = intent.value, inviteLinkInvalid = false) }
            GroupListIntent.SubmitInviteLink -> submitInviteLink()
        }
    }

    /** Sem código no texto a folha aponta o erro e fica aberta; com código, fecha e o resgate segue como deep link. */
    private fun submitInviteLink() {
        val code = InviteLinkParser.parse(state.value.inviteLink)
        if (code == null) {
            update { it.copy(inviteLinkInvalid = true) }
            return
        }
        update { it.copy(inviteSheetOpen = false, inviteLink = "", inviteLinkInvalid = false) }
        emit(GroupListEffect.AcceptInviteCode(code))
    }

    private fun checkCreateGroup() {
        if (checkingPlan) return
        checkingPlan = true
        viewModelScope.launch {
            try {
                emit(
                    if (entitlement.canOpenCreationFlow()) GroupListEffect.OpenCreateGroup
                    else GroupListEffect.OpenPlans,
                )
            } finally {
                checkingPlan = false
            }
        }
    }

    // Com a carga cheia em voo (esqueleto na tela), ela já traz o estado novo: recarregar por cima
    // descartaria a resposta dela e, se a recarga falhasse, o esqueleto ficaria preso.
    private fun refreshSilently() {
        if (!state.value.isLoading) load(softRefresh = true)
    }

    // GER-002: puxar com a lista ainda carregando, ou já atualizando, não dispara outra busca.
    private fun pullRefresh() {
        val current = state.value
        if (current.isLoading || current.isRefreshing) return
        update { it.copy(isRefreshing = true, refreshFailed = false) }
        load(softRefresh = true, pulled = true)
    }

    private fun load(softRefresh: Boolean = false, pulled: Boolean = false) {
        val generation = ++loadGeneration
        update {
            if (softRefresh) {
                it.copy(loadFailed = false, error = null)
            } else {
                it.copy(isLoading = true, loadFailed = false, error = null)
            }
        }
        // Recarga por baixo que falha não derruba a lista; só a puxada avisa.
        val fail: (GroupUiError) -> Unit = { error ->
            when {
                !softRefresh -> showFailure(generation, error)
                pulled && generation == loadGeneration -> update { it.copy(refreshFailed = true) }
            }
        }
        val job = viewModelScope.launch {
            when (val result = athleteGateway.ownProfile()) {
                is SaqzResult.Failure -> fail(result.error.toUiError())
                is SaqzResult.Success -> {
                    val memberships = result.value.memberships.filter(OwnAthleteMembership::active)
                    val loadedCards = mutableListOf<GroupCardUi>()
                    val currentTime = now()
                    for (membership in memberships) {
                        // ponytail: per-group reads until a list endpoint includes game summaries.
                        val group = when (val read = groupGateway.read(membership.groupId)) {
                            is SaqzResult.Success -> read.value.group
                            is SaqzResult.Failure -> {
                                fail(read.error.toUiError())
                                return@launch
                            }
                        }
                        if (generation != loadGeneration) return@launch
                        val games = when (val read = gameGateway.list(membership.groupId)) {
                            is SaqzResult.Success -> read.value
                            is SaqzResult.Failure -> {
                                fail(read.error.toUiError())
                                return@launch
                            }
                        }
                        if (generation != loadGeneration) return@launch
                        val shown = games.shownGame(currentTime)?.first
                        loadedCards += group.toCard(membership).copy(
                            place = shown?.placeLabel() ?: group.profile?.city?.trim()?.takeIf(String::isNotEmpty),
                            nextGame = games.nextGroupCardGame(currentTime),
                        )
                    }
                    if (generation != loadGeneration) return@launch
                    update {
                        it.copy(isLoading = false, loadFailed = false, error = null, groups = loadedCards.sortedForList())
                    }
                }
            }
        }
        // Pelo fim do job: os `return@launch` e o descarte por geração também soltam a bola. Uma
        // carga mais nova que tomou a vez é quem a solta quando terminar.
        job.invokeOnCompletion { if (generation == loadGeneration) update { it.copy(isRefreshing = false) } }
    }

    private fun showFailure(generation: Int, error: GroupUiError) {
        if (generation != loadGeneration) return
        update { it.copy(isLoading = false, loadFailed = true, error = error) }
    }
}

private fun Group.toCard(membership: OwnAthleteMembership) = GroupCardUi(
    id = id.value,
    name = name,
    isAdmin = membership.role != GroupRole.ATHLETE,
    photoUrl = groupPhotoUrl(id.value, version),
)

private fun AttendanceStatus.toCardAttendance(): GroupCardAttendance = when (this) {
    AttendanceStatus.Confirmed -> GroupCardAttendance.Going
    AttendanceStatus.Waitlisted -> GroupCardAttendance.Waitlisted
    AttendanceStatus.Declined -> GroupCardAttendance.Out
}
