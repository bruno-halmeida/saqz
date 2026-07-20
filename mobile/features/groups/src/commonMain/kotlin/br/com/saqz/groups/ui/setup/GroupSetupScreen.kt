package br.com.saqz.groups.ui.setup

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected as semanticsSelected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.designsystem.component.SaqzBottomSheet
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzInput
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupVenueForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.port.GroupPhotoPreviewHandle
import br.com.saqz.groups.presentation.photo.GroupPhotoIntent
import br.com.saqz.groups.presentation.photo.GroupPhotoState
import br.com.saqz.groups.presentation.setup.GroupSetupError
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.setup.newGroupDefaults
import br.com.saqz.groups.resources.*
import br.com.saqz.groups.ui.photo.GroupPhotoEditor
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

enum class GroupSetupAccess { ORGANIZER, ATHLETE }

internal object GroupSetupTags {
    const val Screen = "group-setup-screen"
    const val Profile = "group-setup-profile"
    const val Sports = "group-setup-sports"
    const val GameDefaults = "group-setup-game-defaults"
    const val FinanceDefaults = "group-setup-finance-defaults"
    const val Submit = "group-setup-submit"
    const val AddVenue = "group-setup-add-venue"
    const val AddSlot = "group-setup-add-slot"
    const val TimeZone = "group-setup-timezone"
    const val Header = "group-setup-header"
    const val Back = "group-setup-back"
    const val More = "group-setup-more"
    const val Title = "group-setup-title"
    const val LevelSelector = "group-setup-level-selector"
    const val PlayStyleSelector = "group-setup-play-style-selector"
    const val ConfirmationSelector = "group-setup-confirmation-selector"
    const val MonthlySwitch = "group-setup-monthly-switch"
    const val CapacityValue = "group-setup-capacity-value"
}

private sealed interface SetupSheet {
    data object Level : SetupSheet
    data object PlayStyle : SetupSheet
    data object Confirmation : SetupSheet
    data object DueDay : SetupSheet
}

