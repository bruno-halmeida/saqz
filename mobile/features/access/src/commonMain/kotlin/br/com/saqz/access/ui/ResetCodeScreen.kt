package br.com.saqz.access.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.access.presentation.resetcode.ResetCodeIntent
import br.com.saqz.access.presentation.resetcode.ResetCodeState
import br.com.saqz.access.presentation.resetcode.formatResendCountdown
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.access_signin_link
import br.com.saqz.access.resources.action_back
import br.com.saqz.access.resources.reset_code_error_expired
import br.com.saqz.access.resources.reset_code_error_invalid
import br.com.saqz.access.resources.reset_code_headline
import br.com.saqz.access.resources.reset_code_headline_emphasis
import br.com.saqz.access.resources.reset_code_resend
import br.com.saqz.access.resources.reset_code_resend_countdown
import br.com.saqz.access.resources.reset_code_resend_wait
import br.com.saqz.access.resources.reset_code_resent
import br.com.saqz.access.resources.reset_code_submit
import br.com.saqz.access.resources.reset_code_supporting_text
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.asString
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

internal object ResetCodeTags {
    const val Back = "reset-code-back"
    const val Code = "reset-code-input"
    const val Resend = "reset-code-resend"
    const val Submit = "reset-code-submit"
    const val SignIn = "reset-code-signin"
    const val Resent = "reset-code-resent"
    const val Expired = "reset-code-expired"
    const val Failure = "reset-code-failure"
}

/**
 * As telas 1e, 1f e 1k do export: **uma** tela, com o estado dizendo qual dos três
 * arranjos aparece.
 *
 * O que muda entre eles é sempre um bloco entrando ou saindo da coluna de baixo — o
 * alerta verde do reenvio, a linha vermelha das tentativas, o alerta âmbar do código
 * expirado —, e o botão, que troca de rótulo e de variante quando "Verificar código"
 * deixa de fazer sentido. Nada aqui é uma segunda tela.
 */
@Composable
fun ResetCodeScreen(
    state: ResetCodeState,
    onIntent: (ResetCodeIntent) -> Unit,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AccessScaffold(modifier) {
        SaqzIconButton(
            onClick = onBack,
            contentDescription = stringResource(Res.string.action_back),
            outlined = true,
            modifier = Modifier.align(Alignment.Start).testTag(ResetCodeTags.Back),
        ) {
            SaqzIcon(SaqzIcons.ChevronLeft)
        }
        Spacer(Modifier.height(BRAND_GAP))
        AccessBrandMark()
        Spacer(Modifier.height(BRAND_GAP))
        AccessHeader(
            title = stringResource(Res.string.reset_code_headline),
            emphasis = stringResource(Res.string.reset_code_headline_emphasis),
        )
        Spacer(Modifier.height(AccessMetrics.subtitleGap))
        ResetCodeSubtitle(state.email)
        Spacer(Modifier.height(HEADER_GAP))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BLOCK_GAP),
        ) {
            if (state.resent) {
                SaqzInlineAlert(
                    text = stringResource(Res.string.reset_code_resent),
                    // O 1f é a frase inteira em negrito.
                    emphasis = stringResource(Res.string.reset_code_resent),
                    tone = SaqzInlineAlertTone.Success,
                    modifier = Modifier.testTag(ResetCodeTags.Resent),
                )
            }
            SaqzCodeInput(
                value = state.code,
                onValueChange = { onIntent(ResetCodeIntent.UpdateCode(it)) },
                // Não há chave própria para o rótulo do campo: o leitor de tela recebe o
                // título da tela, que é literalmente o que se pede ali.
                label = codeFieldLabel(),
                errorText = state.remainingAttempts?.let {
                    stringResource(Res.string.reset_code_error_invalid, it)
                },
                enabled = !state.busy,
                modifier = Modifier.testTag(ResetCodeTags.Code),
            )
            // Expirado não conta mais janela de reenvio: quem pede outro código é o
            // botão, que virou "Reenviar código" logo abaixo.
            if (!state.expired) {
                ResetCodeResendLine(state = state, onResend = { onIntent(ResetCodeIntent.Resend) })
            }
            if (state.expired) {
                SaqzInlineAlert(
                    text = stringResource(Res.string.reset_code_error_expired),
                    tone = SaqzInlineAlertTone.Warning,
                    modifier = Modifier.testTag(ResetCodeTags.Expired),
                )
            }
            state.failure?.let { failure ->
                SaqzInlineAlert(
                    text = failure.asString(),
                    tone = SaqzInlineAlertTone.Error,
                    modifier = Modifier.testTag(ResetCodeTags.Failure),
                )
            }
            SaqzButton(
                label = stringResource(
                    if (state.expired) Res.string.reset_code_resend else Res.string.reset_code_submit,
                ),
                onClick = {
                    onIntent(if (state.expired) ResetCodeIntent.Resend else ResetCodeIntent.Verify)
                },
                // Sem seta: o export a reserva para as telas que avançam o cadastro.
                variant = if (state.expired) SaqzButtonVariant.Secondary else SaqzButtonVariant.Primary,
                fullWidth = true,
                // Cada modo espera a sua janela: expirado depende do balde de reenvio, e
                // conferir depende do de verificação. Sem texto próprio para a espera de
                // verificação, quem a comunica é o botão indisponível.
                enabled = if (state.expired) state.canResend else state.canVerify,
                loading = if (state.expired) state.resending else state.verifying,
                modifier = Modifier.testTag(ResetCodeTags.Submit),
            )
        }
        // O 1f e o 1k trocam o "Entrar ›" pelo alerta: os dois não convivem no rodapé.
        if (!state.hasAlert) {
            Spacer(Modifier.height(FOOTER_GAP))
            ResetCodeSignInLink(onSignIn)
        }
    }
}

