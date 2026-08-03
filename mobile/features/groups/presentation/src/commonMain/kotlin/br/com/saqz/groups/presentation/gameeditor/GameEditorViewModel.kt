package br.com.saqz.groups.presentation.gameeditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.GameError
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.game.GameWriteCommand
import br.com.saqz.groups.domain.group.GroupGateway
import br.com.saqz.groups.domain.group.GroupVenue
import br.com.saqz.groups.model.GameEditorDraft
import br.com.saqz.groups.model.GameEditorForm
import br.com.saqz.groups.model.GameEditorMode
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.toUiError
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

class GameEditorViewModel(
    val groupId: String,
    private val gameId: String?,
    private val savedState: SavedStateHandle,
    private val gameGateway: GameGateway,
    private val groupGateway: GroupGateway,
) : MviViewModel<GameEditorState, GameEditorIntent, GameEditorEffect>(
    initialState = GameEditorState(isLoading = true),
) {
    private var loadGeneration = 0
    init {
        load()
    }

    override fun onIntent(intent: GameEditorIntent) {
        when (intent) {
            GameEditorIntent.Retry -> load()
            GameEditorIntent.Submit -> submit()
            GameEditorIntent.OpenDateTimePicker -> Unit
            is GameEditorIntent.SaveDateTime -> updateForm {
                copy(localDate = intent.date, localTime = intent.time)
            }
            is GameEditorIntent.SelectDuration -> updateForm { copy(durationMinutes = intent.minutes) }
            is GameEditorIntent.UpdateVenueName -> updateForm {
                copy(venue = (venue ?: emptyVenue()).copy(name = intent.name), venueEditable = true)
            }
            is GameEditorIntent.UpdateVenueAddress -> updateForm {
                copy(venue = (venue ?: emptyVenue()).copy(address = intent.address), venueEditable = true)
            }
            is GameEditorIntent.UpdateCapacity -> updateForm {
                copy(capacity = intent.capacity.coerceAtLeast(MIN_CAPACITY))
            }
            is GameEditorIntent.SelectConfirmationLead -> updateForm {
                copy(confirmationLeadMinutes = intent.minutes)
            }
            is GameEditorIntent.UpdateNotes -> updateForm { copy(notes = intent.notes) }
            GameEditorIntent.DismissConflict -> update { it.copy(hasConflict = false, conflictGameId = null) }
            GameEditorIntent.OpenExistingGame -> openExistingGame()
        }
    }

    private fun load() {
        val generation = ++loadGeneration
        update {
            it.copy(
                isLoading = true,
                loadFailed = false,
                error = null,
                isSaving = false,
                saveFailed = false,
                hasConflict = false,
                conflictGameId = null,
            )
        }
        viewModelScope.launch {
            val groupResult = groupGateway.read(GroupId(groupId))
            if (generation != loadGeneration) return@launch
            if (groupResult is SaqzResult.Failure) {
                showLoadFailure(generation, groupResult.error.toUiError())
                return@launch
            }
            val group = (groupResult as SaqzResult.Success).value.group
            val profile = group.profile
            val defaultForm = GameEditorFields(
                title = if (gameId == null) DEFAULT_TITLE else "",
                durationMinutes = profile?.regularSlots?.firstOrNull()?.durationMinutes ?: DEFAULT_DURATION,
                venue = profile?.defaultVenue?.toGameVenue(),
                capacity = profile?.defaultCapacity ?: DEFAULT_CAPACITY,
                confirmationLeadMinutes = profile?.defaultConfirmationLeadMinutes ?: DEFAULT_LEAD,
            )
            if (gameId == null) {
                update {
                    it.copy(
                        isLoading = false,
                        groupName = group.name,
                        zoneId = group.timeZone.id,
                        form = restoreForm(defaultForm),
                    )
                }
                return@launch
            }
            val gameResult = gameGateway.read(GroupId(groupId), gameId)
            if (generation != loadGeneration) return@launch
            when (gameResult) {
                is SaqzResult.Failure -> showLoadFailure(generation, gameResult.error.toUiError())
                is SaqzResult.Success -> {
                    val game = gameResult.value.game
                    val serverForm = defaultForm.copy(
                        title = game.title,
                        localDate = game.localDate,
                        localTime = game.localTime,
                        durationMinutes = game.durationMinutes,
                        gameFeeCents = game.gameFeeCents,
                        venue = game.venue,
                        capacity = game.capacity,
                        confirmationLeadMinutes = confirmationLeadMinutes(
                            game.startsAt,
                            game.confirmationDeadline,
                        ) ?: defaultForm.confirmationLeadMinutes,
                        notes = game.notes.orEmpty(),
                    )
                    update {
                        it.copy(
                            isLoading = false,
                            loadFailed = false,
                            groupName = group.name,
                            zoneId = group.timeZone.id,
                            form = restoreForm(serverForm),
                            versionToken = gameResult.value.version.value,
                        )
                    }
                }
            }
        }
    }

    private fun submit() {
        val current = state.value
        val errors = validateGameEditor(current.form)
        if (errors.isNotEmpty()) {
            update { it.copy(validationErrors = errors) }
            return
        }
        if (current.isSaving) return
        val commandKey = savedState.get<String>(KeyCommand) ?: Uuid.random().toString().also {
            savedState[KeyCommand] = it
        }
        val draft = buildDraft(current, commandKey)
        val command = buildCommand(draft)
        val attempt = if (gameId == null) {
            LastAttempt.Create(commandKey)
        } else {
            val versionToken = current.versionToken ?: return
            LastAttempt.Edit(gameId, versionToken, commandKey)
        }
        update { it.copy(isSaving = true, saveFailed = false, hasConflict = false, conflictGameId = null) }
        viewModelScope.launch {
            val result = when (attempt) {
                is LastAttempt.Create -> gameGateway.create(GroupId(groupId), command)
                is LastAttempt.Edit -> gameGateway.edit(
                    GroupId(groupId), attempt.gameId, GameVersionToken(attempt.versionToken), command,
                )
            }
            when (result) {
                is SaqzResult.Success -> {
                    savedState.remove<String>(KeyCommand)
                    update { it.copy(isSaving = false, saveFailed = false) }
                    emit(GameEditorEffect.Saved)
                }
                is SaqzResult.Failure -> {
                    val error = result.error
                    when (error) {
                        is GameError.Conflict -> update {
                            it.copy(
                                isSaving = false,
                                hasConflict = true,
                                conflictGameId = error.conflictGameId,
                            )
                        }
                        GameError.VersionConflict -> if (gameId == null) {
                            update { it.copy(isSaving = false, saveFailed = true, error = error.toUiError()) }
                        } else {
                            load()
                        }
                        else -> update {
                            it.copy(isSaving = false, saveFailed = true, error = error.toUiError())
                        }
                    }
                }
            }
        }
    }

    private fun openExistingGame() {
        val current = state.value
        current.conflictGameId?.let {
            emit(GameEditorEffect.OpenGameDetail(it))
            return
        }
        viewModelScope.launch {
            val result = gameGateway.list(GroupId(groupId))
            if (result is SaqzResult.Success) {
                result.value.firstOrNull {
                    it.id != gameId &&
                        it.localDate == current.form.localDate &&
                        it.localTime == current.form.localTime
                }?.let { emit(GameEditorEffect.OpenGameDetail(it.id)) }
            }
        }
    }

    private fun buildDraft(state: GameEditorState, commandKey: String): GameEditorDraft {
        val startsAt = localStart(state.form.localDate, state.form.localTime, state.zoneId)
        val deadline = startsAt?.let { start ->
            runCatching { (Instant.parse(start) - state.form.confirmationLeadMinutes.minutes).toString() }.getOrNull()
        }
        return GameEditorDraft(
            groupId = groupId,
            gameId = gameId,
            seriesId = null,
            commandKey = commandKey,
            version = state.versionToken?.let(::GameVersionToken),
            mode = GameEditorMode.ONE_TIME,
            form = GameEditorForm(
                title = state.form.title,
                venue = state.form.venue,
                localDate = state.form.localDate,
                localTime = state.form.localTime,
                zoneId = state.zoneId,
                startsAt = startsAt.orEmpty(),
                durationMinutes = state.form.durationMinutes.toString(),
                capacity = state.form.capacity.toString(),
                confirmationDeadline = deadline.orEmpty(),
                notes = state.form.notes,
                gameFeeCents = state.form.gameFeeCents,
            ),
        )
    }

    private fun buildCommand(draft: GameEditorDraft): GameWriteCommand =
        GameWriteCommand(
            requestId = draft.commandKey,
            title = draft.form.title.takeIf(String::isNotBlank),
            venue = draft.form.venue,
            localDate = draft.form.localDate,
            localTime = draft.form.localTime,
            zoneId = draft.form.zoneId,
            startsAt = draft.form.startsAt,
            durationMinutes = draft.form.durationMinutes.toIntOrNull(),
            capacity = draft.form.capacity.toIntOrNull(),
            confirmationDeadline = draft.form.confirmationDeadline,
            gameFeeCents = draft.form.gameFeeCents,
            useDefaultGameFee = draft.form.gameFeeCents == null,
            notes = draft.form.notes.takeIf(String::isNotBlank),
        )

    private fun localStart(date: String, time: String, zoneId: String): String? = runCatching {
        LocalDateTime.parse("${date}T$time").toInstant(TimeZone.of(zoneId)).toString()
    }.getOrNull()

    private fun showLoadFailure(generation: Int, error: GroupUiError) {
        if (generation != loadGeneration) return
        update { it.copy(isLoading = false, loadFailed = true, error = error) }
    }

    private fun updateForm(transform: GameEditorFields.() -> GameEditorFields) {
        update { state ->
            val form = state.form.transform()
            persistForm(form)
            state.copy(form = form, validationErrors = emptySet())
        }
    }

    private fun persistForm(form: GameEditorFields) {
        savedState[KeyDate] = form.localDate
        savedState[KeyTitle] = form.title
        savedState[KeyTime] = form.localTime
        savedState[KeyDuration] = form.durationMinutes
        savedState[KeyCapacity] = form.capacity
        savedState[KeyLead] = form.confirmationLeadMinutes
        savedState[KeyNotes] = form.notes
        form.venue?.let {
            savedState[KeyVenueName] = it.name
            savedState[KeyVenueAddress] = it.address
        }
    }

    private fun restoreForm(form: GameEditorFields): GameEditorFields = form.copy(
        title = savedState.get<String>(KeyTitle) ?: form.title,
        localDate = savedState.get<String>(KeyDate) ?: form.localDate,
        localTime = savedState.get<String>(KeyTime) ?: form.localTime,
        durationMinutes = savedState.get<Int>(KeyDuration) ?: form.durationMinutes,
        capacity = savedState.get<Int>(KeyCapacity) ?: form.capacity,
        confirmationLeadMinutes = savedState.get<Int>(KeyLead) ?: form.confirmationLeadMinutes,
        notes = savedState.get<String>(KeyNotes) ?: form.notes,
        venue = savedState.get<String>(KeyVenueName)?.let { name ->
            (form.venue ?: emptyVenue()).copy(
                name = name,
                address = savedState.get<String>(KeyVenueAddress).orEmpty(),
            )
        } ?: form.venue,
    )

    private sealed interface LastAttempt {
        val commandKey: String
        data class Create(override val commandKey: String) : LastAttempt
        data class Edit(val gameId: String, val versionToken: String, override val commandKey: String) : LastAttempt
    }

    private companion object {
        const val MIN_CAPACITY = 2
        const val DEFAULT_CAPACITY = 12
        const val DEFAULT_DURATION = 120
        const val DEFAULT_LEAD = 360
        const val KeyCommand = "game-editor-command-key"
        const val KeyDate = "game-editor-date"
        const val KeyTitle = "game-editor-title"
        const val KeyTime = "game-editor-time"
        const val KeyDuration = "game-editor-duration"
        const val KeyCapacity = "game-editor-capacity"
        const val KeyLead = "game-editor-confirmation-lead"
        const val KeyNotes = "game-editor-notes"
        const val KeyVenueName = "game-editor-venue-name"
        const val KeyVenueAddress = "game-editor-venue-address"
        const val DEFAULT_TITLE = "Jogo fora da recorrência"
    }
}

private fun confirmationLeadMinutes(startsAt: String, deadline: String): Int? = runCatching {
    (Instant.parse(startsAt) - Instant.parse(deadline)).inWholeMinutes.toInt()
}.getOrNull()

private fun emptyVenue() = GameVenue(name = "", address = "")

private fun GroupVenue.toGameVenue(): GameVenue = GameVenue(id, name, address, court)
