package br.com.saqz.groups.presentation.ui.monthlyinvite

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.designsystem.PhoneVisualTransformation
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzDashedAddButton
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzInputKind
import br.com.saqz.designsystem.SaqzMemberRow
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzTextLink
import br.com.saqz.designsystem.SaqzToast
import br.com.saqz.designsystem.SaqzToastText
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.membership.MonthlyFeeDefault
import br.com.saqz.groups.domain.membership.MonthlyInvitePlanLimit
import br.com.saqz.groups.domain.membership.MonthlyInviteRejection
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInviteIntent
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInviteMatch
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInvitePersonUi
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInvitePhoneError
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInviteRules
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInviteSheet
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInviteState
import br.com.saqz.groups.presentation.monthlyinvite.MonthlyInviteToast
import br.com.saqz.groups.presentation.monthlyinvite.monthlyInviteMessage
import br.com.saqz.groups.presentation.ui.SubscriptionRequiredDialog
import br.com.saqz.groups.resources.*
import org.jetbrains.compose.resources.stringResource

internal object MonthlyInviteTags {
    const val Loading = "monthly-invite-loading"
    const val LoadFailed = "monthly-invite-load-failed"
    const val Empty = "monthly-invite-empty"
    const val AddPerson = "monthly-invite-add-person"
    const val FeeCard = "monthly-invite-fee-card"
    const val FeeChange = "monthly-invite-fee-change"
    const val FeeMissing = "monthly-invite-fee-missing"
    const val LimitNotice = "monthly-invite-limit-notice"
    const val Plans = "monthly-invite-plans"
    const val Pace = "monthly-invite-pace"
    const val MessageToggle = "monthly-invite-message-toggle"
    const val Message = "monthly-invite-message"
    const val Send = "monthly-invite-send"
    const val Help = "monthly-invite-help"
    const val Offline = "monthly-invite-offline"
    const val CopyMessage = "monthly-invite-copy-message"
    const val CopyLink = "monthly-invite-copy-link"
    const val Sheet = "monthly-invite-sheet"
    const val SheetName = "monthly-invite-sheet-name"
    const val SheetPhone = "monthly-invite-sheet-phone"
    const val SheetStrip = "monthly-invite-sheet-strip"
    const val SheetAdd = "monthly-invite-sheet-add"
    const val SheetAddMore = "monthly-invite-sheet-add-more"
    const val SheetPromote = "monthly-invite-sheet-promote"
    const val Toast = "monthly-invite-toast"
    const val Undo = "monthly-invite-undo"
    const val ActionFailed = "monthly-invite-action-failed"

    fun person(index: Int) = "monthly-invite-person-$index"

    fun remove(index: Int) = "monthly-invite-remove-$index"

    fun rowError(index: Int) = "monthly-invite-row-error-$index"
}