@Composable
fun GroupSetupScreen(
    state: GroupSetupState,
    access: GroupSetupAccess = GroupSetupAccess.ORGANIZER,
    photoState: GroupPhotoState,
    onPhotoIntent: (GroupPhotoIntent) -> Unit,
    photoPreview: (@Composable (GroupPhotoPreviewHandle, Modifier) -> Boolean)? = null,
    onBack: () -> Unit = {},
    onMoreOptions: () -> Unit = {},
    onIntent: (GroupSetupIntent) -> Unit,
) {
    val editable = access == GroupSetupAccess.ORGANIZER && state.successGroupId == null
    val form = state.form
    var sheet by remember { mutableStateOf<SetupSheet?>(null) }
    var venueEditing by remember(form.defaultVenue?.id) { mutableStateOf(form.defaultVenue?.name.isNullOrBlank()) }
    var slotsEditing by remember(form.regularSlots.map { it.id }) { mutableStateOf(form.regularSlots.any { it.startTime.isBlank() }) }

    Column(
        Modifier.fillMaxSize()
            .background(SaqzTheme.colors.background)
            .imePadding()
            .testTag(GroupSetupTags.Screen),
    ) {
        GroupSetupTopBar(state.mode, onBack, onMoreOptions)
        Column(
            Modifier.weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                stringResource(Res.string.group_setup_hint),
                style = SaqzTheme.typography.caption.copy(letterSpacing = 0.sp),
                color = SaqzTheme.colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.mode == GroupSetupMode.EDIT && (form.modality == null || form.composition == null)) {
                Notice(stringResource(Res.string.group_setup_incomplete))
            }

            SetupSection(
                Res.string.group_setup_profile_section,
                Res.string.group_setup_profile_description,
                GroupSetupTags.Profile,
            ) {
                GroupPhotoEditor(
                    state = photoState,
                    groupName = form.name,
                    canEdit = editable,
                    optional = state.mode == GroupSetupMode.CREATE,
                    deferUpload = state.mode == GroupSetupMode.CREATE && state.successGroupId == null,
                    onIntent = onPhotoIntent,
                    onPrepared = { onIntent(GroupSetupIntent.SetPhotoPending(it)) },
                    onReloadTarget = { onIntent(GroupSetupIntent.ReloadConflict) },
                    preview = photoPreview,
                    sourceActionBorderColor = SaqzTheme.colors.primary,
                    compactIdle = true,
                    prepared = state.photoPending,
                )
                SetupInput(
                    value = form.name,
                    label = Res.string.group_setup_name,
                    enabled = editable,
                    error = error(state, "name"),
                    placeholder = Res.string.group_setup_name_placeholder,
                    badge = Res.string.group_setup_required,
                ) { onIntent(GroupSetupIntent.UpdateName(it)) }
                SetupInput(
                    value = form.description.orEmpty(),
                    label = Res.string.group_setup_description,
                    enabled = editable,
                    placeholder = Res.string.group_setup_description_placeholder,
                    singleLine = false,
                    minLines = 3,
                ) { onIntent(GroupSetupIntent.UpdateDescription(it)) }
                SetupInput(
                    value = form.city.orEmpty(),
                    label = Res.string.group_setup_city,
                    enabled = editable,
                    placeholder = Res.string.group_setup_city_placeholder,
                    leadingIcon = Res.drawable.material_location_on,
                ) { onIntent(GroupSetupIntent.UpdateCity(it)) }
            }

            SetupSection(
                Res.string.group_setup_sports_section,
                Res.string.group_setup_sports_description,
                GroupSetupTags.Sports,
            ) {
                SegmentedChoice(
                    label = Res.string.group_setup_modality,
                    values = GroupModality.entries,
                    selected = form.modality,
                    enabled = editable,
                    labelFor = { modalityLabel(it) },
                ) { onIntent(GroupSetupIntent.UpdateModality(it)) }
                SegmentedChoice(
                    label = Res.string.group_setup_composition,
                    values = GroupComposition.entries,
                    selected = form.composition,
                    enabled = editable,
                    labelFor = { compositionLabel(it) },
                ) { onIntent(GroupSetupIntent.UpdateComposition(it)) }
                SelectorField(
                    label = Res.string.group_setup_level,
                    value = form.level?.let { levelLabel(it) } ?: stringResource(Res.string.group_setup_not_defined),
                    enabled = editable,
                    tag = GroupSetupTags.LevelSelector,
                ) { sheet = SetupSheet.Level }
                if (state.showCustomLevel) {
                    SetupInput(form.customLevel.orEmpty(), Res.string.group_setup_custom_level, editable, error(state, "customLevel")) {
                        onIntent(GroupSetupIntent.UpdateCustomLevel(it))
                    }
                }
                if (state.showPlayStyle) {
                    SelectorField(
                        label = Res.string.group_setup_play_style,
                        value = form.playStyle?.let { playStyleLabel(it) } ?: stringResource(Res.string.group_setup_not_defined),
                        enabled = editable,
                        tag = GroupSetupTags.PlayStyleSelector,
                    ) { sheet = SetupSheet.PlayStyle }
                }
                if (state.showCustomPlayStyle) {
                    SetupInput(form.customPlayStyle.orEmpty(), Res.string.group_setup_custom_play_style, editable, error(state, "customPlayStyle")) {
                        onIntent(GroupSetupIntent.UpdateCustomPlayStyle(it))
                    }
                }
                if (state.timezoneSelectionRequired) FriendlyTimeZoneSelector(editable, error(state, "timeZone"), onIntent)
            }

            SetupSection(
                Res.string.group_setup_game_defaults_section,
                Res.string.group_setup_game_defaults_description,
                GroupSetupTags.GameDefaults,
            ) {
                if (form.defaultVenue == null) {
                    RoutineAction(
                        Res.drawable.material_location_on,
                        Res.string.group_setup_add_venue,
                        GroupSetupTags.AddVenue,
                        editable,
                    ) {
                        venueEditing = true
                        onIntent(GroupSetupIntent.UpdateVenue(GroupVenueForm(name = "", address = "")))
                    }
                } else if (!venueEditing && form.defaultVenue.name.isNotBlank()) {
                    SummaryRow(form.defaultVenue.name, editable) { venueEditing = true }
                }
                if (form.defaultVenue != null && venueEditing) {
                    VenueEditor(form.defaultVenue, editable, state, onIntent) { venueEditing = false }
                }

                if (form.regularSlots.isEmpty()) {
                    RoutineAction(
                        Res.drawable.material_calendar,
                        Res.string.group_setup_add_slot,
                        GroupSetupTags.AddSlot,
                        editable,
                    ) {
                        slotsEditing = true
                        onIntent(GroupSetupIntent.UpdateSlots(listOf(newSlot())))
                    }
                } else if (!slotsEditing) {
                    SummaryRow(slotSummary(form.regularSlots), editable) { slotsEditing = true }
                }
                if (form.regularSlots.isNotEmpty() && slotsEditing) {
                    SlotEditors(form.regularSlots, editable, state, onIntent) { slotsEditing = false }
                }

                CapacityStepper(form.defaultCapacity ?: 12, editable, error(state, "defaultCapacity")) {
                    onIntent(GroupSetupIntent.UpdateDefaultCapacity(it))
                }
                SelectorField(
                    label = Res.string.group_setup_confirmation_lead,
                    value = confirmationLabel(form.defaultConfirmationLeadMinutes),
                    enabled = editable,
                    tag = GroupSetupTags.ConfirmationSelector,
                ) { sheet = SetupSheet.Confirmation }
                if (form.defaultConfirmationLeadMinutes != null && form.defaultConfirmationLeadMinutes !in confirmationPresets) {
                    SetupInput(
                        value = formatHours(form.defaultConfirmationLeadMinutes),
                        label = Res.string.group_setup_confirmation_custom_hours,
                        enabled = editable,
                        error = error(state, "defaultConfirmationLeadMinutes"),
                        keyboardType = KeyboardType.Decimal,
                    ) { raw -> onIntent(GroupSetupIntent.UpdateConfirmationLeadMinutes(parseHours(raw))) }
                }
                FeeEditor(
                    cents = form.defaultGameFeeCents,
                    label = Res.string.group_setup_game_fee,
                    enabled = editable,
                    error = error(state, "defaultGameFeeCents"),
                    onChange = { onIntent(GroupSetupIntent.UpdateDefaultGameFeeCents(it)) },
                )
            }

            if (access == GroupSetupAccess.ORGANIZER) {
                SetupSection(
                    Res.string.group_setup_finance_defaults_section,
                    Res.string.group_setup_finance_defaults_description,
                    GroupSetupTags.FinanceDefaults,
                ) {
                    MonthlyToggle(form, editable, state, onIntent) { sheet = SetupSheet.DueDay }
                }
            }

            state.error?.let { Notice(stringResource(errorLabel(it))) }
            if (state.conflict) {
                Notice(stringResource(Res.string.group_setup_conflict))
                SetupButton(
                    stringResource(Res.string.group_setup_reload),
                    { onIntent(GroupSetupIntent.ReloadConflict) },
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.photoRetryAvailable) {
                Notice(stringResource(Res.string.group_setup_photo_failed))
                SetupButton(
                    stringResource(Res.string.group_setup_photo_retry),
                    { onIntent(GroupSetupIntent.RetryPhotoUpload) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.successGroupId != null) Notice(stringResource(Res.string.group_setup_success))
            Spacer(Modifier.height(4.dp))
        }

        if (editable) {
            StickySubmit(state) { onIntent(GroupSetupIntent.Submit) }
        }
    }

    when (sheet) {
        SetupSheet.Level -> SelectionSheet(
            title = stringResource(Res.string.group_setup_level),
            options = buildList {
                add(null to stringResource(Res.string.group_setup_not_defined))
                GroupLevel.entries.forEach { add(it to levelLabel(it)) }
            },
            selected = form.level,
            onClose = { sheet = null },
        ) {
            onIntent(GroupSetupIntent.UpdateLevel(it))
            sheet = null
        }
        SetupSheet.PlayStyle -> SelectionSheet(
            title = stringResource(Res.string.group_setup_play_style),
            options = buildList {
                add(null to stringResource(Res.string.group_setup_not_defined))
                GroupPlayStyle.entries.forEach { add(it to playStyleLabel(it)) }
            },
            selected = form.playStyle,
            onClose = { sheet = null },
        ) {
            onIntent(GroupSetupIntent.UpdatePlayStyle(it))
            sheet = null
        }
        SetupSheet.Confirmation -> SelectionSheet(
            title = stringResource(Res.string.group_setup_confirmation_lead),
            options = confirmationOptions(),
            selected = form.defaultConfirmationLeadMinutes?.takeIf { it in confirmationPresets },
            onClose = { sheet = null },
        ) { minutes ->
            onIntent(GroupSetupIntent.UpdateConfirmationLeadMinutes(minutes ?: 0))
            sheet = null
        }
        SetupSheet.DueDay -> SelectionSheet(
            title = stringResource(Res.string.group_setup_due_day),
            options = (1..28).map { it to stringResource(Res.string.group_setup_due_day_value, it) },
            selected = form.monthlyDueDay,
            onClose = { sheet = null },
        ) { day ->
            onIntent(GroupSetupIntent.UpdateMonthlyFee(form.monthlyFeeCents, day))
            sheet = null
        }
        null -> Unit
    }
}

@Composable
private fun GroupSetupTopBar(mode: GroupSetupMode, onBack: () -> Unit, onMoreOptions: () -> Unit) {
    val title = stringResource(if (mode == GroupSetupMode.CREATE) Res.string.group_setup_create_title else Res.string.group_setup_edit_title)
    Box(
        Modifier.fillMaxWidth().height(56.dp).background(SaqzTheme.colors.surface)
            .border(0.dp, Color.Transparent).testTag(GroupSetupTags.Header),
    ) {
        TopBarAction(
            Res.drawable.material_arrow_back,
            stringResource(Res.string.action_back),
            onBack,
            Modifier.align(Alignment.CenterStart).testTag(GroupSetupTags.Back),
        )
        Text(
            title,
            style = SaqzTheme.typography.bodyStrong.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
            color = SaqzTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp).semantics { heading() }.testTag(GroupSetupTags.Title),
        )
        TopBarAction(
            Res.drawable.material_more_horiz,
            stringResource(Res.string.group_setup_more_options),
            onMoreOptions,
            Modifier.align(Alignment.CenterEnd).testTag(GroupSetupTags.More),
        )
    }
}

@Composable
private fun TopBarAction(resource: DrawableResource, description: String, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier.size(48.dp).clickable(onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        MaterialIcon(resource, SaqzTheme.colors.textPrimary, 24.dp)
    }
}

@Composable
private fun SetupSection(title: StringResource, description: StringResource, tag: String, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(SaqzTheme.colors.surface, shape)
            .border(1.dp, SaqzTheme.colors.hairline, shape).padding(16.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(title),
                style = SaqzTheme.typography.lead.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
                color = SaqzTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(description),
                style = SaqzTheme.typography.caption.copy(letterSpacing = 0.sp),
                color = SaqzTheme.colors.textSecondary,
            )
        }
        content()
    }
}

