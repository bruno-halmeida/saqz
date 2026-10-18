package br.com.saqz.groups.presentation.newentry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.Expense
import br.com.saqz.groups.domain.finance.ExpenseCategory
import br.com.saqz.groups.domain.finance.ExpenseStatus
import br.com.saqz.groups.domain.finance.ExpenseWriteCommand
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.FinanceVersionToken
import br.com.saqz.groups.domain.finance.OrganizerFinanceGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.port.GroupNowPort
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class NewEntryViewModel(
    private val groupId: String,
    private val savedState: SavedStateHandle,
    private val gateway: OrganizerFinanceGateway,
    now: GroupNowPort,
) : MviViewModel<NewEntryState, NewEntryIntent, NewEntryEffect>(
    NewEntryState(date = now.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()).restore(savedState),
) {
    private var saveGeneration = 0L
    private var loadGeneration = 0L

    /** Lançamento em correção, como veio do servidor: dele saem a versão e o que a tela não edita. */
    private var editBase: Expense? = null
    private var editExpenseId: String? = savedState.get<String>(KEY_EDIT_ID)
    private var requestId = savedState.get<String>(KEY_REQUEST_ID)
        ?: Uuid.random().toString().also { savedState[KEY_REQUEST_ID] = it }

    override fun handleIntent(intent: NewEntryIntent) {
        when (intent) {
            is NewEntryIntent.SelectDirection -> {
                savedState[KEY_DIRECTION] = intent.direction.name
                update { it.copy(direction = intent.direction, error = null) }
            }
            is NewEntryIntent.AmountChanged -> updateAmount(intent.value)
            is NewEntryIntent.SelectAmountShortcut -> {
                val value = formatEntryCents(intent.cents)
                savedState[KEY_AMOUNT] = value
                update { it.copy(amountText = value, error = null) }
            }
            is NewEntryIntent.DescriptionChanged -> {
                savedState[KEY_DESCRIPTION] = intent.value
                update { it.copy(description = intent.value, error = null) }
            }
            is NewEntryIntent.SelectCategory -> {
                savedState[KEY_CATEGORY] = intent.category.name
                update { it.copy(category = intent.category, error = null) }
            }
            is NewEntryIntent.CustomCategoryChanged -> {
                savedState[KEY_CUSTOM_CATEGORY] = intent.value
                update { it.copy(customCategory = intent.value, error = null) }
            }
            is NewEntryIntent.DateChanged -> updateDate(intent.value)
            is NewEntryIntent.ApplyPrefill -> applyPrefill(intent)
            is NewEntryIntent.LoadEdit -> loadEdit(intent.expenseId)
            NewEntryIntent.RetryLoadEdit -> editExpenseId?.let(::loadEdit)
            NewEntryIntent.Save -> save()
        }
    }

    private fun loadEdit(expenseId: String) {
        editExpenseId = expenseId
        savedState[KEY_EDIT_ID] = expenseId
        val generation = ++loadGeneration
        update { it.copy(editing = true, isLoadingEntry = true, entryLoadFailed = false, error = null) }
        viewModelScope.launch {
            val result = gateway.expenses(GroupId(groupId))
            if (generation != loadGeneration) return@launch
            val expense = (result as? SaqzResult.Success)?.value?.expenses?.firstOrNull { it.id == expenseId }
            if (expense == null || expense.status != ExpenseStatus.Active) {
                val error = (result as? SaqzResult.Failure)?.error?.toUiError() ?: GroupUiError.NotFound
                update { it.copy(isLoadingEntry = false, entryLoadFailed = true, error = error) }
                return@launch
            }
            editBase = expense
            // Voltando de process death, o que a pessoa já digitou vale mais que o servidor.
            val typed = savedState.get<Boolean>(KEY_EDIT_APPLIED) == true
            if (!typed) {
                val amount = formatEntryCents(expense.amountCents)
                savedState[KEY_EDIT_APPLIED] = true
                savedState[KEY_AMOUNT] = amount
                savedState[KEY_DESCRIPTION] = expense.description
            }
            update {
                it.copy(
                    isLoadingEntry = false,
                    entryLoadFailed = false,
                    amountText = if (typed) it.amountText else formatEntryCents(expense.amountCents),
                    description = if (typed) it.description else expense.description,
                )
            }
        }
    }

    private fun saveEdit(base: Expense) {
        val current = state.value
        val amountCents = parseEntryCents(current.amountText)
        if (amountCents == null || amountCents <= 0L || current.description.isBlank()) {
            update { it.copy(error = GroupUiError.Validation) }
            return
        }
        val requestGeneration = ++saveGeneration
        update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = gateway.editExpense(
                GroupId(groupId),
                base.id,
                FinanceVersionToken("\"${base.version}\""),
                ExpenseWriteCommand(
                    description = current.description.trim(),
                    amountCents = amountCents,
                    expenseDate = base.expenseDate,
                    category = base.category,
                    customCategory = base.customCategory,
                    notes = base.notes,
                    direction = base.direction,
                ),
            )
            if (requestGeneration != saveGeneration) return@launch
            when (result) {
                is SaqzResult.Failure -> update { it.copy(isSaving = false, error = result.error.toUiError()) }
                is SaqzResult.Success -> {
                    update { it.copy(isSaving = false, error = null) }
                    emit(NewEntryEffect.Saved)
                }
            }
        }
    }

    private fun applyPrefill(intent: NewEntryIntent.ApplyPrefill) {
        if (savedState.get<Boolean>(KEY_PREFILL_APPLIED) == true) return
        when (intent.prefill) {
            is NewEntryPrefill.GameCourt -> {
                savedState[KEY_PREFILL_APPLIED] = true
                savedState[KEY_DIRECTION] = NewEntryDirection.Out.name
                savedState[KEY_CATEGORY] = NewEntryCategory.Court.name
                savedState[KEY_DESCRIPTION] = intent.description
                savedState[KEY_DATE] = intent.prefill.localDate
                update {
                    it.copy(
                        direction = NewEntryDirection.Out,
                        category = NewEntryCategory.Court,
                        description = intent.description,
                        date = intent.prefill.localDate,
                        error = null,
                    )
                }
            }
        }
    }

    private fun updateAmount(value: String) {
        val filtered = value.filter { it.isDigit() || it == ',' || it == '.' }
        savedState[KEY_AMOUNT] = filtered
        update { it.copy(amountText = filtered, error = null) }
    }

    private fun updateDate(value: String) {
        val iso = parseEntryDate(value) ?: value
        savedState[KEY_DATE] = iso
        update { it.copy(date = iso, error = null) }
    }

    private fun save() {
        val current = state.value
        if (current.isSaving) return
        if (current.editing) {
            // Sem a leitura do lançamento não há versão para mandar: nada a salvar.
            editBase?.let(::saveEdit)
            return
        }
        val amountCents = parseEntryCents(current.amountText)
        val customCategory = current.customCategory.trim()
        val amountInvalid = amountCents == null || amountCents <= 0L
        val categoryInvalid = current.category == NewEntryCategory.Other &&
            !isValidCustomCategory(customCategory)
        if (
            amountInvalid ||
            current.description.isBlank() ||
            !isEntryDate(current.date) ||
            categoryInvalid
        ) {
            update { it.copy(error = GroupUiError.Validation) }
            return
        }
        val requestGeneration = ++saveGeneration
        update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (
                val result = gateway.createExpense(
                    GroupId(groupId),
                    ExpenseWriteCommand(
                        requestId = requestId,
                        description = current.description.trim(),
                        amountCents = amountCents,
                        expenseDate = current.date,
                        category = current.category.toExpenseCategory(),
                        customCategory = customCategory.takeIf { current.category == NewEntryCategory.Other },
                        direction = current.direction.toFinanceDirection(),
                    ),
                )
            ) {
                is SaqzResult.Failure -> if (requestGeneration == saveGeneration) {
                    update { it.copy(isSaving = false, error = result.error.toUiError()) }
                }
                is SaqzResult.Success -> if (requestGeneration == saveGeneration) {
                    update { it.copy(isSaving = false, error = null) }
                    requestId = Uuid.random().toString()
                    savedState[KEY_REQUEST_ID] = requestId
                    emit(NewEntryEffect.Saved)
                }
            }
        }
    }

    private fun NewEntryCategory.toExpenseCategory(): ExpenseCategory = when (this) {
        NewEntryCategory.Court -> ExpenseCategory.Venue
        NewEntryCategory.Material -> ExpenseCategory.Equipment
        NewEntryCategory.Racha -> ExpenseCategory.Racha
        NewEntryCategory.Other -> ExpenseCategory.Other
    }

    private fun FinanceError.toUiError(): GroupUiError = when (this) {
        is FinanceError.Validation -> GroupUiError.Validation
        FinanceError.HiddenResource -> GroupUiError.NotFound
        FinanceError.Forbidden,
        FinanceError.Authentication,
        -> GroupUiError.AccessDenied
        FinanceError.Conflict -> GroupUiError.Conflict
        FinanceError.PreconditionRequired,
        FinanceError.InvalidLifecycle,
        is FinanceError.Data,
        -> GroupUiError.Network
    }
}

