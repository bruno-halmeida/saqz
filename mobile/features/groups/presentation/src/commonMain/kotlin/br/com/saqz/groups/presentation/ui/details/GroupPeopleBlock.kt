package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzAvatarStack
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.ui.components.WaitingRow
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_details_group_cash
import br.com.saqz.groups.resources.group_details_home_court_title
import br.com.saqz.groups.resources.group_details_invite_link
import br.com.saqz.groups.resources.group_details_leave
import br.com.saqz.groups.resources.group_details_manage_invite_meta
import br.com.saqz.groups.resources.group_details_manage_members
import br.com.saqz.groups.resources.group_details_manage_schedule
import br.com.saqz.groups.resources.group_details_manage_title
import br.com.saqz.groups.resources.group_details_map_failure
import br.com.saqz.groups.resources.group_details_names_more
import br.com.saqz.groups.resources.group_details_names_two
import br.com.saqz.groups.resources.group_details_people_count
import br.com.saqz.groups.resources.group_details_people_count_one
import br.com.saqz.groups.resources.group_details_people_fallback
import br.com.saqz.groups.resources.group_details_people_title
import br.com.saqz.groups.resources.group_details_venue_map
import org.jetbrains.compose.resources.stringResource

private const val CrowdAvatarMax = 4

/**
 * A linha da casca do detalhe (mural, galera, gestão): um `WaitingRow` clicável com chevron. A
 * descrição para o leitor de tela é o título seguido da meta.
 */
@Composable
internal fun GroupShellRow(
    icon: ImageVector,
    title: String,
    meta: String?,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    metaMaxLines: Int = 1,
    leading: (@Composable () -> Unit)? = null,
) {
    WaitingRow(
        icon = icon,
        title = title,
        meta = meta,
        contentDescription = listOfNotNull(title, meta).joinToString(". "),
        onClick = onClick,
        tag = tag,
        modifier = modifier,
        metaMaxLines = metaMaxLines,
        leading = leading,
    ) {
        SaqzIcon(SaqzIcons.ChevronRight, tint = SaqzTheme.colors.textSecondary)
    }
}

/** Membro vê a "Galera" (uma linha que abre a lista); gestor vê a "Gestão" (quatro portas). */
@Composable
internal fun GroupPeopleBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isAdmin) {
        GroupManageSection(state = state, onIntent = onIntent, modifier = modifier)
    } else {
        GroupCrowdSection(state = state, onIntent = onIntent, modifier = modifier)
    }
}

/**
 * `ViewAllMembers` fica no card e o clicável é a linha, DESCENDENTE dele: é assim que o e2e
 * (`ProfileFlowsE2eTest`) acha o alvo. `WaitingRow` exige uma tag no próprio nó, então a linha
 * leva `People`.
 */
@Composable
private fun GroupCrowdSection(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val names = state.memberPreview.map { it.name }.filter { it.isNotBlank() }
    val avatars: (@Composable () -> Unit)? = if (names.isEmpty()) {
        null
    } else {
        { SaqzAvatarStack(names = names, max = CrowdAvatarMax) }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.group_details_people_title))
        SaqzCard(modifier = Modifier.testTag(GroupDetailsTags.ViewAllMembers), padded = false) {
            GroupShellRow(
                icon = SaqzIcons.Users,
                title = peopleCount(state.memberCount) ?: stringResource(Res.string.group_details_people_fallback),
                meta = crowdNames(names = names, memberCount = state.memberCount),
                tag = GroupDetailsTags.People,
                onClick = { onIntent(GroupDetailsIntent.ViewAllMembers) },
                leading = avatars,
            )
        }
    }
}

/** "1 pessoa" / "26 pessoas"; `null` enquanto a contagem não chegou. */
@Composable
private fun peopleCount(memberCount: Int): String? = when (memberCount) {
    0 -> null
    1 -> stringResource(Res.string.group_details_people_count_one)
    else -> stringResource(Res.string.group_details_people_count, memberCount)
}

/** Um nome inteiro; dois ou mais, primeiros nomes — "Lucas e Bia", "Lucas, Bia e mais 24". */
@Composable
private fun crowdNames(names: List<String>, memberCount: Int): String? {
    val first = names.map { it.substringBefore(' ') }
    val others = memberCount - 2
    return when {
        first.isEmpty() -> null
        first.size == 1 -> names.single()
        first.size == 2 || others <= 0 -> stringResource(Res.string.group_details_names_two, first[0], first[1])
        else -> stringResource(Res.string.group_details_names_more, first[0], first[1], others)
    }
}

