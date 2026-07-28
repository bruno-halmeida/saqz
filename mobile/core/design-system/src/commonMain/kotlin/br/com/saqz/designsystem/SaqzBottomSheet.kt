package br.com.saqz.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.resources.action_close
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

/**
 * 10n — raio de 28 só no topo, scrim navy a 46%, entrada de 320ms na curva enfática.
 *
 * ponytail: é uma sobreposição, não um Dialog — quem chama coloca o sheet como
 * último filho de um Box que ocupa a tela. Sem Dialog, o mesmo código serve
 * Android e iOS sem depender do que cada plataforma faz com uma janela extra.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SaqzBottomSheet(
    open: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    description: String? = null,
    footer: (@Composable () -> Unit)? = null,
    splitFooter: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val motion = SaqzTheme.motion
    val spec = tween<Float>(motion.sheetDurationMillis, easing = motion.emphasized)
    val slideSpec = tween<IntOffset>(
        motion.sheetDurationMillis,
        easing = motion.emphasized,
    )
    val scrimLabel = stringResource(Res.string.action_close)

    // O back é a terceira saída, ao lado do scrim e do rodapé: botão no Android, gesto no
    // iOS. Fica aqui e não em cada tela porque uma sobreposição que ignora o back deixa o
    // back agir na tela de baixo, com o sheet ainda aberto. `enabled = open` é o que
    // impede o componente fechado de engolir o back da tela inteira; e por estar dentro
    // do sheet, este handler é mais interno que o da tela hospedeira e consome primeiro.
    BackHandler(enabled = open, onBack = onClose)

    // clipToBounds: durante o slide o painel existe fora da tela e nada dele pode
    // pintar por cima do que está acima da sobreposição.
    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        AnimatedVisibility(visible = open, enter = fadeIn(spec), exit = fadeOut(spec)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = scrimLabel,
                        role = Role.Button,
                        onClick = onClose,
                    )
                    // Sem nome, o scrim é um clicável mudo para o TalkBack.
                    .semantics { contentDescription = scrimLabel },
            )
        }
        AnimatedVisibility(
            visible = open,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(slideSpec) { it },
            exit = slideOutVertically(slideSpec) { it },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = metrics.sheetRadius, topEnd = metrics.sheetRadius))
                    .background(colors.surface)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(metrics.horizontalPadding),
                verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
            ) {
                if (title != null) {
                    Text(text = title, style = SaqzTheme.typography.subtitle, color = colors.textPrimary)
                }
                if (description != null) {
                    Text(
                        text = description,
                        style = SaqzTheme.typography.support,
                        color = colors.textSecondary,
                    )
                }
                content()
                if (splitFooter != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = metrics.subGrid),
                        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
                    ) {
                        splitFooter()
                    }
                } else if (footer != null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = metrics.subGrid)) { footer() }
                }
            }
        }
    }
}

@Preview
@Composable
private fun SaqzBottomSheetPreview() = SaqzTheme {
    Box(Modifier.fillMaxSize().background(SaqzTheme.colors.background)) {
        SaqzBottomSheet(
            open = true,
            onClose = {},
            title = "Sair da conta?",
            description = "Você volta para a tela de entrada e precisa entrar de novo.",
            splitFooter = {
                SaqzButton(
                    label = "Cancelar",
                    onClick = {},
                    variant = SaqzButtonVariant.Secondary,
                    modifier = Modifier.weight(1f),
                )
                SaqzButton(
                    label = "Confirmar saída",
                    onClick = {},
                    variant = SaqzButtonVariant.Danger,
                    modifier = Modifier.weight(1f),
                )
            },
            content = {},
        )
    }
}
