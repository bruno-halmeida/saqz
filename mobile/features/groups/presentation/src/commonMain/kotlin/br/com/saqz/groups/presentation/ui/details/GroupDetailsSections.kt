package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import br.com.saqz.designsystem.SaqzAvatar
import br.com.saqz.designsystem.SaqzAvatarStack
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzGameSummaryCard
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzMemberRow
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzMetrics
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.AttendanceSummaryUi
import br.com.saqz.groups.presentation.details.CashboxUi
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupHeaderUi
import br.com.saqz.groups.presentation.details.MemberPreviewUi
import br.com.saqz.groups.presentation.details.MemberStatusUi
import br.com.saqz.groups.presentation.details.NextGameUi
import br.com.saqz.groups.presentation.details.NoticeUi
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_details_admin
import br.com.saqz.groups.resources.group_details_cash
import br.com.saqz.groups.resources.group_details_chat
import br.com.saqz.groups.resources.group_details_create_next_game
import br.com.saqz.groups.resources.group_details_edit_group
import br.com.saqz.groups.resources.group_details_going
import br.com.saqz.groups.resources.group_details_group_cash
import br.com.saqz.groups.resources.group_details_invite
import br.com.saqz.groups.resources.group_details_invite_hint
import br.com.saqz.groups.resources.group_details_invite_link
import br.com.saqz.groups.resources.group_details_leave
import br.com.saqz.groups.resources.group_details_manage
import br.com.saqz.groups.resources.group_details_manage_members
import br.com.saqz.groups.resources.group_details_manage_schedule
import br.com.saqz.groups.resources.group_details_members
import br.com.saqz.groups.resources.group_details_next_game
import br.com.saqz.groups.resources.group_details_notices
import br.com.saqz.groups.resources.group_details_notify_pending
import br.com.saqz.groups.resources.group_details_pending
import br.com.saqz.groups.resources.group_details_recent_notice
import br.com.saqz.groups.resources.group_details_schedule
import br.com.saqz.groups.resources.group_details_view_all
import br.com.saqz.groups.resources.group_details_view_game
import br.com.saqz.groups.resources.group_member_attendance_going
import br.com.saqz.groups.resources.group_member_attendance_maybe
import br.com.saqz.groups.resources.game_response_no
import br.com.saqz.groups.resources.game_response_confirmed_ratio
import br.com.saqz.groups.resources.game_response_confirmed_summary
import br.com.saqz.groups.resources.game_response_next_game
import br.com.saqz.groups.resources.game_response_spots
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import br.com.saqz.designsystem.resources.Res as DesignSystemRes
import br.com.saqz.designsystem.resources.material_sports_volleyball as volleyballWatermark

// A grade de 8 do fluxo 10c não nomeia todo passo que o fluxo 2 usa, e nenhum destes três
// está no inventário do fluxo 10 — não viram token (AGENTS.md §5). Ficam derivados da
// grade, num lugar só, em vez de `dp` cru espalhado pelas seções.
internal val SaqzMetrics.blockSpacing: Dp get() = grid * 2 // 16 — respiro entre blocos
private val SaqzMetrics.emblemSize: Dp get() = grid * 8 // 64 — o quadrado do logo
private val SaqzMetrics.watermarkSize: Dp get() = grid * 18 // 144 — a bola do canto

/**
 * Cabeçalho das duas visões: emblema, nome e resumo. O `2f` acrescenta o chip "Admin" e
 * os dois botões; o `2e`, os chips de resumo. A marca d'água de vôlei vem depois do card
 * porque o fundo dele é opaco e engoliria a bola.
 */
@Composable
internal fun GroupHeaderCard(
    header: GroupHeaderUi,
    isAdmin: Boolean,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Box(modifier = modifier.clip(RoundedCornerShape(metrics.cardRadius))) {
        SaqzCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GroupEmblem()
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
                ) {
                    Text(text = header.name, style = SaqzTheme.typography.title, color = colors.textPrimary)
                    Text(text = header.subtitle, style = SaqzTheme.typography.support, color = colors.textSecondary)
                }
                if (isAdmin) {
                    SaqzStatusChip(text = stringResource(Res.string.group_details_admin), tone = SaqzChipTone.Brand)
                }
            }
            if (header.summaryChips.isNotEmpty()) {
                GroupSummaryChips(header)
            }
            if (isAdmin) {
                GroupAdminActions(onIntent)
            }
        }
        GroupHeaderWatermark(modifier = Modifier.align(Alignment.TopEnd))
    }
}

