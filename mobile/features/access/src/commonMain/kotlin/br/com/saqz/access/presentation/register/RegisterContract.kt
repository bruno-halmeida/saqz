package br.com.saqz.access.presentation.register

import androidx.compose.runtime.Immutable
import br.com.saqz.designsystem.UiText

/**
 * O formulário da 1b, e o 1j é o mesmo estado com os quatro sinalizadores ligados.
 *
 * Cada campo carrega um booleano, e não uma mensagem: no export cada um tem **uma** frase
 * possível, então a string mora na tela e o estado só diz se ela aparece. É também o que
 * mantém a contagem do alerta honesta — [invalidFieldCount] conta o que está aceso agora,
 * em vez de repetir o "3" do mockup (que, aliás, mostra quatro campos errados).
 *
 * [error] é o que **não** é de campo: rede fora, provedor indisponível, o inesperado. Vai
 * para o mesmo alerta, no lugar do resumo.
 */
@Immutable
data class RegisterState(
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val invalidName: Boolean = false,
    val emailTaken: Boolean = false,
    val invalidPhone: Boolean = false,
    val invalidPassword: Boolean = false,
    val error: UiText? = null,
) {
    val invalidFieldCount: Int
        get() = listOf(invalidName, emailTaken, invalidPhone, invalidPassword).count { it }
}

sealed interface RegisterIntent {
    data class UpdateName(val value: String) : RegisterIntent

    data class UpdateEmail(val value: String) : RegisterIntent

    data class UpdatePhone(val value: String) : RegisterIntent

    data class UpdatePassword(val value: String) : RegisterIntent

    data object Submit : RegisterIntent

    /**
     * A resposta ao "Entrar?" que o erro de e-mail duplicado pergunta. Não é o mesmo que o
     * "Já tem uma conta? Entrar ›" do rodapé: este leva o e-mail digitado junto.
     */
    data object SignInWithTakenEmail : RegisterIntent
}

sealed interface RegisterEffect {
    /** Ir para a 1a. O e-mail, quando há, já foi posto no formulário de lá antes daqui. */
    data object OpenLogin : RegisterEffect
}
