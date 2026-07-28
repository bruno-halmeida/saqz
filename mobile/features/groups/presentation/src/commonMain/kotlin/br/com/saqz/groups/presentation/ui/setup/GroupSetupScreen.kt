package br.com.saqz.groups.presentation.ui.setup

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzOfflineBanner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupComposition
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.model.GroupPlayStyle
import br.com.saqz.groups.model.GroupRegularSlotForm
import br.com.saqz.groups.model.GroupSetupForm
import br.com.saqz.groups.model.GroupVenueForm
import br.com.saqz.groups.model.GroupWeekday
import br.com.saqz.groups.presentation.setup.GroupSetupDefaults
import br.com.saqz.groups.presentation.setup.GroupSetupError
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupSheet
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.setup.validate
import br.com.saqz.groups.presentation.ui.label
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_setup_create_action
import br.com.saqz.groups.resources.group_setup_create_title
import br.com.saqz.groups.resources.group_setup_delete_action
import br.com.saqz.groups.resources.group_setup_edit_title
import br.com.saqz.groups.resources.group_setup_error_composition
import br.com.saqz.groups.resources.group_setup_error_custom_level
import br.com.saqz.groups.resources.group_setup_error_description
import br.com.saqz.groups.resources.group_setup_error_modality
import br.com.saqz.groups.resources.group_setup_error_name
import br.com.saqz.groups.resources.group_setup_error_venue_address
import br.com.saqz.groups.resources.group_setup_error_venue_name
import br.com.saqz.groups.resources.group_setup_save_action
import br.com.saqz.groups.resources.group_system_creating
import br.com.saqz.groups.resources.group_system_offline
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal object GroupSetupTags {
    const val Photo = "group-setup-photo"
    const val Name = "group-setup-name"
    const val Modality = "group-setup-modality"
    const val Composition = "group-setup-composition"
    const val Level = "group-setup-level"
    const val CustomLevel = "group-setup-custom-level"
    const val PlayStyle = "group-setup-play-style"
    const val Description = "group-setup-description"
    const val Capacity = "group-setup-capacity"
    const val Duration = "group-setup-duration"
    const val ConfirmationLead = "group-setup-confirmation-lead"
    const val VenueName = "group-setup-venue-name"
    const val VenueAddress = "group-setup-venue-address"
    const val Recurrence = "group-setup-recurrence"
    const val SlotsError = "group-setup-slots-error"
    const val SlotSave = "group-setup-slot-save"
    const val ErrorBanner = "group-setup-error-banner"
    const val SaveFailure = "group-setup-save-failure"
    const val Retry = "group-setup-retry"
    const val SaveDraft = "group-setup-save-draft"
    const val ToastAction = "group-setup-toast-action"
    const val Delete = "group-setup-delete"
    const val DeleteConfirm = "group-setup-delete-confirm"
    const val Submit = "group-setup-submit"
    const val ReviewCreate = "group-review-create"
    const val ReviewEdit = "group-review-edit"
}

/**
 * `2a` (criar) e `2i` (editar) são a mesma tela: o modo decide título, lixeira, rótulo do
 * botão e se existe revisão. Aqui só ficam a barra, a coluna rolável, o rodapé e as
 * folhas — cada card branco é uma seção em `GroupSetupSections.kt`.
 */
@Composable
fun GroupSetupScreen(
    state: GroupSetupState,
    onIntent: (GroupSetupIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Box(modifier = modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            SaqzTopAppBar(
                title = stringResource(
                    if (state.isEditing) Res.string.group_setup_edit_title else Res.string.group_setup_create_title,
                ),
                onBack = onBack,
                actions = {
                    if (state.isEditing) {
                        val deleteLabel = stringResource(Res.string.group_setup_delete_action)
                        SaqzIconButton(
                            onClick = { onIntent(GroupSetupIntent.OpenSheet(GroupSetupSheet.ConfirmDelete)) },
                            contentDescription = deleteLabel,
                            soft = true,
                            modifier = Modifier.testTag(GroupSetupTags.Delete),
                        ) {
                            SaqzIcon(SaqzIcons.Trash, tint = SaqzTheme.colors.errorForeground)
                        }
                    }
                },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = metrics.horizontalPadding, vertical = metrics.grid),
                verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
            ) {
                GroupSetupNotices(state, onIntent)
                GroupSetupCards(state, onIntent)
            }
            GroupSetupFooter(state, onIntent)
        }
        GroupSetupSheets(state = state, onIntent = onIntent)
    }
}

@Composable
private fun GroupSetupNotices(state: GroupSetupState, onIntent: (GroupSetupIntent) -> Unit) {
    if (state.isOffline) {
        SaqzOfflineBanner(message = stringResource(Res.string.group_system_offline))
    }
    if (state.errors.isNotEmpty()) {
        GroupErrorBanner(count = state.errors.size)
    }
    if (state.saveFailed) {
        GroupSaveFailureCard(
            onRetry = { onIntent(GroupSetupIntent.Retry) },
            onSaveDraft = { onIntent(GroupSetupIntent.SaveDraft) },
        )
    }
}

