package br.com.saqz.groups.presentation.details

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.athlete.AthleteGateway
import br.com.saqz.groups.domain.attendance.AttendanceDetail
import br.com.saqz.groups.domain.attendance.AttendanceEntry
import br.com.saqz.groups.domain.attendance.AttendanceError
import br.com.saqz.groups.domain.attendance.AttendanceGateway
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceRoster
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.AutoConfirmationCommand
import br.com.saqz.groups.domain.attendance.SelfAttendanceCommand
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.finance.AthleteFinanceGateway
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeKind
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.domain.finance.FinanceStatementGateway
import br.com.saqz.groups.domain.finance.FinanceStatementQuery
import br.com.saqz.groups.domain.finance.OrganizerFinanceGateway
import br.com.saqz.groups.domain.group.Group
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupProfile
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.membership.GroupDepartureGateway
import br.com.saqz.groups.domain.communication.CommunicationGateway
import br.com.saqz.groups.domain.communication.CommunicationChannel
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.game.gameBellLabel
import br.com.saqz.groups.presentation.game.gameDeadlineSentence
import br.com.saqz.groups.presentation.game.gameDeadlineShort
import br.com.saqz.groups.presentation.game.gameHeroDisplay
import br.com.saqz.groups.presentation.game.gameHeroMeta
import br.com.saqz.groups.presentation.game.gameTimeZone
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import br.com.saqz.groups.presentation.home.HomeWaitlistRowUi
import br.com.saqz.groups.presentation.photo.groupPhotoUrl
import br.com.saqz.groups.presentation.toUiError
import br.com.saqz.groups.presentation.ui.finance.groupcash.PixUi
import br.com.saqz.groups.port.GroupNowPort
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.onboarding_athlete_share_message
import br.com.saqz.groups.resources.finance_overview_month_april
import br.com.saqz.groups.resources.finance_overview_month_august
import br.com.saqz.groups.resources.finance_overview_month_december
import br.com.saqz.groups.resources.finance_overview_month_february
import br.com.saqz.groups.resources.finance_overview_month_january
import br.com.saqz.groups.resources.finance_overview_month_july
import br.com.saqz.groups.resources.finance_overview_month_june
import br.com.saqz.groups.resources.finance_overview_month_march
import br.com.saqz.groups.resources.finance_overview_month_may
import br.com.saqz.groups.resources.finance_overview_month_november
import br.com.saqz.groups.resources.finance_overview_month_october
import br.com.saqz.groups.resources.finance_overview_month_september
import br.com.saqz.groups.resources.own_charges_date
import br.com.saqz.groups.resources.own_charges_due
import br.com.saqz.groups.resources.own_charges_due_history
import br.com.saqz.groups.resources.own_charges_due_overdue
import br.com.saqz.groups.resources.own_charges_game
import br.com.saqz.groups.resources.own_charges_monthly
import br.com.saqz.groups.resources.own_charges_monthly_unknown
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Suppress("LargeClass", "LongParameterList")
class GroupDetailsViewModel(
    private val groupId: String,
    private val groupGateway: GroupGateway,
    private val gameGateway: GameGateway,
    private val attendanceGateway: AttendanceGateway,
    private val athleteGateway: AthleteGateway,
    private val statementGateway: FinanceStatementGateway,
    private val organizerFinanceGateway: OrganizerFinanceGateway,
    private val athleteFinanceGateway: AthleteFinanceGateway,
    private val now: GroupNowPort,
    private val departureGateway: GroupDepartureGateway,
    private val communications: CommunicationGateway,
) : MviViewModel<GroupDetailsState, GroupDetailsIntent, GroupDetailsEffect>(GroupDetailsState()) {

    private var loadGeneration = 0
    private var responseGeneration = 0L
    private var autoConfirmationGeneration = 0L
    private var rosterGeneration = 0L
    private var ownChargesGeneration = 0L
    private var pixCopiedGeneration = 0L
    private var athleteIntroShown = false

    /** O grupo da carga corrente: a seção de cobranças sozinha precisa do fuso e do Pix. */
    private var loadedGroup: Group? = null
    private var reminderRequest: Pair<String, String>? = null

    init {
        load()
    }

    // Dispatch exaustivo: regras e guardas ficam nos handlers de cada operação.
    @Suppress("CyclomaticComplexMethod")
    override fun onIntent(intent: GroupDetailsIntent) {
        when (intent) {
            GroupDetailsIntent.DismissAthleteIntro -> update { it.copy(athleteIntroVisible = false, athleteShareFailed = false) }
            GroupDetailsIntent.ShareSaqz -> shareSaqz()
            GroupDetailsIntent.AthleteShareFailed -> {
                if (state.value.athleteIntroVisible) update { it.copy(athleteShareFailed = true) }
            }
            GroupDetailsIntent.OnboardingAction -> onboardingAction()
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
            GroupDetailsIntent.OpenVenueMap -> openMap()
            GroupDetailsIntent.MapOpenFailed -> update { it.copy(mapFailed = true) }
            GroupDetailsIntent.Leave -> confirmDeparture()
            GroupDetailsIntent.CancelLeave -> cancelDeparture()
            GroupDetailsIntent.ConfirmLeave -> leave()
            GroupDetailsIntent.RetryRoster -> retryRoster()
            GroupDetailsIntent.RetryOwnCharges -> retryOwnCharges()
            GroupDetailsIntent.CopyPix -> copyPix()
            GroupDetailsIntent.DismissToast -> update { it.copy(toast = null) }
            GroupDetailsIntent.ViewGame -> viewGame()
            GroupDetailsIntent.ConfirmAttendance -> viewGame()
            GroupDetailsIntent.NotifyPending -> notifyPending()
            GroupDetailsIntent.OpenNotices -> emit(GroupDetailsEffect.OpenThread(groupId, notices = true))
            GroupDetailsIntent.OpenChat -> emit(GroupDetailsEffect.OpenThread(groupId, notices = false))
            is GroupDetailsIntent.Respond -> respond(intent.intent)
            is GroupDetailsIntent.ToggleAutoConfirmation -> toggleAutoConfirmation(intent.enabled)
        }
    }

    // O mapa abre o endereço DO JOGO: a quadra padrão do grupo pode ser outra. Sem jogo (ou
    // com jogo sem endereço) vale a quadra padrão, que é o que a tela mostra nesse caso.
    private fun openMap() {
        val current = state.value
        val address = current.nextGame?.address?.takeIf(String::isNotBlank)
            ?: current.venue?.address?.takeIf(String::isNotBlank)
        update { it.copy(mapFailed = address == null) }
        if (address != null) emit(GroupDetailsEffect.OpenMap(address))
    }

    private fun onboardingAction() {
        val current = state.value
        if (current.isLoading || current.loadFailed || !current.isAdmin) return
        when (val guide = current.onboarding) {
            GroupOnboarding.CreateGame -> emit(GroupDetailsEffect.OpenCreateGame(groupId))
            is GroupOnboarding.InviteAthletes -> emit(GroupDetailsEffect.OpenInviteLink(groupId))
            is GroupOnboarding.ReviewFinances -> emit(GroupDetailsEffect.OpenSettlement(groupId, guide.gameId))
            null -> Unit
        }
    }

    private fun shareSaqz() {
        if (!state.value.athleteIntroVisible || state.value.isAdmin) return
        val generation = loadGeneration
        update { it.copy(athleteShareFailed = false) }
        viewModelScope.launch {
            val message = getString(Res.string.onboarding_athlete_share_message)
            if (generation == loadGeneration && state.value.athleteIntroVisible) {
                emit(GroupDetailsEffect.ShareSaqz(message))
            }
        }
    }

    private fun confirmDeparture() {
        if (state.value.isOwner || state.value.isLoading) return
        update { it.copy(confirmingLeave = true, leaveFailed = false) }
    }

    private fun cancelDeparture() {
        if (state.value.leaving) return
        update { it.copy(confirmingLeave = false, leaveFailed = false) }
    }

    private fun viewGame() {
        val game = state.value.nextGame ?: return
        emit(GroupDetailsEffect.OpenGame(groupId, game.gameId))
    }

    private fun notifyPending() {
        val current = state.value
        val game = current.nextGame ?: return
        if (!current.isAdmin || current.notifying || !game.confirmationOpen) return
        val request = reminderRequest?.takeIf { it.first == game.gameId }
            ?: (game.gameId to Uuid.random().toString()).also { reminderRequest = it }
        val generation = loadGeneration
        update { it.copy(notifying = true, notificationFailed = false, notifiedCount = null) }
        viewModelScope.launch {
            val result = communications.remind(GroupId(groupId), game.gameId, request.second)
            if (generation != loadGeneration || state.value.nextGame?.gameId != game.gameId) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    reminderRequest = null
                    update { it.copy(notifying = false, notifiedCount = result.value.recipientCount.toString()) }
                }
                is SaqzResult.Failure -> update { it.copy(notifying = false, notificationFailed = true) }
            }
        }
    }

    private fun leave() {
        val current = state.value
        if (!current.confirmingLeave || current.leaving || current.isOwner) return
        update { it.copy(leaving = true, leaveFailed = false) }
        viewModelScope.launch {
            when (departureGateway.leave(GroupId(groupId))) {
                is SaqzResult.Success -> {
                    loadGeneration++
                    update { it.copy(leaving = false, confirmingLeave = false) }
                    emit(GroupDetailsEffect.Left)
                }
                is SaqzResult.Failure -> update { it.copy(leaving = false, leaveFailed = true) }
            }
        }
    }

    private fun copyPix() {
        val pix = state.value.ownCharges?.pix ?: return
        emit(GroupDetailsEffect.CopyPix(pix.key))
        // Contador monotônico, não igualdade de valor: dois toques seguidos não podem deixar
        // o delay do primeiro apagar o estado do segundo (mesma regra da Início, VUL-220).
        val generation = ++pixCopiedGeneration
        update { it.copy(pixCopied = true, toast = GroupDetailsToast.PixCopied) }
        viewModelScope.launch {
            delay(PIX_COPIED_DWELL_MILLIS)
            if (generation == pixCopiedGeneration) update { it.copy(pixCopied = false) }
        }
    }

    private fun load() {
        val generation = ++loadGeneration
        responseGeneration++
        autoConfirmationGeneration++
        rosterGeneration++
        ownChargesGeneration++
        pixCopiedGeneration++
        loadedGroup = null
        update { it.copy(
            isLoading = true, loadFailed = false, error = null,
            onboarding = null,
            athleteIntroVisible = false, athleteShareFailed = false,
            notifying = false, notificationFailed = false, notifiedCount = null,
            pixCopied = false,
        ) }
        viewModelScope.launch {
            when (val groupResult = groupGateway.read(GroupId(groupId))) {
                is SaqzResult.Failure -> showFailure(generation, groupResult.error.toUiError())
                is SaqzResult.Success -> {
                    val group = groupResult.value.group
                    if (generation == loadGeneration) loadedGroup = group
                    when (val gamesResult = gameGateway.list(GroupId(groupId))) {
                        is SaqzResult.Failure -> showFailure(generation, gamesResult.error.toUiError())
                        is SaqzResult.Success -> {
                            loadNextGame(generation, group, gamesResult.value)
                            loadAdminCashbox(generation, group)
                            loadOwnCharges(generation, group)
                            loadLatestNotice(generation)
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadLatestNotice(generation: Int) {
        val result = communications.messages(GroupId(groupId), CommunicationChannel.NOTICE)
        if (generation != loadGeneration) return
        val latest = (result as? SaqzResult.Success)?.value?.items?.firstOrNull()
        update { it.copy(latestNotice = latest?.let { message ->
            NoticeUi(
                message.authorName, true, message.body,
                br.com.saqz.groups.presentation.communication.communicationTime(message.createdAt),
            )
        }) }
    }

    private fun retryOwnCharges() {
        val group = loadedGroup ?: return
        if (state.value.ownCharges?.isLoading == true) return
        val generation = loadGeneration
        viewModelScope.launch { loadOwnCharges(generation, group) }
    }

    /**
     * VUL-203 — o que **eu** devo neste grupo. Falha aqui não derruba a tela: a seção
     * mostra o próprio erro com "tentar novamente", e o resto do detalhe segue montado.
     */
    @Suppress("ReturnCount")
    private suspend fun loadOwnCharges(generation: Int, group: Group) {
        if (generation != loadGeneration) return
        val ownGeneration = ++ownChargesGeneration
        update { it.copy(ownCharges = OwnChargesUi(isLoading = true)) }
        val result = athleteFinanceGateway.ownCharges(GroupId(groupId))
        if (generation != loadGeneration || ownGeneration != ownChargesGeneration) return
        val ownCharges = when (result) {
            is SaqzResult.Failure -> OwnChargesUi(failed = true)
            // Formatar suspende (`getString`): a guarda é re-checada depois, não antes.
            is SaqzResult.Success -> result.value.charges.toOwnCharges(group)
        }
        if (generation != loadGeneration || ownGeneration != ownChargesGeneration) return
        update { it.copy(ownCharges = ownCharges) }
    }

    /**
     * Hoje é hoje **no fuso de cobrança do grupo**, nunca no do aparelho: é o que decide
     * "vence" ou "venceu" para quem viaja. O vencimento e a competência já chegam como
     * data civil do endpoint, então se formatam sem conversão de instante.
     */
    private suspend fun List<Charge>.toOwnCharges(group: Group): OwnChargesUi? {
        if (isEmpty()) return null
        val today = currentDate(group.timeZone.id)
        val pendingCharges = filter { it.status == ChargeStatus.Pending }.sortedBy { it.dueDate }
        val pending = pendingCharges.map { it.toOwnCharge(today) }
        // ponytail: histórico cortado nas 6 mais recentes — a lista não pagina e isto é um
        // `Column` não-lazy dentro da tela mais aberta do app; um mensalista de dois anos
        // comporia 24 linhas toda vez. Quem quiser tudo vai pelo extrato (VUL-176).
        val history = filterNot { it.status == ChargeStatus.Pending }
            .sortedByDescending { it.dueDate }
            .take(OWN_CHARGES_HISTORY_LIMIT)
            .map { it.toOwnCharge(today) }
        val pixKey = group.profile?.pixKey?.trim()?.takeIf { it.isNotEmpty() && pending.isNotEmpty() }
        return OwnChargesUi(
            pending = pending,
            history = history,
            pix = pixKey?.let { PixUi(it, group.profile?.pixLabel) },
            debt = groupOwnDebt(pendingCharges, pending, today, group.profile?.pixLabel, pixKey != null),
        )
    }

    private suspend fun Charge.toOwnCharge(today: LocalDate) = OwnChargeUi(
        id = id,
        title = when (kind) {
            ChargeKind.Game -> getString(Res.string.own_charges_game)
            ChargeKind.Monthly -> month?.monthName()
                ?.let { getString(Res.string.own_charges_monthly, it) }
                ?: getString(Res.string.own_charges_monthly_unknown)
        },
        dueLabel = getString(
            when {
                status != ChargeStatus.Pending -> Res.string.own_charges_due_history
                dueDate < today.toString() -> Res.string.own_charges_due_overdue
                else -> Res.string.own_charges_due
            },
            formatDate(dueDate),
        ),
        amountLabel = formatBrl(amountCents),
        status = status.toOwnChargeStatus(),
    )

    private fun ChargeStatus.toOwnChargeStatus() = when (this) {
        ChargeStatus.Pending -> OwnChargeStatusUi.Pending
        ChargeStatus.Paid -> OwnChargeStatusUi.Paid
        ChargeStatus.Waived -> OwnChargeStatusUi.Waived
        ChargeStatus.Cancelled -> OwnChargeStatusUi.Cancelled
    }

    /** "2026-08" → "Agosto". Chave inválida devolve `null` e o rótulo cai no genérico. */
    private suspend fun String.monthName(): String? = substringAfter('-', "")
        .toIntOrNull()
        ?.takeIf { it in 1..MONTHS_IN_YEAR }
        ?.let { getString(it.monthResource()) }

    private suspend fun formatDate(value: String): String {
        val date = runCatching { LocalDate.parse(value) }.getOrNull() ?: return value
        return getString(
            Res.string.own_charges_date,
            date.day.twoDigits(),
            (date.month.ordinal + 1).twoDigits(),
        )
    }

    private fun Int.twoDigits() = toString().padStart(2, '0')

    @Suppress("ReturnCount")
    private suspend fun loadAdminCashbox(generation: Int, group: Group) {
        if (generation != loadGeneration) return
        if (group.role == GroupRole.ATHLETE) {
            update { it.copy(cashbox = null) }
            return
        }
        val monthKey = currentDate(group.timeZone.id).monthKey()
        val statementResult = statementGateway.statement(
            GroupId(groupId),
            FinanceStatementQuery(month = monthKey),
        )
        if (generation != loadGeneration) return
        val chargesResult = organizerFinanceGateway.charges(GroupId(groupId))
        if (generation != loadGeneration) return
        val cashbox = if (statementResult is SaqzResult.Success && chargesResult is SaqzResult.Success) {
            val openMonthlyCount = chargesResult.value.charges.count {
                it.kind == ChargeKind.Monthly &&
                    it.status == ChargeStatus.Pending
            }
            CashboxUi(
                summary = "Saldo ${formatBrl(statementResult.value.summary.accumulatedBalanceCents)} · " +
                    "$openMonthlyCount mensalidades em aberto",
            )
        } else {
            CashboxUi()
        }
        if (generation != loadGeneration) return
        update { it.copy(cashbox = cashbox) }
    }

    @Suppress("ReturnCount")
    private suspend fun loadNextGame(generation: Int, group: Group, games: List<Game>) {
        if (generation != loadGeneration) return
        val game = games.nextPublishedGame(now.now())
        val onboarding = groupOnboarding(group.role, games, game?.id)
        if (game == null) {
            update {
                it.copy(
                    isLoading = false,
                    loadFailed = false,
                    error = null,
                    nextGame = null,
                    onboarding = onboarding,
                    attendance = null,
                    memberResponse = null,
                    membershipType = null,
                    autoConfirmationVisible = false,
                    waitlist = null,
                ).from(group)
            }
            return
        }
        val detailResult = attendanceGateway.read(GroupId(groupId), game.id)
        if (generation != loadGeneration) return
        val rosterResult = attendanceGateway.roster(GroupId(groupId), game.id)
        if (generation != loadGeneration) return
        val profileResult = athleteGateway.ownProfile()
        if (generation != loadGeneration) return

        when {
            detailResult is SaqzResult.Failure -> showFailure(generation, detailResult.error.toUiError())
            rosterResult is SaqzResult.Failure -> showFailure(generation, rosterResult.error.toUiError())
            profileResult is SaqzResult.Failure -> showFailure(generation, profileResult.error.toUiError())
            else -> {
                val detail = (detailResult as SaqzResult.Success).value
                val roster = (rosterResult as SaqzResult.Success).value
                val membershipType = (profileResult as SaqzResult.Success).value.memberships
                    .firstOrNull { it.groupId == GroupId(groupId) }
                    ?.membershipType
                val nextGame = game.toNextGame(detail, roster)
                // Formatar suspende (`getString`): a guarda é re-checada depois, não antes.
                if (generation != loadGeneration) return
                val response = detail.ownAttendance?.toResponse(roster)
                update {
                    it.copy(
                        isLoading = false,
                        loadFailed = false,
                        error = null,
                        nextGame = nextGame,
                        onboarding = onboarding,
                        attendance = detail.toAttendance(),
                        memberResponse = response,
                        waitlist = response.toWaitlist(roster, emptyList(), membershipType),
                        responding = false,
                        responseFailed = false,
                        membershipType = membershipType,
                        // Dono e admin também são atletas do grupo: quem é mensalista tem
                        // direito à auto-confirmação independente do papel administrativo.
                        autoConfirmationVisible = membershipType == AthleteMembershipType.MENSALISTA &&
                            group.gameConfig.autoConfirmEnabled,
                        autoConfirmationEnabled = detail.autoConfirmEnabled,
                        autoConfirmationUpdating = false,
                        autoConfirmationFailed = false,
                        rosterStale = false,
                        rosterRefreshing = false,
                    ).from(group)
                }
            }
        }
    }

    private fun respond(intent: AttendanceIntent) {
        val current = state.value
        val game = current.nextGame ?: return
        // Quem já está na fila só pode sair dela: um segundo "Vou" não muda nada no servidor
        // e o otimista piscaria "confirmado" (mesma guarda da Início).
        val alreadyQueued = current.memberResponse?.status == GroupDetailsResponseStatus.Waitlisted
        if (!game.confirmationOpen || current.responding || (alreadyQueued && intent == AttendanceIntent.Confirm)) return
        if (!game.confirmationIsOpen()) {
            update { it.copy(nextGame = game.copy(confirmationOpen = false)) }
            return
        }
        val generation = ++responseGeneration
        rosterGeneration++
        val loadAtStart = loadGeneration
        val previousResponse = current.memberResponse
        val previousWaitlist = current.waitlist
        val optimistic = current.optimisticStatus(game, intent)
        update {
            it.copy(
                memberResponse = GroupDetailsResponseUi(optimistic),
                waitlist = GroupDetailsResponseUi(optimistic)
                    .toWaitlist(null, it.waitlist?.rows.orEmpty(), it.membershipType),
                responding = true,
                athleteIntroVisible = false,
                responseFailed = false,
            )
        }
        viewModelScope.launch {
            val result = attendanceGateway.respond(
                GroupId(groupId),
                game.gameId,
                SelfAttendanceCommand(Uuid.random().toString(), intent),
            )
            if (generation != responseGeneration || loadAtStart != loadGeneration) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    val rosterResult = attendanceGateway.roster(GroupId(groupId), game.gameId)
                    if (generation != responseGeneration || loadAtStart != loadGeneration) return@launch
                    val roster = (rosterResult as? SaqzResult.Success)?.value
                    val detail = result.value.value.detail
                    val response = result.value.value.attendance.toResponse(roster)
                    val reconciled = if (roster != null) response.reconcileRoster(roster) else response
                    val showIntroduction = !state.value.isAdmin && !athleteIntroShown
                    if (showIntroduction) athleteIntroShown = true
                    update {
                        it.copy(
                            nextGame = it.nextGame?.reconcile(detail, roster),
                            attendance = detail.toAttendance(),
                            memberResponse = reconciled,
                            waitlist = reconciled.toWaitlist(roster, it.waitlist?.rows.orEmpty(), it.membershipType),
                            toast = reconciled.status.toToast(),
                            responding = false,
                            athleteIntroVisible = it.athleteIntroVisible || showIntroduction,
                            responseFailed = false,
                            rosterStale = rosterResult is SaqzResult.Failure,
                            rosterRefreshing = false,
                        )
                    }
                }
                is SaqzResult.Failure -> update {
                    it.copy(
                        memberResponse = previousResponse,
                        waitlist = previousWaitlist,
                        responding = false,
                        responseFailed = result.error != AttendanceError.Frozen,
                        nextGame = if (result.error == AttendanceError.DeadlinePassed || result.error == AttendanceError.Frozen) {
                            it.nextGame?.copy(confirmationOpen = false)
                        } else it.nextGame,
                    )
                }
            }
        }
    }

    private fun retryRoster() {
        val game = state.value.nextGame ?: return
        if (!state.value.rosterStale || state.value.rosterRefreshing || state.value.responding) return
        val generation = ++rosterGeneration
        val loadAtStart = loadGeneration
        update { it.copy(rosterRefreshing = true) }
        viewModelScope.launch {
            val roster = attendanceGateway.roster(GroupId(groupId), game.gameId)
            val detail = attendanceGateway.read(GroupId(groupId), game.gameId)
            when {
                roster is SaqzResult.Success && detail is SaqzResult.Success ->
                    if (generation == rosterGeneration && loadAtStart == loadGeneration) {
                    update {
                        val reconciled = it.memberResponse?.reconcileRoster(roster.value)
                        it.copy(
                            nextGame = it.nextGame?.reconcile(detail.value, roster.value),
                            attendance = detail.value.toAttendance(),
                            memberResponse = reconciled,
                            waitlist = reconciled.toWaitlist(roster.value, emptyList(), it.membershipType),
                            rosterStale = false,
                            rosterRefreshing = false,
                        )
                    }
                }
                else -> if (generation == rosterGeneration && loadAtStart == loadGeneration) {
                    update { it.copy(rosterStale = true, rosterRefreshing = false) }
                }
            }
        }
    }

    private fun toggleAutoConfirmation(enabled: Boolean) {
        val current = state.value
        if (!current.autoConfirmationVisible || current.autoConfirmationUpdating) return
        val gameId = current.nextGame?.gameId
        val generation = ++autoConfirmationGeneration
        val loadAtStart = loadGeneration
        val previous = current.autoConfirmationEnabled
        update {
            it.copy(
                autoConfirmationEnabled = enabled,
                autoConfirmationUpdating = true,
                autoConfirmationFailed = false,
            )
        }
        viewModelScope.launch {
            val result = attendanceGateway.updateAutoConfirmation(
                GroupId(groupId),
                AutoConfirmationCommand(enabled),
            )
            if (generation != autoConfirmationGeneration || loadAtStart != loadGeneration) return@launch
            when (result) {
                is SaqzResult.Success -> update {
                    it.copy(
                        autoConfirmationEnabled = result.value.enabled,
                        autoConfirmationUpdating = false,
                        autoConfirmationFailed = false,
                    )
                }
                is SaqzResult.Failure -> reconcileAutoConfirmationFailure(
                    error = result.error,
                    gameId = gameId,
                    generation = generation,
                    loadAtStart = loadAtStart,
                    previous = previous,
                )
            }
        }
    }

    private suspend fun reconcileAutoConfirmationFailure(
        error: AttendanceError,
        gameId: String?,
        generation: Long,
        loadAtStart: Int,
        previous: Boolean,
    ) {
        if (error !is AttendanceError.Data || gameId == null) {
            rollbackAutoConfirmation(previous, generation, loadAtStart)
            return
        }
        when (val detail = attendanceGateway.read(GroupId(groupId), gameId)) {
            is SaqzResult.Success -> if (generation == autoConfirmationGeneration && loadAtStart == loadGeneration) {
                update {
                    it.copy(
                        autoConfirmationEnabled = detail.value.autoConfirmEnabled,
                        autoConfirmationUpdating = false,
                        autoConfirmationFailed = false,
                    )
                }
            }
            is SaqzResult.Failure -> rollbackAutoConfirmation(previous, generation, loadAtStart)
        }
    }

    private fun rollbackAutoConfirmation(previous: Boolean, generation: Long, loadAtStart: Int) {
        if (generation != autoConfirmationGeneration || loadAtStart != loadGeneration) return
        update {
            it.copy(
                autoConfirmationEnabled = previous,
                autoConfirmationUpdating = false,
                autoConfirmationFailed = true,
            )
        }
    }

    private fun showFailure(generation: Int, error: GroupUiError) {
        if (generation != loadGeneration) return
        update { it.copy(isLoading = false, loadFailed = true, error = error) }
    }

    private fun AttendanceDetail.toAttendance() = AttendanceSummaryUi(
        confirmedCount = confirmedCount,
        capacity = capacity,
        going = confirmedCount,
        notGoing = declinedCount,
        pending = pendingCount,
        availableSpots = availableSpots,
    )

    private suspend fun Game.toNextGame(detail: AttendanceDetail, roster: AttendanceRoster): NextGameUi {
        val zone = gameTimeZone(zoneId)
        val startsAtLocal = runCatching { Instant.parse(startsAt).toLocalDateTime(zone) }.getOrNull()
        val deadlineLocal = runCatching { Instant.parse(confirmationDeadline).toLocalDateTime(zone) }.getOrNull()
        val place = listOfNotNull(venue.name, venue.court).joinToString(" — ")
        return NextGameUi(
            gameId = id,
            date = displayDate(),
            venue = place,
            deadline = displayDeadline(),
            confirmationDeadline = confirmationDeadline,
            confirmedCount = detail.confirmedCount,
            capacity = detail.capacity,
            confirmedNames = roster.confirmed.map { it.displayName },
            availableSpots = detail.availableSpots,
            confirmationOpen = status == GameStatus.Published && deadlineIsOpen(),
            hasGameFee = gameFeeCents != null,
            display = startsAtLocal?.gameHeroDisplay() ?: displayDate(),
            meta = startsAtLocal?.gameHeroMeta(place) ?: place,
            address = venue.address,
            deadlineLine = deadlineLocal?.gameDeadlineSentence(now.now().toLocalDateTime(zone).date).orEmpty(),
            deadlineShort = deadlineLocal?.gameDeadlineShort().orEmpty(),
            bellLabel = deadlineLocal?.gameBellLabel().orEmpty(),
        )
    }

    private fun NextGameUi.reconcile(detail: AttendanceDetail, roster: AttendanceRoster?): NextGameUi = copy(
        confirmedCount = detail.confirmedCount,
        capacity = detail.capacity,
        confirmedNames = roster?.confirmed?.map { it.displayName } ?: confirmedNames,
        availableSpots = detail.availableSpots,
    )

    private fun NextGameUi.reconcileRoster(roster: AttendanceRoster) = copy(
        confirmedNames = roster.confirmed.map { it.displayName },
    )

    private fun AttendanceEntry.toResponse(roster: AttendanceRoster?) = GroupDetailsResponseUi(
        status = status.toResponseStatus(),
        memberId = memberId,
        waitlistPosition = if (status == AttendanceStatus.Waitlisted) {
            roster?.waitlisted
                ?.indexOfFirst { it.memberId == memberId }
                ?.takeIf { it >= 0 }
                ?.let { it + 1L }
                ?: waitlistPosition
        } else null,
    )

    private fun NextGameUi.confirmationIsOpen() = runCatching {
        Instant.parse(confirmationDeadline) > now.now()
    }.getOrDefault(false)

    private fun GroupDetailsResponseUi.reconcileRoster(roster: AttendanceRoster): GroupDetailsResponseUi {
        val id = memberId ?: return this
        return when {
            roster.confirmed.any { it.memberId == id } -> copy(
                status = GroupDetailsResponseStatus.Confirmed,
                waitlistPosition = null,
            )
            roster.waitlisted.any { it.memberId == id } -> copy(
                status = GroupDetailsResponseStatus.Waitlisted,
                waitlistPosition = roster.waitlisted.indexOfFirst { it.memberId == id } + 1L,
            )
            status != GroupDetailsResponseStatus.Declined -> copy(
                status = GroupDetailsResponseStatus.Declined,
                waitlistPosition = null,
            )
            else -> this
        }
    }

    private fun AttendanceIntent.toResponseStatus() = when (this) {
        AttendanceIntent.Confirm -> GroupDetailsResponseStatus.Confirmed
        AttendanceIntent.Decline -> GroupDetailsResponseStatus.Declined
    }

    private fun AttendanceStatus.toResponseStatus() = when (this) {
        AttendanceStatus.Confirmed -> GroupDetailsResponseStatus.Confirmed
        AttendanceStatus.Declined -> GroupDetailsResponseStatus.Declined
        AttendanceStatus.Waitlisted -> GroupDetailsResponseStatus.Waitlisted
    }

    private fun GroupDetailsResponseStatus.toToast() = when (this) {
        GroupDetailsResponseStatus.Confirmed -> GroupDetailsToast.Confirmed
        GroupDetailsResponseStatus.Declined -> GroupDetailsToast.Declined
        GroupDetailsResponseStatus.Waitlisted -> GroupDetailsToast.Waitlisted
    }

    // A mesma previsão da Início: recusar é sempre recusa; quem já está confirmado continua;
    // avulso sob prioridade de mensalista e jogo lotado caem na fila. O servidor confirma.
    private fun GroupDetailsState.optimisticStatus(game: NextGameUi, intent: AttendanceIntent) = when {
        intent == AttendanceIntent.Decline -> GroupDetailsResponseStatus.Declined
        memberResponse?.status == GroupDetailsResponseStatus.Confirmed -> GroupDetailsResponseStatus.Confirmed
        waitlistKind(membershipType) == HomeWaitlistKind.AvulsoList -> GroupDetailsResponseStatus.Waitlisted
        game.confirmedCount >= game.capacity -> GroupDetailsResponseStatus.Waitlisted
        else -> GroupDetailsResponseStatus.Confirmed
    }

    /** Ponto único do tipo de espera, com a regra da Início: avulso + prioridade ⇒ lista do avulso. */
    private fun waitlistKind(membershipType: AthleteMembershipType?): HomeWaitlistKind =
        if (membershipType == AthleteMembershipType.AVULSO && loadedGroup?.gameConfig?.mensalistaPriority == true) {
            HomeWaitlistKind.AvulsoList
        } else {
            HomeWaitlistKind.Reserva
        }

    /** Fora da fila não há espera. Sem roster novo (otimista), a fila anterior é mantida. */
    private fun GroupDetailsResponseUi?.toWaitlist(
        roster: AttendanceRoster?,
        fallbackRows: List<HomeWaitlistRowUi>,
        membershipType: AthleteMembershipType?,
    ): GroupWaitlistUi? {
        if (this?.status != GroupDetailsResponseStatus.Waitlisted) return null
        val rows = roster?.waitlisted?.mapIndexed { index, member ->
            HomeWaitlistRowUi(name = member.displayName, position = index + 1L, isSelf = member.memberId == memberId)
        } ?: fallbackRows
        return GroupWaitlistUi(kind = waitlistKind(membershipType), rows = rows)
    }

    private fun Game.displayDate(): String {
        val date = runCatching { LocalDate.parse(localDate) }.getOrNull()
        return date?.let {
            "${it.day.toString().padStart(2, '0')}/${it.month.ordinal.plus(1).toString().padStart(2, '0')} · " +
                localTime.take(5)
        }
            ?: "$localDate · ${localTime.take(5)}"
    }

    private fun Game.displayDeadline(): String {
        val zone = runCatching { TimeZone.of(zoneId) }.getOrElse { TimeZone.UTC }
        val local = runCatching { Instant.parse(confirmationDeadline).toLocalDateTime(zone) }.getOrNull()
        return local?.let { "${it.hour.toString().padStart(2, '0')}h${it.minute.toString().padStart(2, '0')}" }
            ?: confirmationDeadline
    }

    private fun Game.deadlineIsOpen(): Boolean {
        return runCatching { Instant.parse(confirmationDeadline) > now.now() }
            .getOrDefault(true)
    }

    private fun currentDate(timeZoneId: String): LocalDate {
        val instant = now.now()
        return runCatching { instant.toLocalDateTime(TimeZone.of(timeZoneId)).date }
            .getOrElse { instant.toLocalDateTime(TimeZone.UTC).date }
    }

    private fun LocalDate.monthKey() = "$year-${month.ordinal.plus(1).toString().padStart(2, '0')}"
}

private const val MONTHS_IN_YEAR = 12
private const val OWN_CHARGES_HISTORY_LIMIT = 6
private const val PIX_COPIED_DWELL_MILLIS = 2_000L

// Os nomes de mês já existem no módulo (fluxo 5, caixa geral). Reusar é o que evita uma
// segunda tabela de doze strings dizendo a mesma coisa — e é por isso que esta é
// `internal`: a Home (VUL-202) formata a mesma competência e importa daqui em vez de
// escrever a terceira cópia. O `monthResource` da Home é outra coisa: a forma curta
// ("JUL") do card de data.
internal fun Int.monthResource(): StringResource = when (this) {
    1 -> Res.string.finance_overview_month_january
    2 -> Res.string.finance_overview_month_february
    3 -> Res.string.finance_overview_month_march
    4 -> Res.string.finance_overview_month_april
    5 -> Res.string.finance_overview_month_may
    6 -> Res.string.finance_overview_month_june
    7 -> Res.string.finance_overview_month_july
    8 -> Res.string.finance_overview_month_august
    9 -> Res.string.finance_overview_month_september
    10 -> Res.string.finance_overview_month_october
    11 -> Res.string.finance_overview_month_november
    else -> Res.string.finance_overview_month_december
}

private fun List<Game>.nextPublishedGame(now: Instant): Game? {
    val published = filter { it.status == GameStatus.Published }
    return published
        .mapNotNull { game -> runCatching { Instant.parse(game.startsAt) to game }.getOrNull() }
        .filter { it.first >= now }
        .minByOrNull { it.first }
        ?.second
}

private fun GroupDetailsState.from(group: Group): GroupDetailsState {
    val profile = group.profile
    return copy(
        isAdmin = group.role != GroupRole.ATHLETE,
        isOwner = group.role == GroupRole.OWNER,
        header = GroupHeaderUi(
            name = group.name,
            subtitle = listOfNotNull(
                profile?.composition?.label(),
                profile?.level?.label(),
            ).joinToString(" · ").ifBlank { group.timeZone.id },
            summaryChips = profile.toSummaryChips(),
            photoUrl = groupPhotoUrl(group.id.value, group.version),
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
