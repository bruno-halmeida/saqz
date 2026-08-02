package br.com.saqz.groups.presentation.membereditor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzAvatar
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChoiceChip
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzInputKind
import br.com.saqz.designsystem.SaqzSegmented
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzSwitch
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthleteLevel
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.domain.athlete.AthletePreferredSide
import br.com.saqz.groups.domain.group.GroupComposition
import br.com.saqz.groups.domain.group.GroupModality
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.member_editor_absences
import br.com.saqz.groups.resources.member_editor_admin
import br.com.saqz.groups.resources.member_editor_admin_hint
import br.com.saqz.groups.resources.member_editor_advanced
import br.com.saqz.groups.resources.member_editor_attendance
import br.com.saqz.groups.resources.member_editor_attendance_value
import br.com.saqz.groups.resources.member_editor_beginner
import br.com.saqz.groups.resources.member_editor_billing_amount
import br.com.saqz.groups.resources.member_editor_billing_description
import br.com.saqz.groups.resources.member_editor_billing_due_day
import br.com.saqz.groups.resources.member_editor_billing_record
import br.com.saqz.groups.resources.member_editor_billing_save
import br.com.saqz.groups.resources.member_editor_billing_title
import br.com.saqz.groups.resources.member_editor_cancel
import br.com.saqz.groups.resources.member_editor_change_billing
import br.com.saqz.groups.resources.member_editor_height
import br.com.saqz.groups.resources.member_editor_height_hint
import br.com.saqz.groups.resources.member_editor_intermediate
import br.com.saqz.groups.resources.member_editor_joined
import br.com.saqz.groups.resources.member_editor_level
import br.com.saqz.groups.resources.member_editor_list_name
import br.com.saqz.groups.resources.member_editor_make_monthly
import br.com.saqz.groups.resources.member_editor_membership
import br.com.saqz.groups.resources.member_editor_monthly
import br.com.saqz.groups.resources.member_editor_monthly_card
import br.com.saqz.groups.resources.member_editor_monthly_explanation
import br.com.saqz.groups.resources.member_editor_none
import br.com.saqz.groups.resources.member_editor_nickname
import br.com.saqz.groups.resources.member_editor_nickname_hint
import br.com.saqz.groups.resources.member_editor_operation_failure
import br.com.saqz.groups.resources.member_editor_position
import br.com.saqz.groups.resources.member_editor_remove
import br.com.saqz.groups.resources.member_editor_remove_body
import br.com.saqz.groups.resources.member_editor_remove_confirm
import br.com.saqz.groups.resources.member_editor_remove_monthly
import br.com.saqz.groups.resources.member_editor_remove_title
import br.com.saqz.groups.resources.member_editor_save
import br.com.saqz.groups.resources.member_editor_secondary_position
import br.com.saqz.groups.resources.member_editor_side
import br.com.saqz.groups.resources.member_editor_side_both
import br.com.saqz.groups.resources.member_editor_side_left
import br.com.saqz.groups.resources.member_editor_side_right
import br.com.saqz.groups.resources.member_editor_single_game
import br.com.saqz.groups.resources.member_editor_stats
import br.com.saqz.groups.resources.member_editor_title
import br.com.saqz.groups.resources.member_editor_unknown_value
import br.com.saqz.groups.resources.member_editor_games
import br.com.saqz.groups.resources.member_editor_due_day_accessibility
import br.com.saqz.groups.resources.member_editor_failure_access
import br.com.saqz.groups.resources.member_editor_failure_generic
import br.com.saqz.groups.resources.member_editor_failure_not_found
import br.com.saqz.groups.resources.member_editor_position_central
import br.com.saqz.groups.resources.member_editor_position_libero
import br.com.saqz.groups.resources.member_editor_position_opposite_female
import br.com.saqz.groups.resources.member_editor_position_opposite_male
import br.com.saqz.groups.resources.member_editor_position_outside_female
import br.com.saqz.groups.resources.member_editor_position_outside_male
import br.com.saqz.groups.resources.member_editor_position_setter_female
import br.com.saqz.groups.resources.member_editor_position_setter_male
import br.com.saqz.groups.resources.member_editor_retry
import org.jetbrains.compose.resources.stringResource

