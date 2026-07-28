package br.com.saqz.groups.presentation.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupWeekday
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupsComponentsTest {
    @Test
    fun slotPickerForwardsDayAndTimePicks() = runComposeUiTest {
        var pickedDay: GroupWeekday? = null
        var pickedTime: Pair<Int, Int>? = null
        setContent {
            SaqzTheme {
                GroupSlotPicker(
                    draft = SlotDraft(GroupWeekday.TUESDAY, hour = 19, minute = 30),
                    onDayPick = { pickedDay = it },
                    onTimePick = { hour, minute -> pickedTime = hour to minute },
                )
            }
        }

        onNodeWithText("Qui").performClick()
        onNodeWithText("20h00").performClick()

        assertEquals(GroupWeekday.THURSDAY, pickedDay)
        assertEquals(20 to 0, pickedTime)
    }

    @Test
    fun recurrenceOnlyOffersSlotsWhenEnabled() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GroupRecurrenceSection(
                    recurring = false,
                    slots = emptyList(),
                    onRecurringChange = {},
                    onAddSlot = {},
                    onRemoveSlot = {},
                )
            }
        }

        onNodeWithText("Sem recorrência: você marca cada jogo manualmente, quando quiser.")
            .assertIsDisplayed()
        onAllNodesWithText("Adicionar dia e horário").assertCountEquals(0)
    }

    @Test
    fun slotPillForwardsRemoval() = runComposeUiTest {
        var removals = 0
        setContent {
            SaqzTheme {
                GroupSlotPill(
                    slot = GroupRegularSlotForm(
                        weekday = GroupWeekday.TUESDAY,
                        startTime = "19:30",
                        durationMinutes = 120,
                    ),
                    onRemove = { removals += 1 },
                )
            }
        }

        onNodeWithContentDescription("Remover dia e horário").performClick()
        assertEquals(1, removals)
    }

    @Test
    fun choiceRowAndVenueForwardTheirActions() = runComposeUiTest {
        var selected: Int? = null
        var venueActions = 0
        setContent {
            SaqzTheme {
                androidx.compose.foundation.layout.Column {
                    GroupChoiceChipRow(
                        values = listOf(60, 90),
                        selectedValue = 60,
                        label = { "${it}min" },
                        onSelect = { selected = it },
                    )
                    GroupVenueRow(
                        name = "CERET — Quadra 2",
                        address = "Tatuapé",
                        actionLabel = "Ver no mapa",
                        onAction = { venueActions += 1 },
                    )
                }
            }
        }

        onNodeWithText("90min").performClick()
        onNodeWithText("Ver no mapa").performClick()
        assertEquals(90, selected)
        assertEquals(1, venueActions)
    }
}
