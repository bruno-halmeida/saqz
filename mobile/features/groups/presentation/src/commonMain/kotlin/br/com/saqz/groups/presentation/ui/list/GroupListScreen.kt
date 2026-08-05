package br.com.saqz.groups.presentation.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzBottomNav
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzNavItem
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.presentation.list.GroupCardAttendance
import br.com.saqz.groups.presentation.list.GroupCardGameUi
import br.com.saqz.groups.presentation.list.GroupCardUi
import br.com.saqz.groups.presentation.list.GroupInviteUi
import br.com.saqz.groups.presentation.list.GroupListIntent
import br.com.saqz.groups.presentation.list.GroupListState

internal object GroupListTags {
    const val Create = "group-list-create"
    const val Empty = "group-list-empty"
    const val Failure = "group-list-failure"
    const val InviteAccept = "group-list-invite-accept"
    const val InviteDecline = "group-list-invite-decline"

    fun group(id: String) = "group-list-group-$id"
}

// O rodapé de 20 do export não tem token: a lista respira embaixo da barra do shell.
private val ListBottomPadding = 20.dp

/**
 * 2n · lista de grupos e 2o · primeiro acesso. A tela não desenha: empilha as seções.
 *
 * O `SaqzBottomNav` é do shell — entra no ticket de ligação, não aqui.
 */
@Composable
fun GroupListScreen(
    state: GroupListState,
    onIntent: (GroupListIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    val onCreate: () -> Unit = { onIntent(GroupListIntent.CreateGroup) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background),
    ) {
        GroupListHeader(
            groupCount = state.groups.size,
            awaitingConfirmation = state.awaitingConfirmation,
            // 2o não tem o "+": a única saída são os botões do centro.
            onCreate = onCreate.takeUnless { state.isEmpty },
        )
        when {
            state.isLoading -> GroupListSkeleton()
            state.loadFailed -> GroupListFailure(
                error = state.error,
                onRetry = { onIntent(GroupListIntent.Retry) },
            )
            state.isEmpty -> GroupListEmpty(onCreate = onCreate)

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = metrics.horizontalPadding,
                    end = metrics.horizontalPadding,
                    top = metrics.subGrid,
                    bottom = ListBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
            ) {
                items(state.groups, key = { it.id }) { group ->
                    GroupCard(
                        group = group,
                        onClick = { onIntent(GroupListIntent.OpenGroup(group.id)) },
                    )
                }
                state.invite?.let { invite ->
                    item(key = invite.id) {
                        GroupInviteCard(
                            invite = invite,
                            onAccept = { onIntent(GroupListIntent.AcceptInvite(invite.id)) },
                            onDecline = { onIntent(GroupListIntent.DeclineInvite(invite.id)) },
                        )
                    }
                }
            }
        }
    }
}

// Dado de cena das previews e das capturas Roborazzi — os três cartões do 2n são
// exatamente os do export: admin pedindo confirmação, membro que vai, e o sem jogo.
internal object GroupListSamples {
    val filled = GroupListState(
        isLoading = false,
        groups = listOf(
            GroupCardUi(
                id = "ceret",
                name = "Vôlei do CERET",
                meta = "Quadra · Tatuapé · 26 membros",
                modality = GroupModality.COURT_VOLLEYBALL,
                isAdmin = true,
                photoUrl = "https://saqz.example/ceret.png",
                nextGame = GroupCardGameUi(
                    label = "Ter, 28/07 · 19h30 · 9 de 12",
                    attendance = GroupCardAttendance.Pending,
                ),
            ),
            GroupCardUi(
                id = "ibira",
                name = "Areia do Ibira",
                meta = "Areia · Ibirapuera · 14 membros",
                modality = GroupModality.BEACH_VOLLEYBALL,
                isAdmin = false,
                nextGame = GroupCardGameUi(
                    label = "Sáb, 01/08 · 09h00",
                    attendance = GroupCardAttendance.Going,
                ),
            ),
            GroupCardUi(
                id = "vila",
                name = "Futevôlei da Vila",
                meta = "Futevôlei · Vila Mariana · 9 membros",
                modality = GroupModality.FOOTVOLLEY,
                isAdmin = false,
            ),
        ),
        invite = GroupInviteUi(
            id = "convite-firma",
            groupName = "Vôlei da firma",
            invitedBy = "Marina Freitas",
        ),
    )

    val empty = GroupListState(isLoading = false)

    val loading = GroupListState()

    val failed = GroupListState(isLoading = false, loadFailed = true)

    // A barra do membro comum (VUL-200): Jogos saiu do app e a Caixa só aparece para quem
    // administra grupo. Uma amostra serve as quatro cenas da 2n, e o caso base é este — a
    // cena da lista cheia tem um grupo com selo Admin, então aquela pessoa veria a quarta
    // aba; a barra aqui é cenário da célula do export, não retrato do papel. Quem manda é
    // o `shellNavItems` do `SaqzAppShell`; aqui é amostra, porque a feature não enxerga o
    // shell.
    val navItems = listOf(
        SaqzNavItem(id = "inicio", label = "Início", icon = SaqzIcons.Home),
        SaqzNavItem(id = "grupos", label = "Grupos", icon = SaqzIcons.Users),
        SaqzNavItem(id = "perfil", label = "Perfil", icon = SaqzIcons.User),
    )
}

// A barra entra só na cena, para o print bater com a célula do export.
@Composable
private fun GroupListShell(state: GroupListState) = SaqzTheme {
    Column(modifier = Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        GroupListScreen(state = state, onIntent = {}, modifier = Modifier.weight(1f))
        SaqzBottomNav(items = GroupListSamples.navItems, activeId = "grupos", onSelect = {})
    }
}

@Preview(name = "2n — lista cheia", widthDp = 390, heightDp = 844)
@Composable
private fun GroupListFilledPreview() = GroupListShell(GroupListSamples.filled)

@Preview(name = "2o — primeiro acesso", widthDp = 390, heightDp = 844)
@Composable
private fun GroupListEmptyPreview() = GroupListShell(GroupListSamples.empty)

@Preview(name = "2n — carregando", widthDp = 390, heightDp = 844)
@Composable
private fun GroupListLoadingPreview() = GroupListShell(GroupListSamples.loading)

@Preview(name = "2n — falha de carga", widthDp = 390, heightDp = 844)
@Composable
private fun GroupListFailurePreview() = GroupListShell(GroupListSamples.failed)
