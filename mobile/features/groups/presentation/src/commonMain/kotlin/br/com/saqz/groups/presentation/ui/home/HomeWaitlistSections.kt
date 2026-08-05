package br.com.saqz.groups.presentation.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzAvatar
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import br.com.saqz.groups.presentation.home.HomeWaitlistRowUi
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_waitlist_avulso_box_body
import br.com.saqz.groups.resources.home_waitlist_avulso_box_title
import br.com.saqz.groups.resources.home_waitlist_avulso_chip
import br.com.saqz.groups.resources.home_waitlist_avulso_leave
import br.com.saqz.groups.resources.home_waitlist_avulso_position
import br.com.saqz.groups.resources.home_waitlist_avulso_upsell_body
import br.com.saqz.groups.resources.home_waitlist_avulso_upsell_title
import br.com.saqz.groups.resources.home_waitlist_position
import br.com.saqz.groups.resources.home_waitlist_reserva_bell
import br.com.saqz.groups.resources.home_waitlist_reserva_box_body
import br.com.saqz.groups.resources.home_waitlist_reserva_box_title
import br.com.saqz.groups.resources.home_waitlist_reserva_chip
import br.com.saqz.groups.resources.home_waitlist_reserva_leave
import br.com.saqz.groups.resources.home_waitlist_section_confirmed
import br.com.saqz.groups.resources.home_waitlist_section_queue
import br.com.saqz.groups.resources.home_waitlist_view_game
import br.com.saqz.groups.resources.home_waitlist_you
import org.jetbrains.compose.resources.stringResource

internal object HomeWaitlistTags {
    const val ReservaChip = "home-reserva-chip"
    const val ReservaLeave = "home-reserva-leave"
    const val ReservaViewGame = "home-reserva-view-game"
    const val AvulsoChip = "home-avulso-chip"
    const val AvulsoLeave = "home-avulso-leave"
    const val AvulsoViewGame = "home-avulso-view-game"
    const val ConfirmedSection = "home-confirmed-section"
    const val QueueSection = "home-queue-section"

    fun queueRow(position: Long) = "home-queue-row-$position"
}

/**
 * Chip de espera do hero — warning com dot. O texto varia conforme o tipo de espera.
 */
@Composable
internal fun HomeWaitlistChip(kind: HomeWaitlistKind, position: Long?, modifier: Modifier = Modifier) {
    val text = when (kind) {
        HomeWaitlistKind.Reserva -> stringResource(Res.string.home_waitlist_reserva_chip, position?.toInt() ?: 0)
        HomeWaitlistKind.AvulsoList -> stringResource(Res.string.home_waitlist_avulso_chip)
    }
    SaqzStatusChip(
        text = text,
        tone = SaqzChipTone.Warning,
        dot = true,
        modifier = modifier.testTag(
            if (kind == HomeWaitlistKind.Reserva) HomeWaitlistTags.ReservaChip else HomeWaitlistTags.AvulsoChip,
        ),
    )
}

/**
 * Caixa branca (radius 14) com ícone de relógio warning para a reserva (6b), ou
 * ícone de circle-alert para a lista do avulso (6e).
 */
@Composable
internal fun HomeWaitlistInfoBox(kind: HomeWaitlistKind, modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val icon = if (kind == HomeWaitlistKind.Reserva) SaqzIcons.Clock else SaqzIcons.CircleAlert
    val iconColor = if (kind == HomeWaitlistKind.Reserva) colors.warningForeground else colors.textSecondary
    val title = when (kind) {
        HomeWaitlistKind.Reserva -> Res.string.home_waitlist_reserva_box_title
        HomeWaitlistKind.AvulsoList -> Res.string.home_waitlist_avulso_box_title
    }
    val body = when (kind) {
        HomeWaitlistKind.Reserva -> Res.string.home_waitlist_reserva_box_body
        HomeWaitlistKind.AvulsoList -> Res.string.home_waitlist_avulso_box_body
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.cardRadius + metrics.subGrid / 2))
            .background(colors.surface)
            .padding(metrics.blockGap),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(metrics.subGrid * 2),
    ) {
        Box(
            modifier = Modifier
                .size(metrics.iconButtonSize - metrics.subGrid / 2)
                .clip(CircleShape)
                .background(colors.surfaceSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            SaqzIcon(icon = icon, tint = iconColor, size = 20.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
            Text(
                text = stringResource(title),
                style = SaqzTheme.typography.body.copy(fontWeight = FontWeight(700)),
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(body),
                style = SaqzTheme.typography.support,
                color = colors.textSecondary,
            )
        }
    }
}

/**
 * Botões em grid 1fr/1fr: secundário sm "Sair da reserva/lista" e primário sm "Ver o jogo".
 */
@Composable
internal fun HomeWaitlistActions(
    kind: HomeWaitlistKind,
    responding: Boolean,
    onLeave: () -> Unit,
    onViewGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    val leaveLabel = when (kind) {
        HomeWaitlistKind.Reserva -> stringResource(Res.string.home_waitlist_reserva_leave)
        HomeWaitlistKind.AvulsoList -> stringResource(Res.string.home_waitlist_avulso_leave)
    }
    val viewLabel = stringResource(Res.string.home_waitlist_view_game)
    val leaveTag = if (kind == HomeWaitlistKind.Reserva) HomeWaitlistTags.ReservaLeave else HomeWaitlistTags.AvulsoLeave
    val viewTag = if (kind == HomeWaitlistKind.Reserva) HomeWaitlistTags.ReservaViewGame else HomeWaitlistTags.AvulsoViewGame
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(metrics.subGrid),
    ) {
        SaqzButton(
            label = leaveLabel,
            onClick = onLeave,
            modifier = Modifier
                .weight(1f)
                .testTag(leaveTag)
                .semantics { contentDescription = leaveLabel },
            variant = SaqzButtonVariant.Secondary,
            size = SaqzButtonSize.Sm,
            fullWidth = true,
            loading = responding,
            enabled = !responding,
        )
        SaqzButton(
            label = viewLabel,
            onClick = onViewGame,
            modifier = Modifier
                .weight(1f)
                .testTag(viewTag)
                .semantics { contentDescription = viewLabel },
            variant = SaqzButtonVariant.Primary,
            size = SaqzButtonSize.Sm,
            fullWidth = true,
            enabled = !responding,
        )
    }
}

