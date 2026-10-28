package br.com.saqz.groups.presentation.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzBottomNav
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzNavItem
import br.com.saqz.designsystem.SaqzPullToRefresh
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.list.GroupCardAttendance
import br.com.saqz.groups.presentation.list.GroupCardGameUi
import br.com.saqz.groups.presentation.list.GroupCardUi
import br.com.saqz.groups.presentation.list.GroupListIntent
import br.com.saqz.groups.presentation.list.GroupListState
import br.com.saqz.groups.presentation.ui.RefreshFailedToast
import br.com.saqz.groups.presentation.ui.invite.InviteLinkSheet

internal object GroupListTags {
    const val Create = "group-list-create"
    const val Summary = "group-list-summary"
    const val Empty = "group-list-empty"
    const val EmptyCreate = "group-list-empty-create"
    const val Failure = "group-list-failure"
    const val EmptyInvite = "group-list-empty-invite"
    const val InviteSheetField = "group-list-invite-sheet-field"
    const val InviteSheetSubmit = "group-list-invite-sheet-submit"
    const val Badge = "group-list-badge"
    const val Spots = "group-list-spots"
    const val SpotsTrack = "group-list-spots-track"
    const val ConfirmSheet = "group-list-confirm-sheet"
    const val ConfirmYes = "group-list-confirm-yes"
    const val ConfirmNo = "group-list-confirm-no"
    const val ConfirmError = "group-list-confirm-error"

    fun group(id: String) = "group-list-group-$id"
    fun confirm(id: String) = "group-list-confirm-$id"
    fun status(id: String) = "group-list-status-$id"
    fun admin(id: String) = "group-list-admin-$id"
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
    isPlanOwner: Boolean = false,
) {
    val metrics = SaqzTheme.metrics
    val onCreate: () -> Unit = { onIntent(GroupListIntent.CreateGroup) }
    // Box por causa da folha "Tenho um convite": ela é um overlay na mesma janela e precisa
    // cobrir a lista inteira, não entrar na coluna como mais uma seção.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SaqzTheme.colors.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
            state.isEmpty -> GroupListEmpty(
                onCreate = onCreate,
                onInvite = { onIntent(GroupListIntent.OpenInviteSheet) },
                isPlanOwner = isPlanOwner,
            )

            else -> SaqzPullToRefresh(
                refreshing = state.isRefreshing,
                onRefresh = { onIntent(GroupListIntent.PullRefresh) },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Faixas de ponta a ponta, sem margem lateral, com 8 do fundo entre um grupo e outro (GER-003).
                    contentPadding = PaddingValues(top = metrics.subGrid, bottom = ListBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(metrics.grid),
                ) {
                    items(state.groups, key = { it.id }) { group ->
                        GroupRow(
                            group = group,
                            onOpen = { onIntent(GroupListIntent.OpenGroup(group.id)) },
                            onConfirm = { onIntent(GroupListIntent.OpenConfirm(group.id)) },
                        )
                    }
                }
            }
        }
        }
        RefreshFailedToast(
            visible = state.refreshFailed,
            onDismiss = { onIntent(GroupListIntent.DismissRefreshFailed) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        InviteLinkSheet(
            open = state.inviteSheetOpen,
            link = state.inviteLink,
            invalid = state.inviteLinkInvalid,
            onLinkChange = { onIntent(GroupListIntent.InviteLinkChanged(it)) },
            onClose = { onIntent(GroupListIntent.CloseInviteSheet) },
            onSubmit = { onIntent(GroupListIntent.SubmitInviteLink) },
            fieldTag = GroupListTags.InviteSheetField,
            submitTag = GroupListTags.InviteSheetSubmit,
        )
        GroupConfirmSheet(
            group = state.confirmGroup,
            responding = state.responding,
            errorText = groupResponseErrorText(
                failed = state.responseFailed,
                planPaused = state.responsePlanPaused,
                offline = state.responseOffline,
            ),
            onRespond = { onIntent(GroupListIntent.Respond(it)) },
            onClose = { onIntent(GroupListIntent.CloseConfirm) },
        )
    }
}

// Dado de cena das previews e das capturas Roborazzi: a linha H do canvas da lista de grupos (gerar.py, board_h_list
// e board_h_states). O CERET tem 18 vagas (traço por vaga); o "Vôlei do clube" tem 40, acima das 24 em que a barra
// vira contínua. O jogo aceita até 100 vagas (`GameCapacityLimits.Max`).
internal object GroupListSamples {
    private val ceretGame = GroupCardGameUi(
        gameId = "ceret-qui",
        weekday = "QUI",
        day = "29",
        time = "19h30",
        live = false,
        confirmedCount = 14,
        capacity = 18,
        attendance = GroupCardAttendance.Pending,
        startsAtEpochSeconds = 1,
    )

