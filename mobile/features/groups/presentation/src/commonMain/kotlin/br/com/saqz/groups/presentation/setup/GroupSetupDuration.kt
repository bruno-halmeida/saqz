package br.com.saqz.groups.presentation.setup

/**
 * Duração do grupo: a duração é do grupo no desenho e por horário no modelo, então o valor se espelha em todos os
 * horários. Intent de outro assunto devolve o estado como veio.
 */
internal fun GroupSetupState.withDurationIntent(intent: GroupSetupIntent): GroupSetupState {
    if (intent !is GroupSetupIntent.SelectDuration) return this
    val slots = form.regularSlots.map { it.copy(durationMinutes = intent.minutes) }
    return copy(
        durationMinutes = intent.minutes,
        creationCommandKey = null,
        form = form.copy(regularSlots = slots),
    ).revalidated()
}
