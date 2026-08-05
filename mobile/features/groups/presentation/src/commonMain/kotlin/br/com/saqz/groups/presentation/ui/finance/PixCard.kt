package br.com.saqz.groups.presentation.ui.finance

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.ui.finance.groupcash.PixUi
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_cashbox_pix_copy
import br.com.saqz.groups.resources.group_cashbox_pix_title
import org.jetbrains.compose.resources.stringResource

/**
 * O card de Pix do caixa do grupo (VUL-182), agora com dois donos: o caixa e a seção de
 * cobranças do próprio membro (VUL-203). Os `testTag` chegam por parâmetro porque cada
 * tela responde pelo próprio inventário de tags.
 */
@Composable
internal fun PixCard(
    pix: PixUi,
    onCopy: () -> Unit,
    cardTag: String,
    copyTag: String,
    modifier: Modifier = Modifier,
) = SaqzCard(modifier = modifier.testTag(cardTag)) {
    SaqzSectionHeader(title = stringResource(Res.string.group_cashbox_pix_title))
    Text(text = pix.key, style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary)
    pix.label?.takeIf(String::isNotBlank)?.let {
        Text(text = it, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
    }
    SaqzButton(
        label = stringResource(Res.string.group_cashbox_pix_copy),
        onClick = onCopy,
        variant = SaqzButtonVariant.Secondary,
        fullWidth = true,
        modifier = Modifier.testTag(copyTag),
    )
}