@Composable
private fun SetupInput(
    value: String,
    label: StringResource,
    enabled: Boolean,
    error: String? = null,
    placeholder: StringResource? = null,
    badge: StringResource? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
    leadingIcon: DrawableResource? = null,
    onChange: (String) -> Unit,
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, selection = TextRange(value.length))) }
    LaunchedEffect(value) {
        if (fieldValue.text != value) fieldValue = fieldValue.copy(text = value, selection = TextRange(value.length), composition = null)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(label), style = SaqzTheme.typography.caption.copy(letterSpacing = 0.sp), color = SaqzTheme.colors.textSecondary)
            badge?.let {
                Text(
                    stringResource(it),
                    style = SaqzTheme.typography.caption.copy(letterSpacing = 0.sp),
                    color = SaqzTheme.colors.primary,
                )
            }
        }
        SaqzInput(
            value = fieldValue,
            onValueChange = {
                fieldValue = it
                onChange(it.text)
            },
            label = stringResource(label),
            errorText = error,
            enabled = enabled,
            placeholder = placeholder?.let { stringResource(it) },
            keyboardType = keyboardType,
            singleLine = singleLine,
            minLines = minLines,
            showLabel = false,
            leadingContent = leadingIcon?.let { icon ->
                { MaterialIcon(icon, SaqzTheme.colors.textSecondary, 20.dp) }
            },
        )
    }
}

