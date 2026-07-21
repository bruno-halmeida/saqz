package br.com.saqz.groups.presentation.games.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.saqz.groups.data.game.GameGateway
import br.com.saqz.groups.data.game.GameGatewayFailure
import br.com.saqz.groups.data.game.VersionedGameDto
import br.com.saqz.groups.data.game.VersionedSeriesDto
import br.com.saqz.groups.data.game.toGameGatewayFailure
import br.com.saqz.network.NetworkResult
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
        mutableState.value = current.copy(isLoading = true, error = null, fieldErrors = emptyMap())
        scope.launch { execute(current.draft) }
    }

    private suspend fun execute(draft: GameEditorDraft) {
        val result = when {
            input.existing == null && draft.mode == GameEditorMode.ONE_TIME -> {
                gateway.create(input.groupId, draft.form.toGameWriteCommand(draft.commandKey))
            }
            input.existing == null -> gateway.createSeries(input.groupId, draft.toSeriesWriteCommand())
            draft.mode == GameEditorMode.ONE_TIME -> gateway.edit(
                input.groupId,
                input.existing.game.id,
                requireNotNull(draft.etag),
                draft.form.toGameWriteCommand(),
            )
            else -> {
                val series = requireNotNull(input.series)
                gateway.boundary(
                    input.groupId,
                    series.series.id,
                    requireNotNull(draft.etag),
                    draft.toBoundaryCommand(series),
                )
            }
        }
        when (result) {
            is NetworkResult.Success<*> -> {
                val id = when (val value = result.value) {
                    is VersionedGameDto -> value.game.id
                    is VersionedSeriesDto -> value.series.id
                    else -> error("unexpected result")
                }
                store.clear(input.groupId, input.existing?.game?.id, draft.commandKey) {}
                mutableState.value = mutableState.value.copy(isLoading = false, successId = id)
                effectChannel.trySend(GameEditorEffect.Saved(id))
            }
            is NetworkResult.Failure -> failure(result.error.toGameGatewayFailure())
        }
    }

    private fun failure(failure: GameGatewayFailure) {
        mutableState.value = when (failure) {
            is GameGatewayFailure.Validation -> mutableState.value.copy(
                isLoading = false,
                fieldErrors = failure.fields,
            )
            GameGatewayFailure.Conflict -> mutableState.value.copy(
                isLoading = false,
                error = GameEditorError.CONFLICT,
                reloadAvailable = true,
            )
            GameGatewayFailure.HiddenResource -> mutableState.value.copy(
                isLoading = false,
                error = GameEditorError.HIDDEN,
                reloadAvailable = true,
            )
            GameGatewayFailure.InvalidLifecycle -> mutableState.value.copy(
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
