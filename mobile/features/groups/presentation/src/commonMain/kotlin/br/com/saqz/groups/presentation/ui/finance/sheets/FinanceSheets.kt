package br.com.saqz.groups.presentation.ui.finance.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.material.Text
import br.com.saqz.designsystem.SaqzAvatar
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzChoiceChip
import br.com.saqz.designsystem.SaqzSegmented
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.finance.PaidMethod
import br.com.saqz.groups.presentation.ui.finance.groupcash.DebtorUi
import br.com.saqz.groups.resources.sheet_charge_close
import br.com.saqz.groups.resources.sheet_charge_notification_title
import br.com.saqz.groups.resources.sheet_charge_notification_note
import br.com.saqz.groups.resources.sheet_charge_failure
import br.com.saqz.groups.resources.sheet_charge_success
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.sheet_charge_all
import br.com.saqz.groups.resources.sheet_charge_clear
import br.com.saqz.groups.resources.sheet_charge_selected
import br.com.saqz.groups.resources.sheet_charge_cancel
import br.com.saqz.groups.resources.sheet_charge_description
import br.com.saqz.groups.resources.sheet_charge_send
import br.com.saqz.groups.resources.sheet_charge_title
import br.com.saqz.groups.resources.sheet_receipt_amount
import br.com.saqz.groups.resources.sheet_receipt_cancel
import br.com.saqz.groups.resources.sheet_receipt_confirm
import br.com.saqz.groups.resources.sheet_receipt_method
import br.com.saqz.groups.resources.sheet_receipt_method_cash
import br.com.saqz.groups.resources.sheet_receipt_method_other
import br.com.saqz.groups.resources.sheet_receipt_method_pix
import br.com.saqz.groups.resources.sheet_receipt_reference
import br.com.saqz.groups.resources.sheet_receipt_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal object FinanceSheetsTags {
    const val Charge = "finance-charge-sheet"
    const val ChargeRecipient = "finance-charge-recipient"
    const val ChargePix = "finance-charge-pix"
    const val ChargeCopyPix = "finance-charge-copy-pix"
    const val ChargeWhatsApp = "finance-charge-whatsapp"
    const val ChargeSuccess = "finance-charge-success"
    const val ChargeSend = "finance-charge-send"
    const val ChargeAll = "finance-charge-all"
    const val ChargeClear = "finance-charge-clear"
    const val ChargeSelection = "finance-charge-selection"
    const val Receipt = "finance-receipt-sheet"
    const val ReceiptAvatar = "finance-receipt-avatar"
    const val ReceiptAmount = "finance-receipt-amount"
    const val ReceiptMethod = "finance-receipt-method"
    const val ReceiptConfirm = "finance-receipt-confirm"

    fun chargeRecipient(chargeId: String) = "$ChargeRecipient-$chargeId"
}

@Composable
internal fun ChargeSheet(
    open: Boolean,
    debtors: List<DebtorUi>,
    onClose: () -> Unit,
    onSend: (List<String>) -> Unit,
    delivery: ChargeReminderState = ChargeReminderState(),
) {
    var selectedChargeIds by remember(open, debtors.map { it.chargeId }) {
        mutableStateOf(setOfNotNull(debtors.firstOrNull()?.chargeId))
    }
    val selected = debtors.filter { it.chargeId in selectedChargeIds }
    val editable = !delivery.sending && delivery.sent == null
    SaqzBottomSheet(
        open = open,
        onClose = { if (!delivery.sending) onClose() },
        modifier = Modifier.testTag(FinanceSheetsTags.Charge),
        title = pluralStringResource(Res.plurals.sheet_charge_title, debtors.size, debtors.size),
        description = stringResource(Res.string.sheet_charge_description),
        splitFooter = {
            SaqzButton(
                label = stringResource(if (delivery.sent == null) Res.string.sheet_charge_cancel else Res.string.sheet_charge_close),
                onClick = onClose,
                enabled = !delivery.sending,
                variant = SaqzButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
            SaqzButton(
                label = stringResource(Res.string.sheet_charge_send, selected.size),
                onClick = { if (editable && selected.isNotEmpty()) onSend(selected.map { it.chargeId }) },
                loading = delivery.sending,
                enabled = editable && selected.isNotEmpty(),
                modifier = Modifier.weight(1f).testTag(FinanceSheetsTags.ChargeSend),
            )
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            SaqzButton(
                label = stringResource(Res.string.sheet_charge_all),
                onClick = { selectedChargeIds = debtors.map { it.chargeId }.toSet() },
                enabled = editable && selected.size != debtors.size,
                variant = SaqzButtonVariant.Secondary,
                modifier = Modifier.weight(1f).testTag(FinanceSheetsTags.ChargeAll),
            )
            SaqzButton(
                label = stringResource(Res.string.sheet_charge_clear),
                onClick = { selectedChargeIds = emptySet() },
                enabled = editable && selected.isNotEmpty(),
                variant = SaqzButtonVariant.Ghost,
                modifier = Modifier.weight(1f).testTag(FinanceSheetsTags.ChargeClear),
            )
        }
        Text(
            text = stringResource(Res.string.sheet_charge_selected, selected.size,
                br.com.saqz.core.common.formatting.formatBrl(selected.sumOf { it.amountCents })),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
            modifier = Modifier.testTag(FinanceSheetsTags.ChargeSelection),
        )
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            debtors.forEach { debtor ->
                SaqzChoiceChip(
                    label = "${debtor.name} · ${debtor.amountLabel}",
                    selected = debtor.chargeId in selectedChargeIds,
                    onClick = {
                        if (editable) selectedChargeIds = if (debtor.chargeId in selectedChargeIds) {
                            selectedChargeIds - debtor.chargeId
                        } else selectedChargeIds + debtor.chargeId
                    },
                    modifier = Modifier.fillMaxWidth().testTag(FinanceSheetsTags.chargeRecipient(debtor.chargeId)),
                )
            }
        }
        SaqzCard(tone = SaqzCardTone.Soft) {
            Text(stringResource(Res.string.sheet_charge_notification_title), style = SaqzTheme.typography.body)
            Text(stringResource(Res.string.sheet_charge_notification_note), style = SaqzTheme.typography.support)
        }
        if (delivery.failed) Text(stringResource(Res.string.sheet_charge_failure), color = SaqzTheme.colors.errorForeground)
        delivery.sent?.let {
            Text(stringResource(Res.string.sheet_charge_success, it), modifier = Modifier.testTag(FinanceSheetsTags.ChargeSuccess))
        }
    }
}

