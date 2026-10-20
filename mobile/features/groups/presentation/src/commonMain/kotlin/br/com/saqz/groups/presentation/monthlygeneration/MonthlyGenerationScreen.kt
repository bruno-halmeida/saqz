package br.com.saqz.groups.presentation.monthlygeneration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.groups.presentation.ui.gameeditor.GameDateTimePicker
import br.com.saqz.groups.presentation.ui.gameeditor.PickerRow
import br.com.saqz.groups.presentation.ui.gameeditor.buildPickerDays
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzSwitch
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_cashbox_retry
import br.com.saqz.groups.resources.group_cashbox_load_failure_body
import br.com.saqz.groups.resources.monthly_generation_title
import br.com.saqz.groups.resources.monthly_generation_note
import br.com.saqz.groups.resources.monthly_generation_month
import br.com.saqz.groups.resources.monthly_generation_due
import br.com.saqz.groups.resources.monthly_generation_amount
import br.com.saqz.groups.resources.monthly_generation_members
import br.com.saqz.groups.resources.monthly_generation_empty
import br.com.saqz.groups.resources.monthly_generation_review
import br.com.saqz.groups.resources.monthly_generation_summary
import br.com.saqz.groups.resources.monthly_generation_confirm
import br.com.saqz.groups.resources.monthly_generation_edit
import br.com.saqz.groups.resources.monthly_generation_failed
import br.com.saqz.groups.resources.monthly_generation_validation
import br.com.saqz.groups.resources.monthly_generation_denied
import br.com.saqz.groups.resources.game_picker_month_january
import br.com.saqz.groups.resources.game_picker_month_february
import br.com.saqz.groups.resources.game_picker_month_march
import br.com.saqz.groups.resources.game_picker_month_april
import br.com.saqz.groups.resources.game_picker_month_may
import br.com.saqz.groups.resources.game_picker_month_june
import br.com.saqz.groups.resources.game_picker_month_july
import br.com.saqz.groups.resources.game_picker_month_august
import br.com.saqz.groups.resources.game_picker_month_september
import br.com.saqz.groups.resources.game_picker_month_october
import br.com.saqz.groups.resources.game_picker_month_november
import br.com.saqz.groups.resources.game_picker_month_december
import br.com.saqz.groups.resources.group_weekday_monday
import br.com.saqz.groups.resources.group_weekday_tuesday
import br.com.saqz.groups.resources.group_weekday_wednesday
import br.com.saqz.groups.resources.group_weekday_thursday
import br.com.saqz.groups.resources.group_weekday_friday
import br.com.saqz.groups.resources.group_weekday_saturday
import br.com.saqz.groups.resources.group_weekday_sunday
import br.com.saqz.groups.resources.monthly_generation_previous_month
import br.com.saqz.groups.resources.monthly_generation_next_month
import br.com.saqz.groups.resources.monthly_generation_due_save
import org.jetbrains.compose.resources.stringResource

internal object MonthlyGenerationTags {
    const val Screen = "monthly-generation"
    const val Month = "monthly-generation-month"
    const val PreviousMonth = "monthly-generation-previous-month"
    const val NextMonth = "monthly-generation-next-month"
    const val DuePicker = "monthly-generation-due-picker"
    const val DueSave = "monthly-generation-due-save"
    const val Due = "monthly-generation-due"
    const val Amount = "monthly-generation-amount"
    const val Review = "monthly-generation-review"
    const val Confirm = "monthly-generation-confirm"
    const val Edit = "monthly-generation-edit"
    const val Retry = "monthly-generation-retry"
    const val Summary = "monthly-generation-summary"
    fun member(id: String) = "monthly-generation-member-$id"
}

