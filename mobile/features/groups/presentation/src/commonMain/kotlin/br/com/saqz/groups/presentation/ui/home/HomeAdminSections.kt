package br.com.saqz.groups.presentation.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzHeroCard
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.home.HomeAdminGroupUi
import br.com.saqz.groups.presentation.home.HomeAdminReadModelUi
import br.com.saqz.groups.presentation.home.HomeIntent
import br.com.saqz.groups.presentation.home.HomeNextGameUi
import br.com.saqz.groups.presentation.ui.components.AttendanceScoreBoard
import br.com.saqz.groups.presentation.ui.components.AttendanceScoreBoardTags
import br.com.saqz.groups.presentation.ui.components.HeroDeadlineLine
import br.com.saqz.groups.presentation.ui.components.HeroOutlineAlpha
import br.com.saqz.groups.presentation.ui.components.WaitingRow
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_admin_cd_shortcut_create_game
import br.com.saqz.groups.resources.home_admin_cd_shortcut_invite
import br.com.saqz.groups.resources.home_admin_cd_waiting_entry_requests
import br.com.saqz.groups.resources.home_admin_cd_waiting_monthly
import br.com.saqz.groups.resources.home_admin_cd_waiting_settle
import br.com.saqz.groups.resources.home_game_next
import br.com.saqz.groups.resources.home_admin_shortcuts_create_game
import br.com.saqz.groups.resources.home_admin_shortcuts_invite
import br.com.saqz.groups.resources.home_admin_waiting_entry_requests
import br.com.saqz.groups.resources.home_admin_waiting_entry_chip
import br.com.saqz.groups.resources.home_admin_waiting_entry_requests_meta
import br.com.saqz.groups.resources.home_admin_waiting_monthly
import br.com.saqz.groups.resources.home_admin_waiting_monthly_meta
import br.com.saqz.groups.resources.home_admin_waiting_settle
import br.com.saqz.groups.resources.home_admin_waiting_settle_meta
import br.com.saqz.groups.resources.home_admin_waiting_title
import br.com.saqz.groups.resources.home_no_game_description
import br.com.saqz.groups.resources.home_no_game_hero_title
import org.jetbrains.compose.resources.stringResource

internal object HomeAdminTags {
    const val Hero = "home-admin-hero"
    const val ScoreGoing = "home-admin-score-going"
    const val ScoreOut = "home-admin-score-out"
    const val ScorePending = "home-admin-score-pending"
    const val Waiting = "home-admin-waiting"
    const val Shortcuts = "home-admin-shortcuts"
    const val ShortcutCreateGame = "home-admin-shortcut-create-game"
    const val ShortcutInvite = "home-admin-shortcut-invite"
    const val EmptyCreateGame = "home-admin-empty-create-game"
    const val EmptyInvite = "home-admin-empty-invite"

    fun entryRequests(groupId: String) = "home-admin-entry-requests-$groupId"
    fun monthly(groupId: String) = "home-admin-monthly-$groupId"
    fun settle(groupId: String) = "home-admin-settle-$groupId"
}

/**
 * O usuário administra o grupo do próximo jogo? O hero segue o papel do usuário
 * no grupo do próximo jogo — se admin, hero admin (placar, sem seletor); se não,
 * hero membro normal (com RSVP).
 */
internal fun isAdminOfNextGame(
    nextGame: HomeNextGameUi,
    admin: HomeAdminReadModelUi?,
): Boolean = admin?.groups?.any { it.id == nextGame.groupId } == true

@Composable
internal fun HomeAdminHero(
    game: HomeNextGameUi,
    responding: Boolean,
    responseFailed: Boolean,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    SaqzHeroCard(
        kicker = stringResource(Res.string.home_game_next),
        title = game.display,
        meta = game.meta,
        trailing = game.adminHeroDeadlineLabel.ifBlank { null }?.let { label ->
            { SaqzStatusChip(text = label, tone = SaqzChipTone.Inverse) }
        },
        modifier = modifier.testTag(HomeAdminTags.Hero),
    ) {
        AttendanceScoreBoard(
            going = game.confirmedCount,
            out = game.declinedCount,
            pending = game.pendingCount,
            // O placar é o dado que o admin mais quer detalhar — tocar abre o jogo,
            // onde mora a lista de quem respondeu (e a cobrança de presença).
            onClick = { onIntent(HomeIntent.OpenGame(game.groupId, game.gameId)) },
            tags = HomeScoreBoardTags,
        )
        HeroDeadlineLine(text = game.deadline, open = game.confirmationOpen)
        HomeAttendanceControls(
            game = game,
            responding = responding,
            responseFailed = responseFailed,
            onIntent = onIntent,
        )
    }
}

