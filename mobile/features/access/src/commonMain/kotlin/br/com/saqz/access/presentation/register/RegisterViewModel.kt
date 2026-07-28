package br.com.saqz.access.presentation.register

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.presentation.AuthTransition
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationStateMachine
import br.com.saqz.access.presentation.message
import br.com.saqz.access.presentation.normalizedBrMobilePhone
import br.com.saqz.access.presentation.normalizedDisplayName
import br.com.saqz.access.presentation.toUiError
import br.com.saqz.core.common.mvi.MviViewModel

/** O que o helper da 1b promete e o mínimo que o Firebase aceita. */
const val REGISTER_MINIMUM_PASSWORD_LENGTH = 8

/**
 * A 1b/1j. Valida **ao submeter**, e não a cada tecla: o 1j mostra os quatro erros de uma
 * vez, que é o que se vê ao tocar em "Criar conta" com o formulário torto. Editar um campo
 * apaga só o erro dele, senão a pessoa consertaria o nome e continuaria vendo a acusação.
 *
 * Três dependências, cada uma por um motivo diferente:
 *
 * - [auth] é quem cria a conta — `createAccount` já existia na porta e nos dois adapters
 *   desde sempre, sem nenhuma tela que o chamasse;
 * - [authentication] é o formulário compartilhado da 1a, e existe aqui só para o "Entrar?"
 *   do e-mail duplicado chegar lá com o campo preenchido. A `LoginViewModel` projeta essa
 *   máquina campo a campo, então escrever nela **é** preencher a 1a;
 * - [transition] entrega a autenticação vitoriosa a quem administra a sessão, exatamente
 *   como o `AuthenticationStateMachine` do login já faz. Dali a pessoa segue para a 1c com
 *   o nome que o `createAccount` gravou no `displayName`.
 *
 * O telefone é validado aqui mas não sobe agora: `createAccount` leva nome, e-mail e senha,
 * e quem grava telefone é o `completeProfile` da 1c, que roda depois do bootstrap. Quem
 * chega na 1c reencontra o campo vazio — carregá-lo até lá é mudança no
 * `SessionAccessStateMachine`, que é de outro dono.
 */
class RegisterViewModel(
    private val auth: NativeAuthPort,
    private val authentication: AuthenticationStateMachine,
    private val transition: (AuthTransition) -> Unit,
) : MviViewModel<RegisterState, RegisterIntent, RegisterEffect>(RegisterState()) {

    override fun onIntent(intent: RegisterIntent) {
        when (intent) {
            is RegisterIntent.UpdateName -> edit { copy(name = intent.value, invalidName = false) }
            is RegisterIntent.UpdateEmail -> edit { copy(email = intent.value, emailTaken = false) }
            is RegisterIntent.UpdatePhone -> edit { copy(phone = intent.value, invalidPhone = false) }
            is RegisterIntent.UpdatePassword -> edit { copy(password = intent.value, invalidPassword = false) }
            RegisterIntent.Submit -> submit()
            RegisterIntent.SignInWithTakenEmail -> {
                authentication.onIntent(AuthenticationIntent.UpdateEmail(state.value.email.trim()))
                emit(RegisterEffect.OpenLogin)
            }
        }
    }

    private fun edit(change: RegisterState.() -> RegisterState) = update { current ->
        if (current.isLoading) current else current.change().copy(error = null)
    }

    private fun submit() {
        val current = state.value
        if (current.isLoading) return

        // Os validadores do `AccessValidators` são os mesmos que a 1c usa; o único critério
        // próprio da 1b é o comprimento da senha, que é o que o helper anuncia.
        val name = normalizedDisplayName(current.name)
        val phone = normalizedBrMobilePhone(current.phone)
        val strongEnough = current.password.length >= REGISTER_MINIMUM_PASSWORD_LENGTH
        if (name == null || phone == null || !strongEnough) {
            update {
                it.copy(
                    invalidName = name == null,
                    invalidPhone = phone == null,
                    invalidPassword = !strongEnough,
                    error = null,
                )
            }
            return
        }

        update { it.copy(isLoading = true, emailTaken = false, error = null) }
        // A senha continua no estado durante o envio, ao contrário do login: o 1j desenha o
        // campo ainda preenchido depois da recusa, e obrigar a redigitar oito caracteres por
        // causa de uma queda de rede seria pior do que o risco que o login evita.
        auth.createAccount(name, current.email.trim(), current.password, callback())
    }

    private fun onAuthResult(result: AuthResult) {
        when (result) {
            AuthResult.Cancelled -> update { it.copy(isLoading = false) }
            is AuthResult.Failure -> fail(result.code)
            is AuthResult.Success -> {
                update { it.copy(isLoading = false) }
                transition(AuthTransition.Authenticated(result.user))
            }
        }
    }

    // O único código que vira erro de campo é o `EMAIL_IN_USE`, e é justamente o erro do
    // 1j que faz uma pergunta. O resto é falha de infraestrutura e vai para o alerta.
    private fun fail(code: NativeFailureCode) = update {
        if (code == NativeFailureCode.EMAIL_IN_USE) {
            it.copy(isLoading = false, emailTaken = true)
        } else {
            it.copy(isLoading = false, error = code.toUiError().message())
        }
    }

    private fun callback() = object : AuthCallback {
        override fun complete(result: AuthResult) = onAuthResult(result)
    }
}
