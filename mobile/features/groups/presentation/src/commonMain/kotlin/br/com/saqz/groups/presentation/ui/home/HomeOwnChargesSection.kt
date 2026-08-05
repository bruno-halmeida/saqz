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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.home.HomeIntent
import br.com.saqz.groups.presentation.home.HomeOwnChargeGroupUi
import br.com.saqz.groups.presentation.home.HomeOwnChargesUi
import br.com.saqz.groups.presentation.ui.finance.PixCard
import br.com.saqz.groups.presentation.ui.finance.groupcash.PixUi
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.home_own_charges_caption
import br.com.saqz.groups.resources.own_charges_note
import br.com.saqz.groups.resources.own_charges_title
import org.jetbrains.compose.resources.stringResource

/**
 * VUL-202 — "o que **eu** devo", na Home, abaixo do hero e acima de "Seus grupos".
 *
 * A tela do admin mostra as duas direções de dinheiro ao mesmo tempo, com palavras quase
 * iguais: "Esperando você" são as mensalidades **do grupo, que ele recebe**; esta seção é o
 * que **ele deve**. O título repete o da tela irmã (VUL-203, no detalhe do grupo) de
 * propósito — mesmo vocabulário para a mesma ideia — e a legenda abaixo dele é quem diz a
 * direção, porque só "Minhas cobranças" ainda pode ser lido como "as cobranças que emiti".
 *
 * Pagar é manual (decisão do fluxo 5): o único verbo aqui é copiar a chave Pix do grupo,
 * pelo mesmo [PixCard] do VUL-203. A linha leva ao detalhe do grupo, onde mora a lista
 * completa com histórico.
 */
