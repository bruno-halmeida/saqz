package br.com.saqz.groups.presentation.finance.expenses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.serialization.saved
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import kotlinx.serialization.Serializable
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.Expense
import br.com.saqz.groups.domain.finance.ExpenseCategory
import br.com.saqz.groups.domain.finance.ExpenseStatus
import br.com.saqz.groups.domain.finance.FinanceError as DomainFinanceError
import br.com.saqz.groups.domain.finance.FinanceVersionToken
import br.com.saqz.groups.domain.finance.OrganizerFinanceGateway
import br.com.saqz.groups.domain.group.GroupRole
import kotlinx.coroutines.launch

sealed interface ExpenseFinanceCapability {
    data object Athlete : ExpenseFinanceCapability
    data class Organizer(val gateway: OrganizerFinanceGateway) : ExpenseFinanceCapability
}

class ExpenseViewModel(
    rawGroupId: String,
    private val role: GroupRole,
    private val capability: ExpenseFinanceCapability,
    private val drafts: ExpenseDraftStorePort,
    private val keys: ExpenseCommandKeyFactory,
    savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : MviViewModel<ExpenseState, ExpenseIntent, ExpenseEffect>(initialExpenseState(rawGroupId, role)) {
    private val groupId = GroupId(rawGroupId)
    private var retryOperation: ExpenseOperation? = null
    private val organizerGateway get() = (capability as? ExpenseFinanceCapability.Organizer)?.gateway

    // Durable draft repo is the source of truth (PMVI-020); the snapshot only carries
    // the identifier needed to reload the in-progress draft after process death (PMVI-018).
    private var reloadKeys by savedStateHandle.saved { ExpenseReloadKeys() }

    init {
        if (state.value.organizer) {
            restore()
            load()
        }
    }

    override fun onIntent(intent: ExpenseIntent) {
        when (intent) {
            ExpenseIntent.Refresh -> load()
            ExpenseIntent.OpenCreate -> openCreate()
            is ExpenseIntent.OpenEdit -> openEdit(intent.expenseId)
            is ExpenseIntent.UpdateForm -> updateForm(intent.form)
            ExpenseIntent.Submit -> submit()
            is ExpenseIntent.RequestVoid -> requestVoid(intent.expenseId)
            ExpenseIntent.DismissVoid -> if (!state.value.isMutating) {
                update { it.copy(pendingVoid = null) }
            }
            ExpenseIntent.ConfirmVoid -> confirmVoid()
            ExpenseIntent.Retry -> retryOperation?.let(::execute)
        }
    }

    private fun restore() {
        if (organizerGateway == null) return
        drafts.read(groupId.value, reloadKeys.expenseId) { result ->
            when (result) {
                is ExpenseDraftReadResult.Success -> result.draft
                    ?.takeIf { it.schemaVersion == ExpenseDraft.CURRENT_SCHEMA && it.groupId == groupId.value }
                    ?.let { draft -> update { it.copy(draft = draft) } }
                ExpenseDraftReadResult.Failure -> update { it.copy(error = ExpenseError.DRAFT_UNAVAILABLE) }
            }
        }
    }

    private fun load() {
        val current = state.value
        if (!current.organizer || current.isMutating) return
        val api = organizerGateway ?: return fail(DomainFinanceError.Forbidden)

        update { it.copy(isLoading = true, error = null, reloadAvailable = false) }
        viewModelScope.launch {
            when (val listed = api.expenses(groupId)) {
                is SaqzResult.Failure -> fail(listed.error)

                is SaqzResult.Success -> when (val totals = api.totals(groupId)) {
                    is SaqzResult.Failure -> fail(totals.error)
                    is SaqzResult.Success -> update {
                        it.copy(
                            expenses = listed.value.expenses,
                            totals = totals.value,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
            }
        }
    }

    private fun openCreate() {
        val current = state.value
        if (!current.organizer || current.isMutating) return

        val draft = ExpenseDraft(
            groupId = groupId.value,
            commandKey = keys.create(),
            form = ExpenseForm(),
        )
        reloadKeys = ExpenseReloadKeys(expenseId = null)
        update { it.copy(draft = draft, fieldErrors = emptyMap(), error = null) }
        persist(draft)
    }

    private fun openEdit(id: String) {
        val current = state.value
        if (!current.organizer || current.isMutating) return

        val expense = current.expenses.firstOrNull { it.id == id } ?: return
        val draft = ExpenseDraft(
            groupId = groupId.value,
            expenseId = id,
            etag = "\"${expense.version}\"",
            commandKey = keys.create(),
            form = expense.toExpenseForm(),
        )
        reloadKeys = ExpenseReloadKeys(expenseId = id)
        update { it.copy(draft = draft, fieldErrors = emptyMap(), error = null) }
        persist(draft)
    }

    private fun updateForm(form: ExpenseForm) {
        val current = state.value
        val draft = current.draft ?: return
        if (!current.organizer || current.isMutating) return

        val normalized = if (form.category != ExpenseCategory.Other) {
            form.copy(customCategory = "")
        } else {
            form
        }
        val changed = draft.copy(form = normalized)
        update {
            it.copy(
                draft = changed,
                fieldErrors = emptyMap(),
                error = null,
                reloadAvailable = false,
            )
        }
        persist(changed)
    }

    private fun submit() {
        val current = state.value
        val draft = current.draft ?: return
        if (!current.organizer || current.isMutating) return

        val errors = draft.form.validate()
        if (errors.isNotEmpty()) {
            update { it.copy(fieldErrors = errors, error = ExpenseError.VALIDATION) }
            return
        }

        execute(ExpenseOperation.Save(draft))
    }

    private fun requestVoid(id: String) {
        val current = state.value
        if (!current.organizer || current.isMutating) return

        current.expenses.firstOrNull { it.id == id && it.status == ExpenseStatus.Active }
            ?.let { expense -> update { it.copy(pendingVoid = expense, error = null) } }
    }

    private fun confirmVoid() {
        val current = state.value
        val expense = current.pendingVoid ?: return
        if (!current.organizer || current.isMutating) return
        execute(ExpenseOperation.Void(expense.id, FinanceVersionToken("\"${expense.version}\"")))
    }

    private fun execute(operation: ExpenseOperation) {
        val current = state.value
        val api = organizerGateway ?: return fail(DomainFinanceError.Forbidden)
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
                is ExpenseOperation.Save -> {
                    val draft = operation.draft
                    val command = draft.form.toExpenseWriteCommand(
                        if (draft.expenseId == null) draft.commandKey else null,
                    )
                    val result = if (draft.expenseId == null) {
                        api.createExpense(groupId, command)
                    } else {
                        api.editExpense(groupId, draft.expenseId, FinanceVersionToken(requireNotNull(draft.etag)), command)
                    }

                    when (result) {
                        is SaqzResult.Success -> {
                            retryOperation = null
                            saved(api, draft, result.value.expense)
                        }
                        is SaqzResult.Failure -> fail(result.error)
                    }
                }

                is ExpenseOperation.Void -> when (val result = api.voidExpense(groupId, operation.expenseId, operation.version)) {
                    is SaqzResult.Success -> {
                        retryOperation = null
                        voided(api, result.value.expense)
                    }
                    is SaqzResult.Failure -> fail(result.error)
                }
            }
        }
    }

    private suspend fun saved(
        api: OrganizerFinanceGateway,
        draft: ExpenseDraft,
        expense: Expense,
    ) {
        drafts.clear(groupId.value, draft.expenseId, draft.commandKey) {}
        reloadKeys = ExpenseReloadKeys()
        refreshAfter(api)
        update {
            it.copy(
                draft = null,
                isMutating = false,
                error = null,
                reloadAvailable = false,
                retryAvailable = false,
                lastAuditOutcome = "Despesa registrada no histórico manual.",
            )
        }
        emit(ExpenseEffect.Saved(expense.id))
    }

    private suspend fun voided(api: OrganizerFinanceGateway, expense: Expense) {
        refreshAfter(api)
        update {
            it.copy(
                pendingVoid = null,
                isMutating = false,
                error = null,
                reloadAvailable = false,
                retryAvailable = false,
                lastAuditOutcome = "Despesa anulada no histórico manual.",
            )
        }
        emit(ExpenseEffect.Voided(expense.id))
    }

    private suspend fun refreshAfter(api: OrganizerFinanceGateway) {
        val listed = api.expenses(groupId)
        val totals = api.totals(groupId)
        if (listed is SaqzResult.Success) {
            update { it.copy(expenses = listed.value.expenses) }
        }
        if (totals is SaqzResult.Success) {
            update { it.copy(totals = totals.value) }
        }
    }

    private fun persist(draft: ExpenseDraft) {
        drafts.write(draft) {
            if (it == ExpenseDraftWriteResult.Failure) {
                update { current -> current.copy(error = ExpenseError.DRAFT_UNAVAILABLE) }
            }
        }
    }

    private fun fail(failure: DomainFinanceError) {
        val error = when (failure) {
            is DomainFinanceError.Validation -> ExpenseError.VALIDATION
            DomainFinanceError.Conflict -> ExpenseError.CONFLICT
            DomainFinanceError.HiddenResource -> ExpenseError.HIDDEN
            DomainFinanceError.Forbidden -> ExpenseError.FORBIDDEN
            DomainFinanceError.InvalidLifecycle -> ExpenseError.INVALID_LIFECYCLE
            else -> ExpenseError.UNAVAILABLE
        }
        update {
            it.copy(
                isLoading = false,
                isMutating = false,
                error = error,
                fieldErrors = (failure as? DomainFinanceError.Validation)?.error?.details?.fieldMessages.orEmpty(),
                reloadAvailable = error == ExpenseError.CONFLICT,
                retryAvailable = error == ExpenseError.CONFLICT || error == ExpenseError.UNAVAILABLE,
            )
        }
    }
}

@Serializable
private data class ExpenseReloadKeys(val expenseId: String? = null)

private fun initialExpenseState(rawGroupId: String, role: GroupRole) = ExpenseState(
    GroupId(rawGroupId).value,
    role,
    isLoading = role == GroupRole.OWNER || role == GroupRole.ADMIN,
)
