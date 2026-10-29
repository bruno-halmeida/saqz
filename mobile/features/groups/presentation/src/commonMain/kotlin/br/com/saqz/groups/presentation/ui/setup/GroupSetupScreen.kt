package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.material.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzOfflineBanner
import br.com.saqz.designsystem.SaqzSpinner
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
import br.com.saqz.groups.model.PromotionMode
import br.com.saqz.groups.presentation.setup.GroupSetupDefaults
import br.com.saqz.groups.presentation.ui.SubscriptionRequiredDialog
import br.com.saqz.groups.presentation.setup.GroupSetupError
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupSheet
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.setup.validate
import br.com.saqz.groups.presentation.ui.label
import br.com.saqz.groups.presentation.ui.components.GroupFormCard
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_pix_error_type
import br.com.saqz.groups.resources.group_setup_create_action
import br.com.saqz.groups.resources.group_setup_create_title
import br.com.saqz.groups.resources.group_setup_trial_offer
import br.com.saqz.groups.resources.onboarding_setup_advanced
import br.com.saqz.groups.resources.onboarding_setup_simple
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
import br.com.saqz.groups.resources.whatsapp_binding_open
import br.com.saqz.groups.resources.whatsapp_binding_section_hint
import br.com.saqz.groups.resources.whatsapp_binding_section_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal object GroupSetupTags {
    const val Advanced = "group-setup-advanced"
    const val TrialOffer = "group-setup-trial-offer"
    const val Photo = "group-setup-photo"
    const val Name = "group-setup-name"
    const val Modality = "group-setup-modality"
    fun modalityOption(modality: GroupModality) = "group-setup-modality-${modality.name}"
    const val Composition = "group-setup-composition"
    const val Level = "group-setup-level"
    const val CustomLevel = "group-setup-custom-level"
    const val PlayStyle = "group-setup-play-style"
    const val Description = "group-setup-description"
    const val Capacity = "group-setup-capacity"
    const val GameFee = "group-setup-game-fee"
    const val Duration = "group-setup-duration"
    const val ConfirmationLead = "group-setup-confirmation-lead"
    const val GameConfigConfirmationLead = "group-game-config-confirmation-lead"
    const val MensalistaPriority = "group-game-config-mensalista-priority"
    const val PromotionMode = "group-game-config-promotion-mode"
    const val AdvancePayment = "group-game-config-advance-payment"
    const val AdvancePaymentTurnedOff = "group-game-config-advance-payment-turned-off"
    const val MonthlyLateJoinPolicy = "group-monthly-late-join-policy"
    const val AttendanceUpdatesMode = "group-attendance-updates-mode"
    const val MonthlyFee = "group-monthly-fee"
    const val MonthlyFeeAmount = "group-monthly-fee-amount"
    const val MonthlyDueDay = "group-monthly-due-day"
    fun monthlyLateJoinPolicyOption(policy: br.com.saqz.groups.model.MonthlyLateJoinPolicy) =
        "group-monthly-late-join-policy-${policy.name}"
    const val PixKey = "group-setup-pix-key"
    const val PixHidden = "group-setup-pix-hidden"
    const val PixType = "group-setup-pix-type"
    fun pixTypeOption(type: br.com.saqz.groups.presentation.pix.PixKeyType) = "group-setup-pix-type-${type.name}"
    const val VenueName = "group-setup-venue-name"
    const val VenueAddress = "group-setup-venue-address"
    const val Recurrence = "group-setup-recurrence"
    const val SlotsError = "group-setup-slots-error"
    const val SlotSave = "group-setup-slot-save"
    const val ErrorBanner = "group-setup-error-banner"
    const val SaveFailure = "group-setup-save-failure"
    const val Retry = "group-setup-retry"
    const val ToastAction = "group-setup-toast-action"
    const val Delete = "group-setup-delete"
    const val DeleteConfirm = "group-setup-delete-confirm"
    const val Submit = "group-setup-submit"
    const val ReviewCreate = "group-review-create"
    const val ReviewEdit = "group-review-edit"
    const val WhatsApp = "group-setup-whatsapp"
    const val WhatsAppOpen = "group-setup-whatsapp-open"
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
    showTrialOffer: Boolean = false,
    onOpenWhatsApp: (() -> Unit)? = null,
    receivables: GroupSetupReceivables? = null,
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
                    if (state.isEditing && state.canDelete) {
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
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SaqzSpinner() }
                return@Column
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = metrics.horizontalPadding, vertical = metrics.grid),
                verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
            ) {
                GroupSetupNotices(state, onIntent)
                GroupSetupCards(state, onIntent, onOpenWhatsApp, receivables)
            }
            GroupSetupFooter(state, onIntent, showTrialOffer)
        }
        GroupSetupSheets(state = state, onIntent = onIntent)
        if (state.subscriptionRequired) {
            SubscriptionRequiredDialog(
                trialEnded = state.trialEnded,
                onDismiss = { onIntent(GroupSetupIntent.DismissSubscriptionRequired) },
            )
        }
    }
}

