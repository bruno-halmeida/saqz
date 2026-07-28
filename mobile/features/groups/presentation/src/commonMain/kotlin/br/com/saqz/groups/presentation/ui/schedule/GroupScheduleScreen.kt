package br.com.saqz.groups.presentation.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.schedule.GroupScheduleIntent
import br.com.saqz.groups.presentation.schedule.GroupScheduleState
import br.com.saqz.groups.presentation.schedule.UpcomingGameStatus
import br.com.saqz.groups.presentation.schedule.UpcomingGameUi
import br.com.saqz.groups.presentation.ui.components.GroupRecurrenceSection
import br.com.saqz.groups.presentation.ui.components.GroupSlotPicker
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_schedule_save
import br.com.saqz.groups.resources.group_schedule_title
import br.com.saqz.groups.resources.group_slot_save
import br.com.saqz.groups.resources.group_slot_title
import org.jetbrains.compose.resources.stringResource

internal object GroupScheduleTags {
    const val Save = "schedule-save"
    const val Pause = "schedule-pause"
    const val SlotSheetSave = "schedule-slot-save"

    /** Prefixo — a tag da linha é `"$UpcomingGame:$gameId"`. */
    const val UpcomingGame = "schedule-upcoming"
}

@Composable
internal fun GroupScheduleScreen(
    state: GroupScheduleState,
    onIntent: (GroupScheduleIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
            SaqzTopAppBar(title = stringResource(Res.string.group_schedule_title), onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = metrics.horizontalPadding)
                    .padding(top = metrics.blockGap, bottom = metrics.blockGap + metrics.grid),
                verticalArrangement = Arrangement.spacedBy(GroupScheduleRowPadding),
            ) {
                if (state.isLoading) {
                    GroupScheduleLoading()
                } else {
                    SaqzCard {
                        GroupRecurrenceSection(
                            recurring = state.recurring,
                            slots = state.slots,
                            onRecurringChange = { onIntent(GroupScheduleIntent.ToggleRecurring(it)) },
                            onAddSlot = { onIntent(GroupScheduleIntent.AddSlot) },
                            onRemoveSlot = { onIntent(GroupScheduleIntent.RemoveSlot(it)) },
                        )
                    }
                    GroupScheduleTimingCard(
                        durationMinutes = state.durationMinutes,
                        confirmationLeadMinutes = state.confirmationLeadMinutes,
                        onSelectDuration = { onIntent(GroupScheduleIntent.SelectDuration(it)) },
                        onSelectConfirmationLead = {
                            onIntent(GroupScheduleIntent.SelectConfirmationLead(it))
                        },
                    )
                    GroupUpcomingGamesSection(
                        games = state.upcoming,
                        onOpenGame = { onIntent(GroupScheduleIntent.OpenGame(it)) },
                    )
                    GroupPauseScheduleCard(
                        isPaused = state.isPaused,
                        onToggle = { onIntent(GroupScheduleIntent.TogglePause) },
                    )
                }
            }
            SaqzDivider()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SaqzTheme.colors.background)
                    .padding(
                        horizontal = metrics.horizontalPadding,
                        vertical = GroupScheduleRowPadding,
                    ),
            ) {
                SaqzButton(
                    label = stringResource(Res.string.group_schedule_save),
                    onClick = { onIntent(GroupScheduleIntent.Save) },
                    fullWidth = true,
                    // Nada a salvar enquanto o skeleton está na tela; a ViewModel também
                    // recusa o intent, isto aqui só evita oferecer o toque morto.
                    enabled = !state.isLoading,
                    loading = state.isSaving,
                    modifier = Modifier.testTag(GroupScheduleTags.Save),
                )
            }
        }
        SaqzBottomSheet(
            open = state.slotSheet != null,
            onClose = { onIntent(GroupScheduleIntent.DismissSlotSheet) },
            title = stringResource(Res.string.group_slot_title),
            footer = {
                SaqzButton(
                    label = stringResource(Res.string.group_slot_save),
                    onClick = { onIntent(GroupScheduleIntent.ConfirmSlot) },
                    fullWidth = true,
                    modifier = Modifier.testTag(GroupScheduleTags.SlotSheetSave),
                )
            },
        ) {
            GroupSlotPicker(
                draft = state.slotDraft,
                onDayPick = { onIntent(GroupScheduleIntent.PickDraftDay(it)) },
                onTimePick = { hour, minute ->
                    onIntent(GroupScheduleIntent.PickDraftTime(hour, minute))
                },
            )
        }
    }
}

internal val previewScheduleState = GroupScheduleState(
    isLoading = false,
    slots = listOf(
        GroupRegularSlotForm(weekday = GroupWeekday.TUESDAY, startTime = "19:30", durationMinutes = 120),
        GroupRegularSlotForm(weekday = GroupWeekday.THURSDAY, startTime = "20:00", durationMinutes = 120),
    ),
    upcoming = listOf(
        UpcomingGameUi(
            id = "g1",
            day = "28",
            month = "JUL",
            label = "Terça · 19h30",
            venue = "CERET — Quadra 2",
            status = UpcomingGameStatus.Published,
        ),
        UpcomingGameUi(
            id = "g2",
            day = "30",
            month = "JUL",
            label = "Quinta · 20h00",
            venue = "CERET — Quadra 2",
            status = UpcomingGameStatus.Scheduled,
        ),
        UpcomingGameUi(
            id = "g3",
            day = "04",
            month = "AGO",
            label = "Terça · 19h30",
            venue = "CERET — Quadra 2",
            status = UpcomingGameStatus.Scheduled,
        ),
    ),
)

@Preview
@Composable
private fun GroupSchedulePreview() = SaqzTheme {
    GroupScheduleScreen(state = previewScheduleState, onIntent = {}, onBack = {})
}

@Preview
@Composable
private fun GroupScheduleWithoutRecurrencePreview() = SaqzTheme {
    GroupScheduleScreen(
        state = previewScheduleState.copy(recurring = false, slots = emptyList()),
        onIntent = {},
        onBack = {},
    )
}

@Preview
@Composable
private fun GroupScheduleWithoutGamesPreview() = SaqzTheme {
    GroupScheduleScreen(
        state = previewScheduleState.copy(upcoming = emptyList()),
        onIntent = {},
        onBack = {},
    )
}

@Preview
@Composable
private fun GroupSchedulePausedPreview() = SaqzTheme {
    GroupScheduleScreen(state = previewScheduleState.copy(isPaused = true), onIntent = {}, onBack = {})
}

@Preview
@Composable
private fun GroupScheduleLoadingPreview() = SaqzTheme {
    GroupScheduleScreen(state = GroupScheduleState(), onIntent = {}, onBack = {})
}
