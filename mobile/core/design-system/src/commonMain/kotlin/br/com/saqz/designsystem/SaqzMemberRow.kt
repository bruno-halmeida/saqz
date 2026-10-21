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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.designsystem.theme.SaqzTheme

/**
 * Iniciais de um nome, a única regra do app: primeira letra do primeiro nome + primeira do
 * último. Palavras sem letra ("13/10", "-", "2") não contam e símbolos no começo da palavra
 * são ignorados, então "QA Vôlei B 13/10" vira "QB". Nome único devolve uma letra; nome sem
 * letra devolve string vazia (o avatar fica só com o fundo, sem inventar um "?").
 */
fun saqzInitials(name: String): String {
    val letters = name.trim().split(Regex("\\s+")).mapNotNull { word -> word.firstOrNull { it.isLetter() } }
    return when (letters.size) {
        0 -> ""
        1 -> letters.first().toString().uppercase()
        else -> (letters.first().toString() + letters.last()).uppercase()
    }
}

// A tipografia do avatar acompanha o diâmetro; é a única escala do design system que
// não sai da tabela, porque 30/40/44 são 3 tamanhos. O "+N" da pilha usa a mesma
// proporção das iniciais vizinhas — no 10k os quatro círculos são `font:700 10.5px`.
private const val AvatarTextRatio = 0.36f

/**
 * As iniciais moram num círculo de tamanho fixo: com "Texto Maior" o `sp` cresceria e as letras
 * vazariam para o vizinho. O corpo sai do diâmetro em dp, convertido sem o fator de fonte.
 */
@Composable
private fun avatarTextSize(size: Dp): TextUnit = with(LocalDensity.current) { (size * AvatarTextRatio).toSp() }

// Sobreposição curta e fixa (não uma fração do diâmetro): o círculo seguinte só encosta na borda do
// anterior, sem passar por cima das iniciais (com 8dp o "BA"/"CU" de 30dp perdia a ponta da letra).
private val AvatarStackOverlap = 4.dp
private val AvatarStackOverlapLargeText = 0.dp

/**
 * 10k — círculo ice com as iniciais e o anel de 1px (`box-shadow:inset 0 0 0 1px`
 * do export, que encosta por dentro e não muda o diâmetro). [photo] substitui as
 * iniciais quando o app tem a foto; o design system não carrega imagem de rede por
 * conta própria.
 *
 * [background] e [initialsColor] existem porque o export pinta o mesmo círculo de
 * dois jeitos: avulso é ice com iniciais navy, dentro do `SaqzMemberRow` é ice com
 * iniciais azuis (`.saqz-memberrow__avatar{color:var(--saqz-blue)}`) e na pilha é
 * branco com iniciais navy — o fundo branco é o que separa um avatar do vizinho.
 */
