package br.com.saqz.access.presentation.login

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.presentation.AuthenticationStateMachine
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.login_error_blocked
import br.com.saqz.access.resources.login_error_credentials
import br.com.saqz.access.resources.login_error_email_invalid
import br.com.saqz.access.resources.login_error_password
import br.com.saqz.designsystem.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `state projects the shared authentication form fields`() = runTest(mainDispatcher) {
        val (viewModel, _) = fixture()

        viewModel.onIntent(LoginIntent.UpdateEmail("person@example.test"))
        viewModel.onIntent(LoginIntent.UpdatePassword("secret-value"))
        runCurrent()

        assertEquals("person@example.test", viewModel.state.value.email)
        assertEquals("secret-value", viewModel.state.value.password)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `submit password login forwards email and captured password`() = runTest(mainDispatcher) {
        val (viewModel, auth) = fixture()
        viewModel.onIntent(LoginIntent.UpdateEmail("person@example.test"))
        viewModel.onIntent(LoginIntent.UpdatePassword("secret-value"))

        viewModel.onIntent(LoginIntent.SubmitPasswordLogin)
        runCurrent()

        assertEquals(listOf(LoginCall("person@example.test", "secret-value")), auth.logins)
        assertTrue(viewModel.state.value.isLoading)
    }

    @Test
    fun `submit google login starts the provider flow`() = runTest(mainDispatcher) {
        val (viewModel, auth) = fixture()

        viewModel.onIntent(LoginIntent.SubmitGoogleLogin)
        runCurrent()

        assertEquals(1, auth.googleCalls)
    }

    // A recusa da 1a fala a língua do export, e não o `auth_error_invalid_credentials`
    // seco que o resto do acesso reusa.
    @Test
    fun `invalid credentials surface the export copy and mark the password field`() = runTest(mainDispatcher) {
        val (viewModel, auth) = fixture(email = "person@example.test")
        viewModel.onIntent(LoginIntent.UpdatePassword("wrong"))
        viewModel.onIntent(LoginIntent.SubmitPasswordLogin)

        auth.completeAuth(AuthResult.Failure(NativeFailureCode.INVALID_CREDENTIALS))
        runCurrent()

        assertEquals(UiText.Res(Res.string.login_error_credentials), viewModel.state.value.error)
        assertEquals(UiText.Res(Res.string.login_error_password), viewModel.state.value.passwordError)
    }

    // E-mail malformado não vira ida ao provedor: a recusa dele voltaria como credencial
    // inválida e cobraria uma tentativa por um erro que a tela já sabia ver sozinha.
    @Test
    fun `malformed email fails locally without reaching the provider`() = runTest(mainDispatcher) {
        val (viewModel, auth) = fixture()
        viewModel.onIntent(LoginIntent.UpdateEmail("ana@exemplo"))
        viewModel.onIntent(LoginIntent.UpdatePassword("secret-value"))

        viewModel.onIntent(LoginIntent.SubmitPasswordLogin)
        runCurrent()

        assertEquals(emptyList(), auth.logins)
        assertEquals(UiText.Res(Res.string.login_error_email_invalid), viewModel.state.value.emailError)
        assertEquals(0, viewModel.state.value.failedAttempts)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `correcting the email clears its field error`() = runTest(mainDispatcher) {
        val (viewModel, _) = fixture()
        viewModel.onIntent(LoginIntent.UpdateEmail("ana@exemplo"))
        viewModel.onIntent(LoginIntent.SubmitPasswordLogin)
        runCurrent()

        viewModel.onIntent(LoginIntent.UpdateEmail("ana@exemplo.com"))
        runCurrent()

        assertNull(viewModel.state.value.emailError)
    }

    // O contador é cosmético e conta só recusa de credencial: cada envio recusado soma um,
    // e uma reemissão do mesmo estado não soma nada.
    @Test
    fun `the cosmetic counter advances once per refused attempt`() = runTest(mainDispatcher) {
        val (viewModel, auth) = fixture(email = "person@example.test")

        // `runCurrent` entre os passos porque é o que a tela faz: a pessoa digita, um
        // quadro desenha, a pessoa toca. Sem isso o `StateFlow` conflacia a digitação, o
        // envio e a recusa numa emissão só — e a segunda rodada acaba num valor igual ao
        // da primeira, que `StateFlow` nem emite.
        repeat(2) {
            viewModel.onIntent(LoginIntent.UpdatePassword("wrong"))
            runCurrent()
            viewModel.onIntent(LoginIntent.SubmitPasswordLogin)
            runCurrent()
            auth.completeAuth(AuthResult.Failure(NativeFailureCode.INVALID_CREDENTIALS))
            runCurrent()
        }

        assertEquals(2, viewModel.state.value.failedAttempts)
    }

    @Test
    fun `network failures never advance the counter`() = runTest(mainDispatcher) {
        val (viewModel, auth) = fixture(email = "person@example.test")
        viewModel.onIntent(LoginIntent.UpdatePassword("secret-value"))
        viewModel.onIntent(LoginIntent.SubmitPasswordLogin)

        auth.completeAuth(AuthResult.Failure(NativeFailureCode.NETWORK_UNAVAILABLE))
        runCurrent()

        assertEquals(0, viewModel.state.value.failedAttempts)
        assertNull(viewModel.state.value.passwordError)
    }

    // O bloqueio de verdade é o do provedor. Quando ele chega, o contador cosmético sai de
    // cena — insistir em "errou 2 de 5" ao lado de uma conta já bloqueada seria mentira.
    @Test
    fun `the provider block replaces the counter with the blocked message`() = runTest(mainDispatcher) {
        val (viewModel, auth) = fixture(email = "person@example.test")
        viewModel.onIntent(LoginIntent.UpdatePassword("wrong"))
        viewModel.onIntent(LoginIntent.SubmitPasswordLogin)
        auth.completeAuth(AuthResult.Failure(NativeFailureCode.INVALID_CREDENTIALS))
        runCurrent()
        assertEquals(1, viewModel.state.value.failedAttempts)

        viewModel.onIntent(LoginIntent.UpdatePassword("wrong-again"))
        viewModel.onIntent(LoginIntent.SubmitPasswordLogin)
        auth.completeAuth(AuthResult.Failure(NativeFailureCode.TOO_MANY_REQUESTS))
        runCurrent()

        assertEquals(0, viewModel.state.value.failedAttempts)
        assertEquals(UiText.Res(Res.string.login_error_blocked), viewModel.state.value.error)
    }

    @Test
    fun `email shape is judged by form and not by existence`() {
        assertTrue(looksLikeEmail("ana@exemplo.com"))
        assertTrue(looksLikeEmail("  ana.souza+time@exemplo.com.br  "))
        assertFalse(looksLikeEmail(""))
        assertFalse(looksLikeEmail("ana@exemplo"))
        assertFalse(looksLikeEmail("@exemplo.com"))
        assertFalse(looksLikeEmail("ana@@exemplo.com"))
        assertFalse(looksLikeEmail("ana exemplo@teste.com"))
        assertFalse(looksLikeEmail("ana@exemplo."))
        assertFalse(looksLikeEmail("ana@.com"))
    }

    private fun fixture(email: String = ""): Pair<LoginViewModel, FakeAuthPort> {
        val auth = FakeAuthPort()
        val viewModel = LoginViewModel(AuthenticationStateMachine(auth) {})
        if (email.isNotEmpty()) viewModel.onIntent(LoginIntent.UpdateEmail(email))
        return viewModel to auth
    }

    private data class LoginCall(val email: String, val password: String)

    private class FakeAuthPort : NativeAuthPort {
        val logins = mutableListOf<LoginCall>()
        var googleCalls = 0
        private var authCallback: AuthCallback? = null

        override fun signInWithPassword(email: String, password: String, done: AuthCallback) {
            logins += LoginCall(email, password)
            authCallback = done
        }

        override fun signInWithGoogle(done: AuthCallback) {
            googleCalls += 1
            authCallback = done
        }

        fun completeAuth(result: AuthResult) = authCallback!!.complete(result)

        override fun observe(listener: AuthStateListener): Cancelable = object : Cancelable { override fun cancel() = Unit }
        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) = Unit
        override fun sendVerification(done: ResultCallback) = Unit
        override fun reloadUser(done: AuthCallback) = Unit
        override fun updateDisplayName(name: String, done: AuthCallback) = Unit
        override fun idToken(forceRefresh: Boolean, done: TokenCallback) = Unit
        override fun signOut(done: ResultCallback) = Unit
    }
}
