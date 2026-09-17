package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.SaqzAvatarStack
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import br.com.saqz.groups.presentation.ui.home.HomeWaitlistActions
import br.com.saqz.groups.presentation.ui.home.HomeWaitlistChip
import br.com.saqz.groups.presentation.ui.home.HomeWaitlistInfoBox

// Opacidades do branco sobre o azul do hero (VUL-218). Só `onPrimary` com alfa: não há
// token de branco translúcido no contrato, e derivar do sólido é o padrão dos chips.
internal const val HeroTextAlpha = 0.9f
internal const val HeroMutedAlpha = 0.7f
internal const val HeroSummaryAlpha = 0.88f
internal const val HeroPanelAlpha = 0.12f
internal const val HeroDotMutedAlpha = 0.45f
internal const val HeroOutlineAlpha = 0.45f
internal val HeroIconGap = 6.dp
internal val HeroLineIconSize = 14.dp
internal val HeroInlineIconSize = 16.dp
internal val HeroCheckSize = 18.dp
internal val HeroStatusDot = 10.dp

/** Textos do seletor de presença: cada tela passa a própria cópia (a do detalhe do grupo é contrato de e2e). */
@Immutable
internal data class HeroAttendanceTexts(
    val yes: String,
    val no: String,
    val confirmed: String,
    val declined: String,
    val change: String,
    val cancel: String,
    val error: String,
)

/** `testTag` por parâmetro, como no `PixCard`: cada tela responde pelo próprio inventário de tags. */
@Immutable
internal data class HeroAttendanceTags(
    val yes: String,
    val no: String,
    val change: String,
    val cancel: String,
    val error: String,
)

/** Linha do prazo do RSVP: relógio + texto; com o prazo encerrado fica mais apagada. */
@Composable
internal fun HeroDeadlineLine(text: String, open: Boolean, modifier: Modifier = Modifier) {
    val color = SaqzTheme.colors.onPrimary.copy(alpha = if (open) HeroTextAlpha else HeroMutedAlpha)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(HeroIconGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaqzIcon(SaqzIcons.Clock, tint = color, size = HeroLineIconSize)
        Text(
            text = text,
            style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
            color = color,
        )
    }
}

/**
 * Aviso sobre o azul. Não há token de erro legível ali: o aviso é branco com o ícone de alerta.
 * [tag] nulo não marca a linha; [action] entra à direita e o texto fica com a largura que sobra.
 */
@Composable
internal fun HeroAlertLine(
    text: String,
    tag: String?,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val color = SaqzTheme.colors.onPrimary.copy(alpha = HeroTextAlpha)
    Row(
        modifier = if (tag != null) modifier.testTag(tag) else modifier,
        horizontalArrangement = Arrangement.spacedBy(HeroIconGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaqzIcon(SaqzIcons.CircleAlert, tint = color, size = HeroInlineIconSize)
        Text(
            text = text,
            style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.Medium),
            color = color,
            modifier = if (action != null) Modifier.weight(1f) else Modifier,
        )
        action?.invoke()
    }
}

/**
 * Quem vai, sobre o azul: pilha de avatares com anel azul e "+N" lima, o resumo e, quando a
 * tela manda, o chip de escassez ("Restam N vagas"). [spotsChip] nulo esconde o chip.
 */
@Composable
internal fun HeroRosterRow(
    names: List<String>,
    summary: String,
    spotsChip: String?,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzAvatarStack(
            names = names,
            ring = colors.primary,
            overflowContainer = colors.accent,
            overflowContent = colors.textPrimary,
        )
        Text(
            text = summary,
            style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onPrimary.copy(alpha = HeroSummaryAlpha),
            modifier = Modifier.weight(1f),
        )
        if (spotsChip != null) {
            SaqzStatusChip(text = spotsChip, tone = SaqzChipTone.Inverse, dot = true)
        }
    }
}

/**
 * Seletor de presença sobre o hero azul. Nasceu na Início (VUL-191/218); o detalhe do grupo é
 * o segundo uso, por isso textos e `testTag` chegam por parâmetro.
 *
 * Sem resposta é o momento do toque: "Vou" (lima) e "Não vou" (contorno) grandes. Depois de
 * respondido os botões somem e fica o painel com a resposta e "Alterar". Em espera entram o
 * chip, a caixa e as ações da fila (`HomeWaitlist*`, com as tags de `HomeWaitlistTags`, que as
 * duas telas compartilham). [responseFailed] acrescenta o aviso branco no fim.
 *
 * É extensão de `ColumnScope` porque emite irmãos direto na coluna do `SaqzHeroCard`: o
 * espaço entre eles é o do hero.
 */
@Composable
internal fun ColumnScope.HeroAttendanceControls(
    status: AttendanceStatus?,
    confirmationOpen: Boolean,
    responding: Boolean,
    responseFailed: Boolean,
    waitlistKind: HomeWaitlistKind?,
    waitlistPosition: Long?,
    onRespond: (AttendanceIntent) -> Unit,
    onViewGame: () -> Unit,
    texts: HeroAttendanceTexts,
    tags: HeroAttendanceTags,
) {
    when {
        status == AttendanceStatus.Waitlisted -> {
            val kind = waitlistKind ?: HomeWaitlistKind.Reserva
            HomeWaitlistChip(kind = kind, position = waitlistPosition)
            HomeWaitlistInfoBox(kind = kind)
            HomeWaitlistActions(
                kind = kind,
                responding = responding,
                confirmationOpen = confirmationOpen,
                onLeave = { onRespond(AttendanceIntent.Decline) },
                onViewGame = onViewGame,
            )
        }
        // Pendente é o momento do toque: botões grandes, pergunta aberta.
        status == null -> HeroResponseRow(
            status = null,
            confirmationOpen = confirmationOpen,
            responding = responding,
            size = SaqzButtonSize.Md,
            onRespond = onRespond,
            texts = texts,
            tags = tags,
        )
        // Depois de respondido, os botões somem — dois botões habilitados num jogo já
        // confirmado pareciam uma pergunta sem resposta e confundiam o atleta.
        else -> HeroAnsweredStatus(
            status = status,
            responding = responding,
            changeEnabled = confirmationOpen,
            onRespond = onRespond,
            texts = texts,
            tags = tags,
        )
    }
    if (responseFailed) {
        HeroAlertLine(text = texts.error, tag = tags.error)
    }
}

