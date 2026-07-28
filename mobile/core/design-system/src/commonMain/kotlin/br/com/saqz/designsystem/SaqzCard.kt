package br.com.saqz.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme

enum class SaqzCardTone { Default, Soft }

enum class SaqzChipTone { Neutral, Accent, Brand, Success, Warning, Error }

/**
 * 10i — branco, borda de 1px, raio 12, sem sombra. `tone = Soft` é o bloco de
 * destaque (fundo ice, sem borda). `padded = false` é a variante flush: a lista
 * encosta na borda e as linhas ficam recortadas pelo raio do card.
 *
 * Card dentro de card não existe no design system.
 */
@Composable
fun SaqzCard(
    modifier: Modifier = Modifier,
    tone: SaqzCardTone = SaqzCardTone.Default,
    padded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val shape = RoundedCornerShape(metrics.cardRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (tone == SaqzCardTone.Soft) colors.surfaceSoft else colors.surface, shape)
            .then(
                if (tone == SaqzCardTone.Default) Modifier.border(1.dp, colors.border, shape) else Modifier,
            )
            .then(if (padded) Modifier.padding(metrics.horizontalPadding) else Modifier),
        verticalArrangement = Arrangement.spacedBy(if (padded) metrics.blockGap else 0.dp),
        content = content,
    )
}

/**
 * Divisória de 1px — a única separação entre linhas de um card flush.
 *
 * [vertical] gira o traço para separar colunas de largura igual: os contadores de presença
 * do `SaqzGameSummaryCard` e do detalhe do grupo. **A `Row` que a contém precisa ter
 * altura própria** (`Modifier.height(IntrinsicSize.Min)`); sem isso o `fillMaxHeight`
 * resolve para zero e o traço desaparece sem erro nenhum.
 */
@Composable
fun SaqzDivider(modifier: Modifier = Modifier, vertical: Boolean = false) = Box(
    modifier = modifier
        .then(
            if (vertical) Modifier.width(1.dp).fillMaxHeight()
            else Modifier.fillMaxWidth().height(1.dp),
        )
        .background(SaqzTheme.colors.border),
)

/**
 * 10j — título da seção com ícone opcional à esquerda e uma ação em texto à direita.
 */
@Composable
fun SaqzSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = SaqzTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        icon?.invoke()
        Text(
            text = title,
            style = SaqzTheme.typography.subtitle,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            Text(
                text = action,
                style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
                color = colors.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClickLabel = action, role = Role.Button, onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * 10j — etiqueta de estado. `dot` acende o ponto na cor do primeiro plano
 * (`background: currentColor` no export).
 *
 * Primeiro plano e fundo de cada tom saem da tabela abaixo, um par explícito por tom,
 * transcrito de `.saqz-chip--*` no `_ds_bundle.js`. Não derive um do outro: a versão
 * anterior calculava o fundo como `foreground.copy(alpha = .12f)` e isso apagou a
 * exceção do `warning` — o único tom em que o export escreve o texto numa cor
 * diferente da que tinge o fundo — além de arredondar os alfas de `brand` e `error`.
 */
@Composable
fun SaqzStatusChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: SaqzChipTone = SaqzChipTone.Neutral,
    dot: Boolean = false,
) {
    val colors = SaqzTheme.colors
    val (foreground, container) = when (tone) {
        SaqzChipTone.Neutral -> colors.textSecondary to colors.surfaceSoft
        SaqzChipTone.Accent -> colors.textPrimary to colors.accent.copy(alpha = 0.32f)
        SaqzChipTone.Brand -> colors.primary to colors.primary.copy(alpha = 0.08f)
        SaqzChipTone.Success -> colors.success to colors.success.copy(alpha = 0.12f)
        SaqzChipTone.Warning -> colors.warningForeground to colors.warning.copy(alpha = 0.14f)
        SaqzChipTone.Error -> colors.errorForeground to colors.errorForeground.copy(alpha = 0.10f)
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(container, CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) {
            // `.saqz-chip__dot{background:currentColor}` — o ponto é sempre o primeiro
            // plano, inclusive no accent, que antes acendia em primary sem o export pedir.
            Box(
                Modifier
                    .size(6.dp)
                    .background(foreground, CircleShape),
            )
        }
        Text(
            text = text,
            style = SaqzTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
            color = foreground,
        )
    }
}

@Preview
@Composable
private fun SaqzCardPreview() = SaqzTheme {
    SaqzPreviewGrid {
        SaqzCard {
            SaqzSectionHeader(title = "Confirmados", action = "Ver todos", onAction = {})
            Text("Branco, borda de 1px, raio 12, sem sombra.", style = SaqzTheme.typography.support)
        }
        SaqzCard(tone = SaqzCardTone.Soft) {
            Text("Próximo jogo", style = SaqzTheme.typography.eyebrow, color = SaqzTheme.colors.textSecondary)
            Text("Ter, 28/07 · 19h30", style = SaqzTheme.typography.title)
        }
        SaqzCard(padded = false) {
            Text("Linha 1", modifier = Modifier.padding(16.dp), style = SaqzTheme.typography.body)
            SaqzDivider()
            Text("Linha 2", modifier = Modifier.padding(16.dp), style = SaqzTheme.typography.body)
        }
    }
}

@Preview
@Composable
private fun SaqzStatusChipPreview() = SaqzTheme {
    // Os seis tons nas duas formas: sem ponto e com ponto. Tom que não está aqui não
    // está sendo conferido — foi assim que o warning ficou em 1,9:1 sem ninguém ver.
    SaqzPreviewGrid {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaqzStatusChip("Reserva", tone = SaqzChipTone.Neutral)
            SaqzStatusChip("Admin", tone = SaqzChipTone.Brand)
            SaqzStatusChip("Mensalista", tone = SaqzChipTone.Accent)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaqzStatusChip("Vou", tone = SaqzChipTone.Success)
            SaqzStatusChip("Talvez", tone = SaqzChipTone.Warning)
            SaqzStatusChip("Não vou", tone = SaqzChipTone.Error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaqzStatusChip("Reserva", tone = SaqzChipTone.Neutral, dot = true)
            SaqzStatusChip("Admin", tone = SaqzChipTone.Brand, dot = true)
            SaqzStatusChip("Mensalista", tone = SaqzChipTone.Accent, dot = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaqzStatusChip("Vou", tone = SaqzChipTone.Success, dot = true)
            SaqzStatusChip("Talvez", tone = SaqzChipTone.Warning, dot = true)
            SaqzStatusChip("Não vou", tone = SaqzChipTone.Error, dot = true)
        }
    }
}
