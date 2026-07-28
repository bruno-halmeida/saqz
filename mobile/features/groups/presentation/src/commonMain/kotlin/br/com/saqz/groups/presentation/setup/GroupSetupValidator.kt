package br.com.saqz.groups.presentation.setup

import br.com.saqz.groups.model.GroupLevel

/** Os cinco campos que o `2g` marca em vermelho. */
enum class GroupSetupError {
    NameRequired,
    CustomLevelRequired,
    CapacityTooLow,
    SlotsRequired,
    VenueAddressNotFound,
}

/**
 * Função pura, fora da ViewModel (AGENTS.md §4). Valida sobre o `cleaned()` para que
 * um nome de espaços em branco conte como vazio, exatamente como o comando contaria.
 */
fun validate(state: GroupSetupState): Set<GroupSetupError> {
    val form = state.form.cleaned()
    val venue = form.defaultVenue
    return buildSet {
        if (form.name.isEmpty()) add(GroupSetupError.NameRequired)
        if (form.level == GroupLevel.CUSTOM && form.customLevel == null) {
            add(GroupSetupError.CustomLevelRequired)
        }
        if ((form.defaultCapacity ?: 0) < GroupSetupDefaults.MinCapacity) {
            add(GroupSetupError.CapacityTooLow)
        }
        if (state.recurring && form.regularSlots.isEmpty()) add(GroupSetupError.SlotsRequired)
        // ponytail: sem geocodificação, "não encontramos esse endereço" é a ausência do
        // endereço de uma quadra que já tem nome. A checagem real nasce junto do
        // gateway de local, que é quem sabe resolver a rua.
        if (venue != null && venue.name.isNotEmpty() && venue.address.isEmpty()) {
            add(GroupSetupError.VenueAddressNotFound)
        }
    }
}
