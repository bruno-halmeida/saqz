package br.com.saqz.groups.presentation.setup

import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupVenueForm
import br.com.saqz.groups.model.GroupWeekday
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupSetupValidatorTest {

    @Test
    fun `formulario completo nao acusa erro`() {
        assertEquals(emptySet(), validate(state()))
    }

    @Test
    fun `nome so de espacos conta como vazio`() {
        val errors = validate(state { copy(name = "   ") })

        assertEquals(setOf(GroupSetupError.NameRequired), errors)
    }

    // Os dois campos que `KtorGroupGateway.toRequest` abre com `requireNotNull`: sem
    // erro aqui, dava para confirmar na revisão um comando que estoura em `data`.
    @Test
    fun `modalidade ausente acusa erro`() {
        val errors = validate(state { copy(modality = null) })

        assertEquals(setOf(GroupSetupError.ModalityRequired), errors)
    }

    @Test
    fun `publico ausente acusa erro`() {
        val errors = validate(state { copy(composition = null) })

        assertEquals(setOf(GroupSetupError.CompositionRequired), errors)
    }

    // Fronteiras dos mínimos do `GroupProfileDefaultsValidator`: um caractere abaixo
    // reprova, o mínimo exato passa. Máximo não é erro, é teto de digitação — o corte
    // está no `GroupSetupViewModelTest`.
    @Test
    fun `nome de grupo abaixo do minimo do backend reprova`() {
        assertEquals(setOf(GroupSetupError.NameRequired), validate(state { copy(name = "A") }))
        assertTrue(validate(state { copy(name = "AB") }).isEmpty())
    }

    @Test
    fun `categoria personalizada abaixo do minimo reprova`() {
        val short = state { copy(level = GroupLevel.CUSTOM, customLevel = "R") }
        val exact = state { copy(level = GroupLevel.CUSTOM, customLevel = "Re") }

        assertEquals(setOf(GroupSetupError.CustomLevelRequired), validate(short))
        assertTrue(validate(exact).isEmpty())
    }

    @Test
    fun `nome e endereco da quadra abaixo do minimo reprovam`() {
        val shortName = state { copy(defaultVenue = GroupVenueForm(name = "C", address = "R. Canuto")) }
        val shortAddress = state { copy(defaultVenue = GroupVenueForm(name = "CERET", address = "R. C")) }
        val exact = state { copy(defaultVenue = GroupVenueForm(name = "CE", address = "R. Ca")) }

        assertEquals(setOf(GroupSetupError.VenueNameRequired), validate(shortName))
        assertEquals(setOf(GroupSetupError.VenueAddressNotFound), validate(shortAddress))
        assertTrue(validate(exact).isEmpty())
    }

    /** O backend conta code point: um emoji é um caractere, não dois. */
    @Test
    fun `nome de um emoji so reprova como um caractere`() {
        assertEquals(setOf(GroupSetupError.NameRequired), validate(state { copy(name = "🏐") }))
        assertTrue(validate(state { copy(name = "🏐🏐") }).isEmpty())
    }

    @Test
    fun `descricao abaixo do minimo reprova e ausente passa`() {
        assertEquals(
            setOf(GroupSetupError.DescriptionTooShort),
            validate(state { copy(description = "A") }),
        )
        assertEquals(
            setOf(GroupSetupError.DescriptionTooShort),
            validate(state { copy(description = "🏐") }),
        )
        assertTrue(validate(state { copy(description = "Ab") }).isEmpty())
        // Opcional: em branco vira nulo no `cleaned()` e não é erro nenhum.
        assertTrue(validate(state { copy(description = "   ") }).isEmpty())
        assertTrue(validate(state { copy(description = null) }).isEmpty())
    }

    @Test
    fun `categoria personalizada exige nome proprio`() {
        val errors = validate(state { copy(level = GroupLevel.CUSTOM, customLevel = null) })

        assertEquals(setOf(GroupSetupError.CustomLevelRequired), errors)
    }

    @Test
    fun `categoria personalizada preenchida passa`() {
        val errors = validate(state { copy(level = GroupLevel.CUSTOM, customLevel = "Recreativo") })

        assertTrue(errors.isEmpty())
    }

    /** `validateRange(defaultCapacity, 2, 100)` do backend, nas duas fronteiras. */
    @Test
    fun `capacidade fora da faixa do backend acusa erro`() {
        assertEquals(
            setOf(GroupSetupError.CapacityOutOfRange),
            validate(state { copy(defaultCapacity = GroupSetupDefaults.MinCapacity - 1) }),
        )
        assertEquals(
            setOf(GroupSetupError.CapacityOutOfRange),
            validate(state { copy(defaultCapacity = GroupSetupDefaults.MaxCapacity + 1) }),
        )
        assertTrue(validate(state { copy(defaultCapacity = GroupSetupDefaults.MinCapacity) }).isEmpty())
        assertTrue(validate(state { copy(defaultCapacity = GroupSetupDefaults.MaxCapacity) }).isEmpty())
    }

    /**
     * As opções de antecedência (3h, 6h, 12h, 24h) e de duração (1h a 2h30) cabem nas
     * faixas do backend — `0..10080` e `15..480` —, então não há erro a validar: a tela
     * não tem como produzir valor fora delas.
     */
    @Test
    fun `as opcoes de antecedencia e duracao cabem nas faixas do backend`() {
        assertTrue(GroupSetupDefaults.ConfirmationLeadOptions.all { it in 0..10_080 })
        assertTrue(GroupSetupDefaults.DurationOptions.all { it in 15..480 })
    }

    /** O DTO aceita capacidade nula; quem não escolheu não errou, sai o padrão da tela. */
    @Test
    fun `capacidade ausente nao acusa erro`() {
        assertTrue(validate(state { copy(defaultCapacity = null) }).isEmpty())
    }

    @Test
    fun `recorrente sem horario e o 2g`() {
        val errors = validate(state(recurring = true) { copy(regularSlots = emptyList()) })

        assertEquals(setOf(GroupSetupError.SlotsRequired), errors)
    }

    @Test
    fun `sem recorrencia a lista vazia de horarios e valida`() {
        val errors = validate(state(recurring = false) { copy(regularSlots = emptyList()) })

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `quadra com nome e sem endereco acusa endereco`() {
        val errors = validate(
            state { copy(defaultVenue = GroupVenueForm(name = "CERET — Quadra 2", address = " ")) },
        )

        assertEquals(setOf(GroupSetupError.VenueAddressNotFound), errors)
    }

    @Test
    fun `quadra com endereco e sem nome acusa nome`() {
        val errors = validate(
            state { copy(defaultVenue = GroupVenueForm(name = " ", address = "R. Canuto Abreu, s/n")) },
        )

        assertEquals(setOf(GroupSetupError.VenueNameRequired), errors)
    }

    @Test
    fun `sem quadra nenhuma nao ha erro de quadra`() {
        val errors = validate(state { copy(defaultVenue = null) })

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `formulario vazio acusa todos os campos exigidos`() {
        val errors = validate(
            GroupSetupState(
                mode = GroupSetupMode.Create,
                form = GroupSetupForm(
                    description = "A",
                    level = GroupLevel.CUSTOM,
                    // Quadra em branco só chega aqui vinda de fora: a ViewModel devolve
                    // a `null` o que o usuário esvazia. O validador tem de ser total.
                    defaultVenue = GroupVenueForm(name = "", address = ""),
                    defaultCapacity = 1,
                ),
                recurring = true,
            ),
        )

        assertEquals(GroupSetupError.entries.toSet(), errors)
    }

    private fun state(
        recurring: Boolean = true,
        form: GroupSetupForm.() -> GroupSetupForm = { this },
    ) = GroupSetupState(
        mode = GroupSetupMode.Create,
        form = validForm.form(),
        recurring = recurring,
    )

    private val validForm = GroupSetupForm(
        name = "Vôlei do CERET",
        modality = GroupModality.COURT_VOLLEYBALL,
        composition = GroupComposition.MIXED,
        level = GroupLevel.INTERMEDIATE,
        defaultVenue = GroupVenueForm(name = "CERET — Quadra 2", address = "R. Canuto Abreu, s/n"),
        regularSlots = listOf(
            GroupRegularSlotForm(weekday = GroupWeekday.TUESDAY, startTime = "19:30", durationMinutes = 120),
        ),
        defaultCapacity = 12,
        defaultConfirmationLeadMinutes = 360,
    )
}