// O quadrado azul do símbolo. Sem componente no design system: emblema de grupo não está
// no fluxo 10, então nasce na jornada. A bola faz o papel do logo enquanto o grupo não
// tem foto — carregar imagem de rede é assunto do ticket que ligar o gateway.
@Composable
private fun GroupEmblem() {
    val metrics = SaqzTheme.metrics
    Box(
        modifier = Modifier
            .size(metrics.emblemSize)
            .clip(RoundedCornerShape(metrics.cardRadius))
            .background(SaqzTheme.colors.primary),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(DesignSystemRes.drawable.volleyballWatermark),
            contentDescription = null,
            colorFilter = ColorFilter.tint(SaqzTheme.colors.onPrimary),
            modifier = Modifier.size(metrics.iconButtonSize).clearAndSetSemantics {},
        )
    }
}

@Composable
private fun GroupHeaderWatermark(modifier: Modifier = Modifier) {
    val metrics = SaqzTheme.metrics
    Image(
        painter = painterResource(DesignSystemRes.drawable.volleyballWatermark),
        contentDescription = null,
        colorFilter = ColorFilter.tint(SaqzTheme.colors.primary),
        modifier = modifier
            .offset(x = metrics.sectionGap, y = -metrics.sectionGap)
            .size(metrics.watermarkSize)
            .alpha(WatermarkOpacity)
            .clearAndSetSemantics {},
    )
}

private const val WatermarkOpacity = 0.055f

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupSummaryChips(header: GroupHeaderUi) = FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
) {
    header.summaryChips.forEach { chip ->
        SaqzStatusChip(
            text = chip.text,
            tone = if (chip.highlighted) SaqzChipTone.Brand else SaqzChipTone.Neutral,
        )
    }
}

/** `2f` — os dois botões do cabeçalho, lado a lado e de larguras iguais. */
@Composable
private fun GroupAdminActions(onIntent: (GroupDetailsIntent) -> Unit) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
) {
    SaqzButton(
        label = stringResource(Res.string.group_details_create_next_game),
        onClick = { onIntent(GroupDetailsIntent.CreateNextGame) },
        modifier = Modifier.weight(1f).testTag(GroupDetailsTags.CreateNextGame),
        size = SaqzButtonSize.Sm,
        fullWidth = true,
    )
    SaqzButton(
        label = stringResource(Res.string.group_details_edit_group),
        onClick = { onIntent(GroupDetailsIntent.EditGroup) },
        modifier = Modifier.weight(1f).testTag(GroupDetailsTags.EditGroup),
        variant = SaqzButtonVariant.Secondary,
        size = SaqzButtonSize.Sm,
        fullWidth = true,
    )
}

/**
 * `2e` — o card de destaque do próximo jogo.
 *
 * SPEC_DEVIATION: o export do fluxo 2 pinta este card com fundo ice e põe o prazo
 * ("Encerra hoje · 18h") à direita do eyebrow, na mesma linha. O `SaqzGameSummaryCard`
 * sai branco com borda e só recebe o eyebrow como texto — foi calibrado no VUL-57 contra
 * o fluxo 10, que não documenta essa variante.
 * Reason: bifurcar ou repintar o componente é o que a AD-031 proíbe. Fica o componente
 * como está, o prazo desce para o topo do slot de conteúdo, e a divergência virou o
 * VUL-97 com as duas evidências (fluxo 10 × fluxo 2).
 */