@Composable
internal fun HomeOwnChargesSection(
    ownCharges: HomeOwnChargesUi,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.fillMaxWidth().testTag(HomeTags.OwnCharges),
    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
) {
    SaqzSectionHeader(title = stringResource(Res.string.own_charges_title))
    Text(
        text = stringResource(Res.string.home_own_charges_caption),
        style = SaqzTheme.typography.caption,
        color = SaqzTheme.colors.textSecondary,
    )
    ownCharges.groups.forEach { group ->
        HomeOwnChargeCard(group = group, onIntent = onIntent)
        group.pix?.let { pix ->
            PixCard(
                pix = pix,
                onCopy = { onIntent(HomeIntent.CopyPix(group.groupId)) },
                cardTag = HomeTags.ownChargePix(group.groupId),
                copyTag = HomeTags.ownChargePixCopy(group.groupId),
            )
            // A mesma nota da tela irmã, na mesma chave, e logo abaixo da chave a que ela
            // se refere: com dois grupos na lista, uma nota só no fim do bloco parecia
            // falar do último card — que pode ser justamente o grupo sem Pix.
            Text(
                text = stringResource(Res.string.own_charges_note),
                style = SaqzTheme.typography.caption,
                color = SaqzTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun HomeOwnChargeCard(
    group: HomeOwnChargeGroupUi,
    onIntent: (HomeIntent) -> Unit,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    SaqzCard(
        tone = SaqzCardTone.Soft,
        modifier = Modifier
            .clickable(
                onClickLabel = group.groupName,
                role = Role.Button,
                onClick = { onIntent(HomeIntent.OpenGroup(group.groupId)) },
            )
            .semantics { contentDescription = group.groupName }
            .testTag(HomeTags.ownCharge(group.groupId)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
            ) {
                Text(
                    text = group.groupName,
                    style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                )
                Text(
                    text = group.competence,
                    style = SaqzTheme.typography.caption,
                    color = colors.textSecondary,
                )
                group.countLabel?.let {
                    Text(
                        text = it,
                        style = SaqzTheme.typography.caption,
                        color = colors.textSecondary,
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
            ) {
                Text(
                    text = group.amountLabel,
                    style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                )
                // O vencido acende no âmbar do design system; no prazo fica no cinza de
                // metadado. É a mesma regra de tom do aviso do shell, e o `overdue` dos
                // dois vem do servidor.
                Text(
                    text = group.dueLabel,
                    style = SaqzTheme.typography.caption,
                    color = if (group.overdue) colors.warningForeground else colors.textSecondary,
                )
            }
        }
    }
}

/**
 * O aviso permanente do shell (VUL-202). Formato do [EmailVerificationBanner][1]: faixa
 * acima do conteúdo, texto curto, sem dispensar — enquanto houver cobrança em aberto ele
 * fica, e some quando o admin baixa a cobrança.
 *
 * Dois tons: navy (a linguagem de faixa do app) enquanto tudo está no prazo, âmbar quando
 * alguma venceu. É lembrete, não alarme — daí nenhum vermelho, e o toque é a única ação.
 *
 * [1]: `compose-app/.../shell/EmailVerificationBanner.kt`
 */
@Composable
internal fun HomeOwnChargesBanner(
    charges: HomeOwnChargesUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val container = if (charges.overdue) colors.warning.copy(alpha = WarningBandAlpha) else colors.textPrimary
    val content = if (charges.overdue) colors.warningForeground else colors.surface
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.grid)
            .background(container, RoundedCornerShape(metrics.cardRadius))
            .clickable(onClickLabel = charges.bannerText, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = charges.bannerContentDescription }
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap)
            .testTag(HomeTags.OwnChargesBanner),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.grid),
    ) {
        SaqzIcon(
            SaqzIcons.CreditCard,
            tint = if (charges.overdue) content else colors.accent,
            size = metrics.grid * 2,
        )
        Text(
            text = charges.bannerText,
            style = SaqzTheme.typography.support,
            color = content,
            modifier = Modifier.weight(1f),
        )
        SaqzIcon(SaqzIcons.ChevronRight, tint = content, size = metrics.grid * 2)
    }
}

/** O mesmo véu do `SaqzStatusChip` no tom warning — a faixa é a versão larga do chip. */
private const val WarningBandAlpha = 0.14f

@Preview
@Composable
private fun HomeOwnChargesBannerPreview() = SaqzTheme {
    HomeOwnChargesBanner(charges = previewOwnCharges(), onClick = {})
}

@Preview
@Composable
private fun HomeOwnChargesBannerOverduePreview() = SaqzTheme {
    HomeOwnChargesBanner(charges = previewOwnChargesOverdue(), onClick = {})
}

@Preview(name = "Minhas cobranças na Home", widthDp = 390)
@Composable
private fun HomeOwnChargesSectionPreview() = SaqzTheme {
    HomeOwnChargesSection(ownCharges = previewOwnCharges(), onIntent = {})
}

internal fun previewOwnCharges() = HomeOwnChargesUi(
    bannerText = "Você tem R$ 80,00 em aberto",
    bannerContentDescription = "Você tem R$ 80,00 em aberto. Abrir a Início para ver suas cobranças.",
    overdue = false,
    groups = listOf(
        HomeOwnChargeGroupUi(
            groupId = "ceret",
            groupName = "Vôlei do CERET",
            competence = "Mensalidade · Julho",
            amountLabel = "R$ 80,00",
            dueLabel = "Vence em 05/08",
            overdue = false,
            countLabel = null,
            pix = PixUi(key = "ceret@volei.com.br", label = "Ana Souza · Nubank"),
        ),
    ),
)

internal fun previewOwnChargesOverdue() = HomeOwnChargesUi(
    bannerText = "Você tem R$ 140,00 em aberto em 2 grupos",
    bannerContentDescription = "Você tem R$ 140,00 em aberto em 2 grupos. Abrir a Início para ver suas cobranças.",
    overdue = true,
    groups = previewOwnCharges().groups.map {
        it.copy(dueLabel = "Venceu em 05/07", overdue = true, countLabel = "2 cobranças em aberto")
    } + HomeOwnChargeGroupUi(
        groupId = "pacaembu",
        groupName = "Vôlei Pacaembu",
        competence = "Jogo avulso",
        amountLabel = "R$ 60,00",
        dueLabel = "Vence em 12/08",
        overdue = false,
        countLabel = null,
        pix = null,
    ),
)
