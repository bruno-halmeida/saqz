package br.com.saqz.groups.presentation.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzGameSummaryCard
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.home.HomeAdminGroupUi
import br.com.saqz.groups.presentation.home.HomeAdminReadModelUi
import br.com.saqz.groups.presentation.home.HomeIntent
import br.com.saqz.groups.presentation.home.HomeNextGameUi
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_admin_cd_score_going
import br.com.saqz.groups.resources.home_admin_cd_score_out
import br.com.saqz.groups.resources.home_admin_cd_score_pending
import br.com.saqz.groups.resources.home_admin_cd_shortcut_cashbox
import br.com.saqz.groups.resources.home_admin_cd_shortcut_create_game
import br.com.saqz.groups.resources.home_admin_cd_shortcut_groups
import br.com.saqz.groups.resources.home_admin_cd_shortcut_invite
import br.com.saqz.groups.resources.home_admin_cd_waiting_entry_requests
import br.com.saqz.groups.resources.home_admin_cd_waiting_monthly
import br.com.saqz.groups.resources.home_admin_cd_waiting_settle
import br.com.saqz.groups.resources.home_game_next
import br.com.saqz.groups.resources.home_admin_hero_location
import br.com.saqz.groups.resources.home_admin_score_value
import br.com.saqz.groups.resources.home_admin_score_going
import br.com.saqz.groups.resources.home_admin_score_out
import br.com.saqz.groups.resources.home_admin_score_pending
import br.com.saqz.groups.resources.home_admin_shortcuts_cashbox
import br.com.saqz.groups.resources.home_admin_shortcuts_create_game
import br.com.saqz.groups.resources.home_admin_shortcuts_groups
import br.com.saqz.groups.resources.home_admin_shortcuts_invite
import br.com.saqz.groups.resources.home_admin_waiting_entry_requests
import br.com.saqz.groups.resources.home_admin_waiting_entry_chip
import br.com.saqz.groups.resources.home_admin_waiting_entry_requests_meta
import br.com.saqz.groups.resources.home_admin_waiting_monthly
import br.com.saqz.groups.resources.home_admin_waiting_monthly_meta
import br.com.saqz.groups.resources.home_admin_waiting_settle
import br.com.saqz.groups.resources.home_admin_waiting_settle_meta
import br.com.saqz.groups.resources.home_admin_waiting_title
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
    const val ShortcutCashbox = "home-admin-shortcut-cashbox"
    const val ShortcutGroups = "home-admin-shortcut-groups"

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

/**
 * Hero card variante admin: mesmo card ice com eyebrow "PRÓXIMO JOGO" e canto
 * direito "Encerra {dia} · {hora}" (do `confirmationDeadline` no fuso do jogo).
 * Placar em 3 colunas (Vão / Não vão / Pendentes) SEM Talvez, e o mesmo seletor
 * de presença do hero de membro — dono e admin também jogam.
 */
@Composable
internal fun HomeAdminHero(
    game: HomeNextGameUi,
    responding: Boolean,
    responseFailed: Boolean,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    SaqzGameSummaryCard(
        eyebrow = stringResource(Res.string.home_game_next),
        title = game.dateTime,
        trailingEyebrow = game.adminHeroDeadlineLabel.ifBlank { null },
        tone = SaqzCardTone.Soft,
        cornerRadius = metrics.blockRadius,
        modifier = modifier.testTag(HomeAdminTags.Hero),
        ) {
        Text(
            text = stringResource(Res.string.home_admin_hero_location, game.groupName, game.local),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
        )
        AdminScoreBoard(
            going = game.confirmedCount,
            out = game.declinedCount,
            pending = game.pendingCount,
        )
        HomeAttendanceControls(
            game = game,
            responding = responding,
            responseFailed = responseFailed,
            onIntent = onIntent,
        )
    }
}

/**
 * Placar em 3 colunas: fundo branco radius 14, divisórias verticais. "Vão" em
 * verde/800 20px, "Não vão" muted, "Pendentes" warning. Rótulos 12px muted.
 * SEM Talvez (decisão do projeto).
 */
