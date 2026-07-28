package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupLevel
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupSheet
import br.com.saqz.groups.presentation.setup.GroupSetupState
import br.com.saqz.groups.presentation.ui.components.GroupSlotPicker
import br.com.saqz.groups.presentation.ui.components.SlotDraft
import br.com.saqz.groups.presentation.ui.label
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_delete_body
import br.com.saqz.groups.resources.group_delete_cancel
import br.com.saqz.groups.resources.group_delete_confirm
import br.com.saqz.groups.resources.group_delete_title
import br.com.saqz.groups.resources.group_setup_level_hint
import br.com.saqz.groups.resources.group_setup_level_label
import br.com.saqz.groups.resources.group_setup_modality_hint
import br.com.saqz.groups.resources.group_setup_modality_label
import br.com.saqz.groups.resources.group_slot_save
import br.com.saqz.groups.resources.group_slot_title
import org.jetbrains.compose.resources.stringResource

private const val SoftTintAlpha = 0.1f
private const val DeleteIconFactor = 2

/**
 * Um campo `sheet` só, nunca quatro booleanos: cada folha abre pelo tipo do estado. As
 * quatro são `SaqzBottomSheet`, que já traz alça, fechar, divisórias e o back do sistema.
 */
@Composable
internal fun GroupSetupSheets(
    state: GroupSetupState,
    onIntent: (GroupSetupIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheet = state.sheet
    val close = { onIntent(GroupSetupIntent.CloseSheet) }

    Box(modifier = modifier.fillMaxSize()) {
        SaqzBottomSheet(
            open = sheet is GroupSetupSheet.Modality,
            onClose = close,
            title = stringResource(Res.string.group_setup_modality_label),
            description = stringResource(Res.string.group_setup_modality_hint),
        ) {
            GroupOptionList(
                options = GroupModality.entries,
                selected = state.form.modality,
                label = { it.label() },
                onSelect = { onIntent(GroupSetupIntent.SelectModality(it)) },
            )
        }

        SaqzBottomSheet(
            open = sheet is GroupSetupSheet.Level,
            onClose = close,
            title = stringResource(Res.string.group_setup_level_label),
            description = stringResource(Res.string.group_setup_level_hint),
        ) {
            GroupOptionList(
                options = GroupLevel.entries,
                selected = state.form.level,
                label = { it.label() },
                onSelect = { onIntent(GroupSetupIntent.SelectLevel(it)) },
            )
        }

        SaqzBottomSheet(
            open = sheet is GroupSetupSheet.Slot,
            onClose = close,
            title = stringResource(Res.string.group_slot_title),
            footer = {
                SaqzButton(
                    label = stringResource(Res.string.group_slot_save),
                    onClick = { onIntent(GroupSetupIntent.ConfirmSlot) },
                    fullWidth = true,
                    modifier = Modifier.testTag(GroupSetupTags.SlotSave),
                )
            },
        ) {
            GroupSlotPicker(
                draft = SlotDraft(
                    weekday = state.slotDraft.weekday,
                    hour = state.slotDraft.hour,
                    minute = state.slotDraft.minute,
                ),
                onDayPick = { onIntent(GroupSetupIntent.PickSlotWeekday(it)) },
                onTimePick = { hour, minute -> onIntent(GroupSetupIntent.PickSlotTime(hour, minute)) },
            )
        }

        GroupDeleteSheet(
            open = sheet is GroupSetupSheet.ConfirmDelete,
            groupName = state.form.name,
            memberCount = state.memberCount,
            onConfirm = { onIntent(GroupSetupIntent.ConfirmDelete) },
            onClose = close,
        )
    }
}

/** `2j` — ícone de lixeira em vermelho, pergunta com o nome do grupo e as duas saídas. */
@Composable
private fun GroupDeleteSheet(
    open: Boolean,
    groupName: String,
    memberCount: Int,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    SaqzBottomSheet(
        open = open,
        onClose = onClose,
        modifier = modifier,
        footer = {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.grid)) {
                SaqzButton(
                    label = stringResource(Res.string.group_delete_confirm),
                    onClick = onConfirm,
                    variant = SaqzButtonVariant.Danger,
                    fullWidth = true,
                    modifier = Modifier.testTag(GroupSetupTags.DeleteConfirm),
                )
                SaqzButton(
                    label = stringResource(Res.string.group_delete_cancel),
                    onClick = onClose,
                    variant = SaqzButtonVariant.Ghost,
                    fullWidth = true,
                )
            }
        },
    ) {
        Box(
            modifier = Modifier
                .size(metrics.sectionGap * DeleteIconFactor + metrics.grid)
                .clip(CircleShape)
                .background(colors.errorForeground.copy(alpha = SoftTintAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            SaqzIcon(SaqzIcons.Trash, tint = colors.errorForeground, size = metrics.sectionGap)
        }
        Text(
            text = stringResource(Res.string.group_delete_title, groupName),
            style = SaqzTheme.typography.title.copy(fontWeight = FontWeight.Bold),
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(Res.string.group_delete_body, memberCount),
            style = SaqzTheme.typography.body,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun <T> GroupOptionList(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.blockRadius))
            .background(SaqzTheme.colors.surface),
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) SaqzDivider()
            GroupOptionRow(
                label = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun GroupOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val metrics = SaqzTheme.metrics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = metrics.buttonHeight)
            .clickable(onClickLabel = label, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = SaqzTheme.typography.label,
            color = SaqzTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (selected) SaqzIcon(SaqzIcons.Check, tint = SaqzTheme.colors.primary)
    }
}