@Composable
internal fun GroupNextGameCard(
    nextGame: NextGameUi,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    SaqzGameSummaryCard(
        // O eyebrow do export é caixa alta no próprio texto; a string vive em sentence
        // case porque o resto da tela a usa como título ("Próximo jogo · terça").
        eyebrow = stringResource(Res.string.group_details_next_game).uppercase(),
        title = nextGame.date,
        modifier = modifier,
    ) {
        Text(
            text = nextGame.deadline,
            style = SaqzTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textSecondary,
            modifier = Modifier.align(Alignment.End),
        )
        Text(text = nextGame.venue, style = SaqzTheme.typography.support, color = colors.textSecondary)
        Row(
            horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SaqzAvatarStack(names = nextGame.confirmedNames)
            Column(verticalArrangement = Arrangement.spacedBy(metrics.grid)) {
                Text(
                    text = stringResource(Res.string.game_response_confirmed_summary, nextGame.confirmedCount, nextGame.capacity),
                    style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textSecondary,
                )
                Text(
                    text = stringResource(Res.string.game_response_spots, nextGame.availableSpots),
                    style = SaqzTheme.typography.caption,
                    color = colors.textSecondary,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
        ) {
            SaqzButton(
                label = stringResource(Res.string.group_details_view_game),
                onClick = { onIntent(GroupDetailsIntent.ViewGame) },
                modifier = Modifier.fillMaxWidth().testTag(GroupDetailsTags.ViewGame),
                variant = SaqzButtonVariant.Secondary,
                size = SaqzButtonSize.Sm,
                fullWidth = true,
            )
        }
    }
}

/**
 * `2f` — os contadores do próximo jogo: título, proporção azul e três colunas de largura
 * igual separadas por traços verticais.
 */
@Composable
internal fun GroupAttendanceStats(
    attendance: AttendanceSummaryUi,
    isAdmin: Boolean,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    SaqzCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.game_response_next_game),
                style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(Res.string.game_response_confirmed_ratio, attendance.confirmedCount, attendance.capacity),
                style = SaqzTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.primary,
            )
        }
        Row(
            // IntrinsicSize.Min é o que dá altura aos traços verticais: sem isso o
            // fillMaxHeight() dentro do SaqzDivider resolve para zero na Row.
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        ) {
            GroupAttendanceStat(attendance.going, stringResource(Res.string.group_details_going), colors.success)
            SaqzDivider(vertical = true)
            GroupAttendanceStat(attendance.notGoing, stringResource(Res.string.game_response_no), colors.errorForeground)
            SaqzDivider(vertical = true)
            GroupAttendanceStat(
                value = attendance.pending,
                label = stringResource(Res.string.group_details_pending),
                color = colors.textSecondary,
            )
        }
        if (isAdmin) {
            SaqzButton(
                label = stringResource(Res.string.group_details_notify_pending),
                onClick = { onIntent(GroupDetailsIntent.NotifyPending) },
                modifier = Modifier.testTag(GroupDetailsTags.NotifyPending),
                variant = SaqzButtonVariant.Ghost,
                fullWidth = true,
                leadingContent = { tint -> SaqzIcon(SaqzIcons.Megaphone, tint = tint) },
            )
        }
    }
}

@Composable
private fun RowScope.GroupAttendanceStat(value: Int, label: String, color: Color) = Column(
    modifier = Modifier.weight(1f),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
) {
    // lineHeight colado no glifo, como o SaqzGameSummaryCard faz com o mesmo número do
    // export: o leading de 27 do `title` empurraria o rótulo para longe do gap de 4.
    Text(
        text = "$value",
        style = SaqzTheme.typography.title.copy(lineHeight = SaqzTheme.typography.title.fontSize),
        color = color,
    )
    Text(text = label, style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
}

/** `2f` — a linha do caixa, que abre o fluxo 5. */
@Composable
internal fun GroupCashboxRow(
    cashbox: CashboxUi,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val label = stringResource(Res.string.group_details_group_cash)
    SaqzCard(
        modifier = modifier
            .clip(RoundedCornerShape(SaqzTheme.metrics.cardRadius))
            .clickable(onClickLabel = label, role = Role.Button) { onIntent(GroupDetailsIntent.OpenCashbox) }
            .testTag(GroupDetailsTags.Cashbox),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupIconCircle(SaqzIcons.CreditCard)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = SaqzTheme.typography.label, color = colors.textPrimary)
                Text(
                    text = cashbox.summary,
                    style = SaqzTheme.typography.caption,
                    color = colors.textSecondary,
                )
            }
            SaqzIcon(SaqzIcons.ChevronRight, tint = colors.textSecondary)
        }
    }
}

