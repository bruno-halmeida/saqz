package br.com.saqz.groups.presentation.finance.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.finance.ExpenseCategoryDto
import br.com.saqz.groups.data.finance.ExpenseDto
import br.com.saqz.groups.data.finance.ExpenseListDto
import br.com.saqz.groups.data.finance.ExpenseStatusDto
import br.com.saqz.groups.data.finance.FinanceGatewayFailure
import br.com.saqz.groups.data.finance.toFinanceGatewayFailure
import br.com.saqz.groups.data.finance.MonthlyChargeCommandDto
import br.com.saqz.groups.data.finance.OrganizerFinanceGateway
import br.com.saqz.groups.data.finance.ExpenseWriteCommandDto
import br.com.saqz.groups.data.finance.ChargeStatusCommandDto
import br.com.saqz.network.NetworkResult
import br.com.saqz.groups.presentation.finance.DraftMutationSupport
import br.com.saqz.groups.presentation.finance.FinanceCapability
import br.com.saqz.groups.presentation.finance.FinanceMutationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ExpenseViewModel(
    private val groupId: String,
    private val role: GroupRoleDto,
    private val capability: FinanceCapability,
    private val drafts: ExpenseDraftStorePort,
    private val keys: ExpenseCommandKeyFactory,
    testScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = testScope ?: viewModelScope
    private val mutable = MutableStateFlow(
        ExpenseState(
            groupId,
            role,
            isLoading = role == GroupRoleDto.OWNER || role == GroupRoleDto.ADMIN,
        ),
    )
    val state: StateFlow<ExpenseState> = mutable.asStateFlow()
    private val channel = Channel<ExpenseEffect>(Channel.BUFFERED)
    val effects: Flow<ExpenseEffect> = channel.receiveAsFlow()
    private val mutations = DraftMutationSupport<ExpenseState, ExpenseDraft, ExpenseOperation, ExpenseError>(
        capability = capability,
        scope = scope,
        state = { mutable.value },
        setState = { mutable.value = it },
        canMutate = { it.organizer && !it.isMutating },
        mutatingState = {
            it.copy(
                isMutating = true,
                error = null,
                retryAvailable = false,
                fieldErrors = emptyMap(),
            )
        },
        failedState = { current, failure, error ->
            current.copy(
                isLoading = false,
                isMutating = false,
                error = error,
                fieldErrors = (failure as? FinanceGatewayFailure.Validation)?.fields.orEmpty(),
                reloadAvailable = error == ExpenseError.CONFLICT,
                retryAvailable = error == ExpenseError.CONFLICT || error == ExpenseError.UNAVAILABLE,
            )
        },
        mapFailure = ::mapError,
    )

    init {
        if (mutable.value.organizer) {
            restore()
            load()
        }
    }

    fun onIntent(intent: ExpenseIntent) {
        when (intent) {
            ExpenseIntent.Refresh -> load()
            ExpenseIntent.OpenCreate -> openCreate()
            is ExpenseIntent.OpenEdit -> openEdit(intent.expenseId)
            is ExpenseIntent.UpdateForm -> update(intent.form)
            ExpenseIntent.Submit -> submit()
            is ExpenseIntent.RequestVoid -> requestVoid(intent.expenseId)
            ExpenseIntent.DismissVoid -> if (!mutable.value.isMutating) {
                mutable.value = mutable.value.copy(pendingVoid = null)
            }
            ExpenseIntent.ConfirmVoid -> confirmVoid()
            ExpenseIntent.Retry -> mutations.retry(::execute)
        }
    }

    private fun restore() {
        mutations.restore(
            read = { success, failure ->
                drafts.read(groupId, null) { result ->
                    when (result) {
                        is ExpenseDraftReadResult.Success -> success(result.draft)
                        ExpenseDraftReadResult.Failure -> failure()
                    }
                }
            },
            valid = { it.schemaVersion == ExpenseDraft.CURRENT_SCHEMA && it.groupId == groupId },
            restored = { mutable.value = mutable.value.copy(draft = it) },
            unavailable = { it.copy(error = ExpenseError.DRAFT_UNAVAILABLE) },
        )
    }

    private fun load() {
        val current = mutable.value
        if (!current.organizer || current.isMutating) return
        val api = mutations.organizer ?: return mutations.fail(FinanceGatewayFailure.Forbidden)

        mutable.value = current.copy(isLoading = true, error = null, reloadAvailable = false)
        scope.launch {
            when (val listed = api.expenses(groupId)) {
                is NetworkResult.Failure -> mutations.fail(listed.error.toFinanceGatewayFailure())

                is NetworkResult.Success -> when (val totals = api.totals(groupId)) {
                    is NetworkResult.Failure -> mutations.fail(totals.error.toFinanceGatewayFailure())
                    is NetworkResult.Success -> mutable.value = mutable.value.copy(
                        expenses = listed.value.expenses,
                        totals = totals.value,
                        isLoading = false,
                        error = null,
                    )
                }
            }
        }
    }

    private fun openCreate() {
        val current = mutable.value
        if (!current.organizer || current.isMutating) return

        val draft = ExpenseDraft(
            groupId = groupId,
            commandKey = keys.create(),
            form = ExpenseForm(),
        )
        mutable.value = current.copy(draft = draft, fieldErrors = emptyMap(), error = null)
        persist(draft)
    }

    private fun openEdit(id: String) {
        val current = mutable.value
        if (!current.organizer || current.isMutating) return

        val expense = current.expenses.firstOrNull { it.id == id } ?: return
        val draft = ExpenseDraft(
            groupId = groupId,
            expenseId = id,
            etag = "\"${expense.version}\"",
            commandKey = keys.create(),
            form = expense.toExpenseForm(),
        )
        mutable.value = current.copy(draft = draft, fieldErrors = emptyMap(), error = null)
        persist(draft)
    }

    private fun update(form: ExpenseForm) {
        val current = mutable.value
        val draft = current.draft ?: return
        if (!current.organizer || current.isMutating) return

        val normalized = if (form.category != ExpenseCategoryDto.OTHER) {
            form.copy(customCategory = "")
        } else {
            form
        }
        val changed = draft.copy(form = normalized)
        mutable.value = current.copy(
            draft = changed,
            fieldErrors = emptyMap(),
            error = null,
            reloadAvailable = false,
        )
        persist(changed)
    }

    private fun submit() {
        val current = mutable.value
        val draft = current.draft ?: return
        if (!current.organizer || current.isMutating) return

        val errors = draft.form.validate()
        if (errors.isNotEmpty()) {
            mutable.value = current.copy(fieldErrors = errors, error = ExpenseError.VALIDATION)
            return
        }

        execute(ExpenseOperation.Save(draft))
    }

    private fun requestVoid(id: String) {
        val current = mutable.value
        if (!current.organizer || current.isMutating) return

        current.expenses.firstOrNull { it.id == id && it.status == ExpenseStatusDto.ACTIVE }
            ?.let { mutable.value = current.copy(pendingVoid = it, error = null) }
    }

    private fun confirmVoid() {
        val current = mutable.value
        val expense = current.pendingVoid ?: return
        if (!current.organizer || current.isMutating) return
        execute(ExpenseOperation.Void(expense.id, "\"${expense.version}\""))
    }

    private fun execute(operation: ExpenseOperation) {
        val current = mutable.value
        if (!current.organizer || current.isMutating) return

        mutations.execute(
            operation = operation,
            perform = { api -> when (operation) {
                is ExpenseOperation.Save -> {
                    val draft = operation.draft
                    val command = draft.form.toExpenseWriteCommand(
                        if (draft.expenseId == null) draft.commandKey else null,
                    )
                    val result = if (draft.expenseId == null) {
                        api.createExpense(groupId, command)
                    } else {
                        api.editExpense(groupId, draft.expenseId, requireNotNull(draft.etag), command)
                    }

                    when (result) {
                        is NetworkResult.Success -> FinanceMutationResult.Success(result.value.expense)
                        is NetworkResult.Failure -> FinanceMutationResult.Failure(result.error.toFinanceGatewayFailure())
                    }
                }

                is ExpenseOperation.Void -> when (val result = api.voidExpense(groupId, operation.expenseId, operation.etag)) {
                    is NetworkResult.Success -> FinanceMutationResult.Success(result.value.expense)
                    is NetworkResult.Failure -> FinanceMutationResult.Failure(result.error.toFinanceGatewayFailure())
                }
            } },
            succeeded = { api, expense ->
                when (operation) {
                    is ExpenseOperation.Save -> saved(api, operation.draft, expense)
                    is ExpenseOperation.Void -> voided(api, expense)
                }
            },
        )
    }

    private suspend fun saved(
        api: OrganizerFinanceGateway,
        draft: ExpenseDraft,
        expense: ExpenseDto,
    ) {
        drafts.clear(groupId, draft.expenseId, draft.commandKey) {}
        refreshAfter(api)
        mutable.value = mutable.value.copy(
            draft = null,
            isMutating = false,
            error = null,
            reloadAvailable = false,
            retryAvailable = false,
            lastAuditOutcome = "Despesa registrada no histórico manual.",
        )
        channel.trySend(ExpenseEffect.Saved(expense.id))
    }

    private suspend fun voided(api: OrganizerFinanceGateway, expense: ExpenseDto) {
        refreshAfter(api)
        mutable.value = mutable.value.copy(
            pendingVoid = null,
            isMutating = false,
            error = null,
            reloadAvailable = false,
            retryAvailable = false,
            lastAuditOutcome = "Despesa anulada no histórico manual.",
        )
        channel.trySend(ExpenseEffect.Voided(expense.id))
    }

    private suspend fun refreshAfter(api: OrganizerFinanceGateway) {
        val listed = api.expenses(groupId)
        val totals = api.totals(groupId)
        if (listed is NetworkResult.Success) {
            mutable.value = mutable.value.copy(expenses = listed.value.expenses)
        }
        if (totals is NetworkResult.Success) {
            mutable.value = mutable.value.copy(totals = totals.value)
        }
    }

    private fun persist(draft: ExpenseDraft) {
        mutations.persist(
            draft = draft,
            write = { value, failure -> drafts.write(value) { if (it == ExpenseDraftWriteResult.Failure) failure() } },
            unavailable = { it.copy(error = ExpenseError.DRAFT_UNAVAILABLE) },
        )
    }

    private fun mapError(failure: FinanceGatewayFailure) = when (failure) {
            is FinanceGatewayFailure.Validation -> ExpenseError.VALIDATION
            FinanceGatewayFailure.Conflict -> ExpenseError.CONFLICT
            FinanceGatewayFailure.HiddenResource -> ExpenseError.HIDDEN
            FinanceGatewayFailure.Forbidden -> ExpenseError.FORBIDDEN
            FinanceGatewayFailure.InvalidLifecycle -> ExpenseError.INVALID_LIFECYCLE
            else -> ExpenseError.UNAVAILABLE
        }
}