internal fun formatEntryCents(cents: Long): String {
    val reais = cents / 100
    val centavos = (cents % 100).toString().padStart(2, '0')
    return "$reais,$centavos"
}

internal fun parseEntryCents(value: String): Long? {
    val normalized = value.trim().removePrefix("R$").replace(" ", "")
    if (normalized.isEmpty()) return null
    val parts = normalized.replace('.', ',').split(',')
    if (parts.size > 2 || parts.any { it.isEmpty() }) return null
    val reais = parts[0].toLongOrNull() ?: return null
    val centavos = when (val fraction = parts.getOrNull(1)) {
        null -> 0L
        else -> if (fraction.length <= 2) fraction.padEnd(2, '0').toLongOrNull() else null
    }
    return centavos?.let { reais * 100 + it }
}

internal fun parseEntryDate(value: String): String? {
    val trimmed = value.trim()
    if (isEntryDate(trimmed)) return trimmed
    if (trimmed.length == 8 && trimmed.all(Char::isDigit)) {
        val iso = "${trimmed.substring(4)}-${trimmed.substring(2, 4)}-${trimmed.substring(0, 2)}"
        return iso.takeIf(::isEntryDate)
    }
    val parts = trimmed.split('/')
    return if (parts.size == 3 && parts[0].length == 2 && parts[1].length == 2 && parts[2].length == 4) {
        "${parts[2]}-${parts[1]}-${parts[0]}"
    } else {
        null
    }
}

