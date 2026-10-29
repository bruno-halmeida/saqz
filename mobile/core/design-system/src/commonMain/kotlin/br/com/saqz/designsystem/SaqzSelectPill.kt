package br.com.saqz.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme

/** Tom da pílula: azul da marca (Cobrar), verde (Já pagou) ou cinza (Não cobrar). */
enum class SaqzSelectPillTone { Brand, Success, Neutral }

/**
 * O estado de uma linha num botão que abre o menu (revisão de quem jogou sem cobrança, FIN-016): rótulo em semibold
 * com a seta para baixo, fundo da cor a 8–12%. O menu é de quem chama.
 *
 * SPEC_DEVIATION: componente fora do export oficial.
 * Reason: revisão B aprovada pelo dono no canvas do financeiro em 28/10/2026; controle de toque nasce no design system.
 */
@Composable
fun SaqzSelectPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: SaqzSelectPillTone = SaqzSelectPillTone.Brand,
    clickDescription: String? = null,
    enabled: Boolean = true,
) {
    val colors = SaqzTheme.colors
    val (fill, content) = when (tone) {
        SaqzSelectPillTone.Brand -> colors.primary.copy(alpha = BRAND_ALPHA) to colors.primary
        SaqzSelectPillTone.Success -> colors.success.copy(alpha = SUCCESS_ALPHA) to colors.successForeground
        SaqzSelectPillTone.Neutral -> colors.textSecondary.copy(alpha = NEUTRAL_ALPHA) to colors.textSecondary
    }
    Row(
        modifier = modifier
            .heightIn(min = PillHeight)
            .clip(CircleShape)
            .background(fill, CircleShape)
            .clickable(enabled = enabled, role = Role.DropdownList, onClick = onClick)
            .then(clickDescription?.let { Modifier.semantics { contentDescription = it } } ?: Modifier)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
            color = content,
            maxLines = 1,
        )
        SaqzIcon(SaqzIcons.ChevronDown, tint = content, size = 16.dp)
    }
}

private val PillHeight = 36.dp
private const val BRAND_ALPHA = 0.08f
private const val SUCCESS_ALPHA = 0.12f
private const val NEUTRAL_ALPHA = 0.10f

@Preview
@Composable
private fun SaqzSelectPillPreview() = SaqzTheme {
    SaqzPreviewGrid {
        SaqzSelectPill("Cobrar", onClick = {})
        SaqzSelectPill("Já pagou", onClick = {}, tone = SaqzSelectPillTone.Success)
        SaqzSelectPill("Não cobrar", onClick = {}, tone = SaqzSelectPillTone.Neutral)
    }
}
