package br.com.saqz.groups.presentation.ui.members

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzToast
import br.com.saqz.designsystem.SaqzToastText
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.members.GroupInviteesIntent
import br.com.saqz.groups.presentation.ui.entryrequest.AcceptAsSheet
import br.com.saqz.groups.presentation.members.GroupInviteesState
import br.com.saqz.groups.presentation.members.GroupMemberAction
import br.com.saqz.groups.presentation.members.GroupMembersFilter
import br.com.saqz.groups.presentation.members.GroupMembersIntent
import br.com.saqz.groups.presentation.members.GroupMembersNotice
import br.com.saqz.groups.presentation.members.GroupMembersState
import br.com.saqz.groups.presentation.members.JoinRequestUi
import br.com.saqz.groups.presentation.members.MemberAutopayUi
import br.com.saqz.groups.presentation.members.MemberUi
import br.com.saqz.groups.resources.members_autopay_count
import br.com.saqz.groups.presentation.members.memberCount
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.presentation.ui.SubscriptionRequiredDialog
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_invite_approved_toast
import br.com.saqz.groups.resources.group_invite_rejected_toast
import br.com.saqz.groups.resources.group_members_action_denied
import br.com.saqz.groups.resources.group_members_action_failed
import br.com.saqz.groups.resources.group_members_admins
import br.com.saqz.groups.resources.group_members_invite
import br.com.saqz.groups.resources.group_members_members
import br.com.saqz.groups.resources.group_members_requests_failed
import br.com.saqz.groups.resources.group_members_showing
import br.com.saqz.groups.resources.group_members_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal object GroupMembersTags {
    const val Search = "group-members-search"
    const val Invite = "group-members-invite"
    const val Sheet = "group-members-sheet"
    const val Cancel = "group-members-cancel"
    const val Loading = "group-members-loading"
    const val ActionFailed = "group-members-action-failed"
    const val ActionDenied = "group-members-action-denied"
    const val ConfirmRemove = "group-members-confirm-remove"
    const val ConfirmRemoveCancel = "group-members-confirm-remove-cancel"
    const val ConfirmDecline = "group-members-confirm-decline"
    const val ConfirmDeclineAction = "group-members-confirm-decline-action"
    const val RequestsFailed = "group-members-requests-failed"
    const val RosterFailed = "group-members-roster-failed"
    const val Notice = "group-members-notice"
    const val InviteesNotice = "group-members-invitees-notice"

    fun filter(filter: GroupMembersFilter) = "group-members-filter-${filter.name}"

    fun member(id: String) = "group-members-member-$id"
    const val AutopaySummary = "group-members-autopay-summary"
    fun autopay(id: String) = "group-members-autopay-$id"

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
    invitees: GroupInviteesState = GroupInviteesState(),
    onInviteesIntent: (GroupInviteesIntent) -> Unit = {},
    autopay: Map<String, MemberAutopayUi>? = null,
) {
    val metrics = SaqzTheme.metrics
    Box(modifier = modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SaqzTopAppBar(title = stringResource(Res.string.group_members_title), onBack = onBack)
            // Os toasts moram na área da lista, acima do rodapé: nunca cobrem "Convidar mais gente" (V1.15).
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
                        showPending = state.canReviewRequests,
                        // O chip só aparece com convidados, e só para quem vê a seção (dono e admin).
                        invitedCount = invitees.invitees.size.takeIf { invitees.visible && it > 0 },
                        onSelect = { onIntent(GroupMembersIntent.SelectFilter(it)) },
                    )
                }
                if (state.actionFailed) {
                    item(key = "action-failed") {
                        Text(
                            text = stringResource(Res.string.group_members_action_failed),
                            style = SaqzTheme.typography.support,
                            color = SaqzTheme.colors.errorForeground,
                            modifier = Modifier.testTag(GroupMembersTags.ActionFailed).clickable {
                                onIntent(GroupMembersIntent.DismissActionFailed)
                            },
                        )
                    }
                }
                if (state.actionDenied) {
                    item(key = "action-denied") {
                        Text(
                            text = stringResource(Res.string.group_members_action_denied),
                            style = SaqzTheme.typography.support,
                            color = SaqzTheme.colors.errorForeground,
                            modifier = Modifier.testTag(GroupMembersTags.ActionDenied).clickable {
                                onIntent(GroupMembersIntent.DismissActionFailed)
                            },
                        )
                    }
                }
                if (state.isLoading) {
                    item(key = "loading") { GroupMembersLoading() }
                } else if (state.loadFailed) {
                    item(key = "failure") {
                        GroupLoadFailure(
                            error = state.error,
                            onRetry = { onIntent(GroupMembersIntent.Retry) },
                        )
                    }
                } else {
                    if (state.filter == GroupMembersFilter.All || state.filter == GroupMembersFilter.Invited) {
                        inviteesContent(invitees, onInviteesIntent)
                    }
                    membersContent(state, onIntent, autopay)
                }
            }
            SaqzToast(
                visible = invitees.notice != null,
                onDismiss = { onInviteesIntent(GroupInviteesIntent.ClearNotice) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(SaqzTheme.metrics.horizontalPadding)
                    .testTag(GroupMembersTags.InviteesNotice),
            ) {
                SaqzToastText(invitees.notice?.let { inviteesNoticeText(it) }.orEmpty())
            }
            SaqzToast(
                visible = state.notice != null,
                onDismiss = { onIntent(GroupMembersIntent.ClearNotice) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(SaqzTheme.metrics.horizontalPadding)
                    .testTag(GroupMembersTags.Notice),
            ) {
                SaqzToastText(
                    when (val notice = state.notice) {
                        is GroupMembersNotice.RequestApproved ->
                            stringResource(Res.string.group_invite_approved_toast, notice.name)
                        else -> stringResource(Res.string.group_invite_rejected_toast)
                    },
                )
            }
            }
            GroupMembersFooter(onInvite = { onIntent(GroupMembersIntent.Invite) })
        }
        GroupMemberActionsSheet(selected = state.selected, onIntent = onIntent)
        GroupMemberRemoveConfirmation(member = state.confirmRemove, onIntent = onIntent)
        GroupJoinRequestDeclineConfirmation(request = state.declineCandidate, onIntent = onIntent)
        AcceptAsSheet(
            sheet = state.acceptAs,
            busy = state.acceptAs?.requestId in state.busyRequestIds,
            onSelect = { onIntent(GroupMembersIntent.SelectAcceptAs(it)) },
            onConfirm = { onIntent(GroupMembersIntent.ConfirmAcceptAs) },
            onClose = { onIntent(GroupMembersIntent.DismissAcceptAs) },
        )
        GroupInviteeActionsSheet(selected = invitees.selected, onIntent = onInviteesIntent)
        GroupInviteesInvalidateConfirmation(open = invitees.confirmInvalidate, onIntent = onInviteesIntent)
        if (state.subscriptionRequired) {
            SubscriptionRequiredDialog(
                trialEnded = state.trialEnded,
                onDismiss = { onIntent(GroupMembersIntent.DismissSubscriptionRequired) },
            )
        }
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
    autopay: Map<String, MemberAutopayUi>? = null,
) {
    val onAutopay = autopay.orEmpty().count { it.value == MemberAutopayUi.Active }
    if (onAutopay > 0) {
        item(key = "autopay-summary") {
            Text(
                text = pluralStringResource(Res.plurals.members_autopay_count, onAutopay, onAutopay),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
                modifier = Modifier.testTag(GroupMembersTags.AutopaySummary),
            )
        }
    }
    if (state.rosterFailed) {
        item(key = "roster-failed") {
            GroupLoadFailure(
                error = state.error,
                onRetry = { onIntent(GroupMembersIntent.Retry) },
                testTag = GroupMembersTags.RosterFailed,
            )
        }
    }
    if (state.requestsFailed) {
        item(key = "requests-failed") {
            GroupLoadFailure(
                error = null,
                onRetry = { onIntent(GroupMembersIntent.Retry) },
                testTag = GroupMembersTags.RequestsFailed,
                failureTitle = stringResource(Res.string.group_members_requests_failed),
            )
        }
    }
    if (state.joinRequests.isNotEmpty()) {
        item(key = "join-requests") {
            GroupJoinRequestsSection(
                requests = state.joinRequests,
                busyIds = state.busyRequestIds,
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
                autopay = autopay,
            )
        }
    }
    if (state.members.isNotEmpty()) {
        item(key = "members") {
            GroupMemberListSection(
                title = stringResource(Res.string.group_members_members),
                members = state.members,
                onOpen = { onIntent(GroupMembersIntent.OpenMember(it)) },
                autopay = autopay,
                action = state.memberCount.toString(),
                footer = pluralStringResource(Res.plurals.group_members_showing, state.memberCount, state.shownCount, state.memberCount),
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

internal val previewMembersState = GroupMembersState(
    isLoading = false,
    totalCount = 26,
    adminCount = 2,
    pendingCount = 2,
    canReviewRequests = true,
    joinRequests = listOf(
        JoinRequestUi("julia", "Julia Martins", "Pediu há 2 h"),
        JoinRequestUi("rafael", "Rafael Costa", "Pediu ontem"),
    ),
    admins = sampleAdmins,
    members = sampleMembers,
    shownCount = 4,
)

@Preview(name = "2k — lista com pedidos", widthDp = 390, heightDp = 844)
@Composable
private fun GroupMembersScreenPreview() = SaqzTheme {
    GroupMembersScreen(state = previewMembersState, onIntent = {}, onBack = {})
}

@Preview(name = "2k — sheet de membro comum", widthDp = 390, heightDp = 844)
@Composable
private fun GroupMembersMemberSheetPreview() = SaqzTheme {
    GroupMembersScreen(
        state = previewMembersState.copy(selected = sampleMembers.first()),
        onIntent = {},
        onBack = {},
    )
}

@Preview(name = "2l — sheet de admin", widthDp = 390, heightDp = 844)
@Composable
private fun GroupMembersAdminSheetPreview() = SaqzTheme {
    GroupMembersScreen(
        state = previewMembersState.copy(selected = sampleAdmins.last()),
        onIntent = {},
        onBack = {},
    )
}

@Preview(name = "2k — carregando", widthDp = 390, heightDp = 844)
@Composable
private fun GroupMembersLoadingPreview() = SaqzTheme {
    GroupMembersScreen(state = GroupMembersState(), onIntent = {}, onBack = {})
}