@Composable
internal fun ReceiptSheet(
    open: Boolean,
    debtor: DebtorUi?,
    onClose: () -> Unit,
    onConfirm: (PaidMethod) -> Unit,
) {
    var selectedMethod by remember(open) { mutableStateOf(PaidMethod.Pix) }
    SaqzBottomSheet(
        open = open,
        onClose = onClose,
        modifier = Modifier.testTag(FinanceSheetsTags.Receipt),
        title = stringResource(Res.string.sheet_receipt_title),
        description = debtor?.referenceLabel,
        splitFooter = {
            SaqzButton(
                label = stringResource(Res.string.sheet_receipt_cancel),
                onClick = onClose,
                variant = SaqzButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
            SaqzButton(
                label = stringResource(Res.string.sheet_receipt_confirm),
                onClick = { onConfirm(selectedMethod) },
                enabled = debtor != null,
                modifier = Modifier.weight(1f).testTag(FinanceSheetsTags.ReceiptConfirm),
            )
        },
    ) {
        debtor?.let { receipt ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
            ) {
                SaqzAvatar(name = receipt.name, modifier = Modifier.testTag(FinanceSheetsTags.ReceiptAvatar))
                Column {
                    Text(
                        text = receipt.name,
                        style = SaqzTheme.typography.subtitle,
                        color = SaqzTheme.colors.textPrimary,
                    )
                    Text(
                        text = receipt.referenceLabel,
                        style = SaqzTheme.typography.support,
                        color = SaqzTheme.colors.textSecondary,
                    )
                }
            }
            SaqzCard(modifier = Modifier.testTag(FinanceSheetsTags.ReceiptAmount)) {
                Text(
                    text = stringResource(Res.string.sheet_receipt_amount),
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textSecondary,
                )
                Text(
                    text = receipt.amountLabel,
                    style = SaqzTheme.typography.title,
                    color = SaqzTheme.colors.textPrimary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                Text(
                    text = stringResource(Res.string.sheet_receipt_reference),
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textSecondary,
                )
                Text(
                    text = receipt.referenceLabel,
                    style = SaqzTheme.typography.body,
                    color = SaqzTheme.colors.textPrimary,
                )
            }
            Column(
                modifier = Modifier.testTag(FinanceSheetsTags.ReceiptMethod),
                verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
            ) {
                Text(
                    text = stringResource(Res.string.sheet_receipt_method),
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textSecondary,
                )
                SaqzSegmented(
                    options = listOf(
                        stringResource(Res.string.sheet_receipt_method_pix),
                        stringResource(Res.string.sheet_receipt_method_cash),
                        stringResource(Res.string.sheet_receipt_method_other),
                    ),
                    selected = selectedMethod.ordinal,
                    onSelect = { selectedMethod = PaidMethod.entries[it] },
                )
            }
        }
    }
}

internal fun whatsappChargeUrl(message: String): String =
    "https://wa.me/?text=${message.percentEncode()}"

private fun String.percentEncode(): String = buildString {
    val hex = "0123456789ABCDEF"
    encodeToByteArray().forEach { byte ->
        val value = byte.toInt() and 0xFF
        if (value.isUnreservedUriByte()) {
            append(value.toChar())
        } else {
            append('%')
            append(hex[value ushr 4])
            append(hex[value and 0x0F])
        }
    }
}

private fun Int.isUnreservedUriByte(): Boolean =
    this in 0x30..0x39 || this in 0x41..0x5A || this in 0x61..0x7A || this in 0x2D..0x2E ||
        this == 0x5F || this == 0x7E