@Composable
private fun <T> SegmentedChoice(
    label: StringResource,
    values: List<T>,
    selected: T?,
    enabled: Boolean,
    labelFor: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(label), style = SaqzTheme.typography.caption.copy(letterSpacing = 0.sp), color = SaqzTheme.colors.textSecondary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            values.forEach { value ->
                val active = value == selected
                val shape = RoundedCornerShape(10.dp)
                Row(
                    Modifier.weight(1f).heightIn(min = 56.dp).clip(shape)
                        .background(if (active) SaqzTheme.colors.primary else SaqzTheme.colors.surface)
                        .border(1.dp, SaqzTheme.colors.primary, shape)
                        .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(value) }
                        .semantics { semanticsSelected = active }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        labelFor(value),
                        style = SaqzTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
                        color = if (active) SaqzTheme.colors.onPrimary else SaqzTheme.colors.textPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (active) {
                        Spacer(Modifier.width(3.dp))
                        MaterialIcon(Res.drawable.material_check, SaqzTheme.colors.onPrimary, 16.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectorField(label: StringResource, value: String, enabled: Boolean, tag: String? = null, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(label), style = SaqzTheme.typography.caption.copy(letterSpacing = 0.sp), color = SaqzTheme.colors.textSecondary)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp))
                .background(SaqzTheme.colors.surface)
                .border(1.dp, SaqzTheme.colors.primary, RoundedCornerShape(10.dp))
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .then(tag?.let { Modifier.testTag(it) } ?: Modifier)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, style = SaqzTheme.typography.body.copy(letterSpacing = 0.sp), color = SaqzTheme.colors.textPrimary, modifier = Modifier.weight(1f))
            MaterialIcon(Res.drawable.material_expand_more, SaqzTheme.colors.textSecondary, 20.dp)
        }
    }
}

@Composable
private fun RoutineAction(icon: DrawableResource, label: StringResource, tag: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(10.dp))
            .background(SaqzTheme.colors.surface)
            .border(1.dp, SaqzTheme.colors.primary, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MaterialIcon(icon, SaqzTheme.colors.primary, 20.dp)
        Text(stringResource(label), style = SaqzTheme.typography.bodyStrong.copy(letterSpacing = 0.sp), color = SaqzTheme.colors.textPrimary)
    }
}

@Composable
private fun SummaryRow(summary: String, enabled: Boolean, onEdit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).background(SaqzTheme.colors.surfacePearl, RoundedCornerShape(10.dp)).padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(summary, style = SaqzTheme.typography.body.copy(letterSpacing = 0.sp), color = SaqzTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        SetupButton(
            stringResource(Res.string.group_setup_edit),
            onEdit,
            variant = SaqzButtonVariant.Ghost,
            enabled = enabled,
        )
    }
}

