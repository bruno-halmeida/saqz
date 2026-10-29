package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.gamedetail.AdvancePaymentUi
import br.com.saqz.groups.presentation.gamedetail.GameDetailIntent
import br.com.saqz.groups.presentation.gamedetail.GameDetailState
import br.com.saqz.groups.presentation.gamedetail.GameDetailWaitlistUi
import br.com.saqz.groups.presentation.gamedetail.WaitlistPaymentUi
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.advance_payment_box_body
import br.com.saqz.groups.resources.advance_payment_box_title
import br.com.saqz.groups.resources.advance_payment_copy_pix
import br.com.saqz.groups.resources.advance_payment_paid
import br.com.saqz.groups.resources.advance_payment_row_guest_missing
import br.com.saqz.groups.resources.advance_payment_row_missing
import br.com.saqz.groups.resources.advance_payment_row_paid
import br.com.saqz.groups.resources.advance_payment_row_place
import br.com.saqz.groups.resources.advance_payment_row_received
import br.com.saqz.groups.resources.advance_payment_sheet_back
import br.com.saqz.groups.resources.advance_payment_sheet_body
import br.com.saqz.groups.resources.advance_payment_sheet_later
import br.com.saqz.groups.resources.advance_payment_sheet_later_hint
import br.com.saqz.groups.resources.advance_payment_sheet_paid
import br.com.saqz.groups.resources.advance_payment_sheet_paid_hint
import br.com.saqz.groups.resources.advance_payment_sheet_title
import br.com.saqz.groups.resources.game_waitlist_promotion_reason
import br.com.saqz.groups.resources.own_charges_pay_in_app
import org.jetbrains.compose.resources.stringResource

internal object GameAdvancePaymentTags {
    const val Box = "game-detail-advance-payment"
    const val CopyPix = "game-detail-advance-payment-copy-pix"
    const val Pay = "game-detail-advance-payment-pay"
    const val Paid = "game-detail-advance-payment-paid"
    const val Sheet = "game-detail-advance-place-sheet"
    const val SheetPaid = "game-detail-advance-place-paid"
    const val SheetLater = "game-detail-advance-place-later"
    const val SheetBack = "game-detail-advance-place-back"

    fun line(rowId: String) = "game-detail-advance-line-$rowId"
    fun receive(rowId: String) = "game-detail-advance-receive-$rowId"
}

/**
 * PROM-010 (prancha Antecipado-avulso): no azul do jogo, o avulso na espera vê quanto pagar e como. Pendente: a caixa
 * com Copiar o Pix do grupo (botão branco) ou, com recebimento pelo app, o Pagar. Depois do Recebi, uma linha só.
 */
@Composable
internal fun HeroAdvancePayment(payment: AdvancePaymentUi, onIntent: (GameDetailIntent) -> Unit) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    if (payment.paid) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(metrics.grid),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag(GameAdvancePaymentTags.Paid),
        ) {
            SaqzIcon(SaqzIcons.Check, tint = colors.accent)
            Text(
                text = stringResource(Res.string.advance_payment_paid),
                style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onPrimary,
            )
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.onPrimary.copy(alpha = BoxAlpha), RoundedCornerShape(metrics.cardRadius))
            .padding(metrics.blockGap)
            .testTag(GameAdvancePaymentTags.Box),
        verticalArrangement = Arrangement.spacedBy(metrics.grid),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.grid), verticalAlignment = Alignment.CenterVertically) {
            SaqzIcon(SaqzIcons.Banknote, tint = colors.onPrimary)
            Text(
                text = stringResource(Res.string.advance_payment_box_title, payment.amount),
                style = SaqzTheme.typography.label,
                color = colors.onPrimary,
            )
        }
        Text(
            text = stringResource(Res.string.advance_payment_box_body),
            style = SaqzTheme.typography.support,
            color = colors.onPrimary.copy(alpha = BodyAlpha),
        )
        when {
            payment.payInApp -> SaqzButton(
                label = stringResource(Res.string.own_charges_pay_in_app),
                onClick = { onIntent(GameDetailIntent.PayAdvance) },
                variant = SaqzButtonVariant.Inverse,
                fullWidth = true,
                modifier = Modifier.testTag(GameAdvancePaymentTags.Pay),
            )
            payment.pixKey != null -> SaqzButton(
                label = stringResource(Res.string.advance_payment_copy_pix),
                onClick = { onIntent(GameDetailIntent.CopyGroupPix) },
                variant = SaqzButtonVariant.Inverse,
                fullWidth = true,
                modifier = Modifier.testTag(GameAdvancePaymentTags.CopyPix),
            )
        }
    }
}

