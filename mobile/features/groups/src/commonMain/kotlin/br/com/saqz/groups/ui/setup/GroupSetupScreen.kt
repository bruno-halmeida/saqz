package br.com.saqz.groups.ui.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected as semanticsSelected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzCard
import br.com.saqz.designsystem.component.SaqzInput
import br.com.saqz.designsystem.resources.Res as DesignRes
import br.com.saqz.designsystem.resources.saqz_wordmark
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupVenueForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.setup.GroupSetupError
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.photo.GroupPhotoIntent
import br.com.saqz.groups.presentation.photo.GroupPhotoState
import br.com.saqz.groups.port.GroupPhotoPreviewHandle
import br.com.saqz.groups.resources.*
import br.com.saqz.groups.ui.photo.GroupPhotoEditor
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

enum class GroupSetupAccess { ORGANIZER, ATHLETE }

internal object GroupSetupTags {
    const val Screen = "group-setup-screen"
    const val Profile = "group-setup-profile"
    const val GameDefaults = "group-setup-game-defaults"
    const val FinanceDefaults = "group-setup-finance-defaults"
    const val Submit = "group-setup-submit"
    const val AddVenue = "group-setup-add-venue"
    const val AddSlot = "group-setup-add-slot"
    const val TimeZone = "group-setup-timezone"
    const val Header = "group-setup-header"
    const val Wordmark = "group-setup-wordmark"
    const val Title = "group-setup-title"
}

