package br.com.saqz.groups.presentation.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.designsystem.SaqzAvatar
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSkeleton
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.list.GroupCardAttendance
import br.com.saqz.groups.presentation.list.GroupCardGameUi
import br.com.saqz.groups.presentation.list.GroupCardUi
import br.com.saqz.groups.presentation.list.GroupInviteUi
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_member_attendance_maybe
import br.com.saqz.groups.resources.group_system_retry
import br.com.saqz.groups.resources.groups_attendance_going
import br.com.saqz.groups.resources.groups_confirm
import br.com.saqz.groups.resources.groups_empty_body
import br.com.saqz.groups.resources.groups_empty_create
import br.com.saqz.groups.resources.groups_empty_join
import br.com.saqz.groups.resources.groups_empty_title
import br.com.saqz.groups.resources.groups_invite_accept
import br.com.saqz.groups.resources.groups_invite_decline
import br.com.saqz.groups.resources.groups_invite_from
import br.com.saqz.groups.resources.groups_invite_title
import br.com.saqz.groups.resources.groups_no_game
import br.com.saqz.groups.resources.groups_role_admin
import br.com.saqz.groups.resources.groups_summary
import br.com.saqz.groups.resources.groups_summary_one
import br.com.saqz.groups.resources.groups_title
import org.jetbrains.compose.resources.stringResource

// Medidas de tela do export que não são token do fluxo 10 (mesmo caminho do fluxo 1).
private val HeaderTopPadding = 14.dp
private val HeaderBottomPadding = 10.dp
private val TitleSize = 26.sp
private val MetaSize = 13.sp
private val AvatarSize = 52.dp
private val AvatarRadius = 14.dp
private val AvatarGlyph = 26.dp
private val CreateGlyph = 20.dp
private val GameGlyph = 18.dp
private val GameRowGap = 10.dp
private val TightGap = 2.dp
private val SkeletonTitleWidth = 150.dp
private val SkeletonMetaWidth = 110.dp
private val SkeletonMetaHeight = 12.dp
private const val SKELETON_CARDS = 3

/** Cabeçalho de 2n. [onCreate] nulo esconde o "+", que é como 2o se desenha. */
@Composable
internal fun GroupListHeader(
    groupCount: Int,
    awaitingConfirmation: Int,
    onCreate: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = SaqzTheme.metrics.horizontalPadding,
                end = SaqzTheme.metrics.horizontalPadding,
                top = HeaderTopPadding,
                bottom = HeaderBottomPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(TightGap),
        ) {
            Text(
                text = stringResource(Res.string.groups_title),
                // headline já carrega o letter-spacing de -0.03em do export.
                style = SaqzTheme.typography.headline.copy(fontSize = TitleSize, fontWeight = FontWeight(800)),
                color = colors.textPrimary,
            )
            if (groupCount > 0) {
                val summary = if (groupCount == 1) Res.string.groups_summary_one else Res.string.groups_summary
                Text(
                    text = stringResource(summary, groupCount, awaitingConfirmation),
                    style = SaqzTheme.typography.support,
                    color = colors.textSecondary,
                )
            }
        }
        if (onCreate != null) {
            SaqzIconButton(
                onClick = onCreate,
                contentDescription = stringResource(Res.string.groups_empty_create),
                filled = true,
                modifier = Modifier.testTag(GroupListTags.Create),
            ) {
                SaqzIcon(SaqzIcons.Plus, tint = colors.onPrimary, size = CreateGlyph)
            }
        }
    }
}

@Composable
internal fun GroupCard(
    group: GroupCardUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    SaqzCard(
        modifier = modifier
            .clip(RoundedCornerShape(SaqzTheme.metrics.cardRadius))
            .clickable(onClickLabel = group.name, role = Role.Button, onClick = onClick)
            .testTag(GroupListTags.group(group.id)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
        ) {
            GroupCardAvatar(modality = group.modality, hasPhoto = group.photoUrl != null)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(TightGap),
            ) {
                Text(
                    text = group.name,
                    style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                )
                Text(
                    text = group.meta,
                    style = SaqzTheme.typography.support.copy(fontSize = MetaSize),
                    color = colors.textSecondary,
                )
            }
            if (group.isAdmin) {
                SaqzStatusChip(
                    text = stringResource(Res.string.groups_role_admin),
                    tone = SaqzChipTone.Brand,
                )
            }
        }
        SaqzDivider()
        GroupCardGameRow(game = group.nextGame)
    }
}

/** Linha do próximo jogo. Sem jogo, a linha inteira vira a frase do export. */
@Composable
internal fun GroupCardGameRow(
    game: GroupCardGameUi?,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    if (game == null) {
        Text(
            text = stringResource(Res.string.groups_no_game),
            style = SaqzTheme.typography.support,
            color = colors.textSecondary,
            modifier = modifier,
        )
        return
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GameRowGap),
    ) {
        SaqzIcon(SaqzIcons.Calendar, tint = colors.primary, size = GameGlyph)
        Text(
            text = game.label,
            style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        GroupCardAttendanceChip(attendance = game.attendance)
    }
}

