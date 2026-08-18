package br.com.saqz.access.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.saqz.access.presentation.login.LoginIntent
import br.com.saqz.access.presentation.login.LoginState
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.google_g
import br.com.saqz.access.resources.login_continue_with
import br.com.saqz.access.resources.login_email
import br.com.saqz.access.resources.login_email_placeholder
import br.com.saqz.access.resources.login_error_attempts
import br.com.saqz.access.resources.login_forgot_password
import br.com.saqz.access.resources.login_google
import br.com.saqz.access.resources.login_headline_emphasis
import br.com.saqz.access.resources.login_headline_first
import br.com.saqz.access.resources.login_headline_second
import br.com.saqz.access.resources.login_password
import br.com.saqz.access.resources.login_signup_link
import br.com.saqz.access.resources.login_signup_prompt
import br.com.saqz.access.resources.login_submit
import br.com.saqz.access.resources.login_supporting_text
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzInputKind
import br.com.saqz.designsystem.rememberSaqzFormScope
import br.com.saqz.designsystem.UiText
import br.com.saqz.designsystem.asString
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

internal object LoginTags {
    const val Email = "login-email"
    const val Password = "login-password"
    const val Submit = "login-submit"
    const val Google = "login-google"
    const val ForgotPassword = "login-forgot-password"
    const val CreateAccount = "login-create-account"
    const val Alert = "login-alert"
    const val Attempts = "login-attempts"
}

// SPEC_DEVIATION: `dp`/`sp` crus em `features/*/src/commonMain`, que a seção 5 do
// mobile/AGENTS.md proíbe por convenção.
// Reason: são medidas das telas 1a/1i, não do inventário do fluxo 10, e o AD-031 mantém
// no `:core:design-system` só o que o fluxo 10 lista. O que já tem número no chrome vem
// do [AccessMetrics]; o que sobra é desta tela e de mais nenhuma — o botão do Google é o
// caso extremo, porque nenhuma outra tela do app entra por provedor. Ficam aqui pelo
// mesmo motivo que os literais do `SaqzCodeInput` e do `SaqzInlineAlert` ficaram nos
// arquivos deles, e os três que o contrato versiona estão amarrados no `LoginScreenTest`.
private object LoginMetrics {
    // `fluxo1.gapDosCampos` — 12 entre os dois campos, 14 onde o export abre.
    val fieldGap = 12.dp
    val wideGap = 14.dp
    val blockGap = 18.dp
    val brandGap = 24.dp
    val headerGap = 30.dp
    val signupPromptGap = 20.dp
    val signupLinkGap = 8.dp

    // A folga entre o "Criar conta ›" e a onda de 130 do rodapé, para o link não deitar
    // sobre o azul nas telas curtas.
    val bottomGap = 40.dp

    val icon = 20.dp
    val arrow = 20.dp

    // Botão do Google. Mora **nesta tela**, e não no design system: é a única do app que
    // entra por provedor. A pílula, a altura de 52 e a borda de 1px são as do
    // `SaqzButton`; o que muda é a cor — linha `--saqz-border` cinza e rótulo navy, em vez
    // do azul do secundário — e o logo de 19.
    val googleLogo = 19.dp

    val dividerLabelSize = 12.5.sp
    val forgotSize = 14.sp
    val signupLinkSize = 15.sp
    val attemptsSize = 13.sp
    val attemptsLineHeight = 18.sp
}

/**
 * 1a (entrar) e 1i (credenciais recusadas) — a mesma tela: o 1i é o 1a com o alerta no
 * lugar do subtítulo, erro por campo e a frase do contador embaixo do botão.
 *
 * As duas são as **únicas** telas do fluxo com topo de 36 (`spacious`), porque são as
 * únicas sem botão de voltar; e as únicas com a marca grande e o lettering.
 *
 * Navegação entre features é callback (AGENTS.md §6): [onCreateAccount] (1b) e
 * [onForgotPassword] (1d) sobem para quem conhece o `NavDisplay`.
 */