object MemberEditorTags {
    const val Screen = "member-editor-screen"
    const val Nickname = "member-editor-nickname"
    const val Admin = "member-editor-admin"
    const val Billing = "member-editor-billing"
    const val Remove = "member-editor-remove"
    const val Save = "member-editor-save"
    const val BillingAmount = "member-editor-billing-amount"
    const val BillingSave = "member-editor-billing-save"
    const val RemoveConfirm = "member-editor-remove-confirm"
    const val OperationError = "member-editor-operation-error"

    fun position(position: AthletePosition) = "member-editor-position-${position.name}"

    fun secondary(position: AthletePosition?) = "member-editor-secondary-${position?.name ?: "none"}"

    fun level(level: AthleteLevel) = "member-editor-level-${level.name}"

    fun side(side: AthletePreferredSide) = "member-editor-side-${side.name}"
}

@Composable
fun MemberEditorScreen(
    state: MemberEditorState,
    onIntent: (MemberEditorIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    val screenTitle = stringResource(Res.string.member_editor_title)
    val saveLabel = stringResource(Res.string.member_editor_save)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background)
            .semantics { contentDescription = screenTitle },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SaqzTopAppBar(title = stringResource(Res.string.member_editor_title), onBack = onBack)
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SaqzSpinner()
                }
                state.loadFailed -> MemberEditorFailure(state.error, onRetry = { onIntent(MemberEditorIntent.Retry) })
                else -> Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .testTag(MemberEditorTags.Screen)
                        .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
                    verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
                ) {
                    if (state.error != null) {
                        MemberEditorOperationError()
                    }
                    MemberEditorHeader(state)
                    MemberEditorIdentity(state, onIntent)
                    MemberEditorAttributes(state, onIntent)
                    MemberEditorMembership(state, onIntent)
                    MemberEditorPermissions(state, onIntent)
                    MemberEditorStats(state)
                    MemberEditorRemove(state, onIntent)
                    SaqzButton(
                        label = stringResource(Res.string.member_editor_save),
                        onClick = { onIntent(MemberEditorIntent.Save) },
                        fullWidth = true,
                        loading = state.operation == MemberEditorOperation.Save,
                        enabled = state.operation == null,
                        modifier = Modifier.semantics { contentDescription = saveLabel }
                            .testTag(MemberEditorTags.Save),
                    )
                    Spacer(Modifier.size(metrics.blockGap))
                }
            }
        }
        MemberEditorBillingSheet(state, onIntent)
        MemberEditorRemoveSheet(state, onIntent)
    }
}

@Composable
private fun MemberEditorOperationError() {
    SaqzCard(modifier = Modifier.testTag(MemberEditorTags.OperationError)) {
        Text(
            text = stringResource(Res.string.member_editor_operation_failure),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.errorForeground,
        )
    }
}