/** Tela "Convidar mensalista" (CONV-010), nos estados do protótipo. */
@Composable
fun MonthlyInviteScreen(
    state: MonthlyInviteState,
    onIntent: (MonthlyInviteIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Box(modifier = modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SaqzTopAppBar(title = stringResource(Res.string.monthly_invite_title), onBack = onBack)
            // O toast mora na área da lista, acima da barra de baixo: nunca cobre "Enviar N convites", seja qual for a altura
            // da barra (fonte grande, tela estreita). Um toque perdido no "Desfazer" já enviou um lote (V1.15).
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
                verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
            ) {
                when {
                    state.isLoading -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().testTag(MonthlyInviteTags.Loading), Alignment.Center) { SaqzSpinner() }
                    }
                    state.loadFailed -> item(key = "failed") {
                        SaqzEmptyState(
                            title = stringResource(Res.string.monthly_invite_load_failed),
                            icon = SaqzIcons.CircleAlert,
                            action = stringResource(Res.string.monthly_invite_retry),
                            onAction = { onIntent(MonthlyInviteIntent.Retry) },
                            modifier = Modifier.testTag(MonthlyInviteTags.LoadFailed),
                        )
                    }
                    state.defaultFee == null -> item(key = "fee-missing") {
                        SaqzEmptyState(
                            title = stringResource(Res.string.monthly_invite_fee_missing_title),
                            description = stringResource(Res.string.monthly_invite_fee_missing_text),
                            icon = SaqzIcons.Calendar,
                            action = stringResource(Res.string.monthly_invite_fee_missing_action),
                            onAction = { onIntent(MonthlyInviteIntent.ConfigureFee) },
                            actionFullWidth = true,
                            modifier = Modifier.testTag(MonthlyInviteTags.FeeMissing),
                        )
                    }
                    !state.whatsAppAvailable -> offlineContent(state, onIntent)
                    else -> inviteContent(state, state.defaultFee, onIntent)
                }
            }
                MonthlyInviteToastHost(state.toast, onIntent, Modifier.align(Alignment.BottomCenter))
            }
            if (!state.isLoading && !state.loadFailed && state.defaultFee != null) {
                MonthlyInviteBottomBar(state, onIntent)
            }
        }
        MonthlyInvitePersonSheet(sheet = state.sheet, onIntent = onIntent)
        if (state.subscriptionRequired) {
            SubscriptionRequiredDialog(trialEnded = false, onDismiss = { onIntent(MonthlyInviteIntent.DismissSubscriptionRequired) })
        }
    }
}

private fun LazyListScope.inviteContent(
    state: MonthlyInviteState,
    fee: MonthlyFeeDefault,
    onIntent: (MonthlyInviteIntent) -> Unit,
) {
    val limit = state.planLimit
    if (limit != null && state.overLimit > 0) {
        item(key = "limit") { MonthlyInviteLimitNotice(limit, state.people.size, onIntent) }
    }
    if (state.actionFailed) {
        item(key = "action-failed") {
            SaqzTextLink(
                label = stringResource(Res.string.monthly_invite_action_failed),
                onClick = { onIntent(MonthlyInviteIntent.DismissActionFailed) },
                modifier = Modifier.testTag(MonthlyInviteTags.ActionFailed),
            )
        }
    }
    item(key = "recipients") { MonthlyInviteRecipients(state.people, state.rowErrors, onIntent) }
    item(key = "fee") { MonthlyInviteFeeCard(fee, onIntent) }
    if (state.people.isNotEmpty()) {
        item(key = "message") { MonthlyInviteMessagePreview(state, onIntent) }
    }
}

@Composable
private fun MonthlyInviteLimitNotice(limit: MonthlyInvitePlanLimit, inviting: Int, onIntent: (MonthlyInviteIntent) -> Unit) {
    val full = limit.free <= 0
    SaqzCard(modifier = Modifier.testTag(MonthlyInviteTags.LimitNotice)) {
        Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
            SaqzIcon(SaqzIcons.CircleAlert, tint = SaqzTheme.colors.warningForeground)
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                Text(
                    text = stringResource(if (full) Res.string.monthly_invite_limit_title else Res.string.monthly_invite_over_title),
                    style = SaqzTheme.typography.label,
                    color = SaqzTheme.colors.textPrimary,
                )
                Text(
                    text = if (full) {
                        stringResource(Res.string.monthly_invite_limit_text, limit.limit, limit.current)
                    } else {
                        stringResource(Res.string.monthly_invite_over_text, limit.free, inviting)
                    },
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textSecondary,
                )
            }
        }
        SaqzButton(
            label = stringResource(Res.string.monthly_invite_plans),
            onClick = { onIntent(MonthlyInviteIntent.OpenPlans) },
            variant = SaqzButtonVariant.Secondary,
            fullWidth = true,
            modifier = Modifier.testTag(MonthlyInviteTags.Plans),
        )
    }
}

