package br.com.saqz.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import br.com.saqz.designsystem.theme.SaqzTheme

/**
 * Iniciais de um nome: primeira letra do primeiro nome + primeira do último.
 * Nome único devolve uma letra; nome vazio devolve string vazia (o avatar fica
 * só com o fundo, sem inventar um "?" que ninguém pediu).
 */
fun saqzInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when (parts.size) {
        0 -> ""
        1 -> parts.first().take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

/**
 * 10k — círculo ice com as iniciais. [photo] substitui as iniciais quando o app
 * tem a foto; o design system não carrega imagem de rede por conta própria.
 */
@Composable
fun SaqzAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    photo: (@Composable () -> Unit)? = null,
) {
    val colors = SaqzTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.surfaceSoft, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            photo()
        } else {
            Text(
                text = saqzInitials(name),
                // A tipografia do avatar acompanha o diâmetro; é a única escala do
                // design system que não sai da tabela, porque 30/40/44 são 3 tamanhos.
                fontSize = (size.value * 0.36f).sp,
                lineHeight = (size.value * 0.36f).sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
            )
        }
    }
}

/**
 * 10k — pilha sobreposta. Acima de [max] avatares, o excedente vira "+N".
 */
@Composable
fun SaqzAvatarStack(
    names: List<String>,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    max: Int = 3,
) {
    val colors = SaqzTheme.colors
    val shown = names.take(max)
    val overflow = names.size - shown.size
    val overlap = size / 4
    Row(horizontalArrangement = Arrangement.spacedBy(-overlap), modifier = modifier) {
        // z decrescente: quem vem antes fica por cima, senão o vizinho da direita
        // cobre justo a metade das iniciais.
        shown.forEachIndexed { index, name ->
            SaqzAvatar(
                name = name,
                size = size,
                modifier = Modifier
                    .zIndex((shown.size - index).toFloat())
                    .border(2.dp, colors.surface, CircleShape),
            )
        }
        if (overflow > 0) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(colors.surfaceSoft, CircleShape)
                    .border(2.dp, colors.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$overflow",
                    fontSize = (size.value * 0.32f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/**
 * 10k — linha de pessoa: avatar, nome, meta e um slot livre à direita (chip, ação).
 */
@Composable
fun SaqzMemberRow(
    name: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    photo: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick == null) Modifier
                else Modifier.clickable(onClickLabel = name, role = Role.Button, onClick = onClick),
            )
            .heightIn(min = metrics.minimumTouchTarget)
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        SaqzAvatar(name = name, photo = photo)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name,
                style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta != null) {
                Text(
                    text = meta,
                    style = SaqzTheme.typography.support,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

@Preview
@Composable
private fun SaqzAvatarPreview() = SaqzTheme {
    SaqzPreviewGrid {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SaqzAvatar("Lucas Pereira", size = 30.dp)
            SaqzAvatar("Bruna Silva", size = 40.dp)
            SaqzAvatar("Tiago", size = 44.dp)
        }
        SaqzAvatarStack(
            names = listOf("Lucas Pereira", "Bruna Silva", "Tiago Moraes", "A", "B", "C", "D", "E", "F"),
            modifier = Modifier.offset(x = 0.dp),
        )
    }
}

@Preview
@Composable
private fun SaqzMemberRowPreview() = SaqzTheme {
    SaqzPreviewGrid {
        SaqzCard(padded = false) {
            SaqzMemberRow(
                name = "Lucas Pereira",
                meta = "Mensalista · desde 2024",
                onClick = {},
                trailing = { SaqzStatusChip("Admin", tone = SaqzChipTone.Brand) },
            )
            SaqzDivider()
            SaqzMemberRow(
                name = "Bruna Silva",
                meta = "Avulsa",
                trailing = { SaqzIcon(SaqzIcons.ChevronRight, tint = SaqzTheme.colors.textSecondary) },
            )
        }
    }
}
