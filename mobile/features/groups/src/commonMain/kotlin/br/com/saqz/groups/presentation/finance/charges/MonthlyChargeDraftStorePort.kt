package br.com.saqz.groups.presentation.finance.charges

sealed interface MonthlyDraftReadResult {
    data class Success(val draft: MonthlyChargeDraft?) : MonthlyDraftReadResult

    data object Failure : MonthlyDraftReadResult
}

sealed interface MonthlyDraftWriteResult {
    data object Success : MonthlyDraftWriteResult

    data object Failure : MonthlyDraftWriteResult
}

interface MonthlyChargeDraftStorePort {
    fun read(groupId: String, done: (MonthlyDraftReadResult) -> Unit)

    fun write(draft: MonthlyChargeDraft, done: (MonthlyDraftWriteResult) -> Unit)

    fun clear(groupId: String, commandKey: String, done: (MonthlyDraftWriteResult) -> Unit)
}
