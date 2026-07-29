package br.com.saqz.access.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import br.com.saqz.access.presentation.register.RegisterEmailError
import br.com.saqz.access.presentation.register.RegisterIntent
import br.com.saqz.access.presentation.register.RegisterPasswordError
import br.com.saqz.access.presentation.register.RegisterState
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.access_password_hint
import br.com.saqz.access.resources.auth_error_weak_password
import br.com.saqz.access.resources.action_back
import br.com.saqz.access.resources.login_error_email_invalid
import br.com.saqz.access.resources.register_email_placeholder
import br.com.saqz.access.resources.register_error_email_taken
import br.com.saqz.access.resources.register_error_name
import br.com.saqz.access.resources.register_error_password
import br.com.saqz.access.resources.register_error_phone
import br.com.saqz.access.resources.register_error_summary
import br.com.saqz.access.resources.register_headline
import br.com.saqz.access.resources.register_headline_emphasis
import br.com.saqz.access.resources.register_name_placeholder
import br.com.saqz.access.resources.register_password_placeholder
import br.com.saqz.access.resources.register_phone_placeholder
import br.com.saqz.access.resources.register_signin_link
import br.com.saqz.access.resources.register_submit
import br.com.saqz.access.resources.register_supporting_text
import br.com.saqz.access.resources.register_terms
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzInput
import br.com.saqz.designsystem.SaqzInputKind
import br.com.saqz.designsystem.asString
import br.com.saqz.designsystem.theme.SaqzTheme
import org.jetbrains.compose.resources.stringResource

internal object RegisterTags {
    const val Back = "register-back"
    const val Alert = "register-alert"
    const val Name = "register-name"
    const val Email = "register-email"
    const val EmailTaken = "register-email-taken"
    const val Phone = "register-phone"
    const val Password = "register-password"
    const val PasswordHint = "register-password-hint"
    const val Submit = "register-submit"
    const val SignIn = "register-signin"
    const val Terms = "register-terms"
}

// A única unidade declarada aqui, e nem ela é um número: `AccessMetrics.TERMS_SIZE` é
// `Float` porque o objeto guarda tamanho de texto sem unidade (como `TITLE_SIZE` e
// `SUBTITLE_SIZE`), e quem o consome escolhe `sp`.
private val TermsFontSize = AccessMetrics.TERMS_SIZE.sp

/**
 * Os dois trechos que o export sublinha dentro da frase dos termos. São **localizadores**,
 * não texto de tela: o que aparece vem sempre de `register_terms`, e um localizador que não
 * casa apenas devolve a frase sem o destaque — a mesma degradação do `emphasis` do
 * [SaqzInlineAlert]. Não há destino para os dois documentos em lugar nenhum do app ainda,
 * então eles são estilo, não link.
 */
private val TermsLinks = listOf("Termos de uso", "Política de privacidade")

/**
 * 1b — criar conta —, e o 1j é esta mesma tela com os erros acesos.
 *
 * Sem botão do Google e sem divisor, ao contrário da 1a: quem quer entrar com Google faz
 * isso lá e cai na 1c. O subtítulo cede o lugar ao alerta quando há erro, que é a diferença
 * visível entre os dois quadros do export.
 */
