package br.com.saqz.groups.presentation.ui.components

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.ui.durationLabel
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = RobolectricDeviceQualifiers.Pixel7,
    application = Application::class,
)
class GroupsComponentsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun slotPicker() = capture("group-slot-picker") {
        GroupSlotPicker(
            draft = SlotDraft(GroupWeekday.TUESDAY, hour = 19, minute = 30),
            onDayPick = {},
            onTimePick = { _, _ -> },
        )
    }

    @Test
    fun recurrenceSection() = capture("group-recurrence-section") {
        GroupRecurrenceSection(
            recurring = true,
            slots = listOf(sampleSlot),
            onRecurringChange = {},
            onAddSlot = {},
            onRemoveSlot = {},
        )
    }

    @Test
    fun slotPill() = capture("group-slot-pill") {
        GroupSlotPill(slot = sampleSlot, onRemove = {})
    }

    @Test
    fun addSlotButton() = capture("group-add-slot-button") {
        GroupAddSlotButton(onClick = {})
    }

    @Test
    fun choiceChipRow() = capture("group-choice-chip-row") {
        GroupChoiceChipRow(
            values = listOf(60, 90, 120, 150),
            selectedValue = 120,
            label = { durationLabel(it) },
            onSelect = {},
        )
    }

    @Test
    fun formCard() = capture("group-form-card") {
        GroupFormCard(
            title = "Duração do jogo",
            hint = "Quanto tempo cada partida costuma durar.",
        ) {
            GroupChoiceChipRow(
                values = listOf(60, 90, 120, 150),
                selectedValue = 120,
                label = { durationLabel(it) },
                onSelect = {},
            )
        }
    }

    @Test
    fun venueRow() = capture("group-venue-row") {
        SaqzCard {
            GroupVenueRow(
                name = "CERET — Quadra 2",
                address = "R. Canuto Abreu, s/n · Tatuapé",
                actionLabel = "Ver no mapa",
                onAction = {},
            )
        }
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            SaqzTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SaqzTheme.colors.background)
                        .padding(16.dp),
                ) {
                    content()
                }
            }
        }
        compose.onRoot().captureRoboImage("screenshots/vul-66/$name.png")
    }

    private val sampleSlot
        get() = GroupRegularSlotForm(
            weekday = GroupWeekday.TUESDAY,
            startTime = "19:30",
            durationMinutes = 120,
        )
}
