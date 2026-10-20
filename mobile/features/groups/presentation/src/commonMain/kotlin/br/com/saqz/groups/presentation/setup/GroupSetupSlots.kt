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

internal fun GroupSlotDraft.withWeekdayToggled(weekday: GroupWeekday): GroupSlotDraft =
    copy(weekdays = if (weekday in weekdays) weekdays - weekday else weekdays + weekday)

internal fun GroupSetupState.withSlotWeekdayToggled(weekday: GroupWeekday): GroupSetupState =
    copy(slotDraft = slotDraft.withWeekdayToggled(weekday))

/**
 * Confirma a folha de dias e horário sobre a lista de horários: horário novo vira um por dia marcado, sem repetir dia e
 * hora que o grupo já tem; horário em edição ([index] válido) troca o que estava no índice, ou só some se o novo dia e
 * hora já existem em outro horário. `null` quando nenhum dia está marcado. Compartilhado entre criar/editar grupo e
 * Jogos e horários.
 */
internal fun List<GroupRegularSlotForm>.withSlotConfirmed(
    draft: GroupSlotDraft,
    durationMinutes: Int,
    index: Int?,
): List<GroupRegularSlotForm>? {
    val days = SlotWeekOrder.filter { it in draft.weekdays }
    if (days.isEmpty()) return null
    val startTime = "${draft.hour.pad()}:${draft.minute.pad()}"
    fun slotOf(day: GroupWeekday) =
        GroupRegularSlotForm(weekday = day, startTime = startTime, durationMinutes = durationMinutes)
    return if (index == null || index !in indices) {
        this + days.map(::slotOf).filter { new -> none { it.weekday == new.weekday && it.startTime == new.startTime } }
    } else {
        val edited = slotOf(days.first())
        val duplicate = withIndex().any { (i, other) ->
            i != index && other.weekday == edited.weekday && other.startTime == edited.startTime
        }
        if (duplicate) filterIndexed { i, _ -> i != index } else toMutableList().also { it[index] = edited }
    }
}

/**
 * Confirma a folha de dias e horário: `null` quando não há folha de horário aberta ou nenhum dia marcado.
 */
internal fun GroupSetupState.withSlotConfirmed(): GroupSetupState? {
    val sheet = sheet as? GroupSetupSheet.Slot ?: return null
    val updated = form.regularSlots.withSlotConfirmed(slotDraft, durationMinutes, sheet.index) ?: return null
    return copy(sheet = null, creationCommandKey = null, form = form.copy(regularSlots = updated)).revalidated()
}

internal fun GroupRegularSlotForm.toDraft(): GroupSlotDraft {
    val parts = startTime.split(':')
    return GroupSlotDraft(weekday, parts.getOrNull(0)?.toIntOrNull() ?: 0, parts.getOrNull(1)?.toIntOrNull() ?: 0)
}

private fun Int.pad() = toString().padStart(2, '0')
