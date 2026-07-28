package br.com.saqz.groups.presentation.ui.members

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.members.GroupMemberAction
import br.com.saqz.groups.presentation.members.GroupMembersFilter
import br.com.saqz.groups.presentation.members.GroupMembersIntent
import br.com.saqz.groups.presentation.members.GroupMembersState
import br.com.saqz.groups.presentation.members.JoinRequestUi
import br.com.saqz.groups.presentation.members.MemberUi
import br.com.saqz.groups.presentation.members.memberCount
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_members_admins
import br.com.saqz.groups.resources.group_members_invite
import br.com.saqz.groups.resources.group_members_members
import br.com.saqz.groups.resources.group_members_showing
import br.com.saqz.groups.resources.group_members_title
import org.jetbrains.compose.resources.stringResource

internal object GroupMembersTags {
    const val Search = "group-members-search"
    const val Invite = "group-members-invite"
    const val Sheet = "group-members-sheet"
    const val Cancel = "group-members-cancel"
    const val Loading = "group-members-loading"

    fun filter(filter: GroupMembersFilter) = "group-members-filter-${filter.name}"

    fun member(id: String) = "group-members-member-$id"

    fun request(id: String) = "group-members-request-$id"

    fun accept(id: String) = "group-members-accept-$id"

    fun decline(id: String) = "group-members-decline-$id"

    fun action(action: GroupMemberAction) = "group-members-action-${action.name}"
}

/**
 * 2k — busca, filtros, pedidos, administradores e membros num scroll, com o convite
 * fixo no rodapé. O 2l é o sheet desta mesma tela, aberto por `state.selected`.
 */
@Composable
fun GroupMembersScreen(
    state: GroupMembersState,
    onIntent: (GroupMembersIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Box(modifier = modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SaqzTopAppBar(title = stringResource(Res.string.group_members_title), onBack = onBack)
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = metrics.horizontalPadding,
                    end = metrics.horizontalPadding,
                    top = metrics.blockGap,
                    // 20 no export: cinco passos da subgrade de 4.
                    bottom = metrics.subGrid * 5,
                ),
                verticalArrangement = Arrangement.spacedBy(metrics.horizontalPadding),
            ) {
                item(key = "search") {
                    GroupMembersSearch(
                        query = state.query,
                        onQueryChange = { onIntent(GroupMembersIntent.UpdateQuery(it)) },
                    )
                }
                item(key = "filters") {
                    GroupMembersFilters(
                        filter = state.filter,
                        totalCount = state.totalCount,
                        adminCount = state.adminCount,
                        pendingCount = state.pendingCount,
                        onSelect = { onIntent(GroupMembersIntent.SelectFilter(it)) },
                    )
                }
                if (state.isLoading) {
                    item(key = "loading") { GroupMembersLoading() }
                } else {
                    membersContent(state, onIntent)
                }
            }
            GroupMembersFooter(onInvite = { onIntent(GroupMembersIntent.Invite) })
        }
        GroupMemberActionsSheet(selected = state.selected, onIntent = onIntent)
    }
}

/**
 * ponytail: cada seção é um item da lazy list, não cada membro. Com as poucas dezenas de
 * pessoas de um grupo, a seção inteira compõe de uma vez e o card mantém as divisórias
 * recortadas pelo raio; passar de algumas centenas, cada linha vira o seu próprio item
 * com `key = member.id` e o card se abre em cabeçalho e rodapé.
 */
private fun LazyListScope.membersContent(
    state: GroupMembersState,
    onIntent: (GroupMembersIntent) -> Unit,
) {
    if (state.joinRequests.isNotEmpty()) {
        item(key = "join-requests") {
            GroupJoinRequestsSection(
                requests = state.joinRequests,
                onAccept = { onIntent(GroupMembersIntent.AcceptRequest(it)) },
                onDecline = { onIntent(GroupMembersIntent.DeclineRequest(it)) },
            )
        }
    }
    if (state.admins.isNotEmpty()) {
        item(key = "admins") {
            GroupMemberListSection(
                title = stringResource(Res.string.group_members_admins),
                members = state.admins,
                onOpen = { onIntent(GroupMembersIntent.OpenMember(it)) },
            )
        }
    }
    if (state.members.isNotEmpty()) {
        item(key = "members") {
            GroupMemberListSection(
                title = stringResource(Res.string.group_members_members),
                members = state.members,
                onOpen = { onIntent(GroupMembersIntent.OpenMember(it)) },
                action = state.memberCount.toString(),
                footer = stringResource(Res.string.group_members_showing, state.shownCount, state.memberCount),
            )
        }
    }
}

