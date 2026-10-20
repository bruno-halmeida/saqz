package br.com.saqz.groups.presentation.setup

import br.com.saqz.groups.presentation.duration.CustomDuration
import br.com.saqz.groups.presentation.duration.DurationDraft

/**
 * Duração do grupo (DEB-105): chips e "Outros". A duração é do grupo no desenho e por horário no modelo, então o valor
 * se espelha em todos os horários. Intent de outro assunto devolve o estado como veio.
 */
internal fun GroupSetupState.withDurationIntent(intent: GroupSetupIntent): GroupSetupState {
    val draft = when (intent) {
        is GroupSetupIntent.SelectDuration -> CustomDuration.selectPreset(intent.minutes)
        GroupSetupIntent.SelectOtherDuration -> CustomDuration.startOther(DurationDraft(durationMinutes, durationText))
        is GroupSetupIntent.UpdateDurationText -> CustomDuration.type(intent.text)
        else -> return this
    }
    val slots = form.regularSlots.map { it.copy(durationMinutes = draft.minutes) }
    return copy(
        durationMinutes = draft.minutes,
        durationText = draft.text,
        creationCommandKey = null,
        form = form.copy(regularSlots = slots),
    ).revalidated()
}