/** "Digite o código.", montado das duas metades do título — não há chave só do rótulo. */
@Composable
private fun codeFieldLabel(): String {
    val head = stringResource(Res.string.reset_code_headline).trim()
    return "$head ${stringResource(Res.string.reset_code_headline_emphasis)}"
}

/**
 * O subtítulo com o e-mail em navy dentro da frase muted. Não sai do [AccessHeader]
 * porque lá o subtítulo é texto puro, e aqui metade dele muda de cor e de peso.
 */
@Composable
private fun ResetCodeSubtitle(email: String) {
    val colors = SaqzTheme.colors
    val sentence = stringResource(Res.string.reset_code_supporting_text, email)
    Text(
        text = emphasize(sentence, email, SpanStyle(color = colors.textPrimary, fontWeight = FontWeight(600))),
        style = SaqzTheme.typography.support.copy(
            fontSize = AccessMetrics.SUBTITLE_SIZE.sp,
            lineHeight = AccessMetrics.SUBTITLE_SIZE.sp * AccessMetrics.SUBTITLE_LINE_HEIGHT_RATIO,
            textAlign = TextAlign.Center,
        ),
        color = colors.textSecondary,
        modifier = Modifier.widthIn(max = AccessMetrics.subtitleMaxWidth).testTag(AccessChromeTags.Subtitle),
    )
}

/**
 * A linha do reenvio, abaixo das caixas.
 *
 * Duas redações da mesma informação, e é o export que as separa: no 1e a pessoa está
 * esperando o código chegar ("Não chegou? Reenviar código · 0:42"), no 1f ela acabou de
 * pedir outro ("Reenviar novamente em 0:59"). Quando o contador zera, sempre a primeira
 * — é ela que carrega o link.
 *
 * ponytail: o toque é da linha inteira, não só do trecho "Reenviar código". Uma linha
 * centrada de uma ação só não tem outro alvo com que confundir, e recortar a região
 * clicável custaria um `LinkAnnotation` para ganhar nada. O alvo só existe com o
 * contador zerado; enquanto ele corre, a linha não é tocável.
 */
