package br.com.saqz.groups.presentation.schedule

import androidx.lifecycle.viewModelScope
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.GroupId
import br.com.saqz.groups.presentation.duration.CustomDuration
import br.com.saqz.groups.presentation.duration.DurationDraft
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameGateway
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.*
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.toUiError
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.presentation.ui.components.SlotDraft
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

internal class GroupScheduleViewModel(
    val groupId: String,
    private val gameGateway: GameGateway,
    private val scheduleGateway: GroupScheduleGateway,
    initialState: GroupScheduleState = GroupScheduleState(),
    private val todayInZone: (String) -> String = { zoneId ->
        Clock.System.todayIn(TimeZone.of(zoneId)).toString()
    },
) : MviViewModel<GroupScheduleState, GroupScheduleIntent, GroupScheduleEffect>(initialState) {

    private var loadGeneration = 0
    private var versionToken: GroupVersionToken? = null
    private var loadInFlight = false

    /** O formulário como veio do servidor: o que difere dele é edição pendente. */
    private var loadedForm: List<Any>? = null

    init {
        load()
    }

    override fun handleIntent(intent: GroupScheduleIntent) {
        if (state.value.isSaving) return
        when (intent) {
            GroupScheduleIntent.Retry -> load()
            GroupScheduleIntent.Refresh -> refresh()
            is GroupScheduleIntent.ToggleRecurring -> update { it.copy(recurring = intent.value, isPaused = it.isPaused && intent.value) }
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
            is GroupScheduleIntent.SelectDuration,
            GroupScheduleIntent.SelectOtherDuration,
            is GroupScheduleIntent.UpdateDurationText,
            -> handleDurationIntent(intent)
            is GroupScheduleIntent.SelectConfirmationLead -> update {
                it.copy(confirmationLeadMinutes = intent.minutes)
            }
            GroupScheduleIntent.TogglePause -> if (state.value.recurring) update { it.copy(isPaused = !it.isPaused) }
            is GroupScheduleIntent.OpenGame -> emit(GroupScheduleEffect.OpenGame(intent.gameId))
            GroupScheduleIntent.Save -> save()
        }
    }

    private fun GroupScheduleState.form(): List<Any> =
        listOf(recurring, isPaused, slots, durationMinutes, confirmationLeadMinutes)

    private fun handleDurationIntent(intent: GroupScheduleIntent) {
        val current = DurationDraft(state.value.durationMinutes, state.value.durationText)
        when (intent) {
            is GroupScheduleIntent.SelectDuration -> changeDuration(CustomDuration.selectPreset(intent.minutes))
            GroupScheduleIntent.SelectOtherDuration -> changeDuration(CustomDuration.startOther(current))
            is GroupScheduleIntent.UpdateDurationText -> changeDuration(CustomDuration.type(intent.text))
            else -> Unit
        }
    }

    private fun changeDuration(draft: DurationDraft) = update {
        it.copy(
            durationMinutes = draft.minutes,
            durationText = draft.text,
            durationError = false,
            slots = it.slots.map { slot -> slot.copy(durationMinutes = draft.minutes) },
        )
    }
    /**
     * Recarga por baixo ([GroupScheduleIntent.Refresh]): só a lista `upcoming`, e só sem edição
     * pendente — o formulário nunca é sobrescrito. Falha mantém a lista que está na tela. Se a
     * primeira carga falhou não há nada para preservar, e a volta tenta a carga completa de novo.
     */
    private fun refresh() {
        if (isFirstAppearance()) return
        val current = state.value
        if (loadInFlight || current.isLoading) return
        if (current.loadFailed) {
            load()
            return
        }
        if (current.slotSheet != null || current.form() != loadedForm) return
        val generation = loadGeneration
        loadInFlight = true
        val job = viewModelScope.launch {
            val result = gameGateway.list(GroupId(groupId))
            if (generation != loadGeneration || result !is SaqzResult.Success) return@launch
            update { it.copy(upcoming = result.value.filter { game -> game.isUpcoming(todayInZone) }.map(Game::toUpcoming)) }
        }
        job.invokeOnCompletion { if (generation == loadGeneration) loadInFlight = false }
    }

    private fun load() {
        val generation = ++loadGeneration
        loadInFlight = true
        update { it.copy(isLoading = true, loadFailed = false, error = null) }
        val job = viewModelScope.launch {
            val snapshot = when (val result = scheduleGateway.readSchedule(GroupId(groupId))) {
                is SaqzResult.Failure -> {
                    showFailure(generation, result.error.toUiError())
                    return@launch
                }
                is SaqzResult.Success -> result.value
            }
            if (generation != loadGeneration) return@launch

            val result = gameGateway.list(GroupId(groupId))
            if (generation != loadGeneration) return@launch
            versionToken = snapshot.versionToken
            when (result) {
                is SaqzResult.Failure -> showFailure(generation, result.error.toUiError())
                is SaqzResult.Success -> update {
                    val schedule = snapshot.schedule
                    val slots = schedule.slots.map {
                        GroupRegularSlotForm(
                            weekday = br.com.saqz.groups.model.GroupWeekday.valueOf(it.weekday.name),
                            startTime = it.startTime,
                            durationMinutes = schedule.durationMinutes,
                        )
                    }
                    it.copy(
                        isLoading = false,
                        loadFailed = false,
                        error = null,
                        recurring = schedule.recurring,
                        isPaused = schedule.paused,
                        slots = slots,
                        durationMinutes = schedule.durationMinutes,
                        confirmationLeadMinutes = schedule.confirmationLeadMinutes,
                        upcoming = result.value
                            .filter { game -> game.isUpcoming(todayInZone) }
                            .map(Game::toUpcoming),
                    ).also { loaded -> loadedForm = loaded.form() }
                }
            }
        }
        job.invokeOnCompletion { if (generation == loadGeneration) loadInFlight = false }
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
        val current = state.value
        val version = versionToken ?: return
        if (current.isLoading || current.loadFailed || current.isSaving) return
        if (CustomDuration.isInvalid(DurationDraft(current.durationMinutes, current.durationText))) {
            update { it.copy(durationError = true) }
            return
        }
        val slots = if (current.recurring) current.slots.map {
            GroupScheduleSlot(GroupWeekday.valueOf(it.weekday.name), it.startTime)
        } else emptyList()
        if (current.recurring && (slots.isEmpty() || slots.distinct().size != slots.size)) {
            update { it.copy(error = GroupUiError.Validation) }
            return
        }
        val schedule = GroupSchedule(
            current.recurring, slots, current.durationMinutes, current.confirmationLeadMinutes,
            current.recurring && current.isPaused,
        )
        update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val result = scheduleGateway.updateSchedule(GroupId(groupId), version, schedule)) {
                is SaqzResult.Success -> {
                    versionToken = result.value.versionToken
                    update { it.copy(isSaving = false) }
                    emit(GroupScheduleEffect.Saved)
                }
                is SaqzResult.Failure -> update { it.copy(isSaving = false, error = result.error.toUiError()) }
            }
        }
    }
}

