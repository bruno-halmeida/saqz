package br.com.saqz.groups.presentation.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import br.com.saqz.groups.presentation.ui.finance.CashIconTile
import br.com.saqz.groups.presentation.ui.finance.CashListRow
import br.com.saqz.groups.presentation.ui.finance.CashRowText
import br.com.saqz.groups.presentation.ui.finance.CashTileTone
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_cashbox_pix_title
import br.com.saqz.groups.resources.group_pix_copy
import br.com.saqz.groups.resources.group_pix_copy_a11y
import br.com.saqz.groups.resources.pix_receiver_typed
import org.jetbrains.compose.resources.stringResource

/**
 * "Pix do grupo" (FIN-008, DEB-148): a chave com o tipo ("Chave Pix · E-mail", como no cartão da cobrança) e
 * Copiar, para todo membro. Fica depois de Minhas cobranças, ou depois da Galera para quem nunca teve cobrança;
 * some com cobrança em aberto, com recebimento pelo app e sem chave.
 */
@Composable
internal fun GroupPixBlock(
    state: GroupDetailsState,
    onIntent: (GroupDetailsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pix = state.groupPix?.takeIf { state.showGroupPix } ?: return
    val copyLabel = stringResource(Res.string.group_pix_copy_a11y)
    Column(
        modifier = modifier.fillMaxWidth().testTag(GroupDetailsTags.GroupPix),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzSectionHeader(title = stringResource(Res.string.group_cashbox_pix_title))
        SaqzCard(padded = false) {
            CashListRow(first = true) {
                CashIconTile(SaqzIcons.CreditCard, CashTileTone.Brand)
                CashRowText(
                    title = pix.displayKey,
                    subtitle = pix.type?.let { stringResource(Res.string.pix_receiver_typed, it.label) }
                        ?: pix.label?.takeIf(String::isNotBlank),
                )
                SaqzButton(
                    label = stringResource(Res.string.group_pix_copy),
                    onClick = { onIntent(GroupDetailsIntent.CopyPix) },
                    variant = SaqzButtonVariant.Secondary,
                    size = SaqzButtonSize.Sm,
                    modifier = Modifier
                        .testTag(GroupDetailsTags.GroupPixCopy)
                        .semantics { contentDescription = copyLabel },
                )
            }
        }
    }
}
