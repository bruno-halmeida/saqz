package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.AttendanceSummaryUi
import br.com.saqz.groups.presentation.details.CashboxUi
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.details.GroupHeaderUi
import br.com.saqz.groups.presentation.details.GroupSummaryChipUi
import br.com.saqz.groups.presentation.details.MemberPreviewUi
import br.com.saqz.groups.presentation.details.MemberStatusUi
import br.com.saqz.groups.presentation.details.NextGameUi
import br.com.saqz.groups.presentation.details.NoticeUi
import br.com.saqz.groups.presentation.details.VenueUi
import br.com.saqz.groups.presentation.ui.components.GroupVenueRow
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_details_venue_edit
import br.com.saqz.groups.resources.group_details_venue_map
import org.jetbrains.compose.resources.stringResource

internal object GroupDetailsTags {
    const val Screen = "group-details"
    const val CreateNextGame = "group-details-create-next-game"
    const val EditGroup = "group-details-edit-group"
    const val NotifyPending = "group-details-notify-pending"
    const val ConfirmAttendance = "group-details-confirm-attendance"
    const val ViewGame = "group-details-view-game"
    const val Cashbox = "group-details-cashbox"
    const val Venue = "group-details-venue"
    const val ShortcutNotices = "group-details-shortcut-notices"
    const val ShortcutCashbox = "group-details-shortcut-cashbox"
    const val ShortcutSchedule = "group-details-shortcut-schedule"
    const val ShortcutChat = "group-details-shortcut-chat"
    const val Notice = "group-details-notice"
    const val ViewAllMembers = "group-details-view-all-members"
    const val Invite = "group-details-invite"
    const val ManageMembers = "group-details-manage-members"
    const val ManageSchedule = "group-details-manage-schedule"
    const val ManageInviteLink = "group-details-manage-invite-link"
    const val Leave = "group-details-leave"
}

/**
 * 2e e 2f — a tela só empilha. Cada bloco é uma seção em `GroupDetailsSections.kt` e
 * aparece porque a sua fatia do estado existe: seção de admin chega `null` na visão de
 * membro, e vice-versa.
 */
@Composable
internal fun GroupDetailsScreen(
    state: GroupDetailsState,
    onBack: () -> Unit,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Column(modifier = modifier.fillMaxSize().testTag(GroupDetailsTags.Screen)) {
        SaqzTopAppBar(title = state.header?.name, onBack = onBack)
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SaqzSpinner()
            }
            return@Column
        }
        if (state.loadFailed) {
            GroupLoadFailure(error = state.error, onRetry = { onIntent(GroupDetailsIntent.Retry) })
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
                verticalArrangement = Arrangement.spacedBy(metrics.blockSpacing),
            ) {
                state.header?.let { GroupHeaderCard(header = it, isAdmin = state.isAdmin, onIntent = onIntent) }
                state.nextGame?.let { GroupNextGameCard(nextGame = it, onIntent = onIntent) }
                state.attendance?.let { GroupAttendanceStats(attendance = it, onIntent = onIntent) }
                state.cashbox?.let { GroupCashboxRow(cashbox = it, onIntent = onIntent) }
                state.venue?.let { GroupVenueCard(venue = it, isAdmin = state.isAdmin, onIntent = onIntent) }
                if (!state.isAdmin) {
                    GroupShortcutTiles(onIntent = onIntent)
                }
                state.latestNotice?.let { GroupLatestNoticeCard(notice = it) }
                if (state.memberPreview.isNotEmpty()) {
                    GroupMemberPreview(members = state.memberPreview, onIntent = onIntent)
                }
                if (!state.isAdmin) {
                    GroupInviteCard(onIntent = onIntent)
                }
                if (state.isAdmin) {
                    GroupManageList(
                        memberCount = state.memberCount,
                        scheduleSummary = state.scheduleSummary,
                        onIntent = onIntent,
                    )
                }
                if (!state.isAdmin) {
                    GroupLeaveButton(onIntent = onIntent)
                }
            }
        }
    }
}