@Composable
fun GroupSetupScreen(
    state: GroupSetupState,
    access: GroupSetupAccess = GroupSetupAccess.ORGANIZER,
    photoState: GroupPhotoState = GroupPhotoState(),
    onPhotoIntent: (GroupPhotoIntent) -> Unit = {},
    photoPreview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> Unit)? = null,
    onIntent: (GroupSetupIntent) -> Unit,
) {
    val editable = access == GroupSetupAccess.ORGANIZER && state.successGroupId == null
    val form = state.form
    val metrics = SaqzTheme.metrics
    Column(
        Modifier.fillMaxSize()
            .background(SaqzTheme.colors.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.grid)
            .testTag(GroupSetupTags.Screen),
        verticalArrangement = Arrangement.spacedBy(metrics.grid + metrics.subGrid),
    ) {
        GroupSetupHeader(
            title = stringResource(
                if (state.mode == GroupSetupMode.CREATE) Res.string.group_setup_create_title
                else Res.string.group_setup_edit_title,
            ),
        )
        if (state.mode == GroupSetupMode.EDIT && (form.modality == null || form.composition == null)) {
            Notice(stringResource(Res.string.group_setup_incomplete))
        }
        SetupSection(Res.string.group_setup_profile_section, GroupSetupTags.Profile) {
            GroupPhotoEditor(
                state = photoState,
                groupName = form.name,
                canEdit = access == GroupSetupAccess.ORGANIZER,
                optional = state.mode == GroupSetupMode.CREATE,
                deferUpload = state.mode == GroupSetupMode.CREATE && state.successGroupId == null,
                onIntent = onPhotoIntent,
                onPrepared = { onIntent(GroupSetupIntent.SetPhotoPending(it)) },
                onReloadTarget = { onIntent(GroupSetupIntent.ReloadConflict) },
                preview = photoPreview,
            )
            SetupInput(form.name, Res.string.group_setup_name, editable, error(state, "name")) {
                onIntent(GroupSetupIntent.UpdateName(it))
            }
            ChoiceField(
                label = Res.string.group_setup_modality,
                values = GroupModality.entries,
                selected = form.modality,
                enabled = editable,
                labelFor = { modalityLabel(it) },
                onSelect = { value -> value?.let { onIntent(GroupSetupIntent.UpdateModality(it)) } },
                errorText = error(state, "modality"),
            )
            ChoiceField(
                label = Res.string.group_setup_composition,
                values = GroupComposition.entries,
                selected = form.composition,
                enabled = editable,
                labelFor = { compositionLabel(it) },
                onSelect = { value -> value?.let { onIntent(GroupSetupIntent.UpdateComposition(it)) } },
                errorText = error(state, "composition"),
            )
            SetupInput(form.description.orEmpty(), Res.string.group_setup_description, editable) {
                onIntent(GroupSetupIntent.UpdateDescription(it))
            }
            SetupInput(form.city.orEmpty(), Res.string.group_setup_city, editable) {
                onIntent(GroupSetupIntent.UpdateCity(it))
            }
            ChoiceField(
                label = Res.string.group_setup_level,
                values = GroupLevel.entries,
                selected = form.level,
                enabled = editable,
                optional = true,
                labelFor = { levelLabel(it) },
                onSelect = { onIntent(GroupSetupIntent.UpdateLevel(it)) },
            )
            if (state.showCustomLevel) {
                SetupInput(form.customLevel.orEmpty(), Res.string.group_setup_custom_level, editable, error(state, "customLevel")) {
                    onIntent(GroupSetupIntent.UpdateCustomLevel(it))
                }
            }
            if (state.showPlayStyle) {
                ChoiceField(
                    label = Res.string.group_setup_play_style,
                    values = GroupPlayStyle.entries,
                    selected = form.playStyle,
                    enabled = editable,
                    optional = true,
                    labelFor = { playStyleLabel(it) },
                    onSelect = { onIntent(GroupSetupIntent.UpdatePlayStyle(it)) },
                )
            }
            if (state.showCustomPlayStyle) {
                SetupInput(form.customPlayStyle.orEmpty(), Res.string.group_setup_custom_play_style, editable, error(state, "customPlayStyle")) {
                    onIntent(GroupSetupIntent.UpdateCustomPlayStyle(it))
                }
            }
            if (state.timezoneSelectionRequired) {
                FriendlyTimeZoneSelector(editable, error(state, "timeZone"), onIntent)
            }
        }

        SetupSection(Res.string.group_setup_game_defaults_section, GroupSetupTags.GameDefaults) {
            Text(stringResource(Res.string.group_setup_optional_hint), style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textMuted)
            if (editable && form.defaultVenue == null && form.regularSlots.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
                ) {
                    AddVenueButton(onIntent, Modifier.weight(1f))
                    AddSlotButton(form.regularSlots, onIntent, Modifier.weight(1f))
                }
            }
            VenueEditor(form.defaultVenue, editable, state, onIntent, showAddAction = form.regularSlots.isNotEmpty())
            SlotEditors(form.regularSlots, editable, state, onIntent, showAddAction = form.defaultVenue != null)
            SetupInput(form.defaultCapacity?.toString().orEmpty(), Res.string.group_setup_capacity, editable, error(state, "defaultCapacity")) {
                onIntent(GroupSetupIntent.UpdateDefaultCapacity(it.toNullableInt()))
            }
            SetupInput(
                form.defaultConfirmationLeadMinutes?.toString().orEmpty(),
                Res.string.group_setup_confirmation_lead,
                editable,
                error(state, "defaultConfirmationLeadMinutes"),
            ) { onIntent(GroupSetupIntent.UpdateConfirmationLeadMinutes(it.toNullableInt())) }
            SetupInput(
                formatBrlInput(form.defaultGameFeeCents),
                Res.string.group_setup_game_fee,
                editable,
                error(state, "defaultGameFeeCents"),
                helper = Res.string.group_setup_brl_helper,
            ) { onIntent(GroupSetupIntent.UpdateDefaultGameFeeCents(parseBrlCents(it))) }
        }

        if (access == GroupSetupAccess.ORGANIZER) {
            SetupSection(Res.string.group_setup_finance_defaults_section, GroupSetupTags.FinanceDefaults) {
                Text(stringResource(Res.string.group_setup_optional_hint), style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textMuted)
                SetupInput(
                    formatBrlInput(form.monthlyFeeCents),
                    Res.string.group_setup_monthly_fee,
                    editable,
                    error(state, "monthlyFeeCents"),
                    helper = Res.string.group_setup_brl_helper,
                ) { onIntent(GroupSetupIntent.UpdateMonthlyFee(parseBrlCents(it), form.monthlyDueDay)) }
                if (form.monthlyFeeCents != null) {
                    SetupInput(form.monthlyDueDay?.toString().orEmpty(), Res.string.group_setup_due_day, editable, error(state, "monthlyDueDay")) {
                        onIntent(GroupSetupIntent.UpdateMonthlyFee(form.monthlyFeeCents, it.toNullableInt()))
                    }
                }
            }
        }

        state.error?.let { Notice(stringResource(errorLabel(it))) }
        if (state.conflict) {
            Notice(stringResource(Res.string.group_setup_conflict))
            SaqzButton(
                stringResource(Res.string.group_setup_reload),
                { onIntent(GroupSetupIntent.ReloadConflict) },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.photoRetryAvailable) {
            Notice(stringResource(Res.string.group_setup_photo_failed))
            SaqzButton(
                stringResource(Res.string.group_setup_photo_retry),
                { onIntent(GroupSetupIntent.RetryPhotoUpload) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.successGroupId != null) Notice(stringResource(Res.string.group_setup_success))
        if (editable) {
            SaqzButton(
                stringResource(if (state.mode == GroupSetupMode.CREATE) Res.string.group_setup_create_action else Res.string.group_setup_save_action),
                { onIntent(GroupSetupIntent.Submit) },
                loading = state.isLoading,
                modifier = Modifier.fillMaxWidth().testTag(GroupSetupTags.Submit),
            )
        }
    }
}

@Composable
private fun GroupSetupHeader(title: String) {
    val colors = SaqzTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth().testTag(GroupSetupTags.Header),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
    ) {
        Box(Modifier.fillMaxWidth().height(SaqzTheme.metrics.minimumTouchTarget)) {
            Image(
                painter = painterResource(DesignRes.drawable.saqz_wordmark),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 80.dp, height = 24.dp)
                    .testTag(GroupSetupTags.Wordmark),
            )
        }
        Text(
            text = title,
            style = SaqzTheme.typography.lead.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
            color = colors.textPrimary,
            modifier = Modifier.fillMaxWidth().semantics { heading() }.testTag(GroupSetupTags.Title),
        )
    }
}