@Composable
private fun MemberEditorHeader(state: MemberEditorState) {
    val metrics = SaqzTheme.metrics
    Column(verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SaqzAvatar(state.name, size = metrics.avatarSize)
            Column(verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
                Text(
                    text = state.name,
                    style = SaqzTheme.typography.title.copy(fontWeight = FontWeight.Bold),
                    color = SaqzTheme.colors.textPrimary,
                )
                Text(
                    text = stringResource(
                        Res.string.member_editor_joined,
                        state.joinedAtLabel.ifBlank { stringResource(Res.string.member_editor_unknown_value) },
                        state.games,
                    ),
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun MemberEditorIdentity(state: MemberEditorState, onIntent: (MemberEditorIntent) -> Unit) {
    MemberEditorSection(stringResource(Res.string.member_editor_list_name)) {
        SaqzInput(
            value = state.nickname,
            onValueChange = { onIntent(MemberEditorIntent.NicknameChanged(it)) },
            label = stringResource(Res.string.member_editor_nickname),
            placeholder = stringResource(Res.string.member_editor_nickname_hint),
            enabled = state.operation == null,
            modifier = Modifier.testTag(MemberEditorTags.Nickname),
        )
    }
}

@Composable
private fun MemberEditorAttributes(state: MemberEditorState, onIntent: (MemberEditorIntent) -> Unit) {
    val metrics = SaqzTheme.metrics
    MemberEditorSection(state.modality?.let { stringResource(Res.string.member_editor_position) }) {
        when (state.modality) {
            GroupModality.COURT_VOLLEYBALL -> {
                MemberEditorChoiceRow(
                    options = AthletePosition.entries,
                    selected = state.position,
                    label = { positionLabel(it, state.composition) },
                    onSelect = { onIntent(MemberEditorIntent.PositionSelected(it)) },
                    tag = MemberEditorTags::position,
                )
                Text(
                    text = stringResource(Res.string.member_editor_secondary_position),
                    style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
                    color = SaqzTheme.colors.textPrimary,
                )
                MemberEditorChoiceRow(
                    options = listOf(null) + AthletePosition.entries.filter { it != state.position },
                    selected = state.secondaryPosition,
                    label = { it?.let { positionLabel(it, state.composition) } ?: stringResource(Res.string.member_editor_none) },
                    onSelect = { onIntent(MemberEditorIntent.SecondaryPositionSelected(it)) },
                    tag = MemberEditorTags::secondary,
                )
                SaqzInput(
                    value = state.heightText,
                    onValueChange = { onIntent(MemberEditorIntent.HeightChanged(it.filter(Char::isDigit))) },
                    label = stringResource(Res.string.member_editor_height),
                    placeholder = stringResource(Res.string.member_editor_height_hint),
                    keyboardType = KeyboardType.Number,
                    enabled = state.operation == null,
                )
            }
            GroupModality.BEACH_VOLLEYBALL, GroupModality.FOOTVOLLEY -> {
                Text(
                    text = stringResource(Res.string.member_editor_side),
                    style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
                    color = SaqzTheme.colors.textPrimary,
                )
                MemberEditorChoiceRow(
                    options = AthletePreferredSide.entries,
                    selected = state.preferredSide,
                    label = { sideLabel(it) },
                    onSelect = { onIntent(MemberEditorIntent.PreferredSideSelected(it)) },
                    tag = MemberEditorTags::side,
                )
            }
            null -> Unit
        }
        Text(
            text = stringResource(Res.string.member_editor_level),
            style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
            color = SaqzTheme.colors.textPrimary,
        )
        MemberEditorChoiceRow(
            options = AthleteLevel.entries,
            selected = state.level,
            label = { levelLabel(it) },
            onSelect = { onIntent(MemberEditorIntent.LevelSelected(it)) },
            tag = MemberEditorTags::level,
        )
        Spacer(Modifier.size(metrics.subGrid))
    }
}

@Composable
private fun MemberEditorMembership(state: MemberEditorState, onIntent: (MemberEditorIntent) -> Unit) {
    val metrics = SaqzTheme.metrics
    val membershipLabel = stringResource(Res.string.member_editor_membership)
    MemberEditorSection(stringResource(Res.string.member_editor_membership)) {
        SaqzSegmented(
            options = listOf(
                stringResource(Res.string.member_editor_monthly),
                stringResource(Res.string.member_editor_single_game),
            ),
            selected = if (state.membershipType == AthleteMembershipType.MENSALISTA) 0 else 1,
            onSelect = { selected ->
                onIntent(
                    MemberEditorIntent.MembershipSelected(
                        if (selected == 0) AthleteMembershipType.MENSALISTA else AthleteMembershipType.AVULSO,
                    ),
                )
            },
            modifier = Modifier.semantics { contentDescription = membershipLabel },
        )
        Text(
            text = stringResource(Res.string.member_editor_monthly_explanation),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
        )
        if (state.membershipType == AthleteMembershipType.MENSALISTA) {
            SaqzCard(tone = br.com.saqz.designsystem.SaqzCardTone.Soft) {
                Text(
                    text = stringResource(
                        Res.string.member_editor_monthly_card,
                        formatCurrency(state.effectiveMonthlyFeeCents),
                        state.effectiveMonthlyDueDay ?: 10,
                    ),
                    style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = SaqzTheme.colors.textPrimary,
                )
                SaqzButton(
                    label = stringResource(Res.string.member_editor_change_billing),
                    onClick = { onIntent(MemberEditorIntent.OpenBilling) },
                    variant = SaqzButtonVariant.Secondary,
                    size = SaqzButtonSize.Sm,
                    fullWidth = true,
                    enabled = state.operation == null,
                    modifier = Modifier.testTag(MemberEditorTags.Billing),
                )
            }
        } else {
            SaqzButton(
                label = stringResource(Res.string.member_editor_make_monthly),
                onClick = { onIntent(MemberEditorIntent.OpenBilling) },
                variant = SaqzButtonVariant.Secondary,
                fullWidth = true,
                enabled = state.operation == null,
                modifier = Modifier.testTag(MemberEditorTags.Billing),
            )
        }
        Spacer(Modifier.size(metrics.subGrid))
    }
}

@Composable
private fun MemberEditorPermissions(state: MemberEditorState, onIntent: (MemberEditorIntent) -> Unit) {
    MemberEditorSection {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            SaqzSwitch(
                checked = state.role == br.com.saqz.groups.domain.group.GroupRole.ADMIN,
                onCheckedChange = { onIntent(MemberEditorIntent.AdminChanged(it)) },
                label = stringResource(Res.string.member_editor_admin),
                enabled = !state.isOwner && state.operation == null,
                modifier = Modifier.testTag(MemberEditorTags.Admin),
            )
            Text(
                text = stringResource(Res.string.member_editor_admin_hint),
                style = SaqzTheme.typography.support,
                color = if (state.isOwner) SaqzTheme.colors.disabledForeground else SaqzTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun MemberEditorStats(state: MemberEditorState) {
    MemberEditorSection(stringResource(Res.string.member_editor_stats)) {
        SaqzCard(padded = false) {
            Row(modifier = Modifier.fillMaxWidth()) {
                MemberEditorStatCell(state.games.toString(), stringResource(Res.string.member_editor_games), Modifier.weight(1f))
                SaqzDivider(vertical = true)
                MemberEditorStatCell(
                    state.attendanceRate?.let { stringResource(Res.string.member_editor_attendance_value, it) }
                        ?: stringResource(Res.string.member_editor_unknown_value),
                    stringResource(Res.string.member_editor_attendance),
                    Modifier.weight(1f),
                )
                SaqzDivider(vertical = true)
                MemberEditorStatCell(state.absences.toString(), stringResource(Res.string.member_editor_absences), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MemberEditorStatCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(vertical = SaqzTheme.metrics.blockGap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
    ) {
        Text(value, style = SaqzTheme.typography.subtitle, color = SaqzTheme.colors.textPrimary)
        Text(label, style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
    }
}

@Composable
private fun MemberEditorRemove(state: MemberEditorState, onIntent: (MemberEditorIntent) -> Unit) {
    Text(
        text = stringResource(Res.string.member_editor_remove),
        style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
        color = SaqzTheme.colors.errorForeground,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SaqzTheme.metrics.minimumTouchTarget)
            .clickable(
                enabled = state.operation == null,
                onClickLabel = stringResource(Res.string.member_editor_remove),
                role = Role.Button,
                onClick = { onIntent(MemberEditorIntent.OpenRemove) },
            )
            .padding(vertical = SaqzTheme.metrics.blockGap)
            .testTag(MemberEditorTags.Remove),
    )
}

@Composable
private fun MemberEditorSection(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
        if (title != null) {
            Text(
                text = title,
                style = SaqzTheme.typography.subtitle.copy(fontWeight = FontWeight.Bold),
                color = SaqzTheme.colors.textPrimary,
            )
        }
        content()
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> MemberEditorChoiceRow(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    tag: (T) -> String,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
    ) {
        options.forEach { option ->
            SaqzChoiceChip(
                label = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                modifier = Modifier.testTag(tag(option)),
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun MemberEditorBillingSheet(state: MemberEditorState, onIntent: (MemberEditorIntent) -> Unit) {
    SaqzBottomSheet(
        open = state.billingSheetOpen,
        onClose = { onIntent(MemberEditorIntent.DismissBilling) },
        title = stringResource(
            Res.string.member_editor_billing_title,
            state.nickname.takeIf(String::isNotBlank) ?: state.name,
        ),
        description = stringResource(Res.string.member_editor_billing_description),
        splitFooter = {
            SaqzButton(
                label = stringResource(Res.string.member_editor_cancel),
                onClick = { onIntent(MemberEditorIntent.DismissBilling) },
                variant = SaqzButtonVariant.Ghost,
                modifier = Modifier.weight(1f),
            )
            SaqzButton(
                label = stringResource(Res.string.member_editor_billing_save),
                onClick = { onIntent(MemberEditorIntent.SaveBilling) },
                loading = state.operation == MemberEditorOperation.Billing,
                enabled = state.operation == null,
                modifier = Modifier.weight(1f).testTag(MemberEditorTags.BillingSave),
            )
        },
    ) {
        SaqzInput(
            value = state.billingAmountText,
            onValueChange = { onIntent(MemberEditorIntent.BillingAmountChanged(it.filter { char -> char.isDigit() || char == ',' })) },
            label = stringResource(Res.string.member_editor_billing_amount),
            kind = SaqzInputKind.Text,
            keyboardType = KeyboardType.Decimal,
            leadingContent = { Text("R$", color = SaqzTheme.colors.textSecondary) },
            enabled = state.operation == null,
            modifier = Modifier.testTag(MemberEditorTags.BillingAmount),
        )
        Text(
            text = stringResource(Res.string.member_editor_billing_due_day),
            style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
            color = SaqzTheme.colors.textPrimary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                listOf(5, 10, 15, 20).forEach { day ->
                val dueDayLabel = stringResource(Res.string.member_editor_due_day_accessibility, day)
                SaqzChoiceChip(
                    label = day.toString(),
                    selected = day == state.billingDueDay,
                    onClick = { onIntent(MemberEditorIntent.BillingDueDaySelected(day)) },
                    modifier = Modifier.semantics {
                        contentDescription = dueDayLabel
                    },
                )
            }
        }
        Text(
            // Decisão de produto do VUL-144: registro simples de mensalidade; não há cobrança automática via Pix.
            text = stringResource(Res.string.member_editor_billing_record),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun MemberEditorRemoveSheet(state: MemberEditorState, onIntent: (MemberEditorIntent) -> Unit) {
    SaqzBottomSheet(
        open = state.removeSheetOpen,
        onClose = { onIntent(MemberEditorIntent.DismissRemove) },
        title = stringResource(Res.string.member_editor_remove_title, state.name),
        splitFooter = {
            SaqzButton(
                label = stringResource(Res.string.member_editor_cancel),
                onClick = { onIntent(MemberEditorIntent.DismissRemove) },
                variant = SaqzButtonVariant.Ghost,
                modifier = Modifier.weight(1f),
            )
            SaqzButton(
                label = stringResource(Res.string.member_editor_remove_confirm),
                onClick = { onIntent(MemberEditorIntent.ConfirmRemove) },
                variant = SaqzButtonVariant.Danger,
                loading = state.operation == MemberEditorOperation.Remove,
                enabled = state.operation == null,
                modifier = Modifier.weight(1f).testTag(MemberEditorTags.RemoveConfirm),
            )
        },
    ) {
        Text(
            text = stringResource(Res.string.member_editor_remove_body),
            style = SaqzTheme.typography.body,
            color = SaqzTheme.colors.textSecondary,
        )
        if (state.membershipType == AthleteMembershipType.MENSALISTA) {
            SaqzStatusChip(
                text = stringResource(Res.string.member_editor_remove_monthly),
                tone = br.com.saqz.designsystem.SaqzChipTone.Warning,
            )
        }
    }
}

@Composable
private fun MemberEditorFailure(error: GroupUiError?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(SaqzTheme.metrics.horizontalPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when (error) {
                GroupUiError.AccessDenied -> stringResource(Res.string.member_editor_failure_access)
                GroupUiError.NotFound -> stringResource(Res.string.member_editor_failure_not_found)
                else -> stringResource(Res.string.member_editor_failure_generic)
            },
            style = SaqzTheme.typography.body,
            color = SaqzTheme.colors.textPrimary,
        )
        SaqzButton(
            label = stringResource(Res.string.member_editor_retry),
            onClick = onRetry,
            variant = SaqzButtonVariant.Secondary,
            size = SaqzButtonSize.Sm,
        )
    }
}

@Composable
private fun positionLabel(position: AthletePosition, composition: GroupComposition?): String = when (position) {
    AthletePosition.LEVANTADOR -> stringResource(
        if (composition == GroupComposition.WOMEN) Res.string.member_editor_position_setter_female
        else Res.string.member_editor_position_setter_male,
    )
    AthletePosition.PONTA -> stringResource(
        if (composition == GroupComposition.WOMEN) Res.string.member_editor_position_outside_female
        else Res.string.member_editor_position_outside_male,
    )
    AthletePosition.CENTRAL -> stringResource(Res.string.member_editor_position_central)
    AthletePosition.OPOSTO -> stringResource(
        if (composition == GroupComposition.WOMEN) Res.string.member_editor_position_opposite_female
        else Res.string.member_editor_position_opposite_male,
    )
    AthletePosition.LIBERO -> stringResource(Res.string.member_editor_position_libero)
}

@Composable
private fun levelLabel(level: AthleteLevel): String = when (level) {
    AthleteLevel.INICIANTE -> stringResource(Res.string.member_editor_beginner)
    AthleteLevel.INTERMEDIARIO -> stringResource(Res.string.member_editor_intermediate)
    AthleteLevel.AVANCADO -> stringResource(Res.string.member_editor_advanced)
}

@Composable
private fun sideLabel(side: AthletePreferredSide): String = when (side) {
    AthletePreferredSide.DIREITA -> stringResource(Res.string.member_editor_side_right)
    AthletePreferredSide.ESQUERDA -> stringResource(Res.string.member_editor_side_left)
    AthletePreferredSide.TANTO_FAZ -> stringResource(Res.string.member_editor_side_both)
}

@Composable
private fun formatCurrency(cents: Long?): String {
    if (cents == null) return stringResource(Res.string.member_editor_unknown_value)
    val reais = cents / 100
    val centavos = (cents % 100).toString().padStart(2, '0')
    return "$reais,$centavos"
}

@Preview(name = "3g mensalista", widthDp = 390, heightDp = 844)
@Composable
private fun MemberEditorMonthlyPreview() = SaqzTheme {
    MemberEditorScreen(
        state = memberEditorPreviewState,
        onIntent = {},
        onBack = {},
    )
}

@Preview(name = "3g avulso", widthDp = 390, heightDp = 844)
@Composable
private fun MemberEditorSingleGamePreview() = SaqzTheme {
    MemberEditorScreen(
        state = memberEditorPreviewState.copy(membershipType = AthleteMembershipType.AVULSO),
        onIntent = {},
        onBack = {},
    )
}

internal val memberEditorPreviewState = MemberEditorState(
    isLoading = false,
    name = "Bia Souza",
    displayName = "Beatriz Souza",
    nickname = "Bia",
    joinedAtLabel = "março",
    games = 18,
    attendanceRate = 92,
    absences = 2,
    modality = GroupModality.COURT_VOLLEYBALL,
    composition = GroupComposition.WOMEN,
    position = AthletePosition.PONTA,
    secondaryPosition = AthletePosition.LEVANTADOR,
    level = AthleteLevel.AVANCADO,
    heightCm = 178,
    heightText = "178",
    membershipType = AthleteMembershipType.MENSALISTA,
    monthlyFeeOverrideCents = null,
    defaultMonthlyFeeCents = 8500,
    defaultMonthlyDueDay = 10,
    billingAmountText = "85,00",
    billingDueDay = 10,
    role = br.com.saqz.groups.domain.group.GroupRole.ADMIN,
)
