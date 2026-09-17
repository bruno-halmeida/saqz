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
import androidx.compose.ui.tooling.preview.Preview
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.home.HomeIntent
import br.com.saqz.groups.presentation.home.HomeOwnChargeGroupUi
import br.com.saqz.groups.presentation.home.HomeOwnChargesUi
import br.com.saqz.groups.presentation.ui.finance.groupcash.PixUi
import br.com.saqz.groups.presentation.ui.components.OwnChargeTicket
import br.com.saqz.groups.presentation.ui.components.OwnChargeTicketTags
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_cashbox_pix_copy
import br.com.saqz.groups.resources.home_own_charge_copied
import br.com.saqz.groups.resources.home_own_charge_pix_receiver
import br.com.saqz.groups.resources.own_charges_title
import org.jetbrains.compose.resources.stringResource

/**
 * VUL-220 — "o que **eu** devo", na Home, logo abaixo do hero: só os grupos com cobrança
 * **vencida**. No prazo a dívida mora no detalhe do grupo e em Perfil → Mensalidades; aqui
 * a seção nem aparece (quem decide é `overdueGroups`, e o `HomeContent` só a monta quando
 * há algo). O título repete o da tela irmã (VUL-203) de propósito.
 *
 * Pagar é manual (decisão do fluxo 5): o único verbo é copiar a chave Pix. O card inteiro
 * leva ao detalhe do grupo, onde mora a lista completa com histórico. Depois de copiar, o
 * botão vira "Chave copiada" por 2 s ([pixCopiedGroupId]) e o toast explica a baixa manual.
 */
@Composable
internal fun HomeOwnChargesSection(
    ownCharges: HomeOwnChargesUi,
    pixCopiedGroupId: String?,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier = modifier.fillMaxWidth().testTag(HomeTags.OwnCharges),
    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
) {
    SaqzSectionHeader(title = stringResource(Res.string.own_charges_title))
    ownCharges.overdueGroups.forEach { group ->
        HomeOwnChargeTicket(
            group = group,
            copied = group.groupId == pixCopiedGroupId,
            onIntent = onIntent,
        )
    }
}

@Composable
private fun HomeOwnChargeTicket(
    group: HomeOwnChargeGroupUi,
    copied: Boolean,
    onIntent: (HomeIntent) -> Unit,
) {
    val copyPix = { onIntent(HomeIntent.CopyPix(group.groupId)) }
    OwnChargeTicket(
        eyebrow = group.competence,
        amountLabel = group.amountLabel,
        dueChipLabel = group.dueLabel,
        dueChipOverdue = group.overdue,
        headerChip = group.groupName,
        countLabel = group.countLabel,
        receiverLabel = group.pix?.let { stringResource(Res.string.home_own_charge_pix_receiver, it.label ?: it.key) },
        copied = copied,
        copyLabel = stringResource(Res.string.group_cashbox_pix_copy),
        copiedLabel = stringResource(Res.string.home_own_charge_copied),
        // Sem chave Pix o ticket não tem verbo: somem a linha do recebedor e o botão.
        onCopy = copyPix.takeIf { group.pix != null },
        onClick = { onIntent(HomeIntent.OpenGroup(group.groupId)) },
        contentDescription = group.groupName,
        tags = OwnChargeTicketTags(
            card = HomeTags.ownCharge(group.groupId),
            copy = HomeTags.ownChargePixCopy(group.groupId),
        ),
    )
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
    HomeOwnChargesSection(ownCharges = previewOwnChargesOverdue(), pixCopiedGroupId = null, onIntent = {})
}

@Preview(name = "Minhas cobranças · chave copiada", widthDp = 390)
@Composable
private fun HomeOwnChargesSectionCopiedPreview() = SaqzTheme {
    HomeOwnChargesSection(ownCharges = previewOwnChargesOverdue(), pixCopiedGroupId = "ceret", onIntent = {})
}

internal fun previewOwnCharges() = HomeOwnChargesUi(
    bannerText = "Você tem R$ 80,00 em aberto",
    bannerContentDescription = "Você tem R$ 80,00 em aberto. Abrir a Início para ver suas cobranças.",
    overdue = false,
    groups = listOf(
        HomeOwnChargeGroupUi(
            groupId = "ceret",
            groupName = "Vôlei do CERET",
            competence = "Mensalidade de julho",
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
