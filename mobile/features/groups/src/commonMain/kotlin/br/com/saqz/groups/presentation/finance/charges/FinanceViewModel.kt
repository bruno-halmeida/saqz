package br.com.saqz.groups.presentation.finance.charges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.RolesInvitesGateway
import br.com.saqz.groups.data.finance.AthleteFinanceGateway
import br.com.saqz.groups.data.finance.ChargeDto
import br.com.saqz.groups.data.finance.ChargeListDto
import br.com.saqz.groups.data.finance.ChargeStatusCommandDto
import br.com.saqz.groups.data.finance.ChargeStatusDto
import br.com.saqz.groups.data.finance.FinanceGatewayFailure
import br.com.saqz.groups.data.finance.MonthlyChargeCommandDto
import br.com.saqz.groups.data.finance.OrganizerFinanceGateway
import br.com.saqz.groups.data.finance.toFinanceGatewayFailure
import br.com.saqz.network.NetworkResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class FinanceViewModel(
    private val groupId: String,
    private val role: GroupRoleDto,
    private val athlete: AthleteFinanceGateway,
    private val organizer: OrganizerFinanceGateway?,
    private val memberships: RolesInvitesGateway?,
    private val drafts: MonthlyChargeDraftStorePort,
    private val keys: FinanceCommandKeyFactory,
    testScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = testScope ?: viewModelScope
    private val mutable = MutableStateFlow(FinanceState(groupId, role))
    val state: StateFlow<FinanceState> = mutable.asStateFlow()
    private val channel = Channel<FinanceEffect>(Channel.BUFFERED)
    val effects: Flow<FinanceEffect> = channel.receiveAsFlow()
    private var retry: FinanceOperation? = null

    init {
        restore()
        load()
    }

    fun onIntent(intent: FinanceIntent) {
        when (intent) {
            FinanceIntent.Refresh -> load()
            is FinanceIntent.UpdateMonthly -> updateMonthly(intent)
            FinanceIntent.ReviewMonthly -> review()
            FinanceIntent.GenerateMonthly -> generate()
            is FinanceIntent.UpdateStatus -> status(intent)
            FinanceIntent.Retry -> retry?.let(::execute)
        }
    }

    private fun restore() {
        if (!mutable.value.organizer) return
        drafts.read(groupId) { result ->
            when (result) {
                is MonthlyDraftReadResult.Success -> result.draft
                    ?.takeIf {
                        it.schemaVersion == MonthlyChargeDraft.CURRENT_SCHEMA &&
                            it.groupId == groupId
                    }
                    ?.let { mutable.value = mutable.value.copy(monthlyDraft = it) }

                MonthlyDraftReadResult.Failure -> {
                    mutable.value = mutable.value.copy(error = FinanceError.DRAFT_UNAVAILABLE)
                }
            }
        }
    }

    private fun load() {
        if (mutable.value.isMutating) return
        mutable.value = mutable.value.copy(
            isLoading = true,
            error = null,
            reloadAvailable = false,
        )
        scope.launch {
            if (mutable.value.organizer) {
                loadOrganizer()
            } else {
                loadAthlete()
            }
        }
    }

    private suspend fun loadAthlete() {
        when (val result = athlete.ownCharges(groupId)) {
            is NetworkResult.Success -> {
                mutable.value = mutable.value.copy(
                    charges = result.value.charges,
                    totals = null,
                    members = emptyList(),
                    monthlyDraft = null,
                    isLoading = false,
                    error = null,
                )
            }

            is NetworkResult.Failure -> failed(result.error.toFinanceGatewayFailure())
        }
    }

    private suspend fun loadOrganizer() {
        val gateway = organizer ?: return failed(FinanceGatewayFailure.Forbidden)
        val roles = memberships ?: return failed(FinanceGatewayFailure.Forbidden)

        when (val chargeResult = gateway.charges(groupId)) {
            is NetworkResult.Failure -> failed(chargeResult.error.toFinanceGatewayFailure())

            is NetworkResult.Success -> when (val memberResult = roles.listMemberships(groupId)) {
                is NetworkResult.Failure -> failed(FinanceGatewayFailure.Temporary)

                is NetworkResult.Success -> {
                    val value = chargeResult.value
                    mutable.value = mutable.value.copy(
                        charges = value.charges,
                        members = memberResult.value,
                        totals = value.totals(),
                        isLoading = false,
                        error = null,
                    )
                }
            }
        }
    }

    private fun updateMonthly(intent: FinanceIntent.UpdateMonthly) {
        val current = mutable.value
        if (!current.organizer || current.isMutating) return

        val selected = intent.memberIds.intersect(current.members.map { it.userId }.toSet())
        val old = current.monthlyDraft
        val draft = MonthlyChargeDraft(
            groupId = groupId,
            commandKey = old?.commandKey ?: keys.create(),
            month = intent.month,
            amountBrl = intent.amountBrl,
            dueDate = intent.dueDate,
            selectedMemberIds = selected,
            reviewed = false,
        )

        mutable.value = current.copy(
            monthlyDraft = draft,
            fieldErrors = emptyMap(),
            error = null,
            reloadAvailable = false,
        )
        persist(draft)
    }

    private fun review() {
        val current = mutable.value
        val draft = current.monthlyDraft ?: return
        if (!current.organizer || current.isMutating) return

        val errors = draft.validate()
        if (errors.isNotEmpty()) {
            mutable.value = current.copy(fieldErrors = errors, error = FinanceError.VALIDATION)
            return
        }

        val reviewed = draft.copy(reviewed = true)
        mutable.value = current.copy(
            monthlyDraft = reviewed,
            fieldErrors = emptyMap(),
            error = null,
        )
        persist(reviewed)
    }

    private fun generate() {
        val current = mutable.value
        val draft = current.monthlyDraft ?: return
        if (!current.organizer || current.isMutating || !draft.reviewed) return
        execute(FinanceOperation.Monthly(draft))
    }

    private fun status(intent: FinanceIntent.UpdateStatus) {
        val current = mutable.value
        if (!current.organizer || current.isMutating) return

        val charge = current.charges.firstOrNull { it.id == intent.chargeId } ?: return
        execute(
            FinanceOperation.Status(
                charge.id,
                "\"${charge.version}\"",
                ChargeStatusCommandDto(intent.status, intent.note?.trim()?.ifBlank { null }),
            ),
        )
    }

    private fun execute(operation: FinanceOperation) {
        val gateway = organizer ?: return
        val current = mutable.value
        if (!current.organizer || current.isMutating) return

        retry = operation
        mutable.value = current.copy(
            isMutating = true,
            error = null,
            retryAvailable = false,
            fieldErrors = emptyMap(),
        )

        scope.launch {
            when (operation) {
                is FinanceOperation.Monthly -> {
                    val draft = operation.draft
                    when (
                        val result = gateway.generateMonthly(
                            groupId,
                            MonthlyChargeCommandDto(
                                draft.commandKey,
                                draft.month,
                                requireNotNull(draft.amountBrl.brlToCents()),
                                draft.dueDate,
                                draft.selectedMemberIds,
                            ),
                        )
                    ) {
                        is NetworkResult.Success -> monthlyApplied(gateway, draft, result.value)
                        is NetworkResult.Failure -> failed(result.error.toFinanceGatewayFailure())
                    }
                }

                is FinanceOperation.Status -> when (
                    val result = gateway.updateChargeStatus(
                        groupId,
                        operation.chargeId,
                        operation.etag,
                        operation.command,
                    )
                ) {
                    is NetworkResult.Success -> statusApplied(result.value.charge)
                    is NetworkResult.Failure -> failed(result.error.toFinanceGatewayFailure())
                }
            }
        }
    }

    private suspend fun monthlyApplied(
        gateway: OrganizerFinanceGateway,
        draft: MonthlyChargeDraft,
        generated: ChargeListDto,
    ) {
        retry = null
        drafts.clear(groupId, draft.commandKey) {}

        val refreshed = when (val result = gateway.charges(groupId)) {
            is NetworkResult.Success -> result.value
            is NetworkResult.Failure -> generated
        }

        mutable.value = mutable.value.copy(
            charges = refreshed.charges,
            totals = refreshed.totals() ?: mutable.value.totals,
            isMutating = false,
            monthlyDraft = null,
            error = null,
            reloadAvailable = false,
            retryAvailable = false,
            lastManualOutcome = "Cobranças registradas manualmente.",
        )
        channel.trySend(FinanceEffect.MonthlyGenerated(generated.charges.size))
    }

    private fun statusApplied(charge: ChargeDto) {
        retry = null
        mutable.value = mutable.value.copy(
            charges = mutable.value.charges.map { if (it.id == charge.id) charge else it },
            isMutating = false,
            error = null,
            reloadAvailable = false,
            retryAvailable = false,
            lastManualOutcome = "Status registrado manualmente no histórico.",
        )
        channel.trySend(FinanceEffect.StatusRecorded(charge.id, charge.status))
    }

    private fun persist(draft: MonthlyChargeDraft) {
        drafts.write(draft) {
            if (it == MonthlyDraftWriteResult.Failure) {
                mutable.value = mutable.value.copy(error = FinanceError.DRAFT_UNAVAILABLE)
            }
        }
    }

    private fun failed(failure: FinanceGatewayFailure) {
        val error = when (failure) {
            is FinanceGatewayFailure.Validation -> FinanceError.VALIDATION
            FinanceGatewayFailure.Conflict -> FinanceError.CONFLICT
            FinanceGatewayFailure.HiddenResource -> FinanceError.HIDDEN
            FinanceGatewayFailure.Forbidden -> FinanceError.FORBIDDEN
            FinanceGatewayFailure.InvalidLifecycle -> FinanceError.INVALID_LIFECYCLE
            else -> FinanceError.UNAVAILABLE
        }
        mutable.value = mutable.value.copy(
            isLoading = false,
            isMutating = false,
            error = error,
            fieldErrors = (failure as? FinanceGatewayFailure.Validation)?.fields.orEmpty(),
            reloadAvailable = error == FinanceError.CONFLICT,
            retryAvailable = error == FinanceError.CONFLICT || error == FinanceError.UNAVAILABLE,
        )
    }
}