/** Gestor sem jogo marcado: os dois verbos dele sobem para dentro do hero (VUL-218). */
@Composable
internal fun HomeAdminNoGame(
    admin: HomeAdminReadModelUi,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val primaryGroupId = admin.groups.firstOrNull()?.id ?: return
    SaqzHeroCard(
        kicker = stringResource(Res.string.home_game_next),
        title = stringResource(Res.string.home_no_game_hero_title),
        meta = stringResource(Res.string.home_no_game_description),
        modifier = modifier.testTag(HomeTags.Empty),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.subGrid),
        ) {
            SaqzButton(
                label = stringResource(Res.string.home_admin_shortcuts_create_game),
                onClick = { onIntent(HomeIntent.OpenGameEditor(primaryGroupId)) },
                variant = SaqzButtonVariant.Accent,
                fullWidth = true,
                modifier = Modifier.weight(1f).testTag(HomeAdminTags.EmptyCreateGame),
            )
            SaqzButton(
                label = stringResource(Res.string.home_admin_shortcuts_invite),
                onClick = { onIntent(HomeIntent.OpenInvite(primaryGroupId)) },
                variant = SaqzButtonVariant.Ghost,
                contentColor = colors.onPrimary,
                borderColor = colors.onPrimary.copy(alpha = HeroOutlineAlpha),
                fullWidth = true,
                modifier = Modifier.weight(1f).testTag(HomeAdminTags.EmptyInvite),
            )
        }
    }
}

private val HomeScoreBoardTags = AttendanceScoreBoardTags(
    going = HomeAdminTags.ScoreGoing,
    out = HomeAdminTags.ScoreOut,
    pending = HomeAdminTags.ScorePending,
)

/**
 * Seção "Esperando você": card branco com linhas divididas. Cada linha tem um
 * ícone circular ice 40px à esquerda, título 14.5px/700, meta 12.5px muted.
 * Itens com contagem zero não aparecem; seção some se não houver nenhum item.
 */
@Composable
internal fun HomeAdminWaitingSection(
    admin: HomeAdminReadModelUi,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    val items = admin.groups.flatMap { group -> buildWaitingItems(group) }
    if (items.isEmpty()) return

    Column(
        modifier = modifier.testTag(HomeAdminTags.Waiting),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.home_admin_waiting_title))
        SaqzCard(padded = false) {
            items.forEachIndexed { index, item ->
                HomeWaitingRow(item = item, onIntent = onIntent)
                if (index < items.lastIndex) SaqzDivider()
            }
        }
    }
}

private data class HomeWaitingItem(
    val groupId: String,
    val groupName: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val meta: String,
    val a11y: String,
    val trailing: WaitingTrailing,
    val tag: String,
    val action: HomeIntent,
)

private sealed interface WaitingTrailing {
    data class WarningChip(val text: String) : WaitingTrailing
    data object Chevron : WaitingTrailing
}