    val ceret = GroupCardUi(
        id = "ceret",
        name = "Vôlei do CERET",
        isAdmin = true,
        photoUrl = "https://saqz.example/ceret.png",
        place = "CERET — Quadra 2",
        nextGame = ceretGame,
    )

    val filled = GroupListState(
        isLoading = false,
        groups = listOf(
            ceret,
            GroupCardUi(
                id = "ibira",
                name = "Areia do Ibira",
                isAdmin = false,
                place = "Parque Ibirapuera — Quadra 3",
                nextGame = ceretGame.copy(
                    gameId = "ibira-sab", weekday = "SÁB", day = "31", time = "09h00",
                    confirmedCount = 6, capacity = 8, attendance = GroupCardAttendance.Going, startsAtEpochSeconds = 2,
                ),
            ),
            GroupCardUi(id = "vila", name = "Futevôlei da Vila", isAdmin = false, place = "Vila Mariana"),
        ),
    )

    /** Em andamento sem outro jogo (AGORA, lotado), acima da lotação, na espera e não vai. */
    val states = GroupListState(
        isLoading = false,
        groups = listOf(
            ceret.copy(
                nextGame = ceretGame.copy(
                    weekday = "QUA", day = "28", live = true, confirmedCount = 18,
                    attendance = GroupCardAttendance.Going,
                ),
            ),
            GroupCardUi(
                id = "firma",
                name = "Vôlei da firma",
                isAdmin = false,
                photoUrl = "https://saqz.example/firma.png",
                place = "Sesc Pinheiros — Quadra 1",
                nextGame = ceretGame.copy(
                    gameId = "firma-sex", weekday = "SEX", day = "30", time = "18h00", confirmedCount = 13, capacity = 12,
                    overCapacityLabel = "13 confirmados para 12 vagas", attendance = GroupCardAttendance.Going,
                ),
            ),
            GroupCardUi(
                id = "ibira",
                name = "Areia do Ibira",
                isAdmin = false,
                place = "Parque Ibirapuera — Quadra 3",
                nextGame = ceretGame.copy(
                    gameId = "ibira-sab", weekday = "SÁB", day = "31", time = "09h00", confirmedCount = 8, capacity = 8,
                    attendance = GroupCardAttendance.Waitlisted,
                ),
            ),
            GroupCardUi(
                id = "clube",
                name = "Vôlei do clube",
                isAdmin = false,
                place = "Clube Pinheiros — Ginásio 1",
                nextGame = ceretGame.copy(
                    gameId = "clube-dom", weekday = "DOM", day = "01", time = "10h00", confirmedCount = 30, capacity = 40,
                    attendance = GroupCardAttendance.Going,
                ),
            ),
            GroupCardUi(
                id = "terca",
                name = "Vôlei de terça",
                isAdmin = false,
                place = "Clube Atlético Mooca — Ginásio",
                nextGame = ceretGame.copy(
                    gameId = "terca-ter", weekday = "TER", day = "03", time = "20h00", confirmedCount = 3, capacity = 12,
                    attendance = GroupCardAttendance.Out,
                ),
            ),
        ),
    )

    /** A folha do "Confirmar" aberta sobre a lista. */
    val confirming = filled.copy(confirmGroupId = "ceret")

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
internal fun GroupListShell(state: GroupListState, isPlanOwner: Boolean = false) = SaqzTheme {
    Column(modifier = Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        GroupListScreen(state = state, onIntent = {}, modifier = Modifier.weight(1f), isPlanOwner = isPlanOwner)
        SaqzBottomNav(items = GroupListSamples.navItems, activeId = "grupos", onSelect = {})
    }
}

@Preview(name = "2n — lista cheia", widthDp = 390, heightDp = 844)
@Composable
private fun GroupListFilledPreview() = GroupListShell(GroupListSamples.filled)

@Preview(name = "2n — estados", widthDp = 390, heightDp = 844)
@Composable
private fun GroupListStatesPreview() = GroupListShell(GroupListSamples.states)

@Preview(name = "2n — folha do Confirmar", widthDp = 390, heightDp = 844)
@Composable
private fun GroupListConfirmingPreview() = GroupListShell(GroupListSamples.confirming)

@Preview(name = "2o — primeiro acesso do dono", widthDp = 390, heightDp = 844)
@Composable
private fun GroupListOwnerEmptyPreview() = GroupListShell(GroupListSamples.empty, isPlanOwner = true)

@Preview(name = "2o — primeiro acesso", widthDp = 390, heightDp = 844)
@Composable
private fun GroupListEmptyPreview() = GroupListShell(GroupListSamples.empty)

@Preview(name = "2n — carregando", widthDp = 390, heightDp = 844)
@Composable
private fun GroupListLoadingPreview() = GroupListShell(GroupListSamples.loading)

@Preview(name = "2n — falha de carga", widthDp = 390, heightDp = 844)
@Composable
private fun GroupListFailurePreview() = GroupListShell(GroupListSamples.failed)
