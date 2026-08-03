package br.com.saqz.groups.presentation.gameeditor

/**
 * 4e — validação do formulário de jogo. Regra densa fora do ViewModel (AGENTS.md §4).
 * Data, horário e quadra são obrigatórios; os defaults do grupo são apenas um preenchimento inicial.
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
    if (venueAddressLength < VENUE_ADDRESS_MIN) {
        errors += GameEditorFieldError.VenueAddressMissing
    }
    if (venueAddressLength > VENUE_ADDRESS_MAX) {
        errors += GameEditorFieldError.VenueAddressTooLong
    }
    return errors
}

private const val VENUE_NAME_MIN = 2
private const val VENUE_NAME_MAX = 120
private const val VENUE_ADDRESS_MIN = 5
private const val VENUE_ADDRESS_MAX = 300
