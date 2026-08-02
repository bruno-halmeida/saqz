package br.com.saqz.groups.presentation.schedule

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.toUiError
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.presentation.ui.components.SlotDraft
import kotlinx.coroutines.launch

internal class GroupScheduleViewModel(
    val groupId: String,
    private val gameGateway: GameGateway,
    initialState: GroupScheduleState = GroupScheduleState(),
) : MviViewModel<GroupScheduleState, GroupScheduleIntent, GroupScheduleEffect>(initialState) {

    private var loadGeneration = 0

    init {
        load()
    }

    override fun onIntent(intent: GroupScheduleIntent) {
        when (intent) {
            GroupScheduleIntent.Retry -> load()
            is GroupScheduleIntent.ToggleRecurring -> update { it.copy(recurring = intent.value) }
            GroupScheduleIntent.AddSlot -> update {
                it.copy(slotSheet = GroupScheduleState.NEW_SLOT, slotDraft = GroupScheduleState.NEW_SLOT_DRAFT)
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

    private fun load() {
        val generation = ++loadGeneration
        update { it.copy(isLoading = true, loadFailed = false, error = null) }
        viewModelScope.launch {
            when (val result = gameGateway.list(GroupId(groupId))) {
                is SaqzResult.Failure -> showFailure(generation, result.error.toUiError())
                is SaqzResult.Success -> {
                    if (generation != loadGeneration) return@launch
                    update {
                        it.copy(
                            isLoading = false,
                            loadFailed = false,
                            error = null,
                            upcoming = result.value
                                .filter { game -> game.status != GameStatus.Cancelled }
                                .map(Game::toUpcoming),
                        )
                    }
                }
            }
        }
    }

    private fun showFailure(generation: Int, error: GroupUiError) {
        if (generation != loadGeneration) return
        update { it.copy(isLoading = false, loadFailed = true, error = error) }
    }

    private fun confirmSlot() {
        if (state.value.slotSheet == null) return
        update {
            it.copy(
                slots = it.slots + it.slotDraft.toForm(it.durationMinutes),
                slotSheet = null,
            )
        }
    }

    private fun save() {
        if (state.value.isLoading || state.value.loadFailed) return
        update { it.copy(isSaving = true) }
        // A agenda editing endpoint is Fluxo 4; this ticket only wires its read path.
        emit(GroupScheduleEffect.Saved)
    }
}

private fun Game.toUpcoming() = UpcomingGameUi(
    id = id,
    day = localDate,
    month = "",
    label = "$localTime · $title",
    venue = venue.name,
    status = if (status == GameStatus.Published) UpcomingGameStatus.Published else UpcomingGameStatus.Scheduled,
)

private fun SlotDraft.toForm(durationMinutes: Int) = GroupRegularSlotForm(
    weekday = weekday,
    startTime = "${hour.padded()}:${minute.padded()}",
    durationMinutes = durationMinutes,
)

private fun Int.padded() = toString().padStart(2, '0')
