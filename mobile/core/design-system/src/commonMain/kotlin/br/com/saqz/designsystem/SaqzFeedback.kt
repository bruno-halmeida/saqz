package br.com.saqz.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.offline_queued
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.designsystem.theme.saqzShadow
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/**
 * 10m — nada aqui é erro: é a tela dizendo que ainda não há conteúdo, com uma
 * saída. [action] é o rótulo do botão; sem [onAction] o estado é só informativo.
 *
 * O [icon] é slot, mas o badge circular em volta é do componente: `.saqz-empty__icon`
 * no `_ds_bundle.js` é um círculo de 64px em ice, e nenhuma tela deveria redescobrir isso.
 */
@Composable
fun SaqzEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Column(
        modifier = modifier.fillMaxWidth().padding(metrics.sectionGap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        if (icon != null) {
            // `icon` é ImageVector, não slot: o export pinta o glifo do vazio em primary
            // sempre, e com slot o componente entregava o badge mas a cor ficava com quem
            // chama — círculo consistente, desenho dentro variando por tela. Uma tentativa
            // anterior via LocalContentColor não resolvia: SaqzIcon recebe `tint` explícito
            // e nunca lê o local.
            Box(
                modifier = Modifier.size(EMPTY_STATE_BADGE).background(colors.surfaceSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                SaqzIcon(icon = icon, tint = colors.primary, size = EMPTY_STATE_GLYPH)
            }
        }
        Text(
            text = title,
            style = SaqzTheme.typography.subtitle,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        if (description != null) {
            Text(
                text = description,
                style = SaqzTheme.typography.support,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null && onAction != null) {
            SaqzButton(label = action, onClick = onAction)
        }
    }
}

/**
 * 10m — navy, uma ação no máximo, some sozinho depois de [SaqzMotionPolicy.toastDwellMillis]
 * com um deslize de 12dp. Quem chama recebe [onDismiss] e desliga [visible].
 */
@Composable
fun SaqzToast(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = SaqzTheme.colors
    val motion = SaqzTheme.motion
    val slide = with(LocalDensity.current) { 12.dp.roundToPx() }

    // rememberUpdatedState: o efeito não reinicia a cada recomposição, então precisa
    // ler o callback mais recente em vez do que existia quando o timer começou.
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(visible) {
        if (visible) {
            delay(motion.toastDwellMillis.toLong())
            dismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(motion.sheetDurationMillis, easing = motion.emphasized)) +
            slideInVertically(tween(motion.sheetDurationMillis, easing = motion.emphasized)) { slide },
        exit = fadeOut(tween(motion.sheetDurationMillis, easing = motion.emphasized)) +
            slideOutVertically(tween(motion.sheetDurationMillis, easing = motion.emphasized)) { slide },
    ) {
        // `--shadow-toast`, o par do sheet: 12dp para BAIXO. É o que separa a barra navy
        // do conteúdo por cima do qual ela aparece.
        val shape = RoundedCornerShape(SaqzTheme.metrics.cardRadius)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .saqzShadow(SaqzTheme.shadows.toast, shape)
                .background(colors.textPrimary, shape)
                .padding(horizontal = SaqzTheme.metrics.horizontalPadding, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
        ) {
            content()
        }
    }
}

// Texto do toast no tom certo — evita cada tela redescobrir que o fundo é navy.
@Composable
fun SaqzToastText(text: String, modifier: Modifier = Modifier) = Text(
    text = text,
    style = SaqzTheme.typography.support,
    color = SaqzTheme.colors.surface,
    modifier = modifier,
)

/**
 * 10o — a faixa de fila offline, na mesma linguagem do toast: navy, texto branco,
 * raio de card. O spinner à esquerda é o ponto: a resposta está *na fila*, é trabalho
 * pendente, não um aviso parado. O texto padrão é o do design system; a tela só troca
 * se tiver algo mais específico a dizer.
 */
@Composable
fun SaqzOfflineBanner(
    modifier: Modifier = Modifier,
    message: String = stringResource(Res.string.offline_queued),
) {
    val colors = SaqzTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.textPrimary, RoundedCornerShape(SaqzTheme.metrics.cardRadius))
            .padding(horizontal = SaqzTheme.metrics.horizontalPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Indicador local em vez de SaqzSpinner: ele só escolhe entre primary e onPrimary,
        // e nenhum dos dois é o lime que o export pede em cima do navy.
        //
        // `clearAndSetSemantics` não é zelo: o CircularProgressIndicator publica semântica
        // de progresso indeterminado por conta própria, mesmo sem contentDescription. Sem
        // limpar, o TalkBack anuncia um nó de progresso ao lado da faixa e a pessoa ouve
        // duas coisas para um aviso só. Aqui o texto já diz tudo; o giro é decoração.
        CircularProgressIndicator(
            color = colors.accent,
            strokeWidth = 2.dp,
            modifier = Modifier.size(16.dp).clearAndSetSemantics {},
        )
        SaqzToastText(message, modifier = Modifier.weight(1f))
    }
}

// Badge do EmptyState: 64px em `.saqz-empty__icon` do export.
private val EMPTY_STATE_BADGE = 64.dp

// Glifo dentro do badge — `.saqz-empty__icon svg` do export.
private val EMPTY_STATE_GLYPH = 28.dp

@Preview
@Composable
private fun SaqzEmptyStatePreview() = SaqzTheme {
    SaqzPreviewGrid {
        SaqzEmptyState(
            title = "Nenhum jogo marcado por enquanto.",
            description = "Quando a galera marcar, ele aparece aqui.",
            icon = SaqzIcons.Calendar,
            action = "Criar jogo",
            onAction = {},
        )
    }
}

@Preview
@Composable
private fun SaqzToastPreview() = SaqzTheme {
    SaqzPreviewGrid {
        SaqzToast(visible = true, onDismiss = {}) {
            SaqzToastText("Presença confirmada. Bom jogo!")
        }
    }
}

@Preview
@Composable
private fun SaqzOfflineBannerPreview() = SaqzTheme {
    SaqzPreviewGrid {
        SaqzOfflineBanner()
        SaqzOfflineBanner(message = "Sem internet. A foto sobe quando a conexão voltar.")
    }
}