/**
 * A quadra: o `GroupVenueRow` do VUL-66 dentro do card branco do export. A ação é a única
 * diferença entre as duas visões — "Ver no mapa" no `2e`, "Editar" no `2f`.
 */
@Composable
private fun GroupVenueCard(
    venue: VenueUi,
    isAdmin: Boolean,
    onIntent: (GroupDetailsIntent) -> Unit,
) = SaqzCard(modifier = Modifier.testTag(GroupDetailsTags.Venue)) {
    GroupVenueRow(
        name = venue.name,
        address = venue.address,
        actionLabel = stringResource(
            if (isAdmin) Res.string.group_details_venue_edit else Res.string.group_details_venue_map,
        ),
        onAction = {
            onIntent(if (isAdmin) GroupDetailsIntent.EditVenue else GroupDetailsIntent.OpenVenueMap)
        },
    )
}

// Dado das previews: o mesmo do export, para o print do PR bater com o desenho.
internal object GroupDetailsPreviewData {
    val header = GroupHeaderUi(
        name = "Vôlei do CERET",
        subtitle = "Tatuapé · Misto · Intermediário",
    )
    val memberHeader = header.copy(
        summaryChips = listOf(
            GroupSummaryChipUi("26 membros"),
            GroupSummaryChipUi("Vôlei de quadra"),
            GroupSummaryChipUi("Terças e quintas", highlighted = true),
        ),
    )
    val venue = VenueUi(name = "CERET — Quadra 2", address = "R. Canuto Abreu, s/n · Tatuapé")

    val admin = GroupDetailsState(
        isLoading = false,
        isAdmin = true,
        isOwner = true,
        header = header,
        attendance = AttendanceSummaryUi(
            title = "Próximo jogo · terça",
            ratio = "9/12",
            going = 9,
            maybe = 3,
            pending = 4,
        ),
        cashbox = CashboxUi(summary = "Saldo R$ 380,00 · 8 mensalidades em aberto"),
        venue = venue,
        memberCount = 26,
        scheduleSummary = "Ter, Qui",
    )

    val member = GroupDetailsState(
        isLoading = false,
        isAdmin = false,
        header = memberHeader,
        nextGame = NextGameUi(
            date = "Ter, 28/07 · 19h30",
            venue = "CERET — Quadra 2 · Tatuapé",
            deadline = "Encerra hoje · 18h",
            confirmedSummary = "9 de 12 confirmados",
            confirmedNames = listOf(
                "Lucas Prado",
                "Bia Souza",
                "Thiago Melo",
                "Ana Lima",
                "Caio Reis",
                "Duda Nunes",
                "Eva Rocha",
                "Fábio Sá",
                "Gil Matos",
            ),
        ),
        venue = venue,
        latestNotice = NoticeUi(
            author = "Lucas",
            authorIsAdmin = true,
            body = "Cheguem 15 min antes para montar a rede.",
            timestamp = "Hoje, 10h30",
        ),
        memberPreview = listOf(
            MemberPreviewUi("1", "Lucas Prado", "Organizador · levantador", MemberStatusUi.Admin),
            MemberPreviewUi("2", "Bia Souza", "Ponteira", MemberStatusUi.Going),
            MemberPreviewUi("3", "Thiago Melo", "Central", MemberStatusUi.Maybe),
        ),
        memberCount = 26,
    )
}

@Preview
@Composable
private fun GroupDetailsAdminPreview() = SaqzTheme {
    GroupDetailsScreen(state = GroupDetailsPreviewData.admin, onBack = {}, onIntent = {})
}

@Preview
@Composable
private fun GroupDetailsMemberPreview() = SaqzTheme {
    GroupDetailsScreen(state = GroupDetailsPreviewData.member, onBack = {}, onIntent = {})
}

@Preview
@Composable
private fun GroupDetailsLoadingPreview() = SaqzTheme {
    GroupDetailsScreen(state = GroupDetailsState(), onBack = {}, onIntent = {})
}