/**
 * A linha do caixa existe sempre que `cashbox != null`, mesmo sem resumo: o resumo vem de outra
 * chamada e a falha dela não pode esconder a porta. A tag `group-details-cashbox` só existe aqui.
 */
@Composable
private fun GroupManageSection(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(GroupDetailsTags.People),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.group_details_manage_title))
        SaqzCard(modifier = Modifier.testTag(GroupDetailsTags.Manage), padded = false) {
            GroupShellRow(
                icon = SaqzIcons.Users,
                title = stringResource(Res.string.group_details_manage_members),
                meta = peopleCount(state.memberCount),
                tag = GroupDetailsTags.ManageMembers,
                onClick = { onIntent(GroupDetailsIntent.ManageMembers) },
            )
            SaqzDivider()
            GroupShellRow(
                icon = SaqzIcons.Calendar,
                title = stringResource(Res.string.group_details_manage_schedule),
                meta = state.scheduleSummary,
                tag = GroupDetailsTags.ManageSchedule,
                onClick = { onIntent(GroupDetailsIntent.ManageSchedule) },
            )
            SaqzDivider()
            GroupShellRow(
                icon = SaqzIcons.Mail,
                title = stringResource(Res.string.group_details_invite_link),
                meta = stringResource(Res.string.group_details_manage_invite_meta),
                tag = GroupDetailsTags.ManageInviteLink,
                onClick = { onIntent(GroupDetailsIntent.InviteByLink) },
            )
            state.cashbox?.let { cashbox ->
                SaqzDivider()
                GroupShellRow(
                    icon = SaqzIcons.CreditCard,
                    title = stringResource(Res.string.group_details_group_cash),
                    meta = cashbox.summary,
                    tag = GroupDetailsTags.Cashbox,
                    onClick = { onIntent(GroupDetailsIntent.OpenCashbox) },
                )
            }
        }
    }
}

/**
 * "Onde a gente joga": a quadra padrão, só quando NÃO há jogo marcado — com jogo, o endereço e
 * o mapa são do hero. A falha do mapa aparece logo abaixo do card.
 */
@Composable
internal fun GroupHomeCourtBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val venue = state.venue
    if (state.nextGame != null || venue == null) return
    val colors = SaqzTheme.colors
    val mapLabel = stringResource(Res.string.group_details_venue_map)
    val address = venue.address.takeIf { it.isNotBlank() }
    Column(
        modifier = modifier.fillMaxWidth().testTag(GroupDetailsTags.HomeCourt),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.group_details_home_court_title))
        SaqzCard(padded = false) {
            WaitingRow(
                icon = SaqzIcons.Pin,
                title = venue.name,
                meta = address,
                contentDescription = listOfNotNull(mapLabel, venue.name, address).joinToString(". "),
                onClick = { onIntent(GroupDetailsIntent.OpenVenueMap) },
                tag = GroupDetailsTags.HomeCourtMap,
            ) {
                Text(
                    text = mapLabel,
                    style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.primary,
                )
            }
        }
        if (state.mapFailed) {
            Text(
                text = stringResource(Res.string.group_details_map_failure),
                style = SaqzTheme.typography.support,
                color = colors.textPrimary,
            )
        }
    }
}

/** Sair é discreto de propósito: texto centralizado, sem card. Dono não sai do grupo. */
@Composable
internal fun GroupLeaveBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isOwner) return
    val metrics = SaqzTheme.metrics
    val label = stringResource(Res.string.group_details_leave)
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClickLabel = label, role = Role.Button) { onIntent(GroupDetailsIntent.Leave) }
                .testTag(GroupDetailsTags.Leave)
                .heightIn(min = metrics.minimumTouchTarget)
                .padding(horizontal = metrics.horizontalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = label, style = SaqzTheme.typography.label, color = SaqzTheme.colors.textSecondary)
        }
    }
}

@Preview
@Composable
private fun GroupShellMemberPreview() = SaqzTheme {
    GroupDetailsScreen(state = GroupShellPreviewData.memberNoGame, onBack = {}, onIntent = {})
}

@Preview
@Composable
private fun GroupShellManagerPreview() = SaqzTheme {
    GroupDetailsScreen(state = GroupShellPreviewData.manager, onBack = {}, onIntent = {})
}