@Composable
private fun VenueEditor(
    venue: GroupVenueForm,
    editable: Boolean,
    state: GroupSetupState,
    onIntent: (GroupSetupIntent) -> Unit,
    onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SetupInput(venue.name, Res.string.group_setup_venue_name, editable, error(state, "defaultVenue.name")) {
            onIntent(GroupSetupIntent.UpdateVenue(venue.copy(name = it)))
        }
        SetupInput(venue.address, Res.string.group_setup_venue_address, editable, error(state, "defaultVenue.address")) {
            onIntent(GroupSetupIntent.UpdateVenue(venue.copy(address = it)))
        }
        SetupInput(venue.court.orEmpty(), Res.string.group_setup_venue_court, editable) {
            onIntent(GroupSetupIntent.UpdateVenue(venue.copy(court = it)))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SetupButton(stringResource(Res.string.group_setup_remove_venue), { onIntent(GroupSetupIntent.UpdateVenue(null)) }, variant = SaqzButtonVariant.Ghost, enabled = editable)
            SetupButton(stringResource(Res.string.group_setup_done), onDone, variant = SaqzButtonVariant.Ghost, enabled = editable && venue.name.isNotBlank())
        }
    }
}

@Composable
private fun SlotEditors(
    slots: List<GroupRegularSlotForm>,
    editable: Boolean,
    state: GroupSetupState,
    onIntent: (GroupSetupIntent) -> Unit,
    onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        slots.forEachIndexed { index, slot ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.group_setup_slot_number, index + 1), style = SaqzTheme.typography.bodyStrong.copy(letterSpacing = 0.sp), color = SaqzTheme.colors.textPrimary)
                InlineChoice(
                    label = Res.string.group_setup_weekday,
                    options = GroupWeekday.entries.map { it to weekdayLabel(it) },
                    selected = slot.weekday,
                    enabled = editable,
                ) { onIntent(GroupSetupIntent.UpdateSlots(slots.replace(index, slot.copy(weekday = it)))) }
                SetupInput(slot.startTime, Res.string.group_setup_start_time, editable, error(state, "regularSlots[$index].startTime")) {
                    onIntent(GroupSetupIntent.UpdateSlots(slots.replace(index, slot.copy(startTime = it))))
                }
                SetupInput(slot.durationMinutes.toString(), Res.string.group_setup_duration, editable, error(state, "regularSlots[$index].durationMinutes"), keyboardType = KeyboardType.Number) {
                    onIntent(GroupSetupIntent.UpdateSlots(slots.replace(index, slot.copy(durationMinutes = it.toIntOrNull() ?: 0))))
                }
                SetupButton(
                    stringResource(Res.string.group_setup_remove_slot),
                    { onIntent(GroupSetupIntent.UpdateSlots(slots.filterIndexed { slotIndex, _ -> slotIndex != index })) },
                    variant = SaqzButtonVariant.Ghost,
                    enabled = editable,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SetupButton(
                stringResource(Res.string.group_setup_add_slot),
                { onIntent(GroupSetupIntent.UpdateSlots(slots + newSlot())) },
                variant = SaqzButtonVariant.Secondary,
                enabled = editable,
                modifier = Modifier.weight(1f).testTag(GroupSetupTags.AddSlot),
            )
            SetupButton(stringResource(Res.string.group_setup_done), onDone, variant = SaqzButtonVariant.Ghost, enabled = editable)
        }
    }
}

@Composable
private fun <T> InlineChoice(label: StringResource, options: List<Pair<T, String>>, selected: T, enabled: Boolean, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(label), style = SaqzTheme.typography.caption.copy(letterSpacing = 0.sp), color = SaqzTheme.colors.textSecondary)
        options.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (value, text) ->
                    SetupButton(
                        text,
                        { onSelect(value) },
                        variant = if (value == selected) SaqzButtonVariant.Primary else SaqzButtonVariant.Secondary,
                        enabled = enabled,
                        modifier = Modifier.weight(1f).semantics { semanticsSelected = value == selected },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CapacityStepper(value: Int, enabled: Boolean, error: String?, onChange: (Int) -> Unit) {
    val capacityDescription = stringResource(Res.string.group_setup_capacity)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.group_setup_capacity), style = SaqzTheme.typography.caption.copy(letterSpacing = 0.sp), color = SaqzTheme.colors.textSecondary)
        Row(
            Modifier.width(200.dp).heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp))
                .border(1.dp, if (error == null) SaqzTheme.colors.primary else SaqzTheme.colors.errorForeground, RoundedCornerShape(10.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton(Res.drawable.material_remove, capacityDescription, enabled && value > 2) { onChange(value - 1) }
            BasicTextField(
                value = value.toString(),
                onValueChange = { raw -> raw.filter(Char::isDigit).toIntOrNull()?.let(onChange) },
                enabled = enabled,
                singleLine = true,
                textStyle = SaqzTheme.typography.bodyStrong.copy(color = SaqzTheme.colors.textPrimary, textAlign = TextAlign.Center, letterSpacing = 0.sp),
                modifier = Modifier.weight(1f).semantics { contentDescription = capacityDescription }.testTag(GroupSetupTags.CapacityValue),
            )
            StepperButton(Res.drawable.material_add, capacityDescription, enabled && value < 100) { onChange(value + 1) }
        }
        error?.let { Text(it, style = SaqzTheme.typography.caption, color = SaqzTheme.colors.errorForeground) }
    }
}

