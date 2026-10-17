package br.com.saqz.groups.presentation.gamedetail
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.analytics.SaqzAnalytics
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.domain.onSuccess
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthleteRosterEntry
import br.com.saqz.groups.domain.athlete.AthleteRosterFilter
import br.com.saqz.groups.domain.attendance.AddGuestCommand
import br.com.saqz.groups.domain.attendance.AttendanceCapacityCommand
import br.com.saqz.groups.domain.attendance.AttendanceDetail
import br.com.saqz.groups.domain.attendance.AttendanceEntry
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendancePromotionCommand
import br.com.saqz.groups.domain.attendance.AttendanceRoster
import br.com.saqz.groups.domain.attendance.AttendanceRosterMember
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.AttendanceVersionToken
import br.com.saqz.groups.domain.attendance.OverrideAttendanceCommand
import br.com.saqz.groups.domain.attendance.SelfAttendanceCommand
import br.com.saqz.groups.domain.communication.NativeNotificationPort
import br.com.saqz.groups.presentation.communication.NotificationPromptAudience
import br.com.saqz.groups.presentation.communication.NotificationPromptOffer
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.game.GameLifecycleAction
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.group.PromotionMode
import br.com.saqz.groups.presentation.attendance.AttendanceRule
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.GameCapacityLimits
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import br.com.saqz.groups.presentation.isPlanPaused
import br.com.saqz.groups.presentation.AutoRetry
import br.com.saqz.groups.presentation.isOffline
import br.com.saqz.groups.presentation.toUiError
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Suppress("LargeClass", "LongParameterList")
class GameDetailViewModel(
    val groupId: String,
    val gameId: String,
    private val gameGateway: GameGateway,
    private val groupGateway: GroupGateway,
    private val attendanceGateway: AttendanceGateway,
    private val athleteGateway: AthleteGateway,
    /** Resposta pelo app limpa o aviso das 24 h do jogo no aparelho; sem a porta (web, teste) não há o que limpar. */
    private val notifications: NativeNotificationPort? = null,
    private val notificationPrompt: NotificationPromptOffer? = null,
) : MviViewModel<GameDetailState, GameDetailIntent, GameDetailEffect>(GameDetailState()) {
    private var loadGeneration = 0
    private var promotionGeneration = 0
    private var capacityGeneration = 0
    private var responseGeneration = 0
    private var overrideGeneration = 0
    private var versionToken: GameVersionToken? = null
    private var selfId: String? = null

    /** Membros ativos da última carga: é de onde sai a lista de "Colocar alguém no jogo". */
    private var athletes: List<AthleteRosterEntry> = emptyList()

    /**
     * Pedido de `override` que falhou: tentar de novo com a mesma pessoa, ação e motivo reaproveita o `requestId`
     * (o servidor deduplica); qualquer mudança gera um novo.
     */
    private var pendingOverride: Pair<String, String>? = null

    /** Do vínculo e do prazo carregados: o tipo da fila e a re-checagem do prazo no "Vou" dependem deles. */
    private var membershipType: AthleteMembershipType? = null
    private var confirmationDeadline: Instant? = null
    private var startsAt: Instant? = null
    private var groupPromotionMode: PromotionMode = PromotionMode.FIFO

    /**
     * O servidor recusou a resposta por prazo vencido (`ATTENDANCE_DEADLINE_PASSED`, backend v.0.0.17). A
     * recarga reconstrói o cabeçalho do zero; sem esta memória os botões voltariam a valer e o aviso do
     * prazo voltaria a prometer a lista de espera, até o próximo toque recusado.
     */
    private var deadlineRefused = false

    /**
     * Carga no ar. Só a recarga por baixo ([GameDetailIntent.Refresh]) é descartada por ela: o
     * primeiro `resume` do Root chega logo depois do `init { load() }`. `Retry` passa por cima.
     */
    private var loadInFlight = false

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
    override fun handleIntent(intent: GameDetailIntent) {
        when (intent) {
            GameDetailIntent.Retry, GameDetailIntent.Refresh -> reload(intent)
            GameDetailIntent.Edit -> emit(GameDetailEffect.OpenEditor)
            GameDetailIntent.OpenSettlement -> if (
                state.value.isAdmin && state.value.header?.statusTone == GameDetailStatusTone.Completed
            ) {
                emit(GameDetailEffect.OpenSettlement)
            }
            GameDetailIntent.RequestCancel -> if (state.value.header?.statusTone == GameDetailStatusTone.Published) {
                update { it.copy(cancelDialogOpen = true, cancelFailed = false) }
            }
            GameDetailIntent.DismissCancel -> if (!state.value.cancelling) {
                update { it.copy(cancelDialogOpen = false, cancelFailed = false) }
            }
            GameDetailIntent.ConfirmCancel -> cancel()
            GameDetailIntent.Publish -> publish()
            is GameDetailIntent.Promote -> promote(intent.memberId, intent.reason, intent.guestSeq)
            GameDetailIntent.OpenCapacitySheet -> openCapacitySheet()
            is GameDetailIntent.UpdateCapacity -> update {
                it.copy(capacityDraft = intent.value.coerceIn(MIN_CAPACITY, MAX_CAPACITY), capacityFailed = false)
            }
            GameDetailIntent.SaveCapacity -> saveCapacity()
            GameDetailIntent.DismissCapacitySheet -> if (!state.value.savingCapacity) {
                update { it.copy(capacitySheetOpen = false, capacityFailed = false) }
            }
            else -> onAttendanceIntent(intent)
        }
    }

    /**
     * Ramos da presença de quem olha: a resposta e o convidado dela (que só habilita depois do "Vou").
     * Extraídos para manter a complexidade ciclomática de [onIntent] no teto.
     */
    private fun onAttendanceIntent(intent: GameDetailIntent): Boolean {
        when (intent) {
            is GameDetailIntent.Respond -> respond(intent.intent)
            GameDetailIntent.OpenGuestSheet -> if (state.value.guest.enabled) {
                updateGuest { it.copy(sheetOpen = true, name = "", addFailed = false) }
            }
            is GameDetailIntent.UpdateGuestName -> updateGuest {
                it.copy(name = intent.value.take(MAX_GUEST_NAME), addFailed = false)
            }
            GameDetailIntent.SubmitGuest -> submitGuest()
            GameDetailIntent.DismissGuestSheet -> if (!state.value.guest.adding) {
                updateGuest { it.copy(sheetOpen = false, addFailed = false) }
            }
            is GameDetailIntent.RequestRemoveGuest -> requestRemoveGuest(intent.rowId)
            GameDetailIntent.ConfirmRemoveGuest -> confirmRemoveGuest()
            GameDetailIntent.DismissRemoveGuest -> if (!state.value.guest.removing) {
                updateGuest { it.copy(removal = null, removeFailed = false) }
            }
            GameDetailIntent.DismissGuestNotice -> updateGuest { it.copy(noticeName = null) }
            else -> return onOrganizerIntent(intent)
        }
        return true
    }

    /** "Tirar" e "Colocar alguém no jogo" do gestor (DEB-017). */
    private fun onOrganizerIntent(intent: GameDetailIntent): Boolean {
        when (intent) {
            is GameDetailIntent.RequestRemoveMember -> requestRemoveMember(intent.rowId)
            GameDetailIntent.ToggleRemoveReason -> updateOrganizer { organizer ->
                organizer.copy(removal = organizer.removal?.let { it.copy(reasonOpen = !it.reasonOpen) })
            }
            is GameDetailIntent.UpdateRemoveReason -> updateOrganizer { organizer ->
                organizer.copy(
                    removal = organizer.removal?.copy(reason = intent.value.take(MAX_OVERRIDE_REASON)),
                    removeFailure = null,
                )
            }
            is GameDetailIntent.ConfirmRemoveMember -> confirmRemoveMember(intent.defaultReason)
            GameDetailIntent.DismissRemoveMember -> if (!state.value.organizer.removing) {
                updateOrganizer { it.copy(removal = null, removeFailure = null) }
            }
            GameDetailIntent.OpenPlaceSheet -> openPlaceSheet()
            is GameDetailIntent.SelectPlaceCandidate -> selectPlaceCandidate(intent.memberId)
            GameDetailIntent.TogglePlaceReason -> updateOrganizer { it.copy(placeReasonOpen = !it.placeReasonOpen) }
            is GameDetailIntent.UpdatePlaceReason -> updateOrganizer {
                it.copy(placeReason = intent.value.take(MAX_OVERRIDE_REASON), placeFailure = null)
            }
            is GameDetailIntent.ConfirmPlaceMember -> confirmPlaceMember(intent.defaultReason)
            GameDetailIntent.DismissPlaceSheet -> if (!state.value.organizer.placing) {
                updateOrganizer {
                    it.copy(
                        placeOpen = false,
                        candidates = emptyList(),
                        selectedId = null,
                        placeFailure = null,
                        placeReasonOpen = false,
                        placeReason = "",
                    )
                }
            }
            else -> return false
        }
        return true
    }
    /**
     * `Retry` recarrega por inteiro. `Refresh` é a volta à tela: recarga por baixo, e o primeiro,
     * logo após o `init`, é descartado.
     */
    private fun reload(intent: GameDetailIntent) {
        autoRetry.reset()
        if (intent is GameDetailIntent.Retry) load() else if (!isFirstAppearance()) load(softRefresh = true)
    }


    private fun load(softRefresh: Boolean = false) {
        // Recarga por baixo (volta à tela): não repete carga em voo nem atropela escrita ou folha aberta.
        if (softRefresh && (loadInFlight || state.value.busy)) return
        val generation = ++loadGeneration
        loadInFlight = true
        if (!softRefresh) {
            promotionGeneration++
            capacityGeneration++
            overrideGeneration++
            update { it.copy(isLoading = true, loadFailed = false, error = null) }
        }
        val job = viewModelScope.launch {
            val gameResult = gameGateway.read(GroupId(groupId), gameId)
            if (generation != loadGeneration) return@launch
            when (gameResult) {
                is SaqzResult.Failure -> showFailure(generation, gameResult.error.toUiError(), softRefresh)
                is SaqzResult.Success -> {
                    versionToken = gameResult.value.version
                    val groupResult = groupGateway.read(GroupId(groupId))
                    if (generation != loadGeneration) return@launch
                    when (groupResult) {
                        is SaqzResult.Failure -> showFailure(generation, groupResult.error.toUiError(), softRefresh)
                        is SaqzResult.Success -> loadAttendance(
                            generation,
                            gameResult.value.game,
                            groupResult.value.group.role != GroupRole.ATHLETE,
                            groupResult.value.group.gameConfig.mensalistaPriority,
                            groupResult.value.group.gameConfig.promotionMode,
                            softRefresh,
                        )
                    }
                }
            }
        }
        // Libera pelo fim do job: os `return` da guarda de geração e o cancelamento do escopo também
        // devolvem a chave, e a checagem impede que uma carga velha destrave a nova.
        job.invokeOnCompletion { if (generation == loadGeneration) loadInFlight = false }
    }

    /**
     * Escrita em voo ou folha/diálogo aberto: a recarga por baixo espera a próxima volta. Recarregar
     * agora descartaria o resultado da escrita (guarda de geração) ou fecharia a folha no meio.
     */
    private val GameDetailState.busy: Boolean
        get() {
            val writing = cancelling || publishing || savingCapacity || promotingMemberId != null || response.responding
            val interacting = cancelDialogOpen || capacitySheetOpen || guest.sheetOpen || guest.removal != null ||
                organizer.removal != null || organizer.placeOpen
            return writing || interacting || guest.adding || guest.removing || organizer.removing || organizer.placing
        }

    private suspend fun loadAttendance(
        generation: Int,
        game: Game,
        isAdmin: Boolean,
        mensalistaPriority: Boolean,
        promotionMode: PromotionMode,
        softRefresh: Boolean,
    ) {
        groupPromotionMode = promotionMode
        val detailResult = attendanceGateway.read(GroupId(groupId), gameId)
        if (generation != loadGeneration) return
        val rosterResult = attendanceGateway.roster(GroupId(groupId), gameId)
        if (generation != loadGeneration) return
        val athletesResult = athleteGateway.roster(GroupId(groupId), AthleteRosterFilter())
        if (generation != loadGeneration) return
        val profileResult = athleteGateway.ownProfile()
        if (generation != loadGeneration) return
        when {
            detailResult is SaqzResult.Failure -> showFailure(generation, detailResult.error.toUiError(), softRefresh)
            rosterResult is SaqzResult.Failure -> showFailure(generation, rosterResult.error.toUiError(), softRefresh)
            athletesResult is SaqzResult.Failure -> showFailure(generation, athletesResult.error.toUiError(), softRefresh)
            else -> {
                val detail = (detailResult as SaqzResult.Success).value
                val roster = (rosterResult as SaqzResult.Success).value
                val byId = (athletesResult as SaqzResult.Success).value.associateBy(AthleteRosterEntry::userId)
                // Quem ainda não respondeu não tem presença: o perfil diz quem é, e o gestor não se tira da lista.
                selfId = detail.ownAttendance?.memberId ?: (profileResult as? SaqzResult.Success)?.value?.userId
                athletes = (athletesResult as SaqzResult.Success).value
                // Só diz o tipo da fila de espera: sem o vínculo a explicação cai na da reserva.
                membershipType = (profileResult as? SaqzResult.Success)?.value?.memberships
                    ?.firstOrNull { it.groupId == GroupId(groupId) }?.membershipType
                confirmationDeadline = runCatching { Instant.parse(game.confirmationDeadline) }.getOrNull()
                startsAt = runCatching { Instant.parse(game.startsAt) }.getOrNull()
                val header = game.toHeader()
                val open = header.confirmationOpen
                val tone = header.statusTone
                update {
                    it.copy(
                        isLoading = false,
                        loadFailed = false,
                        error = null,
                        header = header.copy(availableSpots = detail.availableSpots),
                        attendance = detail.toAttendance(),
                        confirmedRoster = roster.confirmed.map { member ->
                            member.toConfirmed(byId[member.memberId], isAdmin, open, tone)
                        },
                        waitlist = roster.waitlisted.map { member ->
                            member.toWaitlist(byId[member.memberId], isAdmin, open, tone)
                        },
                        guest = guestUi(game, detail, open, it.guest),
                        response = responseUi(tone == GameDetailStatusTone.Published, detail.ownAttendance, roster, mensalistaPriority),
                        mensalistaPriority = mensalistaPriority,
                        promotionMode = promotionMode,
                        isAdmin = isAdmin,
                        promotingMemberId = null,
                        promotionFailed = false,
                        organizer = GameOrganizerUi(),
                        capacitySheetOpen = false,
                        savingCapacity = false,
                        capacityFailed = false,
                    )
                }
                responseRetry.reset()
            }
        }
    }

    /** Jogo publicado e ainda por começar: depois do início ninguém sobe da espera (DEB-018). */
    private fun GameDetailState.canPromoteNow() =
        header?.statusTone == GameDetailStatusTone.Published && header.gameStarted.not()

    private fun promote(memberId: String, reason: String, guestSeq: Int) {
        val current = state.value
        if (
            !current.isAdmin ||
            !current.canPromoteNow() ||
            current.promotionMode != PromotionMode.MANUAL ||
            current.promotingMemberId != null
        ) return
        val rowId = if (guestSeq > 0) "$memberId#$guestSeq" else memberId
        val previousWaitlist = current.waitlist
        val previousAttendance = current.attendance
        if (previousWaitlist.none { it.id == rowId }) return
        val loadAtStart = loadGeneration
        val generation = ++promotionGeneration
        update {
            it.copy(
                waitlist = it.waitlist.filterNot { member -> member.id == rowId },
                attendance = it.attendance?.let { attendance ->
                    attendance.copy(
                        confirmed = attendance.confirmed + 1,
                        availableSpots = (attendance.availableSpots - 1).coerceAtLeast(0),
                    )
                },
                promotingMemberId = rowId,
                promotionFailed = false,
            )
        }
        viewModelScope.launch {
            val result = attendanceGateway.promote(
                GroupId(groupId),
                gameId,
                AttendancePromotionCommand(Uuid.random().toString(), memberId, reason, guestSeq),
            )
            if (!isCurrent(Operation.Promotion, generation, loadAtStart)) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    val detail = result.value.value.detail.toAttendance()
                    update {
                        it.copy(
                            attendance = detail,
                            header = it.header?.copy(availableSpots = detail.availableSpots),
                            promotingMemberId = null,
                            promotionFailed = false,
                        )
                    }
                    refreshRoster(Operation.Promotion, generation, loadAtStart)
                }
                is SaqzResult.Failure -> update {
                    it.copy(
                        waitlist = previousWaitlist,
                        attendance = previousAttendance,
                        promotingMemberId = null,
                        promotionFailed = true,
                    )
                }
            }
        }
    }

    private fun openCapacitySheet() {
        if (!state.value.isAdmin || state.value.header?.statusTone != GameDetailStatusTone.Published) return
        if (state.value.header?.gameStarted == true) return
        val capacity = state.value.attendance?.capacity ?: return
        // Jogo antigo com 2 ou 3 vagas abre a folha no mínimo de hoje; salvar grava o mínimo.
        update { it.copy(capacitySheetOpen = true, capacityDraft = capacity.coerceAtLeast(MIN_CAPACITY), capacityFailed = false) }
    }

    private fun saveCapacity() {
        val current = state.value
        if (current.savingCapacity) return
        val version = versionToken ?: return
        val previousAttendance = current.attendance
        val previousHeader = current.header
        val loadAtStart = loadGeneration
        val generation = ++capacityGeneration
        val capacity = current.capacityDraft
        update {
            it.copy(
                savingCapacity = true,
                capacityFailed = false,
                attendance = it.attendance?.let { attendance ->
                    attendance.copy(
                        capacity = capacity,
                        availableSpots = (capacity - attendance.confirmed).coerceAtLeast(0),
                    )
                },
                header = it.header?.copy(
                    availableSpots = (capacity - (it.attendance?.confirmed ?: 0)).coerceAtLeast(0),
                ),
            )
        }
        viewModelScope.launch {
            val result = attendanceGateway.capacity(
                GroupId(groupId),
                gameId,
                AttendanceVersionToken(version.value),
                AttendanceCapacityCommand(Uuid.random().toString(), capacity),
            )
            if (!isCurrent(Operation.Capacity, generation, loadAtStart)) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    versionToken = GameVersionToken(result.value.version.value)
                    update {
                        it.copy(
                            savingCapacity = false,
                            capacitySheetOpen = false,
                            capacityFailed = false,
                            attendance = result.value.value.detail.toAttendance(),
                            header = it.header?.copy(availableSpots = result.value.value.detail.availableSpots),
                        )
                    }
                    if (result.value.value.promotedCount > 0) {
                        refreshRoster(Operation.Capacity, generation, loadAtStart)
                    }
                }
                is SaqzResult.Failure -> if (result.error == AttendanceError.Conflict) {
                    update { it.copy(savingCapacity = false, capacitySheetOpen = false, capacityFailed = false) }
                    load()
                } else {
                    update {
                        it.copy(
                            savingCapacity = false,
                            attendance = previousAttendance,
                            header = previousHeader,
                            capacityFailed = true,
                        )
                    }
                }
            }
        }
    }

    private fun refreshRoster(operation: Operation, generation: Int, loadAtStart: Int) {
        viewModelScope.launch {
            val rosterResult = attendanceGateway.roster(GroupId(groupId), gameId)
            val athletesResult = athleteGateway.roster(GroupId(groupId), AthleteRosterFilter())
            if (!isCurrent(operation, generation, loadAtStart)) return@launch
            // Depois do "Vou" a resposta já valeu e o placar já mudou: se só a lista falha, a tela fica como está.
            val quiet = operation == Operation.Response
            when {
                rosterResult is SaqzResult.Failure -> showFailure(loadAtStart, rosterResult.error.toUiError(), quiet)
                athletesResult is SaqzResult.Failure -> showFailure(loadAtStart, athletesResult.error.toUiError(), quiet)
                else -> {
                    val roster = (rosterResult as SaqzResult.Success).value
                    athletes = (athletesResult as SaqzResult.Success).value
                    val byId = athletes.associateBy(AthleteRosterEntry::userId)
                    val isAdmin = state.value.isAdmin
                    val open = state.value.header?.confirmationOpen == true
                    val tone = state.value.header?.statusTone
                    update {
                        it.copy(
                            confirmedRoster = roster.confirmed.map { member ->
                                member.toConfirmed(byId[member.memberId], isAdmin, open, tone)
                            },
                            waitlist = roster.waitlisted.map { member ->
                                member.toWaitlist(byId[member.memberId], isAdmin, open, tone)
                            },
                            response = it.response.reconcile(roster, it.mensalistaPriority),
                        )
                    }
                }
            }
        }
    }

    private fun isCurrent(operation: Operation, generation: Int, loadAtStart: Int): Boolean =
        generation == when (operation) {
            Operation.Promotion -> promotionGeneration
            Operation.Capacity -> capacityGeneration
            Operation.Response -> responseGeneration
            Operation.Override -> overrideGeneration
        } && loadAtStart == loadGeneration

    private enum class Operation { Promotion, Capacity, Response, Override }

    /**
     * "Vou"/"Não vou" de quem olha, com a mesma regra do hero da Início e do grupo: o status muda na
     * hora (previsão) e o servidor tem a palavra final. Quem já está na fila só pode sair dela.
     */
    private fun respond(intent: AttendanceIntent) {
        val current = state.value
        val response = current.response
        if (!current.canRespond(intent)) return
        if (!startIsAhead()) {
            closeStarted()
            return
        }
        val loadAtStart = loadGeneration
        val generation = ++responseGeneration
        responseRetry.reset()
        val optimistic = current.optimisticStatus(intent)
        update {
            it.copy(
                response = it.response.copy(
                    status = optimistic,
                    waitlistKind = if (optimistic == AttendanceStatus.Waitlisted) waitlistKind(it.mensalistaPriority) else null,
                    waitlistPosition = if (optimistic == AttendanceStatus.Waitlisted) it.response.waitlistPosition else null,
                    responding = true,
                    failed = false,
                    planPaused = false,
                    offline = false,
                ),
            )
        }
        viewModelScope.launch {
            val result = attendanceGateway.respond(
                GroupId(groupId),
                gameId,
                SelfAttendanceCommand(Uuid.random().toString(), intent),
            ).onSuccess {
                SaqzAnalytics.attendanceAnswered("app", intent == AttendanceIntent.Confirm)
                notifications?.dismissAttendance(gameId)
                notificationPrompt?.offer(NotificationPromptAudience.Athlete)
            }
            if (!isCurrent(Operation.Response, generation, loadAtStart)) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    val mutation = result.value.value
                    selfId = mutation.attendance.memberId
                    applyResponse(mutation.attendance, mutation.detail)
                    refreshRoster(Operation.Response, generation, loadAtStart)
                }
                is SaqzResult.Failure -> {
                    val paused = result.error.isPlanPaused()
                    // Dois fechamentos: o jogo que começou ("Em andamento") e o prazo que o servidor não
                    // perdoa. Nenhum é "tente de novo": a tela explica e os botões desabilitam.
                    val started = result.error == AttendanceError.Frozen
                    val deadline = result.error == AttendanceError.DeadlinePassed
                    update {
                        it.copy(
                            response = response.copy(
                                responding = false,
                                failed = !paused && !started && !deadline,
                                planPaused = paused,
                                offline = result.error.isOffline(),
                            ),
                        )
                    }
                    if (result.error.isOffline()) responseRetry.schedule()
                    if (started) closeStarted()
                    if (deadline) closeByDeadline()
                }
            }
        }
    }

    /** Quem já está na fila só pode sair dela: um segundo "Vou" não muda nada no servidor. */
    private fun GameDetailState.canRespond(intent: AttendanceIntent): Boolean {
        val queuedAgain = response.status == AttendanceStatus.Waitlisted && intent == AttendanceIntent.Confirm
        return response.visible && header?.canRespond == true && !response.responding && !queuedAgain
    }

    /** O que a resposta aceita já traz: placar, vagas, a própria linha e o "Levar convidado" (só quem vai pode levar). */
    private fun applyResponse(entry: AttendanceEntry, detail: AttendanceDetail) = update {
        val open = it.header?.confirmationOpen == true
        it.copy(
            attendance = detail.toAttendance(),
            header = it.header?.copy(availableSpots = detail.availableSpots),
            response = responseUi(it.response.visible, entry, null, it.mensalistaPriority),
            guest = it.guest.withAccess(open, going = entry.status != AttendanceStatus.Declined),
        )
    }

    /** Jogo começado (ou congelado): os botões desabilitam, o selo vira "Em andamento" e o convidado fecha junto. */
    private fun closeStarted() = update {
        it.copy(
            header = it.header?.copy(confirmationOpen = false, responseOpen = false),
            guest = it.guest.copy(enabled = false, hint = GameGuestHint.Closed),
        )
    }

    /**
     * O servidor recusou a resposta por prazo vencido: os botões desabilitam e o convidado fecha junto, mas o
     * jogo não começou. O selo segue "Prazo encerrado", e a tela diz que as confirmações encerraram.
     */
    private fun closeByDeadline() {
        deadlineRefused = true
        update {
            it.copy(
                header = it.header?.let { header ->
                    header.copy(confirmationOpen = false, closedByDeadline = header.responseOpen)
                },
                guest = it.guest.copy(enabled = false, hint = GameGuestHint.Closed),
            )
        }
    }

    /** Re-checa o relógio: o `confirmationOpen` do cabeçalho é de quando a tela carregou. */
    private fun deadlineIsOpen(): Boolean = confirmationDeadline?.let { it > Clock.System.now() } ?: true

    /** Depois do prazo o "Vou" ainda vale (entra pela espera); só o início do jogo fecha a resposta. */
    private fun startIsAhead(): Boolean = startsAt?.let { it > Clock.System.now() } ?: true

    // Recusar é sempre recusa; quem já está confirmado continua; avulso sob prioridade de mensalista
    // e jogo lotado caem na fila. O servidor confirma (mesma previsão do hero da Início).
    private fun GameDetailState.optimisticStatus(intent: AttendanceIntent): AttendanceStatus = when {
        intent == AttendanceIntent.Decline -> AttendanceStatus.Declined
        response.status == AttendanceStatus.Confirmed -> AttendanceStatus.Confirmed
        !AttendanceRule.entersDirectly(membershipType, mensalistaPriority, promotionMode, !deadlineIsOpen()) ->
            AttendanceStatus.Waitlisted
        attendance?.let { it.confirmed >= it.capacity } == true -> AttendanceStatus.Waitlisted
        else -> AttendanceStatus.Confirmed
    }

    /** Ponto único do tipo de espera, com a regra da Início: avulso + prioridade ⇒ lista do avulso. */
    private fun waitlistKind(mensalistaPriority: Boolean): HomeWaitlistKind =
        AttendanceRule.waitlistKind(membershipType, mensalistaPriority, groupPromotionMode, !deadlineIsOpen())

    private fun responseUi(
        published: Boolean,
        own: AttendanceEntry?,
        roster: AttendanceRoster?,
        mensalistaPriority: Boolean,
    ): GameDetailResponseUi {
        val queued = own?.status == AttendanceStatus.Waitlisted
        return GameDetailResponseUi(
            visible = published,
            status = own?.status,
            waitlistKind = if (queued) waitlistKind(mensalistaPriority) else null,
            waitlistPosition = if (queued) own.queuePosition(roster) else null,
        )
    }

    private fun AttendanceEntry.queuePosition(roster: AttendanceRoster?): Long? = roster?.waitlisted
        ?.indexOfFirst { !it.isGuest && it.memberId == memberId }
        ?.takeIf { it >= 0 }
        ?.let { it + 1L }
        ?: waitlistPosition

    /**
     * A fila é do servidor: depois de uma lista nova, quem olha fica confirmado ou com a posição dele.
     * Com a própria resposta em voo a lista é anterior a ela e não manda no status.
     */
    private fun GameDetailResponseUi.reconcile(roster: AttendanceRoster, mensalistaPriority: Boolean): GameDetailResponseUi {
        val id = selfId
        if (id == null || status == null || responding) return this
        val position = roster.waitlisted.indexOfFirst { !it.isGuest && it.memberId == id }
        return when {
            roster.confirmed.any { !it.isGuest && it.memberId == id } ->
                copy(status = AttendanceStatus.Confirmed, waitlistKind = null, waitlistPosition = null)
            position >= 0 -> copy(
                status = AttendanceStatus.Waitlisted,
                waitlistKind = waitlistKind(mensalistaPriority),
                waitlistPosition = position + 1L,
            )
            else -> this
        }
    }
    private fun cancel() {
        if (state.value.cancelling) return
        val token = versionToken ?: return
        update { it.copy(cancelling = true, cancelFailed = false) }
        viewModelScope.launch {
            val result = gameGateway.lifecycle(GroupId(groupId), gameId, token, GameLifecycleAction.Cancel)
            when (result) {
                is SaqzResult.Success -> {
                    versionToken = result.value.version
                    update {
                        it.copy(cancelling = false, cancelDialogOpen = false, header = result.value.game.toHeader())
                    }
                    emit(GameDetailEffect.Cancelled)
                }
                is SaqzResult.Failure -> if (result.error is GameError.Conflict) {
                    update { it.copy(cancelling = false, cancelDialogOpen = false, cancelFailed = false) }
                    load()
                } else {
                    update { it.copy(cancelling = false, cancelFailed = true) }
                }
            }
        }
    }
    /**
     * Rascunho vira jogo publicado: é quando o atleta passa a vê-lo. Os jogos da recorrência nascem
     * assim, e o organizador publica um a um. Convidado, fila e presenças dependem do status, então
     * depois recarrega tudo em vez de remendar o estado.
     */
    private fun publish() {
        val current = state.value
        if (!current.isAdmin || current.header?.statusTone != GameDetailStatusTone.Draft || current.publishing) return
        val token = versionToken ?: return
        update { it.copy(publishing = true, publishFailed = false) }
        viewModelScope.launch {
            val result = gameGateway.lifecycle(GroupId(groupId), gameId, token, GameLifecycleAction.Publish)
            when (result) {
                is SaqzResult.Success -> {
                    versionToken = result.value.version
                    update { it.copy(publishing = false, header = result.value.game.toHeader()) }
                    load()
                }
                is SaqzResult.Failure -> if (result.error is GameError.Conflict) {
                    update { it.copy(publishing = false) }
                    load()
                } else {
                    update { it.copy(publishing = false, publishFailed = true) }
                }
            }
        }
    }

    /** Por baixo ([softRefresh]) a falha não troca a tela por erro: o conteúdo fica e a próxima volta tenta de novo. */
    private fun showFailure(generation: Int, error: GroupUiError, softRefresh: Boolean = false) {
        if (generation != loadGeneration) return
        if (softRefresh) {
            if (state.value.response.offline) responseRetry.schedule()
            return
        }
        update { it.copy(isLoading = false, loadFailed = true, error = error) }
        if (error == GroupUiError.Network) autoRetry.schedule()
    }
    private fun Game.toHeader(): GameDetailHeaderUi {
        val zone = runCatching { TimeZone.of(zoneId) }.getOrElse { TimeZone.UTC }
        val local = runCatching { Instant.parse(startsAt) }.getOrNull()?.toLocalDateTime(zone)
        val dateTime = if (local != null) {
            "${formatDatePtBr(local.date)} · ${local.hour.pad()}h${local.minute.pad()}"
        } else {
            title
        }
        val deadlineLocal = runCatching { Instant.parse(confirmationDeadline) }
            .getOrNull()?.toLocalDateTime(zone)
        val deadlineDateDiffers = deadlineLocal?.date != local?.date
        val deadline = deadlineLocal?.let {
            val date = if (deadlineDateDiffers) "${formatDatePtBr(it.date)} · " else ""
            "$date${it.hour.pad()}h${it.minute.pad()}"
        } ?: confirmationDeadline
        val venueLine = listOfNotNull(venue.name, venue.court).joinToString(" — ")
        val responseOpen = status == GameStatus.Published &&
            (runCatching { Instant.parse(startsAt) > Clock.System.now() }.getOrDefault(true))
        return GameDetailHeaderUi(
            statusTone = status.toTone(),
            confirmationDeadline = deadline,
            confirmationDeadlineWeekday = deadlineLocal?.date
                ?.takeIf { deadlineDateDiffers }
                ?.let { GroupWeekday.entries[it.dayOfWeek.ordinal] },
            weekday = local?.let { GroupWeekday.entries[it.date.dayOfWeek.ordinal] },
            dateTime = dateTime,
            venue = venueLine.ifBlank { venue.name },
            durationMinutes = durationMinutes,
            availableSpots = availableSpots,
            // A palavra do servidor vale mais que o relógio do aparelho: prazo recusado é prazo vencido.
            confirmationOpen = status == GameStatus.Published && !deadlineRefused &&
                (deadlineLocal?.let { it.toInstant(zone) > Clock.System.now() } ?: true),
            responseOpen = responseOpen,
            closedByDeadline = deadlineRefused && responseOpen,
        )
    }
    private fun AttendanceDetail.toAttendance() = GameDetailAttendanceUi(
        confirmed = confirmedCount,
        capacity = capacity,
        availableSpots = availableSpots,
        declined = declinedCount,
        pending = pendingCount,
    )

    private fun AttendanceRosterMember.toConfirmed(
        athlete: AthleteRosterEntry?,
        isAdmin: Boolean,
        open: Boolean,
        tone: GameDetailStatusTone?,
    ) = GameDetailConfirmedUi(
        id = rowKey,
        name = displayName,
        isYou = !isGuest && memberId == selfId,
        position = if (isGuest) null else athlete?.position,
        guest = guestRow(isAdmin, open, tone),
        canRemove = canRemoveMember(isAdmin, tone),
    )

    private fun AttendanceRosterMember.toWaitlist(
        athlete: AthleteRosterEntry?,
        isAdmin: Boolean,
        open: Boolean,
        tone: GameDetailStatusTone?,
    ) = GameDetailWaitlistUi(
        id = rowKey,
        name = displayName,
        queuePosition = waitlistPosition,
        athletePosition = if (isGuest) null else athlete?.position,
        isMensalista = !isGuest && athlete?.membershipType == AthleteMembershipType.MENSALISTA,
        guest = guestRow(isAdmin, open, tone),
        canRemove = canRemoveMember(isAdmin, tone),
    )

    /** Membro (não convidado, que tem a ação própria) que não é o gestor olhando, em jogo publicado. */
    private fun AttendanceRosterMember.canRemoveMember(isAdmin: Boolean, tone: GameDetailStatusTone?) =
        isAdmin && tone == GameDetailStatusTone.Published && !isGuest && memberId != selfId

    private fun AttendanceRosterMember.guestRow(
        isAdmin: Boolean,
        open: Boolean,
        tone: GameDetailStatusTone?,
    ): GameGuestRowUi? {
        if (!isGuest) return null
        val yours = memberId == selfId
        val published = tone != GameDetailStatusTone.Cancelled && tone != GameDetailStatusTone.Completed
        return GameGuestRowUi(
            hostId = memberId,
            guestSeq = guestSeq,
            hostName = hostDisplayName.orEmpty(),
            isYours = yours,
            canRemove = (yours && open) || (isAdmin && published),
        )
    }

    /** Regras A e C: só quem já disse "Vou" (confirmado ou na fila), e só com confirmações abertas. */
    private fun guestUi(game: Game, detail: AttendanceDetail, open: Boolean, previous: GameGuestUi): GameGuestUi {
        val going = detail.ownAttendance?.status == AttendanceStatus.Confirmed ||
            detail.ownAttendance?.status == AttendanceStatus.Waitlisted
        return previous.copy(
            visible = game.status == GameStatus.Published,
            feeLabel = game.gameFeeCents?.let(::formatBrl),
            sheetOpen = false,
            adding = false,
            removal = null,
            removing = false,
        ).withAccess(open, going)
    }

    /** O botão só habilita para quem já disse "Vou" (confirmado ou na fila), com as confirmações abertas. */
    private fun GameGuestUi.withAccess(open: Boolean, going: Boolean) = copy(
        enabled = open && going,
        hint = when {
            !open -> GameGuestHint.Closed
            !going -> GameGuestHint.NeedAnswer
            else -> GameGuestHint.Default
        },
    )

    private fun updateGuest(block: (GameGuestUi) -> GameGuestUi) = update { it.copy(guest = block(it.guest)) }

    private fun submitGuest() {
        val guest = state.value.guest
        if (!guest.enabled || !guest.canSubmit) return
        val name = guest.name.trim()
        val loadAtStart = loadGeneration
        updateGuest { it.copy(adding = true, addFailed = false) }
        viewModelScope.launch {
            val result = attendanceGateway.addGuest(
                GroupId(groupId),
                gameId,
                AddGuestCommand(Uuid.random().toString(), name),
            )
            if (loadAtStart != loadGeneration) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    updateGuest {
                        it.copy(sheetOpen = false, adding = false, name = "", noticeName = name, noticeJoined = true)
                    }
                    load()
                }
                is SaqzResult.Failure -> updateGuest { it.copy(adding = false, addFailed = true) }
            }
        }
    }

    private fun requestRemoveGuest(rowId: String) {
        val confirmed = state.value.confirmedRoster.firstOrNull { it.id == rowId }
        val waiting = state.value.waitlist.firstOrNull { it.id == rowId }
        val row = confirmed?.guest ?: waiting?.guest ?: return
        if (!row.canRemove) return
        updateGuest {
            it.copy(
                removal = GameGuestRemovalUi(
                    rowId,
                    row.hostId,
                    row.guestSeq,
                    confirmed?.name ?: waiting?.name.orEmpty(),
                    confirmed != null,
                    notifiedHostName = row.hostName.takeUnless { row.isYours },
                ),
                removeFailed = false,
            )
        }
    }

    private fun confirmRemoveGuest() {
        val removal = state.value.guest.removal ?: return
        if (state.value.guest.removing) return
        val loadAtStart = loadGeneration
        updateGuest { it.copy(removing = true, removeFailed = false) }
        viewModelScope.launch {
            val result = attendanceGateway.removeGuest(GroupId(groupId), gameId, removal.hostId, removal.guestSeq)
            if (loadAtStart != loadGeneration) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    updateGuest {
                        it.copy(removal = null, removing = false, noticeName = removal.name, noticeJoined = false)
                    }
                    load()
                }
                is SaqzResult.Failure -> updateGuest { it.copy(removing = false, removeFailed = true) }
            }
        }
    }

    private fun updateOrganizer(block: (GameOrganizerUi) -> GameOrganizerUi) =
        update { it.copy(organizer = block(it.organizer)) }

    private fun requestRemoveMember(rowId: String) {
        val current = state.value
        if (!current.canManageAttendance || current.organizer.removing) return
        val confirmed = current.confirmedRoster.firstOrNull { it.id == rowId && it.canRemove }
        val waiting = current.waitlist.firstOrNull { it.id == rowId && it.canRemove }
        val name = confirmed?.name ?: waiting?.name ?: return
        updateOrganizer {
            it.copy(removal = GameMemberRemovalUi(rowId, name, confirmed = confirmed != null), removeFailure = null)
        }
    }

    private fun confirmRemoveMember(defaultReason: String) {
        val current = state.value
        val removal = current.organizer.removal ?: return
        if (!current.canManageAttendance || current.organizer.removing) return
        // Motivo é opcional: campo fechado, vazio ou curto demais cai no padrão, nunca vira erro do backend.
        val custom = removal.reason.trim().takeIf { removal.reasonOpen && it.length >= MIN_OVERRIDE_REASON }
        updateOrganizer { it.copy(removing = true, removeFailure = null) }
        sendOverride(removal.memberId, AttendanceIntent.Decline, custom ?: defaultReason) { failure ->
            updateOrganizer { it.copy(removing = false, removeFailure = failure) }
        }
    }

    private fun openPlaceSheet() {
        val current = state.value
        if (!current.canManageAttendance || current.organizer.placing) return
        val taken = (current.confirmedRoster.map { it.id } + current.waitlist.map { it.id }).toSet()
        val candidates = athletes
            .filter { it.active && it.userId != selfId && it.userId !in taken }
            .map { GameMemberCandidateUi(it.userId, it.displayName, it.position) }
        updateOrganizer {
            it.copy(
                placeOpen = true,
                candidates = candidates,
                selectedId = null,
                placeFailure = null,
                placeReasonOpen = false,
                placeReason = "",
            )
        }
    }

    private fun selectPlaceCandidate(memberId: String) {
        val organizer = state.value.organizer
        if (organizer.placing || organizer.candidates.none { it.id == memberId }) return
        updateOrganizer { it.copy(selectedId = memberId, placeFailure = null) }
    }

    private fun confirmPlaceMember(defaultReason: String) {
        val current = state.value
        val selected = current.organizer.selected ?: return
        if (!current.canManageAttendance || !current.organizer.placeOpen || current.organizer.placing) return
        if (current.placeBlocked) return
        val organizer = current.organizer
        val custom = organizer.placeReason.trim().takeIf { organizer.placeReasonOpen && it.length >= MIN_OVERRIDE_REASON }
        updateOrganizer { it.copy(placing = true, placeFailure = null) }
        sendOverride(selected.id, AttendanceIntent.Confirm, custom ?: defaultReason) { failure ->
            updateOrganizer { it.copy(placing = false, placeFailure = failure) }
        }
    }

    /**
     * `override` do gestor. Sucesso fecha a folha e recarrega as listas; conflito de versão recarrega a tela;
     * jogo fechado mostra o motivo; o resto libera a folha para tentar de novo com o mesmo `requestId`.
     */
    private fun sendOverride(
        memberId: String,
        intent: AttendanceIntent,
        reason: String,
        onFailure: (GameOrganizerFailure) -> Unit,
    ) {
        val key = "$intent:$memberId:$reason"
        val requestId = pendingOverride?.takeIf { it.first == key }?.second
            ?: Uuid.random().toString().also { pendingOverride = key to it }
        val loadAtStart = loadGeneration
        val generation = ++overrideGeneration
        viewModelScope.launch {
            val result = attendanceGateway.override(
                GroupId(groupId),
                gameId,
                OverrideAttendanceCommand(requestId, memberId, intent, reason),
            )
            if (!isCurrent(Operation.Override, generation, loadAtStart)) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    pendingOverride = null
                    val detail = result.value.value.detail.toAttendance()
                    update {
                        it.copy(
                            attendance = detail,
                            header = it.header?.copy(availableSpots = detail.availableSpots),
                            organizer = GameOrganizerUi(),
                        )
                    }
                    refreshRoster(Operation.Override, generation, loadAtStart)
                }
                is SaqzResult.Failure -> when (result.error) {
                    AttendanceError.Conflict -> {
                        pendingOverride = null
                        updateOrganizer { GameOrganizerUi() }
                        load()
                    }
                    AttendanceError.Frozen, AttendanceError.HiddenResource -> onFailure(GameOrganizerFailure.Closed)
                    else -> onFailure(GameOrganizerFailure.Generic)
                }
            }
        }
    }

    private fun Int.pad(): String = if (this < 10) "0$this" else toString()
}

