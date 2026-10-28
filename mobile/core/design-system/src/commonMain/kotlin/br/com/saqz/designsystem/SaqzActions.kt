package br.com.saqz.designsystem

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme

/**
 * Um bloco inteiro como um botão (ex. o painel de presença do cartão do jogo, que abre o jogo): toque com o rótulo de
 * ação [onClickLabel], papel de botão e os textos de dentro fundidos num nó só para o leitor de tela.
 */
fun Modifier.saqzTapTarget(onClickLabel: String, onClick: () -> Unit): Modifier =
    clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) {}

/**
 * Atalho em cartão: ícone em cima, rótulo curto embaixo. O toque anuncia
 * [contentDescription] (mais longo que o rótulo) como nome e como ação.
 */
@Composable
fun SaqzShortcutCard(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(metrics.cardRadius))
            .background(colors.surface, RoundedCornerShape(metrics.cardRadius))
            .clickable(onClickLabel = contentDescription, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(metrics.blockGap),
        verticalArrangement = Arrangement.spacedBy(metrics.subGrid),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SaqzIcon(icon = icon, tint = colors.primary, size = metrics.grid * 3)
        Text(
            text = label,
            style = SaqzTheme.typography.compactMeta.copy(fontWeight = FontWeight(600)),
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

/** Botão tracejado de adicionar ("+ Adicionar dia e horário"): largura toda, azul sobre fundo suave. */
@Composable
fun SaqzDashedAddButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    val borderColor = SaqzTheme.colors.primary
    val dash = metrics.grid
    val shape = RoundedCornerShape(metrics.cardRadius)
    val dismissKeyboard = rememberSaqzDismissKeyboard()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = metrics.buttonHeight)
            .clip(shape)
            .background(SaqzTheme.colors.surfaceSoft, shape)
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(metrics.cardRadius.toPx()),
                    style = Stroke(
                        width = metrics.subGrid.toPx() / DASHED_BORDER_DIVISOR,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), dash.toPx())),
                    ),
                )
            }
            .clickable(
                onClickLabel = label,
                role = Role.Button,
                onClick = {
                    dismissKeyboard()
                    onClick()
                },
            )
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        horizontalArrangement = Arrangement.spacedBy(metrics.grid, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SaqzIcon(SaqzIcons.Plus, tint = SaqzTheme.colors.primary)
        Text(label, style = SaqzTheme.typography.label, color = SaqzTheme.colors.primary, textAlign = TextAlign.Center)
    }
}

private const val DASHED_BORDER_DIVISOR = 4

/**
 * Linha de atalho com ícone num quadrado tracejado, título azul e uma linha de apoio (primeiro acesso da lista de grupos,
 * "Criar um grupo" · "Para quem organiza os jogos"). A linha inteira é o toque.
 */
@Composable
fun SaqzDashedRow(
    icon: ImageVector,
    label: String,
    supporting: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val shape = RoundedCornerShape(metrics.cardRadius)
    val iconShape = RoundedCornerShape(DASHED_ROW_ICON_RADIUS)
    val borderColor = colors.border
    val dash = metrics.subGrid
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface, shape)
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(metrics.cardRadius.toPx()),
                    style = Stroke(width = 1f),
                )
            }
            .saqzTapTarget(onClickLabel = label, onClick = onClick)
            .heightIn(min = DASHED_ROW_MIN_HEIGHT)
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(DASHED_ROW_ICON_BOX)
                .drawBehind {
                    drawRoundRect(
                        color = colors.primary,
                        cornerRadius = CornerRadius(DASHED_ROW_ICON_RADIUS.toPx()),
                        style = Stroke(
                            width = metrics.subGrid.toPx() * 3 / 8,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), dash.toPx())),
                        ),
                    )
                }
                .clip(iconShape),
            contentAlignment = Alignment.Center,
        ) {
            SaqzIcon(icon, tint = colors.primary, size = DASHED_ROW_GLYPH)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.subGrid / 2)) {
            Text(label, style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold), color = colors.primary)
            Text(supporting, style = SaqzTheme.typography.support, color = colors.textSecondary)
        }
    }
}

// Medidas do `dashed_row` do desenho da lista de grupos (gerar.py): quadrado de 52 com raio 14 e glifo de 22.
private val DASHED_ROW_ICON_BOX = 52.dp
private val DASHED_ROW_ICON_RADIUS = 14.dp
private val DASHED_ROW_GLYPH = 22.dp
private val DASHED_ROW_MIN_HEIGHT = 76.dp

/**
 * Pílula de um valor já escolhido (ex.: "Terça · 19h30"), com ícone à esquerda. Tocar no corpo chama [onClick]
 * (anunciado como [clickDescription]); o X à direita chama [onRemove] sem acionar o corpo. Os dois alvos têm o piso de toque.
 */
@Composable
fun SaqzEditablePill(
    icon: ImageVector,
    label: String,
    clickDescription: String,
    onClick: () -> Unit,
    removeDescription: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    val dismissKeyboard = rememberSaqzDismissKeyboard()
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(SaqzTheme.colors.surfaceSoft, CircleShape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = metrics.minimumTouchTarget)
                .clickable(
                    onClickLabel = clickDescription,
                    role = Role.Button,
                    onClick = {
                        dismissKeyboard()
                        onClick()
                    },
                )
                .semantics { contentDescription = clickDescription }
                .padding(start = metrics.blockGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.grid),
        ) {
            SaqzIcon(icon, tint = SaqzTheme.colors.primary)
            Text(
                text = label,
                style = SaqzTheme.typography.label,
                color = SaqzTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        SaqzIconButton(
            onClick = onRemove,
            contentDescription = removeDescription,
            modifier = Modifier.padding(end = metrics.subGrid),
            size = metrics.sectionGap,
        ) {
            SaqzIcon(SaqzIcons.Close, tint = SaqzTheme.colors.textSecondary, size = metrics.sectionGap)
        }
    }
}

/**
 * Ação de texto em azul, no meio de uma frase ou ao lado de uma linha. O rótulo que quebra sai
 * centralizado. [touchTarget] reserva o alvo de toque mínimo (linha de lista); sem ele o link
 * ocupa só o texto, como o de fim de formulário.
 */
@Composable
fun SaqzTextLink(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = SaqzTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
    touchTarget: Boolean = false,
) {
    val text = @Composable { modifier: Modifier ->
        Text(
            text = label,
            style = style,
            color = SaqzTheme.colors.primary,
            textAlign = TextAlign.Center,
            modifier = modifier,
        )
    }
    if (touchTarget) {
        Box(
            modifier = modifier
                .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
                .heightIn(min = SaqzTheme.metrics.minimumTouchTarget)
                .padding(horizontal = SaqzTheme.metrics.subGrid),
            contentAlignment = Alignment.Center,
        ) { text(Modifier) }
    } else {
        text(modifier.clickable(onClickLabel = label, role = Role.Button, onClick = onClick))
    }
}

@Preview
@Composable
private fun SaqzActionsPreview() = SaqzTheme {
    SaqzPreviewGrid {
        Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            SaqzShortcutCard(SaqzIcons.Plus, "Marcar jogo", "Marcar um jogo", {}, Modifier.weight(1f))
            SaqzShortcutCard(SaqzIcons.Mail, "Convidar", "Convidar atletas", {}, Modifier.weight(1f))
        }
        SaqzDashedAddButton("Adicionar dia e horário", {})
        SaqzTextLink("Criar conta ›", {})
        SaqzTextLink("Trocar a quadra do grupo", {}, touchTarget = true)
    }
}