@Composable
private fun ResetCodeResendLine(state: ResetCodeState, onResend: () -> Unit) {
    val colors = SaqzTheme.colors
    val clock = formatResendCountdown(state.resendSeconds)
    val action = stringResource(Res.string.reset_code_resend)
    val waiting = state.resent && !state.canResend

    val text = if (waiting) {
        emphasize(
            sentence = stringResource(Res.string.reset_code_resend_wait, clock),
            fragment = clock,
            style = SpanStyle(color = colors.textPrimary, fontWeight = FontWeight(700)),
        )
    } else {
        val sentence = stringResource(Res.string.reset_code_resend_countdown, clock)
        val start = sentence.indexOf(action)
        buildAnnotatedString {
            append(sentence)
            if (start >= 0) {
                // Travado, o trecho da ação e o contador saem juntos em placeholder;
                // liberado, só a ação vira link azul e o 0:00 continua apagado.
                val end = if (state.canResend) start + action.length else sentence.length
                addStyle(
                    SpanStyle(
                        color = if (state.canResend) colors.primary else colors.textPlaceholder,
                        fontWeight = if (state.canResend) FontWeight(600) else null,
                    ),
                    start,
                    end,
                )
            }
        }
    }

    Text(
        text = text,
        style = SaqzTheme.typography.support.copy(
            fontSize = HINT_SIZE.sp,
            lineHeight = HINT_SIZE.sp * HINT_LINE_HEIGHT_RATIO,
            textAlign = TextAlign.Center,
        ),
        color = colors.textSecondary,
        modifier = Modifier
            .clickable(
                enabled = state.canResend,
                onClickLabel = action,
                role = Role.Button,
                onClick = onResend,
            )
            .testTag(ResetCodeTags.Resend),
    )
}

/** "Lembrou a senha? **Entrar ›**" — uma chave só, com o link do "? " em diante. */
@Composable
private fun ResetCodeSignInLink(onSignIn: () -> Unit) {
    val colors = SaqzTheme.colors
    val sentence = stringResource(Res.string.access_signin_link)
    val prompt = sentence.lastIndexOf("? ")
    val start = if (prompt < 0) 0 else prompt + 2
    Text(
        text = buildAnnotatedString {
            append(sentence)
            addStyle(
                SpanStyle(color = colors.primary, fontWeight = FontWeight(600)),
                start,
                sentence.length,
            )
        },
        style = SaqzTheme.typography.support.copy(textAlign = TextAlign.Center),
        color = colors.textSecondary,
        modifier = Modifier
            .clickable(onClickLabel = sentence.substring(start), role = Role.Button, onClick = onSignIn)
            .testTag(ResetCodeTags.SignIn),
    )
}

private fun emphasize(sentence: String, fragment: String, style: SpanStyle): AnnotatedString {
    val start = sentence.lastIndexOf(fragment)
    if (start < 0) return AnnotatedString(sentence)
    return buildAnnotatedString {
        append(sentence)
        addStyle(style, start, start + fragment.length)
    }
}

// SPEC_DEVIATION: `dp` e `sp` crus em `features/*/src/commonMain`, que a seção 5 do
// AGENTS.md proíbe por convenção.
// Reason: mesmo motivo do `AccessMetrics` (AccessChrome.kt) e dos literais que o VUL-77 e
// o VUL-78 já mergearam — são medidas das telas do fluxo 1, e o AD-031 mantém no
// `:core:design-system` só o que o fluxo 10 lista. Ficam neste arquivo, e não no
// `AccessMetrics`, porque são do desenho destas três telas e mais ninguém as lê; subir
// para o objeto compartilhado é conflito garantido com os seis tickets de tela irmãos.
private val BRAND_GAP = 24.dp
private val HEADER_GAP = 20.dp
private val BLOCK_GAP = 14.dp
private val FOOTER_GAP = 20.dp
private const val HINT_SIZE = 13f
private const val HINT_LINE_HEIGHT_RATIO = 1.5f

@Preview(name = "1e — aguardando o código", widthDp = 390, heightDp = 844)
@Composable
private fun ResetCodeWaitingPreview() = SaqzTheme {
    ResetCodeScreen(
        state = ResetCodeState(email = "ana@exemplo.com", resendSeconds = 42),
        onIntent = {},
        onBack = {},
        onSignIn = {},
    )
}

@Preview(name = "1f — código reenviado", widthDp = 390, heightDp = 844)
@Composable
private fun ResetCodeResentPreview() = SaqzTheme {
    ResetCodeScreen(
        state = ResetCodeState(email = "ana@exemplo.com", resendSeconds = 59, resent = true),
        onIntent = {},
        onBack = {},
        onSignIn = {},
    )
}

@Preview(name = "1k — código incorreto e expirado", widthDp = 390, heightDp = 844)
@Composable
private fun ResetCodeRefusedPreview() = SaqzTheme {
    ResetCodeScreen(
        state = ResetCodeState(
            email = "ana@exemplo.com",
            code = "1359",
            resendSeconds = 0,
            remainingAttempts = 2,
            expired = true,
        ),
        onIntent = {},
        onBack = {},
        onSignIn = {},
    )
}
