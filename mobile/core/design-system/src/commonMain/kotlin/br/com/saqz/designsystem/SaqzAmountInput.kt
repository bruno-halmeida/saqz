package br.com.saqz.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme

/**
 * Valor primeiro e grande (Entrada e Saída do caixa, DEB-148): "R$" cinza e o número em `display`, sobre uma linha
 * azul de 2 (vermelha com erro). O texto é o que a pessoa digita ("120,00"); quem chama formata e valida.
 *
 * SPEC_DEVIATION: componente fora do export oficial.
 * Reason: telas de Entrada e Saída aprovadas pelo dono no canvas do financeiro em 28 e 29/10/2026; controle de
 * digitação nasce no design system.
 */
@Composable
fun SaqzAmountInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "0,00",
    errorText: String? = null,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val line = if (errorText != null) colors.errorForeground else colors.primary
    val style = SaqzTheme.typography.display
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(metrics.grid)) {
        Text(
            text = label,
            style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
            color = colors.textPrimary,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = metrics.grid),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(metrics.grid),
        ) {
            Text(
                text = "R$",
                style = SaqzTheme.typography.title,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = metrics.subGrid),
            )
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text(placeholder, style = style, color = colors.textPlaceholder)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = style.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(AmountLine).background(line))
        if (errorText != null) {
            Text(text = errorText, style = SaqzTheme.typography.support, color = colors.errorForeground)
        }
    }
}

private val AmountLine = 2.dp

@Preview
@Composable
private fun SaqzAmountInputPreview() = SaqzTheme {
    SaqzPreviewGrid {
        SaqzAmountInput(value = "120,00", onValueChange = {}, label = "Valor")
        SaqzAmountInput(value = "", onValueChange = {}, label = "Valor", errorText = "Digite o valor.")
    }
}