@Composable
private fun GroupSetupCards(state: GroupSetupState, onIntent: (GroupSetupIntent) -> Unit) {
    val form = state.form
    GroupPhotoSection(
        photoUrl = state.photoUrl,
        groupName = form.name,
        isEditing = state.isEditing,
        onPick = { onIntent(GroupSetupIntent.PickPhoto) },
    )
    GroupNameSection(
        name = form.name,
        errorText = state.errorText(GroupSetupError.NameRequired, Res.string.group_setup_error_name),
        onChange = { onIntent(GroupSetupIntent.UpdateName(it)) },
    )
    GroupModalitySection(
        modality = form.modality,
        errorText = state.errorText(
            GroupSetupError.ModalityRequired,
            Res.string.group_setup_error_modality,
        ),
        onOpen = { onIntent(GroupSetupIntent.OpenSheet(GroupSetupSheet.Modality)) },
    )
    GroupCompositionSection(
        composition = form.composition,
        errorText = state.errorText(
            GroupSetupError.CompositionRequired,
            Res.string.group_setup_error_composition,
        ),
        onSelect = { onIntent(GroupSetupIntent.SelectComposition(it)) },
    )
    GroupLevelSection(
        levelLabel = form.level?.label(),
        customLevel = form.customLevel,
        showsCustomLevel = state.showsCustomLevel,
        customLevelError = state.errorText(
            GroupSetupError.CustomLevelRequired,
            Res.string.group_setup_error_custom_level,
        ),
        onOpen = { onIntent(GroupSetupIntent.OpenSheet(GroupSetupSheet.Level)) },
        onCustomLevelChange = { onIntent(GroupSetupIntent.UpdateCustomLevel(it)) },
    )
    if (state.showsPlayStyle) {
        GroupPlayStyleSection(
            playStyle = form.playStyle,
            onSelect = { onIntent(GroupSetupIntent.SelectPlayStyle(it)) },
        )
    }
    GroupDescriptionSection(
        description = form.description,
        errorText = state.errorText(
            GroupSetupError.DescriptionTooShort,
            Res.string.group_setup_error_description,
        ),
        onChange = { onIntent(GroupSetupIntent.UpdateDescription(it)) },
    )
    GroupCapacitySection(
        capacity = form.defaultCapacity ?: GroupSetupDefaults.Capacity,
        isBeach = form.modality == GroupModality.BEACH_VOLLEYBALL,
        hasError = GroupSetupError.CapacityOutOfRange in state.errors,
        onChange = { onIntent(GroupSetupIntent.UpdateCapacity(it)) },
    )
    GroupDurationSection(
        minutes = state.durationMinutes,
        onSelect = { onIntent(GroupSetupIntent.SelectDuration(it)) },
    )
    GroupConfirmationLeadSection(
        minutes = form.defaultConfirmationLeadMinutes,
        onSelect = { onIntent(GroupSetupIntent.SelectConfirmationLead(it)) },
    )
    GroupVenueSection(
        name = form.defaultVenue?.name.orEmpty(),
        address = form.defaultVenue?.address.orEmpty(),
        nameError = state.errorText(
            GroupSetupError.VenueNameRequired,
            Res.string.group_setup_error_venue_name,
        ),
        addressError = state.errorText(
            GroupSetupError.VenueAddressNotFound,
            Res.string.group_setup_error_venue_address,
        ),
        onNameChange = { onIntent(GroupSetupIntent.UpdateVenueName(it)) },
        onAddressChange = { onIntent(GroupSetupIntent.UpdateVenueAddress(it)) },
    )
    GroupRecurrenceCard(
        recurring = state.recurring,
        slots = form.regularSlots,
        hasError = GroupSetupError.SlotsRequired in state.errors,
        onRecurringChange = { onIntent(GroupSetupIntent.ToggleRecurring(it)) },
        onAddSlot = { onIntent(GroupSetupIntent.OpenSheet(GroupSetupSheet.Slot(index = null))) },
        onRemoveSlot = { onIntent(GroupSetupIntent.RemoveSlot(it)) },
    )
}

@Composable
private fun GroupSetupFooter(state: GroupSetupState, onIntent: (GroupSetupIntent) -> Unit) {
    val metrics = SaqzTheme.metrics
    Column {
        SaqzDivider()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SaqzTheme.colors.background)
                .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        ) {
            SaqzButton(
                label = stringResource(
                    when {
                        state.isSaving && !state.isEditing -> Res.string.group_system_creating
                        state.isEditing -> Res.string.group_setup_save_action
                        else -> Res.string.group_setup_create_action
                    },
                ),
                onClick = { onIntent(GroupSetupIntent.Submit) },
                loading = state.isSaving,
                fullWidth = true,
                modifier = Modifier.testTag(GroupSetupTags.Submit),
            )
        }
    }
}