@Composable
private fun StepperButton(icon: DrawableResource, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { MaterialIcon(icon, if (enabled) SaqzTheme.colors.primary else SaqzTheme.colors.disabledForeground, 20.dp) }
}

@Composable
private fun FeeEditor(cents: Long?, label: StringResource, enabled: Boolean, error: String?, onChange: (Long?) -> Unit) {
    if (cents == null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(label), style = SaqzTheme.typography.caption.copy(letterSpacing = 0.sp), color = SaqzTheme.colors.textSecondary)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.group_setup_no_charge), style = SaqzTheme.typography.body.copy(letterSpacing = 0.sp), color = SaqzTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                SetupButton(stringResource(Res.string.group_setup_add_value), { onChange(0) }, variant = SaqzButtonVariant.Ghost, enabled = enabled)
            }
        }
    } else {
        MoneyInput(cents, label, enabled, error) { onChange(it) }
        SetupButton(stringResource(Res.string.group_setup_remove_value), { onChange(null) }, variant = SaqzButtonVariant.Ghost, enabled = enabled)
    }
}

@Composable
private fun MoneyInput(cents: Long, label: StringResource, enabled: Boolean, error: String?, onChange: (Long?) -> Unit) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(if (cents == 0L) "" else formatBrlInput(cents))) }
    val external = if (cents == 0L) "" else formatBrlInput(cents)
    LaunchedEffect(cents) {
        if ((parseBrlCents(fieldValue.text) ?: 0L) != cents) {
            fieldValue = TextFieldValue(external, TextRange(external.length))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(label), style = SaqzTheme.typography.caption.copy(letterSpacing = 0.sp), color = SaqzTheme.colors.textSecondary)
        SaqzInput(
            value = fieldValue,
            onValueChange = {
                val sanitized = sanitizeBrlInput(it.text)
                fieldValue = it.copy(
                    text = sanitized,
                    selection = TextRange(
                        it.selection.start.coerceAtMost(sanitized.length),
                        it.selection.end.coerceAtMost(sanitized.length),
                    ),
                    composition = null,
                )
                onChange(parseBrlCents(sanitized) ?: 0)
            },
            label = stringResource(label),
            placeholder = "0,00",
            keyboardType = KeyboardType.Decimal,
            enabled = enabled,
            errorText = error,
            leadingContent = { Text("R$", style = SaqzTheme.typography.bodyStrong.copy(letterSpacing = 0.sp), color = SaqzTheme.colors.textSecondary) },
            showLabel = false,
        )
    }
}

@Composable
private fun MonthlyToggle(form: GroupSetupForm, editable: Boolean, state: GroupSetupState, onIntent: (GroupSetupIntent) -> Unit, onDueDay: () -> Unit) {
    val active = form.monthlyFeeCents != null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(Res.string.group_setup_monthly_question), style = SaqzTheme.typography.bodyStrong.copy(letterSpacing = 0.sp), color = SaqzTheme.colors.textPrimary)
            Text(stringResource(if (active) Res.string.group_setup_monthly_yes else Res.string.group_setup_monthly_no), style = SaqzTheme.typography.caption.copy(letterSpacing = 0.sp), color = SaqzTheme.colors.textSecondary)
        }
        Switch(
            checked = active,
            onCheckedChange = { checked -> onIntent(GroupSetupIntent.UpdateMonthlyFee(if (checked) 0 else null, if (checked) 10 else null)) },
            enabled = editable,
            colors = SwitchDefaults.colors(checkedThumbColor = SaqzTheme.colors.onPrimary, checkedTrackColor = SaqzTheme.colors.primary),
            modifier = Modifier.testTag(GroupSetupTags.MonthlySwitch),
        )
    }
    if (active) {
        MoneyInput(form.monthlyFeeCents, Res.string.group_setup_monthly_fee, editable, error(state, "monthlyFeeCents")) {
            onIntent(GroupSetupIntent.UpdateMonthlyFee(it ?: 0, form.monthlyDueDay ?: 10))
        }
        SelectorField(
            label = Res.string.group_setup_due_day,
            value = stringResource(Res.string.group_setup_due_day_value, form.monthlyDueDay ?: 10),
            enabled = editable,
            onClick = onDueDay,
        )
    }
}

