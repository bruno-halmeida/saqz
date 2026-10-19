package br.com.saqz.groups.presentation.ui.gameeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzStepper
import br.com.saqz.designsystem.SaqzSwitch
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.rememberSaqzDismissKeyboard
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.place.PlaceSuggestion
import br.com.saqz.groups.presentation.ui.components.GroupChoiceChipRow
import br.com.saqz.groups.presentation.ui.components.VenueSearchField
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import br.com.saqz.groups.domain.group.GroupRegularSlot
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.gameeditor.GameEditorEffect
import br.com.saqz.groups.presentation.gameeditor.GameEditorFieldError
import br.com.saqz.groups.presentation.gameeditor.GameEditorFields
import br.com.saqz.groups.presentation.gameeditor.GameEditorIntent
import br.com.saqz.groups.presentation.gameeditor.GameEditorState
import br.com.saqz.groups.presentation.gameeditor.hasSlotAt
import br.com.saqz.groups.presentation.gameeditor.recurrenceSlotFor
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.presentation.ui.LocalOpenSubscriptionPlans
import br.com.saqz.groups.presentation.ui.SubscriptionRequiredDialog
import br.com.saqz.groups.presentation.ui.confirmationLeadLabel
import br.com.saqz.groups.presentation.ui.durationLabel
import br.com.saqz.groups.presentation.ui.label
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.game_capacity_below_confirmed
import br.com.saqz.groups.presentation.GameCapacityLimits
import br.com.saqz.groups.resources.game_editor_capacity_legacy
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
import br.com.saqz.groups.resources.game_editor_error_save_title_edit
import br.com.saqz.groups.resources.game_editor_error_time
import br.com.saqz.groups.resources.game_editor_error_venue_address
import br.com.saqz.groups.resources.game_editor_error_venue_address_too_long
import br.com.saqz.groups.resources.game_editor_error_venue_name
import br.com.saqz.groups.resources.game_editor_error_venue_name_too_long
import br.com.saqz.groups.resources.game_editor_error_recurrence_body
import br.com.saqz.groups.resources.game_editor_error_recurrence_title
import br.com.saqz.groups.resources.game_editor_recurrence_already
import br.com.saqz.groups.resources.game_editor_recurrence_drafts
import br.com.saqz.groups.resources.game_editor_recurrence_hint
import br.com.saqz.groups.resources.game_editor_recurrence_label
import br.com.saqz.groups.resources.game_editor_recurrence_off
import br.com.saqz.groups.resources.game_editor_recurrence_pick_date
import br.com.saqz.groups.resources.game_editor_recurrence_slot
import br.com.saqz.groups.resources.game_editor_recurrence_slot_new
import br.com.saqz.groups.resources.game_editor_recurrence_venue_group
import br.com.saqz.groups.resources.game_editor_recurrence_venue_new
import br.com.saqz.groups.resources.game_editor_group_name
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
import br.com.saqz.groups.resources.game_editor_plan_block_admin
import br.com.saqz.groups.resources.game_editor_plan_block_plan
import br.com.saqz.groups.resources.game_editor_plan_block_plan_passed
import br.com.saqz.groups.resources.game_editor_plan_block_trial
import br.com.saqz.groups.resources.game_editor_plan_block_trial_passed
import br.com.saqz.groups.resources.subscription_required_view_plans
import br.com.saqz.groups.resources.game_editor_venue_address_hint
import br.com.saqz.groups.resources.game_editor_venue_address_label
import br.com.saqz.groups.resources.game_editor_venue_helper
import br.com.saqz.groups.resources.game_editor_venue_helper_new
import br.com.saqz.groups.resources.game_editor_error_fee
import br.com.saqz.groups.resources.game_editor_fee_hint
import br.com.saqz.groups.resources.game_editor_fee_label
import br.com.saqz.groups.resources.game_editor_fee_placeholder
import br.com.saqz.groups.resources.game_editor_missing_and
import br.com.saqz.groups.resources.game_editor_missing_item_fee
import br.com.saqz.groups.resources.game_editor_missing_item_date
import br.com.saqz.groups.resources.game_editor_missing_item_time
import br.com.saqz.groups.resources.game_editor_missing_item_venue_address
import br.com.saqz.groups.resources.game_editor_missing_item_venue_name
import br.com.saqz.groups.resources.game_editor_venue_label
import br.com.saqz.groups.resources.game_editor_venue_name_hint
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal object GameEditorTags {
    const val Screen = "game-editor"
    const val Submit = "game-editor-submit"
    const val PlanBlock = "game-editor-plan-block"
    const val PlanBlockViewPlans = "game-editor-plan-block-view-plans"
    const val Date = "game-editor-date"
    const val Time = "game-editor-time"
    const val DateTimePicker = "game-editor-date-time-picker"
    const val ErrorBanner = "game-editor-error-banner"
    const val ConflictBanner = "game-editor-conflict-banner"
    const val SaveFailure = "game-editor-save-failure"
    const val Recurrence = "game-editor-recurrence"
    const val RecurrenceToggle = "game-editor-recurrence-toggle"
    const val RecurrenceFailure = "game-editor-recurrence-failure"
    const val VenueName = "game-editor-venue-name"
    const val VenueAddress = "game-editor-venue-address"
    const val GameFee = "game-editor-game-fee"
    const val Duration = "game-editor-duration"
    const val ConfirmationLead = "game-editor-confirmation-lead"
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
    // Com o teclado aberto e as sugestões do Google na tela, o botão fixo sai da frente: o teclado já
    // come metade da altura, e com o botão sobrava pouco mais que o campo, com a lista atrás dos dois.
    // Fora desse caso (digitando o recado, por exemplo) o botão segue onde sempre esteve.
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val suggestionsOverKeyboard = keyboardOpen && state.venueSearch.suggestions.isNotEmpty()
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
            if (!state.isLoading && !state.loadFailed && !suggestionsOverKeyboard) {
                SubmitButton(
                    isEditing = isEditing,
                    isSaving = state.isSaving,
                    enabled = state.conflictGameId == null && !state.isSaving && !state.planBlocked,
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
        if (state.subscriptionRequired) {
            SubscriptionRequiredDialog(
                trialEnded = state.trialEnded,
                onDismiss = { onIntent(GameEditorIntent.DismissSubscriptionRequired) },
            )
        }
    }
}

/**
 * O jogo começa depois do fim do plano ou do teste (DEB-075): não marca nem salva. Para o dono o texto diz até que dia
 * dá para marcar e oferece os planos; o admin, que não assina, só lê que o plano do grupo acaba antes.
 */
@Composable
private fun PlanBlockCard(state: GameEditorState) {
    val openPlans = LocalOpenSubscriptionPlans.current
    val lastDay = state.planLastDay?.let(::formatDisplayDate).orEmpty()
    SaqzCard(modifier = Modifier.testTag(GameEditorTags.PlanBlock), tone = SaqzCardTone.Soft) {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
            Text(
                text = when {
                    !state.isOwner -> stringResource(Res.string.game_editor_plan_block_admin)
                    state.planLimitIsTrial && state.planLastDayPassed -> stringResource(Res.string.game_editor_plan_block_trial_passed)
                    state.planLastDayPassed -> stringResource(Res.string.game_editor_plan_block_plan_passed)
                    state.planLimitIsTrial -> stringResource(Res.string.game_editor_plan_block_trial, lastDay)
                    else -> stringResource(Res.string.game_editor_plan_block_plan, lastDay)
                },
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textPrimary,
            )
            if (state.isOwner) {
                SaqzButton(
                    stringResource(Res.string.subscription_required_view_plans),
                    openPlans,
                    Modifier.testTag(GameEditorTags.PlanBlockViewPlans),
                    SaqzButtonVariant.Secondary,
                )
            }
        }
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
    val scrollState = rememberScrollState()
    val targets = remember { EditorScrollTargets() }
    // A tela rola até o primeiro campo com erro a cada toque em "Marcar jogo": o erro aparece no
    // campo, não só no topo. `submitAttempts` faz o mesmo erro rolar de novo no segundo toque.
    LaunchedEffect(state.submitAttempts) {
        if (state.submitAttempts > 0) targets.firstFor(errors)?.bringIntoView()
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
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
                body = stringResource(Res.string.game_editor_error_missing_body, missingItems(errors)),
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
                isEditing = state.versionToken != null,
                onRetry = { onIntent(GameEditorIntent.Submit) },
            )
        }
        if (state.recurrenceFailed) {
            RecurrenceFailureBanner(
                onRetry = { onIntent(GameEditorIntent.Submit) },
            )
        }
        DateTimeFields(
            form = state.form,
            errors = errors,
            onOpenPicker = onOpenPicker,
            modifier = Modifier.bringIntoViewRequester(targets.dateTime),
        )
        if (state.recurrenceOffered) {
            RecurrenceSection(
                state = state,
                onToggle = { onIntent(GameEditorIntent.ToggleRecurring(it)) },
            )
        }
        if (state.planBlocked) PlanBlockCard(state)
        DurationChips(
            selected = state.form.durationMinutes,
            onSelect = { onIntent(GameEditorIntent.SelectDuration(it)) },
        )
        VenueFields(
            venue = state.form.venue,
            errors = errors,
            groupHasVenue = state.groupHasVenue,
            creating = state.versionToken == null,
            suggestions = state.venueSearch.suggestions,
            targets = targets,
            onNameChange = { onIntent(GameEditorIntent.UpdateVenueName(it)) },
            onAddressChange = { onIntent(GameEditorIntent.UpdateVenueAddress(it)) },
            onSelectSuggestion = { onIntent(GameEditorIntent.SelectPlaceSuggestion(it)) },
            onDismissSuggestions = { onIntent(GameEditorIntent.DismissPlaceSuggestions) },
        )
        SaqzCard(padded = false) {
            Column(
                modifier = Modifier.padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
                verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
            ) {
                SaqzStepper(
                    value = state.form.capacity,
                    onValueChange = { onIntent(GameEditorIntent.UpdateCapacity(it)) },
                    min = GameCapacityLimits.Min,
                    max = GameCapacityLimits.Max,
                    label = stringResource(Res.string.game_editor_capacity_label),
                )
                HelperText(stringResource(Res.string.game_editor_capacity_helper))
                state.legacyCapacity?.let { legacy ->
                    HelperText(stringResource(Res.string.game_editor_capacity_legacy, legacy, GameCapacityLimits.Min))
                }
                if (state.confirmedCount > state.form.capacity) {
                    HelperText(
                        pluralStringResource(
                            Res.plurals.game_capacity_below_confirmed,
                            state.confirmedCount - state.form.capacity,
                            state.confirmedCount,
                            state.form.capacity,
                            state.confirmedCount - state.form.capacity,
                        ),
                    )
                }
            }
        }
        ConfirmationLeadChips(
            selected = state.form.confirmationLeadMinutes,
            onSelect = { onIntent(GameEditorIntent.SelectConfirmationLead(it)) },
        )
        SaqzCard(modifier = Modifier.bringIntoViewRequester(targets.gameFee)) {
            SaqzSectionHeader(title = stringResource(Res.string.game_editor_fee_label))
            SaqzInput(
                value = state.form.gameFeeText,
                onValueChange = { onIntent(GameEditorIntent.UpdateGameFee(it)) },
                label = stringResource(Res.string.game_editor_fee_label),
                showLabel = false,
                placeholder = stringResource(Res.string.game_editor_fee_placeholder),
                helperText = stringResource(Res.string.game_editor_fee_hint),
                errorText = if (GameEditorFieldError.GameFeeInvalid in errors) {
                    stringResource(Res.string.game_editor_error_fee)
                } else null,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.testTag(GameEditorTags.GameFee),
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
private fun SaveFailureBanner(isEditing: Boolean, onRetry: () -> Unit) {
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
            // Numa edição o jogo já existe: "criar" seria o texto errado.
            stringResource(
                if (isEditing) Res.string.game_editor_error_save_title_edit else Res.string.game_editor_error_save_title,
            ),
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
private fun RecurrenceFailureBanner(onRetry: () -> Unit) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(GameEditorTags.RecurrenceFailure)
            .background(colors.errorForeground.copy(alpha = SoftTintAlpha), RoundedCornerShape(metrics.cardRadius))
            .padding(metrics.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        Text(
            stringResource(Res.string.game_editor_error_recurrence_title),
            style = SaqzTheme.typography.subtitle,
            color = colors.errorForeground,
        )
        Text(
            stringResource(Res.string.game_editor_error_recurrence_body),
            style = SaqzTheme.typography.support,
            color = colors.errorForeground,
        )
        SaqzButton(
            label = stringResource(Res.string.game_editor_error_retry),
            onClick = onRetry,
            variant = SaqzButtonVariant.Secondary,
            size = SaqzButtonSize.Sm,
        )
    }
}

/**
 * "Repetir toda semana" (4a). Ligado, mostra os horários que o grupo já tem e o deste jogo; a
 * recorrência continua sendo a da agenda do grupo (2m), só que ligada daqui.
 */
@Composable
private fun RecurrenceSection(state: GameEditorState, onToggle: (Boolean) -> Unit) {
    val metrics = SaqzTheme.metrics
    val label = stringResource(Res.string.game_editor_recurrence_label)
    val slot = recurrenceSlotFor(state.form)
    val alreadyRecurring = slot != null && state.regularSlots.hasSlotAt(slot)
    SaqzCard(modifier = Modifier.testTag(GameEditorTags.Recurrence)) {
        Column(verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
                    Text(label, style = SaqzTheme.typography.label, color = SaqzTheme.colors.textPrimary)
                    Text(
                        stringResource(Res.string.game_editor_recurrence_hint),
                        style = SaqzTheme.typography.support,
                        color = SaqzTheme.colors.textSecondary,
                    )
                }
                SaqzSwitch(
                    checked = state.form.recurring,
                    onCheckedChange = onToggle,
                    contentDescription = label,
                    modifier = Modifier.testTag(GameEditorTags.RecurrenceToggle),
                )
            }
            when {
                !state.form.recurring -> HelperText(stringResource(Res.string.game_editor_recurrence_off))
                slot == null -> HelperText(stringResource(Res.string.game_editor_recurrence_pick_date))
                else -> RecurrenceSlots(state, slot, alreadyRecurring)
            }
        }
    }
}

@Composable
private fun RecurrenceSlots(state: GameEditorState, slot: GroupRegularSlot, alreadyRecurring: Boolean) {
    val metrics = SaqzTheme.metrics
    Column(verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
        state.regularSlots.forEach { existing -> RecurrencePill(existing, highlighted = false) }
        if (!alreadyRecurring) RecurrencePill(slot, highlighted = true)
        HelperText(
            stringResource(
                when {
                    alreadyRecurring -> Res.string.game_editor_recurrence_already
                    state.groupHasVenue -> Res.string.game_editor_recurrence_venue_group
                    else -> Res.string.game_editor_recurrence_venue_new
                },
            ),
        )
        if (!alreadyRecurring) HelperText(stringResource(Res.string.game_editor_recurrence_drafts))
    }
}

@Composable
private fun RecurrencePill(slot: GroupRegularSlot, highlighted: Boolean) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val weekday = GroupWeekday.valueOf(slot.weekday.name).label()
    val time = formatDisplayTime(slot.startTime)
    Row(
        modifier = Modifier
            .background(
                if (highlighted) colors.primary.copy(alpha = SoftTintAlpha) else colors.surfaceSoft,
                RoundedCornerShape(metrics.cardRadius),
            )
            .padding(horizontal = metrics.blockGap, vertical = metrics.subGrid),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.subGrid),
    ) {
        SaqzIcon(SaqzIcons.Calendar, tint = if (highlighted) colors.primary else colors.textSecondary)
        Text(
            text = stringResource(
                if (highlighted) Res.string.game_editor_recurrence_slot_new else Res.string.game_editor_recurrence_slot,
                weekday,
                time,
            ),
            style = SaqzTheme.typography.label,
            color = colors.textPrimary,
        )
    }
}

