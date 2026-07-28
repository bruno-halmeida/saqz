package br.com.saqz.groups.presentation.setup

import br.com.saqz.groups.model.GroupLevel

/**
 * Os cinco campos que o `2g` marca em vermelho, mais três que só o backend exige.
 *
 * `ModalityRequired` e `CompositionRequired` não estão no desenho, mas
 * `KtorGroupGateway.toRequest` faz `requireNotNull` nos dois: sem eles a revisão deixaria
 * confirmar um comando que estoura na camada de dados. `VenueNameRequired` veio da mesma
 * conferência, um nível abaixo — `GroupProfileDefaultsValidator.validateVenue` exige nome
 * **e** endereço sempre que a quadra vem preenchida.
 */
enum class GroupSetupError {
    NameRequired,
    ModalityRequired,
    CompositionRequired,
    CustomLevelRequired,
    DescriptionTooShort,
    CapacityOutOfRange,
    SlotsRequired,
    VenueNameRequired,
    VenueAddressNotFound,
}

/**
 * Comprimentos de texto espelhados de `GroupProfileDefaultsValidator`
 * (`backend/features/groups/src/main/kotlin/br/com/saqz/groups/domain/group/GroupProfileDefaults.kt`),
 * que é o contrato real — o `requireNotNull` do gateway Ktor é só a ponta dele.
 *
 * | Campo | Linha | Regra do backend | Aqui |
 * | -- | -- | -- | -- |
 * | `name` | 109 | `requiredText(2, 80)` | mínimo valida, máximo corta |
 * | `description` | 110 | `optionalText(2, 500)` | mínimo valida, máximo corta |
 * | `customLevel` | 112 | `optionalText(2, 40)` | mínimo valida, máximo corta |
 * | `defaultVenue.name` | 171 | `requiredText(2, 120)` | mínimo valida, máximo corta |
 * | `defaultVenue.address` | 172 | `requiredText(5, 300)` | mínimo valida, máximo corta |
 *
 * Fora desta tela e por isso fora daqui: `city` (2..80), `customPlayStyle` (2..40) e
 * `defaultVenue.court` (1..80) não têm campo no formulário e nunca são preenchidos.
 *
 * Mínimo é erro porque o usuário precisa saber o que corrigir; máximo é teto de
 * digitação, aplicado em `GroupSetupViewModel`, porque deixar digitar para reprovar
 * depois é pior e não precisa de mensagem nenhuma. Caractere de controle segue a mesma
 * política do teto: sai na entrada, não vira erro.
 */
internal object GroupTextLimits {
    const val NameMin = 2
    const val NameMax = 80
    const val DescriptionMin = 2
    const val DescriptionMax = 500
    const val CustomLevelMin = 2
    const val CustomLevelMax = 40
    const val VenueNameMin = 2
    const val VenueNameMax = 120
    const val VenueAddressMin = 5
    const val VenueAddressMax = 300
}

/**
 * O backend conta `value.codePointCount(0, value.length)`; `String.length` do Kotlin
 * conta unidades UTF-16. "🏐" tem `length` 2 e **um** code point, então um nome de um
 * emoji só passaria no mínimo daqui e voltaria recusado de lá.
 */
internal fun String.codePointLength(): Int = count { !it.isLowSurrogate() }

/**
 * Corta pelo mesmo critério, e nunca no meio de um par substituto — `take(n)` cru
 * partiria o emoji em duas metades inválidas.
 */
internal fun String.takeCodePoints(max: Int): String {
    var index = 0
    var counted = 0
    while (index < length && counted < max) {
        val pair = this[index].isHighSurrogate() &&
            index + 1 < length &&
            this[index + 1].isLowSurrogate()
        index += if (pair) 2 else 1
        counted++
    }
    return if (index >= length) this else substring(0, index)
}

/**
 * A normalização de entrada do backend (`blankToNull` + `trim`, e nenhum caractere de
 * controle). O controle é **removido na digitação** em vez de virar erro: ninguém digita
 * uma quebra de linha de propósito num campo de uma linha, e limpar na hora é melhor do
 * que recusar depois do envio.
 */
internal fun String.withoutControlChars(): String = filterNot(Char::isISOControl)

/**
 * Função pura, fora da ViewModel (AGENTS.md §4). Valida sobre o `cleaned()` para que
 * um nome de espaços em branco conte como vazio, exatamente como o comando contaria.
 */
fun validate(state: GroupSetupState): Set<GroupSetupError> {
    val form = state.form.cleaned()
    val venue = form.defaultVenue
    return buildSet {
        if (form.name.codePointLength() < GroupTextLimits.NameMin) add(GroupSetupError.NameRequired)
        if (form.modality == null) add(GroupSetupError.ModalityRequired)
        if (form.composition == null) add(GroupSetupError.CompositionRequired)
        // `cleaned()` já fez o `blankToNull` + `trim` do backend: descrição em branco é
        // nula e não é erro — o campo é opcional. Curta demais, é.
        val description = form.description
        if (description != null && description.codePointLength() < GroupTextLimits.DescriptionMin) {
            add(GroupSetupError.DescriptionTooShort)
        }
        val customLevel = form.customLevel
        val customLevelTooShort = customLevel != null &&
            customLevel.codePointLength() < GroupTextLimits.CustomLevelMin
        if ((form.level == GroupLevel.CUSTOM && customLevel == null) || customLevelTooShort) {
            add(GroupSetupError.CustomLevelRequired)
        }
        // `defaultCapacity` é nulo-aceito no DTO: ausência não é erro (a tela mostra o
        // padrão), fora da faixa é. O intervalo é o `validateRange(defaultCapacity, 2, 100)`
        // do backend — o teto entra aqui porque estado carregado de fora não passa pelo
        // stepper, que é quem impede a tela de produzir mais de 100.
        form.defaultCapacity?.let {
            if (it !in GroupSetupDefaults.MinCapacity..GroupSetupDefaults.MaxCapacity) {
                add(GroupSetupError.CapacityOutOfRange)
            }
        }
        if (state.recurring && form.regularSlots.isEmpty()) add(GroupSetupError.SlotsRequired)
        // A quadra inteira é opcional, mas pela metade não existe: o
        // `GroupProfileDefaultsValidator.validateVenue` do backend exige nome **e**
        // endereço sempre que `defaultVenue` vem preenchido. A ViewModel devolve a
        // quadra a `null` quando os dois campos ficam vazios, então aqui só sobra o
        // preenchimento parcial.
        if (venue != null && venue.name.codePointLength() < GroupTextLimits.VenueNameMin) {
            add(GroupSetupError.VenueNameRequired)
        }
        // ponytail: sem geocodificação, "não encontramos esse endereço" é a ausência do
        // endereço de uma quadra que já começou a ser preenchida. A checagem real nasce
        // junto do gateway de local, que é quem sabe resolver a rua.
        if (venue != null && venue.address.codePointLength() < GroupTextLimits.VenueAddressMin) {
            add(GroupSetupError.VenueAddressNotFound)
        }
    }
}