@Composable
private fun MonthlyInviteRecipients(
    people: List<MonthlyInvitePersonUi>,
    rowErrors: Map<Int, MonthlyInviteRejection>,
    onIntent: (MonthlyInviteIntent) -> Unit,
) {
    SaqzCard {
        LabelText(stringResource(Res.string.monthly_invite_recipients_title))
        if (people.isEmpty()) {
            SaqzEmptyState(
                title = stringResource(Res.string.monthly_invite_empty_title),
                description = stringResource(Res.string.monthly_invite_empty_text),
                icon = SaqzIcons.User,
                modifier = Modifier.testTag(MonthlyInviteTags.Empty),
            )
        } else {
            Text(
                stringResource(Res.string.monthly_invite_recipients_support),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
            )
            people.forEachIndexed { index, person ->
                if (index > 0) SaqzDivider()
                SaqzMemberRow(
                    name = person.name,
                    meta = rowErrors[index]?.let { rowErrorText(it) } ?: MonthlyInviteRules.formatPhone(person.phone),
                    modifier = Modifier.testTag(MonthlyInviteTags.person(index)),
                    trailing = {
                        val description = stringResource(Res.string.monthly_invite_remove, person.name)
                        SaqzIconButton(
                            onClick = { onIntent(MonthlyInviteIntent.Remove(index)) },
                            contentDescription = description,
                            modifier = Modifier.testTag(MonthlyInviteTags.remove(index)),
                        ) { SaqzIcon(SaqzIcons.Close, tint = SaqzTheme.colors.textSecondary) }
                    },
                )
                rowErrors[index]?.let {
                    SaqzStatusChip(
                        text = rowErrorText(it),
                        tone = SaqzChipTone.Error,
                        modifier = Modifier.testTag(MonthlyInviteTags.rowError(index)),
                    )
                }
            }
        }
        SaqzDashedAddButton(
            label = stringResource(if (people.isEmpty()) Res.string.monthly_invite_add_person else Res.string.monthly_invite_add_another),
            onClick = { onIntent(MonthlyInviteIntent.OpenSheet) },
            modifier = Modifier.testTag(MonthlyInviteTags.AddPerson),
        )
    }
}

@Composable
private fun rowErrorText(reason: MonthlyInviteRejection): String = stringResource(
    when (reason) {
        MonthlyInviteRejection.INVALID_NAME -> Res.string.monthly_invite_row_name
        MonthlyInviteRejection.DUPLICATE_IN_BATCH -> Res.string.monthly_invite_row_duplicate
        MonthlyInviteRejection.ALREADY_INVITED -> Res.string.monthly_invite_row_invited
        MonthlyInviteRejection.ALREADY_MENSALISTA -> Res.string.monthly_invite_row_mensalista
        MonthlyInviteRejection.ALREADY_AVULSO -> Res.string.monthly_invite_row_avulso
        MonthlyInviteRejection.INVALID_PHONE, MonthlyInviteRejection.UNKNOWN -> Res.string.monthly_invite_row_phone
    },
)

@Composable
private fun MonthlyInviteFeeCard(fee: MonthlyFeeDefault, onIntent: (MonthlyInviteIntent) -> Unit) {
    SaqzCard(modifier = Modifier.testTag(MonthlyInviteTags.FeeCard)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
            SaqzIcon(SaqzIcons.Calendar, tint = SaqzTheme.colors.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(Res.string.monthly_invite_fee_amount, formatBrl(fee.monthlyFeeCents)),
                    style = SaqzTheme.typography.label,
                    color = SaqzTheme.colors.textPrimary,
                )
                Text(
                    stringResource(Res.string.monthly_invite_fee_due, fee.monthlyDueDay),
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textSecondary,
                )
            }
            SaqzTextLink(
                label = stringResource(Res.string.monthly_invite_fee_change),
                onClick = { onIntent(MonthlyInviteIntent.ConfigureFee) },
                touchTarget = true,
                modifier = Modifier.testTag(MonthlyInviteTags.FeeChange),
            )
        }
    }
}

