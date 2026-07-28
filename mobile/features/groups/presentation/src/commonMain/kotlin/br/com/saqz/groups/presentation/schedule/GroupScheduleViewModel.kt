package br.com.saqz.groups.presentation.schedule

import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.presentation.ui.components.SlotDraft

/**
 * ponytail: a tela não carrega dado — o estado inicial chega pelo construtor. Quando o
 * gateway de agenda entrar, este parâmetro vira um `Flow` coletado no `init` e nenhuma
 * tela muda.
 */
internal class GroupScheduleViewModel(
    initialState: GroupScheduleState = GroupScheduleState(),
) : MviViewModel<GroupScheduleState, GroupScheduleIntent, GroupScheduleEffect>(initialState) {

    override fun onIntent(intent: GroupScheduleIntent) {
        when (intent) {
            is GroupScheduleIntent.ToggleRecurring -> update { it.copy(recurring = intent.value) }
            GroupScheduleIntent.AddSlot -> update {
                it.copy(
                    slotSheet = GroupScheduleState.NEW_SLOT,
                    slotDraft = GroupScheduleState.NEW_SLOT_DRAFT,
                )
            }

            is GroupScheduleIntent.RemoveSlot -> update { it.copy(slots = it.slots - intent.slot) }
            is GroupScheduleIntent.PickDraftDay -> update {
                it.copy(slotDraft = it.slotDraft.copy(weekday = intent.weekday))
            }

            is GroupScheduleIntent.PickDraftTime -> update {
                it.copy(slotDraft = it.slotDraft.copy(hour = intent.hour, minute = intent.minute))
            }

            GroupScheduleIntent.ConfirmSlot -> confirmSlot()
            GroupScheduleIntent.DismissSlotSheet -> update { it.copy(slotSheet = null) }
            is GroupScheduleIntent.SelectDuration -> update {
                // A duração é do grupo, mas o `GroupRegularSlotForm` a guarda por slot: sem
                // espelhar aqui, os slots já criados ficariam com o valor velho e salvar
                // persistiria estado inconsistente.
                // ponytail: um valor para todos. Se o produto pedir duração por slot, a
                // escolha sobe para o picker e este espelhamento sai.
                it.copy(
                    durationMinutes = intent.minutes,
                    slots = it.slots.map { slot -> slot.copy(durationMinutes = intent.minutes) },
                )
            }
            is GroupScheduleIntent.SelectConfirmationLead -> update {
                it.copy(confirmationLeadMinutes = intent.minutes)
            }

            GroupScheduleIntent.TogglePause -> update { it.copy(isPaused = !it.isPaused) }
            is GroupScheduleIntent.OpenGame -> emit(GroupScheduleEffect.OpenGame(intent.gameId))
            GroupScheduleIntent.Save -> save()
        }
    }

    private fun confirmSlot() {
        // Intent inválido retorna cedo: confirmar com o sheet fechado não mexe em nada.
        if (state.value.slotSheet == null) return
        update {
            it.copy(
                slots = it.slots + it.slotDraft.toForm(it.durationMinutes),
                slotSheet = null,
            )
        }
    }

    private fun save() {
        // Intent inválido retorna cedo: salvar o que ainda não carregou gravaria o estado
        // vazio e, pior, emitiria `Saved` — que fecha a tela como se tivesse dado certo.
        if (state.value.isLoading) return
        // ponytail: o seam do gateway é aqui. Hoje `isSaving` fica marcado e o efeito sai
        // na hora; a chamada real entra no meio e limpa a flag quando responder.
        update { it.copy(isSaving = true) }
        emit(GroupScheduleEffect.Saved)
    }
}

private fun SlotDraft.toForm(durationMinutes: Int) = GroupRegularSlotForm(
    weekday = weekday,
    startTime = "${hour.padded()}:${minute.padded()}",
    durationMinutes = durationMinutes,
)

private fun Int.padded() = toString().padStart(2, '0')