/**
 * Card informativo com ícone sino: "Avisamos você se abrir vaga até {hora} de {dia}."
 * Só aparece na reserva (6b).
 */
@Composable
internal fun HomeWaitlistBellCard(label: String, modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.cardRadius))
            .background(colors.surfaceSoft, RoundedCornerShape(metrics.cardRadius))
            .padding(metrics.blockGap),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(metrics.subGrid * 2),
    ) {
        SaqzIcon(icon = SaqzIcons.Bell, tint = colors.primary, size = 20.dp)
        Text(
            text = label,
            style = SaqzTheme.typography.support,
            color = colors.textPrimary,
        )
    }
}

/**
 * Card upsell estático do avulso (6e): ícone cartão, título e body — sem ação.
 */
@Composable
internal fun HomeWaitlistUpsellCard(modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.cardRadius))
            .background(colors.surfaceSoft, RoundedCornerShape(metrics.cardRadius))
            .padding(metrics.blockGap),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(metrics.subGrid * 2),
    ) {
        SaqzIcon(icon = SaqzIcons.CreditCard, tint = colors.primary, size = 20.dp)
        Column(verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
            Text(
                text = stringResource(Res.string.home_waitlist_avulso_upsell_title),
                style = SaqzTheme.typography.body.copy(fontWeight = FontWeight(700)),
                color = colors.textPrimary,
            )
            Text(
                text = stringResource(Res.string.home_waitlist_avulso_upsell_body),
                style = SaqzTheme.typography.support,
                color = colors.textSecondary,
            )
        }
    }
}

/**
 * Linha muted do avulso (6e): "{posição}º na lista · {N} mensalistas confirmados".
 */
@Composable
internal fun HomeWaitlistPositionLine(position: Long, mensalistaCount: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(Res.string.home_waitlist_avulso_position, position.toInt(), mensalistaCount),
        style = SaqzTheme.typography.support,
        color = SaqzTheme.colors.textSecondary,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Seção "Confirmados" (6b): header com ação "{confirmedCount} de {capacity}" e
 * card com avatares de iniciais em wrap. "+{N}" quando estourar o máximo.
 */
@Composable
internal fun HomeWaitlistConfirmedSection(
    confirmedRoster: List<String>,
    confirmedCount: Int,
    capacity: Int,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier.testTag(HomeWaitlistTags.ConfirmedSection),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        SaqzSectionHeader(
            title = stringResource(Res.string.home_waitlist_section_confirmed),
            action = "$confirmedCount de $capacity",
        )
        SaqzCard {
            if (confirmedRoster.isEmpty()) {
                Text(
                    text = "—",
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textSecondary,
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy((-8).dp),
                    verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
                ) {
                    confirmedRoster.forEach { name ->
                        SaqzAvatar(name = name, size = 32.dp, background = SaqzTheme.colors.surface)
                    }
                }
            }
        }
    }
}

/**
 * Seção "Como está a lista" (6e): linhas de `rosterPreview.waitlisted` em ordem de
 * posição. A linha do usuário destacada: fundo ice, "Você" no lugar do nome,
 * texto da posição em azul/600.
 */
@Composable
internal fun HomeWaitlistQueueSection(
    rows: List<HomeWaitlistRowUi>,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    val colors = SaqzTheme.colors
    Column(
        modifier = modifier.testTag(HomeWaitlistTags.QueueSection),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.home_waitlist_section_queue))
        SaqzCard(padded = false) {
            rows.forEachIndexed { index, row ->
                if (index > 0) SaqzDivider()
                HomeWaitlistQueueRow(row)
            }
        }
    }
}

@Composable
private fun HomeWaitlistQueueRow(row: HomeWaitlistRowUi) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val nameColor = if (row.isSelf) colors.primary else colors.textPrimary
    val positionColor = if (row.isSelf) colors.primary else colors.textSecondary
    val nameText = if (row.isSelf) {
        stringResource(Res.string.home_waitlist_you)
    } else {
        row.name
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (row.isSelf) Modifier.background(colors.surfaceSoft) else Modifier)
            .testTag(HomeWaitlistTags.queueRow(row.position))
            .semantics { contentDescription = "$nameText, ${row.position}º na espera" }
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        SaqzAvatar(name = nameText, initialsColor = colors.primary)
        Text(
            text = nameText,
            style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = nameColor,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(Res.string.home_waitlist_position, row.position.toInt()),
            style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
            color = positionColor,
        )
    }
}