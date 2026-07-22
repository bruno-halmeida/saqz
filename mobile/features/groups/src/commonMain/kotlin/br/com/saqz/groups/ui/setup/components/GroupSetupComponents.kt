package br.com.saqz.groups.ui.setup.components

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
import br.com.saqz.core.common.formatting.formatBrlPlain
import br.com.saqz.core.common.formatting.parseBrlToCents
import br.com.saqz.core.common.formatting.sanitizeBrlInput
import br.com.saqz.designsystem.component.SaqzBottomSheet
import br.com.saqz.designsystem.component.SaqzButton
import br.com.saqz.designsystem.component.SaqzButtonVariant
import br.com.saqz.designsystem.component.SaqzInput
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.group.GroupRegularSlot
import br.com.saqz.groups.domain.group.GroupSetupForm
import br.com.saqz.groups.domain.group.GroupVenue
import br.com.saqz.groups.domain.group.GroupWeekday
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupLevel
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.domain.group.GroupPlayStyle
import br.com.saqz.groups.domain.photo.GroupPhotoPreviewHandle
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

import br.com.saqz.groups.ui.setup.*
@Composable
internal fun GroupSetupTopBar(mode: GroupSetupMode, onBack: () -> Unit, onMoreOptions: () -> Unit) {
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
internal fun TopBarAction(resource: DrawableResource, description: String, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier.size(48.dp).clickable(onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        MaterialIcon(resource, SaqzTheme.colors.textPrimary, 24.dp)
    }
}

@Composable
internal fun SetupSection(title: StringResource, description: StringResource, tag: String, content: @Composable () -> Unit) {
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
internal fun SetupInput(
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
internal fun <T> SegmentedChoice(
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
internal fun SelectorField(label: StringResource, value: String, enabled: Boolean, tag: String? = null, onClick: () -> Unit) {
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
internal fun RoutineAction(icon: DrawableResource, label: StringResource, tag: String, enabled: Boolean, onClick: () -> Unit) {
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
internal fun SummaryRow(summary: String, enabled: Boolean, onEdit: () -> Unit) {
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
internal fun VenueEditor(
    venue: GroupVenue,
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
internal fun SlotEditors(
    slots: List<GroupRegularSlot>,
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
                    options = GroupWeekday.entries.map { it to weekdayLabel(it.name) },
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
internal fun <T> InlineChoice(label: StringResource, options: List<Pair<T, String>>, selected: T, enabled: Boolean, onSelect: (T) -> Unit) {
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
internal fun CapacityStepper(value: Int, minimum: Int, maximum: Int, enabled: Boolean, error: String?, onChange: (Int) -> Unit) {
    val capacityDescription = stringResource(Res.string.group_setup_capacity)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.group_setup_capacity), style = SaqzTheme.typography.caption.copy(letterSpacing = 0.sp), color = SaqzTheme.colors.textSecondary)
        Row(
            Modifier.width(200.dp).heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp))
                .border(1.dp, if (error == null) SaqzTheme.colors.primary else SaqzTheme.colors.errorForeground, RoundedCornerShape(10.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton(Res.drawable.material_remove, capacityDescription, enabled && value > minimum) { onChange(value - 1) }
            BasicTextField(
                value = value.toString(),
                onValueChange = { raw -> raw.filter(Char::isDigit).toIntOrNull()?.let(onChange) },
                enabled = enabled,
                singleLine = true,
                textStyle = SaqzTheme.typography.bodyStrong.copy(color = SaqzTheme.colors.textPrimary, textAlign = TextAlign.Center, letterSpacing = 0.sp),
                modifier = Modifier.weight(1f).semantics { contentDescription = capacityDescription }.testTag(GroupSetupTags.CapacityValue),
            )
            StepperButton(Res.drawable.material_add, capacityDescription, enabled && value < maximum) { onChange(value + 1) }
        }
        error?.let { Text(it, style = SaqzTheme.typography.caption, color = SaqzTheme.colors.errorForeground) }
    }
}

@Composable
internal fun StepperButton(icon: DrawableResource, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { MaterialIcon(icon, if (enabled) SaqzTheme.colors.primary else SaqzTheme.colors.disabledForeground, 20.dp) }
}

@Composable
internal fun FeeEditor(cents: Long?, label: StringResource, enabled: Boolean, error: String?, onChange: (Long?) -> Unit) {
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
internal fun MoneyInput(cents: Long, label: StringResource, enabled: Boolean, error: String?, onChange: (Long?) -> Unit) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(if (cents == 0L) "" else formatBrlPlain(cents))) }
    val external = if (cents == 0L) "" else formatBrlPlain(cents)
    LaunchedEffect(cents) {
        if ((parseBrlToCents(fieldValue.text) ?: 0L) != cents) {
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
                onChange(parseBrlToCents(sanitized) ?: 0)
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
internal fun MonthlyToggle(form: GroupSetupForm, editable: Boolean, state: GroupSetupState, defaultMonthlyDueDay: Int, onIntent: (GroupSetupIntent) -> Unit, onDueDay: () -> Unit) {
    val monthlyFeeCents = form.monthlyFeeCents
    val active = monthlyFeeCents != null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(Res.string.group_setup_monthly_question), style = SaqzTheme.typography.bodyStrong.copy(letterSpacing = 0.sp), color = SaqzTheme.colors.textPrimary)
            Text(stringResource(if (active) Res.string.group_setup_monthly_yes else Res.string.group_setup_monthly_no), style = SaqzTheme.typography.caption.copy(letterSpacing = 0.sp), color = SaqzTheme.colors.textSecondary)
        }
        Switch(
            checked = active,
            onCheckedChange = { checked -> onIntent(GroupSetupIntent.UpdateMonthlyFee(if (checked) 0 else null, if (checked) defaultMonthlyDueDay else null)) },
            enabled = editable,
            colors = SwitchDefaults.colors(checkedThumbColor = SaqzTheme.colors.onPrimary, checkedTrackColor = SaqzTheme.colors.primary),
            modifier = Modifier.testTag(GroupSetupTags.MonthlySwitch),
        )
    }
    if (active) {
        MoneyInput(monthlyFeeCents, Res.string.group_setup_monthly_fee, editable, error(state, "monthlyFeeCents")) {
            onIntent(GroupSetupIntent.UpdateMonthlyFee(it ?: 0, form.monthlyDueDay ?: defaultMonthlyDueDay))
        }
        SelectorField(
            label = Res.string.group_setup_due_day,
            value = stringResource(Res.string.group_setup_due_day_value, form.monthlyDueDay ?: defaultMonthlyDueDay),
            enabled = editable,
            onClick = onDueDay,
        )
    }
}

@Composable
internal fun <T> SelectionSheet(title: String, options: List<Pair<T?, String>>, selected: T?, onClose: () -> Unit, onSelect: (T?) -> Unit) {
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
internal fun StickySubmit(state: GroupSetupState, onSubmit: () -> Unit) {
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
internal fun FriendlyTimeZoneSelector(editable: Boolean, error: String?, onIntent: (GroupSetupIntent) -> Unit) {
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
internal fun Notice(message: String) {
    Text(
        message,
        style = SaqzTheme.typography.caption.copy(letterSpacing = 0.sp),
        color = SaqzTheme.colors.textPrimary,
        modifier = Modifier.fillMaxWidth().background(SaqzTheme.colors.infoSurface, RoundedCornerShape(10.dp)).padding(12.dp),
    )
}

@Composable
internal fun SetupButton(
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
internal fun MaterialIcon(resource: DrawableResource, tint: Color, size: androidx.compose.ui.unit.Dp) {
    Image(painterResource(resource), null, colorFilter = ColorFilter.tint(tint), modifier = Modifier.size(size))
}