/**
 * PROM-010 (prancha Antecipado-espera): a linha do pagamento embaixo de cada pessoa da espera do gestor. Pagou: verde
 * e Colocar; falta pagar: Recebi e Colocar (que abre a folha). Convidado: "<anfitrião> não pagou R$ 20".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WaitlistPaymentLine(
    state: GameDetailState,
    person: GameDetailWaitlistUi,
    payment: WaitlistPaymentUi,
    onIntent: (GameDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reason = stringResource(Res.string.game_waitlist_promotion_reason)
    // FlowRow: com fonte grande ou tela estreita os botões descem para baixo do chip em vez de espremê-lo.
    FlowRow(
        modifier = modifier.fillMaxWidth().testTag(GameAdvancePaymentTags.line(person.id)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        SaqzStatusChip(
            text = when {
                payment.paid -> stringResource(Res.string.advance_payment_row_paid, payment.amount)
                payment.hostName != null -> stringResource(Res.string.advance_payment_row_guest_missing, payment.hostName, payment.amount)
                else -> stringResource(Res.string.advance_payment_row_missing, payment.amount)
            },
            tone = if (payment.paid) SaqzChipTone.Success else SaqzChipTone.Warning,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid), verticalAlignment = Alignment.CenterVertically) {
        val idle = state.promotingMemberId == null && state.receivingPaymentId == null
        if (!payment.paid) {
            SaqzButton(
                label = stringResource(Res.string.advance_payment_row_received),
                onClick = { onIntent(GameDetailIntent.ReceiveWaitlistPayment(person.id)) },
                variant = SaqzButtonVariant.Ghost,
                size = SaqzButtonSize.Sm,
                loading = state.receivingPaymentId == person.id,
                enabled = idle,
                modifier = Modifier.testTag(GameAdvancePaymentTags.receive(person.id)),
            )
        }
        if (state.canPromote) {
            SaqzButton(
                label = stringResource(Res.string.advance_payment_row_place),
                onClick = {
                    if (payment.paid) {
                        onIntent(GameDetailIntent.Promote(person.guest?.hostId ?: person.id, reason, person.guest?.guestSeq ?: 0))
                    } else {
                        onIntent(GameDetailIntent.OpenAdvancePlace(person.id))
                    }
                },
                variant = if (payment.paid) SaqzButtonVariant.Primary else SaqzButtonVariant.Secondary,
                size = SaqzButtonSize.Sm,
                loading = state.promotingMemberId == person.id,
                enabled = idle,
                modifier = Modifier.testTag(GameWaitlistTags.promote(person.id)),
            )
        }
        }
    }
}

/**
 * PROM-010 (prancha Antecipado-folha): colocar quem não pagou. Os textos não supõem gênero ("Rafa ainda não pagou",
 * "Colocar, já pagou"). Cada opção fecha a folha antes de colocar, então um segundo toque não chega (GER-001).
 */
@Composable
internal fun AdvancePlaceSheet(state: GameDetailState, onIntent: (GameDetailIntent) -> Unit) {
    val person = state.waitlist.firstOrNull { it.id == state.advancePlaceRowId }
    val payment = person?.payment
    val reason = stringResource(Res.string.game_waitlist_promotion_reason)
    val name = person?.name.orEmpty()
    val amount = payment?.amount.orEmpty()
    SaqzBottomSheet(
        open = person != null && payment != null,
        onClose = { onIntent(GameDetailIntent.DismissAdvancePlace) },
        modifier = Modifier.testTag(GameAdvancePaymentTags.Sheet),
        title = stringResource(Res.string.advance_payment_sheet_title, name),
        description = stringResource(Res.string.advance_payment_sheet_body, amount, name),
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
                SheetOption(
                    label = stringResource(Res.string.advance_payment_sheet_paid),
                    hint = stringResource(Res.string.advance_payment_sheet_paid_hint, amount),
                    variant = SaqzButtonVariant.Primary,
                    tag = GameAdvancePaymentTags.SheetPaid,
                ) { onIntent(GameDetailIntent.ConfirmAdvancePlace(markPaid = true, reason = reason)) }
                SheetOption(
                    label = stringResource(Res.string.advance_payment_sheet_later),
                    hint = stringResource(Res.string.advance_payment_sheet_later_hint),
                    variant = SaqzButtonVariant.Secondary,
                    tag = GameAdvancePaymentTags.SheetLater,
                ) { onIntent(GameDetailIntent.ConfirmAdvancePlace(markPaid = false, reason = reason)) }
                SaqzButton(
                    label = stringResource(Res.string.advance_payment_sheet_back),
                    onClick = { onIntent(GameDetailIntent.DismissAdvancePlace) },
                    variant = SaqzButtonVariant.Ghost,
                    fullWidth = true,
                    modifier = Modifier.testTag(GameAdvancePaymentTags.SheetBack),
                )
            }
        },
    )
}

@Composable
private fun SheetOption(label: String, hint: String, variant: SaqzButtonVariant, tag: String, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
        SaqzButton(label = label, onClick = onClick, variant = variant, fullWidth = true, modifier = Modifier.testTag(tag))
        Text(
            text = hint,
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val BoxAlpha = 0.12f
private const val BodyAlpha = 0.82f