@Composable
fun LoginScreen(
    state: LoginState,
    onIntent: (LoginIntent) -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val alert = state.error?.asString()

    AccessScaffold(modifier = modifier, spacious = true) {
        AccessBrandMark(large = true)
        Spacer(Modifier.height(LoginMetrics.brandGap))
        AccessHeader(
            title = "${stringResource(Res.string.login_headline_first)}\n" +
                stringResource(Res.string.login_headline_second),
            emphasis = stringResource(Res.string.login_headline_emphasis),
            // 1i não tem subtítulo: o alerta ocupa o lugar dele.
            subtitle = if (alert == null) stringResource(Res.string.login_supporting_text) else null,
            spacious = true,
        )
        Spacer(Modifier.height(LoginMetrics.headerGap))

        if (alert != null) {
            SaqzInlineAlert(
                text = alert,
                emphasis = alertEmphasis(alert),
                tone = SaqzInlineAlertTone.Error,
                modifier = Modifier.testTag(LoginTags.Alert),
            )
            Spacer(Modifier.height(LoginMetrics.fieldGap))
        }

        val form = rememberSaqzFormScope(onSubmit = { onIntent(LoginIntent.SubmitPasswordLogin) })
        SaqzInput(
            value = state.email,
            onValueChange = { onIntent(LoginIntent.UpdateEmail(it)) },
            label = stringResource(Res.string.login_email),
            kind = SaqzInputKind.Email,
            enabled = !state.isLoading,
            inlineLabel = true,
            placeholder = stringResource(Res.string.login_email_placeholder),
            errorText = state.emailError?.asString(),
            leadingContent = { SaqzIcon(SaqzIcons.Mail, tint = colors.primary, size = LoginMetrics.icon) },
            ime = form.imeNext(),
            modifier = Modifier.testTag(LoginTags.Email),
        )
        Spacer(Modifier.height(LoginMetrics.fieldGap))
        SaqzInput(
            value = state.password,
            onValueChange = { onIntent(LoginIntent.UpdatePassword(it)) },
            label = stringResource(Res.string.login_password),
            kind = SaqzInputKind.Password,
            enabled = !state.isLoading,
            inlineLabel = true,
            errorText = state.passwordError?.asString(),
            leadingContent = { SaqzIcon(SaqzIcons.Lock, tint = colors.primary, size = LoginMetrics.icon) },
            ime = form.imeDone(),
            modifier = Modifier.testTag(LoginTags.Password),
        )
        Spacer(Modifier.height(LoginMetrics.wideGap))
        LoginLink(
            label = stringResource(Res.string.login_forgot_password),
            size = LoginMetrics.forgotSize,
            weight = FontWeight(600),
            onClick = onForgotPassword,
            modifier = Modifier.testTag(LoginTags.ForgotPassword),
        )
        Spacer(Modifier.height(LoginMetrics.wideGap))
        SaqzButton(
            label = stringResource(Res.string.login_submit),
            onClick = { onIntent(LoginIntent.SubmitPasswordLogin) },
            fullWidth = true,
            enabled = !state.isLoading,
            loading = state.isLoading,
            trailingContent = { tint -> SaqzIcon(SaqzIcons.ArrowRight, tint = tint, size = LoginMetrics.arrow) },
            modifier = Modifier.testTag(LoginTags.Submit),
        )

        // Passado o teto anunciado a frase some: o limiar de verdade é do provedor e não é
        // conhecido, então "Errou 6 de 5 tentativas." é a saída errada de um contador que
        // já era só enfeite.
        if (state.failedAttempts in 1..LoginState.ANNOUNCED_ATTEMPT_LIMIT) {
            Spacer(Modifier.height(LoginMetrics.fieldGap))
            Text(
                text = stringResource(Res.string.login_error_attempts, state.failedAttempts),
                style = SaqzTheme.typography.caption.copy(
                    fontSize = LoginMetrics.attemptsSize,
                    lineHeight = LoginMetrics.attemptsLineHeight,
                    textAlign = TextAlign.Center,
                ),
                color = colors.textSecondary,
                modifier = Modifier.fillMaxWidth().testTag(LoginTags.Attempts),
            )
        }

        Spacer(Modifier.height(LoginMetrics.blockGap))
        LoginDivider(stringResource(Res.string.login_continue_with))
        Spacer(Modifier.height(LoginMetrics.blockGap))
        GoogleButton(
            label = stringResource(Res.string.login_google),
            onClick = { onIntent(LoginIntent.SubmitGoogleLogin) },
            enabled = !state.isLoading,
        )
        Spacer(Modifier.height(LoginMetrics.signupPromptGap))
        Text(
            text = stringResource(Res.string.login_signup_prompt),
            style = SaqzTheme.typography.support,
            color = colors.textSecondary,
        )
        Spacer(Modifier.height(LoginMetrics.signupLinkGap))
        LoginLink(
            label = stringResource(Res.string.login_signup_link),
            size = LoginMetrics.signupLinkSize,
            weight = FontWeight(700),
            onClick = onCreateAccount,
            modifier = Modifier.testTag(LoginTags.CreateAccount),
        )
        Spacer(Modifier.height(LoginMetrics.bottomGap))
    }
}