@Composable
private fun MonthlyInviteMessagePreview(state: MonthlyInviteState, onIntent: (MonthlyInviteIntent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
        Text(
            text = paceText(state.people.size),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
            modifier = Modifier.testTag(MonthlyInviteTags.Pace),
        )
        SaqzTextLink(
            label = stringResource(Res.string.monthly_invite_message_toggle),
            onClick = { onIntent(MonthlyInviteIntent.ToggleMessage) },
            touchTarget = true,
            modifier = Modifier.testTag(MonthlyInviteTags.MessageToggle),
        )
        if (state.messageOpen) {
            val link = state.linkUrl ?: stringResource(Res.string.monthly_invite_message_link_placeholder)
            MonthlyInviteMessageBox(
                monthlyInviteMessage(
                    MonthlyInviteRules.firstName(state.people.first().name),
                    state.senderName,
                    state.groupName,
                    link,
                ),
            )
        }
    }
}

@Composable
private fun MonthlyInviteMessageBox(text: String) {
    SaqzCard(tone = SaqzCardTone.Soft, modifier = Modifier.testTag(MonthlyInviteTags.Message)) {
        Text(text, style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary)
    }
}

/** Uns 10 s por pessoa (a fila espera de 5 a 15 s entre mensagens). */
@Composable
private fun paceText(count: Int): String {
    val seconds = count * SECONDS_PER_INVITE
    return when {
        count == 1 -> stringResource(Res.string.monthly_invite_pace_one)
        seconds < SECONDS_PER_MINUTE -> stringResource(Res.string.monthly_invite_pace_short, count)
        else -> stringResource(Res.string.monthly_invite_pace_long, count, (seconds + SECONDS_PER_MINUTE / 2) / SECONDS_PER_MINUTE)
    }
}

private const val SECONDS_PER_INVITE = 10
private const val SECONDS_PER_MINUTE = 60

private fun LazyListScope.offlineContent(state: MonthlyInviteState, onIntent: (MonthlyInviteIntent) -> Unit) {
    item(key = "offline") {
        SaqzCard(modifier = Modifier.testTag(MonthlyInviteTags.Offline)) {
            Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
                SaqzIcon(SaqzIcons.CircleAlert, tint = SaqzTheme.colors.warningForeground)
                Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                    LabelText(stringResource(Res.string.monthly_invite_offline_title))
                    SupportText(stringResource(Res.string.monthly_invite_offline_text))
                }
            }
        }
    }
    item(key = "offline-message") {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            LabelText(stringResource(Res.string.monthly_invite_offline_message))
            val link = state.linkUrl ?: stringResource(Res.string.monthly_invite_message_link_placeholder)
            MonthlyInviteMessageBox(monthlyInviteMessage(null, state.senderName, state.groupName, link))
            SaqzTextLink(
                label = stringResource(Res.string.monthly_invite_copy_link),
                onClick = { onIntent(MonthlyInviteIntent.CopyLink) },
                touchTarget = true,
                modifier = Modifier.testTag(MonthlyInviteTags.CopyLink),
            )
        }
    }
    if (state.people.isNotEmpty()) {
        item(key = "offline-people") {
            SaqzCard {
                LabelText(stringResource(Res.string.monthly_invite_offline_people))
                state.people.forEachIndexed { index, person ->
                    if (index > 0) SaqzDivider()
                    SaqzMemberRow(name = person.name, meta = MonthlyInviteRules.formatPhone(person.phone))
                }
            }
        }
    }
}

@Composable
private fun MonthlyInviteBottomBar(state: MonthlyInviteState, onIntent: (MonthlyInviteIntent) -> Unit) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = Modifier.fillMaxWidth().background(SaqzTheme.colors.background).navigationBarsPadding()
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
    ) {
        if (!state.whatsAppAvailable) {
            SaqzButton(
                label = stringResource(Res.string.monthly_invite_copy_message),
                onClick = { onIntent(MonthlyInviteIntent.CopyMessage) },
                fullWidth = true,
                loading = state.copying,
                modifier = Modifier.testTag(MonthlyInviteTags.CopyMessage),
            )
            return@Column
        }
        helpText(state)?.let {
            Text(
                it,
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().testTag(MonthlyInviteTags.Help),
            )
        }
        val count = state.people.size
        SaqzButton(
            label = when {
                state.sending -> stringResource(Res.string.monthly_invite_sending)
                count == 0 -> stringResource(Res.string.monthly_invite_send)
                count == 1 -> stringResource(Res.string.monthly_invite_send_one)
                else -> stringResource(Res.string.monthly_invite_send_many, count)
            },
            onClick = { onIntent(MonthlyInviteIntent.Send) },
            fullWidth = true,
            enabled = state.canSend || state.sending,
            loading = state.sending,
            modifier = Modifier.testTag(MonthlyInviteTags.Send),
        )
    }
}

