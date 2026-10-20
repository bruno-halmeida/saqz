package br.com.saqz.groups.presentation.duration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomDurationTest {
    @Test
    fun `whole hours from 3 to 8 become 180 to 480 minutes`() {
        (3..8).forEach { hours ->
            val draft = CustomDuration.type(hours.toString())
            assertEquals(hours * 60, draft.minutes)
            assertFalse(CustomDuration.isInvalid(draft))
        }
        assertEquals(480, CustomDuration.type("8").minutes)
    }

    @Test
    fun `empty out of range comma and dot are invalid`() {
        listOf("", "1", "2", "9", "10", "24", "3,5", "3.5", ",", ".").forEach { text ->
            assertTrue(CustomDuration.isInvalid(CustomDuration.type(text)), "\"$text\" deveria dar erro")
        }
    }

    @Test
    fun `letters are dropped and the text is limited`() {
        assertEquals("3", CustomDuration.type("3h").text)
        assertEquals("12", CustomDuration.type("1a2").text)
        assertEquals(4, CustomDuration.type("123456").text?.length)
    }

    @Test
    fun `typing 1 or 2 does not select a chip`() {
        assertTrue(CustomDuration.isOther(CustomDuration.type("1")))
        assertTrue(CustomDuration.isOther(CustomDuration.type("2")))
        assertEquals(0, CustomDuration.type("2").minutes)
    }

    @Test
    fun `chips are valid and are not other`() {
        CustomDuration.Presets.forEach {
            val draft = CustomDuration.selectPreset(it)
            assertFalse(CustomDuration.isOther(draft))
            assertFalse(CustomDuration.isInvalid(draft))
        }
    }

    @Test
    fun `saved value off the hour opens as other with the field empty`() {
        val draft = DurationDraft(195)
        assertTrue(CustomDuration.isOther(draft))
        assertFalse(CustomDuration.isInvalid(draft))
        assertEquals("", CustomDuration.fieldText(draft))
        assertEquals("3", CustomDuration.fieldText(DurationDraft(180)))
    }

    @Test
    fun `saved custom value outside 150 to 480 is invalid`() {
        assertTrue(CustomDuration.isInvalid(DurationDraft(45)))
        assertTrue(CustomDuration.isInvalid(DurationDraft(540)))
        assertFalse(CustomDuration.isInvalid(DurationDraft(480)))
    }

    @Test
    fun `tapping other from a chip starts empty and from a custom value keeps it`() {
        assertEquals(DurationDraft(0, ""), CustomDuration.startOther(DurationDraft(90)))
        assertEquals(DurationDraft(195), CustomDuration.startOther(DurationDraft(195)))
        assertNull(CustomDuration.selectPreset(60).text)
    }
}
