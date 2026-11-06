package br.com.saqz.access.presentation.login

import androidx.lifecycle.viewModelScope
import br.com.saqz.access.presentation.AuthUiError
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationState
import br.com.saqz.access.presentation.AuthenticationStateMachine
import br.com.saqz.access.presentation.message
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.login_error_credentials
import br.com.saqz.access.resources.login_error_email_invalid
import br.com.saqz.access.resources.login_error_password
import br.com.saqz.access.resources.login_error_password_empty
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.designsystem.UiText
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class LoginViewModel(
    private val authentication: AuthenticationStateMachine,
) : MviViewModel<LoginState, LoginIntent, LoginEffect>(authentication.state.value.toLoginState()) {

    // Ligada no envio e desligada na primeira resposta que voltar. O `StateFlow` do state
    // machine conflacia — dois envios recusados coletados no mesmo quadro chegam como uma
    // emissão só —, então comparar o erro com o erro anterior contaria uma tentativa a
    // menos. Quem marca a tentativa é o envio; a resposta só diz qual foi.
    private var awaitingAnswer = false

    // O e-mail que o último envio recusou por formato. Fica aqui, e não só no `LoginState`,
    // porque a projeção do state machine chega um quadro depois e reconstrói o estado
    // inteiro — sem esta memória ela apagaria o erro que o envio acabou de escrever.
    private var rejectedEmail: String? = null

    // Se o último envio foi barrado por senha vazia. Mesma razão do [rejectedEmail]: a
    // projeção do state machine reconstrói o estado e apagaria o erro do campo.
    private var missingPassword = false

    init {
        authentication.state
            .onEach { auth ->
                val answered = awaitingAnswer && !auth.isLoading
                if (answered) awaitingAnswer = false
                update { current ->
                    auth.toLoginState().copy(
                        emailError = emailErrorFor(auth.email),
                        passwordError = passwordErrorFor(auth),
                        failedAttempts = current.attemptsAfter(auth, answered),
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: LoginIntent) {
        when (intent) {
            is LoginIntent.UpdateEmail -> authentication.onIntent(AuthenticationIntent.UpdateEmail(intent.value))
            is LoginIntent.UpdatePassword -> authentication.onIntent(AuthenticationIntent.UpdatePassword(intent.value))
            LoginIntent.SubmitPasswordLogin -> submitPasswordLogin()
            LoginIntent.SubmitGoogleLogin -> authentication.onIntent(AuthenticationIntent.SubmitGoogleLogin)
            LoginIntent.SubmitAppleLogin -> authentication.onIntent(AuthenticationIntent.SubmitAppleLogin)
        }
    }

    // E-mail malformado não vira ida ao provedor: a recusa dele voltaria como credencial
    // inválida e cobraria uma tentativa por um erro que a tela já sabia ver sozinha.
    //
    // Senha vazia também não: o Firebase do Android não recusa, lança
    // `IllegalArgumentException` e derruba o app (Crashlytics 09fbdfc2, 1.0.11). E chegar
    // aqui com ela vazia é comum, porque o envio apaga a senha e o state machine é `single`:
    // quem erra, passa por "Esqueci minha senha" e volta encontra o e-mail preenchido e a
    // senha em branco — foi o caminho do registro da falha.
    private fun submitPasswordLogin() {
        val form = authentication.state.value
        // Em voo a senha já foi apagada pelo envio: julgá-la agora acusaria "vazia" à toa.
        if (form.isLoading) return
        rejectedEmail = form.email.takeIf { !looksLikeEmail(it) }
        missingPassword = form.password.isEmpty()
        if (rejectedEmail != null || missingPassword) {
            update { it.copy(emailError = emailErrorFor(form.email), passwordError = passwordErrorFor(form)) }
            return
        }
        awaitingAnswer = true
        authentication.onIntent(AuthenticationIntent.SubmitPasswordLogin)
    }

    private fun emailErrorFor(email: String): UiText? =
        UiText.Res(Res.string.login_error_email_invalid).takeIf { email == rejectedEmail }

    private fun passwordErrorFor(auth: AuthenticationState): UiText? = when {
        missingPassword && auth.password.isEmpty() -> UiText.Res(Res.string.login_error_password_empty)
        auth.error == AuthUiError.INVALID_CREDENTIALS -> UiText.Res(Res.string.login_error_password)
        else -> null
    }
}

// ponytail: o contador é cosmético — veja [LoginState.failedAttempts]. Só credencial
// recusada conta; o bloqueio do provedor zera a conta e a frase dá lugar à mensagem de
// conta bloqueada, que é o único limite que existe de verdade.
private fun LoginState.attemptsAfter(auth: AuthenticationState, answered: Boolean): Int = when {
    auth.error == AuthUiError.TOO_MANY_REQUESTS -> 0
    answered && auth.error == AuthUiError.INVALID_CREDENTIALS -> failedAttempts + 1
    else -> failedAttempts
}

private fun AuthenticationState.toLoginState() = LoginState(
    email = email,
    password = password,
    isLoading = isLoading,
    appleSignInAvailable = appleSignInAvailable,
    error = error?.loginMessage(),
)

// A 1a fala a língua do export — "E-mail ou senha incorretos. Confira os dados e tente de
// novo." — e não o `auth_error_invalid_credentials` seco que o resto do acesso reusa.
private fun AuthUiError.loginMessage(): UiText = when (this) {
    AuthUiError.INVALID_CREDENTIALS -> UiText.Res(Res.string.login_error_credentials)
    else -> message()
}

// ponytail: forma, não existência — quem sabe se a caixa existe é o provedor. Um arroba no
// meio, sem espaço e com ponto no domínio é o que a 1i chama de "e-mail válido".
internal fun looksLikeEmail(value: String): Boolean {
    val trimmed = value.trim()
    val domain = trimmed.substringAfter('@', missingDelimiterValue = "")
    return trimmed.count { it == '@' } == 1 &&
        trimmed.first() != '@' &&
        trimmed.none(Char::isWhitespace) &&
        domain.contains('.') &&
        !domain.startsWith('.') &&
        !domain.endsWith('.')
}
