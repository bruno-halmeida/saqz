package br.com.saqz.groups.presentation.gameeditor

import br.com.saqz.groups.domain.game.GameError

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
    if (form.notes.trim().let { it.isNotEmpty() && it.length !in 2..500 }) {
        errors += GameEditorFieldError.NotesInvalid
    }
    return errors
}

/**
 * O que o servidor recusou, traduzido para os campos do formulário. O backend que ainda exige o
 * endereço da quadra (v.0.0.17) responde `400 VALIDATION_FAILED` com `venue.address: is required`;
 * com o backend atual o endereço é opcional e isto devolve vazio. Vazio quer dizer "sem campo para
 * apontar": quem chama cai no aviso genérico.
 */
internal fun GameError.Validation.toGameEditorFieldErrors(): Set<GameEditorFieldError> = buildSet {
    if (VENUE_ADDRESS_FIELD in error.details.fieldMessages) add(GameEditorFieldError.VenueAddressRequired)
}

private const val VENUE_ADDRESS_FIELD = "venue.address"
private const val VENUE_NAME_MIN = 2
private const val VENUE_NAME_MAX = 120
private const val VENUE_ADDRESS_MIN = 5
private const val VENUE_ADDRESS_MAX = 300
