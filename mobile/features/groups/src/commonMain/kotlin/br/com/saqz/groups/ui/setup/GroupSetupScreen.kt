package br.com.saqz.groups.ui.setup

import br.com.saqz.groups.ui.setup.components.*

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
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupVenueForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
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
internal fun newSlot() = GroupRegularSlotForm(weekday = GroupWeekday.MONDAY, startTime = "", durationMinutes = 60)
private fun slotSummary(slots: List<GroupRegularSlotForm>): String = slots.joinToString(" • ") { "${it.startTime.ifBlank { "--:--" }}" }
internal fun <T> List<T>.replace(index: Int, value: T): List<T> = mapIndexed { current, item -> if (current == index) value else item }
internal fun error(state: GroupSetupState, key: String): String? = state.fieldErrors[key]?.firstOrNull()?.let { "Revise este campo." }
private fun errorLabel(error: GroupSetupError): StringResource = when (error) {
    GroupSetupError.UNAVAILABLE -> Res.string.group_setup_unavailable
    GroupSetupError.NOT_FOUND -> Res.string.group_setup_not_found
    GroupSetupError.FORBIDDEN -> Res.string.group_setup_forbidden
    GroupSetupError.DRAFT_UNAVAILABLE -> Res.string.group_setup_draft_unavailable
}


@Preview()
@Composable
private fun GroupSetupScreenPreview() = SaqzTheme {
    GroupSetupScreen(
        state = GroupSetupState(GroupSetupMode.CREATE, newGroupDefaults(), "preview"),
        photoState = GroupPhotoState(),
        onPhotoIntent = {},
        onIntent = {},
    )
}
