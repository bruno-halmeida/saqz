package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import br.com.saqz.core.common.formatting.formatBrlPlain
import br.com.saqz.core.common.formatting.parseBrlToCents
import br.com.saqz.core.common.formatting.sanitizeBrlInput
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzStepper
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.setup.GroupSetupDefaults
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_monthly_fee_amount
import br.com.saqz.groups.resources.group_monthly_fee_due_day
import br.com.saqz.groups.resources.group_monthly_fee_due_day_after
import br.com.saqz.groups.resources.group_monthly_fee_due_day_before
import br.com.saqz.groups.resources.group_monthly_fee_error
import br.com.saqz.groups.resources.group_monthly_fee_support
import br.com.saqz.groups.resources.group_monthly_fee_title
import br.com.saqz.groups.resources.group_setup_currency_prefix
import br.com.saqz.groups.resources.group_setup_game_fee_placeholder
import org.jetbrains.compose.resources.stringResource

/**
 * Cartão "Mensalidade" de Editar grupo (CONV-010): valor por mês em reais e dia do vencimento de 1 a 28, por contador.
 * É a mensalidade padrão de quem entra pelo link de mensalistas; vazio, o convite fica bloqueado.
 */
@Composable
internal fun GroupMonthlyFeeSection(
    cents: Long?,
    dueDay: Int?,
    hasError: Boolean,
    onFeeChange: (Long?) -> Unit,
    onDueDayChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    receivables: GroupSetupReceivables? = null,
) {
    var text by remember { mutableStateOf(cents?.let(::formatBrlPlain).orEmpty()) }
    LaunchedEffect(cents) {
        if (parseBrlToCents(text) != cents) text = cents?.let(::formatBrlPlain).orEmpty()
    }
    SaqzCard(modifier = modifier.fillMaxWidth().testTag(GroupSetupTags.MonthlyFee)) {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            Text(
                stringResource(Res.string.group_monthly_fee_title),
                style = SaqzTheme.typography.subtitle,
                color = SaqzTheme.colors.textPrimary,
            )
            Text(
                stringResource(Res.string.group_monthly_fee_support),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
            )
        }
        val label = stringResource(Res.string.group_monthly_fee_amount)
        GroupFieldLabel(label, icon = SaqzIcons.Banknote)
        SaqzInput(
            value = text,
            onValueChange = { raw ->
                text = sanitizeBrlInput(raw)
                onFeeChange(parseBrlToCents(text))
            },
            label = label,
            showLabel = false,
            placeholder = stringResource(Res.string.group_setup_game_fee_placeholder),
            errorText = if (hasError) stringResource(Res.string.group_monthly_fee_error) else null,
            keyboardType = KeyboardType.Decimal,
            leadingContent = {
                Text(
                    text = stringResource(Res.string.group_setup_currency_prefix),
                    style = SaqzTheme.typography.label,
                    color = SaqzTheme.colors.textSecondary,
                )
            },
            modifier = Modifier.testTag(GroupSetupTags.MonthlyFeeAmount),
        )
        receivables?.feePreview?.invoke(cents)
        SaqzStepper(
            value = dueDay ?: GroupSetupDefaults.DefaultMonthlyDueDay,
            onValueChange = onDueDayChange,
            min = 1,
            max = GroupSetupDefaults.MaxMonthlyDueDay,
            label = stringResource(Res.string.group_monthly_fee_due_day),
            icon = SaqzIcons.Calendar,
            decreaseDescription = stringResource(Res.string.group_monthly_fee_due_day_before),
            increaseDescription = stringResource(Res.string.group_monthly_fee_due_day_after),
            modifier = Modifier.testTag(GroupSetupTags.MonthlyDueDay),
        )
        receivables?.values?.invoke()
    }
}