@Composable
private fun GroupSetupNotices(state: GroupSetupState, onIntent: (GroupSetupIntent) -> Unit) {
    if (state.isOffline) {
        SaqzOfflineBanner(message = stringResource(Res.string.group_system_offline))
    }
    if (state.errors.isNotEmpty()) {
        GroupErrorBanner(
            count = state.errors.size,
            isEditing = state.isEditing,
            // DEB-146: só o valor do Cobrar diária faltando ganha o aviso da prancha, que diz onde ele está.
            gameFeeOnly = state.errors.all { it == GroupSetupError.GameFeeInvalid },
        )
    }
    if (state.saveFailed) {
        GroupSaveFailureCard(
            onRetry = { onIntent(GroupSetupIntent.Retry) },
            isEditing = state.isEditing,
        )
    }
}

@Composable
private fun GroupSetupCards(
    state: GroupSetupState,
    onIntent: (GroupSetupIntent) -> Unit,
    onOpenWhatsApp: (() -> Unit)?,
    receivables: GroupSetupReceivables? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
        var userExpanded by rememberSaveable { mutableStateOf(false) }
        // Na edição tudo fica aberto, sem a linha. Na criação, "Mais detalhes" só abre sozinho se algum erro estiver lá dentro.
        val expanded = state.isEditing || userExpanded || state.hasErrorInsideDetails()
        GroupBasicsCard(state, onIntent)
        GroupGamesCard(state, onIntent)
        if (!state.isEditing) {
            GroupMoreDetailsRow(
                expanded = expanded,
                onToggle = { userExpanded = !userExpanded },
                modifier = Modifier.testTag(GroupSetupTags.Advanced),
            )
        }
        if (expanded) GroupSetupDetails(state, onIntent, onOpenWhatsApp, receivables)
    }
}

/** Erros que moram dentro de "Mais detalhes": só eles abrem a seção sozinha. */
private fun GroupSetupState.hasErrorInsideDetails(): Boolean = errors.any {
    when (it) {
        GroupSetupError.CustomLevelRequired,
        GroupSetupError.DescriptionTooShort,
        GroupSetupError.CapacityOutOfRange,
        GroupSetupError.PositionSlotsSumMismatch,
        GroupSetupError.GameFeeInvalid,
        GroupSetupError.MonthlyFeeInvalid,
        GroupSetupError.DurationOutOfRange,
        -> true
        // Sem dia fixo a quadra vai para "Regras dos jogos".
        GroupSetupError.VenueNameRequired, GroupSetupError.VenueAddressNotFound -> !recurring
        else -> false
    }
}