@Composable
private fun helpText(state: MonthlyInviteState): String? = when {
    state.sending -> null
    state.people.isEmpty() -> stringResource(Res.string.monthly_invite_help_empty)
    state.overLimit > 0 && (state.planLimit?.free ?: 0) <= 0 -> stringResource(Res.string.monthly_invite_help_full)
    state.overLimit == 1 -> stringResource(Res.string.monthly_invite_help_over_one)
    state.overLimit > 1 -> stringResource(Res.string.monthly_invite_help_over_many, state.overLimit)
    state.rowErrors.isNotEmpty() -> stringResource(Res.string.monthly_invite_help_rows)
    else -> null
}

@Composable
private fun MonthlyInvitePersonSheet(sheet: MonthlyInviteSheet?, onIntent: (MonthlyInviteIntent) -> Unit) {
    val current = sheet ?: MonthlyInviteSheet()
    val match = current.match
    SaqzBottomSheet(
        open = sheet != null,
        onClose = { onIntent(MonthlyInviteIntent.CloseSheet) },
        title = stringResource(Res.string.monthly_invite_sheet_title),
        modifier = Modifier.testTag(MonthlyInviteTags.Sheet),
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                when (match) {
                    is MonthlyInviteMatch.Avulso -> SaqzButton(
                        label = stringResource(Res.string.monthly_invite_sheet_promote),
                        onClick = { onIntent(MonthlyInviteIntent.Promote) },
                        fullWidth = true,
                        loading = current.promoting,
                        modifier = Modifier.testTag(MonthlyInviteTags.SheetPromote),
                    )
                    is MonthlyInviteMatch.Mensalista -> SaqzButton(
                        label = stringResource(Res.string.monthly_invite_sheet_add),
                        onClick = {},
                        fullWidth = true,
                        enabled = false,
                        modifier = Modifier.testTag(MonthlyInviteTags.SheetAdd),
                    )
                    null -> {
                        val blocked = current.phoneError == MonthlyInvitePhoneError.Duplicate ||
                            current.phoneError == MonthlyInvitePhoneError.AlreadyInvited
                        SaqzButton(
                            label = stringResource(Res.string.monthly_invite_sheet_add),
                            onClick = { onIntent(MonthlyInviteIntent.Add(keepOpen = false)) },
                            fullWidth = true,
                            enabled = !blocked,
                            modifier = Modifier.testTag(MonthlyInviteTags.SheetAdd),
                        )
                        SaqzButton(
                            label = stringResource(Res.string.monthly_invite_sheet_add_more),
                            onClick = { onIntent(MonthlyInviteIntent.Add(keepOpen = true)) },
                            variant = SaqzButtonVariant.Ghost,
                            fullWidth = true,
                            enabled = !blocked,
                            modifier = Modifier.testTag(MonthlyInviteTags.SheetAddMore),
                        )
                    }
                }
            }
        },
    ) {
        SaqzInput(
            value = current.name,
            onValueChange = { onIntent(MonthlyInviteIntent.UpdateName(it)) },
            label = stringResource(Res.string.monthly_invite_sheet_name),
            placeholder = stringResource(Res.string.monthly_invite_sheet_name_placeholder),
            errorText = if (current.nameError) stringResource(Res.string.monthly_invite_error_name) else null,
            modifier = Modifier.testTag(MonthlyInviteTags.SheetName),
        )
        SaqzInput(
            value = current.phone,
            onValueChange = { onIntent(MonthlyInviteIntent.UpdatePhone(it)) },
            label = stringResource(Res.string.monthly_invite_sheet_phone),
            kind = SaqzInputKind.Phone,
            placeholder = stringResource(Res.string.monthly_invite_sheet_phone_placeholder),
            errorText = phoneErrorText(current),
            visualTransformation = PhoneVisualTransformation(),
            modifier = Modifier.testTag(MonthlyInviteTags.SheetPhone),
        )
        match?.let {
            SaqzCard(tone = SaqzCardTone.Soft, modifier = Modifier.testTag(MonthlyInviteTags.SheetStrip)) {
                Text(
                    text = when (it) {
                        is MonthlyInviteMatch.Mensalista -> stringResource(Res.string.monthly_invite_strip_mensalista, it.name)
                        is MonthlyInviteMatch.Avulso -> stringResource(Res.string.monthly_invite_strip_avulso, it.name)
                    },
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun phoneErrorText(sheet: MonthlyInviteSheet): String? = when (sheet.phoneError) {
    null -> null
    MonthlyInvitePhoneError.Empty -> stringResource(Res.string.monthly_invite_error_phone_empty)
    MonthlyInvitePhoneError.Incomplete -> stringResource(Res.string.monthly_invite_error_phone_incomplete)
    MonthlyInvitePhoneError.NotMobile -> stringResource(Res.string.monthly_invite_error_phone_mobile)
    MonthlyInvitePhoneError.Duplicate -> stringResource(Res.string.monthly_invite_error_phone_duplicate, sheet.duplicateOf.orEmpty())
    MonthlyInvitePhoneError.AlreadyInvited -> stringResource(Res.string.monthly_invite_error_phone_invited)
}

@Composable
private fun MonthlyInviteToastHost(
    toast: MonthlyInviteToast?,
    onIntent: (MonthlyInviteIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    SaqzToast(
        visible = toast != null,
        onDismiss = { onIntent(MonthlyInviteIntent.DismissToast) },
        modifier = modifier.padding(SaqzTheme.metrics.horizontalPadding).testTag(MonthlyInviteTags.Toast),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SaqzToastText(
                text = when (toast) {
                    is MonthlyInviteToast.Sent -> if (toast.count == 1) {
                        stringResource(Res.string.monthly_invite_toast_sent_one)
                    } else {
                        stringResource(Res.string.monthly_invite_toast_sent_many, toast.count)
                    }
                    is MonthlyInviteToast.Removed -> stringResource(Res.string.monthly_invite_toast_removed, toast.name)
                    is MonthlyInviteToast.Promoted -> stringResource(Res.string.monthly_invite_toast_promoted, toast.name)
                    MonthlyInviteToast.Copied -> stringResource(Res.string.monthly_invite_toast_copied)
                    null -> ""
                },
                modifier = Modifier.weight(1f),
            )
            if (toast is MonthlyInviteToast.Removed) {
                SaqzButton(
                    label = stringResource(Res.string.monthly_invite_toast_undo),
                    onClick = { onIntent(MonthlyInviteIntent.Undo) },
                    variant = SaqzButtonVariant.Ghost,
                    contentColor = SaqzTheme.colors.surface,
                    modifier = Modifier.testTag(MonthlyInviteTags.Undo),
                )
            }
        }
    }
}

@Composable
private fun LabelText(text: String) = Text(text, style = SaqzTheme.typography.label, color = SaqzTheme.colors.textPrimary)

@Composable
private fun SupportText(text: String) = Text(text, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)

@Preview
@Composable
private fun MonthlyInviteScreenPreview() = SaqzTheme {
    MonthlyInviteScreen(
        state = MonthlyInviteState(
            isLoading = false,
            groupName = "Vôlei da Quinta",
            senderName = "Lucas",
            defaultFee = MonthlyFeeDefault(8_000, 10),
            people = listOf(
                MonthlyInvitePersonUi("Larissa Prado", "11911122233"),
                MonthlyInvitePersonUi("Diego Farias", "11982223344"),
                MonthlyInvitePersonUi("Marcos Vieira", "11973334455"),
            ),
        ),
        onIntent = {},
        onBack = {},
    )
}