/**
 * O export marca em negrito a **primeira frase** do alerta ("E-mail ou senha
 * incorretos.") e deixa o resto no peso normal. Alerta de uma frase só — rede fora,
 * provedor indisponível, conta bloqueada — fica sem destaque, que é o que o
 * `SaqzInlineAlert` faz com `emphasis` nulo.
 */
internal fun alertEmphasis(text: String): String? =
    text.indexOf(". ").takeIf { it >= 0 }?.let { text.substring(0, it + 1) }

// Os dois links de saída da 1a: azul, centralizados, e diferentes só no tamanho e no peso.
@Composable
private fun ColumnScope.LoginLink(
    label: String,
    size: TextUnit,
    weight: FontWeight,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Text(
    text = label,
    style = SaqzTheme.typography.support.copy(fontSize = size, fontWeight = weight),
    color = SaqzTheme.colors.primary,
    modifier = modifier.align(Alignment.CenterHorizontally).clickable(onClick = onClick),
)

// Duas linhas de 1px com o rótulo no meio.
@Composable
private fun LoginDivider(label: String) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(LoginMetrics.fieldGap),
    verticalAlignment = Alignment.CenterVertically,
) {
    SaqzDivider(Modifier.weight(1f))
    Text(
        text = label,
        style = SaqzTheme.typography.caption.copy(fontSize = LoginMetrics.dividerLabelSize),
        color = SaqzTheme.colors.textSecondary,
    )
    SaqzDivider(Modifier.weight(1f))
}

@Composable
private fun GoogleButton(label: String, onClick: () -> Unit, enabled: Boolean) = SaqzButton(
    label = label,
    onClick = onClick,
    variant = SaqzButtonVariant.Secondary,
    fullWidth = true,
    enabled = enabled,
    contentColor = SaqzTheme.colors.textPrimary,
    borderColor = SaqzTheme.colors.border,
    leadingContent = {
        Image(
            painter = painterResource(Res.drawable.google_g),
            contentDescription = null,
            modifier = Modifier.size(LoginMetrics.googleLogo).clearAndSetSemantics {},
        )
    },
    modifier = Modifier.testTag(LoginTags.Google),
)

@Preview(name = "1a — entrar", widthDp = 390, heightDp = 844)
@Composable
private fun LoginScreenPreview() = SaqzTheme {
    LoginScreen(LoginState(email = "ana@exemplo.com"), {}, {}, {})
}

@Preview(name = "1i — credenciais recusadas", widthDp = 390, heightDp = 844)
@Composable
private fun LoginScreenRefusedPreview() = SaqzTheme {
    LoginScreen(
        state = LoginState(
            email = "ana@exemplo",
            password = "12345678",
            error = UiText.Raw("E-mail ou senha incorretos. Confira os dados e tente de novo."),
            emailError = UiText.Raw("Digite um e-mail válido."),
            passwordError = UiText.Raw("A senha não confere."),
            failedAttempts = 2,
        ),
        onIntent = {},
        onCreateAccount = {},
        onForgotPassword = {},
    )
}