@Composable
private fun AdminScoreBoard(
    going: Int,
    out: Int,
    pending: Int,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Column(modifier = modifier.fillMaxWidth()) {
        SaqzDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(vertical = metrics.blockGap),
        ) {
            AdminScoreColumn(
                value = going,
                label = stringResource(Res.string.home_admin_score_going),
                color = colors.success,
                contentDescription = stringResource(Res.string.home_admin_cd_score_going, going),
                modifier = Modifier.testTag(HomeAdminTags.ScoreGoing),
            )
            SaqzDivider(vertical = true)
            AdminScoreColumn(
                value = out,
                label = stringResource(Res.string.home_admin_score_out),
                color = colors.textSecondary,
                contentDescription = stringResource(Res.string.home_admin_cd_score_out, out),
                modifier = Modifier.testTag(HomeAdminTags.ScoreOut),
            )
            SaqzDivider(vertical = true)
            AdminScoreColumn(
                value = pending,
                label = stringResource(Res.string.home_admin_score_pending),
                color = colors.warningForeground,
                contentDescription = stringResource(Res.string.home_admin_cd_score_pending, pending),
                modifier = Modifier.testTag(HomeAdminTags.ScorePending),
            )
        }
        SaqzDivider()
    }
}

@Composable
private fun RowScope.AdminScoreColumn(
    value: Int,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .weight(1f)
            .semantics { this.contentDescription = contentDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
    ) {
        Text(
            text = stringResource(Res.string.home_admin_score_value, value),
            style = SaqzTheme.typography.title.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight(800),
                lineHeight = 20.sp,
            ),
            color = color,
        )
        Text(
            text = label,
            style = SaqzTheme.typography.caption,
            color = SaqzTheme.colors.textSecondary,
        )
    }
}

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
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val clickAction = { onIntent(item.action) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = item.a11y, role = Role.Button, onClick = clickAction)
            .semantics { contentDescription = item.a11y }
            .testTag(item.tag)
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        Box(
            modifier = Modifier
                .size(metrics.grid * 5)
                .clip(CircleShape)
                .background(colors.surfaceSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SaqzIcon(icon = item.icon, tint = colors.textSecondary)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.subGrid / 2)) {
            Text(
                text = item.title,
                style = SaqzTheme.typography.compactTitle,
                color = colors.textPrimary,
            )
            Text(
                text = item.meta,
                style = SaqzTheme.typography.compactMeta,
                color = colors.textSecondary,
            )
        }
        when (item.trailing) {
            is WaitingTrailing.WarningChip -> SaqzStatusChip(
                text = item.trailing.text,
                tone = SaqzChipTone.Warning,
                dot = true,
            )
            WaitingTrailing.Chevron -> SaqzIcon(SaqzIcons.ChevronRight, tint = colors.textSecondary)
        }
    }
}

/**
 * Atalhos rápidos: grid de 4 cards brancos (ícone azul 24px + rótulo 12.5px/600).
 * Criar jogo → GameEditor, Convidar → Invite, Caixa → caixa do fluxo 5,
 * Grupos → aba Grupos. Tudo via callback cross-feature.
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.subGrid),
        ) {
            HomeShortcutCard(
                icon = SaqzIcons.CreditCard,
                label = stringResource(Res.string.home_admin_shortcuts_cashbox),
                contentDescription = stringResource(Res.string.home_admin_cd_shortcut_cashbox),
                modifier = Modifier
                    .weight(1f)
                    .testTag(HomeAdminTags.ShortcutCashbox),
                onClick = { onIntent(HomeIntent.OpenCashbox(primaryGroupId)) },
            )
            HomeShortcutCard(
                icon = SaqzIcons.Users,
                label = stringResource(Res.string.home_admin_shortcuts_groups),
                contentDescription = stringResource(Res.string.home_admin_cd_shortcut_groups),
                modifier = Modifier
                    .weight(1f)
                    .testTag(HomeAdminTags.ShortcutGroups),
                onClick = { onIntent(HomeIntent.OpenGroups) },
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
