package br.com.saqz.access.presentation.register

import androidx.compose.runtime.Immutable
import br.com.saqz.designsystem.UiText

/**
 * As duas recusas possíveis do campo de e-mail — o único dos quatro que tem mais de uma.
 *
 * [Invalid] é validação local, como a dos outros três. [Taken] vem do `EMAIL_IN_USE` que o
 * `createAccount` devolve, e é a única que **pergunta** ("Entrar?"), por isso a única
 * clicável. Um enum e não dois booleanos: os dois estados se excluem, e o par permitiria
 * um e-mail ao mesmo tempo malformado e já cadastrado.
 */
enum class RegisterEmailError { Invalid, Taken }

/**
 * As duas recusas do campo de senha, pelo mesmo motivo do [RegisterEmailError].
 *
 * [TooShort] é a validação local — o mínimo de 8 que o helper anuncia. [TooWeak] é a
 * política do provedor recusando uma senha que **passou** por ela, e por isso não pode
 * repetir a frase do comprimento: quem escolheu doze caracteres fracos e lê "use no mínimo
 * 8" não tem o que fazer com a informação.
 */
enum class RegisterPasswordError { TooShort, TooWeak }

@Immutable
data class RegisterInviteContext(
    val groupName: String? = null,
    val inviterName: String? = null,
    val entryRequiresApproval: Boolean = false,
) {
    val hasPreview: Boolean get() = groupName != null && inviterName != null

    companion object {
        /** Preview não chegou; o convite continua guardado no coordinator. */
        val Generic = RegisterInviteContext()

        fun preview(
            groupName: String,
            inviterName: String,
            entryRequiresApproval: Boolean,
        ) = RegisterInviteContext(groupName, inviterName, entryRequiresApproval)
    }
}

/**
 * O formulário da 1b, e o 1j é o mesmo estado com os quatro sinalizadores ligados.
 *
 * Nome e telefone carregam um booleano, e não uma mensagem: cada um tem **uma** frase
 * possível, então a string mora na tela e o estado só diz se ela aparece. E-mail e senha
 * fogem disso — os dois têm uma recusa local e uma do provedor, e por isso ganham
 * [RegisterEmailError] e [RegisterPasswordError]. É também o que mantém a
 * contagem do alerta honesta — [invalidFieldCount] conta o que está aceso agora, em vez de
 * repetir o "3" do mockup (que, aliás, mostra quatro campos errados).
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
    val emailError: RegisterEmailError? = null,
    val invalidPhone: Boolean = false,
    val passwordError: RegisterPasswordError? = null,
    val error: UiText? = null,
) {
    val invalidFieldCount: Int
        get() = listOf(invalidName, emailError != null, invalidPhone, passwordError != null).count { it }
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
