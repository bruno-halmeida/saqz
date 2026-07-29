package br.com.saqz.access.presentation.register

import androidx.lifecycle.SavedStateHandle
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
 * Quatro dependências, cada uma por um motivo diferente:
 *
 * - [savedState] guarda o rascunho contra morte de processo — ver [saveDraft];
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
    private val savedState: SavedStateHandle,
    private val auth: NativeAuthPort,
    private val authentication: AuthenticationStateMachine,
    private val transition: (AuthTransition) -> Unit,
) : MviViewModel<RegisterState, RegisterIntent, RegisterEffect>(savedState.restoredDraft()) {

    override fun onIntent(intent: RegisterIntent) {
        when (intent) {
            is RegisterIntent.UpdateName -> {
                saveDraft(KeyName, intent.value)
                edit { copy(name = intent.value, invalidName = false) }
            }
            is RegisterIntent.UpdateEmail -> {
                saveDraft(KeyEmail, intent.value)
                edit { copy(email = intent.value, emailError = null) }
            }
            is RegisterIntent.UpdatePhone -> {
                saveDraft(KeyPhone, intent.value)
                edit { copy(phone = intent.value, invalidPhone = false) }
            }
            // A senha **não** é gravada: rascunho de formulário sobrevive num arquivo do
            // sistema, e credencial não tem por que morar lá.
            is RegisterIntent.UpdatePassword -> edit { copy(password = intent.value, passwordError = null) }
            RegisterIntent.Submit -> submit()
            RegisterIntent.SignInWithTakenEmail -> {
                authentication.onIntent(AuthenticationIntent.UpdateEmail(state.value.email.trim()))
                emit(RegisterEffect.OpenLogin)
            }
        }
    }

    /**
     * O rascunho é gravado **na entrada**, e não ao sair da tela, porque morte de processo
     * não avisa. Só os três campos não sensíveis: quem volta de um processo morto reencontra
     * nome, e-mail e telefone e digita a senha de novo.
     */
    private fun saveDraft(key: String, value: String) {
        savedState[key] = value
    }

    private fun edit(change: RegisterState.() -> RegisterState) = update { current ->
        if (current.isLoading) current else current.change().copy(error = null)
    }

    private fun submit() {
        val current = state.value
        if (current.isLoading) return

        // Os validadores do nome e do telefone são os mesmos que a 1c usa. O e-mail e a
        // senha são checados aqui: o mínimo de 8 é o que o helper anuncia, e o e-mail
        // precisa de recusa **local** para o erro cair no campo — sem ela um endereço
        // malformado chega ao Firebase, volta como `INVALID_CREDENTIALS` e a tela de
        // cadastro exibiria "E-mail ou senha inválidos" sem apontar campo nenhum.
        val name = normalizedDisplayName(current.name)
        val email = normalizedEmail(current.email)
        val phone = normalizedBrMobilePhone(current.phone)
        val strongEnough = current.password.length >= REGISTER_MINIMUM_PASSWORD_LENGTH
        if (name == null || email == null || phone == null || !strongEnough) {
            update {
                it.copy(
                    invalidName = name == null,
                    emailError = if (email == null) RegisterEmailError.Invalid else null,
                    invalidPhone = phone == null,
                    passwordError = if (strongEnough) null else RegisterPasswordError.TooShort,
                    error = null,
                )
            }
            return
        }

        update { it.copy(isLoading = true, emailError = null, passwordError = null, error = null) }
        // A senha continua no estado durante o envio, ao contrário do login: o 1j desenha o
        // campo ainda preenchido depois da recusa, e obrigar a redigitar oito caracteres por
        // causa de uma queda de rede seria pior do que o risco que o login evita.
        auth.createAccount(name, email, current.password, callback(++submission))
    }

    private fun onAuthResult(result: AuthResult) {
        when (result) {
            AuthResult.Cancelled -> update { it.copy(isLoading = false) }
            is AuthResult.Failure -> fail(result.code)
            is AuthResult.Success -> {
                // O rascunho morre com a conta criada: a tela não volta, e o que sobreviveu
                // até aqui não tem por que esperar a próxima instalação.
                clearDraft()
                update { it.copy(isLoading = false) }
                transition(AuthTransition.Authenticated(result.user))
            }
        }
    }

    private fun clearDraft() {
        listOf(KeyName, KeyEmail, KeyPhone).forEach { savedState.remove<String>(it) }
    }

    /**
     * Os sete códigos que a porta pode devolver, e a quem cada um pertence.
     *
     * A 1j existe para mostrar erro **por campo**; recusa com campo dono que cai no alerta
     * global é a tela mentindo sobre onde está o problema. Por isso o `when` é exaustivo e
     * sem `else`: código novo na `NativeFailureCode` não compila até alguém decidir de quem
     * ele é.
     */
    private fun fail(code: NativeFailureCode) = update { current ->
        val settled = current.copy(isLoading = false)
        when (code) {
            // O e-mail já é de alguém. Vale para a colisão simples e para a conta que
            // existe por outro provedor (`ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL`
            // no Android, `accountExistsWithDifferentCredential` no iOS): as duas têm a
            // mesma resposta — entrar em vez de criar —, e a 1a é onde estão os dois
            // caminhos de entrada, inclusive o Google.
            NativeFailureCode.EMAIL_IN_USE,
            NativeFailureCode.AUTH_METHOD_CONFLICT,
            -> settled.copy(emailError = RegisterEmailError.Taken)

            // Num `createAccount` a credencial inválida só pode ser o e-mail: senha ruim
            // sai como `WEAK_PASSWORD`, e os dois adapters mapeiam `invalidEmail` para cá.
            // É o que a validação local barra antes; isto é a rede de segurança para o
            // endereço que passou por ela e o provedor recusou.
            NativeFailureCode.INVALID_CREDENTIALS -> settled.copy(emailError = RegisterEmailError.Invalid)

            // A política do provedor recusou a senha escolhida — o campo é o dono, e a
            // mensagem não é a do comprimento, que esta senha já cumpriu.
            NativeFailureCode.WEAK_PASSWORD -> settled.copy(passwordError = RegisterPasswordError.TooWeak)

            // Sem campo dono de verdade: nada no formulário explica nem conserta.
            NativeFailureCode.NETWORK_UNAVAILABLE,
            NativeFailureCode.PROVIDER_UNAVAILABLE,
            NativeFailureCode.UNKNOWN,
            -> settled.copy(error = code.toUiError().message())
        }
    }

    /**
     * A guarda de geração que o `mobile/AGENTS.md` exige em carga assíncrona ("a resposta é
     * descartada se o contexto mudou"), e o que de fato fecha o buraco que travar as saídas
     * só estreita.
     *
     * `createAccount` não tem cancelamento: o callback fica retido no provedor e vai chegar,
     * com a tela viva ou não. Bloquear o voltar, os dois links e o back do sistema reduz as
     * portas conhecidas; [submission] cobre as que ninguém previu, porque o resultado só é
     * aceito se ainda for o **corrente**.
     *
     * Duas coisas mudam o contexto: a tela morrer ([onCleared]) e um envio novo tomar o
     * lugar do anterior. Nos dois casos a resposta velha entra aqui e vai embora sem tocar
     * no estado e, principalmente, sem entregar sessão.
     */
    private var submission = 0

    override fun onCleared() {
        discardPendingSubmission()
        super.onCleared()
    }

    /** Invalida o que estiver em voo. Também é o gancho por onde o teste move o contexto. */
    internal fun discardPendingSubmission() {
        submission++
    }

    private fun callback(generation: Int) = object : AuthCallback {
        override fun complete(result: AuthResult) {
            if (generation != submission) return
            onAuthResult(result)
        }
    }
}

