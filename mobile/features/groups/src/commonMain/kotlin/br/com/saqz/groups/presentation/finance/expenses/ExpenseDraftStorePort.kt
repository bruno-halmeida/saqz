package br.com.saqz.groups.presentation.finance.expenses

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

    fun clear(
        groupId: String,
        expenseId: String?,
        commandKey: String,
        done: (ExpenseDraftWriteResult) -> Unit,
    )
}