private fun Game.isUpcoming(todayInZone: (String) -> String): Boolean {
    if (status != GameStatus.Draft && status != GameStatus.Published) return false
    val today = runCatching { todayInZone(zoneId) }.getOrNull() ?: return false
    return localDate.substringBefore('T') >= today
}

private fun Game.toUpcoming(): UpcomingGameUi {
    val (day, month) = localDate.toDateBadge()
    return UpcomingGameUi(
        id = id,
        day = day,
        month = month,
        label = "${localTime.toHourLabel()} · $title",
        venue = venue.name,
        status = if (status == GameStatus.Published) UpcomingGameStatus.Published else UpcomingGameStatus.Scheduled,
    )
}

private val PortugueseMonthLabels = listOf(
    "JAN", "FEV", "MAR", "ABR", "MAI", "JUN",
    "JUL", "AGO", "SET", "OUT", "NOV", "DEZ",
)

private fun String.toDateBadge(): Pair<String, String> {
    val parts = substringBefore('T').split('-')
    val month = parts.getOrNull(1)?.toIntOrNull()
    val day = parts.getOrNull(2)?.toIntOrNull()
    val validMonth = month?.let { it in 1..12 } == true
    val validDay = day?.let { it in 1..31 } == true
    return if (parts.getOrNull(0)?.length == 4 && validMonth && validDay) {
        parts[2].padStart(2, '0') to PortugueseMonthLabels[month - 1]
    } else {
        this to ""
    }
}

private fun SlotDraft.toForm(durationMinutes: Int) = GroupRegularSlotForm(
    weekday = weekday,
    startTime = "${hour.padded()}:${minute.padded()}",
    durationMinutes = durationMinutes,
)

private fun Int.padded() = toString().padStart(2, '0')

/** `localTime` chega da API como `HH:mm` ou `HH:mm:ss`; a lista mostra `HHhmm`, como o resto do app. */
private fun String.toHourLabel(): String {
    val parts = split(':')
    val hour = parts.getOrNull(0)?.padStart(2, '0') ?: return this
    val minute = parts.getOrNull(1)?.take(2)?.padStart(2, '0') ?: "00"
    return "${hour}h$minute"
}
