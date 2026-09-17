package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupAgendaStatus
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.ui.components.UpcomingGameRow
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_details_agenda_more
import br.com.saqz.groups.resources.group_details_agenda_more_one
import br.com.saqz.groups.resources.home_admin_shortcuts_create_game
import br.com.saqz.groups.resources.home_upcoming_title
import org.jetbrains.compose.resources.stringResource

/** Linhas à vista antes do "Ver mais". */
private const val AgendaCollapsedCount = 3

/**
 * "Próximos jogos" do grupo, sem o jogo do hero: data, hora, quantos confirmaram e a resposta
 * do próprio usuário. Toque abre o jogo; "Ver mais" expande no lugar e some. Sem jogos a seção
 * não existe — o "Marcar jogo" de quem não tem agenda mora no hero. Para o gestor, a mesma
 * ação fica no cabeçalho.
 */
@Composable
internal fun GroupAgendaBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.agenda.isEmpty()) return
    val metrics = SaqzTheme.metrics
    // Só visual: não é dado do grupo, então não sobe para o ViewModel.
    var expanded by rememberSaveable { mutableStateOf(false) }
    val visible = if (expanded) state.agenda else state.agenda.take(AgendaCollapsedCount)
    val hidden = state.agenda.size - visible.size
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
        SaqzSectionHeader(
            title = stringResource(Res.string.home_upcoming_title),
            // O header não expõe modifier para a ação: a tag fica nele e o clicável é descendente.
            modifier = if (state.isAdmin) Modifier.testTag(GroupDetailsTags.AgendaCreate) else Modifier,
            action = if (state.isAdmin) stringResource(Res.string.home_admin_shortcuts_create_game) else null,
            onAction = { onIntent(GroupDetailsIntent.CreateNextGame) },
        )
        SaqzCard(modifier = Modifier.testTag(GroupDetailsTags.Agenda), padded = false) {
            visible.forEachIndexed { index, row ->
                if (index > 0) SaqzDivider()
                UpcomingGameRow(
                    day = row.day,
                    month = row.month,
                    title = row.title,
                    meta = row.meta,
                    contentDescription = row.contentDescription,
                    onClick = { onIntent(GroupDetailsIntent.OpenAgendaGame(row.gameId)) },
                    tag = GroupDetailsTags.agendaGame(row.gameId),
                ) {
                    SaqzStatusChip(
                        text = row.statusLabel,
                        tone = when (row.status) {
                            GroupAgendaStatus.Going -> SaqzChipTone.Success
                            GroupAgendaStatus.Waitlisted -> SaqzChipTone.Warning
                            GroupAgendaStatus.Draft -> SaqzChipTone.Brand
                            GroupAgendaStatus.Pending, GroupAgendaStatus.Out -> SaqzChipTone.Neutral
                        },
                        dot = row.status == GroupAgendaStatus.Going || row.status == GroupAgendaStatus.Waitlisted,
                    )
                }
            }
            if (hidden > 0) {
                SaqzDivider()
                SaqzButton(
                    label = if (hidden == 1) {
                        stringResource(Res.string.group_details_agenda_more_one)
                    } else {
                        stringResource(Res.string.group_details_agenda_more, hidden)
                    },
                    onClick = { expanded = true },
                    // O Sm nasce com 44; o alvo de toque da casa é 48.
                    modifier = Modifier
                        .testTag(GroupDetailsTags.AgendaMore)
                        .heightIn(min = metrics.minimumTouchTarget),
                    variant = SaqzButtonVariant.Ghost,
                    size = SaqzButtonSize.Sm,
                    fullWidth = true,
                )
            }
        }
    }
}
