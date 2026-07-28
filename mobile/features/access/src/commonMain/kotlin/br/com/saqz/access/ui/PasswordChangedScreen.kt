package br.com.saqz.access.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.action_back
import br.com.saqz.access.resources.password_changed_headline
import br.com.saqz.access.resources.password_changed_headline_emphasis
import br.com.saqz.access.resources.password_changed_submit
import br.com.saqz.access.resources.password_changed_supporting_text
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.theme.SaqzMotionPolicy
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

internal object PasswordChangedTags {
    const val Back = "password-changed-back"
    const val Check = "password-changed-check"
    const val Submit = "password-changed-submit"
}

/**
 * O 1h: um check e um botão. **Sem ViewModel** — não há estado nenhum a guardar, e criar
 * um por simetria com as outras dez telas seria encanamento sem água.
 *
 * É a única tela do fluxo que **centraliza o conteúdo verticalmente**, sobre uma folga
 * fixa embaixo. Por isso monta a própria coluna sobre a [AccessWave] em vez de ganhar um
 * slot no [AccessScaffold] que nenhuma outra tela usaria — e por isso o voltar aqui é
 * filho da coluna, no topo, com o resto empurrado para o meio do que sobra.
 */
@Composable
fun PasswordChangedScreen(
    onSignIn: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(SaqzTheme.colors.surface)) {
        AccessWave(Modifier.align(Alignment.BottomCenter))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(horizontal = AccessMetrics.horizontalPadding)
                .padding(top = AccessMetrics.topPadding, bottom = PasswordChangedMetrics.bottomSlack)
                .testTag(AccessChromeTags.Content),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SaqzIconButton(
                onClick = onBack,
                contentDescription = stringResource(Res.string.action_back),
                outlined = true,
                modifier = Modifier.align(Alignment.Start).testTag(PasswordChangedTags.Back),
            ) {
                SaqzIcon(SaqzIcons.ChevronLeft, tint = SaqzTheme.colors.textPrimary)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                SuccessCheck()
                Spacer(Modifier.height(CHECK_TO_HEADER))
                AccessHeader(
                    title = stringResource(Res.string.password_changed_headline),
                    emphasis = stringResource(Res.string.password_changed_headline_emphasis),
                    subtitle = stringResource(Res.string.password_changed_supporting_text),
                )
                Spacer(Modifier.height(HEADER_TO_BUTTON))
                SaqzButton(
                    label = stringResource(Res.string.password_changed_submit),
                    onClick = onSignIn,
                    fullWidth = true,
                    trailingContent = { color -> SaqzIcon(SaqzIcons.ArrowRight, tint = color, size = ARROW) },
                    modifier = Modifier.testTag(PasswordChangedTags.Submit),
                )
            }
        }
    }
}

/**
 * O selo verde. A entrada roda quando o círculo **entra na composição**, que aqui é a
 * própria abertura da tela — mesmo mecanismo do [SaqzInlineAlert], e por isso a mesma
 * curva. Com Reduce Motion o selo aparece sem crescer nem deslizar: o que fica é a
 * opacidade, que é o que diz "isto acabou de acontecer" para quem não quer movimento.
 */
@Composable
private fun SuccessCheck() {
    val motion = SaqzTheme.motion
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(
            1f,
            tween(PasswordChangedMetrics.CHECK_DURATION_MILLIS, easing = motion.emphasized),
        )
    }
    Box(
        modifier = Modifier
            .graphicsLayer {
                alpha = entrance.value
                val grow = passwordChangedCheckScale(entrance.value, motion)
                scaleX = grow
                scaleY = grow
            }
            .size(PasswordChangedMetrics.circle)
            .background(SaqzTheme.colors.success.copy(alpha = PasswordChangedMetrics.CIRCLE_TINT), CircleShape)
            .testTag(PasswordChangedTags.Check),
        contentAlignment = Alignment.Center,
    ) {
        SaqzIcon(SaqzIcons.Check, tint = SaqzTheme.colors.success, size = PasswordChangedMetrics.check)
    }
}

/**
 * O quanto o selo cresce ao entrar. Com Reduce Motion ele não cresce — nasce no tamanho
 * final e só a opacidade anda —, e é por isso que a decisão é uma função pura: é o que o
 * `PasswordChangedScreenTest` consegue conferir sem capturar quadro.
 */
internal fun passwordChangedCheckScale(entrance: Float, motion: SaqzMotionPolicy): Float =
    if (motion == SaqzMotionPolicy.Reduced) 1f
    else ENTRANCE_SCALE_FROM + (1f - ENTRANCE_SCALE_FROM) * entrance

// SPEC_DEVIATION: `dp` cru em `features/*/src/commonMain` (mobile/AGENTS.md §5), pelo
// mesmo motivo do `NewPasswordScreen`: são as medidas desta tela do export, não tokens do
// fluxo 10. Os números do selo e a folga de baixo estão versionados em
// `fluxo1.senhaAlterada` do ui-contract.json, e o `PasswordChangedScreenTest` os amarra
// lá — o traço do check é a exceção registrada em `_exceptions.fluxo1.senhaAlterada`,
// porque o glifo vem inteiro da Lucide (stroke 2) desde o VUL-54.
internal object PasswordChangedMetrics {
    val circle = 96.dp
    val check = 46.dp
    val bottomSlack = 60.dp
    const val CIRCLE_TINT = 0.12f
    const val CHECK_DURATION_MILLIS = 400
}

// Afastamentos do desenho desta tela, sem número no contrato.
private val CHECK_TO_HEADER = 18.dp
private val HEADER_TO_BUTTON = 26.dp
private val ARROW = 16.dp

// De onde o selo cresce. Não está no export — ele descreve só a duração —, mas é a mesma
// escala do press do design system, do outro lado do 1.
private const val ENTRANCE_SCALE_FROM = 0.98f

@Preview(name = "1h — senha alterada", widthDp = 390, heightDp = 844)
@Composable
private fun PasswordChangedScreenPreview() = SaqzTheme {
    PasswordChangedScreen(onSignIn = {}, onBack = {})
}
