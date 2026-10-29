package br.com.saqz.groups.presentation.newentry

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzAmountInput
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzSegmented
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.new_entry_edit_conflict
import br.com.saqz.groups.resources.new_entry_save_edit
import br.com.saqz.groups.resources.new_entry_title_edit
import br.com.saqz.groups.resources.new_entry_amount
import br.com.saqz.groups.resources.new_entry_category
import br.com.saqz.groups.resources.new_entry_category_court
import br.com.saqz.groups.resources.new_entry_category_material
import br.com.saqz.groups.resources.new_entry_category_other
import br.com.saqz.groups.resources.new_entry_date
import br.com.saqz.groups.resources.new_entry_description
import br.com.saqz.groups.resources.new_entry_save_error
import br.com.saqz.groups.resources.new_entry_saving
import br.com.saqz.groups.resources.new_entry_validation
import br.com.saqz.groups.resources.*
import br.com.saqz.groups.presentation.ui.finance.formatBrlShort
import br.com.saqz.groups.presentation.ui.finance.weekdayDayMonth
import br.com.saqz.groups.presentation.ui.gameeditor.GameDateTimePicker
import br.com.saqz.groups.presentation.ui.gameeditor.PickerRow
import br.com.saqz.groups.presentation.ui.gameeditor.buildPickerDays
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.stringResource

internal object NewEntryTags {
    const val Screen = "finance-new-entry"
    const val Amount = "finance-new-entry-amount"
    const val Shortcuts = "finance-new-entry-shortcuts"
    const val Description = "finance-new-entry-description"
    const val Category = "finance-new-entry-category"
    const val Date = "finance-new-entry-date"
    const val DatePicker = "finance-new-entry-date-picker"
    const val DateSave = "finance-new-entry-date-save"
    const val Save = "finance-new-entry-save"
}

/**
 * Entrada e Saída (DEB-148): duas telas no lugar do "Novo lançamento". Valor primeiro e grande; a Saída tem os
 * atalhos de R$ 80, 120 e 160; categoria em segmentado de largura igual; data com hoje e o calendário no toque.
 * Em correção (DEB-056) só valor e descrição mudam.
 */
@Composable
internal fun NewEntryScreen(
    state: NewEntryState,
    onBack: () -> Unit,
    onIntent: (NewEntryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    val out = state.direction == NewEntryDirection.Out
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SaqzTheme.colors.background)
                .imePadding()
                .testTag(NewEntryTags.Screen),
        ) {
            SaqzTopAppBar(
                title = stringResource(
                    when {
                        state.editing -> Res.string.new_entry_title_edit
                        out -> Res.string.entry_out_title
                        else -> Res.string.entry_in_title
                    },
                ),
                onBack = onBack,
            )
            if (state.isLoadingEntry) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { SaqzSpinner() }
                return@Column
            }
            if (state.entryLoadFailed) {
                GroupLoadFailure(
                    error = state.error,
                    onRetry = { onIntent(NewEntryIntent.RetryLoadEdit) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                return@Column
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = metrics.horizontalPadding, vertical = metrics.grid),
                verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
                    SaqzAmountInput(
                        value = state.amountText,
                        onValueChange = { onIntent(NewEntryIntent.AmountChanged(it)) },
                        label = stringResource(Res.string.new_entry_amount),
                        modifier = Modifier.testTag(NewEntryTags.Amount),
                    )
                    if (out && !state.editing) {
                        SaqzSegmented(
                            options = ExpenseShortcuts.map(::formatBrlShort),
                            selected = ExpenseShortcuts.indexOfFirst { state.amountText == formatEntryCents(it) },
                            onSelect = { onIntent(NewEntryIntent.SelectAmountShortcut(ExpenseShortcuts[it])) },
                            modifier = Modifier.testTag(NewEntryTags.Shortcuts),
                        )
                    }
                }
                SaqzInput(
                    value = state.description,
                    onValueChange = { onIntent(NewEntryIntent.DescriptionChanged(it)) },
                    label = stringResource(Res.string.new_entry_description),
                    placeholder = stringResource(
                        if (out) Res.string.entry_description_out_hint else Res.string.entry_description_in_hint,
                    ),
                    modifier = Modifier.testTag(NewEntryTags.Description),
                )
                if (!state.editing) {
                    val categories = NewEntryCategory.forDirection(state.direction)
                    Column(verticalArrangement = Arrangement.spacedBy(metrics.grid)) {
                        FieldLabel(stringResource(Res.string.new_entry_category))
                        SaqzSegmented(
                            options = categories.map { it.label() },
                            selected = categories.indexOf(state.category),
                            onSelect = { onIntent(NewEntryIntent.SelectCategory(categories[it])) },
                            modifier = Modifier.testTag(NewEntryTags.Category),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(metrics.grid)) {
                        SaqzCard(padded = false) {
                            PickerRow(
                                label = stringResource(Res.string.new_entry_date),
                                value = if (state.date == state.today) {
                                    stringResource(Res.string.entry_date_today, weekdayDayMonth(state.date).lowercase())
                                } else {
                                    weekdayDayMonth(state.date)
                                },
                                placeholder = "",
                                errorText = null,
                                tag = NewEntryTags.Date,
                                onClick = { onIntent(NewEntryIntent.OpenDatePicker) },
                            )
                        }
                        Text(
                            text = stringResource(Res.string.entry_date_hint),
                            style = SaqzTheme.typography.support,
                            color = SaqzTheme.colors.textSecondary,
                        )
                    }
                }
                state.error?.let { error ->
                    Text(
                        text = if (error == br.com.saqz.groups.presentation.GroupUiError.Validation) {
                            stringResource(Res.string.new_entry_validation)
                        } else if (state.editing && error == br.com.saqz.groups.presentation.GroupUiError.Conflict) {
                            stringResource(Res.string.new_entry_edit_conflict)
                        } else {
                            stringResource(Res.string.new_entry_save_error)
                        },
                        style = SaqzTheme.typography.caption,
                        color = SaqzTheme.colors.errorForeground,
                    )
                }
            }
            SaqzButton(
                label = when {
                    state.isSaving -> stringResource(Res.string.new_entry_saving)
                    state.editing -> stringResource(Res.string.new_entry_save_edit)
                    out -> stringResource(Res.string.entry_out_save)
                    else -> stringResource(Res.string.entry_in_save)
                },
                onClick = { onIntent(NewEntryIntent.Save) },
                enabled = !state.isSaving,
                loading = state.isSaving,
                fullWidth = true,
                modifier = Modifier
                    .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap)
                    .testTag(NewEntryTags.Save),
            )
        }
        EntryDateSheet(
            open = state.datePickerOpen,
            date = state.date,
            today = state.today,
            onClose = { onIntent(NewEntryIntent.DismissDatePicker) },
            onSave = { onIntent(NewEntryIntent.DateChanged(it)) },
        )
    }
}

