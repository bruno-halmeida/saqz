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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.clearAndSetSemantics
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
 * O painel tem as quatro faixas do `.saqz-sheet` do export, nesta ordem: alça, cabeçalho
 * (título + descrição à esquerda, fechar à direita) com divisória embaixo, conteúdo
 * rolável e rodapé com divisória em cima. As faixas pagam o próprio padding porque as
 * divisórias vão de borda a borda — um padding único no painel as encolheria.
 *
 * ponytail: é uma sobreposição, não um Dialog — quem chama coloca o sheet como
 * último filho de um Box que ocupa a tela. Sem Dialog, o mesmo código serve
 * Android e iOS sem depender do que cada plataforma faz com uma janela extra.
 *
 * ponytail: sem a sombra `--shadow-sheet` (`0 -12px 40px rgba(14,23,56,.12)`) — não há
 * token de sombra em `SaqzMetrics`/`SaqzColorTokens` e o VUL-58 proíbe cravar o número
 * aqui. Entra quando o VUL-44 versionar `--shadow-sheet` e `--shadow-toast`.
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
    val closeLabel = stringResource(Res.string.action_close)

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
                        onClickLabel = closeLabel,
                        role = Role.Button,
                        onClick = onClose,
                    )
                    // Sem nome, o scrim é um clicável mudo para o TalkBack.
                    .semantics { contentDescription = closeLabel },
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
                    .windowInsetsPadding(WindowInsets.navigationBars),
            ) {
                // `.saqz-sheet__handle-area{min-height:36px;place-items:center}` com a
                // `.saqz-sheet__handle{width:42px;height:5px;border-radius:var(--radius-pill)}`.
                // É a única affordance visível de que o painel é dispensável.
                Box(
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 42.dp, height = 5.dp)
                            .clip(CircleShape)
                            // O `#c9ced9` da alça é literal no bundle, não token; `disabledSurface`
                            // é o `--saqz-disabled-bg` (#C9CED8), o mesmo cinza a um passo de azul.
                            .background(colors.disabledSurface)
                            // Decorativa: sem isso o TalkBack para num nó que não diz nada.
                            .clearAndSetSemantics {},
                    )
                }
                // `.saqz-sheet__header{padding:4px 20px 16px;grid-template-columns:1fr auto;gap:16px;
                // align-items:start;border-bottom:1px solid var(--saqz-border)}`. Sem cabeçalho não
                // há fechar: no export ele nunca aparece sozinho, e um botão solto acima do
                // conteúdo não é desenho nenhum.
                if (title != null || description != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(metrics.horizontalPadding),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (title != null) {
                                Text(
                                    text = title,
                                    style = SaqzTheme.typography.subtitle,
                                    color = colors.textPrimary,
                                )
                            }
                            if (description != null) {
                                Text(
                                    text = description,
                                    style = SaqzTheme.typography.support,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                        // Círculo ice de 44 com o X navy — e a única saída que o TalkBack
                        // encontra varrendo o cabeçalho. Mesmo rótulo do scrim, de propósito.
                        SaqzIconButton(
                            onClick = onClose,
                            contentDescription = closeLabel,
                            soft = true,
                        ) {
                            SaqzIcon(SaqzIcons.Close, size = 20.dp)
                        }
                    }
                    SaqzDivider()
                }
                // `.saqz-sheet__content{overflow-y:auto;padding:16px 20px 24px;flex:1 1 auto}`.
                // As três outras faixas são fixas, então esta é a que cede: `weight(fill = false)`
                // a limita ao que sobra sem esticar o painel quando o conteúdo é curto, e o
                // scroll é o que impede conteúdo alto — ou fonte ampliada, ou tela baixa — de
                // empurrar o rodapé para fora da tela, onde o `clipToBounds` o cortaria e as
                // ações ficariam inalcançáveis.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = metrics.horizontalPadding, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
                    content = content,
                )
                // `.saqz-sheet__footer{padding:12px 20px 16px;border-top:1px solid var(--saqz-border)}`.
                if (splitFooter != null || footer != null) {
                    SaqzDivider()
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = metrics.blockGap, bottom = metrics.horizontalPadding),
                    ) {
                        if (splitFooter != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
                            ) {
                                splitFooter()
                            }
                        } else {
                            footer?.invoke()
                        }
                    }
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
            content = {
                Text(
                    text = "Os jogos marcados continuam lá quando você voltar.",
                    style = SaqzTheme.typography.body,
                    color = SaqzTheme.colors.textSecondary,
                )
            },
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
        )
    }
}
