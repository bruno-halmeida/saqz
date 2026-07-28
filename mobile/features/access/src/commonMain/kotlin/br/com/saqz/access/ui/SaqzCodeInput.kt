package br.com.saqz.access.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.designsystem.theme.SaqzTheme

/** Dígitos do código de verificação do fluxo 1. O export só desenha quatro caixas. */
const val SAQZ_CODE_LENGTH = 4

// Bloco `fluxo1.caixaDeCodigo` do ui-contract.json. Raio e gap não estão aqui porque já
// são token vivo (`inputRadius` 10 e `blockGap` 12), e o SaqzFluxo1ContractTest amarra
// os dois lados.
private val BoxWidth = 56.dp
private val BoxHeight = 60.dp
private val DigitSize = 24.sp
private val GlowWidth = 3.dp
private val CursorWidth = 2.dp
private val CursorHeight = 26.dp
private val CursorRadius = 2.dp

/**
 * A fileira de quatro caixas do código de verificação (telas 1e, 1f e 1k do export).
 *
 * Por dentro é **um** campo de texto só, e não quatro. O leitor de tela então anuncia um
 * campo com um rótulo, que é o que o ticket pede; e de quebra o resto do comportamento sai
 * de graça, porque é o comportamento normal de um campo: colar um código de quatro dígitos
 * chega como uma edição só (não como quatro digitações), o backspace numa caixa vazia apaga
 * o dígito anterior, e o avanço automático é só o cursor andando. Quatro campos de verdade
 * custariam gerência de foco em cada tecla — e ainda erram a colagem.
 *
 * O campo é invisível: quem desenha é a fileira. O cursor também é desenhado à mão (a barra
 * de 2×26 do export) porque o texto real não é pintado.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SaqzCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    enabled: Boolean = true,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val shape = RoundedCornerShape(metrics.inputRadius)
    var focused by remember { mutableStateOf(false) }
    val digits = value.filter { it.isDigit() }.take(SAQZ_CODE_LENGTH)
    val wrong = errorText != null

    Column(
        // Sem `mergeDescendants` aqui de propósito: o próprio campo de texto já é uma
        // fronteira de merge, então um merge por fora não engoliria a fileira — só criaria
        // um grupo a mais para o leitor de tela anunciar antes do campo.
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(metrics.grid),
    ) {
        BasicTextField(
            // A seleção volta ao fim a cada composição: o cursor desenhado só sabe apontar
            // para a próxima caixa vazia, então editar no meio do código não faria sentido.
            value = TextFieldValue(digits, TextRange(digits.length)),
            onValueChange = { edit ->
                // Não-dígito é descartado, não vira caixa vazia — vale para a tecla solta e
                // para a colagem ("12-34" entra como 1234).
                onValueChange(edit.text.filter { it.isDigit() }.take(SAQZ_CODE_LENGTH))
            },
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier
                .onFocusChanged { focused = it.isFocused }
                .semantics {
                    contentDescription = label
                    // Autopreenchimento do código que chega por SMS/e-mail, onde a
                    // plataforma oferecer.
                    contentType = ContentType.SmsOtpCode
                    // Erro como mensagem associada, e não só como a borda vermelha.
                    if (errorText != null) error(errorText)
                },
            decorationBox = {
                // `innerTextField` de propósito não é chamado: o texto real é invisível e
                // quem pinta os dígitos é a fileira abaixo.
                Row(horizontalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
                    repeat(SAQZ_CODE_LENGTH) { index ->
                        SaqzCodeBox(
                            digit = digits.getOrNull(index),
                            // Só uma caixa é a "da vez": a primeira vazia. Com o código
                            // cheio o foco fica na última, que é onde o backspace apaga.
                            active = focused && index == digits.length.coerceAtMost(SAQZ_CODE_LENGTH - 1),
                            wrong = wrong,
                            enabled = enabled,
                            shape = shape,
                            radius = metrics.inputRadius,
                        )
                    }
                }
            },
        )
        if (errorText != null) {
            Text(
                text = errorText,
                style = SaqzTheme.typography.label,
                color = colors.errorForeground,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SaqzCodeBox(
    digit: Char?,
    active: Boolean,
    wrong: Boolean,
    enabled: Boolean,
    shape: Shape,
    radius: Dp,
) {
    val colors = SaqzTheme.colors
    // Mesma regra de acento do SaqzInput — travado não anuncia foco nem erro, e erro ganha
    // do foco —, escrita de novo porque as funções de lá (`inputAccent`, `inputContent`) são
    // `internal` do design system e não atravessam a fronteira de módulo. O `!enabled` vem
    // primeiro, e não como guarda em cada consumidor, para o halo vermelho não sobreviver no
    // travado + erro. O export dá 11% ao halo azul e 10% ao vermelho: são dois números.
    val accent = when {
        !enabled -> null
        wrong -> colors.errorForeground
        active -> colors.primary
        else -> null
    }
    val line = accent ?: colors.border
    val glow = accent?.copy(alpha = if (wrong) 0.10f else 0.11f) ?: Color.Transparent

    Box(
        modifier = Modifier
            .size(width = BoxWidth, height = BoxHeight)
            // O halo é `box-shadow` no export: mora fora da caixa e não empurra o layout,
            // então é pintado por fora dos limites em vez de virar borda.
            .drawBehind {
                if (glow != Color.Transparent) {
                    val spread = GlowWidth.toPx()
                    drawRoundRect(
                        color = glow,
                        topLeft = Offset(-spread, -spread),
                        size = Size(size.width + spread * 2, size.height + spread * 2),
                        cornerRadius = CornerRadius(radius.toPx() + spread),
                    )
                }
            }
            .background(colors.surface, shape)
            .border(1.dp, line, shape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            digit != null -> Text(
                text = digit.toString(),
                // 24/700 não é estilo da escala (o mais perto é `title`, 22/700); o tamanho
                // vem por `copy` para não abrir token novo por causa de uma tela.
                style = SaqzTheme.typography.title.copy(
                    fontSize = DigitSize,
                    lineHeight = DigitSize,
                    fontWeight = FontWeight(700),
                ),
                color = when {
                    !enabled -> colors.disabledForeground
                    wrong -> colors.errorForeground
                    else -> colors.textPrimary
                },
            )
            // ponytail: cursor parado, sem piscar — é o que o mockup mostra, e animação
            // infinita aqui só serviria para tirar o determinismo dos screenshots.
            active -> Box(
                modifier = Modifier
                    .size(width = CursorWidth, height = CursorHeight)
                    .background(colors.primary, RoundedCornerShape(CursorRadius)),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun SaqzCodeInputPreview() = SaqzTheme {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SaqzCodeInput("", {}, label = "Código de verificação")
        SaqzCodeInput("13", {}, label = "Código de verificação")
        SaqzCodeInput("1359", {}, label = "Código de verificação")
        SaqzCodeInput(
            "1359", {},
            label = "Código de verificação",
            errorText = "Código incorreto. Restam 2 tentativas.",
        )
        SaqzCodeInput("1359", {}, label = "Código de verificação", enabled = false)
    }
}