@Composable
private fun FieldLabel(text: String) = Text(
    text = text,
    style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
    color = SaqzTheme.colors.textPrimary,
)

/** O calendário da data: do começo do mês passado (ou do mês da data já escolhida) até o fim do mês atual. */
@Composable
private fun EntryDateSheet(open: Boolean, date: String, today: String, onClose: () -> Unit, onSave: (String) -> Unit) {
    val weekdays = listOf(
        Res.string.group_weekday_monday, Res.string.group_weekday_tuesday, Res.string.group_weekday_wednesday,
        Res.string.group_weekday_thursday, Res.string.group_weekday_friday, Res.string.group_weekday_saturday,
        Res.string.group_weekday_sunday,
    ).map { stringResource(it) }
    val months = listOf(
        Res.string.game_picker_month_january, Res.string.game_picker_month_february, Res.string.game_picker_month_march,
        Res.string.game_picker_month_april, Res.string.game_picker_month_may, Res.string.game_picker_month_june,
        Res.string.game_picker_month_july, Res.string.game_picker_month_august, Res.string.game_picker_month_september,
        Res.string.game_picker_month_october, Res.string.game_picker_month_november, Res.string.game_picker_month_december,
    ).map { stringResource(it) }
    val days = remember(date, today, weekdays, months) {
        val now = runCatching { LocalDate.parse(today) }.getOrNull() ?: return@remember emptyList()
        val chosen = runCatching { LocalDate.parse(date) }.getOrNull() ?: now
        val previousMonth = LocalDate(now.year, now.month, 1).minus(DatePeriod(months = 1))
        val start = minOf(previousMonth, LocalDate(chosen.year, chosen.month, 1))
        val end = LocalDate(now.year, now.month, 1).plus(DatePeriod(months = 1))
        buildPickerDays(start, (end.toEpochDays() - start.toEpochDays()).toInt(), weekdays, months)
    }
    val wheel = remember(days, date) { GameDateTimePicker.state(days, date, 0, 0) }
    var dayIndex by remember(open, date) { mutableStateOf(wheel.selectedDayIndex) }
    SaqzBottomSheet(
        open = open && days.isNotEmpty(),
        onClose = onClose,
        title = stringResource(Res.string.entry_date_pick_title),
        modifier = Modifier.testTag(NewEntryTags.DatePicker),
        footer = {
            SaqzButton(
                label = stringResource(Res.string.entry_date_pick_save),
                onClick = { days.getOrNull(dayIndex)?.isoDate?.let(onSave) },
                fullWidth = true,
                modifier = Modifier.testTag(NewEntryTags.DateSave),
            )
        },
    ) {
        Text(days.getOrNull(dayIndex)?.label.orEmpty(), style = SaqzTheme.typography.subtitle, color = SaqzTheme.colors.textPrimary)
        GameDateTimePicker(
            state = wheel.copy(selectedDayIndex = dayIndex),
            monthLabels = months,
            onDayChange = { dayIndex = it },
            onHourChange = {},
            onMinuteChange = {},
            showTime = false,
        )
    }
}

@Composable
private fun NewEntryCategory.label(): String = stringResource(
    when (this) {
        NewEntryCategory.Court -> Res.string.new_entry_category_court
        NewEntryCategory.Material -> Res.string.new_entry_category_material
        NewEntryCategory.Referee -> Res.string.entry_category_referee
        NewEntryCategory.Sponsorship -> Res.string.entry_category_sponsorship
        NewEntryCategory.Sale -> Res.string.entry_category_sale
        NewEntryCategory.Other -> Res.string.new_entry_category_other
    },
)

@Preview
@Composable
private fun NewEntryPreview() = SaqzTheme {
    NewEntryScreen(
        state = NewEntryState(
            date = "2026-10-27",
            direction = NewEntryDirection.Out,
            amountText = "160,00",
            description = "Quadra de terça",
            category = NewEntryCategory.Court,
        ),
        onBack = {},
        onIntent = {},
    )
}
