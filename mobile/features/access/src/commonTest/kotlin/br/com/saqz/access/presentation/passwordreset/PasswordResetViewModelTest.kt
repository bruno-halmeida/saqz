package br.com.saqz.access.presentation.passwordreset

import br.com.saqz.access.domain.port.AuthCallback
import br.com.saqz.access.domain.port.AuthStateListener
import br.com.saqz.access.domain.port.Cancelable
import br.com.saqz.access.domain.port.NativeAuthPort
import br.com.saqz.access.domain.port.OperationResult
import br.com.saqz.access.domain.port.ResultCallback
import br.com.saqz.access.domain.port.TokenCallback
import br.com.saqz.access.presentation.AuthScreen
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordResetViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `state projects the shared reset email field`() = runTest(mainDispatcher) {
        val (viewModel, _) = fixture()

        viewModel.onIntent(PasswordResetIntent.UpdateEmail("person@example.test"))
        runCurrent()

        assertEquals("person@example.test", viewModel.state.value.email)
        assertFalse(viewModel.state.value.resetConfirmation)
    }

    @Test
    fun `submit reset forwards the email and enters loading`() = runTest(mainDispatcher) {
        val (viewModel, auth) = fixture()
        viewModel.onIntent(PasswordResetIntent.UpdateEmail("person@example.test"))

        viewModel.onIntent(PasswordResetIntent.SubmitPasswordReset)
        runCurrent()

        assertEquals(listOf("person@example.test"), auth.resets)
        assertTrue(viewModel.state.value.isLoading)
    }

    @Test
    fun `completed reset surfaces the confirmation state`() = runTest(mainDispatcher) {
        val (viewModel, auth) = fixture()
        viewModel.onIntent(PasswordResetIntent.UpdateEmail("person@example.test"))
        viewModel.onIntent(PasswordResetIntent.SubmitPasswordReset)

        auth.completeReset(OperationResult.Success)
        runCurrent()

        assertTrue(viewModel.state.value.resetConfirmation)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `show login intent switches the shared authentication screen`() = runTest(mainDispatcher) {
        val machine = AuthenticationStateMachine(FakeAuthPort()) {}
        machine.onIntent(AuthenticationIntent.ShowPasswordReset)
        val viewModel = PasswordResetViewModel(machine)

        viewModel.onIntent(PasswordResetIntent.ShowLogin)

        assertEquals(AuthScreen.LOGIN, machine.state.value.screen)
    }

    @Test
    fun `invalid email shows corrective state and never submits`() = runTest(mainDispatcher) {
        val (viewModel, auth) = fixture()
        viewModel.onIntent(PasswordResetIntent.UpdateEmail("invalid-email"))

        viewModel.onIntent(PasswordResetIntent.SubmitPasswordReset)
        runCurrent()

        assertTrue(viewModel.state.value.validationAttempted)
        assertTrue(auth.resets.isEmpty())
        assertFalse(viewModel.state.value.isLoading)
    }

    private fun fixture(): Pair<PasswordResetViewModel, FakeAuthPort> {
        val auth = FakeAuthPort()
        return PasswordResetViewModel(AuthenticationStateMachine(auth) {}) to auth
    }

    private class FakeAuthPort : NativeAuthPort {
        val resets = mutableListOf<String>()
        private var resetCallback: ResultCallback? = null

        override fun sendPasswordReset(email: String, done: ResultCallback) {
            resets += email
            resetCallback = done
        }

        fun completeReset(result: OperationResult) = resetCallback!!.complete(result)

        override fun observe(listener: AuthStateListener): Cancelable = object : Cancelable { override fun cancel() = Unit }
        override fun createAccount(name: String, email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithPassword(email: String, password: String, done: AuthCallback) = Unit
        override fun signInWithGoogle(done: AuthCallback) = Unit
        override fun sendVerification(done: ResultCallback) = Unit
        override fun reloadUser(done: AuthCallback) = Unit
        override fun updateDisplayName(name: String, done: AuthCallback) = Unit
        override fun idToken(forceRefresh: Boolean, done: TokenCallback) = Unit
        override fun signOut(done: ResultCallback) = Unit
    }
}
