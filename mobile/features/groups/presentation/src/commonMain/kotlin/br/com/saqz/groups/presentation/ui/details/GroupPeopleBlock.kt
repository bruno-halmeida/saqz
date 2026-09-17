package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState

/**
 * Andaime (T): membro vê a prévia de membros e o convite; gestor vê o caixa e "Gerenciar". O
 * C2 troca por "Galera" (membro) e "Gestão" (gestor). A linha do caixa fica AQUI, e não no
 * bloco "Esperando você", porque a tag `group-details-cashbox` só pode existir uma vez na
 * árvore e o destino final dela é a lista de gestão.
 */
@Composable
internal fun GroupPeopleBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(GroupDetailsTags.People),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        if (state.isAdmin) {
            state.cashbox?.let { GroupCashboxRow(cashbox = it, onIntent = onIntent) }
            GroupManageList(
                memberCount = state.memberCount,
                scheduleSummary = state.scheduleSummary,
                onIntent = onIntent,
            )
        } else {
            GroupMemberPreview(members = state.memberPreview, onIntent = onIntent)
            GroupInviteCard(onIntent = onIntent)
        }
    }
}

/** Andaime (T): a quadra padrão, quando não há jogo marcado, chega no ticket C2. */
@Suppress("UnusedParameter")
@Composable
internal fun GroupHomeCourtBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) = Unit

/** Dono não sai do grupo: para ele o bloco não emite nada. */
@Composable
internal fun GroupLeaveBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isOwner) return
    GroupLeaveButton(onIntent = onIntent, modifier = modifier)
}
