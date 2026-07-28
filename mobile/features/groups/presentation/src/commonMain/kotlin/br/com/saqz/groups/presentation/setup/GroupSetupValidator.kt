package br.com.saqz.groups.presentation.setup

import br.com.saqz.groups.model.GroupLevel

/**
 * Os cinco campos que o `2g` marca em vermelho, mais os dois que o gateway exige.
 *
 * `ModalityRequired` e `CompositionRequired` não estão no desenho do `2g`, mas
 * `KtorGroupGateway.toRequest` faz `requireNotNull(form.modality)` e
 * `requireNotNull(form.composition)`: sem eles a revisão deixaria confirmar um comando
 * que estoura na camada de dados. Os demais campos do DTO são nulos-aceitos.
 */
enum class GroupSetupError {
    NameRequired,
    ModalityRequired,
    CompositionRequired,
    CustomLevelRequired,
    CapacityTooLow,
    SlotsRequired,
    VenueNameRequired,
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
        if (form.modality == null) add(GroupSetupError.ModalityRequired)
        if (form.composition == null) add(GroupSetupError.CompositionRequired)
        if (form.level == GroupLevel.CUSTOM && form.customLevel == null) {
            add(GroupSetupError.CustomLevelRequired)
        }
        // `defaultCapacity` é nulo-aceito no DTO: ausência não é erro, valor abaixo de
        // dois é (o `2g` pinta o 1 em vermelho). A tela mostra o padrão quando é nulo.
        form.defaultCapacity?.let {
            if (it < GroupSetupDefaults.MinCapacity) add(GroupSetupError.CapacityTooLow)
        }
        if (state.recurring && form.regularSlots.isEmpty()) add(GroupSetupError.SlotsRequired)
        // A quadra inteira é opcional, mas pela metade não existe: o
        // `GroupProfileDefaultsValidator.validateVenue` do backend exige nome **e**
        // endereço sempre que `defaultVenue` vem preenchido. A ViewModel devolve a
        // quadra a `null` quando os dois campos ficam vazios, então aqui só sobra o
        // preenchimento parcial.
        if (venue != null && venue.name.isEmpty()) add(GroupSetupError.VenueNameRequired)
        // ponytail: sem geocodificação, "não encontramos esse endereço" é a ausência do
        // endereço de uma quadra que já começou a ser preenchida. A checagem real nasce
        // junto do gateway de local, que é quem sabe resolver a rua.
        if (venue != null && venue.address.isEmpty()) add(GroupSetupError.VenueAddressNotFound)
    }
}
