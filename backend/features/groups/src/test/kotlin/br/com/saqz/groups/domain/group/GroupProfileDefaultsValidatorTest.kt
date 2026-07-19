package br.com.saqz.groups.domain.group

import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class GroupProfileDefaultsValidatorTest {
    @Test
    fun `modality enum has exact supported values`() {
        assertEquals(setOf("COURT_VOLLEYBALL", "BEACH_VOLLEYBALL", "FOOTVOLLEY"), GroupModality.entries.map { it.name }.toSet())
    }

    @Test
    fun `composition enum has exact supported values`() {
        assertEquals(setOf("WOMEN", "MEN", "MIXED"), GroupComposition.entries.map { it.name }.toSet())
    }

    @Test
    fun `level enum has exact supported values`() {
        assertEquals(setOf("BEGINNER", "INTERMEDIATE", "ADVANCED", "MIXED_LEVELS", "CUSTOM"), GroupLevel.entries.map { it.name }.toSet())
    }

    @Test
    fun `court play style enum has exact supported values`() {
        assertEquals(setOf("SIX_ZERO", "FOUR_TWO", "FIVE_ONE", "CUSTOM"), CourtPlayStyle.entries.map { it.name }.toSet())
    }

    @Test
    fun `valid complete profile trims user text and accepts all defaults`() {
        val valid = valid(input(
            name = "  Saturday Volley  ",
            description = "  Competitive games  ",
            city = "  São Paulo  ",
            level = GroupLevel.CUSTOM,
            customLevel = "  Open Plus  ",
            playStyle = CourtPlayStyle.CUSTOM,
            customPlayStyle = "  Fast 5-1  ",
            defaultVenue = GroupVenueInput("  Arena Beach  ", "  Rua Teste 123  ", "  Quadra 2  "),
            regularSlots = listOf(RegularSlotInput(DayOfWeek.MONDAY, LocalTime.of(19, 30), 120)),
            defaultCapacity = 18,
            defaultConfirmationLeadMinutes = 1440,
            defaultGameFeeCents = 2500,
            monthlyFeeCents = 7500,
            monthlyDueDay = 10,
        ))

        assertEquals("Saturday Volley", valid.name)
        assertEquals("Competitive games", valid.description)
        assertEquals("São Paulo", valid.city)
        assertEquals("Open Plus", valid.customLevel)
        assertEquals("Fast 5-1", valid.customPlayStyle)
        assertEquals("Arena Beach", valid.defaultVenue!!.name)
        assertEquals(1, valid.regularSlots.size)
    }

    @Test
    fun `missing required profile fields report stable field errors together`() {
        assertFields(
            input(name = null, modality = null, composition = null),
            "name",
            "modality",
            "composition",
        )
    }

    @Test
    fun `name rejects too short too long and control characters`() {
        assertFields(input(name = "A"), "name")
        assertFields(input(name = "A".repeat(81)), "name")
        assertFields(input(name = "line\nbreak"), "name")
    }

    @Test
    fun `optional description blank becomes null`() {
        assertNull(valid(input(description = "   ")).description)
    }

    @Test
    fun `optional description enforces range and control rejection when present`() {
        assertFields(input(description = "A"), "description")
        assertFields(input(description = "A".repeat(501)), "description")
        assertFields(input(description = "line\nbreak"), "description")
    }

    @Test
    fun `optional city blank becomes null`() {
        assertNull(valid(input(city = "   ")).city)
    }

    @Test
    fun `optional city enforces range and control rejection when present`() {
        assertFields(input(city = "A"), "city")
        assertFields(input(city = "A".repeat(81)), "city")
        assertFields(input(city = "line\nbreak"), "city")
    }

    @Test
    fun `custom level is required only for custom level`() {
        assertFields(input(level = GroupLevel.CUSTOM), "customLevel")
        assertEquals("Elite", valid(input(level = GroupLevel.CUSTOM, customLevel = " Elite ")).customLevel)
    }

    @Test
    fun `preset level rejects obsolete custom level text`() {
        assertFields(input(level = GroupLevel.ADVANCED, customLevel = "Elite"), "customLevel")
    }

    @Test
    fun `cleaned clears custom level when level is not custom`() {
        assertNull(GroupProfileDefaultsValidator.cleaned(input(level = GroupLevel.ADVANCED, customLevel = "Elite")).customLevel)
    }

    @Test
    fun `non court modality rejects play style payloads`() {
        assertFields(
            input(
                modality = GroupModality.BEACH_VOLLEYBALL,
                playStyle = CourtPlayStyle.FIVE_ONE,
                customPlayStyle = "Fast",
            ),
            "playStyle",
            "customPlayStyle",
        )
    }

    @Test
    fun `cleaned clears play style fields when modality is not court`() {
        val cleaned = GroupProfileDefaultsValidator.cleaned(
            input(modality = GroupModality.FOOTVOLLEY, playStyle = CourtPlayStyle.FIVE_ONE, customPlayStyle = "Fast"),
        )

        assertNull(cleaned.playStyle)
        assertNull(cleaned.customPlayStyle)
    }

    @Test
    fun `custom play style requires custom text`() {
        assertFields(input(playStyle = CourtPlayStyle.CUSTOM), "customPlayStyle")
        assertEquals("Fast", valid(input(playStyle = CourtPlayStyle.CUSTOM, customPlayStyle = " Fast ")).customPlayStyle)
    }

    @Test
    fun `preset play style rejects obsolete custom text`() {
        assertFields(input(playStyle = CourtPlayStyle.FIVE_ONE, customPlayStyle = "Fast"), "customPlayStyle")
    }

    @Test
    fun `cleaned clears custom play style when play style is preset`() {
        assertNull(GroupProfileDefaultsValidator.cleaned(input(playStyle = CourtPlayStyle.FIVE_ONE, customPlayStyle = "Fast")).customPlayStyle)
    }

    @Test
    fun `default venue reports name and address errors together`() {
        assertFields(
            input(defaultVenue = GroupVenueInput(name = "A", address = "Rua")),
            "defaultVenue.name",
            "defaultVenue.address",
        )
    }

    @Test
    fun `default venue court blank becomes null and present court is trimmed`() {
        assertNull(valid(input(defaultVenue = GroupVenueInput("Arena", "Rua Teste 123", "   "))).defaultVenue!!.court)
        assertEquals("Quadra 2", valid(input(defaultVenue = GroupVenueInput("Arena", "Rua Teste 123", " Quadra 2 "))).defaultVenue!!.court)
    }

    @Test
    fun `regular slot reports missing fields and invalid duration together`() {
        assertFields(
            input(regularSlots = listOf(RegularSlotInput(null, null, 10))),
            "regularSlots[0].weekday",
            "regularSlots[0].startTime",
            "regularSlots[0].durationMinutes",
        )
    }

    @Test
    fun `regular slot accepts monday through sunday and duration boundaries`() {
        val slots = valid(input(regularSlots = DayOfWeek.entries.map { RegularSlotInput(it, LocalTime.NOON, 15) })).regularSlots
        assertEquals(DayOfWeek.entries.toList(), slots.map { it.weekday })
        assertEquals(480, valid(input(regularSlots = listOf(RegularSlotInput(DayOfWeek.SUNDAY, LocalTime.MIDNIGHT, 480)))).regularSlots.single().durationMinutes)
    }

    @Test
    fun `default capacity enforces available spot limits`() {
        assertFields(input(defaultCapacity = 1), "defaultCapacity")
        assertFields(input(defaultCapacity = 101), "defaultCapacity")
        assertEquals(2, valid(input(defaultCapacity = 2)).defaultCapacity)
        assertEquals(100, valid(input(defaultCapacity = 100)).defaultCapacity)
    }

    @Test
    fun `confirmation lead accepts zero through one week`() {
        assertFields(input(defaultConfirmationLeadMinutes = -1), "defaultConfirmationLeadMinutes")
        assertFields(input(defaultConfirmationLeadMinutes = 10081), "defaultConfirmationLeadMinutes")
        assertEquals(0, valid(input(defaultConfirmationLeadMinutes = 0)).defaultConfirmationLeadMinutes)
        assertEquals(10080, valid(input(defaultConfirmationLeadMinutes = 10080)).defaultConfirmationLeadMinutes)
    }

    @Test
    fun `per game fee accepts only positive brl cents up to limit`() {
        assertFields(input(defaultGameFeeCents = 0), "defaultGameFeeCents")
        assertFields(input(defaultGameFeeCents = 100000000), "defaultGameFeeCents")
        assertEquals(1, valid(input(defaultGameFeeCents = 1)).defaultGameFeeCents)
        assertEquals(99999999, valid(input(defaultGameFeeCents = 99999999)).defaultGameFeeCents)
    }

    @Test
    fun `monthly fee accepts only positive brl cents up to limit`() {
        assertFields(input(monthlyFeeCents = 0, monthlyDueDay = 10), "monthlyFeeCents")
        assertFields(input(monthlyFeeCents = 100000000, monthlyDueDay = 10), "monthlyFeeCents")
        assertEquals(1, valid(input(monthlyFeeCents = 1, monthlyDueDay = 1)).monthlyFeeCents)
        assertEquals(99999999, valid(input(monthlyFeeCents = 99999999, monthlyDueDay = 28)).monthlyFeeCents)
    }

    @Test
    fun `monthly fee requires due day and due day requires monthly fee`() {
        assertFields(input(monthlyFeeCents = 5000, monthlyDueDay = null), "monthlyDueDay")
        assertFields(input(monthlyFeeCents = null, monthlyDueDay = 10), "monthlyDueDay")
    }

    @Test
    fun `monthly due day accepts only days one through twenty eight`() {
        assertFields(input(monthlyFeeCents = 5000, monthlyDueDay = 0), "monthlyDueDay")
        assertFields(input(monthlyFeeCents = 5000, monthlyDueDay = 29), "monthlyDueDay")
        assertEquals(1, valid(input(monthlyFeeCents = 5000, monthlyDueDay = 1)).monthlyDueDay)
        assertEquals(28, valid(input(monthlyFeeCents = 5000, monthlyDueDay = 28)).monthlyDueDay)
    }

    @Test
    fun `multiple conditional errors are returned before any transaction can start`() {
        assertFields(
            input(
                name = "",
                modality = GroupModality.BEACH_VOLLEYBALL,
                composition = null,
                level = GroupLevel.CUSTOM,
                playStyle = CourtPlayStyle.CUSTOM,
                defaultVenue = GroupVenueInput(null, null),
                regularSlots = listOf(RegularSlotInput(null, null, null)),
                monthlyFeeCents = 5000,
            ),
            "name",
            "composition",
            "customLevel",
            "playStyle",
            "customPlayStyle",
            "defaultVenue.name",
            "defaultVenue.address",
            "regularSlots[0].weekday",
            "regularSlots[0].startTime",
            "regularSlots[0].durationMinutes",
            "monthlyDueDay",
        )
    }

    private fun input(
        name: String? = "Training Club",
        modality: GroupModality? = GroupModality.COURT_VOLLEYBALL,
        composition: GroupComposition? = GroupComposition.MIXED,
        description: String? = null,
        city: String? = null,
        level: GroupLevel? = null,
        customLevel: String? = null,
        playStyle: CourtPlayStyle? = null,
        customPlayStyle: String? = null,
        defaultVenue: GroupVenueInput? = null,
        regularSlots: List<RegularSlotInput> = emptyList(),
        defaultCapacity: Int? = null,
        defaultConfirmationLeadMinutes: Int? = null,
        defaultGameFeeCents: Long? = null,
        monthlyFeeCents: Long? = null,
        monthlyDueDay: Int? = null,
    ) = GroupProfileDefaultsInput(
        name = name,
        modality = modality,
        composition = composition,
        description = description,
        city = city,
        level = level,
        customLevel = customLevel,
        playStyle = playStyle,
        customPlayStyle = customPlayStyle,
        defaultVenue = defaultVenue,
        regularSlots = regularSlots,
        defaultCapacity = defaultCapacity,
        defaultConfirmationLeadMinutes = defaultConfirmationLeadMinutes,
        defaultGameFeeCents = defaultGameFeeCents,
        monthlyFeeCents = monthlyFeeCents,
        monthlyDueDay = monthlyDueDay,
    )

    private fun valid(input: GroupProfileDefaultsInput): ValidGroupProfileDefaults =
        assertIs<GroupProfileDefaultsValidation.Valid>(GroupProfileDefaultsValidator.validate(input)).value

    private fun assertFields(input: GroupProfileDefaultsInput, vararg fields: String) {
        val invalid = assertIs<GroupProfileDefaultsValidation.Invalid>(GroupProfileDefaultsValidator.validate(input))
        assertEquals(fields.toSet(), invalid.errors.map { it.field }.toSet())
    }
}
