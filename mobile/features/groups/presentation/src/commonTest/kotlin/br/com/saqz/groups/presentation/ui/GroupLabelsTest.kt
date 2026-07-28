package br.com.saqz.groups.presentation.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupWeekday
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GroupLabelsTest {
    @Test
    fun everyDomainEnumHasALocalizedLabel() = runComposeUiTest {
        setContent {
            Column {
                GroupModality.entries.forEach { Text(it.label()) }
                GroupComposition.entries.forEach { Text(it.label()) }
                GroupLevel.entries.forEach { Text(it.label()) }
                GroupPlayStyle.entries.forEach { Text(it.label()) }
                GroupWeekday.entries.forEach { Text(it.label()) }
                GroupWeekday.entries.forEach { Text(it.shortLabel()) }
            }
        }

        onNodeWithText("Vôlei de quadra").assertTextEquals("Vôlei de quadra")
        onNodeWithText("Feminino").assertTextEquals("Feminino")
        onNodeWithText("Níveis mistos").assertTextEquals("Níveis mistos")
        onNodeWithText("4-2").assertTextEquals("4-2")
        onNodeWithText("Segunda").assertTextEquals("Segunda")
        onNodeWithText("Sáb").assertTextEquals("Sáb")
    }

    @Test
    fun durationAndConfirmationLeadUseTheExportLabels() = runComposeUiTest {
        setContent {
            Column {
                listOf(60, 90, 120, 150).forEach { Text(durationLabel(it)) }
                listOf(180, 360, 720, 1_440).forEach { Text(confirmationLeadLabel(it)) }
            }
        }

        listOf("1h", "1h30", "2h", "2h30", "3h antes", "6h antes", "12h antes", "24h antes")
            .forEach { expected -> onNodeWithText(expected).assertTextEquals(expected) }
    }
}
