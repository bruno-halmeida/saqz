package br.com.saqz.groups.presentation.presentation.schedule

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.ui.components.SlotDraft

/**
 * 2m — agenda e recorrência.
 *
 * `recurring` **não** se deriva de `slots.isEmpty()`: recorrência ligada sem nenhum
 * horário é um estado legítimo da tela e sumiria com a derivação.
 *
 * Convenção do `slotSheet`: `null` é sheet fechado; [NEW_SLOT] (-1) é o slot que ainda
 * não existe na lista. Índice >= 0 fica reservado para editar uma pílula existente —
 * o 2m não oferece esse toque (a pílula só tem o botão de remover), então nada produz
 * esse valor hoje.
 *
 * O contrato inteiro é `internal` porque [SlotDraft] é `internal` ao módulo; só o Root
 * atravessa a fronteira pública.
 */
@Immutable
internal data class GroupScheduleState(
    val isLoading: Boolean = true,
    val recurring: Boolean = true,
    val slots: List<GroupRegularSlotForm> = emptyList(),
    val durationMinutes: Int = 120,
    val confirmationLeadMinutes: Int = 360,
    val upcoming: List<UpcomingGameUi> = emptyList(),
    val isPaused: Boolean = false,
    val slotSheet: Int? = null,
    val slotDraft: SlotDraft = NEW_SLOT_DRAFT,
    val isSaving: Boolean = false,
) {
    companion object {
        const val NEW_SLOT = -1

        val NEW_SLOT_DRAFT = SlotDraft(weekday = GroupWeekday.TUESDAY, hour = 19, minute = 0)
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
    data class ToggleRecurring(val value: Boolean) : GroupScheduleIntent

    data object AddSlot : GroupScheduleIntent

    data class RemoveSlot(val slot: GroupRegularSlotForm) : GroupScheduleIntent

    data class PickDraftDay(val weekday: GroupWeekday) : GroupScheduleIntent

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
