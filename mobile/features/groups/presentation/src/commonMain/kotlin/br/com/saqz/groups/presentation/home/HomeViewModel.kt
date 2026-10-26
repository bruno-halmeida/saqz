package br.com.saqz.groups.presentation.home

import br.com.saqz.groups.presentation.photo.groupPhotoUrl
import br.com.saqz.groups.presentation.photo.memberPhoto
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.membership.GroupEntryRequestGateway
import br.com.saqz.domain.onSuccess
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.presentation.attendance.AttendanceRule
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.SelfAttendanceCommand
import br.com.saqz.groups.domain.attendance.VersionedAttendanceMutation
import br.com.saqz.groups.domain.communication.NativeNotificationPort
import br.com.saqz.groups.presentation.communication.NotificationPromptAudience
import br.com.saqz.groups.presentation.communication.NotificationPromptOffer
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.GroupTrialAccessPort
import br.com.saqz.groups.domain.home.HomeAdminGroup
import br.com.saqz.groups.domain.home.HomeAdminReadModel
import br.com.saqz.groups.domain.home.HomeGameToReview
import br.com.saqz.groups.domain.home.HomeGameToSettle
import br.com.saqz.groups.domain.home.HomeGateway
import br.com.saqz.groups.domain.home.HomeMemberGroup
import br.com.saqz.groups.domain.home.HomeMemberReadModel
import br.com.saqz.groups.domain.home.HomeMonthlyCharges
import br.com.saqz.groups.domain.home.HomeNextGame
import br.com.saqz.groups.domain.home.HomeOwnAttendance
import br.com.saqz.groups.domain.home.HomeOwnChargeGroup
import br.com.saqz.groups.domain.home.HomeOwnChargeOldest
import br.com.saqz.groups.domain.home.HomeOwnCharges
import br.com.saqz.groups.domain.home.HomeReadModel
import br.com.saqz.groups.domain.home.HomeUpcomingGame
import br.com.saqz.groups.presentation.game.waitlistBellLabel
import br.com.saqz.groups.presentation.game.gameDateLabel
import br.com.saqz.groups.presentation.game.gamePrioritySentence
import br.com.saqz.groups.presentation.game.gamePriorityShort
import br.com.saqz.groups.presentation.game.gameHeroDisplay
import br.com.saqz.groups.presentation.game.gameHeroMeta
import br.com.saqz.groups.presentation.game.gameShortMonthLabel
import br.com.saqz.groups.presentation.game.gameTimeLabel
import br.com.saqz.groups.presentation.game.gameTimeZone
import br.com.saqz.groups.presentation.game.gameWeekdayLabel
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.isPlanPaused
import br.com.saqz.groups.presentation.AutoRetry
import br.com.saqz.groups.presentation.isOffline
import br.com.saqz.groups.presentation.toUiError
import br.com.saqz.groups.presentation.ui.finance.groupcash.PixUi
import br.com.saqz.groups.port.GroupNowPort
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_admin_hero_priority_closed
import br.com.saqz.groups.resources.home_admin_score_pending
import br.com.saqz.groups.resources.home_admin_groups
import br.com.saqz.groups.resources.home_admin_subtitle
import br.com.saqz.groups.resources.home_admin_things_waiting
import br.com.saqz.groups.resources.home_admin_waiting_entry_requests_meta
import br.com.saqz.groups.resources.home_admin_waiting_monthly_meta
import br.com.saqz.groups.resources.home_admin_waiting_settle
import br.com.saqz.groups.resources.home_admin_waiting_settle_meta
import br.com.saqz.groups.resources.home_confirmed_summary
import br.com.saqz.groups.resources.game_response_deadline_closed
import br.com.saqz.groups.resources.home_game_date_time
import br.com.saqz.groups.resources.home_group_games_played
import br.com.saqz.groups.resources.home_group_meta
import br.com.saqz.groups.resources.home_group_people
import br.com.saqz.groups.resources.home_month_april_long
import br.com.saqz.groups.resources.home_month_august_long
import br.com.saqz.groups.resources.home_month_december_long
import br.com.saqz.groups.resources.home_month_february_long
import br.com.saqz.groups.resources.home_month_january_long
import br.com.saqz.groups.resources.home_month_july_long
import br.com.saqz.groups.resources.home_month_june_long
import br.com.saqz.groups.resources.home_month_march_long
import br.com.saqz.groups.resources.home_month_may_long
import br.com.saqz.groups.resources.home_month_november_long
import br.com.saqz.groups.resources.home_month_october_long
import br.com.saqz.groups.resources.home_month_september_long
import br.com.saqz.groups.resources.home_own_charges_banner
import br.com.saqz.groups.resources.home_own_charges_banner_groups
import br.com.saqz.groups.resources.home_own_charges_cd_banner
import br.com.saqz.groups.resources.home_own_charge_competence_monthly
import br.com.saqz.groups.resources.home_own_charges_count
import br.com.saqz.groups.resources.own_charges_date
import br.com.saqz.groups.resources.own_charges_due
import br.com.saqz.groups.resources.own_charges_due_overdue
import br.com.saqz.groups.resources.own_charges_game
import br.com.saqz.groups.resources.own_charges_guest
import br.com.saqz.groups.resources.own_charges_monthly_unknown
import br.com.saqz.groups.resources.home_upcoming_cd_row
import br.com.saqz.groups.resources.home_upcoming_row_meta
import br.com.saqz.groups.resources.home_upcoming_row_title
import br.com.saqz.groups.resources.home_upcoming_status_going
import br.com.saqz.groups.resources.home_upcoming_status_out
import br.com.saqz.groups.resources.home_upcoming_status_waitlisted
import br.com.saqz.groups.resources.home_weekday_friday
import br.com.saqz.groups.resources.home_weekday_friday_short
import br.com.saqz.groups.resources.home_weekday_monday
import br.com.saqz.groups.resources.home_weekday_monday_short
import br.com.saqz.groups.resources.home_weekday_saturday
import br.com.saqz.groups.resources.home_weekday_saturday_short
import br.com.saqz.groups.resources.home_weekday_sunday
import br.com.saqz.groups.resources.home_weekday_sunday_short
import br.com.saqz.groups.resources.home_weekday_thursday
import br.com.saqz.groups.resources.home_weekday_thursday_short
import br.com.saqz.groups.resources.home_weekday_tuesday
import br.com.saqz.groups.resources.home_weekday_tuesday_short
import br.com.saqz.groups.resources.home_weekday_wednesday
import br.com.saqz.groups.resources.home_weekday_wednesday_short
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Suppress("LargeClass")
class HomeViewModel(
    private val homeGateway: HomeGateway,
    private val athleteGateway: AthleteGateway,
    private val attendanceGateway: AttendanceGateway,
    private val now: GroupNowPort,
    private val notifications: NativeNotificationPort? = null,
    private val trialAccess: GroupTrialAccessPort? = null,
    /** Depois da resposta de presença é a hora de explicar os avisos; sem a porta (teste, web) não há folha. */
    private val notificationPrompt: NotificationPromptOffer? = null,
    /** Cancelar o próprio pedido de entrada; sem a porta (teste) o botão não faz nada. */
    private val entryRequests: GroupEntryRequestGateway? = null,
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {
    private var loadGeneration = 0L
    private var responseGeneration = 0L
    private var pixCopiedGeneration = 0L

    /**
     * Jogos em que o servidor recusou a resposta por prazo vencido (`ATTENDANCE_DEADLINE_PASSED`, backend
     * v.0.0.17). A carga seguinte reconstrói a tela do zero; sem esta memória os botões voltariam a
     * valer e o aviso do prazo voltaria a prometer a lista de espera, até o próximo toque recusado.
     */
    private val lateRefusedGames = mutableSetOf<String>()

    /**
     * Há carga no ar. Existe para a recarga por baixo (VUL-202) não repetir o que já está
     * acontecendo: na abertura do app o `init` dispara a carga e o primeiro `resume` da
     * faixa chega microssegundos depois — sem esta guarda seriam **duas** idas ao
     * `GET /api/me/home` e ao `ownProfile()` na tela de entrada, que é justamente o
     * desperdício que o agregado do Fluxo 6 existe para não ter.
     *
     * Só a recarga por baixo é descartada. `Retry` é pedido explícito da pessoa e passa
     * por cima de qualquer coisa em voo.
     */
    private var loadInFlight = false

    /** Geração da carga que um puxão acompanha — só ela avisa quando falha. */
    private var pulledGeneration = -1L

    /** Erro de rede na carga: tenta de novo sozinha até a rede voltar. */
    private val autoRetry = AutoRetry(viewModelScope) { load() }

    /**
     * A resposta que falhou sem rede deixa o aviso "Sem conexão" na tela; quando a rede volta, uma recarga
     * por baixo que dá certo o apaga. Sem monitor de conectividade, a volta é detectada tentando de novo.
     */
    private val responseRetry = AutoRetry(viewModelScope) { load(softRefresh = true) }

    init {
        load()
    }

    override fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Retry -> {
                autoRetry.reset()
                load()
            }
            HomeIntent.Refresh -> {
                autoRetry.reset()
                load(softRefresh = true)
            }
            HomeIntent.PullRefresh -> pullRefresh()
            is HomeIntent.Respond -> respond(intent.intent)
            HomeIntent.DismissToast -> update { it.copy(toast = null) }
            HomeIntent.OpenGroups -> emit(HomeEffect.OpenGroups)
            HomeIntent.OpenNotifications -> emit(HomeEffect.OpenNotifications)
            is HomeIntent.OpenGroup -> emit(HomeEffect.OpenGroup(intent.groupId))
            is HomeIntent.OpenGame -> emit(HomeEffect.OpenGame(intent.groupId, intent.gameId))
            is HomeIntent.OpenMembers -> emit(HomeEffect.OpenMembers(intent.groupId))
            is HomeIntent.OpenCashbox -> emit(HomeEffect.OpenCashbox(intent.groupId))
            is HomeIntent.OpenGameSettlement -> emit(HomeEffect.OpenGameSettlement(intent.groupId, intent.gameId))
            is HomeIntent.OpenGameEditor -> emit(HomeEffect.OpenGameEditor(intent.groupId))
            is HomeIntent.OpenInvite -> emit(HomeEffect.OpenInvite(intent.groupId))
            // Os dois verbos do aviso de cobrança: copiar a chave (sem recebimento) ou pagar pelo app.
            is HomeIntent.CopyPix, is HomeIntent.PayCharges -> ownChargeAction(intent)
            is HomeCancelEntryIntent -> handleCancelEntry(intent)
            HomeIntent.CreateGroup -> emit(HomeEffect.OpenCreateGroup)
            is HomeInviteSheetIntent -> handleInviteSheet(intent)
        }
    }

    private fun handleCancelEntry(intent: HomeCancelEntryIntent) {
        when (intent) {
            is HomeIntent.RequestCancelEntryRequest -> update { current ->
                current.copy(
                    cancelEntryCandidate = current.member?.pendingRequests
                        ?.firstOrNull { it.groupId == intent.groupId },
                )
            }
            HomeIntent.DismissCancelEntryRequest -> update { it.copy(cancelEntryCandidate = null) }
            HomeIntent.ConfirmCancelEntryRequest -> cancelEntryRequest()
        }
    }

    /** Desiste do pedido de entrada: some da Início na hora; falha mantém o cartão e avisa. */
    private fun cancelEntryRequest() {
        val gateway = entryRequests ?: return
        val candidate = state.value.cancelEntryCandidate ?: return
        if (state.value.cancelingEntry) return
        update { it.copy(cancelingEntry = true, cancelEntryCandidate = null) }
        viewModelScope.launch {
            when (gateway.cancelOwn(GroupId(candidate.groupId))) {
                is SaqzResult.Success -> update { current ->
                    current.copy(
                        cancelingEntry = false,
                        toast = HomeToast.EntryRequestCanceled,
                        member = current.member?.let { member ->
                            member.copy(pendingRequests = member.pendingRequests.filterNot { it.groupId == candidate.groupId })
                        },
                    )
                }
                is SaqzResult.Failure -> update {
                    it.copy(cancelingEntry = false, toast = HomeToast.EntryRequestCancelFailed)
                }
            }
        }
    }

    private fun ownChargeAction(intent: HomeIntent) = when (intent) {
        is HomeIntent.CopyPix -> copyPix(intent.groupId)
        is HomeIntent.PayCharges -> payCharges(intent.groupId)
        else -> Unit
    }

    /** Só grupo que recebe pelo app abre o pagamento (FIN-011); o resto volta cedo. */
    private fun payCharges(groupId: String) {
        state.value.ownCharges?.groups
            ?.firstOrNull { it.groupId == groupId && it.payInApp }
            ?.let { emit(HomeEffect.OpenPayment(it.groupId, it.groupName)) }
    }

    /** Grupo sem chave, ou sem pendência, não emite nada — intent inválido volta cedo. */
    private fun copyPix(groupId: String) {
        val pix = state.value.ownCharges
            ?.groups
            ?.firstOrNull { it.groupId == groupId }
            ?.pix
            ?: return
        emit(HomeEffect.CopyPix(pix.key))
        // Contador monotônico, não igualdade de valor: dois toques seguidos no mesmo grupo
        // não podem deixar o primeiro delay apagar o estado do segundo.
        val generation = ++pixCopiedGeneration
        update { it.copy(pixCopiedGroupId = groupId, toast = HomeToast.PixCopied) }
        viewModelScope.launch {
            delay(PixCopiedDwellMillis)
            if (generation == pixCopiedGeneration) update { it.copy(pixCopiedGroupId = null) }
        }
    }

    /**
     * GER-002: com uma carga já no ar o puxão pega carona nela — a bola mostra a espera e a falha
     * dela avisa —, sem disparar outra busca.
     */
    private fun pullRefresh() {
        if (state.value.isRefreshing) return
        autoRetry.reset()
        update { it.copy(isRefreshing = true) }
        if (loadInFlight) {
            pulledGeneration = loadGeneration
            return
        }
        // Antes do `load`: a falha pode chegar antes de ele voltar.
        pulledGeneration = loadGeneration + 1
        load(softRefresh = true)
    }

    private fun load(softRefresh: Boolean = false) {
        // A guarda é sobre carga concorrente, não sobre "primeira vez": a faixa pode entrar
        // em composição muito depois da carga inicial, com dado velho e nada em voo, e aí a
        // recarga é exatamente o que se quer.
        if (softRefresh && loadInFlight) return
        val generation = ++loadGeneration
        responseGeneration++
        loadInFlight = true
        update {
            if (softRefresh) {
                it.copy(loadFailed = false, error = null)
            } else {
                it.copy(
                    isLoading = true,
                    loadFailed = false,
                    error = null,
                    responding = false,
                    responseFailed = false,
                    responseOffline = false,
                )
            }
        }
        val job = viewModelScope.launch {
            val homeRequest = async { homeGateway.read() }
            val profileRequest = async { athleteGateway.ownProfile() }
            val homeResult = homeRequest.await()
            val profileResult = profileRequest.await()
            if (generation < loadGeneration) return@launch

            when (homeResult) {
                is SaqzResult.Failure -> if (!softRefresh) {
                    showFailure(generation, homeResult.error.toUiError())
                } else {
                    if (generation == pulledGeneration) update { it.copy(toast = HomeToast.RefreshFailed) }
                    retryOfflineResponse()
                }
                is SaqzResult.Success -> when (profileResult) {
                    is SaqzResult.Failure -> if (!softRefresh) {
                        showFailure(generation, profileResult.error.toUiError())
                    } else {
                        if (generation == pulledGeneration) update { it.copy(toast = HomeToast.RefreshFailed) }
                        retryOfflineResponse()
                    }
                    is SaqzResult.Success -> {
                        val member = homeResult.value.member.toUi()
                        if (generation < loadGeneration) return@launch
                        val admin = homeResult.value.admin?.toUi()
                        if (generation < loadGeneration) return@launch
                        val adminSubtitle = admin?.let { deriveAdminSubtitle(it, member.groups.size) }
                        if (generation < loadGeneration) return@launch
                        // Formatar suspende (`getString`): a guarda é re-checada depois de
                        // cada bloco, não só no retorno da rede.
                        val ownCharges = homeResult.value.ownCharges?.toUi()
                        if (generation < loadGeneration) return@launch
                        update {
                            it.copy(
                                isLoading = false,
                                loadFailed = false,
                                error = null,
                                displayName = profileResult.value.displayName.firstName(),
                                home = homeResult.value,
                                member = member.copy(
                                    admin = admin,
                                    adminSubtitle = adminSubtitle,
                                ),
                                responding = false,
                                responseFailed = false,
                                responsePlanPaused = false,
                                responseOffline = false,
                                ownCharges = ownCharges,
                            )
                        }
                        responseRetry.reset()
                        checkOwnerTrial(generation, homeResult.value.member.groups)
                    }
                }
            }
        }
        // Libera pelo fim do job, e não no corpo dele: os `return@launch` da guarda de
        // geração e um cancelamento do escopo também têm que devolver a chave. A checagem
        // impede que uma carga velha destrave enquanto a nova ainda está no ar.
        job.invokeOnCompletion {
            if (generation == loadGeneration) {
                loadInFlight = false
                update { it.copy(isRefreshing = false) }
            }
        }
    }

    /**
     * Dono com o teste encerrado e sem plano não pode marcar jogo nem mexer no grupo, e a Início
     * não dizia nada. Um grupo seu basta: o aviso é da conta, não de cada grupo.
     */
    private suspend fun checkOwnerTrial(generation: Long, groups: List<HomeMemberGroup>) {
        val port = trialAccess ?: return
        val owned = groups.filter { it.role == GroupRole.OWNER }
        val ended = owned.any { group ->
            port.read(group.id)?.let { it.isOwner && it.trialEnded && !it.planActive } == true
        }
        if (generation == loadGeneration) update { it.copy(ownerTrialEnded = ended) }
    }

    private fun handleInviteSheet(intent: HomeInviteSheetIntent) {
        when (intent) {
            HomeIntent.OpenInviteSheet -> update { it.copy(inviteSheetOpen = true, inviteLinkInvalid = false) }
            HomeIntent.CloseInviteSheet -> update {
                it.copy(inviteSheetOpen = false, inviteLink = "", inviteLinkInvalid = false)
            }
            is HomeIntent.InviteLinkChanged -> update { it.copy(inviteLink = intent.value, inviteLinkInvalid = false) }
            HomeIntent.SubmitInviteLink -> submitInviteLink()
        }
    }

    /**
     * O link colado vira código aqui, sem rede: se não houver código, a folha aponta o erro e
     * fica aberta. Com código, a folha fecha e o resgate segue o mesmo caminho do deep link.
     */
    private fun submitInviteLink() {
        val code = InviteLinkParser.parse(state.value.inviteLink)
        if (code == null) {
            update { it.copy(inviteLinkInvalid = true) }
            return
        }
        update { it.copy(inviteSheetOpen = false, inviteLink = "", inviteLinkInvalid = false) }
        emit(HomeEffect.AcceptInviteCode(code))
    }

    /**
     * "Vou" no cartão do próximo jogo. Jogo com vagas por posição pede a escolha da posição na tela própria (DEB-107): lê a
     * presença antes e, havendo posições, abre a tela de posição em vez de responder. Sem posições, responde direto como
     * sempre; com a leitura falhando, não envia nada e mostra o erro de resposta.
     */
    private fun respond(intent: AttendanceIntent) {
        val current = state.value
        val context = responseContext(current, intent) ?: return
        val entering = intent == AttendanceIntent.Confirm && context.game.ownAttendance?.status != AttendanceStatus.Confirmed
        if (!entering) {
            sendResponse(intent)
            return
        }
        viewModelScope.launch {
            when (val read = attendanceGateway.read(GroupId(context.game.groupId.value), context.game.gameId)) {
                is SaqzResult.Success ->
                    if (read.value.hasPositions) emit(HomeEffect.PickPosition(context.game.groupId.value, context.game.gameId))
                    else sendResponse(intent)
                // Sem a leitura não dá para saber se o jogo pede posição: nada é enviado, e o erro de resposta convida a
                // tentar de novo (em jogo 5-1 o servidor escolheria a posição por baixo).
                is SaqzResult.Failure -> update {
                    it.copy(responding = false, responseFailed = true, responseOffline = read.error.isOffline())
                }
            }
        }
    }

    private fun sendResponse(intent: AttendanceIntent) {
        val current = state.value
        val context = responseContext(current, intent) ?: return
        val game = context.game
        val generation = ++responseGeneration
        responseRetry.reset()
        val loadAtStart = loadGeneration
        val optimisticStatus = game.optimisticStatus(intent)
        updateOptimisticAttendance(context, optimisticStatus)
        viewModelScope.launch {
            val result = attendanceGateway.respond(
                GroupId(game.groupId.value),
                game.gameId,
                SelfAttendanceCommand(Uuid.random().toString(), intent),
            ).onSuccess {
                SaqzAnalytics.attendanceAnswered("app", intent == AttendanceIntent.Confirm)
                notifications?.dismissAttendance(game.gameId)
                notificationPrompt?.offer(NotificationPromptAudience.Athlete)
            }
            if (generation >= responseGeneration && loadAtStart >= loadGeneration) {
                applyAttendanceResult(result, context)
            }
        }
    }

    private fun responseContext(current: HomeState, intent: AttendanceIntent): HomeResponseContext? {
        val home = current.home
        val game = home?.member?.nextGame
        val member = current.member
        val gameUi = member?.nextGame
        val startsAt = game?.let { runCatching { Instant.parse(it.startsAt) }.getOrNull() }
        return when {
            home == null -> null
            game == null -> null
            member == null -> null
            gameUi == null -> null
            startsAt == null -> null
            !canRespond(current, game, gameUi, startsAt, intent) -> null
            else -> HomeResponseContext(home, member, game, gameUi)
        }
    }

    private fun canRespond(
        current: HomeState,
        game: br.com.saqz.groups.domain.home.HomeNextGame,
        gameUi: HomeNextGameUi,
        startsAt: Instant,
        intent: AttendanceIntent,
    ): Boolean = when {
        now.now() >= startsAt -> false
        current.responding -> false
        !gameUi.canRespond -> false
        game.ownAttendance?.status == AttendanceStatus.Waitlisted &&
            intent != AttendanceIntent.Decline -> false
        else -> true
    }

    private fun updateOptimisticAttendance(context: HomeResponseContext, status: AttendanceStatus) {
        val optimisticKind = if (status == AttendanceStatus.Waitlisted) context.game.waitlistKind() else null
        update {
            it.copy(
                home = context.home.copy(
                    member = context.home.member.copy(
                        nextGame = context.game.copy(
                            ownAttendance = context.game.ownAttendance?.copy(status = status)
                                ?: HomeOwnAttendance(status, null),
                        ),
                    ),
                ),
                member = context.member.copy(
                    nextGame = context.gameUi.copy(
                        ownAttendance = status,
                        waitlistKind = optimisticKind ?: context.gameUi.waitlistKind,
                    ),
                ),
                responding = true,
                responseFailed = false,
                responseOffline = false,
            )
        }
    }

    private fun applyAttendanceResult(
        result: SaqzResult<VersionedAttendanceMutation, AttendanceError>,
        context: HomeResponseContext,
    ) {
        when (result) {
            is SaqzResult.Success -> {
                val attendance = result.value.value.attendance
                val actualStatus = attendance.status
                val reconciledKind = if (actualStatus == AttendanceStatus.Waitlisted) {
                    context.game.waitlistKind()
                } else {
                    null
                }
                update {
                    it.copy(
                        home = context.home.copy(
                            member = context.home.member.copy(
                                nextGame = context.home.member.nextGame?.copy(
                                    ownAttendance = HomeOwnAttendance(
                                        status = actualStatus,
                                        waitlistPosition = attendance.waitlistPosition,
                                    ),
                                ),
                            ),
                        ),
                        member = context.member.copy(
                            nextGame = context.gameUi.copy(
                                ownAttendance = actualStatus,
                                waitlistKind = reconciledKind ?: context.gameUi.waitlistKind,
                                waitlistPosition = if (actualStatus == AttendanceStatus.Waitlisted) {
                                    attendance.waitlistPosition
                                } else {
                                    null
                                },
                            ),
                        ),
                        responding = false,
                        toast = actualStatus.toToast(reconciledKind),
                    )
                }
                load(softRefresh = true)
            }
            is SaqzResult.Failure -> {
                // Jogo já começado, ou prazo vencido num backend que não aceita resposta tardia: a linha do
                // cartão explica, não o erro genérico. São dois fechamentos: "Em andamento" é só do jogo que
                // começou.
                val started = result.error == AttendanceError.Frozen
                val deadline = result.error == AttendanceError.DeadlinePassed
                val paused = result.error.isPlanPaused()
                if (deadline) lateRefusedGames += context.game.gameId
                update {
                    it.copy(
                        home = context.home,
                        member = when {
                            started -> context.member.copy(nextGame = context.gameUi.copy(responseOpen = false))
                            deadline -> context.member.copy(
                                nextGame = context.gameUi.copy(confirmationOpen = false, closedByDeadline = true),
                            )
                            else -> context.member
                        },
                        responding = false,
                        responseFailed = !started && !deadline && !paused,
                        responsePlanPaused = paused,
                        responseOffline = result.error.isOffline(),
                    )
                }
                if (result.error.isOffline()) responseRetry.schedule()
            }
        }
    }

    private data class HomeResponseContext(
        val home: HomeReadModel,
        val member: HomeMemberUi,
        val game: br.com.saqz.groups.domain.home.HomeNextGame,
        val gameUi: HomeNextGameUi,
    )

    private fun retryOfflineResponse() {
        if (state.value.responseOffline) responseRetry.schedule()
    }

    private fun showFailure(generation: Long, error: GroupUiError) {
        if (generation < loadGeneration) return
        update { it.copy(isLoading = false, loadFailed = true, error = error) }
        if (error == GroupUiError.Network) autoRetry.schedule()
    }

    private suspend fun HomeMemberReadModel.toUi(): HomeMemberUi = HomeMemberUi(
        nextGame = nextGame?.toUi(),
        groups = groups.map { it.toUi() },
        upcomingGames = upcomingGames.map { it.toUi() },
        pendingRequests = pendingEntryRequests.map { HomePendingRequestUi(it.groupId.value, it.groupName) },
    )

    private suspend fun HomeNextGame.toUi(): HomeNextGameUi {
        val zone = gameTimeZone(zoneId)
        val deadlineInstant = runCatching { Instant.parse(confirmationDeadline) }.getOrNull()
        val startsAtLocal = runCatching { Instant.parse(startsAt).toLocalDateTime(zone) }.getOrNull()
        val deadline = deadlineInstant?.toLocalDateTime(zone)
        val dateTime = startsAtLocal?.let {
            getString(
                Res.string.home_game_date_time,
                getString(it.date.dayOfWeek.shortResource()),
                it.date.gameDateLabel(),
                it.gameTimeLabel(),
            )
        } ?: startsAt
        val time = startsAtLocal?.gameTimeLabel() ?: startsAt
        val weekday = startsAtLocal?.let { getString(it.date.dayOfWeek.longResource()) } ?: ""
        val lateRefused = gameId in lateRefusedGames
        // A palavra do servidor vale mais que o relógio do aparelho: prazo recusado é prazo vencido.
        val confirmationOpen = !lateRefused && deadlineInstant?.let { now.now() < it } == true
        val responseOpen = startsAtIsAhead()
        val (display, meta) = heroLabels(startsAtLocal, dateTime, local)
        val ownPosition = ownAttendance
            ?.takeIf { it.status == AttendanceStatus.Waitlisted }
            ?.waitlistPosition
        val previewWaitlistedRows = rosterPreview.waitlisted.mapNotNull { member ->
            val pos = member.waitlistPosition ?: return@mapNotNull null
            HomeWaitlistRowUi(
                name = member.displayName,
                position = pos,
                isSelf = ownPosition != null && pos == ownPosition,
                photo = memberPhoto(groupId.value, member.userId, member.photoTag),
            )
        }.sortedBy { it.position }
        val waitlistedRows = if (
            ownPosition != null && previewWaitlistedRows.none { it.position == ownPosition }
        ) {
            previewWaitlistedRows + HomeWaitlistRowUi(name = "", position = ownPosition, isSelf = true)
        } else {
            previewWaitlistedRows
        }
        val priority = priorityWindow()
        val adminHeroDeadline = deadline?.takeIf { priority }?.let { adminHeroDeadlineLabel(it, confirmationOpen) } ?: ""
        return HomeNextGameUi(
            groupId = groupId.value,
            gameId = gameId,
            groupName = groupName,
            dateTime = dateTime,
            local = local,
            deadline = deadlineText(confirmationOpen, responseOpen, priority, deadline, zone),
            confirmedSummary = if (confirmedCount > capacity) {
                br.com.saqz.groups.presentation.details.overCapacityLabel(confirmedCount, capacity)
            } else {
                getPluralString(Res.plurals.home_confirmed_summary, capacity, confirmedCount, capacity)
            },
            confirmedCount = confirmedCount,
            capacity = capacity,
            rosterNames = rosterPreview.confirmed.map { it.displayName },
            confirmedPhotos = rosterPreview.confirmed.map { memberPhoto(groupId.value, it.userId, it.photoTag) },
            ownAttendance = ownAttendance?.status,
            confirmationOpen = confirmationOpen,
            responseOpen = responseOpen,
            closedByDeadline = lateRefused && responseOpen,
            weekday = weekday,
            time = time,
            waitlistKind = ownAttendance?.status?.let { waitlistKind() },
            promotionMode = promotionMode,
            waitlistPosition = ownPosition,
            confirmedRoster = rosterPreview.confirmed.map { it.displayName },
            waitlistedRoster = waitlistedRows,
            confirmedCountTotal = confirmedCount,
            deadlineBellLabel = deadline?.let { waitlistBellLabel() } ?: "",
            declinedCount = declinedCount,
            pendingCount = pendingCount,
            waitlistCount = waitlistCount,
            adminHeroDeadlineLabel = adminHeroDeadline,
            display = display,
            meta = meta,
        )
    }

    private suspend fun HomeMemberGroup.toUi() = HomeGroupUi(
        id = id.value,
        name = name,
        meta = getString(
            Res.string.home_group_meta,
            getPluralString(Res.plurals.home_group_people, memberCount, memberCount),
            getPluralString(Res.plurals.home_group_games_played, gamesPlayed, gamesPlayed),
        ),
        isAdmin = role == GroupRole.OWNER || role == GroupRole.ADMIN,
        photoUrl = photoVersion?.let { groupPhotoUrl(id.value, it) },
    )

    private suspend fun HomeUpcomingGame.toUi(): HomeUpcomingGameUi {
        val zone = gameTimeZone(zoneId)
        val local = runCatching { Instant.parse(startsAt).toLocalDateTime(zone) }.getOrNull()
        val time = local?.gameTimeLabel() ?: startsAt
        val weekday = local?.date?.dayOfWeek?.gameWeekdayLabel() ?: ""
        val dateLabel = local?.date?.gameDateLabel() ?: startsAt
        val status = when (ownStatus) {
            null -> HomeUpcomingStatus.Pending
            AttendanceStatus.Confirmed -> HomeUpcomingStatus.Going
            AttendanceStatus.Declined -> HomeUpcomingStatus.Out
            AttendanceStatus.Waitlisted -> HomeUpcomingStatus.Waitlisted
        }
        val statusLabel = getString(
            when (status) {
                HomeUpcomingStatus.Pending -> Res.string.home_admin_score_pending
                HomeUpcomingStatus.Going -> Res.string.home_upcoming_status_going
                HomeUpcomingStatus.Out -> Res.string.home_upcoming_status_out
                HomeUpcomingStatus.Waitlisted -> Res.string.home_upcoming_status_waitlisted
            },
        )
        return HomeUpcomingGameUi(
            groupId = groupId.value,
            gameId = gameId,
            day = local?.day?.toString() ?: "",
            month = local?.let { gameShortMonthLabel(it.month.ordinal + 1) } ?: "",
            title = getString(Res.string.home_upcoming_row_title, groupName, time),
            meta = getPluralString(Res.plurals.home_upcoming_row_meta, confirmedCount, weekday, confirmedCount),
            status = status,
            statusLabel = statusLabel,
            contentDescription = getString(Res.string.home_upcoming_cd_row, groupName, dateLabel, time, statusLabel),
        )
    }

    private suspend fun deadlineLabel(deadline: kotlinx.datetime.LocalDateTime?, zone: TimeZone): String =
        deadline?.gamePrioritySentence(now.now().toLocalDateTime(zone).date) ?: ""

    private suspend fun HomeAdminReadModel.toUi(): HomeAdminReadModelUi =
        HomeAdminReadModelUi(groups = groups.map { it.toUi() })

    private suspend fun HomeAdminGroup.toUi(): HomeAdminGroupUi = HomeAdminGroupUi(
        id = id.value,
        name = name,
        entryRequestCount = entryRequestCount,
        monthlyCharges = monthlyCharges.takeIf { it.count > 0 }?.toUi(),
        gameToSettle = gameToSettle?.takeIf { it.pendingCount > 0 }?.toUi(),
        gameToReview = gameToReview?.takeIf { it.reviewCount > 0 }?.toUi(),
    )

    // O mês das mensalidades vem da API (o backend bucketiza pelo
    // `saqz.finance.monthly-charges.zone`); o rótulo deriva desse campo, não do
    // `now` em UTC, para não errar na virada do mês.
    private suspend fun HomeMonthlyCharges.toUi(): HomeMonthlyChargesUi {
        val monthIndex = billingMonth.monthIndexOrNull() ?: (now.now().toLocalDateTime(TimeZone.UTC).month.ordinal + 1)
        return HomeMonthlyChargesUi(
            count = count,
            formattedTotal = formatBrl(totalCents),
            month = gameShortMonthLabel(monthIndex),
        )
    }

    /**
     * VUL-202 — o que **o usuário deve**, do bloco `ownCharges` do VUL-201. `overdue` vem
     * calculado no servidor (no fuso de cobrança do contrato) e é só consumido: recalcular
     * aqui com o relógio do aparelho erraria justamente para quem viaja ou virou o dia.
     *
     * O texto do aviso sai dos agregados do topo, não da soma das linhas — é o mesmo número
     * que o backend usou para decidir que existe pendência.
     */
    private suspend fun HomeOwnCharges.toUi(): HomeOwnChargesUi {
        val total = formatBrl(totalCents)
        val bannerText = if (groupCount > 1) {
            getString(Res.string.home_own_charges_banner_groups, total, groupCount)
        } else {
            getString(Res.string.home_own_charges_banner, total)
        }
        return HomeOwnChargesUi(
            bannerText = bannerText,
            bannerContentDescription = getString(Res.string.home_own_charges_cd_banner, bannerText),
            overdue = groups.any { it.overdue },
            groups = groups.map { it.toUi() },
        )
    }

    private suspend fun HomeOwnChargeGroup.toUi() = HomeOwnChargeGroupUi(
        groupId = groupId.value,
        groupName = groupName,
        competence = when (val competence = oldest) {
            // "Mensalidade de julho": mês por extenso minúsculo, as chaves do VUL-215.
            is HomeOwnChargeOldest.Monthly -> competence.month.monthIndexOrNull()
                ?.let { getString(Res.string.home_own_charge_competence_monthly, getString(it.homeLongMonthResource())) }
                ?: getString(Res.string.own_charges_monthly_unknown)
            is HomeOwnChargeOldest.Game -> competence.guestDisplayName?.let { getString(Res.string.own_charges_guest, it) }
                ?: getString(Res.string.own_charges_game)
        },
        amountLabel = formatBrl(totalCents),
        dueLabel = getString(
            if (overdue) Res.string.own_charges_due_overdue else Res.string.own_charges_due,
            formatCivilDate(nextDueDate),
        ),
        overdue = overdue,
        countLabel = count.takeIf { it > 1 }?.let { getString(Res.string.home_own_charges_count, it) },
        // Com recebimento pelo app a chave pessoal some (FIN-009): o atleta paga pelo "Pagar".
        pix = pixKey?.trim()?.takeIf { it.isNotEmpty() && !receivablesActive }?.let { PixUi(it, pixLabel) },
        payInApp = receivablesActive,
    )

    /** "2026-08-05" → "05/08". Data civil do payload: formata, não converte de instante. */
    private suspend fun formatCivilDate(value: String): String {
        val date = runCatching { LocalDate.parse(value) }.getOrNull() ?: return value
        return getString(
            Res.string.own_charges_date,
            date.day.twoDigits(),
            (date.month.ordinal + 1).twoDigits(),
        )
    }

    private suspend fun HomeGameToSettle.toUi(): HomeGameToSettleUi {
        val zone = gameTimeZone(zoneId)
        val local = runCatching { Instant.parse(startsAt).toLocalDateTime(zone) }.getOrNull()
        val formattedDate = local?.date?.gameDateLabel() ?: startsAt
        return HomeGameToSettleUi(
            gameId = gameId,
            formattedDate = formattedDate,
            diaristCount = pendingCount,
            formattedTotal = formatBrl(totalCents),
        )
    }

    private suspend fun HomeGameToReview.toUi(): HomeGameToReviewUi {
        val zone = gameTimeZone(zoneId)
        val local = runCatching { Instant.parse(startsAt).toLocalDateTime(zone) }.getOrNull()
        return HomeGameToReviewUi(
            gameId = gameId,
            formattedDate = local?.date?.gameDateLabel() ?: startsAt,
            reviewCount = reviewCount,
            otherGameCount = (gameCount - 1).coerceAtLeast(0),
        )
    }

    /**
     * Título e linha meta do hero (VUL-218): "Terça, 19h30" e "28 de julho · {local}".
     * Fora do `toUi()` só para o método não estourar o teto de complexidade do detekt.
     */
    private suspend fun heroLabels(
        startsAtLocal: kotlinx.datetime.LocalDateTime?,
        dateTime: String,
        local: String,
    ): Pair<String, String> {
        startsAtLocal ?: return dateTime to local
        return startsAtLocal.gameHeroDisplay() to startsAtLocal.gameHeroMeta(local)
    }

    private suspend fun adminHeroDeadlineLabel(deadline: kotlinx.datetime.LocalDateTime, open: Boolean): String =
        if (open) {
            deadline.gamePriorityShort()
        } else {
            getString(Res.string.home_admin_hero_priority_closed, deadline.date.gameDateLabel(), deadline.gameTimeLabel())
        }

    private suspend fun deriveAdminSubtitle(admin: HomeAdminReadModelUi, groupCount: Int): String? {
        val pending = admin.totalPendingItems
        if (pending == 0) return null
        return getString(
            Res.string.home_admin_subtitle,
            getPluralString(Res.plurals.home_admin_groups, groupCount, groupCount),
            getPluralString(Res.plurals.home_admin_things_waiting, pending, pending),
        )
    }

    private fun Int.twoDigits() = toString().padStart(2, '0')

    private fun String.firstName() = trim().substringBefore(' ').ifBlank { trim() }

    private fun HomeNextGame.optimisticStatus(intent: AttendanceIntent) = AttendanceRule.predict(
        intent = intent,
        current = ownAttendance?.status,
        membershipType = membershipType,
        mensalistaPriority = priorityWindow(),
        promotionMode = promotionMode,
        deadlinePassed = deadlinePassed(),
        full = confirmedCount >= capacity,
    )

    private fun HomeNextGame.startsAtIsAhead(): Boolean =
        runCatching { Instant.parse(startsAt) }.getOrNull()?.let { now.now() < it } ?: true

    /**
     * O prazo só importa ao atleta como fim da prioridade de mensalista: sem janela de prioridade a linha
     * some. Depois do prazo e antes do jogo, o aviso do seletor explica; a linha só fecha com o jogo.
     */
    private suspend fun deadlineText(
        confirmationOpen: Boolean,
        responseOpen: Boolean,
        priorityWindow: Boolean,
        deadline: kotlinx.datetime.LocalDateTime?,
        zone: TimeZone,
    ): String = when {
        confirmationOpen -> if (priorityWindow) deadlineLabel(deadline, zone) else ""
        responseOpen -> ""
        else -> getString(Res.string.game_response_deadline_closed)
    }

    /** Jogo com prazo no início não tem janela de prioridade de mensalista (DEB-045). */
    private fun HomeNextGame.priorityWindow(): Boolean =
        AttendanceRule.priorityWindow(mensalistaPriority, confirmationDeadline, startsAt)

    private fun HomeNextGame.deadlinePassed(): Boolean =
        runCatching { now.now() > Instant.parse(confirmationDeadline) }.getOrDefault(false)

    /**
     * Ponto único de derivação do tipo de espera: `Avulso` com `mensalistaPriority`
     * é a lista do avulso (6e); o resto é reserva (6b). O hero, o subtítulo do
     * cabeçalho e o `optimisticStatus` usam a mesma regra.
     */
    private fun HomeNextGame.waitlistKind(): HomeWaitlistKind =
        AttendanceRule.waitlistKind(membershipType, priorityWindow(), promotionMode, deadlinePassed())

    private fun AttendanceStatus.toToast(kind: HomeWaitlistKind?) = when (this) {
        AttendanceStatus.Confirmed -> HomeToast.Confirmed
        AttendanceStatus.Declined -> HomeToast.Declined
        AttendanceStatus.Waitlisted -> when (kind) {
            HomeWaitlistKind.Manual -> HomeToast.WaitlistedManual
            HomeWaitlistKind.AfterDeadline -> HomeToast.WaitlistedAfterDeadline
            else -> HomeToast.Waitlisted
        }
    }
}