internal fun isEntryDate(value: String): Boolean = runCatching {
    kotlinx.datetime.LocalDate.parse(value)
}.isSuccess

internal fun isValidCustomCategory(value: String): Boolean = value.trim().length in 2..40

private fun NewEntryState.restore(savedState: SavedStateHandle): NewEntryState = copy(
    direction = savedState.get<String>(KEY_DIRECTION)?.let { value ->
        NewEntryDirection.entries.firstOrNull { it.name == value }
    } ?: direction,
    amountText = savedState.get<String>(KEY_AMOUNT) ?: amountText,
    description = savedState.get<String>(KEY_DESCRIPTION) ?: description,
    category = savedState.get<String>(KEY_CATEGORY)?.let { value ->
        NewEntryCategory.entries.firstOrNull { it.name == value }
    } ?: category,
    customCategory = savedState.get<String>(KEY_CUSTOM_CATEGORY) ?: customCategory,
    date = savedState.get<String>(KEY_DATE) ?: date,
)

private const val KEY_DIRECTION = "new-entry-direction"
private const val KEY_AMOUNT = "new-entry-amount"
private const val KEY_DESCRIPTION = "new-entry-description"
private const val KEY_CATEGORY = "new-entry-category"
private const val KEY_CUSTOM_CATEGORY = "new-entry-custom-category"
private const val KEY_DATE = "new-entry-date"
private const val KEY_REQUEST_ID = "new-entry-request-id"
private const val KEY_PREFILL_APPLIED = "new-entry-prefill-applied"
private const val KEY_EDIT_ID = "new-entry-edit-id"
private const val KEY_EDIT_APPLIED = "new-entry-edit-applied"