@Composable
internal fun MonthlyGenerationScreen(
    state: MonthlyGenerationState,
    onBack: () -> Unit,
    onIntent: (MonthlyGenerationIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A folha do vencimento fica fora da coluna rolável: dentro dela o overlay não tem altura para cobrir a tela.
    var duePickerOpen by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(SaqzTheme.colors.background).imePadding().testTag(MonthlyGenerationTags.Screen)) {
        SaqzTopAppBar(title = stringResource(Res.string.monthly_generation_title), onBack = onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(SaqzTheme.metrics.horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
        ) {
            when {
                state.isLoading -> SaqzSpinner()
                state.loadFailed -> {
                    Text(stringResource(Res.string.group_cashbox_load_failure_body), color = SaqzTheme.colors.textPrimary)
                    SaqzButton(
                        label = stringResource(Res.string.group_cashbox_retry),
                        onClick = { onIntent(MonthlyGenerationIntent.Retry) },
                        modifier = Modifier.testTag(MonthlyGenerationTags.Retry),
                    )
                }
                else -> {
                    Text(
                        stringResource(Res.string.monthly_generation_note),
                        style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary,
                    )
                    if (state.reviewing) {
                        MonthlyReview(state, onIntent)
                    } else {
                        MonthlyFormFields(state, onIntent, onOpenDuePicker = { duePickerOpen = true })
                    }
                }
            }
            state.error?.let { error ->
                Text(
                    stringResource(when (error) {
                        GroupUiError.Validation -> Res.string.monthly_generation_validation
                        GroupUiError.AccessDenied, GroupUiError.NotFound -> Res.string.monthly_generation_denied
                        else -> Res.string.monthly_generation_failed
                    }),
                    color = SaqzTheme.colors.errorForeground, style = SaqzTheme.typography.support,
                )
            }
        }
    }
    DueDatePickerSheet(
        open = duePickerOpen && !state.reviewing, month = state.form.month, dueDate = state.form.dueDate,
        onClose = { duePickerOpen = false },
        onSave = { onIntent(MonthlyGenerationIntent.DueDateChanged(it)); duePickerOpen = false },
    )
    }
}

@Composable
private fun MonthlyFormFields(
    state: MonthlyGenerationState,
    onIntent: (MonthlyGenerationIntent) -> Unit,
    onOpenDuePicker: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
        MonthSelector(state, onIntent)
        PickerRow(
            label = stringResource(Res.string.monthly_generation_due),
            value = state.form.dueDate,
            placeholder = "",
            errorText = null,
            tag = MonthlyGenerationTags.Due,
            onClick = onOpenDuePicker,
        )
        SaqzInput(
            value = state.form.amount, onValueChange = { onIntent(MonthlyGenerationIntent.AmountChanged(it)) },
            label = stringResource(Res.string.monthly_generation_amount), keyboardType = KeyboardType.Decimal,
            modifier = Modifier.testTag(MonthlyGenerationTags.Amount),
        )
        Text(stringResource(Res.string.monthly_generation_members), color = SaqzTheme.colors.textPrimary)
        if (state.members.isEmpty()) {
            Text(stringResource(Res.string.monthly_generation_empty), color = SaqzTheme.colors.textSecondary)
        }
        state.members.forEach { member ->
            SaqzSwitch(
                checked = member.id in state.form.selectedIds, label = member.name,
                onCheckedChange = { onIntent(MonthlyGenerationIntent.ToggleMember(member.id)) },
                modifier = Modifier.testTag(MonthlyGenerationTags.member(member.id)),
            )
        }
        SaqzButton(
            label = stringResource(Res.string.monthly_generation_review),
            onClick = { onIntent(MonthlyGenerationIntent.Review) },
            enabled = state.form.selectedIds.isNotEmpty(), fullWidth = true,
            modifier = Modifier.testTag(MonthlyGenerationTags.Review),
        )
    }
}

@Composable
private fun MonthSelector(state: MonthlyGenerationState, onIntent: (MonthlyGenerationIntent) -> Unit) {
    val colors = SaqzTheme.colors
    Column(Modifier.testTag(MonthlyGenerationTags.Month), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
        Text(stringResource(Res.string.monthly_generation_month), style = SaqzTheme.typography.caption, color = colors.textSecondary)
        Text(monthTitle(state.form.month, monthNames()), style = SaqzTheme.typography.subtitle, color = colors.textPrimary)
        Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            SaqzButton(
                label = stringResource(Res.string.monthly_generation_previous_month),
                onClick = { onIntent(MonthlyGenerationIntent.PreviousMonth) },
                variant = SaqzButtonVariant.Secondary, enabled = state.canGoToPreviousMonth,
                modifier = Modifier.weight(1f).testTag(MonthlyGenerationTags.PreviousMonth),
            )
            SaqzButton(
                label = stringResource(Res.string.monthly_generation_next_month),
                onClick = { onIntent(MonthlyGenerationIntent.NextMonth) },
                variant = SaqzButtonVariant.Secondary, enabled = state.canGoToNextMonth,
                modifier = Modifier.weight(1f).testTag(MonthlyGenerationTags.NextMonth),
            )
        }
    }
}