/** Rodapé fixo: divisória em cima, convite ocupando a largura. */
@Composable
private fun GroupMembersFooter(onInvite: () -> Unit) {
    val metrics = SaqzTheme.metrics
    Column {
        SaqzDivider()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SaqzTheme.colors.background)
                .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap + metrics.subGrid / 2),
        ) {
            SaqzButton(
                label = stringResource(Res.string.group_members_invite),
                onClick = onInvite,
                variant = SaqzButtonVariant.Secondary,
                fullWidth = true,
                // TODO(VUL-96): o export usa user-plus; `SaqzIcons` ainda não tem o glifo.
                leadingContent = { color -> SaqzIcon(SaqzIcons.Plus, tint = color) },
                modifier = Modifier.testTag(GroupMembersTags.Invite),
            )
        }
    }
}

private val sampleAdmins = listOf(
    MemberUi(
        id = "lucas",
        name = "Lucas Prado",
        meta = "Criou o grupo · levantador",
        isAdmin = true,
        isSelf = true,
        stats = "42 jogos · 98% de presença",
    ),
    MemberUi(
        id = "bia",
        name = "Bia Souza",
        meta = "Ponteira · desde março",
        isAdmin = true,
        isSelf = false,
        stats = "18 jogos · 92% de presença",
    ),
)

private val sampleMembers = listOf(
    MemberUi("thiago", "Thiago Melo", "Central · mensalista", false, false, "31 jogos · 88% de presença"),
    MemberUi("camila", "Camila Alves", "Levantadora · avulso", false, false, "9 jogos · 74% de presença"),
    MemberUi("pedro", "Pedro Henrique", "Oposto · mensalista", false, false, "24 jogos · 91% de presença"),
    MemberUi("marina", "Marina Freitas", "Líbero · mensalista", false, false, "27 jogos · 95% de presença"),
)

private val sampleState = GroupMembersState(
    isLoading = false,
    totalCount = 26,
    adminCount = 2,
    pendingCount = 2,
    joinRequests = listOf(
        JoinRequestUi("julia", "Julia Martins", "Entrou pelo código · há 2h", awaitingReview = false),
        JoinRequestUi("rafael", "Rafael Costa", "Entrou pelo link · ontem", awaitingReview = true),
    ),
    admins = sampleAdmins,
    members = sampleMembers,
    shownCount = 4,
)

@Preview(name = "2k — lista com pedidos", widthDp = 390, heightDp = 844)
@Composable
private fun GroupMembersScreenPreview() = SaqzTheme {
    GroupMembersScreen(state = sampleState, onIntent = {}, onBack = {})
}

@Preview(name = "2k — sheet de membro comum", widthDp = 390, heightDp = 844)
@Composable
private fun GroupMembersMemberSheetPreview() = SaqzTheme {
    GroupMembersScreen(
        state = sampleState.copy(selected = sampleMembers.first()),
        onIntent = {},
        onBack = {},
    )
}

@Preview(name = "2l — sheet de admin", widthDp = 390, heightDp = 844)
@Composable
private fun GroupMembersAdminSheetPreview() = SaqzTheme {
    GroupMembersScreen(
        state = sampleState.copy(selected = sampleAdmins.last()),
        onIntent = {},
        onBack = {},
    )
}

@Preview(name = "2k — carregando", widthDp = 390, heightDp = 844)
@Composable
private fun GroupMembersLoadingPreview() = SaqzTheme {
    GroupMembersScreen(state = GroupMembersState(), onIntent = {}, onBack = {})
}
