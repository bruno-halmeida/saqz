package br.com.saqz.groups.presentation.ui.gameeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChoiceChip
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzStepper
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.presentation.gameeditor.GameEditorEffect
import br.com.saqz.groups.presentation.gameeditor.GameEditorFieldError
import br.com.saqz.groups.presentation.gameeditor.GameEditorFields
import br.com.saqz.groups.presentation.gameeditor.GameEditorIntent
import br.com.saqz.groups.presentation.gameeditor.GameEditorState
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.game_editor_capacity_helper
import br.com.saqz.groups.resources.game_editor_capacity_label
import br.com.saqz.groups.resources.game_editor_confirmation_lead_label
import br.com.saqz.groups.resources.game_editor_create_action
import br.com.saqz.groups.resources.game_editor_create_title
import br.com.saqz.groups.resources.game_editor_creating
import br.com.saqz.groups.resources.game_editor_date_label
import br.com.saqz.groups.resources.game_editor_date_placeholder
import br.com.saqz.groups.resources.game_editor_duration_label
import br.com.saqz.groups.resources.game_editor_edit_title
import br.com.saqz.groups.resources.game_editor_error_conflict_open_existing
import br.com.saqz.groups.resources.game_editor_error_conflict_pick_other
import br.com.saqz.groups.resources.game_editor_error_conflict_title
import br.com.saqz.groups.resources.game_editor_error_date
import br.com.saqz.groups.resources.game_editor_error_missing_body
import br.com.saqz.groups.resources.game_editor_error_missing_title
import br.com.saqz.groups.resources.game_editor_error_retry
import br.com.saqz.groups.resources.game_editor_error_save_body
import br.com.saqz.groups.resources.game_editor_error_save_title
import br.com.saqz.groups.resources.game_editor_error_time
import br.com.saqz.groups.resources.game_editor_error_venue_address
import br.com.saqz.groups.resources.game_editor_error_venue_name
import br.com.saqz.groups.resources.game_editor_group_name
import br.com.saqz.groups.resources.game_editor_notes_hint
import br.com.saqz.groups.resources.game_editor_notes_label
import br.com.saqz.groups.resources.game_picker_save
import br.com.saqz.groups.resources.game_picker_summary
import br.com.saqz.groups.resources.game_picker_title
import br.com.saqz.groups.resources.game_picker_month_april
import br.com.saqz.groups.resources.game_picker_month_august
import br.com.saqz.groups.resources.game_picker_month_december
import br.com.saqz.groups.resources.game_picker_month_february
import br.com.saqz.groups.resources.game_picker_month_january
import br.com.saqz.groups.resources.game_picker_month_july
import br.com.saqz.groups.resources.game_picker_month_june
import br.com.saqz.groups.resources.game_picker_month_march
import br.com.saqz.groups.resources.game_picker_month_may
import br.com.saqz.groups.resources.game_picker_month_november
import br.com.saqz.groups.resources.game_picker_month_october
import br.com.saqz.groups.resources.game_picker_month_september
import br.com.saqz.groups.resources.group_weekday_friday
import br.com.saqz.groups.resources.group_weekday_monday
import br.com.saqz.groups.resources.group_weekday_saturday
import br.com.saqz.groups.resources.group_weekday_sunday
import br.com.saqz.groups.resources.group_weekday_thursday
import br.com.saqz.groups.resources.group_weekday_tuesday
import br.com.saqz.groups.resources.group_weekday_wednesday
import br.com.saqz.groups.resources.game_editor_save_action
import br.com.saqz.groups.resources.game_editor_saving
import br.com.saqz.groups.resources.game_editor_subtitle
import br.com.saqz.groups.resources.game_editor_time_label
import br.com.saqz.groups.resources.game_editor_time_placeholder
import br.com.saqz.groups.resources.game_editor_venue_address_hint
import br.com.saqz.groups.resources.game_editor_venue_address_label
import br.com.saqz.groups.resources.game_editor_venue_helper
import br.com.saqz.groups.resources.game_editor_venue_label
import br.com.saqz.groups.resources.game_editor_venue_name_hint
import br.com.saqz.groups.resources.group_duration_one_hour
import br.com.saqz.groups.resources.group_duration_one_hour_thirty
import br.com.saqz.groups.resources.group_duration_two_hours
import br.com.saqz.groups.resources.group_duration_two_hours_thirty
import br.com.saqz.groups.resources.group_lead_six_hours
import br.com.saqz.groups.resources.group_lead_three_hours
import br.com.saqz.groups.resources.group_lead_twelve_hours
import br.com.saqz.groups.resources.group_lead_twenty_four_hours
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.compose.resources.stringResource