/**
 * `2e` — os atalhos do membro. Caixa é do organizador; a rota de pagamentos próprios ainda
 * não está navegável nesta branch, então não expomos uma porta para o caixa do grupo aqui.
 */
@Composable
internal fun GroupShortcutTiles(
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) = Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
) {
    GroupShortcutTile(
        icon = SaqzIcons.Bell,
        label = stringResource(Res.string.group_details_notices),
        tag = GroupDetailsTags.ShortcutNotices,
        onClick = { onIntent(GroupDetailsIntent.OpenNotices) },
    )
    GroupShortcutTile(
        icon = SaqzIcons.Calendar,
        label = stringResource(Res.string.group_details_schedule),
        tag = GroupDetailsTags.ShortcutSchedule,
        onClick = { onIntent(GroupDetailsIntent.OpenSchedule) },
    )
    GroupShortcutTile(
        icon = SaqzIcons.MessageSquare,
        label = stringResource(Res.string.group_details_chat),
        tag = GroupDetailsTags.ShortcutChat,
        onClick = { onIntent(GroupDetailsIntent.OpenChat) },
    )
}

@Composable
private fun RowScope.GroupShortcutTile(
    icon: ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit,
) = SaqzCard(
    modifier = Modifier
        .weight(1f)
        .clip(RoundedCornerShape(SaqzTheme.metrics.cardRadius))
        .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
        .testTag(tag),
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        SaqzIcon(icon, tint = SaqzTheme.colors.primary)
        Text(
            text = label,
            style = SaqzTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = SaqzTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

/** `2e` — o último aviso do grupo. */
@Composable
internal fun GroupLatestNoticeCard(notice: NoticeUi, modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    val adminLabel = stringResource(Res.string.group_details_admin)
    SaqzCard(modifier = modifier.testTag(GroupDetailsTags.Notice)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GroupIconCircle(SaqzIcons.Megaphone)
            Text(
                text = stringResource(Res.string.group_details_recent_notice),
                style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )
        }
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(notice.author) }
                if (notice.authorIsAdmin) {
                    append(" ")
                    withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold)) {
                        append("($adminLabel)")
                    }
                }
                append(" · ")
                append(notice.body)
            },
            style = SaqzTheme.typography.body,
            color = colors.textPrimary,
        )
        Text(text = notice.timestamp, style = SaqzTheme.typography.caption, color = colors.textSecondary)
    }
}

/** `2e` — cabeçalho "Membros" com "Ver todos" e as três primeiras linhas. */
@Composable
internal fun GroupMemberPreview(
    members: List<MemberPreviewUi>,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
) {
    SaqzSectionHeader(
        title = stringResource(Res.string.group_details_members),
        action = stringResource(Res.string.group_details_view_all),
        onAction = { onIntent(GroupDetailsIntent.ViewAllMembers) },
        modifier = Modifier.testTag(GroupDetailsTags.ViewAllMembers),
    )
    SaqzCard(padded = false) {
        members.forEachIndexed { index, member ->
            if (index > 0) {
                SaqzDivider()
            }
            SaqzMemberRow(
                name = member.name,
                meta = member.meta,
                trailing = { member.status?.let { GroupMemberStatusChip(it) } },
            )
        }
    }
}

@Composable
private fun GroupMemberStatusChip(status: MemberStatusUi) = when (status) {
    MemberStatusUi.Admin -> SaqzStatusChip(
        text = stringResource(Res.string.group_details_admin),
        tone = SaqzChipTone.Brand,
    )
    MemberStatusUi.Going -> SaqzStatusChip(
        text = stringResource(Res.string.group_member_attendance_going),
        tone = SaqzChipTone.Success,
        dot = true,
    )
    MemberStatusUi.Maybe -> SaqzStatusChip(
        text = stringResource(Res.string.group_member_attendance_maybe),
        tone = SaqzChipTone.Warning,
        dot = true,
    )
}