private fun AttendanceError.toUiError(): GroupUiError = when (this) {
    AttendanceError.HiddenResource -> GroupUiError.NotFound
    AttendanceError.Conflict -> GroupUiError.Conflict
    AttendanceError.Authentication -> GroupUiError.AccessDenied
    AttendanceError.DeadlinePassed,
    AttendanceError.Frozen,
    is AttendanceError.Validation,
    is AttendanceError.Data,
    -> GroupUiError.Network
}

private fun AthleteError.toUiError(): GroupUiError = when (this) {
    is AthleteError.Validation -> GroupUiError.Validation
    is AthleteError.DataFailure -> GroupUiError.Network
}

private const val MIN_CAPACITY = GameCapacityLimits.Min
private const val MAX_CAPACITY = GameCapacityLimits.Max
private const val MAX_GUEST_NAME = 80
private const val MIN_OVERRIDE_REASON = 2
private const val MAX_OVERRIDE_REASON = 500
private fun GameStatus.toTone(): GameDetailStatusTone = when (this) {
    GameStatus.Draft -> GameDetailStatusTone.Draft
    GameStatus.Published -> GameDetailStatusTone.Published
    GameStatus.Cancelled -> GameDetailStatusTone.Cancelled
    GameStatus.Completed -> GameDetailStatusTone.Completed
}
private fun formatDatePtBr(date: LocalDate): String =
    "${date.day.toString().padStart(2, '0')}/${(date.month.ordinal + 1).toString().padStart(2, '0')}/${date.year}"