internal object GameEditorTags {
    const val Screen = "game-editor"
    const val Submit = "game-editor-submit"
    const val Date = "game-editor-date"
    const val Time = "game-editor-time"
    const val DateTimePicker = "game-editor-date-time-picker"
    const val ErrorBanner = "game-editor-error-banner"
    const val ConflictBanner = "game-editor-conflict-banner"
    const val SaveFailure = "game-editor-save-failure"
}

private val DurationOptions = listOf(60, 90, 120, 150)
private val LeadOptions = listOf(180, 360, 720, 1440)
private const val SoftTintAlpha = 0.10f

@Composable
internal fun GameEditorScreen(
    state: GameEditorState,
    onBack: () -> Unit,
    onIntent: (GameEditorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    val isEditing = state.versionToken != null
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        Column(modifier = Modifier.fillMaxSize().imePadding().testTag(GameEditorTags.Screen)) {
            SaqzTopAppBar(
                title = stringResource(if (isEditing) Res.string.game_editor_edit_title else Res.string.game_editor_create_title),
                onBack = onBack,
            )
            when {
                state.isLoading -> LoadingBox()
                state.loadFailed -> GroupLoadFailure(
                    error = state.error,
                    onRetry = { onIntent(GameEditorIntent.Retry) },
                    modifier = Modifier.fillMaxSize(),
                )
                else -> FormScroll(
                    state = state,
                    onIntent = onIntent,
                    onOpenPicker = { pickerOpen = true },
                    modifier = Modifier.weight(1f),
                )
            }
            if (!state.isLoading && !state.loadFailed) {
                SubmitButton(
                    isEditing = isEditing,
                    isSaving = state.isSaving,
                    enabled = state.conflictGameId == null && !state.isSaving,
                    onSubmit = { onIntent(GameEditorIntent.Submit) },
                )
            }
        }
        DateTimePickerSheet(
            open = pickerOpen,
            state = state,
            onClose = { pickerOpen = false },
            onSave = { date, time ->
                onIntent(GameEditorIntent.SaveDateTime(date, time))
                pickerOpen = false
            },
        )
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SaqzSpinner() }
}

@Composable
private fun FormScroll(
    state: GameEditorState,
    onIntent: (GameEditorIntent) -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    val errors = state.validationErrors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        HeaderSubtitle(
            groupName = state.groupName,
            subtitle = stringResource(Res.string.game_editor_subtitle),
        )
        if (errors.isNotEmpty()) {
            MissingInfoBanner(
                title = stringResource(Res.string.game_editor_error_missing_title),
                body = stringResource(Res.string.game_editor_error_missing_body),
            )
        }
        if (state.hasConflict) {
            ConflictBanner(
                onPickOther = { onIntent(GameEditorIntent.DismissConflict) },
                onOpenExisting = { onIntent(GameEditorIntent.OpenExistingGame) },
            )
        }
        if (state.saveFailed) {
            SaveFailureBanner(
                onRetry = { onIntent(GameEditorIntent.Submit) },
            )
        }
        DateTimeFields(
            form = state.form,
            errors = errors,
            onOpenPicker = onOpenPicker,
        )
        DurationChips(
            selected = state.form.durationMinutes,
            onSelect = { onIntent(GameEditorIntent.SelectDuration(it)) },
        )
        VenueFields(
            venue = state.form.venue,
            errors = errors,
            onNameChange = { onIntent(GameEditorIntent.UpdateVenueName(it)) },
            onAddressChange = { onIntent(GameEditorIntent.UpdateVenueAddress(it)) },
        )
        SaqzCard(padded = false) {
            Column(
                modifier = Modifier.padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
                verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
            ) {
                SaqzStepper(
                    value = state.form.capacity,
                    onValueChange = { onIntent(GameEditorIntent.UpdateCapacity(it)) },
                    min = 2,
                    max = 100,
                    label = stringResource(Res.string.game_editor_capacity_label),
                )
                HelperText(stringResource(Res.string.game_editor_capacity_helper))
            }
        }
        ConfirmationLeadChips(
            selected = state.form.confirmationLeadMinutes,
            onSelect = { onIntent(GameEditorIntent.SelectConfirmationLead(it)) },
        )
        SaqzCard {
            SaqzSectionHeader(title = stringResource(Res.string.game_editor_notes_label))
            SaqzInput(
                value = state.form.notes,
                onValueChange = { onIntent(GameEditorIntent.UpdateNotes(it)) },
                label = stringResource(Res.string.game_editor_notes_label),
                showLabel = false,
                placeholder = stringResource(Res.string.game_editor_notes_hint),
                singleLine = false,
                minLines = 3,
            )
        }
    }
}