@Composable
fun RegisterScreen(
    state: RegisterState,
    onIntent: (RegisterIntent) -> Unit,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SaqzTheme.colors
    val alert = registerAlert(state)

    AccessScaffold(modifier = modifier) {
        Box(Modifier.fillMaxWidth()) {
            SaqzIconButton(
                onClick = onBack,
                contentDescription = stringResource(Res.string.action_back),
                outlined = true,
                // As saídas fecham junto com os campos: o `createAccount` não tem
                // cancelamento, então sair no meio do envio deixaria a resposta chegando a
                // uma tela que já saiu — e criando conta e trocando sessão pelas costas.
                enabled = !state.isLoading,
                modifier = Modifier.align(Alignment.CenterStart).testTag(RegisterTags.Back),
            ) {
                SaqzIcon(SaqzIcons.ChevronLeft)
            }
        }
        Spacer(Modifier.height(AccessMetrics.blockGap))
        AccessBrandMark()
        Spacer(Modifier.height(AccessMetrics.blockGap))
        AccessHeader(
            title = stringResource(Res.string.register_headline),
            emphasis = stringResource(Res.string.register_headline_emphasis),
            // O 1j troca o subtítulo pelo alerta; não empilha os dois.
            subtitle = if (alert == null) stringResource(Res.string.register_supporting_text) else null,
        )
        Spacer(Modifier.height(AccessMetrics.blockGap))
        if (alert != null) {
            SaqzInlineAlert(
                text = alert.text,
                emphasis = alert.emphasis,
                tone = SaqzInlineAlertTone.Error,
                modifier = Modifier.testTag(RegisterTags.Alert),
            )
            Spacer(Modifier.height(AccessMetrics.blockGap))
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AccessMetrics.fieldGap),
        ) {
            RegisterInput(
                value = state.name,
                onValueChange = { onIntent(RegisterIntent.UpdateName(it)) },
                label = stringResource(Res.string.register_name_placeholder),
                icon = SaqzIcons.User,
                enabled = !state.isLoading,
                errorText = stringResource(Res.string.register_error_name).takeIf { state.invalidName },
                tag = RegisterTags.Name,
            )
            // O e-mail é o único campo com duas recusas possíveis, e elas se comportam de
            // formas diferentes. A malformada é como as outras três — mensagem no slot do
            // `SaqzInput`. A do e-mail já cadastrado **pergunta** ("Entrar?"), e a linha
            // inteira leva à 1a com o e-mail preenchido; por ser clicável ela não cabe no
            // slot, daí `invalid` para a borda vermelha e o texto desenhado ao lado, com o
            // mesmo estilo e o mesmo afastamento que o slot usaria.
            Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                RegisterInput(
                    value = state.email,
                    onValueChange = { onIntent(RegisterIntent.UpdateEmail(it)) },
                    label = stringResource(Res.string.register_email_placeholder),
                    icon = SaqzIcons.Mail,
                    kind = SaqzInputKind.Email,
                    enabled = !state.isLoading,
                    invalid = state.emailError == RegisterEmailError.Taken,
                    errorText = stringResource(Res.string.login_error_email_invalid)
                        .takeIf { state.emailError == RegisterEmailError.Invalid },
                    tag = RegisterTags.Email,
                )
                if (state.emailError == RegisterEmailError.Taken) {
                    Text(
                        text = stringResource(Res.string.register_error_email_taken),
                        style = SaqzTheme.typography.caption,
                        color = colors.errorForeground,
                        modifier = Modifier
                            .clickable(enabled = !state.isLoading) {
                                onIntent(RegisterIntent.SignInWithTakenEmail)
                            }
                            .testTag(RegisterTags.EmailTaken),
                    )
                }
            }
            RegisterInput(
                value = state.phone,
                onValueChange = { onIntent(RegisterIntent.UpdatePhone(it)) },
                label = stringResource(Res.string.register_phone_placeholder),
                icon = SaqzIcons.Phone,
                kind = SaqzInputKind.Phone,
                enabled = !state.isLoading,
                errorText = stringResource(Res.string.register_error_phone).takeIf { state.invalidPhone },
                tag = RegisterTags.Phone,
            )
            RegisterInput(
                value = state.password,
                onValueChange = { onIntent(RegisterIntent.UpdatePassword(it)) },
                label = stringResource(Res.string.register_password_placeholder),
                icon = SaqzIcons.Lock,
                kind = SaqzInputKind.Password,
                enabled = !state.isLoading,
                errorText = when (state.passwordError) {
                    // O comprimento é a recusa local; a política do provedor é a outra, e
                    // repetir "no mínimo 8" para uma senha de doze não ajudaria ninguém.
                    RegisterPasswordError.TooShort -> stringResource(Res.string.register_error_password)
                    RegisterPasswordError.TooWeak -> stringResource(Res.string.auth_error_weak_password)
                    null -> null
                },
                tag = RegisterTags.Password,
            )
        }
        // O helper mora fora do `SaqzInput` só por causa do `margin-top:-4px` do export; o
        // erro da senha o substitui, que é o que o 1j mostra.
        if (state.passwordError == null) {
            Text(
                text = stringResource(Res.string.access_password_hint),
                style = SaqzTheme.typography.caption,
                color = colors.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = AccessMetrics.helperLift)
                    .testTag(RegisterTags.PasswordHint),
            )
        }
        Spacer(Modifier.height(AccessMetrics.fieldGap))
        SaqzButton(
            label = stringResource(Res.string.register_submit),
            onClick = { onIntent(RegisterIntent.Submit) },
            enabled = !state.isLoading,
            loading = state.isLoading,
            trailingContent = { color -> SaqzIcon(SaqzIcons.ArrowRight, tint = color, size = AccessMetrics.primaryActionIcon) },
            modifier = Modifier.fillMaxWidth().clip(CircleShape).testTag(RegisterTags.Submit),
        )
        Spacer(Modifier.height(AccessMetrics.blockGap))
        Text(
            text = signInLink(stringResource(Res.string.register_signin_link), colors.primary),
            style = SaqzTheme.typography.support,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            // Mesma razão do voltar: enquanto envia, a 1b não tem saída.
            modifier = Modifier
                .clickable(enabled = !state.isLoading, onClick = onSignIn)
                .testTag(RegisterTags.SignIn),
        )
        Spacer(Modifier.height(AccessMetrics.fieldGap))
        Text(
            text = termsText(stringResource(Res.string.register_terms), colors.textSecondary),
            style = SaqzTheme.typography.caption.copy(
                fontSize = TermsFontSize,
                lineHeight = TermsFontSize * AccessMetrics.TERMS_LINE_HEIGHT_RATIO,
                textAlign = TextAlign.Center,
            ),
            color = colors.textPlaceholder,
            modifier = Modifier.widthIn(max = AccessMetrics.termsMaxWidth).testTag(RegisterTags.Terms),
        )
        // A onda sangra por cima do que estiver embaixo dela; sem este piso os termos
        // ficariam sob o azul numa tela curta.
        Spacer(Modifier.height(AccessMetrics.waveHeight))
    }
}

