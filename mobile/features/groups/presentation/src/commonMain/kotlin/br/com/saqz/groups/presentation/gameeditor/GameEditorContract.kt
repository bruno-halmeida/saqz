package br.com.saqz.groups.presentation.gameeditor

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.presentation.GroupUiError

/**
 * 4a/4b/4e — editor de jogo fora da recorrência. `gameId == null` cria; presente edita.
 * O formulário carrega defaults da quadra/vagas/prazo do grupo e deixa o admin trocar só
 * neste jogo. Data e horário compartilham o mesmo bottom-sheet de rolagem (4b).
 */
@Immutable
data class GameEditorState(
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val error: GroupUiError? = null,
    val groupName: String = "",
    val zoneId: String = "",
    val form: GameEditorFields = GameEditorFields(),
    val validationErrors: Set<GameEditorFieldError> = emptySet(),
    val versionToken: String? = null,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val hasConflict: Boolean = false,
    val conflictGameId: String? = null,
)

/** Campos editáveis. Strings formatadas já vêm prontas para a UI (AGENTS.md §8). */
@Immutable
data class GameEditorFields(
    val localDate: String = "",
    val localTime: String = "",
    val durationMinutes: Int = 0,
    val venue: GameVenue? = null,
    val venueEditable: Boolean = false,
    val capacity: Int = 0,
    val confirmationLeadMinutes: Int = 0,
    val notes: String = "",
) {
    val hasDateTime: Boolean get() = localDate.isNotBlank() && localTime.isNotBlank()
}

enum class GameEditorFieldError { DateMissing, TimeMissing }

sealed interface GameEditorIntent {
    data object Retry : GameEditorIntent
    data object Submit : GameEditorIntent
    data object OpenDateTimePicker : GameEditorIntent
    data class SaveDateTime(val date: String, val time: String) : GameEditorIntent
    data class SelectDuration(val minutes: Int) : GameEditorIntent
    data class UpdateVenueName(val name: String) : GameEditorIntent
    data class UpdateVenueAddress(val address: String) : GameEditorIntent
    data class UpdateCapacity(val capacity: Int) : GameEditorIntent
    data class SelectConfirmationLead(val minutes: Int) : GameEditorIntent
    data class UpdateNotes(val notes: String) : GameEditorIntent
    data object DismissConflict : GameEditorIntent
    data object OpenExistingGame : GameEditorIntent
}

sealed interface GameEditorEffect {
    data object Saved : GameEditorEffect
    data class OpenGameDetail(val gameId: String) : GameEditorEffect
}
