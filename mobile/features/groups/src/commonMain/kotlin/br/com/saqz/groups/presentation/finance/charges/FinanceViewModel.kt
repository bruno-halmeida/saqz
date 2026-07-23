package br.com.saqz.groups.presentation.finance.charges

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.formatting.parseBrlToCents
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.AthleteFinanceGateway
import br.com.saqz.groups.domain.finance.Charge
import br.com.saqz.groups.domain.finance.ChargeList
import br.com.saqz.groups.domain.finance.ChargeStatusCommand
import br.com.saqz.groups.domain.finance.FinanceError as DomainFinanceError
import br.com.saqz.groups.domain.finance.FinanceVersionToken
import br.com.saqz.groups.domain.finance.MonthlyChargeCommand
import br.com.saqz.groups.domain.finance.OrganizerFinanceGateway
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.membership.GroupMembershipGateway
import kotlinx.coroutines.launch

sealed interface ChargeFinanceCapability {
    data object Athlete : ChargeFinanceCapability
    data class Organizer(val gateway: OrganizerFinanceGateway) : ChargeFinanceCapability
}

class FinanceViewModel(
    rawGroupId: String,
    private val role: GroupRole,
    private val athlete: AthleteFinanceGateway,
    private val capability: ChargeFinanceCapability,
    private val memberships: GroupMembershipGateway?,
    private val drafts: MonthlyChargeDraftStorePort,
    private val keys: FinanceCommandKeyFactory,
) : MviViewModel<FinanceState, FinanceIntent, FinanceEffect>(FinanceState(GroupId(rawGroupId).value, role)) {
    private val groupId = GroupId(rawGroupId)
    private var retryOperation: FinanceOperation? = null

    private val organizerGateway: OrganizerFinanceGateway?
        get() = (capability as? ChargeFinanceCapability.Organizer)?.gateway

    init {
        restore()
        load()
    }

    override fun onIntent(intent: FinanceIntent) {
        when (intent) {
            FinanceIntent.Refresh -> load()
            is FinanceIntent.UpdateMonthly -> updateMonthly(intent)
            FinanceIntent.ReviewMonthly -> review()
            FinanceIntent.GenerateMonthly -> generate()
            is FinanceIntent.UpdateStatus -> status(intent)
            FinanceIntent.Retry -> retryOperation?.let(::execute)
        }
    }

    private fun restore() {
        if (organizerGateway == null) return
        drafts.read(groupId.value) { result ->
            when (result) {
                is MonthlyDraftReadResult.Success -> result.draft
                    ?.takeIf {
                        it.schemaVersion == MonthlyChargeDraft.CURRENT_SCHEMA &&
                            it.groupId == groupId.value
                    }
                    ?.let { draft -> update { it.copy(monthlyDraft = draft) } }
                MonthlyDraftReadResult.Failure -> {
                    update { it.copy(error = FinanceError.DRAFT_UNAVAILABLE) }
                }
            }
        }
    }

    private fun load() {
        if (state.value.isMutating) return
        update {
            it.copy(
                isLoading = true,
                error = null,
                reloadAvailable = false,
            )
        }
        viewModelScope.launch {
            if (state.value.organizer) loadOrganizer() else loadAthlete()
        }
    }

    private suspend fun loadAthlete() {
        when (val result = athlete.ownCharges(groupId)) {
            is SaqzResult.Success -> update {
                it.copy(
                    charges = result.value.charges,
                    totals = null,
                    members = emptyList(),
                    monthlyDraft = null,
                    isLoading = false,
                    error = null,
                )
            }
            is SaqzResult.Failure -> fail(result.error)
        }
    }

    private suspend fun loadOrganizer() {
        val gateway = organizerGateway ?: return fail(DomainFinanceError.Forbidden)
        val membershipGateway = memberships ?: return fail(DomainFinanceError.Forbidden)

        when (val chargeResult = gateway.charges(groupId)) {
            is SaqzResult.Failure -> fail(chargeResult.error)
            is SaqzResult.Success -> when (val memberResult = membershipGateway.listMemberships(groupId)) {
                is SaqzResult.Failure -> fail(DomainFinanceError.Data(br.com.saqz.domain.DataError.Unknown))
                is SaqzResult.Success -> update {
                    it.copy(
                        charges = chargeResult.value.charges,
                        members = memberResult.value,
                        totals = chargeResult.value.toChargeTotalsState(),
                        isLoading = false,
                        error = null,
                    )
                }
            }
        }
    }

    private fun updateMonthly(intent: FinanceIntent.UpdateMonthly) {
        val current = state.value
        if (!current.organizer || current.isMutating) return

        val selected = intent.memberIds.intersect(current.members.map { it.userId }.toSet())
        val draft = MonthlyChargeDraft(
            groupId = groupId.value,
            commandKey = current.monthlyDraft?.commandKey ?: keys.create(),
            month = intent.month,
            amountBrl = intent.amountBrl,
            dueDate = intent.dueDate,
            selectedMemberIds = selected,
            reviewed = false,
        )
        update {
            it.copy(
                monthlyDraft = draft,
                fieldErrors = emptyMap(),
                error = null,
                reloadAvailable = false,
            )
        }
        persist(draft)
    }

    private fun review() {
        val current = state.value
        val draft = current.monthlyDraft ?: return
        if (!current.organizer || current.isMutating) return

        val errors = draft.validate()
        if (errors.isNotEmpty()) {
            update { it.copy(fieldErrors = errors, error = FinanceError.VALIDATION) }
            return
        }

        val reviewed = draft.copy(reviewed = true)
        update {
            it.copy(
                monthlyDraft = reviewed,
                fieldErrors = emptyMap(),
                error = null,
            )
        }
        persist(reviewed)
    }

    private fun generate() {
        val current = state.value
        val draft = current.monthlyDraft ?: return
        if (!current.organizer || current.isMutating || !draft.reviewed) return
        execute(FinanceOperation.Monthly(draft))
    }

    private fun status(intent: FinanceIntent.UpdateStatus) {
        val current = state.value
        if (!current.organizer || current.isMutating) return

        val charge = current.charges.firstOrNull { it.id == intent.chargeId } ?: return
        execute(
            FinanceOperation.Status(
                chargeId = charge.id,
                version = FinanceVersionToken("\"${charge.version}\""),
                command = ChargeStatusCommand(
                    status = intent.status,
                    note = intent.note?.trim()?.ifBlank { null },
                ),
            ),
        )
    }

    private fun execute(operation: FinanceOperation) {
        val current = state.value
        val gateway = organizerGateway ?: return fail(DomainFinanceError.Forbidden)
        if (!current.organizer || current.isMutating) return

        retryOperation = operation
        update {
            it.copy(
                isMutating = true,
                error = null,
                retryAvailable = false,
                fieldErrors = emptyMap(),
            )
        }
        viewModelScope.launch {
            when (operation) {
                is FinanceOperation.Monthly -> executeMonthly(gateway, operation)
                is FinanceOperation.Status -> executeStatus(gateway, operation)
            }
        }
    }

    private suspend fun executeMonthly(
        gateway: OrganizerFinanceGateway,
        operation: FinanceOperation.Monthly,
    ) {
        val draft = operation.draft
        val command = MonthlyChargeCommand(
            requestId = draft.commandKey,
            month = draft.month,
            amountCents = requireNotNull(parseBrlToCents(draft.amountBrl)),
            dueDate = draft.dueDate,
            memberIds = draft.selectedMemberIds,
        )
        when (val result = gateway.generateMonthly(groupId, command)) {
            is SaqzResult.Success -> {
                retryOperation = null
                monthlyApplied(gateway, draft, result.value)
            }
            is SaqzResult.Failure -> fail(result.error)
        }
    }

    private suspend fun executeStatus(
        gateway: OrganizerFinanceGateway,
        operation: FinanceOperation.Status,
    ) {
        when (
            val result = gateway.updateChargeStatus(
                groupId,
                operation.chargeId,
                operation.version,
                operation.command,
            )
        ) {
            is SaqzResult.Success -> {
                retryOperation = null
                statusApplied(result.value.charge)
            }
            is SaqzResult.Failure -> fail(result.error)
        }
    }

    private suspend fun monthlyApplied(
        gateway: OrganizerFinanceGateway,
        draft: MonthlyChargeDraft,
        generated: ChargeList,
    ) {
        drafts.clear(groupId.value, draft.commandKey) {}
        val refreshed = when (val result = gateway.charges(groupId)) {
            is SaqzResult.Success -> result.value
            is SaqzResult.Failure -> generated
        }
        update {
            it.copy(
                charges = refreshed.charges,
                totals = refreshed.toChargeTotalsState() ?: it.totals,
                isMutating = false,
                monthlyDraft = null,
                error = null,
                reloadAvailable = false,
                retryAvailable = false,
                lastManualOutcome = "Cobranças registradas manualmente.",
            )
        }
        emit(FinanceEffect.MonthlyGenerated(generated.charges.size))
    }

    private fun statusApplied(charge: Charge) {
        update { s ->
            s.copy(
                charges = s.charges.map { if (it.id == charge.id) charge else it },
                isMutating = false,
                error = null,
                reloadAvailable = false,
                retryAvailable = false,
                lastManualOutcome = "Status registrado manualmente no histórico.",
            )
        }
        emit(FinanceEffect.StatusRecorded(charge.id, charge.status))
    }

    private fun persist(draft: MonthlyChargeDraft) {
        drafts.write(draft) { result ->
            if (result == MonthlyDraftWriteResult.Failure) {
                update { it.copy(error = FinanceError.DRAFT_UNAVAILABLE) }
            }
        }
    }

    private fun fail(failure: DomainFinanceError) {
        val error = failure.toPresentationError()
        update {
            it.copy(
                isLoading = false,
                isMutating = false,
                error = error,
                fieldErrors = (failure as? DomainFinanceError.Validation)
                    ?.error
                    ?.details
                    ?.fieldMessages
                    .orEmpty(),
                reloadAvailable = error == FinanceError.CONFLICT,
                retryAvailable = error == FinanceError.CONFLICT || error == FinanceError.UNAVAILABLE,
            )
        }
    }

    private fun DomainFinanceError.toPresentationError() = when (this) {
        is DomainFinanceError.Validation -> FinanceError.VALIDATION
        DomainFinanceError.Conflict -> FinanceError.CONFLICT
        DomainFinanceError.HiddenResource -> FinanceError.HIDDEN
        DomainFinanceError.Forbidden -> FinanceError.FORBIDDEN
        DomainFinanceError.InvalidLifecycle -> FinanceError.INVALID_LIFECYCLE
        else -> FinanceError.UNAVAILABLE
    }
}