private const val KeyName = "register-name"
private const val KeyEmail = "register-email"
private const val KeyPhone = "register-phone"

private fun SavedStateHandle.restoredDraft() = RegisterState(
    name = get<String>(KeyName).orEmpty(),
    email = get<String>(KeyEmail).orEmpty(),
    phone = get<String>(KeyPhone).orEmpty(),
)

/**
 * A forma mínima de um e-mail, sem regex: sem espaço nem caractere de controle, exatamente
 * um `@` com algo antes, e um domínio com ponto que não abre nem fecha o texto.
 *
 * Mora aqui, e não no `AccessValidators`, porque hoje só a 1b valida e-mail — o campo da
 * 1a nunca recusou nada localmente. Sobe para lá no segundo uso, junto com o ticket que o
 * trouxer; é a mesma regra que mantém o `SaqzInlineAlert` fora do design system.
 *
 * Não é o RFC 5322 e nem tenta ser: o julgamento final é do provedor, e o que se quer aqui
 * é que o erro caia **no campo**, em vez de voltar como falha global de autenticação.
 */
internal fun normalizedEmail(input: String): String? {
    val value = input.trim()
    if (value.any { it.isWhitespace() || it.isISOControl() }) return null
    val at = value.indexOf('@')
    if (at <= 0 || at != value.lastIndexOf('@')) return null
    val domain = value.substring(at + 1)
    val dot = domain.lastIndexOf('.')
    return value.takeIf { dot > 0 && dot < domain.length - 1 }
}
