package br.com.saqz.groups.presentation.gameeditor

import br.com.saqz.groups.presentation.duration.CustomDuration

/**
 * 4e — validação do formulário de jogo. Regra densa fora do ViewModel (AGENTS.md §4).
 * Data, horário e o nome da quadra são obrigatórios; o endereço é opcional, mas, se vier, precisa ter
 * o mínimo do backend. Os defaults do grupo são apenas um preenchimento inicial.
 */
fun validateGameEditor(form: GameEditorFields): Set<GameEditorFieldError> {
    val errors = mutableSetOf<GameEditorFieldError>()
    if (form.localDate.isBlank()) errors += GameEditorFieldError.DateMissing
    if (form.localTime.isBlank()) errors += GameEditorFieldError.TimeMissing
    val venueNameLength = form.venue?.name?.trim().orEmpty().length
    val venueAddressLength = form.venue?.address?.trim().orEmpty().length
    if (venueNameLength < VENUE_NAME_MIN) {
        errors += GameEditorFieldError.VenueNameMissing
    }
    if (venueNameLength > VENUE_NAME_MAX) {
        errors += GameEditorFieldError.VenueNameTooLong
    }
    if (venueAddressLength in 1 until VENUE_ADDRESS_MIN) {
        errors += GameEditorFieldError.VenueAddressTooShort
    }
    if (venueAddressLength > VENUE_ADDRESS_MAX) {
        errors += GameEditorFieldError.VenueAddressTooLong
    }
    // Vazio = sem cobrança; preenchido precisa ser um valor de 0,01 a 999.999,99 (R$ 0,00 é recusado).
    if (form.gameFeeText.isNotBlank() && form.gameFeeCents.let { it == null || it !in 1L..GAME_FEE_MAX_CENTS }) {
        errors += GameEditorFieldError.GameFeeInvalid
    }
    if (form.durationInvalid()) errors += GameEditorFieldError.DurationInvalid
    return errors
}

/** Formulário ainda sem duração (0, antes da carga) não acusa erro. */
private fun GameEditorFields.durationInvalid() = durationMinutes != 0 && CustomDuration.isInvalid(durationMinutes)

private const val GAME_FEE_MAX_CENTS = 99_999_999L
private const val VENUE_NAME_MIN = 2
private const val VENUE_NAME_MAX = 120
private const val VENUE_ADDRESS_MIN = 5
private const val VENUE_ADDRESS_MAX = 300
