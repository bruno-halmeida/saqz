package br.com.saqz.groups.presentation.games.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.game.VersionedGame
import br.com.saqz.groups.domain.game.VersionedSeries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class GameEditorViewModel(
    private val input: GameEditorInput,
    private val gateway: GameGateway,
    private val store: GameDraftStorePort,
    private val keys: GameCommandKeyFactory,
    testScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = testScope ?: viewModelScope
    private val initial = input.toGameEditorDraft(keys.create())
    private val mutableState = MutableStateFlow(GameEditorState(initial))
    val state: StateFlow<GameEditorState> = mutableState.asStateFlow()

    private val effectChannel = Channel<GameEditorEffect>(Channel.BUFFERED)
    val effects: Flow<GameEditorEffect> = effectChannel.receiveAsFlow()

    init {
        store.read(input.groupId, input.existing?.game?.id) { result ->
            when (result) {
                is GameDraftReadResult.Success -> result.draft
                    ?.takeIf {
                        it.schemaVersion == GameEditorDraft.CURRENT_SCHEMA && it.groupId == input.groupId
                    }
                    ?.let { mutableState.value = GameEditorState(it) }
                GameDraftReadResult.Failure -> mutableState.value = mutableState.value.copy(
                    error = GameEditorError.DRAFT_UNAVAILABLE,
                )
            }
        }
    }

    fun onIntent(intent: GameEditorIntent) {
        when (intent) {
            is GameEditorIntent.SetMode -> update { copy(mode = intent.mode, scope = null) }
            is GameEditorIntent.UpdateForm -> update { copy(form = intent.form) }
            GameEditorIntent.AddSlot -> update {
                copy(form = form.copy(slots = form.slots + form.newWeeklySlot(keys.create())))
            }
            is GameEditorIntent.SetScope -> update { copy(scope = intent.scope) }
            GameEditorIntent.Submit -> submit()
            GameEditorIntent.Reload -> reload()
        }
    }

    private fun update(change: GameEditorDraft.() -> GameEditorDraft) {
        mutableState.value = mutableState.value.copy(
            draft = mutableState.value.draft.change(),
            fieldErrors = emptyMap(),
            globalValidationMessages = emptyList(),
            error = null,
            reloadAvailable = false,
        )
        persist()
    }

    private fun persist() {
        store.write(mutableState.value.draft) {
            if (it == GameDraftWriteResult.Failure) {
                mutableState.value = mutableState.value.copy(error = GameEditorError.DRAFT_UNAVAILABLE)
            }
        }
    }

    private fun submit() {
        val current = mutableState.value
        if (current.isLoading) return

        val errors = validateGameEditor(current.draft)
        if (errors.isNotEmpty()) {
            mutableState.value = current.copy(fieldErrors = errors)
            return
        }
        persist()
        mutableState.value = current.copy(
            isLoading = true,
            error = null,
            fieldErrors = emptyMap(),
            globalValidationMessages = emptyList(),
        )
        scope.launch { execute(current.draft) }
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
        mutableState.value = mutableState.value.copy(isLoading = false, successId = id)
        effectChannel.trySend(GameEditorEffect.Saved(id))
    }

    private fun failure(failure: GameError) {
        mutableState.value = when (failure) {
            is GameError.Validation -> mutableState.value.copy(
                isLoading = false,
                fieldErrors = failure.error.details.fieldMessages,
                globalValidationMessages = failure.error.details.globalMessages,
                error = GameEditorError.VALIDATION.takeIf {
                    failure.error.details.globalMessages.isEmpty()
                },
            )
            GameError.Conflict -> mutableState.value.copy(
                isLoading = false,
                error = GameEditorError.CONFLICT,
                reloadAvailable = true,
            )
            GameError.HiddenResource -> mutableState.value.copy(
                isLoading = false,
                error = GameEditorError.HIDDEN,
                reloadAvailable = true,
            )
            GameError.InvalidLifecycle -> mutableState.value.copy(
                isLoading = false,
                error = GameEditorError.INVALID_LIFECYCLE,
                reloadAvailable = true,
            )
            else -> mutableState.value.copy(isLoading = false, error = GameEditorError.UNAVAILABLE)
        }
    }

    private fun reload() {
        val game = input.existing?.game ?: return
        if (!mutableState.value.reloadAvailable) return

        effectChannel.trySend(GameEditorEffect.Reload(input.groupId, game.id))
    }
}