@Composable
private fun DateTimeFields(
    form: GameEditorFields,
    errors: Set<GameEditorFieldError>,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    val dateError = if (GameEditorFieldError.DateMissing in errors) stringResource(Res.string.game_editor_error_date) else null
    val timeError = if (GameEditorFieldError.TimeMissing in errors) stringResource(Res.string.game_editor_error_time) else null
    val dateDisplay = if (form.localDate.isNotBlank()) formatDisplayDate(form.localDate) else ""
    val timeDisplay = if (form.localTime.isNotBlank()) formatDisplayTime(form.localTime) else ""
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
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
internal fun PickerRow(
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
    val dismissKeyboard = rememberSaqzDismissKeyboard()
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    role = androidx.compose.ui.semantics.Role.Button,
                    onClick = {
                        dismissKeyboard()
                        onClick()
                    },
                )
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

/**
 * Os chips quebram de linha em vez de dividir a largura em partes iguais: o texto do chip não quebra nem
 * cede, e uma fatia igual de uma tela estreita (ou com fonte grande) o cortava ("24h ante"). É a mesma
 * fileira que o grupo usa para as mesmas opções.
 */
@Composable
private fun DurationChips(selected: Int, onSelect: (Int) -> Unit) {
    val metrics = SaqzTheme.metrics
    Column(verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
        SaqzSectionHeader(title = stringResource(Res.string.game_editor_duration_label))
        GroupChoiceChipRow(
            values = DurationOptions,
            selectedValue = selected,
            label = { durationLabel(it) },
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth().testTag(GameEditorTags.Duration),
        )
    }
}

@Composable
private fun VenueFields(
    venue: GameVenue?,
    errors: Set<GameEditorFieldError>,
    groupHasVenue: Boolean,
    creating: Boolean,
    suggestions: List<PlaceSuggestion>,
    targets: EditorScrollTargets,
    onNameChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onSelectSuggestion: (PlaceSuggestion) -> Unit,
    onDismissSuggestions: () -> Unit,
) {
    val metrics = SaqzTheme.metrics
    val nameError = when {
        GameEditorFieldError.VenueNameMissing in errors -> stringResource(Res.string.game_editor_error_venue_name)
        GameEditorFieldError.VenueNameTooLong in errors -> stringResource(Res.string.game_editor_error_venue_name_too_long)
        else -> null
    }
    val addressError = when {
        GameEditorFieldError.VenueAddressTooShort in errors -> stringResource(Res.string.game_editor_error_venue_address)
        GameEditorFieldError.VenueAddressTooLong in errors -> stringResource(Res.string.game_editor_error_venue_address_too_long)
        else -> null
    }
    SaqzCard(padded = false) {
        Column(
            modifier = Modifier.padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
            verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
        ) {
            SaqzSectionHeader(title = stringResource(Res.string.game_editor_venue_label))
            VenueSearchField(
                suggestions = suggestions,
                onSelect = onSelectSuggestion,
                onDismiss = onDismissSuggestions,
                modifier = Modifier.bringIntoViewRequester(targets.venueName),
            ) {
                SaqzInput(
                    value = venue?.name.orEmpty(),
                    onValueChange = onNameChange,
                    label = stringResource(Res.string.game_editor_venue_label),
                    showLabel = false,
                    placeholder = stringResource(Res.string.game_editor_venue_name_hint),
                    errorText = nameError,
                    modifier = Modifier.testTag(GameEditorTags.VenueName),
                )
            }
            SaqzInput(
                value = venue?.address.orEmpty(),
                onValueChange = onAddressChange,
                label = stringResource(Res.string.game_editor_venue_address_label),
                showLabel = false,
                placeholder = stringResource(Res.string.game_editor_venue_address_hint),
                errorText = addressError,
                modifier = Modifier.bringIntoViewRequester(targets.venueAddress).testTag(GameEditorTags.VenueAddress),
            )
            // "Usamos a quadra do grupo" só é verdade quando o grupo tem uma; sem ela, a que a
            // pessoa digita aqui vira a do grupo ao marcar o jogo.
            when {
                groupHasVenue -> HelperText(stringResource(Res.string.game_editor_venue_helper))
                creating -> HelperText(stringResource(Res.string.game_editor_venue_helper_new))
            }
        }
    }
}

/** Onde a tela rola quando "Marcar jogo" acusa erro: o primeiro campo com problema, de cima para baixo. */
@Stable
private class EditorScrollTargets {
    val dateTime = BringIntoViewRequester()
    val venueName = BringIntoViewRequester()
    val venueAddress = BringIntoViewRequester()
    val gameFee = BringIntoViewRequester()

    fun firstFor(errors: Set<GameEditorFieldError>): BringIntoViewRequester? = when {
        GameEditorFieldError.DateMissing in errors || GameEditorFieldError.TimeMissing in errors -> dateTime
        GameEditorFieldError.VenueNameMissing in errors || GameEditorFieldError.VenueNameTooLong in errors -> venueName
        GameEditorFieldError.VenueAddressTooShort in errors ||
            GameEditorFieldError.VenueAddressTooLong in errors -> venueAddress
        GameEditorFieldError.GameFeeInvalid in errors -> gameFee
        else -> null
    }
}

/** "a data, o horário e o nome da quadra": só o que falta de verdade, na ordem da tela. */
@Composable
private fun missingItems(errors: Set<GameEditorFieldError>): String {
    val items = buildList {
        if (GameEditorFieldError.DateMissing in errors) add(stringResource(Res.string.game_editor_missing_item_date))
        if (GameEditorFieldError.TimeMissing in errors) add(stringResource(Res.string.game_editor_missing_item_time))
        if (GameEditorFieldError.VenueNameMissing in errors || GameEditorFieldError.VenueNameTooLong in errors) {
            add(stringResource(Res.string.game_editor_missing_item_venue_name))
        }
        if (
            GameEditorFieldError.VenueAddressTooShort in errors ||
            GameEditorFieldError.VenueAddressTooLong in errors
        ) {
            add(stringResource(Res.string.game_editor_missing_item_venue_address))
        }
        if (GameEditorFieldError.GameFeeInvalid in errors) add(stringResource(Res.string.game_editor_missing_item_fee))
    }
    val and = stringResource(Res.string.game_editor_missing_and)
    return when (items.size) {
        0, 1 -> items.joinToString()
        else -> items.dropLast(1).joinToString(", ") + " $and " + items.last()
    }
}

@Composable
private fun ConfirmationLeadChips(selected: Int, onSelect: (Int) -> Unit) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.game_editor_confirmation_lead_label))
        GroupChoiceChipRow(
            values = LeadOptions,
            selectedValue = selected,
            label = { confirmationLeadLabel(it) },
            onSelect = onSelect,
            modifier = Modifier.fillMaxWidth().testTag(GameEditorTags.ConfirmationLead),
        )
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
    return "${date.day.toString().padStart(2, '0')}/${date.monthNumber.toString().padStart(2, '0')}/${date.year}"
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
        selected >= lastDate -> selected.minus(DatePeriod(days = 14))
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
            recurrenceOffered = true,
            groupHasVenue = true,
        ),
        onBack = {},
        onIntent = {},
    )
}

@Preview
@Composable
private fun GameEditorRecurringPreview() = SaqzTheme {
    GameEditorScreen(
        state = GameEditorState(
            isLoading = false,
            groupName = "Vôlei do CERET",
            form = GameEditorFields(
                localDate = "2026-08-06",
                localTime = "20:00",
                durationMinutes = 120,
                venue = GameVenue(name = "CERET", address = "R. Canuto Abreu"),
                capacity = 12,
                confirmationLeadMinutes = 360,
                recurring = true,
            ),
            recurrenceOffered = true,
            regularSlots = listOf(
                GroupRegularSlot(
                    weekday = br.com.saqz.groups.domain.group.GroupWeekday.TUESDAY,
                    startTime = "19:30",
                    durationMinutes = 120,
                ),
            ),
            groupHasVenue = true,
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
