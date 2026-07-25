package br.com.saqz.groups.port

import br.com.saqz.groups.model.ExpenseDraft
import br.com.saqz.groups.model.GameEditorDraft
import br.com.saqz.groups.model.MonthlyChargeDraft

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

sealed interface ExpenseDraftReadResult {
    data class Success(val draft: ExpenseDraft?) : ExpenseDraftReadResult

    data object Failure : ExpenseDraftReadResult
}

sealed interface ExpenseDraftWriteResult {
    data object Success : ExpenseDraftWriteResult

    data object Failure : ExpenseDraftWriteResult
}

interface ExpenseDraftStorePort {
    fun read(groupId: String, expenseId: String?, done: (ExpenseDraftReadResult) -> Unit)

    fun write(draft: ExpenseDraft, done: (ExpenseDraftWriteResult) -> Unit)

    fun clear(
        groupId: String,
        expenseId: String?,
        commandKey: String,
        done: (ExpenseDraftWriteResult) -> Unit,
    )
}

sealed interface GameDraftReadResult {
    data class Success(val draft: GameEditorDraft?) : GameDraftReadResult

    data object Failure : GameDraftReadResult
}

sealed interface GameDraftWriteResult {
    data object Success : GameDraftWriteResult

    data object Failure : GameDraftWriteResult
}

interface GameDraftStorePort {
    fun read(groupId: String, resourceId: String?, done: (GameDraftReadResult) -> Unit)

    fun write(draft: GameEditorDraft, done: (GameDraftWriteResult) -> Unit)

    fun clear(
        groupId: String,
        resourceId: String?,
        commandKey: String,
        done: (GameDraftWriteResult) -> Unit,
    )
}