@Composable
private fun GroupSetupDetails(
    state: GroupSetupState,
    onIntent: (GroupSetupIntent) -> Unit,
    onOpenWhatsApp: (() -> Unit)?,
    receivables: GroupSetupReceivables? = null,
) {
    val form = state.form
    GroupAboutSection(state, onIntent)
    GroupRulesSection(state, onIntent, feePreview = receivables?.feePreview)
    if (state.isEditing && state.canManageGameConfig) {
        GroupGameConfigSection(
            mensalistaPriority = form.mensalistaPriority,
            promotionMode = form.promotionMode,
            confirmationLeadMinutes = form.defaultConfirmationLeadMinutes,
            onMensalistaPriorityChange = { onIntent(GroupSetupIntent.ToggleMensalistaPriority(it)) },
            onPromotionModeSelect = { onIntent(GroupSetupIntent.SelectPromotionMode(it)) },
            advancePayment = GroupAdvancePaymentState(
                visible = state.showsAdvancePayment,
                checked = form.advancePaymentRequired,
                turnedOff = state.advancePaymentTurnedOff,
                onChange = { onIntent(GroupSetupIntent.ToggleAdvancePayment(it)) },
            ),
            onConfirmationLeadSelect = { onIntent(GroupSetupIntent.SelectConfirmationLead(it)) },
        )
        if (state.showsMonthlyFee) {
            GroupMonthlyFeeSection(
                cents = form.monthlyFeeCents,
                dueDay = form.monthlyDueDay,
                hasError = GroupSetupError.MonthlyFeeInvalid in state.errors,
                onFeeChange = { onIntent(GroupSetupIntent.UpdateMonthlyFee(it)) },
                onDueDayChange = { onIntent(GroupSetupIntent.UpdateMonthlyDueDay(it)) },
                receivables = receivables,
            )
        }
        if (state.showsMonthlyLateJoinPolicy) {
            GroupMonthlyLateJoinPolicySection(
                policy = requireNotNull(form.monthlyLateJoinPolicy),
                onSelect = { onIntent(GroupSetupIntent.SelectMonthlyLateJoinPolicy(it)) },
            )
        }
        receivables?.receiving?.invoke()
        GroupPixSection(
            hiddenFromAthletes = receivables?.active == true,
            pixKey = state.pixKey,
            pixType = state.pixType,
            onPixTypeSelect = { onIntent(GroupSetupIntent.SelectPixType(it)) },
            onPixKeyChange = { onIntent(GroupSetupIntent.UpdatePixKey(it)) },
            pixKeyError = state.errorText(GroupSetupError.PixKeyInvalid, state.pixType.errorRes()),
            pixTypeError = state.errorText(GroupSetupError.PixTypeRequired, Res.string.group_pix_error_type),
        )
    }
    val attendanceUpdatesMode = form.attendanceUpdatesMode
    if (state.isEditing && state.canChangeAttendanceUpdates && attendanceUpdatesMode != null) {
        GroupAttendanceUpdatesSection(
            mode = attendanceUpdatesMode,
            onSelect = { onIntent(GroupSetupIntent.SelectAttendanceUpdatesMode(it)) },
        )
    }
    if (state.isEditing && onOpenWhatsApp != null) {
        GroupWhatsAppSection(onOpen = onOpenWhatsApp)
    }
}

/**
 * Entrada das configurações avançadas → grupo do WhatsApp. Só aparece no editar e quando
 * o host expõe a navegação: a tela em si não conhece `NavDisplay` (AGENTS.md §6).
 */
@Composable
private fun GroupWhatsAppSection(onOpen: () -> Unit) {
    GroupFormCard(
        title = stringResource(Res.string.whatsapp_binding_section_title),
        hint = stringResource(Res.string.whatsapp_binding_section_hint),
        modifier = Modifier.testTag(GroupSetupTags.WhatsApp),
    ) {
        SaqzButton(
            label = stringResource(Res.string.whatsapp_binding_open),
            onClick = onOpen,
            variant = SaqzButtonVariant.Secondary,
            fullWidth = true,
            modifier = Modifier.testTag(GroupSetupTags.WhatsAppOpen),
        )
    }
}

@Composable
private fun GroupSetupFooter(state: GroupSetupState, onIntent: (GroupSetupIntent) -> Unit, showTrialOffer: Boolean) {
    val metrics = SaqzTheme.metrics
    Column {
        SaqzDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SaqzTheme.colors.background)
                .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
            verticalArrangement = Arrangement.spacedBy(metrics.grid),
        ) {
            if (showTrialOffer && !state.isEditing) {
                Text(
                    text = stringResource(Res.string.group_setup_trial_offer),
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().testTag(GroupSetupTags.TrialOffer),
                )
            }
            SaqzButton(
                label = stringResource(
                    when {
                        state.isSaving && !state.isEditing -> Res.string.group_system_creating
                        state.isEditing -> Res.string.group_setup_save_action
                        else -> Res.string.group_setup_create_action
                    },
                ),
                onClick = { onIntent(GroupSetupIntent.Submit) },
                // A soma das posições não desabilita o botão: tocar com a soma errada recusa e conta no aviso geral.
                enabled = !state.isSaving,
                loading = state.isSaving,
                fullWidth = true,
                modifier = Modifier.testTag(GroupSetupTags.Submit),
            )
        }
    }
}

@Composable
internal fun GroupSetupState.errorText(error: GroupSetupError, resource: StringResource): String? =
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
            canDelete = true,
            canManageGameConfig = true,
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
            canDelete = true,
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