@Composable
private fun buildWaitingItems(group: HomeAdminGroupUi): List<HomeWaitingItem> = buildList {
    if (group.entryRequestCount > 0) {
        add(
            HomeWaitingItem(
                groupId = group.id,
                groupName = group.name,
                icon = SaqzIcons.Users,
                title = stringResource(
                    Res.string.home_admin_waiting_entry_requests,
                    group.entryRequestCount,
                ),
                meta = stringResource(
                    Res.string.home_admin_waiting_entry_requests_meta,
                    group.name,
                ),
                a11y = stringResource(
                    Res.string.home_admin_cd_waiting_entry_requests,
                    group.entryRequestCount,
                    group.name,
                ),
                trailing = WaitingTrailing.WarningChip(
                    stringResource(Res.string.home_admin_waiting_entry_chip, group.entryRequestCount),
                ),
                tag = HomeAdminTags.entryRequests(group.id),
                action = HomeIntent.OpenInvite(group.id),
            ),
        )
    }
    group.monthlyCharges?.let { charges ->
        add(
            HomeWaitingItem(
                groupId = group.id,
                groupName = group.name,
                icon = SaqzIcons.CreditCard,
                title = stringResource(
                    Res.string.home_admin_waiting_monthly,
                    charges.count,
                ),
                meta = stringResource(
                    Res.string.home_admin_waiting_monthly_meta,
                    charges.formattedTotal,
                    charges.month,
                ),
                a11y = stringResource(
                    Res.string.home_admin_cd_waiting_monthly,
                    charges.count,
                    group.name,
                ),
                trailing = WaitingTrailing.Chevron,
                tag = HomeAdminTags.monthly(group.id),
                action = HomeIntent.OpenCashbox(group.id),
            ),
        )
    }
    group.gameToSettle?.let { game ->
        add(
            HomeWaitingItem(
                groupId = group.id,
                groupName = group.name,
                icon = SaqzIcons.Calendar,
                title = stringResource(
                    Res.string.home_admin_waiting_settle,
                    game.formattedDate,
                ),
                meta = stringResource(
                    Res.string.home_admin_waiting_settle_meta,
                    game.diaristCount,
                    game.formattedTotal,
                ),
                a11y = stringResource(
                    Res.string.home_admin_cd_waiting_settle,
                    game.formattedDate,
                    group.name,
                ),
                trailing = WaitingTrailing.Chevron,
                tag = HomeAdminTags.settle(group.id),
                action = HomeIntent.OpenGameSettlement(group.id, game.gameId),
            ),
        )
    }
}

@Composable
private fun HomeWaitingRow(
    item: HomeWaitingItem,
    onIntent: (HomeIntent) -> Unit,
) {
    WaitingRow(
        icon = item.icon,
        title = item.title,
        meta = item.meta,
        contentDescription = item.a11y,
        onClick = { onIntent(item.action) },
        tag = item.tag,
        // A Início nunca limitou a meta; o default de 1 linha é do detalhe do grupo.
        metaMaxLines = Int.MAX_VALUE,
    ) {
        when (item.trailing) {
            is WaitingTrailing.WarningChip -> SaqzStatusChip(
                text = item.trailing.text,
                tone = SaqzChipTone.Warning,
                dot = true,
            )
            WaitingTrailing.Chevron -> SaqzIcon(SaqzIcons.ChevronRight, tint = SaqzTheme.colors.textSecondary)
        }
    }
}

/**
 * Atalhos rápidos do gestor: Marcar jogo → GameEditor e Convidar → Invite (VUL-219).
 * Caixa e Grupos saíram porque já são abas.
 */
@Composable
internal fun HomeAdminShortcuts(
    admin: HomeAdminReadModelUi,
    nextGame: HomeNextGameUi?,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    val primaryGroupId = admin.groups
        .firstOrNull { it.id == nextGame?.groupId }
        ?.id
        ?: admin.groups.firstOrNull()?.id
        ?: return
    Column(
        modifier = modifier.testTag(HomeAdminTags.Shortcuts),
        verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.subGrid),
        ) {
            HomeShortcutCard(
                icon = SaqzIcons.Plus,
                label = stringResource(Res.string.home_admin_shortcuts_create_game),
                contentDescription = stringResource(Res.string.home_admin_cd_shortcut_create_game),
                modifier = Modifier
                    .weight(1f)
                    .testTag(HomeAdminTags.ShortcutCreateGame),
                onClick = { onIntent(HomeIntent.OpenGameEditor(primaryGroupId)) },
            )
            HomeShortcutCard(
                icon = SaqzIcons.Mail,
                label = stringResource(Res.string.home_admin_shortcuts_invite),
                contentDescription = stringResource(Res.string.home_admin_cd_shortcut_invite),
                modifier = Modifier
                    .weight(1f)
                    .testTag(HomeAdminTags.ShortcutInvite),
                onClick = { onIntent(HomeIntent.OpenInvite(primaryGroupId)) },
            )
        }
    }
}

@Composable
private fun HomeShortcutCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(metrics.cardRadius))
            .background(colors.surface, RoundedCornerShape(metrics.cardRadius))
            .clickable(onClickLabel = contentDescription, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(metrics.blockGap),
        verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SaqzIcon(icon = icon, tint = colors.primary, size = metrics.grid * 3)
        Text(
            text = label,
            style = SaqzTheme.typography.compactMeta.copy(fontWeight = FontWeight(600)),
            color = colors.textPrimary,
        )
    }
}
