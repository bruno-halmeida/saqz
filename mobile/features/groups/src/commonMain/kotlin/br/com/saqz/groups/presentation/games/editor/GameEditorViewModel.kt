package br.com.saqz.groups.presentation.games.editor

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.game.VersionedGame
import br.com.saqz.groups.domain.game.VersionedSeries
import kotlinx.coroutines.launch

class GameEditorViewModel(
    private val input: GameEditorInput,
    private val gateway: GameGateway,
    private val store: GameDraftStorePort,
    private val keys: GameCommandKeyFactory,
) : MviViewModel<GameEditorState, GameEditorIntent, GameEditorEffect>(
    GameEditorState(input.toGameEditorDraft(keys.create())),
) {
    init {
        store.read(input.groupId, input.existing?.game?.id) { result ->
            when (result) {
                is GameDraftReadResult.Success -> result.draft
                    ?.takeIf {
                        it.schemaVersion == GameEditorDraft.CURRENT_SCHEMA && it.groupId == input.groupId
                    }
                    ?.let { draft -> update { GameEditorState(draft) } }
                GameDraftReadResult.Failure -> update {
                    it.copy(error = GameEditorError.DRAFT_UNAVAILABLE)
                }
            }
        }
    }

    override fun onIntent(intent: GameEditorIntent) {
        when (intent) {
            is GameEditorIntent.SetMode -> updateDraft { copy(mode = intent.mode, scope = null) }
            is GameEditorIntent.UpdateForm -> updateDraft { copy(form = intent.form) }
            GameEditorIntent.AddSlot -> updateDraft {
                copy(form = form.copy(slots = form.slots + form.newWeeklySlot(keys.create())))
            }
            is GameEditorIntent.SetScope -> updateDraft { copy(scope = intent.scope) }
            GameEditorIntent.Submit -> submit()
            GameEditorIntent.Reload -> reload()
        }
    }

    private fun updateDraft(change: GameEditorDraft.() -> GameEditorDraft) {
        update {
            it.copy(
                draft = it.draft.change(),
                fieldErrors = emptyMap(),
                globalValidationMessages = emptyList(),
                error = null,
                reloadAvailable = false,
            )
        }
        persist()
    }

    private fun persist() {
        store.write(state.value.draft) {
            if (it == GameDraftWriteResult.Failure) {
                update { current -> current.copy(error = GameEditorError.DRAFT_UNAVAILABLE) }
            }
        }
    }

    private fun submit() {
        val current = state.value
        if (current.isLoading) return

        val errors = validateGameEditor(current.draft)
        if (errors.isNotEmpty()) {
            update { it.copy(fieldErrors = errors) }
            return
        }
        persist()
        update {
            it.copy(
                isLoading = true,
                error = null,
                fieldErrors = emptyMap(),
                globalValidationMessages = emptyList(),
            )
        }
        viewModelScope.launch { execute(current.draft) }
    }

    private suspend fun execute(draft: GameEditorDraft) {
        when {
            input.existing == null && draft.mode == GameEditorMode.ONE_TIME -> {
                handleGameResult(
                    gateway.create(GroupId(input.groupId), draft.form.toGameWriteCommand(draft.commandKey)),
                    draft,
                )
            }
            input.existing == null -> handleSeriesResult(
                gateway.createSeries(GroupId(input.groupId), draft.toSeriesWriteCommand()),
                draft,
            )
            draft.mode == GameEditorMode.ONE_TIME -> handleGameResult(
                gateway.edit(
                    GroupId(input.groupId),
                    input.existing.game.id,
                    requireNotNull(draft.version),
                    draft.form.toGameWriteCommand(),
                ),
                draft,
            )
            else -> {
                val series = requireNotNull(input.series)
                handleSeriesResult(
                    gateway.boundary(
                        GroupId(input.groupId),
                        series.series.id,
                        requireNotNull(draft.version),
                        draft.toBoundaryCommand(series),
                    ),
                    draft,
                )
            }
        }
    }

    private fun handleGameResult(
        result: SaqzResult<VersionedGame, GameError>,
        draft: GameEditorDraft,
    ) {
        when (result) {
            is SaqzResult.Success -> success(result.value.game.id, draft)
            is SaqzResult.Failure -> failure(result.error)
        }
    }

    private fun handleSeriesResult(
        result: SaqzResult<VersionedSeries, GameError>,
        draft: GameEditorDraft,
    ) {
        when (result) {
            is SaqzResult.Success -> success(result.value.series.id, draft)
            is SaqzResult.Failure -> failure(result.error)
        }
    }

    private fun success(id: String, draft: GameEditorDraft) {
        store.clear(input.groupId, input.existing?.game?.id, draft.commandKey) {}
        update { it.copy(isLoading = false, successId = id) }
        emit(GameEditorEffect.Saved(id))
    }

    private fun failure(failure: GameError) {
        update { current ->
            when (failure) {
                is GameError.Validation -> current.copy(
                    isLoading = false,
                    fieldErrors = failure.error.details.fieldMessages,
                    globalValidationMessages = failure.error.details.globalMessages,
                    error = GameEditorError.VALIDATION.takeIf {
                        failure.error.details.globalMessages.isEmpty()
                    },
                )
                GameError.Conflict -> current.copy(
                    isLoading = false,
                    error = GameEditorError.CONFLICT,
                    reloadAvailable = true,
                )
                GameError.HiddenResource -> current.copy(
                    isLoading = false,
                    error = GameEditorError.HIDDEN,
                    reloadAvailable = true,
                )
                GameError.InvalidLifecycle -> current.copy(
                    isLoading = false,
                    error = GameEditorError.INVALID_LIFECYCLE,
                    reloadAvailable = true,
                )
                else -> current.copy(isLoading = false, error = GameEditorError.UNAVAILABLE)
            }
        }
    }

    private fun reload() {
        val game = input.existing?.game ?: return
        if (!state.value.reloadAvailable) return

        emit(GameEditorEffect.Reload(input.groupId, game.id))
    }
}