@Composable
private fun HeaderSubtitle(groupName: String, subtitle: String) {
    val metrics = SaqzTheme.metrics
    Column(verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
        Text(
            text = stringResource(Res.string.game_editor_group_name, groupName),
            style = SaqzTheme.typography.subtitle,
            color = SaqzTheme.colors.textPrimary,
        )
        Text(
            text = subtitle,
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun MissingInfoBanner(title: String, body: String) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(GameEditorTags.ErrorBanner)
            .background(colors.errorForeground.copy(alpha = SoftTintAlpha), RoundedCornerShape(metrics.cardRadius))
            .padding(metrics.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
    ) {
        Text(title, style = SaqzTheme.typography.subtitle, color = colors.errorForeground)
        Text(body, style = SaqzTheme.typography.support, color = colors.errorForeground)
    }
}

@Composable
private fun ConflictBanner(onPickOther: () -> Unit, onOpenExisting: () -> Unit) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(GameEditorTags.ConflictBanner)
            .background(colors.errorForeground.copy(alpha = SoftTintAlpha), RoundedCornerShape(metrics.cardRadius))
            .padding(metrics.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        Text(
            stringResource(Res.string.game_editor_error_conflict_title),
            style = SaqzTheme.typography.subtitle,
            color = colors.errorForeground,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
            SaqzButton(
                label = stringResource(Res.string.game_editor_error_conflict_pick_other),
                onClick = onPickOther,
                variant = SaqzButtonVariant.Secondary,
                size = SaqzButtonSize.Sm,
                modifier = Modifier.weight(1f),
            )
            SaqzButton(
                label = stringResource(Res.string.game_editor_error_conflict_open_existing),
                onClick = onOpenExisting,
                size = SaqzButtonSize.Sm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SaveFailureBanner(onRetry: () -> Unit) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(GameEditorTags.SaveFailure)
            .background(colors.errorForeground.copy(alpha = SoftTintAlpha), RoundedCornerShape(metrics.cardRadius))
            .padding(metrics.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        Text(
            stringResource(Res.string.game_editor_error_save_title),
            style = SaqzTheme.typography.subtitle,
            color = colors.errorForeground,
        )
        Text(stringResource(Res.string.game_editor_error_save_body), style = SaqzTheme.typography.support, color = colors.errorForeground)
        SaqzButton(
            label = stringResource(Res.string.game_editor_error_retry),
            onClick = onRetry,
            variant = SaqzButtonVariant.Secondary,
            size = SaqzButtonSize.Sm,
        )
    }
}

@Composable
private fun DateTimeFields(
    form: GameEditorFields,
    errors: Set<GameEditorFieldError>,
    onOpenPicker: () -> Unit,
) {
    val metrics = SaqzTheme.metrics
    val dateError = if (GameEditorFieldError.DateMissing in errors) stringResource(Res.string.game_editor_error_date) else null
    val timeError = if (GameEditorFieldError.TimeMissing in errors) stringResource(Res.string.game_editor_error_time) else null
    val dateDisplay = if (form.localDate.isNotBlank()) formatDisplayDate(form.localDate) else ""
    val timeDisplay = if (form.localTime.isNotBlank()) formatDisplayTime(form.localTime) else ""
    Column(verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
        SaqzCard(padded = false) {
            PickerRow(
                label = stringResource(Res.string.game_editor_date_label),
                value = dateDisplay,
                placeholder = stringResource(Res.string.game_editor_date_placeholder),
                errorText = dateError,
                tag = GameEditorTags.Date,
                onClick = onOpenPicker,
            )
            SaqzDivider()
            PickerRow(
                label = stringResource(Res.string.game_editor_time_label),
                value = timeDisplay,
                placeholder = stringResource(Res.string.game_editor_time_placeholder),
                errorText = timeError,
                tag = GameEditorTags.Time,
                onClick = onOpenPicker,
            )
        }
    }
}

@Composable
private fun PickerRow(
    label: String,
    value: String,
    placeholder: String,
    errorText: String?,
    tag: String,
    onClick: () -> Unit,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(metrics.inputRadius)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
                .testTag(tag)
                .background(colors.surface, shape)
                .then(
                    if (errorText == null) Modifier else Modifier.border(1.dp, colors.errorForeground, shape),
                )
                .padding(horizontal = metrics.horizontalPadding, vertical = metrics.subGrid),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
            ) {
                Text(label, style = SaqzTheme.typography.caption, color = colors.textSecondary)
                Text(
                    text = value.ifBlank { placeholder },
                    style = SaqzTheme.typography.body,
                    color = if (value.isBlank()) colors.textPlaceholder else colors.textPrimary,
                )
            }
            br.com.saqz.designsystem.SaqzIcon(br.com.saqz.designsystem.SaqzIcons.ChevronRight, tint = colors.textSecondary)
        }
        if (errorText != null) {
            Text(
                errorText,
                style = SaqzTheme.typography.caption,
                color = colors.errorForeground,
                modifier = Modifier.padding(
                    start = metrics.horizontalPadding,
                    end = metrics.horizontalPadding,
                    bottom = metrics.subGrid,
                ),
            )
        }
    }
}

@Composable
private fun DurationChips(selected: Int, onSelect: (Int) -> Unit) {
    val metrics = SaqzTheme.metrics
    val labels = listOf(
        stringResource(Res.string.group_duration_one_hour),
        stringResource(Res.string.group_duration_one_hour_thirty),
        stringResource(Res.string.group_duration_two_hours),
        stringResource(Res.string.group_duration_two_hours_thirty),
    )
    Column(verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
        SaqzSectionHeader(title = stringResource(Res.string.game_editor_duration_label))
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
            DurationOptions.forEachIndexed { index, minutes ->
                SaqzChoiceChip(
                    label = labels[index],
                    selected = selected == minutes,
                    onClick = { onSelect(minutes) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun VenueFields(
    venue: GameVenue?,
    errors: Set<GameEditorFieldError>,
    onNameChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
) {
    val metrics = SaqzTheme.metrics
    val nameError = if (GameEditorFieldError.VenueNameMissing in errors) {
        stringResource(Res.string.game_editor_error_venue_name)
    } else {
        null
    }
    val addressError = if (GameEditorFieldError.VenueAddressMissing in errors) {
        stringResource(Res.string.game_editor_error_venue_address)
    } else {
        null
    }
    SaqzCard(padded = false) {
        Column(
            modifier = Modifier.padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
            verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
        ) {
            SaqzSectionHeader(title = stringResource(Res.string.game_editor_venue_label))
            SaqzInput(
                value = venue?.name.orEmpty(),
                onValueChange = onNameChange,
                label = stringResource(Res.string.game_editor_venue_label),
                showLabel = false,
                placeholder = stringResource(Res.string.game_editor_venue_name_hint),
                errorText = nameError,
            )
            SaqzInput(
                value = venue?.address.orEmpty(),
                onValueChange = onAddressChange,
                label = stringResource(Res.string.game_editor_venue_address_label),
                showLabel = false,
                placeholder = stringResource(Res.string.game_editor_venue_address_hint),
                errorText = addressError,
            )
            HelperText(stringResource(Res.string.game_editor_venue_helper))
        }
    }
}

@Composable
private fun ConfirmationLeadChips(selected: Int, onSelect: (Int) -> Unit) {
    val metrics = SaqzTheme.metrics
    val labels = listOf(
        stringResource(Res.string.group_lead_three_hours),
        stringResource(Res.string.group_lead_six_hours),
        stringResource(Res.string.group_lead_twelve_hours),
        stringResource(Res.string.group_lead_twenty_four_hours),
    )
    Column(verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
        SaqzSectionHeader(title = stringResource(Res.string.game_editor_confirmation_lead_label))
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
            LeadOptions.forEachIndexed { index, minutes ->
                SaqzChoiceChip(
                    label = labels[index],
                    selected = selected == minutes,
                    onClick = { onSelect(minutes) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SubmitButton(isEditing: Boolean, isSaving: Boolean, enabled: Boolean, onSubmit: () -> Unit) {
    val metrics = SaqzTheme.metrics
    val label = if (isEditing) {
        if (isSaving) stringResource(Res.string.game_editor_saving) else stringResource(Res.string.game_editor_save_action)
    } else {
        if (isSaving) stringResource(Res.string.game_editor_creating) else stringResource(Res.string.game_editor_create_action)
    }
    Box(modifier = Modifier.fillMaxWidth().padding(metrics.horizontalPadding)) {
        SaqzButton(
            label = label,
            onClick = onSubmit,
            fullWidth = true,
            enabled = enabled && !isSaving,
            loading = isSaving,
            modifier = Modifier.testTag(GameEditorTags.Submit),
        )
    }
}

@Composable
private fun HelperText(text: String) {
    Text(text, style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
}

@Composable
private fun DateTimePickerSheet(
    open: Boolean,
    state: GameEditorState,
    onClose: () -> Unit,
    onSave: (date: String, time: String) -> Unit,
) {
    val pickerZoneId = state.zoneId.ifBlank { TimeZone.currentSystemDefault().id }
    val today = remember(pickerZoneId) { pickerTodayAt(Clock.System.now(), pickerZoneId) }
    val weekdayLabels = listOf(
        stringResource(Res.string.group_weekday_monday),
        stringResource(Res.string.group_weekday_tuesday),
        stringResource(Res.string.group_weekday_wednesday),
        stringResource(Res.string.group_weekday_thursday),
        stringResource(Res.string.group_weekday_friday),
        stringResource(Res.string.group_weekday_saturday),
        stringResource(Res.string.group_weekday_sunday),
    )
    val monthLabels = listOf(
        stringResource(Res.string.game_picker_month_january),
        stringResource(Res.string.game_picker_month_february),
        stringResource(Res.string.game_picker_month_march),
        stringResource(Res.string.game_picker_month_april),
        stringResource(Res.string.game_picker_month_may),
        stringResource(Res.string.game_picker_month_june),
        stringResource(Res.string.game_picker_month_july),
        stringResource(Res.string.game_picker_month_august),
        stringResource(Res.string.game_picker_month_september),
        stringResource(Res.string.game_picker_month_october),
        stringResource(Res.string.game_picker_month_november),
        stringResource(Res.string.game_picker_month_december),
    )
    val rangeStart = remember(today, state.form.localDate) {
        pickerRangeStart(today, state.form.localDate)
    }
    val days = remember(rangeStart, weekdayLabels, monthLabels) {
        buildWheelDays(rangeStart, 30, weekdayLabels, monthLabels)
    }
    val (initialHour, initialMinute) = pickerTimeParts(state.form.localTime)
    val wheelState = remember(open, state.form.localDate, initialHour, initialMinute) {
        GameDateTimeWheel.state(days, state.form.localDate, initialHour, initialMinute)
    }
    var dayIndex by remember(open) { mutableStateOf(wheelState.selectedDayIndex) }
    var hour by remember(open) { mutableStateOf(wheelState.selectedHour) }
    var minute by remember(open) { mutableStateOf(wheelState.selectedMinute) }
    val dayLabel = days.getOrNull(dayIndex)?.label.orEmpty()
    val timeLabel = "${hour.toString().padStart(2, '0')}h${minute.toString().padStart(2, '0')}"
    val summary = stringResource(Res.string.game_picker_summary, dayLabel, timeLabel)
    SaqzBottomSheet(
        open = open,
        onClose = onClose,
        title = stringResource(Res.string.game_picker_title),
        modifier = Modifier.testTag(GameEditorTags.DateTimePicker),
        footer = {
            SaqzButton(
                label = stringResource(Res.string.game_picker_save),
                onClick = {
                    val date = days.getOrNull(dayIndex)?.isoDate.orEmpty()
                    val time = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
                    onSave(date, time)
                },
                fullWidth = true,
            )
        },
    ) {
        Text(summary, style = SaqzTheme.typography.subtitle, color = SaqzTheme.colors.textPrimary)
        GameDateTimeWheel(
            state = wheelState.copy(selectedDayIndex = dayIndex, selectedHour = hour, selectedMinute = minute),
            onDayChange = { dayIndex = it },
            onHourChange = { hour = it },
            onMinuteChange = { minute = it },
        )
    }
}

internal fun formatDisplayDate(iso: String): String {
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    return "${date.day}/${date.monthNumber.toString().padStart(2, '0')}/${date.year}"
}

internal fun formatDisplayTime(time: String): String {
    val parts = time.split(':')
    if (parts.size < 2) return time
    val hour = parts[0].toIntOrNull() ?: return time
    val minute = parts[1].toIntOrNull() ?: return time
    return "${hour.toString().padStart(2, '0')}h${minute.toString().padStart(2, '0')}"
}

internal fun pickerTimeParts(time: String): Pair<Int, Int> = runCatching {
    kotlinx.datetime.LocalTime.parse(time)
}.recoverCatching {
    kotlinx.datetime.LocalTime.parse("${time.trim()}:00")
}.getOrElse {
    kotlinx.datetime.LocalTime(19, 0)
}.let { it.hour to it.minute }

internal fun pickerTodayAt(now: Instant, zoneId: String): LocalDate =
    now.toLocalDateTime(TimeZone.of(zoneId)).date

internal fun pickerRangeStart(today: LocalDate, selectedDate: String): LocalDate {
    val selected = runCatching { LocalDate.parse(selectedDate) }.getOrNull() ?: return today
    val lastDate = today.plus(DatePeriod(days = 29))
    return when {
        selected < today -> selected
        selected > lastDate -> selected.minus(DatePeriod(days = 29))
        else -> today
    }
}

@Preview
@Composable
private fun GameEditorCreatePreview() = SaqzTheme {
    GameEditorScreen(
        state = GameEditorState(
            isLoading = false,
            groupName = "Vôlei do CERET",
            form = GameEditorFields(
                durationMinutes = 120,
                venue = GameVenue(name = "CERET", address = "R. Canuto Abreu"),
                capacity = 12,
                confirmationLeadMinutes = 360,
            ),
        ),
        onBack = {},
        onIntent = {},
    )
}

@Preview
@Composable
private fun GameEditorEditPreview() = SaqzTheme {
    GameEditorScreen(
        state = GameEditorState(
            isLoading = false,
            groupName = "Vôlei do CERET",
            form = GameEditorFields(
                localDate = "2026-08-04",
                localTime = "19:30",
                durationMinutes = 120,
                venue = GameVenue(name = "CERET", address = "R. Canuto Abreu"),
                capacity = 12,
                confirmationLeadMinutes = 360,
                notes = "Cheguem 15 min antes.",
            ),
            versionToken = "etag-1",
        ),
        onBack = {},
        onIntent = {},
    )
}

@Preview
@Composable
private fun GameEditorMissingInfoPreview() = SaqzTheme {
    GameEditorScreen(
        state = GameEditorState(
            isLoading = false,
            groupName = "Vôlei do CERET",
            form = GameEditorFields(
                durationMinutes = 120,
                venue = GameVenue(name = "CERET", address = "R. Canuto Abreu"),
                capacity = 12,
                confirmationLeadMinutes = 360,
            ),
        ),
        onBack = {},
        onIntent = {},
    )
}
