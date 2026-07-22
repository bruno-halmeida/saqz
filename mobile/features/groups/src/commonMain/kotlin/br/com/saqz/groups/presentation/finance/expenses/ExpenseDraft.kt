package br.com.saqz.groups.presentation.finance.expenses

import kotlinx.serialization.Serializable

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