@Composable
fun SaqzAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    background: Color = SaqzTheme.colors.surfaceSoft,
    initialsColor: Color = SaqzTheme.colors.textPrimary,
    ring: Color = SaqzTheme.colors.border,
    photo: (@Composable () -> Unit)? = null,
) {
    val colors = SaqzTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background, CircleShape)
            .border(1.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            photo()
        } else {
            Text(
                text = saqzInitials(name),
                fontSize = avatarTextSize(size),
                lineHeight = avatarTextSize(size),
                fontWeight = FontWeight.SemiBold,
                color = initialsColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * 10k — pilha sobreposta. Acima de [max] avatares, o excedente vira "+N" em azul
 * sólido: é ele que diz "tem mais gente aqui", então é o círculo mais forte da
 * pilha, não o mais apagado.
 *
 * Sobre o hero azul (VUL-217) o "+N" em primary some: a Home passa ring = primary,
 * overflowContainer = accent e overflowContent = textPrimary.
 */
@Composable
fun SaqzAvatarStack(
    names: List<String>,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
    max: Int = 3,
    ring: Color = SaqzTheme.colors.border,
    overflowContainer: Color = SaqzTheme.colors.primary,
    overflowContent: Color = SaqzTheme.colors.onPrimary,
) {
    val colors = SaqzTheme.colors
    val shown = names.take(max)
    val overflow = names.size - shown.size
    // Com "Texto Maior" o círculo não cresce e as iniciais largas ("CB") ficavam sob o vizinho:
    // a sobreposição diminui para a letra caber inteira.
    val overlap = if (LocalDensity.current.fontScale > 1.2f) AvatarStackOverlapLargeText else AvatarStackOverlap
    Row(horizontalArrangement = Arrangement.spacedBy(-overlap), modifier = modifier) {
        // Sem zIndex: a ordem de pintura é a do Row, que é a do DOM no export —
        // cada círculo cobre a borda esquerda do anterior e o "+N" fica inteiro.
        shown.forEach { name ->
            SaqzAvatar(name = name, size = size, background = colors.surface, ring = ring)
        }
        if (overflow > 0) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    // Sem anel: no export só os círculos brancos têm o inset de 1px.
                    .background(overflowContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$overflow",
                    fontSize = avatarTextSize(size),
                    lineHeight = avatarTextSize(size),
                    fontWeight = FontWeight.SemiBold,
                    color = overflowContent,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * 10k — linha de pessoa: avatar, nome, meta e um slot livre à direita (chip, ação).
 *
 * Na lista de borda a borda da tela do jogo (DEB-116) a linha ganha uma coluna à esquerda do avatar
 * ([leading], o número) e a divisória de cima ([divider]) começa na coluna do nome: não cruza número e avatar.
 */
@Composable
fun SaqzMemberRow(
    name: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    photo: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    /** Nome de onde saem as iniciais do avatar; difere de [name] quando este ganha um sufixo ("· você"). */
    avatarName: String = name,
    /** Com fonte grande, [trailing] (um chip de texto) desce para baixo do nome em vez de espremê-lo. */
    stackTrailingOnLargeFont: Boolean = false,
    /** Coluna à esquerda do avatar (o "1", "2º" da lista do jogo). */
    leading: (@Composable () -> Unit)? = null,
    /** Troca o círculo de iniciais inteiro (vaga aberta tracejada); [photo] só troca o miolo. */
    avatar: (@Composable () -> Unit)? = null,
    nameColor: Color = SaqzTheme.colors.textPrimary,
    initialsColor: Color = SaqzTheme.colors.primary,
    /** Linha de cima a partir da coluna do nome. */
    divider: Boolean = false,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val dividerColor = colors.border
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick == null) Modifier
                else Modifier.clickable(onClickLabel = name, role = Role.Button, onClick = onClick),
            )
            .heightIn(min = metrics.minimumTouchTarget)
            .padding(start = if (leading != null) LeadingStartPadding else metrics.horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        leading?.invoke()
        if (avatar != null) avatar() else SaqzAvatar(name = avatarName, initialsColor = initialsColor, photo = photo)
        // Fonte grande: o chip à direita espremia o nome até ele sumir. Ele desce para baixo do
        // texto e o nome quebra por palavra em vez de virar reticências.
        val largeFont = LocalDensity.current.fontScale >= LargeFontScale
        val stackTrailing = largeFont && stackTrailingOnLargeFont
        Row(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (divider) {
                        Modifier.drawBehind { drawRect(dividerColor, size = Size(size.width, 1.dp.toPx())) }
                    } else {
                        Modifier
                    },
                )
                .heightIn(min = RowAvatarSize + metrics.blockGap * 2)
                .padding(end = metrics.horizontalPadding, top = metrics.blockGap, bottom = metrics.blockGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = name,
                    style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = nameColor,
                    maxLines = if (largeFont) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (meta != null) {
                    Text(
                        text = meta,
                        style = SaqzTheme.typography.support,
                        color = colors.textSecondary,
                        maxLines = if (largeFont) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (stackTrailing) trailing?.invoke()
            }
            if (!stackTrailing) trailing?.invoke()
        }
    }
}

/**
 * Círculo tracejado do tamanho do avatar: a vaga aberta (cinza, sem ícone) e as linhas de ação da lista do
 * jogo (azul, com "+" ou o ícone da ação). Decorativo: quem toca é a linha.
 */
@Composable
fun SaqzDashedCircle(
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    size: Dp = RowAvatarSize,
) {
    Box(
        modifier = modifier.size(size).drawBehind {
            val stroke = DashedStroke.toPx()
            drawCircle(
                color = color,
                radius = this.size.minDimension / 2 - stroke / 2,
                style = Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashLength.toPx(), DashLength.toPx())),
                ),
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) SaqzIcon(icon, tint = color, size = DashedIconSize)
    }
}

private val RowAvatarSize = 40.dp
private val LeadingStartPadding = 10.dp
private val DashedStroke = 1.5.dp
private val DashLength = 4.dp
private val DashedIconSize = 20.dp
private const val LargeFontScale = 1.3f

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
            SaqzAvatar("Camila Alves", size = 44.dp) {
                Box(modifier = Modifier.size(44.dp).background(SaqzTheme.colors.accent))
            }
        }
        // Com e sem excedente: o "+N" azul só aparece quando a lista passa de `max`.
        SaqzAvatarStack(
            names = listOf("Lucas Pereira", "Bruna Silva", "Tiago Moraes", "A", "B", "C", "D", "E", "F"),
        )
        SaqzAvatarStack(names = listOf("Lucas Pereira", "Bruna Silva"))
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