private fun DayOfWeek.shortResource(): StringResource = when (this) {
    DayOfWeek.MONDAY -> Res.string.home_weekday_monday_short
    DayOfWeek.TUESDAY -> Res.string.home_weekday_tuesday_short
    DayOfWeek.WEDNESDAY -> Res.string.home_weekday_wednesday_short
    DayOfWeek.THURSDAY -> Res.string.home_weekday_thursday_short
    DayOfWeek.FRIDAY -> Res.string.home_weekday_friday_short
    DayOfWeek.SATURDAY -> Res.string.home_weekday_saturday_short
    DayOfWeek.SUNDAY -> Res.string.home_weekday_sunday_short
}

private fun DayOfWeek.longResource(): StringResource = when (this) {
    DayOfWeek.MONDAY -> Res.string.home_weekday_monday
    DayOfWeek.TUESDAY -> Res.string.home_weekday_tuesday
    DayOfWeek.WEDNESDAY -> Res.string.home_weekday_wednesday
    DayOfWeek.THURSDAY -> Res.string.home_weekday_thursday
    DayOfWeek.FRIDAY -> Res.string.home_weekday_friday
    DayOfWeek.SATURDAY -> Res.string.home_weekday_saturday
    DayOfWeek.SUNDAY -> Res.string.home_weekday_sunday
}

// Mês por extenso e minúsculo ("28 de julho"), as chaves `home_month_*_long` do VUL-215.
private fun Int.homeLongMonthResource(): StringResource = when (this) {
    1 -> Res.string.home_month_january_long
    2 -> Res.string.home_month_february_long
    3 -> Res.string.home_month_march_long
    4 -> Res.string.home_month_april_long
    5 -> Res.string.home_month_may_long
    6 -> Res.string.home_month_june_long
    7 -> Res.string.home_month_july_long
    8 -> Res.string.home_month_august_long
    9 -> Res.string.home_month_september_long
    10 -> Res.string.home_month_october_long
    11 -> Res.string.home_month_november_long
    else -> Res.string.home_month_december_long
}

// "YYYY-MM" (formato do backend) → índice 1..12. Devolve null se o formato for
// inesperado; quem chama cai no `now` em UTC como fallback de segurança.
private fun String.monthIndexOrNull(): Int? = split("-").let { parts ->
    if (parts.size != 2) return null
    val month = parts[1].toIntOrNull() ?: return null
    if (month in 1..12) month else null
}

private const val PixCopiedDwellMillis = 2_000L