/** `2e` — o bloco ice de convite, que abre o fluxo 3. */
@Composable
internal fun GroupInviteCard(
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val title = stringResource(Res.string.group_details_invite)
    SaqzCard(
        modifier = modifier
            .clip(RoundedCornerShape(metrics.cardRadius))
            .clickable(onClickLabel = title, role = Role.Button) { onIntent(GroupDetailsIntent.Invite) }
            .testTag(GroupDetailsTags.Invite),
        tone = SaqzCardTone.Soft,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // O círculo branco com anel de 1px do export é exatamente o do SaqzAvatar; o
            // slot de foto recebe o glifo em vez das iniciais.
            SaqzAvatar(
                name = "",
                size = metrics.minimumTouchTarget,
                background = colors.surface,
            ) {
                SaqzIcon(SaqzIcons.Users, tint = colors.primary)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
            ) {
                Text(
                    text = title,
                    style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                )
                Text(
                    text = stringResource(Res.string.group_details_invite_hint),
                    style = SaqzTheme.typography.support,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/** `2f` — "Gerenciar": três linhas num card flush. */
@Composable
internal fun GroupManageList(
    memberCount: Int,
    scheduleSummary: String?,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
) {
    SaqzSectionHeader(title = stringResource(Res.string.group_details_manage))
    SaqzCard(padded = false) {
        GroupManageRow(
            icon = SaqzIcons.Users,
            label = stringResource(Res.string.group_details_manage_members),
            tag = GroupDetailsTags.ManageMembers,
            onClick = { onIntent(GroupDetailsIntent.ManageMembers) },
        ) {
            Text(
                text = "$memberCount",
                style = SaqzTheme.typography.caption,
                color = SaqzTheme.colors.textSecondary,
            )
        }
        SaqzDivider()
        GroupManageRow(
            icon = SaqzIcons.Calendar,
            label = stringResource(Res.string.group_details_manage_schedule),
            tag = GroupDetailsTags.ManageSchedule,
            onClick = { onIntent(GroupDetailsIntent.ManageSchedule) },
        ) {
            if (scheduleSummary != null) {
                Text(
                    text = scheduleSummary,
                    style = SaqzTheme.typography.caption,
                    color = SaqzTheme.colors.textSecondary,
                )
            }
        }
        SaqzDivider()
        GroupManageRow(
            icon = SaqzIcons.Users,
            label = stringResource(Res.string.group_details_invite_link),
            tag = GroupDetailsTags.ManageInviteLink,
            onClick = { onIntent(GroupDetailsIntent.InviteByLink) },
        ) {
            SaqzIcon(SaqzIcons.ChevronRight, tint = SaqzTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun GroupManageRow(
    icon: ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val metrics = SaqzTheme.metrics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .heightIn(min = metrics.minimumTouchTarget)
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap)
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaqzIcon(icon, tint = SaqzTheme.colors.primary)
        Text(
            text = label,
            style = SaqzTheme.typography.label,
            color = SaqzTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/**
 * `2e` — o rodapé "Sair do grupo". No export é um `<button>` de canto arredondado, não o
 * `SaqzDesignSystem.Button`: o design system só tem pílula (10d), então a peça é a da
 * jornada e o card branco é o que o export desenha.
 */
@Composable
internal fun GroupLeaveButton(
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(Res.string.group_details_leave)
    SaqzCard(
        modifier = modifier
            .clip(RoundedCornerShape(SaqzTheme.metrics.cardRadius))
            .clickable(onClickLabel = label, role = Role.Button) { onIntent(GroupDetailsIntent.Leave) }
            .testTag(GroupDetailsTags.Leave),
    ) {
        Text(
            text = label,
            style = SaqzTheme.typography.label,
            color = SaqzTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// Círculo ice com o glifo em azul: o contêiner de ícone que o 2e/2f repete no caixa e no
// aviso. Sem anel — no export só o círculo branco do convite tem o inset de 1px.
@Composable
private fun GroupIconCircle(icon: ImageVector) = Box(
    modifier = Modifier
        .size(SaqzTheme.metrics.iconButtonSize)
        .background(SaqzTheme.colors.surfaceSoft, CircleShape),
    contentAlignment = Alignment.Center,
) {
    SaqzIcon(icon, tint = SaqzTheme.colors.primary)
}
