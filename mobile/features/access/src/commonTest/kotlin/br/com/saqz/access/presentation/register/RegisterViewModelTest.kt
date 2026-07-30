package br.com.saqz.access.presentation.register

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.NativeUser
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.presentation.AuthTransition
import br.com.saqz.access.presentation.AuthenticationStateMachine
import br.com.saqz.access.presentation.SessionIntent
import br.com.saqz.access.presentation.message
import br.com.saqz.access.presentation.toUiError
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.auth_error_network
import br.com.saqz.designsystem.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    // O 1j inteiro: os quatro campos errados de uma vez, que é o que se vê ao tocar em
    // "Criar conta" com o formulário torto. O e-mail é o único que não vem daqui.
    @Test
    fun `submitting a crooked form lights every local field at once`() = runTest(mainDispatcher) {
        val (viewModel, auth) = fixture()
        viewModel.fill(name = " ", email = "rafa@galera.com", phone = "(11) 9999", password = "12345")

        viewModel.onIntent(RegisterIntent.Submit)

        val state = viewModel.state.value
        assertTrue(state.invalidName, "nome vazio")
        assertTrue(state.invalidPhone, "telefone incompleto")
        assertEquals(RegisterPasswordError.TooShort, state.passwordError, "senha curta")
        assertNull(state.emailError, "e-mail bem formado não entra na conta")
        assertEquals(3, state.invalidFieldCount)
        assertTrue(auth.accounts.isEmpty(), "formulário torto não chega ao provedor")
    }

    @Test
    fun `each local rule fails on its own`() = runTest(mainDispatcher) {
        val (nameOnly, _) = fixture()
        nameOnly.fill(name = "A", email = "ana@exemplo.com", phone = "11999990000", password = "senha-forte")
        nameOnly.onIntent(RegisterIntent.Submit)
        assertEquals(1, nameOnly.state.value.invalidFieldCount)
        assertTrue(nameOnly.state.value.invalidName)

        // Fixo é telefone válido para o backend, mas não para o `normalizedBrMobilePhone`:
        // sem o 9 na frente não é celular.
        val (phoneOnly, _) = fixture()
        phoneOnly.fill(name = "Ana", email = "ana@exemplo.com", phone = "(11) 3333-4444", password = "senha-forte")
        phoneOnly.onIntent(RegisterIntent.Submit)
        assertEquals(1, phoneOnly.state.value.invalidFieldCount)
        assertTrue(phoneOnly.state.value.invalidPhone)

        // Sete caracteres: um a menos do que o helper promete.
        val (passwordOnly, _) = fixture()
        passwordOnly.fill(name = "Ana", email = "ana@exemplo.com", phone = "11999990000", password = "1234567")
        passwordOnly.onIntent(RegisterIntent.Submit)
        assertEquals(1, passwordOnly.state.value.invalidFieldCount)
        assertEquals(RegisterPasswordError.TooShort, passwordOnly.state.value.passwordError)
    }

    @Test
    fun `the happy path creates the account with the normalized name`() = runTest(mainDispatcher) {
        val (viewModel, auth) = fixture()
        viewModel.fill(name = "  Ana Souza  ", email = " ana@exemplo.com ", phone = "(11) 99999-0000", password = "senha-forte")

        viewModel.onIntent(RegisterIntent.Submit)

        assertEquals(listOf(AccountCall("Ana Souza", "ana@exemplo.com", "senha-forte")), auth.accounts)
        assertEquals(0, viewModel.state.value.invalidFieldCount)
        assertTrue(viewModel.state.value.isLoading)
    }

    @Test
    fun `a created account becomes a session transition`() = runTest(mainDispatcher) {
        val sessionIntents = mutableListOf<SessionIntent>()
        val (viewModel, auth) = fixture(onSessionIntent = sessionIntents::add)
        viewModel.submitValidForm()

        auth.complete(AuthResult.Success(USER))

        assertIs<SessionIntent.StageRegistrationIdentity>(sessionIntents.first())
        assertEquals(
            SessionIntent.Accept(AuthTransition.Authenticated(USER)),
            sessionIntents.last(),
        )
        assertTrue(!viewModel.state.value.isLoading)
    }

    // VUL-101: o telefone validado sobe à sessão **antes** do provedor, senão o observe
    // autentica sozinho e a 1c abre sem o número que a pessoa acabou de digitar.
    @Test
    fun `submitting stages the phone before createAccount`() = runTest(mainDispatcher) {
        val sessionIntents = mutableListOf<SessionIntent>()
        val (viewModel, auth) = fixture(onSessionIntent = sessionIntents::add)
        viewModel.submitValidForm()

        assertEquals(
            listOf(
                SessionIntent.StageRegistrationIdentity(
                    name = "Ana Souza",
                    phone = "+5511999990000",
                ),
            ),
            sessionIntents.toList(),
        )
        assertEquals(1, auth.accounts.size)
    }

    @Test
    fun `a provider refusal clears the staged registration identity`() = runTest(mainDispatcher) {
        val sessionIntents = mutableListOf<SessionIntent>()
        val (viewModel, auth) = fixture(onSessionIntent = sessionIntents::add)
        viewModel.submitValidForm()

        auth.complete(AuthResult.Failure(NativeFailureCode.EMAIL_IN_USE))

        assertEquals(SessionIntent.ClearRegistrationIdentity, sessionIntents.last())
        assertEquals(RegisterEmailError.Taken, viewModel.state.value.emailError)
    }

    @Test
    fun `an email already registered lights only the email field`() = runTest(mainDispatcher) {
        val (viewModel, auth) = fixture()
        viewModel.submitValidForm()

        auth.complete(AuthResult.Failure(NativeFailureCode.EMAIL_IN_USE))

        val state = viewModel.state.value
        assertEquals(RegisterEmailError.Taken, state.emailError)
        assertEquals(1, state.invalidFieldCount)
        assertNull(state.error, "e-mail duplicado é erro de campo, não do alerta")
    }

    // Rede fora não é campo torto: vai para o alerta, e a contagem continua zerada.
    @Test
    fun `an infrastructure failure lands on the alert instead of a field`() = runTest(mainDispatcher) {
        val (viewModel, auth) = fixture()
        viewModel.submitValidForm()

        auth.complete(AuthResult.Failure(NativeFailureCode.NETWORK_UNAVAILABLE))

        assertEquals(UiText.Res(Res.string.auth_error_network), viewModel.state.value.error)
        assertEquals(0, viewModel.state.value.invalidFieldCount)
    }

    /**
     * O mapeamento inteiro, e não um código de cada vez: a 1j existe para mostrar erro por
     * campo, e as três primeiras rodadas de review deste PR foram a mesma falha — recusa do
     * provedor com campo dono caindo no alerta global. Este teste é o que impede a quarta.
     *
     * Percorre os sete códigos da porta e exige, para cada um, o campo dono (ou a ausência
     * dele). Código novo na `NativeFailureCode` quebra o `when` da ViewModel na compilação e
     * este `when` aqui junto.
     */
    @Test
    fun `every provider failure lands on the field that owns it`() = runTest(mainDispatcher) {
        NativeFailureCode.entries.forEach { code ->
            val (viewModel, auth) = fixture()
            viewModel.submitValidForm()

            auth.complete(AuthResult.Failure(code))

            val state = viewModel.state.value
            when (code) {
                NativeFailureCode.EMAIL_IN_USE,
                NativeFailureCode.AUTH_METHOD_CONFLICT,
                -> {
                    assertEquals(RegisterEmailError.Taken, state.emailError, "$code")
                    assertNull(state.error, "$code tem campo dono e não pode ir ao alerta")
                }
                NativeFailureCode.INVALID_CREDENTIALS -> {
                    assertEquals(RegisterEmailError.Invalid, state.emailError, "$code")
                    assertNull(state.error, "$code tem campo dono e não pode ir ao alerta")
                }
                NativeFailureCode.WEAK_PASSWORD -> {
                    assertEquals(RegisterPasswordError.TooWeak, state.passwordError, "$code")
                    assertNull(state.error, "$code tem campo dono e não pode ir ao alerta")
                }
                NativeFailureCode.NETWORK_UNAVAILABLE,
                NativeFailureCode.PROVIDER_UNAVAILABLE,
                NativeFailureCode.TOO_MANY_REQUESTS,
                NativeFailureCode.UNKNOWN,
                -> {
                    assertEquals(0, state.invalidFieldCount, "$code não tem campo dono")
                    assertEquals(code.toUiError().message(), state.error, "$code")
                }
            }
            assertTrue(!state.isLoading, "$code precisa destravar o formulário")
        }
    }

    // A senha recusada pela política do provedor não repete a frase do comprimento: quem
    // escolheu doze caracteres fracos não tem o que fazer com "use no mínimo 8".
    @Test
    fun `a password refused by the provider is not the local length message`() = runTest(mainDispatcher) {
        val (viewModel, auth) = fixture()
        viewModel.fill(name = "Ana Souza", email = "ana@exemplo.com", phone = "11999990000", password = "12345678")
        viewModel.onIntent(RegisterIntent.Submit)

        auth.complete(AuthResult.Failure(NativeFailureCode.WEAK_PASSWORD))

        assertEquals(RegisterPasswordError.TooWeak, viewModel.state.value.passwordError)
        assertEquals(1, viewModel.state.value.invalidFieldCount)
    }

    @Test
    fun `editing a field clears only its own error`() = runTest(mainDispatcher) {
        val (viewModel, _) = fixture()
        viewModel.fill(name = "", email = "ana@exemplo.com", phone = "119", password = "123")
        viewModel.onIntent(RegisterIntent.Submit)
        assertEquals(3, viewModel.state.value.invalidFieldCount)

        viewModel.onIntent(RegisterIntent.UpdateName("Ana"))

        assertTrue(!viewModel.state.value.invalidName)
        assertEquals(2, viewModel.state.value.invalidFieldCount)
    }

    // O "Entrar?" do e-mail duplicado: a 1a precisa aparecer com o e-mail já preenchido,
    // senão a pergunta fica sem resposta possível.
    @Test
    fun `answering the taken email question prefills the login form`() = runTest(mainDispatcher) {
        val auth = FakeAuthPort()
        val authentication = AuthenticationStateMachine(auth) {}
        val viewModel = RegisterViewModel(SavedStateHandle(), auth, authentication) {}
        viewModel.onIntent(RegisterIntent.UpdateEmail(" rafa@galera.com "))

        viewModel.onIntent(RegisterIntent.SignInWithTakenEmail)

        assertEquals("rafa@galera.com", authentication.state.value.email)
        assertEquals(RegisterEffect.OpenLogin, viewModel.effects.first())
    }

    /**
     * O achado do Codex: sem recusa local, um e-mail malformado chegava ao provedor, voltava
     * como `INVALID_CREDENTIALS` e a tela de **cadastro** exibia "E-mail ou senha inválidos"
     * sem apontar campo nenhum. Agora nem sai daqui, e o erro é do campo.
     */
    @Test
    fun `a malformed email is refused locally and never reaches the provider`() = runTest(mainDispatcher) {
        listOf("", "   ", "ana", "ana@", "@exemplo.com", "ana@exemplo", "ana@exemplo.", "a n@exemplo.com", "a@b@c.com")
            .forEach { malformed ->
                val (viewModel, auth) = fixture()
                viewModel.fill(name = "Ana Souza", email = malformed, phone = "11999990000", password = "senha-forte")

                viewModel.onIntent(RegisterIntent.Submit)

                val state = viewModel.state.value
                assertEquals(RegisterEmailError.Invalid, state.emailError, "aceitou \"$malformed\"")
                assertEquals(1, state.invalidFieldCount, "\"$malformed\" acendeu campo alheio")
                assertNull(state.error, "e-mail torto é erro de campo, não do alerta")
                assertTrue(auth.accounts.isEmpty(), "\"$malformed\" chegou ao provedor")
            }
    }

    // Morte de processo: nome, e-mail e telefone voltam; a senha, **nunca** — ela não é
    // gravada, e é isso que este teste tranca.
    @Test
    fun `the draft survives process death without the password`() = runTest(mainDispatcher) {
        val savedState = SavedStateHandle()
        val (dying, _) = fixture(savedState)
        dying.fill(name = "Ana Souza", email = "ana@exemplo.com", phone = "11999990000", password = "senha-forte")

        val (restored, _) = fixture(savedState)

        assertEquals("Ana Souza", restored.state.value.name)
        assertEquals("ana@exemplo.com", restored.state.value.email)
        assertEquals("11999990000", restored.state.value.phone)
        assertEquals("", restored.state.value.password, "senha não pode sobreviver ao processo")
    }

    @Test
    fun `the draft dies with the account created`() = runTest(mainDispatcher) {
        val savedState = SavedStateHandle()
        val (viewModel, auth) = fixture(savedState)
        viewModel.submitValidForm()

        auth.complete(AuthResult.Success(USER))

        val (fresh, _) = fixture(savedState)
        assertEquals(RegisterState(), fresh.state.value)
    }

    /**
     * A guarda de geração do `mobile/AGENTS.md` ("a resposta é descartada se o contexto
     * mudou"), e o que ela protege: o **estado desta tela**.
     *
     * O que ela deliberadamente não protege é a sessão — quem a entrega é o `auth.observe`
     * do `AccessOrchestrator`, que dispara sozinho quando a conta nasce. Ver o KDoc de
     * `submission`: a conta foi criada, e ficar autenticado é o que a pessoa pediu.
     */
    @Test
    fun `a response that arrives after the screen is gone writes no state`() = runTest(mainDispatcher) {
        val sessionIntents = mutableListOf<SessionIntent>()
        val (viewModel, auth) = fixture(onSessionIntent = sessionIntents::add)
        viewModel.submitValidForm()

        // O que o back do sistema provoca: a tela sai e a ViewModel morre com o envio em voo.
        viewModel.discardPendingSubmission()
        auth.complete(AuthResult.Success(USER))

        assertTrue(viewModel.state.value.isLoading, "ViewModel morta não escreve estado")
        // Este caminho de transição cala; o do orquestrador, não — e é ele que vale.
        // O discard limpa o depósito da 1b para não sobrar no SignedOut (Codex/VUL-101).
        assertEquals(
            listOf(
                SessionIntent.StageRegistrationIdentity(name = "Ana Souza", phone = "+5511999990000"),
                SessionIntent.ClearRegistrationIdentity,
            ),
            sessionIntents.toList(),
        )
    }

    @Test
    fun `discarding the submission clears the staged registration identity`() = runTest(mainDispatcher) {
        val sessionIntents = mutableListOf<SessionIntent>()
        val (viewModel, _) = fixture(onSessionIntent = sessionIntents::add)
        viewModel.submitValidForm()

        viewModel.discardPendingSubmission()

        assertEquals(SessionIntent.ClearRegistrationIdentity, sessionIntents.last())
    }

    // A outra forma de o contexto mudar: um envio novo toma o lugar do anterior, e a
    // resposta velha chega atrasada.
    @Test
    fun `a late response from a replaced submission is discarded`() = runTest(mainDispatcher) {
        val sessionIntents = mutableListOf<SessionIntent>()
        val (viewModel, auth) = fixture(onSessionIntent = sessionIntents::add)
        viewModel.submitValidForm()
        auth.completeSubmission(0, AuthResult.Cancelled)
        val afterCancel = sessionIntents.toList()
        viewModel.onIntent(RegisterIntent.Submit)

        auth.completeSubmission(0, AuthResult.Success(USER))

        assertEquals(2, auth.accounts.size, "o segundo envio saiu")
        // Cancelamento limpa o depósito; o envio novo deposita de novo; a resposta velha
        // não acrescenta Accept.
        assertTrue(afterCancel.contains(SessionIntent.ClearRegistrationIdentity))
        assertTrue(
            sessionIntents.none { it is SessionIntent.Accept },
            "a resposta do envio substituído não vale mais",
        )
        assertTrue(viewModel.state.value.isLoading, "nem o estado ela toca")
    }

    @Test
    fun `a submit already in flight is ignored`() = runTest(mainDispatcher) {
        val (viewModel, auth) = fixture()
        viewModel.submitValidForm()

        viewModel.onIntent(RegisterIntent.Submit)

        assertEquals(1, auth.accounts.size)
    }

    private fun RegisterViewModel.fill(name: String, email: String, phone: String, password: String) {
        onIntent(RegisterIntent.UpdateName(name))
        onIntent(RegisterIntent.UpdateEmail(email))
        onIntent(RegisterIntent.UpdatePhone(phone))
        onIntent(RegisterIntent.UpdatePassword(password))
    }

    private fun RegisterViewModel.submitValidForm() {
        fill(name = "Ana Souza", email = "ana@exemplo.com", phone = "(11) 99999-0000", password = "senha-forte")
        onIntent(RegisterIntent.Submit)
    }

    private fun fixture(
        savedState: SavedStateHandle = SavedStateHandle(),
        onSessionIntent: (SessionIntent) -> Unit = {},
    ): Pair<RegisterViewModel, FakeAuthPort> {
        val auth = FakeAuthPort()
        return RegisterViewModel(savedState, auth, AuthenticationStateMachine(auth) {}, onSessionIntent) to auth
    }

    private data class AccountCall(val name: String, val email: String, val password: String)

    private class FakeAuthPort : NativeAuthPort {
        val accounts = mutableListOf<AccountCall>()

        // Todos os callbacks, e não só o último: o `createAccount` não cancela, então o
        // teste da guarda de geração precisa disparar um envio **velho** depois do novo.
        private val callbacks = mutableListOf<AuthCallback>()

        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) {
            accounts += AccountCall(name, email, password)
            callbacks += done
        }

        fun complete(result: AuthResult) = callbacks.last().complete(result)

        fun completeSubmission(index: Int, result: AuthResult) = callbacks[index].complete(result)

        override fun observe(listener: AuthStateListener): Cancelable =
            object : Cancelable { override fun cancel() = Unit }
        override fun signInWithPassword(email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithGoogle(done: AuthCallback) = Unit
        override fun sendVerification(done: ResultCallback) = Unit
        override fun reloadUser(done: AuthCallback) = Unit
        override fun updateDisplayName(name: String, done: AuthCallback) = Unit
        override fun idToken(forceRefresh: Boolean, done: TokenCallback) = Unit
        override fun signOut(done: ResultCallback) = Unit
    }

    private companion object {
        val USER = NativeUser(
            subject = "uid-ana",
            email = "ana@exemplo.com",
            emailVerified = false,
            displayName = "Ana Souza",
        )
    }
}
