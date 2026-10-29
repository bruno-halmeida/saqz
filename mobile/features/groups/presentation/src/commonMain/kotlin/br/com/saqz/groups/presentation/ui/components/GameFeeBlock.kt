package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzSegmented
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.GameFeeChoice
import br.com.saqz.groups.presentation.GameFeeProblem
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.game_fee_block_label
import br.com.saqz.groups.resources.game_fee_charge_hint
import br.com.saqz.groups.resources.game_fee_choice_charge
import br.com.saqz.groups.resources.game_fee_choice_none
import br.com.saqz.groups.resources.game_fee_error_invalid
import br.com.saqz.groups.resources.game_fee_error_missing
import br.com.saqz.groups.resources.game_fee_none_hint
import br.com.saqz.groups.resources.game_fee_suggestion
import br.com.saqz.groups.resources.group_setup_currency_prefix
import br.com.saqz.groups.resources.group_setup_game_fee_placeholder
import org.jetbrains.compose.resources.stringResource

object GameFeeBlockTags {
    const val Block = "game-fee-block"
    const val Choice = "game-fee-choice"
    const val Charge = "game-fee-choice-charge"
    const val NoFee = "game-fee-choice-none"
    const val Amount = "game-fee-amount"
    const val Error = "game-fee-error"
}

/**
 * DEB-146 · JOGO-008 (prancha Valor-A): o bloco "Valor por jogo" do editor de jogo, de Criar grupo e de Editar grupo.
 * Segmentado de largura igual **Cobrar diária · Sem diária**, nada marcado de início; Cobrar diária abre o campo do
 * valor com a sugestão (mensalidade ÷ 4) no apoio; sem escolha, o erro fica embaixo do segmentado.
 */
@Composable
internal fun GameFeeBlock(
    choice: GameFeeChoice?,
    feeText: String,
    onChoice: (GameFeeChoice) -> Unit,
    onFeeTextChange: (String) -> Unit,
    problem: GameFeeProblem?,
    modifier: Modifier = Modifier,
    monthlyFeeCents: Long? = null,
) {
    val colors = SaqzTheme.colors
    val label = stringResource(Res.string.game_fee_block_label)
    Column(
        modifier = modifier.fillMaxWidth().testTag(GameFeeBlockTags.Block),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
        ) {
            SaqzIcon(SaqzIcons.Banknote, tint = colors.textSecondary)
            Text(text = label, style = SaqzTheme.typography.label, color = colors.textPrimary)
        }
        SaqzSegmented(
            options = listOf(
                stringResource(Res.string.game_fee_choice_charge),
                stringResource(Res.string.game_fee_choice_none),
            ),
            selected = when (choice) {
                GameFeeChoice.Charge -> 0
                GameFeeChoice.NoFee -> 1
                null -> NoChoice
            },
            onSelect = { onChoice(if (it == 0) GameFeeChoice.Charge else GameFeeChoice.NoFee) },
            optionTags = listOf(GameFeeBlockTags.Charge, GameFeeBlockTags.NoFee),
            modifier = Modifier.testTag(GameFeeBlockTags.Choice),
        )
        when (choice) {
            null -> if (problem == GameFeeProblem.Missing) GameFeeError(stringResource(Res.string.game_fee_error_missing))
            GameFeeChoice.NoFee -> GameFeeSupport(stringResource(Res.string.game_fee_none_hint))
            GameFeeChoice.Charge -> {
                SaqzInput(
                    value = feeText,
                    onValueChange = onFeeTextChange,
                    label = label,
                    showLabel = false,
                    placeholder = stringResource(Res.string.group_setup_game_fee_placeholder),
                    errorText = if (problem == GameFeeProblem.Invalid) {
                        stringResource(Res.string.game_fee_error_invalid)
                    } else null,
                    keyboardType = KeyboardType.Decimal,
                    leadingContent = {
                        Text(
                            text = stringResource(Res.string.group_setup_currency_prefix),
                            style = SaqzTheme.typography.label,
                            color = colors.textSecondary,
                        )
                    },
                    modifier = Modifier.testTag(GameFeeBlockTags.Amount),
                )
                if (problem != GameFeeProblem.Invalid) {
                    GameFeeSupport(
                        monthlyFeeCents?.takeIf { it > 0 }
                            ?.let { stringResource(Res.string.game_fee_suggestion, formatBrlShort(it)) }
                            ?: stringResource(Res.string.game_fee_charge_hint),
                    )
                }
            }
        }
    }
}

@Composable
private fun GameFeeSupport(text: String) {
    Text(text = text, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary, maxLines = 2)
}

@Composable
private fun GameFeeError(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
        modifier = Modifier.testTag(GameFeeBlockTags.Error),
    ) {
        SaqzIcon(SaqzIcons.CircleAlert, tint = SaqzTheme.colors.errorForeground)
        Text(text = text, style = SaqzTheme.typography.support, color = SaqzTheme.colors.errorForeground)
    }
}

/** "R$ 20" para valor inteiro e "R$ 20,50" com centavos, como nas pranchas. */
internal fun formatBrlShort(cents: Long): String = formatBrl(cents).removeSuffix(",00")

private const val NoChoice = -1
