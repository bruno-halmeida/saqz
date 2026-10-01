package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupChecklistItem
import br.com.saqz.groups.presentation.details.GroupChecklistRowUi
import br.com.saqz.groups.presentation.details.GroupChecklistUi
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.ui.home.HomeStepRow
import br.com.saqz.groups.presentation.ui.home.HomeStepState
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.checklist_mensalistas_body
import br.com.saqz.groups.resources.checklist_mensalistas_title
import br.com.saqz.groups.resources.checklist_pix_body
import br.com.saqz.groups.resources.checklist_pix_title
import br.com.saqz.groups.resources.checklist_recurrence_body
import br.com.saqz.groups.resources.checklist_recurrence_title
import br.com.saqz.groups.resources.checklist_rules_body
import br.com.saqz.groups.resources.checklist_rules_title
import br.com.saqz.groups.resources.checklist_snooze
import br.com.saqz.groups.resources.checklist_title
import br.com.saqz.groups.resources.checklist_whatsapp_body
import br.com.saqz.groups.resources.checklist_whatsapp_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * "Deixe o grupo redondo": os cinco ajustes que fazem o Saqz valer a pena, um "Agora" por vez,
 * cada um com o porquê em uma linha. Toda linha leva direto à tela da ação. Entra quando os
 * três passos do guia terminam, só no primeiro grupo da conta, e some quando os cinco estão
 * feitos. "Deixar para depois" esconde por uma semana.
 */
@Composable
internal fun GroupChecklistBlock(
    checklist: GroupChecklistUi,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(GroupDetailsTags.Checklist),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.checklist_title))
        SaqzCard(padded = false) {
            checklist.rows.forEachIndexed { index, row ->
                if (index > 0) SaqzDivider()
                HomeStepRow(
                    number = index + 1,
                    title = stringResource(row.item.title()),
                    body = stringResource(row.item.body()),
                    state = when {
                        row.done -> HomeStepState.Done
                        row.item == checklist.current -> HomeStepState.Now
                        else -> HomeStepState.Next
                    },
                    onClick = { onIntent(GroupDetailsIntent.ChecklistAction(row.item)) },
                    modifier = Modifier.testTag(GroupDetailsTags.checklistItem(row.item)),
                )
            }
        }
        SaqzButton(
            label = stringResource(Res.string.checklist_snooze),
            onClick = { onIntent(GroupDetailsIntent.SnoozeChecklist) },
            variant = SaqzButtonVariant.Ghost,
            modifier = Modifier.testTag(GroupDetailsTags.ChecklistSnooze),
        )
    }
}

private fun GroupChecklistItem.title(): StringResource = when (this) {
    GroupChecklistItem.WhatsApp -> Res.string.checklist_whatsapp_title
    GroupChecklistItem.Mensalistas -> Res.string.checklist_mensalistas_title
    GroupChecklistItem.Pix -> Res.string.checklist_pix_title
    GroupChecklistItem.Rules -> Res.string.checklist_rules_title
    GroupChecklistItem.Recurrence -> Res.string.checklist_recurrence_title
}

private fun GroupChecklistItem.body(): StringResource = when (this) {
    GroupChecklistItem.WhatsApp -> Res.string.checklist_whatsapp_body
    GroupChecklistItem.Mensalistas -> Res.string.checklist_mensalistas_body
    GroupChecklistItem.Pix -> Res.string.checklist_pix_body
    GroupChecklistItem.Rules -> Res.string.checklist_rules_body
    GroupChecklistItem.Recurrence -> Res.string.checklist_recurrence_body
}

internal val GroupChecklistPreview = GroupChecklistUi(
    rows = listOf(
        GroupChecklistRowUi(GroupChecklistItem.WhatsApp, done = false),
        GroupChecklistRowUi(GroupChecklistItem.Mensalistas, done = false),
        GroupChecklistRowUi(GroupChecklistItem.Pix, done = true),
        GroupChecklistRowUi(GroupChecklistItem.Rules, done = false),
        GroupChecklistRowUi(GroupChecklistItem.Recurrence, done = false),
    ),
)