@Composable
private fun <T> SelectionSheet(title: String, options: List<Pair<T?, String>>, selected: T?, onClose: () -> Unit, onSelect: (T?) -> Unit) {
    SaqzBottomSheet(
        title = title,
        onCloseRequest = onClose,
        dismissOnBackPress = true,
        dismissOnClickOutside = true,
        primaryAction = {
            SetupButton(stringResource(Res.string.action_cancel), onClose, variant = SaqzButtonVariant.Ghost, modifier = Modifier.fillMaxWidth())
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (value, label) ->
                val active = value == selected
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (active) SaqzTheme.colors.infoSurface else Color.Transparent)
                        .border(1.dp, SaqzTheme.colors.primary, RoundedCornerShape(8.dp))
                        .clickable(role = Role.RadioButton) { onSelect(value) }
                        .semantics { semanticsSelected = active }.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = SaqzTheme.typography.body.copy(letterSpacing = 0.sp), color = if (active) SaqzTheme.colors.primary else SaqzTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                    if (active) MaterialIcon(Res.drawable.material_check, SaqzTheme.colors.primary, 20.dp)
                }
            }
        }
    }
}

@Composable
private fun StickySubmit(state: GroupSetupState, onSubmit: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(SaqzTheme.colors.surface)
            .border(1.dp, SaqzTheme.colors.hairline).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        SetupButton(
            stringResource(if (state.mode == GroupSetupMode.CREATE) Res.string.group_setup_create_action else Res.string.group_setup_save_action),
            onSubmit,
            enabled = state.form.name.isNotBlank() && !state.conflict,
            loading = state.isLoading,
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag(GroupSetupTags.Submit),
            leadingContent = { color -> MaterialIcon(Res.drawable.material_group_add, color, 20.dp) },
        )
    }
}

@Composable
private fun FriendlyTimeZoneSelector(editable: Boolean, error: String?, onIntent: (GroupSetupIntent) -> Unit) {
    Column(Modifier.testTag(GroupSetupTags.TimeZone), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.group_setup_timezone_region), style = SaqzTheme.typography.caption.copy(letterSpacing = 0.sp), color = SaqzTheme.colors.textSecondary)
        listOf(
            Res.string.group_setup_timezone_sao_paulo to "America/Sao_Paulo",
            Res.string.group_setup_timezone_manaus to "America/Manaus",
            Res.string.group_setup_timezone_recife to "America/Recife",
        ).forEach { (label, identifier) ->
            SetupButton(stringResource(label), { onIntent(GroupSetupIntent.SelectFallbackTimeZone(identifier)) }, variant = SaqzButtonVariant.Secondary, enabled = editable, modifier = Modifier.fillMaxWidth())
        }
        error?.let { Text(it, style = SaqzTheme.typography.caption, color = SaqzTheme.colors.errorForeground) }
    }
}

@Composable
private fun Notice(message: String) {
    Text(
        message,
        style = SaqzTheme.typography.caption.copy(letterSpacing = 0.sp),
        color = SaqzTheme.colors.textPrimary,
        modifier = Modifier.fillMaxWidth().background(SaqzTheme.colors.infoSurface, RoundedCornerShape(10.dp)).padding(12.dp),
    )
}

@Composable
private fun SetupButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SaqzButtonVariant = SaqzButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingContent: (@Composable (Color) -> Unit)? = null,
) {
    SaqzButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        enabled = enabled,
        loading = loading,
        borderColor = if (enabled && !loading) SaqzTheme.colors.primary else null,
        leadingContent = leadingContent,
    )
}

@Composable
private fun MaterialIcon(resource: DrawableResource, tint: Color, size: androidx.compose.ui.unit.Dp) {
    Image(painterResource(resource), null, colorFilter = ColorFilter.tint(tint), modifier = Modifier.size(size))
}

@Composable private fun modalityLabel(value: GroupModality): String = stringResource(when (value) {
    GroupModality.COURT_VOLLEYBALL -> Res.string.group_setup_modality_court
    GroupModality.BEACH_VOLLEYBALL -> Res.string.group_setup_modality_beach
    GroupModality.FOOTVOLLEY -> Res.string.group_setup_modality_footvolley
})

@Composable private fun compositionLabel(value: GroupComposition): String = stringResource(when (value) {
    GroupComposition.WOMEN -> Res.string.group_setup_composition_women
    GroupComposition.MEN -> Res.string.group_setup_composition_men
    GroupComposition.MIXED -> Res.string.group_setup_composition_mixed
})

@Composable private fun levelLabel(value: GroupLevel): String = stringResource(when (value) {
    GroupLevel.BEGINNER -> Res.string.group_setup_level_beginner
    GroupLevel.INTERMEDIATE -> Res.string.group_setup_level_intermediate
    GroupLevel.ADVANCED -> Res.string.group_setup_level_advanced
    GroupLevel.MIXED_LEVELS -> Res.string.group_setup_level_mixed
    GroupLevel.CUSTOM -> Res.string.group_setup_custom
})

@Composable private fun playStyleLabel(value: GroupPlayStyle): String = stringResource(when (value) {
    GroupPlayStyle.SIX_ZERO -> Res.string.group_setup_style_six_zero
    GroupPlayStyle.FOUR_TWO -> Res.string.group_setup_style_four_two
    GroupPlayStyle.FIVE_ONE -> Res.string.group_setup_style_five_one
    GroupPlayStyle.CUSTOM -> Res.string.group_setup_custom
})

