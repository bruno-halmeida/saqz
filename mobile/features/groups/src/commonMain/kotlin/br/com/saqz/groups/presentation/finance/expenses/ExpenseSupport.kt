package br.com.saqz.groups.presentation.finance.expenses

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.data.GroupRoleDto
import br.com.saqz.groups.data.finance.ChargeStatusCommandDto
import br.com.saqz.groups.data.finance.ExpenseActionDto
import br.com.saqz.groups.data.finance.ExpenseCategoryDto
import br.com.saqz.groups.data.finance.ExpenseDto
import br.com.saqz.groups.data.finance.ExpenseStatusDto
import br.com.saqz.groups.data.finance.ExpenseWriteCommandDto
import br.com.saqz.groups.data.finance.FinanceTotalsDto
import br.com.saqz.groups.data.finance.MonthlyChargeCommandDto
import br.com.saqz.groups.data.finance.ChargeStatusDto
import br.com.saqz.groups.data.finance.OrganizerFinanceGateway
import kotlinx.serialization.Serializable

@Serializable
data class ExpenseForm(
    val description: String = "",
    val amountBrl: String = "",
    val expenseDate: String = "",
    val category: ExpenseCategoryDto? = null,
    val customCategory: String = "",
    val notes: String = "",
)

@Serializable
data class ExpenseDraft(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val groupId: String,
    val expenseId: String? = null,
    val etag: String? = null,
    val commandKey: String,
    val form: ExpenseForm,
) {
    companion object {
        const val CURRENT_SCHEMA = 1
    }
}

sealed interface ExpenseDraftReadResult {
    data class Success(val draft: ExpenseDraft?) : ExpenseDraftReadResult
    data object Failure : ExpenseDraftReadResult
}

sealed interface ExpenseDraftWriteResult {
    data object Success : ExpenseDraftWriteResult
    data object Failure : ExpenseDraftWriteResult
}

interface ExpenseDraftStorePort {
    fun read(groupId: String, done: (ExpenseDraftReadResult) -> Unit)
    fun write(draft: ExpenseDraft, done: (ExpenseDraftWriteResult) -> Unit)
    fun clear(groupId: String, expenseId: String?, commandKey: String, done: (ExpenseDraftWriteResult) -> Unit)
}

fun interface ExpenseCommandKeyFactory {
    fun create(): String
}

enum class ExpenseError {
    UNAVAILABLE,
    DRAFT_UNAVAILABLE,
    VALIDATION,
    CONFLICT,
    HIDDEN,
    FORBIDDEN,
    INVALID_LIFECYCLE,
}

@Immutable
data class ExpenseState(
    val groupId: String,
    val role: GroupRoleDto,
    val expenses: List<ExpenseDto> = emptyList(),
    val totals: FinanceTotalsDto? = null,
    val draft: ExpenseDraft? = null,
    val pendingVoid: ExpenseDto? = null,
    val fieldErrors: Map<String, List<String>> = emptyMap(),
    val isLoading: Boolean = true,
    val isMutating: Boolean = false,
    val error: ExpenseError? = null,
    val reloadAvailable: Boolean = false,
    val retryAvailable: Boolean = false,
    val lastAuditOutcome: String? = null,
) {
    val organizer: Boolean
        get() = role == GroupRoleDto.OWNER || role == GroupRoleDto.ADMIN

    val routeAvailable: Boolean
        get() = organizer
}

sealed interface ExpenseIntent {
    data object Refresh : ExpenseIntent
    data object OpenCreate : ExpenseIntent
    data class OpenEdit(val expenseId: String) : ExpenseIntent
    data class UpdateForm(val form: ExpenseForm) : ExpenseIntent
    data object Submit : ExpenseIntent
    data class RequestVoid(val expenseId: String) : ExpenseIntent
    data object DismissVoid : ExpenseIntent
    data object ConfirmVoid : ExpenseIntent
    data object Retry : ExpenseIntent
}

sealed interface ExpenseEffect {
    data class Saved(val expenseId: String) : ExpenseEffect
    data class Voided(val expenseId: String) : ExpenseEffect
}

internal fun ExpenseDto.form() = ExpenseForm(
    description,
    amountCents.centsToBrl(),
    expenseDate,
    category,
    customCategory.orEmpty(),
    notes.orEmpty(),
)

internal fun ExpenseForm.command(key: String?) = ExpenseWriteCommandDto(
    key,
    description.trim(),
    requireNotNull(amountBrl.brlToCents()),
    expenseDate,
    requireNotNull(category),
    customCategory.trim().ifBlank { null },
    notes.trim().ifBlank { null },
)

internal fun ExpenseForm.validate() = buildMap<String, List<String>> {
    val description = description.trim()
    if (description.length !in 2..160 || description.any(Char::isISOControl)) {
        put("description", listOf("is invalid"))
    }

    val amount = amountBrl.brlToCents()
    if (amount == null || amount !in 1..99_999_999) {
        put("amountBrl", listOf("is invalid"))
    }

    if (!expenseDate.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) {
        put("expenseDate", listOf("is invalid"))
    }

    if (category == null) {
        put("category", listOf("is required"))
    }

    if (category == ExpenseCategoryDto.OTHER && customCategory.trim().let {
            it.length !in 2..40 || it.any(Char::isISOControl)
        }) {
        put("customCategory", listOf("is invalid"))
    }

    if (notes.trim().let {
            it.isNotEmpty() && (it.length !in 2..500 || it.any(Char::isISOControl))
        }) {
        put("notes", listOf("is invalid"))
    }
}

internal fun Long.centsToBrl() = "${this / 100},${(this % 100).toString().padStart(2, '0')}"

internal fun String.brlToCents(): Long? {
    val clean = trim().replace("R$", "").trim()
    if (!clean.matches(Regex("[0-9]+([,.][0-9]{1,2})?"))) return null

    val parts = clean.replace('.', ',').split(',')
    return parts[0].toLongOrNull()
        ?.times(100)
        ?.plus(parts.getOrNull(1)?.padEnd(2, '0')?.toLongOrNull() ?: 0)
}
