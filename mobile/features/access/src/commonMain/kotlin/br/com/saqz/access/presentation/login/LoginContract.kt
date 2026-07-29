package br.com.saqz.access.presentation.login

import androidx.compose.runtime.Immutable
import br.com.saqz.designsystem.UiText

/**
 * O estado das telas 1a e 1i — a mesma tela, e é por isso que não há dois estados: o 1i é
 * o 1a com [error] preenchido.
 *
 * [emailError] e [passwordError] são erro **por campo**, e não a repetição do alerta:
 * o e-mail acusa formato inválido antes de qualquer chamada ao provedor, e a senha acusa
 * a recusa que voltou dele.
 */
@Immutable
data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: UiText? = null,
    val emailError: UiText? = null,
    val passwordError: UiText? = null,
    // ponytail: contador **cosmético**, só para desenhar a frase "Errou n de 5 tentativas"
    // do export. Ele mora na memória da ViewModel e zera ao sair da tela, porque o login
    // roda no cliente contra o Firebase e o nosso backend nunca vê tentativa que falhou —
    // não existe contador de servidor para ler. O limite que vale é o do provedor, que não
    // é 5, não diz quantas faltam e chega como `AuthUiError.TOO_MANY_REQUESTS`; quando ele
    // chega, este contador zera e a mensagem de conta bloqueada ocupa o lugar da frase.
    //
    // Por isso o teto de [ANNOUNCED_ATTEMPT_LIMIT]: o limiar real do provedor **não é
    // conhecido**, e nada garante que ele barre na quinta. Se ele aceitar a sexta senha
    // errada, a tela escreveria "Errou 6 de 5 tentativas.", que é texto sem sentido —
    // então, passado o teto, a frase some. Travar o número em "5 de 5" enquanto o login
    // ainda aceita tentativa continuaria mentindo, só que mais devagar.
    //
    // Para o "5 tentativas, 15 minutos" valer de verdade, o login tem de passar pelo
    // backend — é outro projeto.
    val failedAttempts: Int = 0,
) {
    companion object {
        // O "5" que a frase do export anuncia. Está aqui, e não solto na tela, porque é
        // do contador que ele é teto — e casa com o literal de `login_error_attempts`:
        // mexer num sem mexer no outro é exatamente como nasce o "Errou 6 de 5".
        const val ANNOUNCED_ATTEMPT_LIMIT = 5
    }
}

sealed interface LoginIntent {
    data class UpdateEmail(val value: String) : LoginIntent

    data class UpdatePassword(val value: String) : LoginIntent

    data object SubmitPasswordLogin : LoginIntent

    data object SubmitGoogleLogin : LoginIntent
}

/** Login exposes no one-off effects: sign-in flows through the shared session. */
sealed interface LoginEffect
