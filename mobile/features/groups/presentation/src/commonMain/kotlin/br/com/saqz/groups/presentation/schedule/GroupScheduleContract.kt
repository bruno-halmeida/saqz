package br.com.saqz.groups.presentation.schedule

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.setup.GroupSlotDraft

/**
 * 2m — agenda e recorrência.
 *
 * Durante a edição local, `recurring` **não** se deriva de `slots.isEmpty()`: recorrência
 * ligada sem nenhum horário é um estado legítimo. No primeiro carregamento, o snapshot
 * remoto só expõe os horários recorrentes, então ele serve para hidratar o valor inicial.
 *
 * Convenção do `slotSheet`: `null` é sheet fechado; [NEW_SLOT] (-1) é o slot que ainda
 * não existe na lista. Índice >= 0 edita o horário que está nessa posição da lista
 * ([GroupScheduleIntent.EditSlot]); a pílula ainda não oferece esse toque, então a tela não produz esse valor hoje.
 *
 * O contrato inteiro é `internal`; só o Root atravessa a fronteira pública.
 */
@Immutable
internal data class GroupScheduleState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val error: GroupUiError? = null,
    val recurring: Boolean = true,
    val slots: List<GroupRegularSlotForm> = emptyList(),
    val durationMinutes: Int = 120,
    /** Salvar com duração fora de 1h a 8h: o erro aparece no contador. */
    val durationError: Boolean = false,
    val confirmationLeadMinutes: Int = 360,
    val upcoming: List<UpcomingGameUi> = emptyList(),
    val isPaused: Boolean = false,
    val slotSheet: Int? = null,
    val slotDraft: GroupSlotDraft = NEW_SLOT_DRAFT,
    val isSaving: Boolean = false,
) {
    companion object {
        const val NEW_SLOT = -1

        val NEW_SLOT_DRAFT = GroupSlotDraft()
    }
}

@Immutable
internal data class UpcomingGameUi(
    val id: String,
    val day: String,
    val month: String,
    val label: String,
    val venue: String,
    val status: UpcomingGameStatus,
)

internal enum class UpcomingGameStatus { Published, Scheduled }

internal sealed interface GroupScheduleIntent {
    data object Retry : GroupScheduleIntent

    /**
     * Volta à tela: recarga por baixo só da lista `upcoming`. Nunca toca no formulário — com
     * edição pendente, folha aberta, salvando ou carga em voo é ignorado.
     */
    data object Refresh : GroupScheduleIntent

    data class ToggleRecurring(val value: Boolean) : GroupScheduleIntent

    data object AddSlot : GroupScheduleIntent

    data class RemoveSlot(val slot: GroupRegularSlotForm) : GroupScheduleIntent

    data class EditSlot(val index: Int) : GroupScheduleIntent

    /** Ao editar um horário que existe, um dia só. */
    data class PickDraftDay(val weekday: GroupWeekday) : GroupScheduleIntent

    /** Ao adicionar, vários dias de uma vez. */
    data class ToggleDraftDay(val weekday: GroupWeekday) : GroupScheduleIntent

    data class PickDraftTime(val hour: Int, val minute: Int) : GroupScheduleIntent

    data object ConfirmSlot : GroupScheduleIntent

    data object DismissSlotSheet : GroupScheduleIntent

    data class SelectDuration(val minutes: Int) : GroupScheduleIntent

    data class SelectConfirmationLead(val minutes: Int) : GroupScheduleIntent

    data object TogglePause : GroupScheduleIntent

    data class OpenGame(val gameId: String) : GroupScheduleIntent

    data object Save : GroupScheduleIntent
}

internal sealed interface GroupScheduleEffect {
    data object Saved : GroupScheduleEffect

    /** Fluxo 4 — quem sabe abrir o jogo é o host da rota. */
    data class OpenGame(val gameId: String) : GroupScheduleEffect
}
