package br.com.saqz.groups.presentation.gamedetail
import br.com.saqz.groups.domain.attendance.WaitlistCharge
import br.com.saqz.groups.presentation.ui.components.formatBrlShort
import br.com.saqz.groups.presentation.photo.memberPhoto
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
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.PositionSummary
import br.com.saqz.groups.presentation.attendancehistory.gameHistorySubtitle
import br.com.saqz.groups.presentation.attendance.PositionSuggestion
import br.com.saqz.groups.domain.attendance.AttendancePromotionCommand
import br.com.saqz.groups.domain.attendance.AttendanceRoster
import br.com.saqz.groups.domain.attendance.AttendanceRosterMember
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.AttendanceVersionToken
import br.com.saqz.groups.domain.attendance.OverrideAttendanceCommand
import br.com.saqz.groups.domain.attendance.SelfAttendanceCommand
import br.com.saqz.groups.domain.communication.GroupWhatsAppGateway
import br.com.saqz.groups.domain.communication.GroupWhatsAppStatus
import br.com.saqz.groups.domain.communication.NativeNotificationPort
import br.com.saqz.groups.presentation.whatsappbinding.WhatsAppAvailability
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
import br.com.saqz.groups.presentation.GamePlanEndAction
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.trackGameAfterPlanEnd
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import br.com.saqz.groups.presentation.isPlanPaused
import br.com.saqz.groups.presentation.AutoRetry
import br.com.saqz.groups.presentation.isOffline
import br.com.saqz.groups.presentation.toUiError
import br.com.saqz.domain.DataError
import br.com.saqz.groups.domain.communication.CommunicationGateway
import br.com.saqz.groups.presentation.game.gameHeroDisplay
import br.com.saqz.groups.presentation.game.gameLongDateLabel
import br.com.saqz.groups.presentation.game.gameMenuTitle
import br.com.saqz.groups.presentation.game.gamePrioritySentence
import br.com.saqz.groups.presentation.game.gameTimeLabel
import br.com.saqz.groups.presentation.game.gameTimeZone
import br.com.saqz.groups.presentation.game.gameWeekdayLabel
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
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
    /** "Avisar" quem não respondeu (DEB-116); sem a porta (teste antigo) o botão não envia. */
    private val communications: CommunicationGateway? = null,
    /** JOGO-011: diária pendente do jogo encerrado decide entre "Encerrar jogo e acertar" e "Ver acerto". */
    private val organizerFinance: br.com.saqz.groups.domain.finance.OrganizerFinanceGateway? = null,
    /** PRES-016: "Marcar falta" e "Desfazer"; sem a porta (teste antigo) a ação não aparece. */
    private val pastGames: br.com.saqz.groups.domain.game.PastGamesGateway? = null,
    /** Vínculo com o grupo do WhatsApp: ativo, o "Avisar" não some; sem as portas, vale só o pendente. */
    private val whatsApp: GroupWhatsAppGateway? = null,
    private val whatsAppAvailability: WhatsAppAvailability? = null,
    /** PROM-010: a diária do avulso na espera (pagamento antecipado); sem a porta, a caixa mostra só o valor do jogo. */
    private val athleteFinance: br.com.saqz.groups.domain.finance.AthleteFinanceGateway? = null,
) : MviViewModel<GameDetailState, GameDetailIntent, GameDetailEffect>(GameDetailState()) {
    private var loadGeneration = 0
    private var promotionGeneration = 0
    private var capacityGeneration = 0
    private var responseGeneration = 0
    private var overrideGeneration = 0
    private var versionToken: GameVersionToken? = null
    private var selfId: String? = null

    /** Linha de quem não subiu da espera: o aviso de falha fica só enquanto ela ainda espera na lista recarregada. */
    private var failedPromotionRowId: String? = null

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
    private var gameZoneId: String? = null
    /** PROM-010: o grupo pede pagamento antecipado (valor do jogo na leitura) e o jogo tem valor. */
    private var advancePayment = false
    private var groupPixKey: String? = null
    private var lastGame: Game? = null
    private var groupReceivablesActive = false
    /** Início do rascunho que o servidor recusou publicar por cair além do fim do plano; outro início na recarga limpa o aviso. */
    private var publishRefusedStart: Instant? = null
    private var groupPromotionMode: PromotionMode = PromotionMode.FIFO
    private var groupName: String? = null

    /**
     * O servidor recusou a resposta por prazo vencido (`ATTENDANCE_DEADLINE_PASSED`, backend v.0.0.17). A
     * recarga reconstrói o cabeçalho do zero; sem esta memória os botões voltariam a valer e o aviso do
     * prazo voltaria a prometer a lista de espera, até o próximo toque recusado.
     */
    private var deadlineRefused = false

    /** "Avisar" que falhou: tentar de novo reaproveita o `requestId` (o servidor deduplica); o sucesso o descarta. */
    private var reminderRequestId: String? = null

    /**
     * Carga no ar. Só a recarga por baixo ([GameDetailIntent.Refresh]) é descartada por ela: o
     * primeiro `resume` do Root chega logo depois do `init { load() }`. `Retry` passa por cima.
     */
    private var loadInFlight = false

    /** Geração da carga que veio de um puxão — só ela avisa quando falha. */
    private var pulledGeneration = -1

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
    @Suppress("CyclomaticComplexMethod")
    override fun handleIntent(intent: GameDetailIntent) {
        when (intent) {
            GameDetailIntent.Retry, GameDetailIntent.Refresh -> reload(intent)
            GameDetailIntent.PullRefresh -> pullRefresh()
            GameDetailIntent.DismissRefreshFailed -> update { it.copy(refreshFailed = false) }
            GameDetailIntent.Edit -> {
                update { it.copy(gameMenuOpen = false) }
                emit(GameDetailEffect.OpenEditor)
            }
            GameDetailIntent.OpenHistory -> if (state.value.hasGameMenu) {
                update { it.copy(gameMenuOpen = false) }
                emit(GameDetailEffect.OpenHistory(state.value.hero?.historySubtitle.orEmpty()))
            }
            GameDetailIntent.OpenSettlement -> if (
                state.value.isAdmin && state.value.header?.statusTone == GameDetailStatusTone.Completed ||
                state.value.canReviewFinance
            ) {
                emit(GameDetailEffect.OpenSettlement)
            }
            GameDetailIntent.RequestCancel -> if (state.value.header?.statusTone == GameDetailStatusTone.Published) {
                update { it.copy(cancelDialogOpen = true, cancelFailed = false, gameMenuOpen = false) }
            }
            GameDetailIntent.DismissCancel -> if (!state.value.cancelling) {
                update { it.copy(cancelDialogOpen = false, cancelFailed = false) }
            }
            GameDetailIntent.ConfirmCancel -> cancel()
            GameDetailIntent.Publish -> publish()
            is GameDetailIntent.Promote -> promote(intent.memberId, intent.reason, intent.guestSeq, intent.markPaid)
            is GameDetailIntent.ReceiveWaitlistPayment -> receiveWaitlistPayment(intent.rowId)
            is GameDetailIntent.OpenAdvancePlace -> if (state.value.waitlist.any { it.id == intent.rowId && it.payment?.paid == false }) {
                update { it.copy(advancePlaceRowId = intent.rowId) }
            }
            GameDetailIntent.DismissAdvancePlace -> update { it.copy(advancePlaceRowId = null) }
            is GameDetailIntent.ConfirmAdvancePlace -> confirmAdvancePlace(intent.markPaid, intent.reason)
            GameDetailIntent.CopyGroupPix -> state.value.advancePayment?.pixKey?.let { emit(GameDetailEffect.CopyPix(it)) }
            GameDetailIntent.PayAdvance -> if (state.value.advancePayment?.payInApp == true) {
                emit(GameDetailEffect.OpenPayment(groupId, groupName.orEmpty()))
            }
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
        if (onPositionIntent(intent)) return true
        when (intent) {
            is GameDetailIntent.Respond -> onRespond(intent.intent)
            GameDetailIntent.OpenGuestSheet -> if (state.value.guest.enabled) {
                updateGuest { it.copy(sheetOpen = true, name = "", addFailed = false, position = state.value.mostFreePosition) }
            }
            is GameDetailIntent.UpdateGuestName -> updateGuest {
                it.copy(name = intent.value.take(MAX_GUEST_NAME), addFailed = false)
            }
            GameDetailIntent.SubmitGuest -> submitGuest()
            GameDetailIntent.DismissGuestSheet -> if (!state.value.guest.adding) {
                updateGuest { it.copy(sheetOpen = false, addFailed = false) }
            }
            is GameDetailIntent.RequestRemoveGuest -> {
                update { it.copy(personActionsId = null) }
                requestRemoveGuest(intent.rowId)
            }
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
            is GameDetailIntent.RequestRemoveMember -> {
                update { it.copy(personActionsId = null) }
                requestRemoveMember(intent.rowId)
            }
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
            else -> return onScreenIntent(intent)
        }
        return true
    }

    /** Cabeçalho e menus da tela redesenhada (DEB-116): mapa, Avisar, ⋯ da barra e ⋮ de cada pessoa. */
    private fun onScreenIntent(intent: GameDetailIntent): Boolean {
        when (intent) {
            GameDetailIntent.OpenVenueMap -> openMap()
            GameDetailIntent.MapOpenFailed -> update { it.copy(mapFailed = true) }
            GameDetailIntent.NotifyPending -> notifyPending()
            GameDetailIntent.OpenGameMenu -> if (state.value.hasGameMenu) update { it.copy(gameMenuOpen = true) }
            GameDetailIntent.DismissGameMenu -> update { it.copy(gameMenuOpen = false) }
            is GameDetailIntent.OpenPersonActions -> openPersonActions(intent.rowId)
            GameDetailIntent.DismissPersonActions -> update { it.copy(personActionsId = null, noShow = it.noShow.copy(failed = false)) }
            is GameDetailIntent.SetNoShow -> setNoShow(intent.rowId, intent.marked)
            else -> return false
        }
        return true
    }

    /** O mapa abre o endereço DO JOGO; jogo sem endereço não mostra o botão, mas a falha fica colada na linha. */
    private fun openMap() {
        val address = state.value.hero?.address?.takeIf(String::isNotBlank)
        update { it.copy(mapFailed = address == null) }
        if (address != null) emit(GameDetailEffect.OpenMap(address))
    }

    /** O ⋮ só abre para quem tem alguma ação naquela linha (gestor, ou o anfitrião no convidado dele). */
    private fun openPersonActions(rowId: String) {
        val current = state.value
        val confirmed = current.confirmedRoster.firstOrNull { it.id == rowId }
        val waiting = current.waitlist.firstOrNull { it.id == rowId }
        val canAct = (confirmed != null && current.canMarkNoShow && pastGames != null) ||
            confirmed?.canRemove == true || confirmed?.guest?.canRemove == true ||
            waiting?.canRemove == true || waiting?.guest?.canRemove == true ||
            (waiting != null && current.canForce(waiting))
        if (canAct) update { it.copy(personActionsId = rowId) }
    }

    /**
     * "Avisar" de quem não respondeu, o mesmo envio do grupo: trava de toque duplo, `requestId` reaproveitado na
     * falha, recusa do servidor (jogo começou) com o texto próprio.
     */
    private fun notifyPending() {
        val current = state.value
        val gateway = communications ?: return
        if (!current.isAdmin || current.reminder.sending) return
        if (current.header?.statusTone != GameDetailStatusTone.Published || current.header.canRespond.not()) return
        val requestId = reminderRequestId ?: Uuid.random().toString().also { reminderRequestId = it }
        val loadAtStart = loadGeneration
        update { it.copy(reminder = GameReminderUi(sending = true)) }
        viewModelScope.launch {
            val result = gateway.remind(GroupId(groupId), gameId, requestId)
            // A carga mudou no meio do envio: o resultado não vale para a tela nova, mas o "Avisar" tem de voltar.
            if (loadAtStart != loadGeneration) {
                update { it.copy(reminder = it.reminder.copy(sending = false)) }
                return@launch
            }
            when (result) {
                is SaqzResult.Success -> {
                    reminderRequestId = null
                    update { it.copy(reminder = GameReminderUi(notifiedCount = result.value.recipientCount)) }
                }
                is SaqzResult.Failure -> {
                    val refused = result.error.cause is DataError.Validation
                    update { it.copy(reminder = GameReminderUi(failed = !refused, refused = refused)) }
                }
            }
        }
    }
    /**
     * `Retry` recarrega por inteiro. `Refresh` é a volta à tela: recarga por baixo, e o primeiro,
     * logo após o `init`, é descartado.
     */
    private fun reload(intent: GameDetailIntent) {
        autoRetry.reset()
        if (intent is GameDetailIntent.Retry) load() else if (!isFirstAppearance()) load(softRefresh = true)
    }


    /**
     * GER-002: escrita ou folha aberta ignoram o puxão, como a recarga por baixo; carga no ar leva o
     * puxão de carona — a bola mostra a espera e a falha dela avisa —, sem outra busca.
     */
    private fun pullRefresh() {
        val current = state.value
        val showingContent = !current.isLoading && !current.loadFailed
        if (!showingContent || current.isRefreshing || current.busy) return
        autoRetry.reset()
        update { it.copy(isRefreshing = true, refreshFailed = false) }
        if (loadInFlight) {
            pulledGeneration = loadGeneration
            return
        }
        // Antes do `load`: a falha pode chegar antes de ele voltar.
        pulledGeneration = loadGeneration + 1
        load(softRefresh = true)
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
                        is SaqzResult.Success -> {
                            groupName = groupResult.value.group.name
                            groupPixKey = groupResult.value.group.profile?.pixKey?.takeIf(String::isNotBlank)
                            groupReceivablesActive = groupResult.value.group.receivablesActive
                            if (groupResult.value.group.role != GroupRole.ATHLETE) loadWhatsApp(generation)
                            loadAttendance(
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
        }
        // Libera pelo fim do job: os `return` da guarda de geração e o cancelamento do escopo também
        // devolvem a chave, e a checagem impede que uma carga velha destrave a nova.
        job.invokeOnCompletion {
            if (generation == loadGeneration) {
                loadInFlight = false
                update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** Status do vínculo em paralelo com a lista, perguntado ao servidor a cada carga. Falha mantém o último valor. */
    private fun loadWhatsApp(generation: Int) {
        val availability = whatsAppAvailability ?: return
        val gateway = whatsApp ?: return
        viewModelScope.launch {
            val active = if (availability.refresh()) {
                val result = gateway.binding(GroupId(groupId)) as? SaqzResult.Success ?: return@launch
                result.value.status == GroupWhatsAppStatus.ACTIVE
            } else {
                false
            }
            if (generation == loadGeneration) update { it.copy(whatsAppGroupActive = active) }
        }
    }

    /**
     * Escrita em voo ou folha/diálogo aberto: a recarga por baixo espera a próxima volta. Recarregar
     * agora descartaria o resultado da escrita (guarda de geração) ou fecharia a folha no meio.
     */
    private val GameDetailState.busy: Boolean
        get() {
            val writing = cancelling || publishing || savingCapacity || promotingMemberId != null || response.responding ||
                receivingPaymentId != null
            val interacting = cancelDialogOpen || capacitySheetOpen || guest.sheetOpen || guest.removal != null ||
                organizer.removal != null || organizer.placeOpen || positionPicker != null || gameMenuOpen ||
                personActionsId != null || advancePlaceRowId != null
            return writing || interacting || guest.adding || guest.removing || organizer.removing || organizer.placing ||
                reminder.sending || noShow.sendingId != null
        }

    private suspend fun loadAttendance(
        generation: Int,
        game: Game,
        isAdmin: Boolean,
        groupMensalistaPriority: Boolean,
        promotionMode: PromotionMode,
        softRefresh: Boolean,
    ) {
        // Jogo com prazo no início não tem janela de prioridade de mensalista (DEB-045).
        val mensalistaPriority = AttendanceRule.priorityWindow(groupMensalistaPriority, game.confirmationDeadline, game.startsAt)
        groupPromotionMode = promotionMode
        val detailResult = attendanceGateway.read(GroupId(groupId), gameId)
        if (generation != loadGeneration) return
        val rosterResult = attendanceGateway.roster(GroupId(groupId), gameId)
        if (generation != loadGeneration) return
        val athletesResult = athleteGateway.roster(GroupId(groupId), AthleteRosterFilter())
        if (generation != loadGeneration) return
        val profileResult = athleteGateway.ownProfile()
        val hero = game.toHero(AttendanceRule.priorityWindow(groupMensalistaPriority, game.confirmationDeadline, game.startsAt))
        val settlementPending = if (isAdmin && game.status == GameStatus.Completed) settlementPending() else null
        val noShow = game.noShowUi(isAdmin && pastGames != null)
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
                gameZoneId = game.zoneId
                advancePayment = game.advancePaymentRequired && game.gameFeeCents != null
                lastGame = game
                val header = game.toHeader()
                val open = header.confirmationOpen
                val tone = header.statusTone
                val waitlist = roster.waitlisted.map { member -> member.toWaitlist(byId[member.memberId], isAdmin, open, tone) }
                failedPromotionRowId = failedPromotionRowId?.takeIf { id -> waitlist.any { it.id == id } }
                update {
                    it.copy(
                        isLoading = false,
                        loadFailed = false,
                        error = null,
                        header = header.copy(availableSpots = detail.availableSpots),
                        attendance = detail.toAttendance(),
                        positions = detail.positions.map(PositionSummary::toUi),
                        positionPicker = null,
                        confirmedRoster = roster.confirmed.map { member ->
                            member.toConfirmed(byId[member.memberId], isAdmin, open, tone)
                        },
                        waitlist = waitlist,
                        declinedRoster = roster.declined?.map { member -> member.toDeclined(byId[member.memberId]) },
                        groupName = groupName,
                        hero = hero,
                        showsFeeNote = membershipType == AthleteMembershipType.AVULSO && game.gameFeeCents != null,
                        feeNoteAmount = game.gameFeeCents
                            ?.takeIf { membershipType == AthleteMembershipType.AVULSO && it > 0 }?.let(::formatBrlShort),
                        gameMenuOpen = false,
                        personActionsId = null,
                        settlementPending = settlementPending,
                        noShow = noShow,
                        guest = guestUi(game, detail, open, it.guest),
                        response = responseUi(tone == GameDetailStatusTone.Published, detail.ownAttendance, roster, mensalistaPriority),
                        mensalistaPriority = mensalistaPriority,
                        promotionMode = promotionMode,
                        isAdmin = isAdmin,
                        promotingMemberId = null,
                        promotionFailed = failedPromotionRowId != null,
                        publishAfterPlanEnd = it.publishAfterPlanEnd && startsAt == publishRefusedStart,
                        organizer = GameOrganizerUi(),
                        capacitySheetOpen = false,
                        savingCapacity = false,
                        capacityFailed = false,
                    )
                }
                responseRetry.reset()
                loadAdvancePayment(generation, game, detail.ownAttendance?.status)
            }
        }
    }

    /** Jogo publicado e ainda por começar: depois do início ninguém sobe da espera (DEB-018). */
    private fun GameDetailState.canPromoteNow() =
        header?.statusTone == GameDetailStatusTone.Published && header.gameStarted.not()

    /** A folha de "ainda não pagou": fecha e coloca, com ou sem a baixa da diária. */
    private fun confirmAdvancePlace(markPaid: Boolean, reason: String) {
        val rowId = state.value.advancePlaceRowId ?: return
        val person = state.value.waitlist.firstOrNull { it.id == rowId }
        update { it.copy(advancePlaceRowId = null) }
        if (person == null) return
        val hostId = person.guest?.hostId ?: person.id
        promote(hostId, reason, person.guest?.guestSeq ?: 0, markPaid)
    }

    /**
     * Recebi na espera (PROM-010): a rota de status de sempre com a `version` do `charge`. Um toque por vez (GER-001);
     * conflito (outro gestor, outra aba) ou falha recarrega a espera.
     */
    private fun receiveWaitlistPayment(rowId: String) {
        val current = state.value
        val payment = current.waitlist.firstOrNull { it.id == rowId }?.payment ?: return
        val finance = organizerFinance ?: return
        if (!current.isAdmin || payment.paid || current.receivingPaymentId != null) return
        val loadAtStart = loadGeneration
        update { it.copy(receivingPaymentId = rowId, receivePaymentFailed = false) }
        viewModelScope.launch {
            val result = finance.updateChargeStatus(
                GroupId(groupId),
                payment.chargeId,
                br.com.saqz.groups.domain.finance.FinanceVersionToken("\"${payment.version}\""),
                br.com.saqz.groups.domain.finance.ChargeStatusCommand(br.com.saqz.groups.domain.finance.ChargeStatus.Paid),
            )
            if (loadAtStart != loadGeneration) return@launch
            val conflict = result is SaqzResult.Failure &&
                result.error == br.com.saqz.groups.domain.finance.FinanceError.Conflict
            update {
                it.copy(
                    receivingPaymentId = null,
                    receivePaymentFailed = result is SaqzResult.Failure && !conflict,
                    waitlist = if (result is SaqzResult.Success) {
                        it.waitlist.map { row ->
                            if (row.id == rowId) row.copy(payment = row.payment?.copy(paid = true)) else row
                        }
                    } else it.waitlist,
                )
            }
            refreshRoster(Operation.Promotion, promotionGeneration, loadAtStart)
        }
    }

    private fun promote(memberId: String, reason: String, guestSeq: Int, markPaid: Boolean = false) {
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
        failedPromotionRowId = null
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
                AttendancePromotionCommand(
                    Uuid.random().toString(), memberId, reason, guestSeq,
                    markPaid = markPaid,
                    paidMethod = br.com.saqz.groups.domain.finance.PaidMethod.Pix.takeIf { markPaid },
                ),
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
                is SaqzResult.Failure -> {
                    update {
                        it.copy(
                            waitlist = previousWaitlist,
                            attendance = previousAttendance,
                            promotingMemberId = null,
                            promotionFailed = true,
                        )
                    }
                    // A lista pode ter mudado por outro gestor (a pessoa já subiu ou saiu): recarrega por baixo em
                    // vez de deixar a linha velha para um "tente de novo" que falharia igual.
                    failedPromotionRowId = rowId
                    load(softRefresh = true)
                }
            }
        }
    }

    private fun openCapacitySheet() {
        update { it.copy(gameMenuOpen = false) }
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
                            positions = roster.positions.takeIf { positions -> positions.isNotEmpty() }
                                ?.map(PositionSummary::toUi) ?: it.positions,
                            confirmedRoster = roster.confirmed.map { member ->
                                member.toConfirmed(byId[member.memberId], isAdmin, open, tone)
                            },
                            waitlist = roster.waitlisted.map { member ->
                                member.toWaitlist(byId[member.memberId], isAdmin, open, tone)
                            },
                            declinedRoster = roster.declined?.map { member -> member.toDeclined(byId[member.memberId]) },
                            response = it.response.reconcile(roster, it.mensalistaPriority),
                        )
                    }
                    // PROM-010: o Vou do avulso foi para a espera (ou saiu dela): a caixa de pagar acompanha.
                    if (operation == Operation.Response) {
                        lastGame?.let { game -> loadAdvancePayment(loadAtStart, game, state.value.response.status) }
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
    /**
     * "Vou" em jogo com posições abre o seletor (a do perfil vem sugerida); "Não vou", "Sair da lista" e jogo sem
     * posições respondem direto, como sempre. Quem já está confirmado ou na fila não escolhe de novo (PRES-009).
     */
    /** DEB-102: intents do seletor de posição, do convidado e da entrada forçada. Devolve `true` quando tratou. */
    private fun onPositionIntent(intent: GameDetailIntent): Boolean {
        when (intent) {
            GameDetailIntent.DismissPositionPicker -> if (state.value.positionPicker?.sending != true) {
                update { it.copy(positionPicker = null) }
            }
            is GameDetailIntent.UpdateGuestPosition -> updateGuest { it.copy(position = intent.position) }
            is GameDetailIntent.SelectPlacePosition ->
                updateOrganizer { it.copy(placePosition = intent.position, placeFailure = null) }
            is GameDetailIntent.RequestForceEntry -> {
                update { it.copy(personActionsId = null) }
                requestForceEntry(intent.rowId)
            }
            is GameDetailIntent.ConfirmForceEntry -> confirmForceEntry(intent.defaultReason)
            else -> return false
        }
        return true
    }

    private fun onRespond(intent: AttendanceIntent) {
        val current = state.value
        val entering = intent == AttendanceIntent.Confirm && current.response.status != AttendanceStatus.Confirmed &&
            current.response.status != AttendanceStatus.Waitlisted
        if (!entering || !current.hasPositions) {
            respond(intent)
            return
        }
        if (!current.canRespond(intent) || current.positionPicker != null) return
        // Jogo com posições: a escolha é a tela própria (DEB-107); nada é confirmado aqui.
        emit(GameDetailEffect.PickPosition)
    }

    private fun requestForceEntry(rowId: String) {
        val current = state.value
        if (!current.canManageAttendance || current.positionPicker != null || current.organizer.removing) return
        val person = current.waitlist.firstOrNull { it.id == rowId && it.guest == null } ?: return
        val position = person.athletePosition ?: return
        if (current.positions.none { it.position == position && it.full }) return
        update {
            it.copy(
                positionPicker = GamePositionPickerUi(
                    GamePositionPurpose.Force(person.id, person.name, position),
                    suggested = null,
                    selected = position,
                ),
            )
        }
    }

    private fun confirmForceEntry(defaultReason: String) {
        val current = state.value
        val picker = current.positionPicker ?: return
        val force = picker.purpose as? GamePositionPurpose.Force ?: return
        if (picker.sending || !current.canManageAttendance) return
        update { it.copy(positionPicker = picker.copy(sending = true, failed = false)) }
        sendOverride(force.memberId, AttendanceIntent.Confirm, defaultReason, force.position, force = true) {
            update { state -> state.copy(positionPicker = state.positionPicker?.copy(sending = false, failed = true)) }
        }
    }

    private fun respond(intent: AttendanceIntent, position: AthletePosition? = null) {
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
        val optimistic = current.optimisticStatus(intent, position)
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
                SelfAttendanceCommand(Uuid.random().toString(), intent, position),
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
            positions = detail.positions.map(PositionSummary::toUi),
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
    private fun GameDetailState.optimisticStatus(intent: AttendanceIntent, position: AthletePosition? = null): AttendanceStatus = when {
        intent == AttendanceIntent.Decline -> AttendanceStatus.Declined
        response.status == AttendanceStatus.Confirmed -> AttendanceStatus.Confirmed
        !AttendanceRule.entersDirectly(membershipType, mensalistaPriority, promotionMode, !deadlineIsOpen()) ->
            AttendanceStatus.Waitlisted
        // Posição cheia = espera daquela posição, mesmo com vaga no jogo (DEB-102).
        position != null && positions.any { it.position == position && it.full } -> AttendanceStatus.Waitlisted
        position == null && attendance?.let { it.confirmed >= it.capacity } == true -> AttendanceStatus.Waitlisted
        position != null && attendance?.let { it.confirmed >= it.capacity } == true && !hasPositions -> AttendanceStatus.Waitlisted
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

    private fun AttendanceEntry.queuePosition(roster: AttendanceRoster?): Long? {
        val waiting = roster?.waitlisted.orEmpty()
        val index = waiting.indexOfFirst { !it.isGuest && it.memberId == memberId }
        // Com posições a vez é a da fila da própria posição, que o servidor já conta (DEB-102).
        val perPosition = waiting.getOrNull(index)?.waitlistPosition?.takeIf { positionsOn(roster) }
        return perPosition ?: index.takeIf { it >= 0 }?.let { it + 1L } ?: waitlistPosition
    }

    private fun positionsOn(roster: AttendanceRoster?) = roster?.positions?.isNotEmpty() == true

    /**
     * A fila é do servidor: depois de uma lista nova, quem olha fica confirmado ou com a posição dele.
     * Com a própria resposta em voo a lista é anterior a ela e não manda no status.
     */
    private fun GameDetailResponseUi.reconcile(roster: AttendanceRoster, mensalistaPriority: Boolean): GameDetailResponseUi {
        val id = selfId
        if (id == null || status == null || responding) return this
        val index = roster.waitlisted.indexOfFirst { !it.isGuest && it.memberId == id }
        val position = if (index >= 0 && roster.positions.isNotEmpty()) {
            (roster.waitlisted[index].waitlistPosition?.toInt() ?: (index + 1)) - 1
        } else {
            index
        }
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
    /** Dia do jogo que o publicar tentou, no fuso do grupo (aaaa-mm-dd). */
    private fun publishAttemptDate(): String? {
        val zone = gameZoneId?.let { runCatching { TimeZone.of(it) }.getOrNull() } ?: TimeZone.UTC
        return startsAt?.toLocalDateTime(zone)?.date?.toString()
    }

    /** Agenda mudou (409 de conflito) ou outra pessoa mexeu antes (versão velha): o certo é recarregar, não repetir. */
    private fun GameError.isStaleGame() = this is GameError.Conflict || this == GameError.VersionConflict

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
                is SaqzResult.Failure -> if (result.error.isStaleGame()) {
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
        update { it.copy(publishing = true, publishFailed = false, publishAfterPlanEnd = false) }
        viewModelScope.launch {
            val result = gameGateway.lifecycle(GroupId(groupId), gameId, token, GameLifecycleAction.Publish)
            when (result) {
                is SaqzResult.Success -> {
                    versionToken = result.value.version
                    update { it.copy(publishing = false, header = result.value.game.toHeader()) }
                    load()
                }
                is SaqzResult.Failure -> if (result.error.isStaleGame()) {
                    update { it.copy(publishing = false) }
                    load()
                } else {
                    val afterPlanEnd = result.error is GameError.AfterPlanEnd
                    publishAttemptDate()?.let { trackGameAfterPlanEnd(result.error, it, gameZoneId, GamePlanEndAction.Publish) }
                    publishRefusedStart = startsAt.takeIf { afterPlanEnd }
                    update { it.copy(publishing = false, publishFailed = !afterPlanEnd, publishAfterPlanEnd = afterPlanEnd) }
                }
            }
        }
    }

    /** Por baixo ([softRefresh]) a falha não troca a tela por erro: o conteúdo fica e a próxima volta tenta de novo. */
    private fun showFailure(generation: Int, error: GroupUiError, softRefresh: Boolean = false) {
        if (generation != loadGeneration) return
        if (softRefresh) {
            if (generation == pulledGeneration) update { it.copy(refreshFailed = true) }
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
            financeReviewRequired = financeReviewRequired,
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
        // Com posições vale a posição da vaga no jogo; sem elas, a do perfil, como antes.
        position = position ?: if (isGuest) null else athlete?.position,
        guest = guestRow(isAdmin, open, tone),
        canRemove = canRemoveMember(isAdmin, tone),
        isMensalista = !isGuest && athlete?.membershipType == AthleteMembershipType.MENSALISTA,
        forced = forced,
        photo = rosterPhoto(),
        noShow = noShow,
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
        athletePosition = position ?: if (isGuest) null else athlete?.position,
        isMensalista = !isGuest && athlete?.membershipType == AthleteMembershipType.MENSALISTA,
        guest = guestRow(isAdmin, open, tone),
        canRemove = canRemoveMember(isAdmin, tone),
        isYou = !isGuest && memberId == selfId,
        photo = rosterPhoto(),
        payment = charge?.takeIf { isAdmin && advancePayment }?.toPayment(hostDisplayName.takeIf { isGuest }),
    )

    private fun WaitlistCharge.toPayment(hostName: String?): WaitlistPaymentUi? = when (status) {
        br.com.saqz.groups.domain.finance.ChargeStatus.Pending ->
            WaitlistPaymentUi(formatBrlShort(amountCents), paid = false, chargeId = id, version = version, hostName = hostName)
        br.com.saqz.groups.domain.finance.ChargeStatus.Paid ->
            WaitlistPaymentUi(formatBrlShort(amountCents), paid = true, chargeId = id, version = version, hostName = hostName)
        else -> null
    }

    /**
     * PROM-010: o avulso na espera de grupo com pagamento antecipado vê a caixa de pagar. A diária vem de Minhas
     * cobranças (a nascida no Vou); sem ela, a caixa mostra o valor do jogo como pendente.
     */
    private fun loadAdvancePayment(generation: Int, game: Game, status: AttendanceStatus?) {
        val fee = game.gameFeeCents
        if (!advancePayment || fee == null || status != AttendanceStatus.Waitlisted ||
            membershipType != AthleteMembershipType.AVULSO
        ) {
            update { it.copy(advancePayment = null) }
            return
        }
        val pending = AdvancePaymentUi(formatBrlShort(fee), paid = false, pixKey = groupPixKey, payInApp = groupReceivablesActive)
        update { it.copy(advancePayment = it.advancePayment ?: pending) }
        val gateway = athleteFinance ?: return
        viewModelScope.launch {
            val charges = (gateway.ownCharges(GroupId(groupId)) as? SaqzResult.Success)?.value ?: return@launch
            if (generation != loadGeneration) return@launch
            val own = charges.charges.firstOrNull { it.gameId == gameId && it.guestDisplayName == null }
            val paid = own?.status == br.com.saqz.groups.domain.finance.ChargeStatus.Paid
            update {
                it.copy(advancePayment = pending.copy(amount = formatBrlShort(own?.amountCents ?: fee), paid = paid))
            }
        }
    }

    /** Quem não vai: só nome, posição e vínculo; ninguém mexe nessa lista. */
    private fun AttendanceRosterMember.toDeclined(athlete: AthleteRosterEntry?) = GameDetailConfirmedUi(
        id = rowKey,
        name = displayName,
        isYou = !isGuest && memberId == selfId,
        position = position ?: athlete?.position,
        isMensalista = athlete?.membershipType == AthleteMembershipType.MENSALISTA,
        photo = rosterPhoto(),
    )

    private fun AttendanceRosterMember.rosterPhoto() = if (isGuest) null else memberPhoto(groupId, memberId, photoTag)

    /** "Terça, 19h30", "28 de julho", o fim do jogo e o endereço, no fuso do jogo. */
    private suspend fun Game.toHero(priority: Boolean): GameDetailHeroUi? {
        val zone = gameTimeZone(zoneId)
        val start = runCatching { Instant.parse(startsAt) }.getOrNull() ?: return null
        val local = start.toLocalDateTime(zone)
        // O prazo só importa como fim da prioridade de mensalista: sem janela, a linha some (como na Início e no grupo).
        val deadline = runCatching { Instant.parse(confirmationDeadline).toLocalDateTime(zone) }.getOrNull()
        return GameDetailHeroUi(
            title = local.gameHeroDisplay(),
            date = local.date.gameLongDateLabel(),
            endTime = (start + durationMinutes.minutes).toLocalDateTime(zone).gameTimeLabel(),
            address = venue.address.trim(),
            menuTitle = local.date.gameMenuTitle(),
            historySubtitle = local.gameHistorySubtitle(),
            deadlineLine = deadline?.takeIf { priority }
                ?.gamePrioritySentence(Clock.System.now().toLocalDateTime(zone).date).orEmpty(),
        )
    }

    /** Alguma diária deste jogo segue pendente; sem a porta ou com falha, `null` (o botão fica como sempre). */
    private suspend fun settlementPending(): Boolean? =
        (organizerFinance?.charges(GroupId(groupId)) as? SaqzResult.Success)?.value?.charges
            ?.any { it.gameId == gameId && it.status == br.com.saqz.groups.domain.finance.ChargeStatus.Pending }

    /** Prazo da falta (início + duração + 7 dias, PRES-016), o dia do jogo e a diária, no fuso do jogo. */
    private suspend fun Game.noShowUi(manager: Boolean): GameNoShowUi {
        if (!manager || status != GameStatus.Completed) return GameNoShowUi()
        val zone = gameTimeZone(zoneId)
        val start = runCatching { Instant.parse(startsAt) }.getOrNull() ?: return GameNoShowUi()
        val local = start.toLocalDateTime(zone)
        val until = start + durationMinutes.minutes + NO_SHOW_WINDOW
        return GameNoShowUi(
            until = until.takeIf { it >= Clock.System.now() }?.toLocalDateTime(zone)?.date?.gameLongDateLabel(),
            gameDay = "${local.date.dayOfWeek.gameWeekdayLabel().lowercase()}, ${local.date.gameLongDateLabel()}",
            feeLabel = gameFeeCents?.takeIf { it > 0 }?.let(::formatBrl),
        )
    }

    /**
     * "Marcar falta" / "Desfazer": uma por vez (GER-001). O servidor é idempotente, então repetir depois de uma
     * falha não duplica nada; a linha só muda de aba quando ele confirma.
     */
    private fun setNoShow(rowId: String, marked: Boolean) {
        val current = state.value
        val gateway = pastGames ?: return
        if (!current.canMarkNoShow || current.noShow.sendingId != null) return
        val person = current.confirmedRoster.firstOrNull { it.id == rowId } ?: return
        if (person.noShow == marked) {
            update { it.copy(personActionsId = null) }
            return
        }
        val memberId = person.guest?.hostId ?: person.id
        val guestSeq = person.guest?.guestSeq ?: 0
        val loadAtStart = loadGeneration
        update { it.copy(noShow = it.noShow.copy(sendingId = rowId, failed = false)) }
        viewModelScope.launch {
            val result = gateway.setNoShow(GroupId(groupId), gameId, memberId, guestSeq, marked)
            // Uma recarga começou no meio: ela já traz a lista do servidor; só destrava a linha.
            if (loadAtStart != loadGeneration) {
                update { it.copy(noShow = it.noShow.copy(sendingId = null), personActionsId = null) }
                return@launch
            }
            when (result) {
                is SaqzResult.Success -> {
                    update { state ->
                        state.copy(
                            confirmedRoster = state.confirmedRoster.map { if (it.id == rowId) it.copy(noShow = marked) else it },
                            noShow = state.noShow.copy(sendingId = null),
                            personActionsId = null,
                        )
                    }
                    // O acerto pendente do jogo muda junto ("Faltou" no acerto): a recarga por baixo traz o resto.
                    load(softRefresh = true)
                }
                is SaqzResult.Failure -> update {
                    it.copy(noShow = it.noShow.copy(sendingId = null, failed = true))
                }
            }
        }
    }

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
        if (!guest.enabled || !guest.canSubmitWith(state.value.hasPositions)) return
        val name = guest.name.trim()
        val loadAtStart = loadGeneration
        updateGuest { it.copy(adding = true, addFailed = false) }
        viewModelScope.launch {
            val result = attendanceGateway.addGuest(
                GroupId(groupId),
                gameId,
                AddGuestCommand(Uuid.random().toString(), name, guest.position.takeIf { state.value.hasPositions }),
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
        val feeCancelled = confirmed != null && !confirmed.isMensalista && current.guest.feeLabel != null
        // Depois do início ninguém sobe (DEB-018); na escolha manual o gestor é quem coloca.
        val queueMoves = confirmed != null && startIsAhead() &&
            groupPromotionMode != PromotionMode.MANUAL && current.waitlist.isNotEmpty()
        updateOrganizer {
            it.copy(
                removal = GameMemberRemovalUi(
                    rowId,
                    name,
                    confirmed = confirmed != null,
                    feeCancelled = feeCancelled,
                    queueMoves = queueMoves,
                ),
                removeFailure = null,
            )
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
            .map { GameMemberCandidateUi(it.userId, it.displayName, it.position, memberPhoto(groupId, it.userId, it.photoTag)) }
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
        val candidate = organizer.candidates.first { it.id == memberId }
        val own = PositionSuggestion.suggested(candidate.position, state.value.positions.filter { it.slots > 0 }.map { it.position })
        updateOrganizer {
            it.copy(selectedId = memberId, placeFailure = null, placePosition = own ?: state.value.mostFreePosition)
        }
    }

    private fun confirmPlaceMember(defaultReason: String) {
        val current = state.value
        val selected = current.organizer.selected ?: return
        if (!current.canManageAttendance || !current.organizer.placeOpen || current.organizer.placing) return
        if (current.placeBlocked) return
        val organizer = current.organizer
        if (current.hasPositions && organizer.placePosition == null) return
        val custom = organizer.placeReason.trim().takeIf { organizer.placeReasonOpen && it.length >= MIN_OVERRIDE_REASON }
        updateOrganizer { it.copy(placing = true, placeFailure = null) }
        // Posição cheia: o gestor entra acima das vagas dela (DEB-102); a folha avisa antes de confirmar.
        val position = organizer.placePosition.takeIf { current.hasPositions }
        val force = position != null && current.positions.any { it.position == position && it.full }
        sendOverride(selected.id, AttendanceIntent.Confirm, custom ?: defaultReason, position, force) { failure ->
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
        position: AthletePosition? = null,
        force: Boolean = false,
        onFailure: (GameOrganizerFailure) -> Unit,
    ) {
        val key = "$intent:$memberId:$reason:$position:$force"
        val requestId = pendingOverride?.takeIf { it.first == key }?.second
            ?: Uuid.random().toString().also { pendingOverride = key to it }
        val loadAtStart = loadGeneration
        val generation = ++overrideGeneration
        viewModelScope.launch {
            val result = attendanceGateway.override(
                GroupId(groupId),
                gameId,
                OverrideAttendanceCommand(requestId, memberId, intent, reason, position, force),
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
                            positions = result.value.value.detail.positions.map(PositionSummary::toUi),
                            organizer = GameOrganizerUi(),
                            positionPicker = null,
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

/** DEB-102: posição cheia + gestor = pode forçar a entrada de quem espera nela (fica acima das vagas). */
internal fun GameDetailState.canForce(person: GameDetailWaitlistUi): Boolean =
    canManageAttendance && header?.gameStarted != true && person.guest == null &&
        positions.any { it.position == person.athletePosition && it.full }

private fun PositionSummary.toUi() = GamePositionUi(position, slots, confirmed, missing, waitlisted)

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

/** PRES-016: a falta se marca ou desfaz até 7 dias depois do fim do jogo. */
private val NO_SHOW_WINDOW = 7.days
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
