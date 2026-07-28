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

    @Test
    fun `capacidade abaixo de dois acusa erro`() {
        assertEquals(
            setOf(GroupSetupError.CapacityTooLow),
            validate(state { copy(defaultCapacity = 1) }),
        )
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