@Composable private fun weekdayLabel(value: GroupWeekday): String = stringResource(when (value) {
    GroupWeekday.MONDAY -> Res.string.group_setup_monday
    GroupWeekday.TUESDAY -> Res.string.group_setup_tuesday
    GroupWeekday.WEDNESDAY -> Res.string.group_setup_wednesday
    GroupWeekday.THURSDAY -> Res.string.group_setup_thursday
    GroupWeekday.FRIDAY -> Res.string.group_setup_friday
    GroupWeekday.SATURDAY -> Res.string.group_setup_saturday
    GroupWeekday.SUNDAY -> Res.string.group_setup_sunday
})

@Composable
private fun confirmationOptions(): List<Pair<Int?, String>> = listOf(
    60 to stringResource(Res.string.group_setup_confirmation_1h),
    180 to stringResource(Res.string.group_setup_confirmation_3h),
    360 to stringResource(Res.string.group_setup_confirmation_6h),
    720 to stringResource(Res.string.group_setup_confirmation_12h),
    1440 to stringResource(Res.string.group_setup_confirmation_1d),
    null to stringResource(Res.string.group_setup_confirmation_custom),
)

@Composable
private fun confirmationLabel(minutes: Int?): String = when (minutes) {
    60 -> stringResource(Res.string.group_setup_confirmation_1h)
    180 -> stringResource(Res.string.group_setup_confirmation_3h)
    360 -> stringResource(Res.string.group_setup_confirmation_6h)
    720 -> stringResource(Res.string.group_setup_confirmation_12h)
    1440 -> stringResource(Res.string.group_setup_confirmation_1d)
    null -> stringResource(Res.string.group_setup_not_defined)
    else -> "${formatHours(minutes)} h antes"
}

private val confirmationPresets = setOf(60, 180, 360, 720, 1440)
private fun formatHours(minutes: Int): String = if (minutes % 60 == 0) (minutes / 60).toString() else (minutes / 60.0).toString().replace('.', ',')
private fun parseHours(value: String): Int? = value.replace(',', '.').toDoubleOrNull()?.let { (it * 60).roundToInt() }
private fun newSlot() = GroupRegularSlotForm(weekday = GroupWeekday.MONDAY, startTime = "", durationMinutes = 60)
private fun slotSummary(slots: List<GroupRegularSlotForm>): String = slots.joinToString(" • ") { "${it.startTime.ifBlank { "--:--" }}" }
private fun <T> List<T>.replace(index: Int, value: T): List<T> = mapIndexed { current, item -> if (current == index) value else item }
private fun error(state: GroupSetupState, key: String): String? = state.fieldErrors[key]?.firstOrNull()?.let { "Revise este campo." }
private fun errorLabel(error: GroupSetupError): StringResource = when (error) {
    GroupSetupError.UNAVAILABLE -> Res.string.group_setup_unavailable
    GroupSetupError.NOT_FOUND -> Res.string.group_setup_not_found
    GroupSetupError.FORBIDDEN -> Res.string.group_setup_forbidden
    GroupSetupError.DRAFT_UNAVAILABLE -> Res.string.group_setup_draft_unavailable
}

internal fun parseBrlCents(value: String): Long? {
    val normalized = value.trim().replace(".", "")
    if (!Regex("\\d{1,8}([,.]\\d{0,2})?").matches(normalized)) return null
    val parts = normalized.replace(',', '.').split('.')
    val reais = parts[0].toLongOrNull() ?: return null
    val decimals = parts.getOrNull(1).orEmpty().padEnd(2, '0').take(2).toLongOrNull() ?: 0L
    return reais * 100 + decimals
}

internal fun sanitizeBrlInput(value: String): String {
    val normalized = when {
        ',' in value -> value.replace(".", "")
        value.count { it == '.' } == 1 && value.substringAfter('.').count(Char::isDigit) <= 2 -> value.replace('.', ',')
        else -> value.replace(".", "")
    }
    val separator = normalized.indexOf(',')
    val reaisSource = if (separator >= 0) normalized.substring(0, separator) else normalized
    val reais = reaisSource.filter(Char::isDigit).take(8)
    if (separator < 0) return reais
    val decimals = normalized.substring(separator + 1).filter(Char::isDigit).take(2)
    return "$reais,$decimals"
}

internal fun formatBrlInput(cents: Long?): String {
    if (cents == null) return ""
    val reais = cents / 100
    val decimals = (cents % 100).toString().padStart(2, '0')
    val grouped = reais.toString().reversed().chunked(3).joinToString(".").reversed()
    return "$grouped,$decimals"
}

@Preview(heightDp = 2000)
@Composable
private fun GroupSetupScreenPreview() = SaqzTheme {
    GroupSetupScreen(
        state = GroupSetupState(GroupSetupMode.CREATE, newGroupDefaults(), "preview"),
        photoState = GroupPhotoState(),
        onPhotoIntent = {},
        onIntent = {},
    )
}
