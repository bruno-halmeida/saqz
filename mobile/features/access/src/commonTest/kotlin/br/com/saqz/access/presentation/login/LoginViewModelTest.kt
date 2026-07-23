package br.com.saqz.access.presentation.login

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.presentation.AuthScreen
import br.com.saqz.access.presentation.AuthTransition
import br.com.saqz.access.presentation.AuthenticationStateMachine
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.auth_error_invalid_credentials
import br.com.saqz.designsystem.text.UiText
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

    @Test
    fun `invalid credentials surface as localized UiText`() = runTest(mainDispatcher) {
        val (viewModel, auth) = fixture()
        viewModel.onIntent(LoginIntent.UpdatePassword("wrong"))
        viewModel.onIntent(LoginIntent.SubmitPasswordLogin)

        auth.completeAuth(AuthResult.Failure(NativeFailureCode.INVALID_CREDENTIALS))
        runCurrent()

        assertEquals(UiText.Res(Res.string.auth_error_invalid_credentials), viewModel.state.value.error)
    }

    @Test
    fun `navigation intents switch the shared authentication screen`() = runTest(mainDispatcher) {
        val (viewModel, machine) = fixtureWithMachine()

        viewModel.onIntent(LoginIntent.ShowRegistration)
        assertEquals(AuthScreen.REGISTRATION, machine.state.value.screen)

        viewModel.onIntent(LoginIntent.ShowPasswordReset)
        assertEquals(AuthScreen.PASSWORD_RESET, machine.state.value.screen)
    }

    private fun fixture(): Pair<LoginViewModel, FakeAuthPort> {
        val auth = FakeAuthPort()
        return LoginViewModel(AuthenticationStateMachine(auth) {}) to auth
    }

    private fun fixtureWithMachine(): Pair<LoginViewModel, AuthenticationStateMachine> {
        val machine = AuthenticationStateMachine(FakeAuthPort()) {}
        return LoginViewModel(machine) to machine
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
        override fun sendPasswordReset(email: String, done: ResultCallback) = Unit
        override fun updateDisplayName(name: String, done: AuthCallback) = Unit
        override fun idToken(forceRefresh: Boolean, done: TokenCallback) = Unit
        override fun signOut(done: ResultCallback) = Unit
    }
}
