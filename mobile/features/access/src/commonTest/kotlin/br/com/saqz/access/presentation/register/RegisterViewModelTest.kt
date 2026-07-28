package br.com.saqz.access.presentation.register

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
        assertTrue(state.invalidPassword, "senha curta")
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
        assertTrue(passwordOnly.state.value.invalidPassword)
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
        val transitions = mutableListOf<AuthTransition>()
        val (viewModel, auth) = fixture(onTransition = transitions::add)
        viewModel.submitValidForm()

        auth.complete(AuthResult.Success(USER))

        assertEquals(listOf<AuthTransition>(AuthTransition.Authenticated(USER)), transitions.toList())
        assertTrue(!viewModel.state.value.isLoading)
    }

    @Test
    fun `an email already registered lights only the email field`() = runTest(mainDispatcher) {
        val (viewModel, auth) = fixture()
        viewModel.submitValidForm()

        auth.complete(AuthResult.Failure(NativeFailureCode.EMAIL_IN_USE))

        val state = viewModel.state.value
        assertTrue(state.emailTaken)
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
        val viewModel = RegisterViewModel(auth, authentication) {}
        viewModel.onIntent(RegisterIntent.UpdateEmail(" rafa@galera.com "))

        viewModel.onIntent(RegisterIntent.SignInWithTakenEmail)

        assertEquals("rafa@galera.com", authentication.state.value.email)
        assertEquals(RegisterEffect.OpenLogin, viewModel.effects.first())
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
        onTransition: (AuthTransition) -> Unit = {},
    ): Pair<RegisterViewModel, FakeAuthPort> {
        val auth = FakeAuthPort()
        return RegisterViewModel(auth, AuthenticationStateMachine(auth) {}, onTransition) to auth
    }

    private data class AccountCall(val name: String, val email: String, val password: String)

    private class FakeAuthPort : NativeAuthPort {
        val accounts = mutableListOf<AccountCall>()
        private var authCallback: AuthCallback? = null

        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) {
            accounts += AccountCall(name, email, password)
            authCallback = done
        }

        fun complete(result: AuthResult) = authCallback!!.complete(result)

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
