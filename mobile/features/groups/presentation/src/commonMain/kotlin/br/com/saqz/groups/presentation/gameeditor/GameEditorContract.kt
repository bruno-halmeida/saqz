package br.com.saqz.groups.presentation.gameeditor

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.group.GroupRegularSlot
import br.com.saqz.groups.presentation.GroupUiError

/**
 * 4a/4b/4e — editor de jogo. `gameId == null` cria; presente edita.
 * O formulário carrega defaults da quadra/vagas/prazo do grupo e deixa o admin trocar só
 * neste jogo. Data e horário compartilham o mesmo bottom-sheet de rolagem (4b).
 *
 * Na criação, "Repetir toda semana" grava o dia e a hora do jogo como horário regular do grupo
 * (a mesma recorrência da agenda, 2m), para não obrigar ninguém a editar o grupo. O jogo marcado
 * sai publicado; os das próximas semanas o backend cria como rascunho.
 */
@Immutable
data class GameEditorState(
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val error: GroupUiError? = null,
    val groupName: String = "",
    val zoneId: String = "",
    val trialEndsAt: String? = null,
    val trialWarningVisible: Boolean = false,
    val form: GameEditorFields = GameEditorFields(),
    /** Só na criação, e só com perfil completo: na edição a recorrência é da agenda do grupo. */
    val recurrenceOffered: Boolean = false,
    /** Horários regulares que o grupo já tem; a pílula deste jogo entra ao lado deles. */
    val regularSlots: List<GroupRegularSlot> = emptyList(),
    /** Sem quadra padrão, a quadra deste jogo vira a do grupo quando a repetição liga. */
    val groupHasVenue: Boolean = false,
    /** O jogo foi marcado, mas o horário regular não foi gravado; o retry resume o rascunho. */
    val recurrenceFailed: Boolean = false,
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
    val title: String = "",
    val localDate: String = "",
    val localTime: String = "",
    val durationMinutes: Int = 0,
    val gameFeeCents: Long? = null,
    val venue: GameVenue? = null,
    val venueEditable: Boolean = false,
    val capacity: Int = 0,
    val confirmationLeadMinutes: Int = 0,
    val notes: String = "",
    /** "Repetir toda semana": só vale na criação ([GameEditorState.recurrenceOffered]). */
    val recurring: Boolean = false,
) {
    val hasDateTime: Boolean get() = localDate.isNotBlank() && localTime.isNotBlank()
}

enum class GameEditorFieldError {
    DateMissing,
    TimeMissing,
    VenueNameMissing,
    VenueAddressMissing,
    VenueNameTooLong,
    VenueAddressTooLong,
    NotesInvalid,
}

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
    data class ToggleRecurring(val value: Boolean) : GameEditorIntent
    data object DismissConflict : GameEditorIntent
    data object OpenExistingGame : GameEditorIntent
}

sealed interface GameEditorEffect {
    data object Saved : GameEditorEffect
    data class OpenGameDetail(val gameId: String) : GameEditorEffect
}