/**
 * "Vou" é sempre o CTA lima; "Não vou" é contorno branco. Com resposta marcada (modo
 * alterar) a marcada leva o check: "Vou" continua lima, "Não vou" marcado vira branco sólido
 * e o outro cai para contorno.
 */
@Composable
private fun HeroResponseRow(
    status: AttendanceStatus?,
    confirmationOpen: Boolean,
    responding: Boolean,
    size: SaqzButtonSize,
    onRespond: (AttendanceIntent) -> Unit,
    texts: HeroAttendanceTexts,
    tags: HeroAttendanceTags,
) {
    val declined = status == AttendanceStatus.Declined
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
    ) {
        HeroResponseButton(
            label = texts.yes,
            variant = if (declined) HeroResponseVariant.Outline else HeroResponseVariant.Accent,
            selected = status == AttendanceStatus.Confirmed,
            loading = responding && status == AttendanceStatus.Confirmed,
            enabled = confirmationOpen && !responding,
            size = size,
            onClick = { onRespond(AttendanceIntent.Confirm) },
            modifier = Modifier.weight(1f).testTag(tags.yes),
        )
        HeroResponseButton(
            label = texts.no,
            variant = if (declined) HeroResponseVariant.Inverse else HeroResponseVariant.Outline,
            selected = declined,
            loading = responding && declined,
            enabled = confirmationOpen && !responding,
            size = size,
            onClick = { onRespond(AttendanceIntent.Decline) },
            modifier = Modifier.weight(1f).testTag(tags.no),
        )
    }
}

private enum class HeroResponseVariant { Accent, Inverse, Outline }

/**
 * Estado de quem já respondeu: painel único com a resposta e "Alterar" — os botões só
 * reaparecem a pedido (`editing`), porque dois botões habilitados num jogo já
 * confirmado não dizem que a resposta foi registrada e parecem exigir nova ação.
 * `editing` vive na composição e morre quando o status muda: responder de novo
 * (com sucesso) volta para o painel automaticamente.
 */
@Composable
private fun HeroAnsweredStatus(
    status: AttendanceStatus,
    responding: Boolean,
    changeEnabled: Boolean,
    onRespond: (AttendanceIntent) -> Unit,
    texts: HeroAttendanceTexts,
    tags: HeroAttendanceTags,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    var editing by remember(status) { mutableStateOf(false) }
    if (editing) {
        Column(verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
            HeroResponseRow(
                status = status,
                confirmationOpen = changeEnabled,
                responding = responding,
                size = SaqzButtonSize.Sm,
                onRespond = onRespond,
                texts = texts,
                tags = tags,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SaqzButton(
                    label = texts.cancel,
                    onClick = { editing = false },
                    variant = SaqzButtonVariant.Ghost,
                    contentColor = colors.onPrimary,
                    size = SaqzButtonSize.Sm,
                    enabled = !responding,
                    modifier = Modifier.testTag(tags.cancel),
                )
            }
        }
    } else {
        val (color, text) = when (status) {
            AttendanceStatus.Confirmed -> colors.success to texts.confirmed
            else -> colors.onPrimary.copy(alpha = HeroDotMutedAlpha) to texts.declined
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.onPrimary.copy(alpha = HeroPanelAlpha), RoundedCornerShape(metrics.inputRadius))
                .padding(
                    start = metrics.blockGap,
                    end = metrics.subGrid,
                    top = metrics.subGrid,
                    bottom = metrics.subGrid,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.subGrid * 2),
        ) {
            Box(
                modifier = Modifier
                    .size(HeroStatusDot)
                    .background(color, CircleShape),
            )
            Text(
                text = text,
                style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onPrimary,
                modifier = Modifier.weight(1f),
            )
            SaqzButton(
                label = texts.change,
                onClick = { editing = true },
                variant = SaqzButtonVariant.Ghost,
                contentColor = colors.onPrimary,
                size = SaqzButtonSize.Sm,
                enabled = changeEnabled && !responding,
                loading = responding,
                modifier = Modifier.testTag(tags.change),
            )
        }
    }
}

@Composable
private fun HeroResponseButton(
    label: String,
    variant: HeroResponseVariant,
    selected: Boolean,
    loading: Boolean,
    enabled: Boolean,
    size: SaqzButtonSize,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val saqzVariant = when (variant) {
        HeroResponseVariant.Accent -> SaqzButtonVariant.Accent
        HeroResponseVariant.Inverse -> SaqzButtonVariant.Inverse
        HeroResponseVariant.Outline -> SaqzButtonVariant.Ghost
    }
    val outline = variant == HeroResponseVariant.Outline
    SaqzButton(
        label = label,
        onClick = onClick,
        modifier = modifier,
        variant = saqzVariant,
        size = size,
        fullWidth = true,
        enabled = enabled,
        loading = loading,
        contentColor = if (outline) colors.onPrimary else null,
        borderColor = if (outline) colors.onPrimary.copy(alpha = HeroOutlineAlpha) else null,
        leadingContent = if (selected) {
            { tint -> SaqzIcon(SaqzIcons.Check, tint = tint, size = HeroCheckSize) }
        } else {
            null
        },
    )
}
