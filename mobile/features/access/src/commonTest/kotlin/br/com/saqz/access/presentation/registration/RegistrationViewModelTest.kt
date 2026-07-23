package br.com.saqz.access.presentation.registration

import androidx.lifecycle.SavedStateHandle
import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthResult
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.NativeFailureCode
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.presentation.AuthScreen
import br.com.saqz.access.presentation.AuthUiError
import br.com.saqz.access.presentation.AuthenticationIntent
import br.com.saqz.access.presentation.AuthenticationStateMachine
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
class RegistrationViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `state projects the shared authentication form fields`() = runTest(mainDispatcher) {
        val (viewModel, _) = fixture()

        viewModel.onIntent(RegistrationIntent.UpdateName("Person Name"))
        viewModel.onIntent(RegistrationIntent.UpdateEmail("person@example.test"))
        viewModel.onIntent(RegistrationIntent.UpdatePassword("secret-value"))
        runCurrent()

        assertEquals("Person Name", viewModel.state.value.name)
        assertEquals("person@example.test", viewModel.state.value.email)
        assertEquals("secret-value", viewModel.state.value.password)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `submit registration forwards captured name email and password`() = runTest(mainDispatcher) {
        val (viewModel, auth) = fixture()
        viewModel.onIntent(RegistrationIntent.UpdateName("Person Name"))
        viewModel.onIntent(RegistrationIntent.UpdateEmail("person@example.test"))
        viewModel.onIntent(RegistrationIntent.UpdatePassword("secret-value"))

        viewModel.onIntent(RegistrationIntent.SubmitRegistration)
        runCurrent()

        assertEquals(
            listOf(CreateAccountCall("Person Name", "person@example.test", "secret-value")),
            auth.createAccounts,
        )
        assertTrue(viewModel.state.value.isLoading)
    }

    @Test
    fun `submit google login starts the provider flow`() = runTest(mainDispatcher) {
        val (viewModel, auth) = fixture()

        viewModel.onIntent(RegistrationIntent.SubmitGoogleLogin)
        runCurrent()

        assertEquals(1, auth.googleCalls)
    }

    @Test
    fun `account creation failure surfaces as an auth error`() = runTest(mainDispatcher) {
        val (viewModel, auth) = fixture()
        viewModel.onIntent(RegistrationIntent.UpdateName("Person Name"))
        viewModel.onIntent(RegistrationIntent.UpdateEmail("person@example.test"))
        viewModel.onIntent(RegistrationIntent.UpdatePassword("secret-value"))
        viewModel.onIntent(RegistrationIntent.SubmitRegistration)

        auth.completeCreateAccount(AuthResult.Failure(NativeFailureCode.EMAIL_IN_USE))
        runCurrent()

        assertEquals(AuthUiError.EMAIL_IN_USE, viewModel.state.value.error)
    }

    @Test
    fun `show login intent switches the shared authentication screen`() = runTest(mainDispatcher) {
        val machine = AuthenticationStateMachine(FakeAuthPort()) {}
        machine.onIntent(AuthenticationIntent.ShowRegistration)
        val viewModel = RegistrationViewModel(machine)

        viewModel.onIntent(RegistrationIntent.ShowLogin)

        assertEquals(AuthScreen.LOGIN, machine.state.value.screen)
    }

    @Test
    fun `in-progress input restores after process death and excludes the password`() = runTest(mainDispatcher) {
        val handle = SavedStateHandle()
        val (first, _) = fixture(handle = handle)
        runCurrent()
        first.onIntent(RegistrationIntent.UpdateName("Person Name"))
        first.onIntent(RegistrationIntent.UpdateEmail("person@example.test"))
        first.onIntent(RegistrationIntent.UpdatePassword("secret-value"))
        runCurrent()

        val restoredHandle = SavedStateHandle.createHandle(handle.savedStateProvider().saveState(), null)
        val (second, _) = fixture(handle = restoredHandle)
        runCurrent()

        assertEquals("Person Name", second.state.value.name)
        assertEquals("person@example.test", second.state.value.email)
        assertEquals("", second.state.value.password)
    }

    @Test
    fun `restored invalid input shows corrective state and never auto-submits`() = runTest(mainDispatcher) {
        val handle = SavedStateHandle()
        val (first, _) = fixture(handle = handle)
        runCurrent()
        first.onIntent(RegistrationIntent.UpdateName("Person Name"))
        first.onIntent(RegistrationIntent.UpdateEmail("invalid-email"))
        runCurrent()

        val restoredHandle = SavedStateHandle.createHandle(handle.savedStateProvider().saveState(), null)
        val (second, secondAuth) = fixture(handle = restoredHandle)
        runCurrent()
        assertEquals("invalid-email", second.state.value.email)
        assertTrue(secondAuth.createAccounts.isEmpty())

        second.onIntent(RegistrationIntent.SubmitRegistration)
        runCurrent()

        assertTrue(second.state.value.validationAttempted)
        assertTrue(secondAuth.createAccounts.isEmpty())
        assertFalse(second.state.value.isLoading)
    }

    private fun fixture(handle: SavedStateHandle = SavedStateHandle()): Pair<RegistrationViewModel, FakeAuthPort> {
        val auth = FakeAuthPort()
        return RegistrationViewModel(AuthenticationStateMachine(auth) {}, handle) to auth
    }

    private data class CreateAccountCall(val name: String, val email: String, val password: String)

    private class FakeAuthPort : NativeAuthPort {
        val createAccounts = mutableListOf<CreateAccountCall>()
        var googleCalls = 0
        private var authCallback: AuthCallback? = null

        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) {
            createAccounts += CreateAccountCall(name, email, password)
            authCallback = done
        }

        override fun signInWithGoogle(done: AuthCallback) {
            googleCalls += 1
            authCallback = done
        }

        fun completeCreateAccount(result: AuthResult) = authCallback!!.complete(result)

        override fun observe(listener: AuthStateListener): Cancelable = object : Cancelable { override fun cancel() = Unit }
        override fun signInWithPassword(email: String, password: String, done: AuthCallback) = Unit
        override fun sendVerification(done: ResultCallback) = Unit
        override fun reloadUser(done: AuthCallback) = Unit
        override fun sendPasswordReset(email: String, done: ResultCallback) = Unit
        override fun updateDisplayName(name: String, done: AuthCallback) = Unit
        override fun idToken(forceRefresh: Boolean, done: TokenCallback) = Unit
        override fun signOut(done: ResultCallback) = Unit
    }
}