/** Seletor do vencimento: só os dias do mês escolhido, com o seletor de datas por toque do editor de jogo. */
@Composable
private fun DueDatePickerSheet(open: Boolean, month: String, dueDate: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    val weekdays = listOf(
        Res.string.group_weekday_monday, Res.string.group_weekday_tuesday, Res.string.group_weekday_wednesday,
        Res.string.group_weekday_thursday, Res.string.group_weekday_friday, Res.string.group_weekday_saturday,
        Res.string.group_weekday_sunday,
    ).map { stringResource(it) }
    val monthNames = monthNames()
    val first = remember(month) { runCatching { LocalDate.parse("$month-01") }.getOrNull() }
    val days = remember(first, weekdays, monthNames) {
        first?.let {
            val daysInMonth = it.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).day
            buildPickerDays(it, daysInMonth, weekdays, monthNames)
        }.orEmpty()
    }
    val selectedIso = dueDate.split('/').reversed().joinToString("-")
    val wheel = remember(days, selectedIso) { GameDateTimePicker.state(days, selectedIso, 0, 0) }
    var dayIndex by remember(open, selectedIso) { mutableStateOf(wheel.selectedDayIndex) }
    SaqzBottomSheet(
        open = open && days.isNotEmpty(),
        onClose = onClose,
        title = stringResource(Res.string.monthly_generation_due),
        modifier = Modifier.testTag(MonthlyGenerationTags.DuePicker),
        footer = {
            SaqzButton(
                label = stringResource(Res.string.monthly_generation_due_save),
                onClick = {
                    days.getOrNull(dayIndex)?.isoDate?.let { onSave(it.split('-').reversed().joinToString("/")) }
                },
                fullWidth = true,
                modifier = Modifier.testTag(MonthlyGenerationTags.DueSave),
            )
        },
    ) {
        Text(days.getOrNull(dayIndex)?.label.orEmpty(), style = SaqzTheme.typography.subtitle, color = SaqzTheme.colors.textPrimary)
        GameDateTimePicker(
            state = wheel.copy(selectedDayIndex = dayIndex), monthLabels = monthNames, onDayChange = { dayIndex = it },
            onHourChange = {}, onMinuteChange = {}, showTime = false,
        )
    }
}

@Composable
private fun monthNames(): List<String> = listOf(
    Res.string.game_picker_month_january, Res.string.game_picker_month_february, Res.string.game_picker_month_march,
    Res.string.game_picker_month_april, Res.string.game_picker_month_may, Res.string.game_picker_month_june,
    Res.string.game_picker_month_july, Res.string.game_picker_month_august, Res.string.game_picker_month_september,
    Res.string.game_picker_month_october, Res.string.game_picker_month_november, Res.string.game_picker_month_december,
).map { stringResource(it) }

/** "2026-10" vira "outubro de 2026". */
internal fun monthTitle(month: String, names: List<String>): String {
    val (year, number) = month.split('-').let { it.getOrNull(0) to it.getOrNull(1)?.toIntOrNull() }
    val name = number?.let { names.getOrNull(it - 1) } ?: return month
    return "$name de $year"
}

@Composable
private fun MonthlyReview(state: MonthlyGenerationState, onIntent: (MonthlyGenerationIntent) -> Unit) {
    Column(
        Modifier.testTag(MonthlyGenerationTags.Summary),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        Text(
            stringResource(
                Res.string.monthly_generation_summary, monthTitle(state.form.month, monthNames()), state.form.dueDate,
                formatBrl(state.form.command("")?.amountCents ?: 0), state.form.selectedIds.size,
            ),
            style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary,
        )
        state.members.filter { it.id in state.form.selectedIds }.forEach {
            Text(it.name, color = SaqzTheme.colors.textPrimary)
        }
        SaqzButton(
            label = stringResource(Res.string.monthly_generation_confirm),
            onClick = { onIntent(MonthlyGenerationIntent.Confirm) },
            enabled = !state.isSaving, loading = state.isSaving, fullWidth = true,
            modifier = Modifier.testTag(MonthlyGenerationTags.Confirm),
        )
        SaqzButton(
            label = stringResource(Res.string.monthly_generation_edit),
            onClick = { onIntent(MonthlyGenerationIntent.Edit) },
            variant = SaqzButtonVariant.Ghost, enabled = !state.isSaving, fullWidth = true,
            modifier = Modifier.testTag(MonthlyGenerationTags.Edit),
        )
    }
}

@Preview
@Composable
private fun MonthlyGenerationPreview() = SaqzTheme {
    MonthlyGenerationScreen(
        state = MonthlyGenerationState(
            isLoading = false,
            members = listOf(MonthlyMemberUi("ana", "Ana Souza"), MonthlyMemberUi("bia", "Bia Santos")),
            form = MonthlyForm("2026-08", "80,00", "12/08/2026"),
        ),
        onBack = {}, onIntent = {},
    )
}
