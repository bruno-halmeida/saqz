package br.com.saqz.groups.presentation.setup

import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupWeekday

/** Ordem em que os dias marcados viram horários: a da folha, de domingo a sábado. */
private val SlotWeekOrder = listOf(
    GroupWeekday.SUNDAY,
    GroupWeekday.MONDAY,
    GroupWeekday.TUESDAY,
    GroupWeekday.WEDNESDAY,
    GroupWeekday.THURSDAY,
    GroupWeekday.FRIDAY,
    GroupWeekday.SATURDAY,
)

internal fun GroupSetupState.withSlotWeekdayToggled(weekday: GroupWeekday): GroupSetupState {
    val days = slotDraft.weekdays
    return copy(slotDraft = slotDraft.copy(weekdays = if (weekday in days) days - weekday else days + weekday))
}

/**
 * Confirma a folha de dias e horário: horário novo vira um por dia marcado, sem repetir dia e hora que o grupo já tem;
 * horário em edição troca o que estava no índice. `null` quando não há folha de horário aberta ou nenhum dia marcado.
 */
internal fun GroupSetupState.withSlotConfirmed(): GroupSetupState? {
    val sheet = sheet as? GroupSetupSheet.Slot ?: return null
    val days = SlotWeekOrder.filter { it in slotDraft.weekdays }
    if (days.isEmpty()) return null
    val startTime = "${slotDraft.hour.pad()}:${slotDraft.minute.pad()}"
    fun slotOf(day: GroupWeekday) =
        GroupRegularSlotForm(weekday = day, startTime = startTime, durationMinutes = durationMinutes)
    val slots = form.regularSlots
    val updated = if (sheet.index == null || sheet.index !in slots.indices) {
        slots + days.map(::slotOf).filter { new -> slots.none { it.weekday == new.weekday && it.startTime == new.startTime } }
    } else {
        slots.toMutableList().also { it[sheet.index] = slotOf(days.first()) }
    }
    return copy(sheet = null, creationCommandKey = null, form = form.copy(regularSlots = updated)).revalidated()
}

private fun Int.pad() = toString().padStart(2, '0')