@Composable
private fun GroupSetupState.errorText(error: GroupSetupError, resource: StringResource): String? =
    if (error in errors) stringResource(resource) else null

// --- Previews: um estado real por célula do export ---------------------------------

internal val PreviewCourtForm = GroupSetupForm(
    name = "Vôlei do CERET",
    modality = GroupModality.COURT_VOLLEYBALL,
    composition = GroupComposition.MIXED,
    description = "Galera do Tatuapé, nível intermediário. Cheguem 15 min antes pra montar a rede.",
    level = GroupLevel.INTERMEDIATE,
    playStyle = GroupPlayStyle.SIX_ZERO,
    defaultVenue = GroupVenueForm(name = "CERET — Quadra 2", address = "R. Canuto Abreu, s/n · Tatuapé"),
    regularSlots = listOf(
        GroupRegularSlotForm(weekday = GroupWeekday.TUESDAY, startTime = "19:30", durationMinutes = 120),
        GroupRegularSlotForm(weekday = GroupWeekday.THURSDAY, startTime = "20:00", durationMinutes = 120),
    ),
    defaultCapacity = 12,
    defaultConfirmationLeadMinutes = 360,
)

@Preview
@Composable
private fun GroupSetupCreatePreview() = SaqzTheme {
    GroupSetupScreen(
        state = GroupSetupState(mode = GroupSetupMode.Create, form = PreviewCourtForm),
        onIntent = {},
        onBack = {},
    )
}

/** `2b` — areia e avulso: sem card de sistema de jogo e com o texto de sem recorrência. */
@Preview
@Composable
private fun GroupSetupBeachPreview() = SaqzTheme {
    GroupSetupScreen(
        state = GroupSetupState(
            mode = GroupSetupMode.Create,
            form = GroupSetupForm(
                name = "Beach da Vila",
                modality = GroupModality.BEACH_VOLLEYBALL,
                composition = GroupComposition.WOMEN,
                level = GroupLevel.ADVANCED,
                defaultCapacity = 4,
                defaultConfirmationLeadMinutes = 360,
            ),
            photoUrl = "https://saqz.example/beach-da-vila.png",
            recurring = false,
        ),
        onIntent = {},
        onBack = {},
    )
}

/**
 * `2g` — os cinco campos que o export marca. Os erros saem do próprio `validate`, não
 * de uma lista escrita à mão: cena que inventa erro não confere nada.
 */
internal val PreviewErrorState = GroupSetupState(
    mode = GroupSetupMode.Create,
    form = GroupSetupForm(
        modality = GroupModality.COURT_VOLLEYBALL,
        composition = GroupComposition.MIXED,
        level = GroupLevel.CUSTOM,
        playStyle = GroupPlayStyle.SIX_ZERO,
        defaultVenue = GroupVenueForm(name = "CERET — Quadra 2", address = ""),
        defaultCapacity = 1,
        defaultConfirmationLeadMinutes = 360,
    ),
).let { it.copy(errors = validate(it)) }

@Preview
@Composable
private fun GroupSetupErrorsPreview() = SaqzTheme {
    GroupSetupScreen(state = PreviewErrorState, onIntent = {}, onBack = {})
}

/** `2i` — editar, com lixeira no topo e "Salvar alterações" no rodapé. */
@Preview
@Composable
private fun GroupSetupEditPreview() = SaqzTheme {
    GroupSetupScreen(
        state = GroupSetupState(
            mode = GroupSetupMode.Edit(groupId = "grp-1"),
            form = PreviewCourtForm,
            photoUrl = "https://saqz.example/ceret.png",
            memberCount = 26,
        ),
        onIntent = {},
        onBack = {},
    )
}

/** `2j` — a mesma tela de edição com a folha de exclusão aberta. */
@Preview
@Composable
private fun GroupSetupDeletePreview() = SaqzTheme {
    GroupSetupScreen(
        state = GroupSetupState(
            mode = GroupSetupMode.Edit(groupId = "grp-1"),
            form = PreviewCourtForm,
            photoUrl = "https://saqz.example/ceret.png",
            memberCount = 26,
            sheet = GroupSetupSheet.ConfirmDelete,
        ),
        onIntent = {},
        onBack = {},
    )
}

/** `2c` — o picker de dia e horário sobre o formulário. */
@Preview
@Composable
private fun GroupSetupSlotSheetPreview() = SaqzTheme {
    GroupSetupScreen(
        state = GroupSetupState(
            mode = GroupSetupMode.Create,
            form = PreviewCourtForm,
            sheet = GroupSetupSheet.Slot(index = null),
        ),
        onIntent = {},
        onBack = {},
    )
}
