package br.com.saqz.access.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import br.com.saqz.access.presentation.forgotpassword.ForgotPasswordIntent
import br.com.saqz.access.presentation.forgotpassword.ForgotPasswordState
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.access_signin_link
import br.com.saqz.access.resources.action_back
import br.com.saqz.access.resources.forgot_headline
import br.com.saqz.access.resources.forgot_headline_emphasis
import br.com.saqz.access.resources.forgot_submit
import br.com.saqz.access.resources.forgot_supporting_text
import br.com.saqz.access.resources.login_email
import br.com.saqz.access.resources.login_email_placeholder
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzInputKind
import br.com.saqz.designsystem.asString
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

internal object ForgotPasswordTags {
    const val Back = "forgot-back"
    const val Email = "forgot-email"
    const val Submit = "forgot-submit"
    const val SignIn = "forgot-signin"
    const val Error = "forgot-error"
}

/**
 * 1d — a menor tela do fluxo e a porta de entrada da recuperação de senha: voltar, marca
 * pequena, cabeçalho e três blocos (campo, botão e o link de volta ao 1a).
 *
 * A tela **não sabe** se a conta existe, e é assim de propósito: o efeito de sucesso sai
 * do ViewModel para toda resposta aceita. O que aparece aqui é recusa de transporte ou
 * e-mail malformado — nada que separe "tem conta" de "não tem".
 */
@Composable
fun ForgotPasswordScreen(
    state: ForgotPasswordState,
    onIntent: (ForgotPasswordIntent) -> Unit,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) = AccessScaffold(modifier) {
    SaqzIconButton(
        onClick = onBack,
        contentDescription = stringResource(Res.string.action_back),
        outlined = true,
        modifier = Modifier.align(Alignment.Start).testTag(ForgotPasswordTags.Back),
    ) {
        SaqzIcon(SaqzIcons.ChevronLeft)
    }
    Spacer(Modifier.height(BACK_TO_BRAND))
    AccessBrandMark()
    Spacer(Modifier.height(BRAND_TO_HEADER))
    AccessHeader(
        title = stringResource(Res.string.forgot_headline),
        emphasis = stringResource(Res.string.forgot_headline_emphasis),
        subtitle = stringResource(Res.string.forgot_supporting_text),
    )
    Spacer(Modifier.height(HEADER_TO_FORM))
    state.error?.let { error ->
        SaqzInlineAlert(
            text = error.asString(),
            tone = SaqzInlineAlertTone.Error,
            modifier = Modifier.testTag(ForgotPasswordTags.Error),
        )
        Spacer(Modifier.height(FIELD_GAP))
    }
    SaqzInput(
        value = state.email,
        onValueChange = { onIntent(ForgotPasswordIntent.UpdateEmail(it)) },
        label = stringResource(Res.string.login_email),
        kind = SaqzInputKind.Email,
        enabled = !state.isSubmitting,
        inlineLabel = true,
        placeholder = stringResource(Res.string.login_email_placeholder),
        leadingContent = { SaqzIcon(SaqzIcons.Mail, tint = SaqzTheme.colors.primary) },
        modifier = Modifier.testTag(ForgotPasswordTags.Email),
    )
    Spacer(Modifier.height(FIELD_GAP))
    SaqzButton(
        label = stringResource(Res.string.forgot_submit),
        onClick = { onIntent(ForgotPasswordIntent.Submit) },
        fullWidth = true,
        loading = state.isSubmitting,
        trailingContent = { SaqzIcon(SaqzIcons.ArrowRight, tint = it, size = SUBMIT_ICON) },
        modifier = Modifier.testTag(ForgotPasswordTags.Submit),
    )
    Spacer(Modifier.height(FORM_TO_SIGNIN))
    AccessSignInLink(onClick = onSignIn)
}

/**
 * "Lembrou a senha? **Entrar ›**" — uma chave só no `strings.xml`, como a convenção do
 * arquivo manda, e a tela é quem parte a frase: o que vem depois da pergunta é o link.
 * Sem `?` na frase, tudo cai no texto muted e a linha continua legível, só sem link.
 */
@Composable
private fun AccessSignInLink(onClick: () -> Unit) {
    val (prompt, link) = splitAccessSignInLink(stringResource(Res.string.access_signin_link))
    Row(
        horizontalArrangement = Arrangement.spacedBy(SIGNIN_GAP),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = prompt,
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
        )
        if (link.isNotEmpty()) {
            Text(
                text = link,
                style = SaqzTheme.typography.support.copy(fontWeight = FontWeight(SIGNIN_LINK_WEIGHT)),
                color = SaqzTheme.colors.primary,
                modifier = Modifier.clickable(onClick = onClick).testTag(ForgotPasswordTags.SignIn),
            )
        }
    }
}

internal fun splitAccessSignInLink(label: String): Pair<String, String> {
    val cut = label.indexOf('?')
    if (cut < 0) return label to ""
    return label.take(cut + 1) to label.drop(cut + 1).trim()
}

// SPEC_DEVIATION: `dp` cru em `features/*/src/commonMain`, que a seção 5 do mobile/AGENTS.md
// proíbe por convenção.
// Reason: mesma razão do `AccessMetrics` (AccessChrome.kt): são medidas do frame do fluxo 1,
// que o AD-031 mantém fora do `:core:design-system`. Estas cinco são os **afastamentos entre
// os blocos da 1d**, que o `ui-contract.json` não versiona bloco a bloco — saem da régua do
// próprio export (390×844 @2x): voltar 20..64, marca 90..158, título 177..240, subtítulo
// 251..293, campo 317..373, botão 385..437, link 457..478. O único com token é o 12 entre
// campo e botão (`gapDosCampos.padrao`), e é por isso que ele reaparece entre o alerta e o
// campo. Ficam privados neste arquivo, como o VUL-77 e o VUL-78 já fazem com os deles;
// sobem para o `AccessMetrics` quando uma segunda tela repetir a mesma régua.
private val BACK_TO_BRAND = 26.dp
private val BRAND_TO_HEADER = 18.dp
private val HEADER_TO_FORM = 24.dp
private val FIELD_GAP = 12.dp
private val FORM_TO_SIGNIN = 20.dp
private val SIGNIN_GAP = 6.dp
private val SUBMIT_ICON = 20.dp
private const val SIGNIN_LINK_WEIGHT = 700

@Preview(name = "1d — esqueci a senha", widthDp = 390, heightDp = 844)
@Composable
private fun ForgotPasswordScreenPreview() = SaqzTheme {
    ForgotPasswordScreen(ForgotPasswordState(), {}, {}, {})
}
