package br.com.saqz.groups.presentation.games.editor

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