/** O que o alerta do topo diz, ou `null` quando não há o que dizer (que é o 1b). */
internal data class RegisterAlert(val text: String, val emphasis: String?)

@Composable
private fun registerAlert(state: RegisterState): RegisterAlert? {
    val count = state.invalidFieldCount
    if (count > 0) {
        val text = stringResource(Res.string.register_error_summary, count)
        return RegisterAlert(text, registerSummaryEmphasis(text, count))
    }
    return state.error?.let { RegisterAlert(it.asString(), emphasis = null) }
}

/**
 * "**Revise 3 campos** para criar sua conta." — o negrito vai do começo da frase até o fim
 * da palavra seguinte ao número.
 *
 * A fatia sai da própria string formatada em vez de um literal em PT-BR: assim mudar a
 * frase no `strings.xml` reposiciona o negrito sozinho, e no pior caso ele some (o
 * [SaqzInlineAlert] ignora ênfase que não casa) em vez de negritar o trecho errado.
 */
internal fun registerSummaryEmphasis(text: String, count: Int): String? {
    val number = text.indexOf(count.toString()).takeIf { it >= 0 } ?: return null
    val afterNumber = number + count.toString().length
    val wordEnd = text.indexOf(' ', startIndex = afterNumber + 1).takeIf { it > 0 } ?: return null
    return text.take(wordEnd)
}

/** "Já tem uma conta? **Entrar ›**" — o link é o que vem depois da interrogação. */
internal fun signInLink(text: String, color: Color): AnnotatedString {
    val start = text.indexOf('?').takeIf { it >= 0 }?.plus(2) ?: return AnnotatedString(text)
    if (start >= text.length) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), start, text.length)
    }
}

internal fun termsText(text: String, linkColor: Color): AnnotatedString =
    buildAnnotatedString {
        append(text)
        TermsLinks.forEach { link ->
            val start = text.indexOf(link)
            if (start >= 0) {
                addStyle(
                    SpanStyle(
                        color = linkColor,
                        fontWeight = FontWeight(AccessMetrics.LINK_WEIGHT),
                        textDecoration = TextDecoration.Underline,
                    ),
                    start,
                    start + link.length,
                )
            }
        }
    }

@Composable
private fun RegisterInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    tag: String,
    kind: SaqzInputKind = SaqzInputKind.Text,
    enabled: Boolean = true,
    invalid: Boolean = false,
    errorText: String? = null,
) = SaqzInput(
    value = value,
    onValueChange = onValueChange,
    label = label,
    kind = kind,
    enabled = enabled,
    invalid = invalid,
    errorText = errorText,
    // Sem rótulo acima: o export desenha só o placeholder dentro do campo.
    inlineLabel = true,
    leadingContent = { SaqzIcon(icon, tint = SaqzTheme.colors.primary, size = AccessMetrics.fieldIcon) },
    modifier = Modifier.testTag(tag),
)

@Preview(name = "1b — criar conta", widthDp = 390, heightDp = 844)
@Composable
private fun RegisterScreenPreview() = SaqzTheme {
    RegisterScreen(RegisterState(), {}, {}, {})
}

@Preview(name = "1j — criar conta, recusado", widthDp = 390, heightDp = 844)
@Composable
private fun RegisterScreenErrorPreview() = SaqzTheme {
    RegisterScreen(
        state = RegisterState(
            email = "rafa@galera.com",
            phone = "(11) 9999",
            password = "12345",
            invalidName = true,
            emailError = RegisterEmailError.Taken,
            invalidPhone = true,
            passwordError = RegisterPasswordError.TooShort,
        ),
        onIntent = {},
        onBack = {},
        onSignIn = {},
    )
}
