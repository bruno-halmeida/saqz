package br.com.saqz.groups.presentation.ui.entryrequest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzSegmentedColumn
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.presentation.entryrequest.AcceptAsUi
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.accept_as_avulso
import br.com.saqz.groups.resources.accept_as_avulso_hint
import br.com.saqz.groups.resources.accept_as_confirm
import br.com.saqz.groups.resources.accept_as_fee_required
import br.com.saqz.groups.resources.accept_as_mensalista
import br.com.saqz.groups.resources.accept_as_mensalista_hint
import br.com.saqz.groups.resources.accept_as_title
import org.jetbrains.compose.resources.stringResource

object AcceptAsTags {
    const val Sheet = "accept-as-sheet"
    const val Avulso = "accept-as-avulso"
    const val Mensalista = "accept-as-mensalista"
    const val Confirm = "accept-as-confirm"
    const val FeeRequired = "accept-as-fee-required"
}

private val Options = listOf(AthleteMembershipType.AVULSO, AthleteMembershipType.MENSALISTA)

/** Folha "Aceitar como" (DEB-112): Avulso marcado, Mensalista com o valor e o dia do grupo; fechar não muda nada. */
@Composable
internal fun AcceptAsSheet(
    sheet: AcceptAsUi?,
    busy: Boolean,
    onSelect: (AthleteMembershipType) -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = SaqzTheme.colors
    SaqzBottomSheet(
        open = sheet != null,
        onClose = onClose,
        modifier = Modifier.testTag(AcceptAsTags.Sheet),
        title = stringResource(Res.string.accept_as_title),
        footer = {
            SaqzButton(
                label = stringResource(Res.string.accept_as_confirm),
                onClick = onConfirm,
                fullWidth = true,
                loading = busy,
                enabled = !busy,
                modifier = Modifier.testTag(AcceptAsTags.Confirm),
            )
        },
    ) {
        if (sheet == null) return@SaqzBottomSheet
        SaqzSegmentedColumn(
            count = Options.size,
            selected = Options.indexOf(sheet.selected),
            onSelect = { onSelect(Options[it]) },
            enabled = !busy,
            optionTags = listOf(AcceptAsTags.Avulso, AcceptAsTags.Mensalista),
        ) { index, selected ->
            val text = if (selected) colors.onPrimary else colors.textPrimary
            val support = if (selected) colors.onPrimary else colors.textSecondary
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                Text(
                    stringResource(if (index == 0) Res.string.accept_as_avulso else Res.string.accept_as_mensalista),
                    style = SaqzTheme.typography.body.copy(fontWeight = FontWeight(600)),
                    color = text,
                )
                Text(
                    if (index == 0) {
                        stringResource(Res.string.accept_as_avulso_hint)
                    } else {
                        stringResource(Res.string.accept_as_mensalista_hint, formatBrl(sheet.feeCents), sheet.dueDay)
                    },
                    style = SaqzTheme.typography.support,
                    color = support,
                )
            }
        }
        if (sheet.feeRequired) {
            Text(
                stringResource(Res.string.accept_as_fee_required),
                style = SaqzTheme.typography.support,
                color = colors.errorForeground,
                modifier = Modifier.testTag(AcceptAsTags.FeeRequired),
            )
        }
    }
}
