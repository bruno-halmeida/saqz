package br.com.saqz.groups.presentation.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HeightPickerTest {
    @Test
    fun `format shows meters with two decimals`() {
        assertEquals("1,75 m", HeightPicker.format(175))
        assertEquals("1,00 m", HeightPicker.format(100))
        assertEquals("2,05 m", HeightPicker.format(205))
        assertEquals("2,50 m", HeightPicker.format(250))
    }

    @Test
    fun `opening without a height starts near 1 70 m and keeps a valid one`() {
        assertEquals(170, HeightPicker.initial(null))
        assertEquals(190, HeightPicker.initial(190))
        assertEquals(170, HeightPicker.initial(80))
    }

    @Test
    fun `range goes from 1 00 m to 2 50 m in one centimeter steps`() {
        assertEquals((10..25).toList(), HeightPicker.tenths())
        assertEquals((0..9).toList(), HeightPicker.centimeters(24))
        assertEquals(listOf(0), HeightPicker.centimeters(25))
        // Trocar para 2,5 m com 9 cm escolhidos cai no limite, não passa dele.
        assertEquals(250, HeightPicker.withTenth(179, 25))
        assertEquals(165, HeightPicker.withTenth(175, 16))
        assertEquals(178, HeightPicker.withCentimeter(175, 8))
    }

    @Test
    fun `tapping 1 7 and 5 then saving returns 175`() = runComposeUiTest {
        val saved = mutableListOf<Int?>()
        setContent { SaqzTheme { HeightPickerSheet(open = true, heightCm = null, onClose = {}, onSave = { saved += it }) } }
        waitForIdle()

        onNodeWithTag(HeightPickerTags.tenth(17)).assertIsSelected()
        onNodeWithTag(HeightPickerTags.centimeter(0)).assertIsSelected()
        onNodeWithTag(HeightPickerTags.centimeter(5)).performScrollTo().performClick()
        onNodeWithTag(HeightPickerTags.Save).performClick()

        assertEquals(listOf<Int?>(175), saved)
    }

    @Test
    fun `picking another tenth and the clear button return the chosen value and null`() = runComposeUiTest {
        val saved = mutableListOf<Int?>()
        setContent { SaqzTheme { HeightPickerSheet(open = true, heightCm = 182, onClose = {}, onSave = { saved += it }) } }
        waitForIdle()

        onNodeWithTag(HeightPickerTags.tenth(19)).performScrollTo().performClick()
        onNodeWithTag(HeightPickerTags.Save).performClick()
        onNodeWithTag(HeightPickerTags.Clear).performClick()

        assertEquals(listOf<Int?>(192, null), saved)
    }

    @Test
    fun `chips speak their label and carry the selected state`() = runComposeUiTest {
        setContent { SaqzTheme { HeightPickerSheet(open = true, heightCm = 181, onClose = {}, onSave = {}) } }
        waitForIdle()

        onNodeWithContentDescription("1,8 metros").assertIsSelected()
        onNodeWithContentDescription("1,7 metros").assertIsNotSelected()
        onNodeWithContentDescription("1 centímetro").assertIsSelected()
        onNodeWithContentDescription("5 centímetros").assertIsNotSelected()
    }
}