@Composable
private fun GroupCardAttendanceChip(attendance: GroupCardAttendance) = when (attendance) {
    // A pílula "Confirme" do export é o próprio tom Brand do chip: 12sp, primary sobre
    // primary a 8%, raio de pílula. Reusar o componente evita repintar o mesmo par de cores.
    GroupCardAttendance.Pending -> SaqzStatusChip(
        text = stringResource(Res.string.groups_confirm),
        tone = SaqzChipTone.Brand,
    )

    GroupCardAttendance.Going -> SaqzStatusChip(
        text = stringResource(Res.string.groups_attendance_going),
        tone = SaqzChipTone.Success,
        dot = true,
    )

    // TODO(VUL-66): falta `groups_attendance_maybe`; o rótulo de 2e diz o mesmo "Talvez".
    GroupCardAttendance.Maybe -> SaqzStatusChip(
        text = stringResource(Res.string.group_member_attendance_maybe),
        tone = SaqzChipTone.Warning,
        dot = true,
    )

    // TODO(VUL-66): falta `groups_attendance_out`; 2n não desenha o caso, mas o estado existe.
    GroupCardAttendance.Out -> SaqzStatusChip(
        text = "Não vou",
        tone = SaqzChipTone.Error,
        dot = true,
    )
}

@Composable
private fun GroupCardAvatar(modality: GroupModality, hasPhoto: Boolean) {
    val colors = SaqzTheme.colors
    Box(
        modifier = Modifier
            .size(AvatarSize)
            .clip(RoundedCornerShape(AvatarRadius))
            .background(if (hasPhoto) colors.primary else colors.surfaceSoft),
        contentAlignment = Alignment.Center,
    ) {
        // ponytail: o módulo não tem carregador de imagem, então a foto do grupo ainda
        // não pinta — o quadrado azul do export fica, com o glifo em cima. Teto: entra
        // Coil (ou o port de foto) no ticket que ligar o gateway.
        SaqzIcon(
            icon = modality.avatarIcon(),
            tint = if (hasPhoto) colors.onPrimary else colors.primary,
            size = AvatarGlyph,
        )
    }
}

// ponytail: `SaqzIcons` não tem glifo por modalidade — o export desenha sol para areia e
// bola para futevôlei em SVG inline, e o mapa conceito→glifo é decisão do design system.
// `Users` cobre os três até o glifo existir lá; o mapa então vira uma linha por modalidade.
private fun GroupModality.avatarIcon(): ImageVector = when (this) {
    GroupModality.COURT_VOLLEYBALL,
    GroupModality.BEACH_VOLLEYBALL,
    GroupModality.FOOTVOLLEY,
    -> SaqzIcons.Users
}

@Composable
internal fun GroupInviteCard(
    invite: GroupInviteUi,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.groups_invite_title))
        SaqzCard(tone = SaqzCardTone.Soft) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
            ) {
                // As iniciais do export são as de quem convidou ("MF"), não as do grupo.
                SaqzAvatar(
                    name = invite.invitedBy,
                    size = SaqzTheme.metrics.iconButtonSize,
                    background = colors.surface,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(TightGap),
                ) {
                    Text(
                        text = invite.groupName,
                        style = SaqzTheme.typography.label.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                    )
                    Text(
                        text = stringResource(Res.string.groups_invite_from, invite.invitedBy),
                        style = SaqzTheme.typography.support.copy(fontSize = MetaSize),
                        color = colors.textSecondary,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(GameRowGap)) {
                SaqzButton(
                    label = stringResource(Res.string.groups_invite_accept),
                    onClick = onAccept,
                    size = SaqzButtonSize.Sm,
                    fullWidth = true,
                    modifier = Modifier.weight(1f).testTag(GroupListTags.InviteAccept),
                )
                SaqzButton(
                    label = stringResource(Res.string.groups_invite_decline),
                    onClick = onDecline,
                    variant = SaqzButtonVariant.Secondary,
                    size = SaqzButtonSize.Sm,
                    fullWidth = true,
                    modifier = Modifier.weight(1f).testTag(GroupListTags.InviteDecline),
                )
            }
        }
    }
}

/** 2o — primeiro acesso. `SaqzEmptyState` só aceita uma ação; a segunda vem logo abaixo. */
@Composable
internal fun GroupListEmpty(
    onCreate: () -> Unit,
    onJoinWithCode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = SaqzTheme.metrics.horizontalPadding),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SaqzEmptyState(
            title = stringResource(Res.string.groups_empty_title),
            description = stringResource(Res.string.groups_empty_body),
            icon = SaqzIcons.Users,
            action = stringResource(Res.string.groups_empty_create),
            onAction = onCreate,
            modifier = Modifier.testTag(GroupListTags.Empty),
        )
        SaqzButton(
            label = stringResource(Res.string.groups_empty_join),
            onClick = onJoinWithCode,
            variant = SaqzButtonVariant.Secondary,
            modifier = Modifier.testTag(GroupListTags.EmptyJoin),
        )
    }
}

/** Falha de carga não está desenhada: vazio com o erro e a saída de `Retry`. */
@Composable
internal fun GroupListFailure(
    error: GroupUiError?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GroupLoadFailure(error, onRetry, modifier.fillMaxSize(), GroupListTags.Failure)
}

/** Carregando não está desenhado: três cartões na forma do card do 2n. */
@Composable
internal fun GroupListSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SaqzTheme.metrics.horizontalPadding, vertical = SaqzTheme.metrics.subGrid),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        repeat(SKELETON_CARDS) {
            SaqzCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
                ) {
                    SaqzSkeleton(width = AvatarSize, height = AvatarSize, radius = AvatarRadius)
                    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
                        SaqzSkeleton(width = SkeletonTitleWidth)
                        SaqzSkeleton(width = SkeletonMetaWidth, height = SkeletonMetaHeight)
                    }
                }
                SaqzDivider()
                SaqzSkeleton(width = SkeletonTitleWidth, height = SkeletonMetaHeight)
            }
        }
    }
}