@Composable
private fun SetupSection(title: StringResource, tag: String, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(SaqzTheme.metrics.cardRadius)
    SaqzCard(
        Modifier
            .fillMaxWidth()
            .shadow(2.dp, shape, clip = false)
            .border(1.dp, SaqzTheme.colors.hairline, shape)
            .testTag(tag),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid + SaqzTheme.metrics.subGrid)) {
            Text(
                stringResource(title),
                style = SaqzTheme.typography.bodyStrong,
                color = SaqzTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            content()
        }
    }
}

@Composable
private fun SetupInput(
    value: String,
    label: StringResource,
    enabled: Boolean,
    error: String? = null,
    helper: StringResource? = null,
    onChange: (String) -> Unit,
) = SaqzInput(
    value = TextFieldValue(value),
    onValueChange = { onChange(it.text) },
    label = stringResource(label),
    helperText = helper?.let { stringResource(it) },
    errorText = error,
    enabled = enabled,
    inlineLabel = value.isEmpty(),
)

@Composable
private fun <T> ChoiceField(
    label: StringResource,
    values: List<T>,
    selected: T?,
    enabled: Boolean,
    optional: Boolean = false,
    labelFor: @Composable (T) -> String,
    onSelect: (T?) -> Unit,
    errorText: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
        Text(stringResource(label), style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
        val options = buildList<Pair<T?, String>> {
            if (optional) add(null to stringResource(Res.string.group_setup_not_defined))
            values.forEach { value -> add(value to labelFor(value)) }
        }
        options.chunked(3).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
            ) {
                rowOptions.forEach { (value, text) ->
                    val isSelected = selected == value
                    SaqzButton(
                        text,
                        { onSelect(value) },
                        variant = if (isSelected) SaqzButtonVariant.Primary else SaqzButtonVariant.Secondary,
                        enabled = enabled,
                        labelStyle = SaqzTheme.typography.caption,
                        modifier = Modifier.weight(1f).semantics { semanticsSelected = isSelected },
                    )
                }
                repeat(3 - rowOptions.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        errorText?.let { Text(it, style = SaqzTheme.typography.caption, color = SaqzTheme.colors.errorForeground) }
    }
}

@Composable
private fun VenueEditor(
    venue: GroupVenueForm?,
    editable: Boolean,
    state: GroupSetupState,
    onIntent: (GroupSetupIntent) -> Unit,
    showAddAction: Boolean,
) {
    if (venue == null) {
        if (editable && showAddAction) AddVenueButton(onIntent, Modifier.fillMaxWidth())
        return
    }
    Text(stringResource(Res.string.group_setup_venue), style = SaqzTheme.typography.bodyStrong, color = SaqzTheme.colors.textPrimary)
    SetupInput(venue.name, Res.string.group_setup_venue_name, editable, error(state, "defaultVenue.name")) {
        onIntent(GroupSetupIntent.UpdateVenue(venue.copy(name = it)))
    }
    SetupInput(venue.address, Res.string.group_setup_venue_address, editable, error(state, "defaultVenue.address")) {
        onIntent(GroupSetupIntent.UpdateVenue(venue.copy(address = it)))
    }
    SetupInput(venue.court.orEmpty(), Res.string.group_setup_venue_court, editable) {
        onIntent(GroupSetupIntent.UpdateVenue(venue.copy(court = it)))
    }
    if (editable) SaqzButton(
        stringResource(Res.string.group_setup_remove_venue),
        { onIntent(GroupSetupIntent.UpdateVenue(null)) },
        variant = SaqzButtonVariant.Ghost,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SlotEditors(
    slots: List<GroupRegularSlotForm>,
    editable: Boolean,
    state: GroupSetupState,
    onIntent: (GroupSetupIntent) -> Unit,
    showAddAction: Boolean,
) {
    slots.forEachIndexed { index, slot ->
        Text(stringResource(Res.string.group_setup_slot_number, index + 1), style = SaqzTheme.typography.bodyStrong, color = SaqzTheme.colors.textPrimary)
        ChoiceField(
            label = Res.string.group_setup_weekday,
            values = GroupWeekday.entries,
            selected = slot.weekday,
            enabled = editable,
            labelFor = { weekdayLabel(it) },
            onSelect = { value -> value?.let { onIntent(GroupSetupIntent.UpdateSlots(slots.replace(index, slot.copy(weekday = it)))) } },
        )
        SetupInput(slot.startTime, Res.string.group_setup_start_time, editable, error(state, "regularSlots[$index].startTime")) {
            onIntent(GroupSetupIntent.UpdateSlots(slots.replace(index, slot.copy(startTime = it))))
        }
        SetupInput(slot.durationMinutes.toString(), Res.string.group_setup_duration, editable, error(state, "regularSlots[$index].durationMinutes")) {
            onIntent(GroupSetupIntent.UpdateSlots(slots.replace(index, slot.copy(durationMinutes = it.toIntOrNull() ?: 0))))
        }
        if (editable) SaqzButton(
            stringResource(Res.string.group_setup_remove_slot),
            { onIntent(GroupSetupIntent.UpdateSlots(slots.filterIndexed { slotIndex, _ -> slotIndex != index })) },
            variant = SaqzButtonVariant.Ghost,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (editable && (showAddAction || slots.isNotEmpty())) AddSlotButton(slots, onIntent, Modifier.fillMaxWidth())
}

@Composable
private fun AddVenueButton(onIntent: (GroupSetupIntent) -> Unit, modifier: Modifier) = SaqzButton(
    stringResource(Res.string.group_setup_add_venue),
    { onIntent(GroupSetupIntent.UpdateVenue(GroupVenueForm(name = "", address = ""))) },
    variant = SaqzButtonVariant.Secondary,
    labelStyle = SaqzTheme.typography.caption,
    modifier = modifier.testTag(GroupSetupTags.AddVenue),
)

@Composable
private fun AddSlotButton(
    slots: List<GroupRegularSlotForm>,
    onIntent: (GroupSetupIntent) -> Unit,
    modifier: Modifier,
) = SaqzButton(
    stringResource(Res.string.group_setup_add_slot),
    {
        onIntent(
            GroupSetupIntent.UpdateSlots(
                slots + GroupRegularSlotForm(weekday = GroupWeekday.MONDAY, startTime = "", durationMinutes = 60),
            ),
        )
    },
    variant = SaqzButtonVariant.Secondary,
    labelStyle = SaqzTheme.typography.caption,
    modifier = modifier.testTag(GroupSetupTags.AddSlot),
)

@Composable
private fun FriendlyTimeZoneSelector(
    enabled: Boolean,
    error: String?,
    onIntent: (GroupSetupIntent) -> Unit,
) {
    Column(Modifier.testTag(GroupSetupTags.TimeZone), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
        Text(stringResource(Res.string.group_setup_timezone_region), style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
        listOf(
            Res.string.group_setup_timezone_sao_paulo to "America/Sao_Paulo",
            Res.string.group_setup_timezone_manaus to "America/Manaus",
            Res.string.group_setup_timezone_recife to "America/Recife",
        ).forEach { (label, identifier) ->
            SaqzButton(
                stringResource(label),
                { onIntent(GroupSetupIntent.SelectFallbackTimeZone(identifier)) },
                variant = SaqzButtonVariant.Secondary,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let { Text(it, style = SaqzTheme.typography.caption, color = SaqzTheme.colors.errorForeground) }
    }
}

@Composable
private fun Notice(message: String) = SaqzCard(Modifier.fillMaxWidth()) {
    Text(message, style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary)
}

@Composable private fun modalityLabel(value: GroupModality) = stringResource(
    when (value) {
        GroupModality.COURT_VOLLEYBALL -> Res.string.group_setup_modality_court
        GroupModality.BEACH_VOLLEYBALL -> Res.string.group_setup_modality_beach
        GroupModality.FOOTVOLLEY -> Res.string.group_setup_modality_footvolley
    },
)

@Composable private fun compositionLabel(value: GroupComposition) = stringResource(
    when (value) {
        GroupComposition.WOMEN -> Res.string.group_setup_composition_women
        GroupComposition.MEN -> Res.string.group_setup_composition_men
        GroupComposition.MIXED -> Res.string.group_setup_composition_mixed
    },
)

@Composable private fun levelLabel(value: GroupLevel) = stringResource(
    when (value) {
        GroupLevel.BEGINNER -> Res.string.group_setup_level_beginner
        GroupLevel.INTERMEDIATE -> Res.string.group_setup_level_intermediate
        GroupLevel.ADVANCED -> Res.string.group_setup_level_advanced
        GroupLevel.MIXED_LEVELS -> Res.string.group_setup_level_mixed
        GroupLevel.CUSTOM -> Res.string.group_setup_custom
    },
)

@Composable private fun playStyleLabel(value: GroupPlayStyle) = stringResource(
    when (value) {
        GroupPlayStyle.SIX_ZERO -> Res.string.group_setup_style_six_zero
        GroupPlayStyle.FOUR_TWO -> Res.string.group_setup_style_four_two
        GroupPlayStyle.FIVE_ONE -> Res.string.group_setup_style_five_one
        GroupPlayStyle.CUSTOM -> Res.string.group_setup_custom
    },
)

@Composable private fun weekdayLabel(value: GroupWeekday) = stringResource(
    when (value) {
        GroupWeekday.MONDAY -> Res.string.group_setup_monday
        GroupWeekday.TUESDAY -> Res.string.group_setup_tuesday
        GroupWeekday.WEDNESDAY -> Res.string.group_setup_wednesday
        GroupWeekday.THURSDAY -> Res.string.group_setup_thursday
        GroupWeekday.FRIDAY -> Res.string.group_setup_friday
        GroupWeekday.SATURDAY -> Res.string.group_setup_saturday
        GroupWeekday.SUNDAY -> Res.string.group_setup_sunday
    },
)

@Composable private fun error(state: GroupSetupState, key: String): String? =
    state.fieldErrors[key]?.firstOrNull()?.let { stringResource(Res.string.group_setup_invalid_field) }

private fun errorLabel(error: GroupSetupError): StringResource = when (error) {
    GroupSetupError.UNAVAILABLE -> Res.string.group_setup_unavailable
    GroupSetupError.NOT_FOUND -> Res.string.group_setup_not_found
    GroupSetupError.FORBIDDEN -> Res.string.group_setup_forbidden
    GroupSetupError.DRAFT_UNAVAILABLE -> Res.string.group_setup_draft_unavailable
}

private fun String.toNullableInt(): Int? = trim().takeIf(String::isNotEmpty)?.toIntOrNull()

internal fun parseBrlCents(raw: String): Long? {
    val normalized = raw.trim().removePrefix("R$").trim().replace(".", "")
    if (normalized.isEmpty()) return null
    val parts = normalized.split(',')
    if (parts.size > 2 || parts[0].isEmpty() || parts.any { part -> part.any { !it.isDigit() } }) return null
    val reais = parts[0].toLongOrNull() ?: return null
    val fraction = when (val decimal = parts.getOrNull(1)) {
        null -> 0L
        "" -> 0L
        else -> if (decimal.length <= 2) decimal.padEnd(2, '0').toLongOrNull() ?: return null else return null
    }
    if (reais > (Long.MAX_VALUE - fraction) / 100) return null
    return reais * 100 + fraction
}

internal fun formatBrlInput(cents: Long?): String {
    if (cents == null) return ""
    val whole = cents / 100
    val digits = whole.toString()
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return "$grouped,${(cents % 100).toString().padStart(2, '0')}"
}

private fun <T> List<T>.replace(index: Int, value: T): List<T> = mapIndexed { current, item -> if (current == index) value else item }

@Preview
@Composable
private fun GroupSetupScreenPreview() = SaqzTheme {
    GroupSetupScreen(
        state = GroupSetupState(GroupSetupMode.CREATE, GroupSetupForm(), "preview"),
        onIntent = {},
    )
}
