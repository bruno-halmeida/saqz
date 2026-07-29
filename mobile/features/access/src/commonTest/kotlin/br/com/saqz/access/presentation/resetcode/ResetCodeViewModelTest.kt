package br.com.saqz.access.presentation.resetcode

import br.com.saqz.access.domain.passwordreset.PasswordResetError
import br.com.saqz.access.domain.passwordreset.PasswordResetGateway
import br.com.saqz.access.domain.passwordreset.PasswordResetTicket
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
class ResetCodeViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `countdown starts at the server resend window`() = runTest(mainDispatcher) {
        val (viewModel, _) = fixture()
        runCurrent()

        assertEquals(RESET_CODE_RESEND_SECONDS, viewModel.state.value.resendSeconds)
        assertFalse(viewModel.state.value.canResend)
    }

    @Test
    fun `countdown derives the remaining seconds from the deadline`() = runTest(mainDispatcher) {
        val (viewModel, _) = fixture()
        runCurrent()

        elapse(1_000)
        assertEquals(59, viewModel.state.value.resendSeconds)

        // O buraco é o caso real da tela: a pessoa saiu para o app de e-mail e voltou.
        // Um contador que decrementa perderia os 40 tiques; este deriva a diferença.
        elapse(40_000)
        assertEquals(19, viewModel.state.value.resendSeconds)
    }

    @Test
    fun `countdown floors at zero and frees the resend`() = runTest(mainDispatcher) {
        val (viewModel, _) = fixture()

        elapse((RESET_CODE_RESEND_SECONDS + 5) * 1_000L)

        assertEquals(0, viewModel.state.value.resendSeconds)
        assertTrue(viewModel.state.value.canResend)
    }

    @Test
    fun `resend is ignored while the countdown runs`() = runTest(mainDispatcher) {
        val (viewModel, gateway) = fixture()

        viewModel.onIntent(ResetCodeIntent.Resend)
        runCurrent()

        assertEquals(0, gateway.requested.size)
        assertFalse(viewModel.state.value.resent)
    }

    @Test
    fun `resend restarts the window and clears the code and raises the alert`() = runTest(mainDispatcher) {
        val (viewModel, gateway) = fixture()
        elapse(RESET_CODE_RESEND_SECONDS * 1_000L)
        viewModel.onIntent(ResetCodeIntent.UpdateCode("1359"))

        viewModel.onIntent(ResetCodeIntent.Resend)
        runCurrent()

        assertEquals(listOf("ana@exemplo.com"), gateway.requested)
        val state = viewModel.state.value
        assertTrue(state.resent)
        assertEquals("", state.code)
        assertEquals(RESET_CODE_RESEND_SECONDS, state.resendSeconds)
        assertFalse(state.canResend)
    }

    @Test
    fun `resend rate limited adopts the window the server asked for`() = runTest(mainDispatcher) {
        val (viewModel, gateway) = fixture()
        gateway.requestResult = SaqzResult.Failure(PasswordResetError.RateLimited(retryAfterSeconds = 25))
        elapse(RESET_CODE_RESEND_SECONDS * 1_000L)

        viewModel.onIntent(ResetCodeIntent.Resend)
        runCurrent()

        assertEquals(25, viewModel.state.value.resendSeconds)
        // Sem alerta: o contador de volta já é a explicação.
        assertFalse(viewModel.state.value.resent)
        assertNull(viewModel.state.value.failure)
    }

    @Test
    fun `verify rate limited waits on its own bucket and leaves the resend alone`() = runTest(mainDispatcher) {
        val (viewModel, gateway) = fixture()
        gateway.verifyResult = SaqzResult.Failure(PasswordResetError.RateLimited(retryAfterSeconds = 30))
        // Janela de reenvio já vencida: é o caso que prova a separação — misturar os dois
        // baldes tiraria da pessoa a saída que o servidor não recusou.
        elapse(RESET_CODE_RESEND_SECONDS * 1_000L)
        viewModel.onIntent(ResetCodeIntent.UpdateCode("1359"))

        viewModel.onIntent(ResetCodeIntent.Verify)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(30, state.verifyRetrySeconds)
        assertFalse(state.canVerify)
        assertEquals(0, state.resendSeconds)
        assertTrue(state.canResend)
    }

    @Test
    fun `verify is ignored while its own window runs`() = runTest(mainDispatcher) {
        val (viewModel, gateway) = fixture()
        gateway.verifyResult = SaqzResult.Failure(PasswordResetError.RateLimited(retryAfterSeconds = 30))
        viewModel.onIntent(ResetCodeIntent.UpdateCode("1359"))
        viewModel.onIntent(ResetCodeIntent.Verify)
        runCurrent()

        viewModel.onIntent(ResetCodeIntent.Verify)
        runCurrent()

        assertEquals(1, gateway.verified.size)
    }

    @Test
    fun `editing a digit drops the refusal of the code that was there before`() = runTest(mainDispatcher) {
        val (viewModel, gateway) = fixture()
        gateway.verifyResult = SaqzResult.Failure(PasswordResetError.CodeInvalid(remainingAttempts = 2))
        viewModel.onIntent(ResetCodeIntent.UpdateCode("1359"))
        viewModel.onIntent(ResetCodeIntent.Verify)
        runCurrent()

        viewModel.onIntent(ResetCodeIntent.UpdateCode("135"))

        // "Restam 2 tentativas" era sobre o código antigo; este ainda nem foi conferido.
        assertNull(viewModel.state.value.remainingAttempts)
    }

    @Test
    fun `editing a digit does not resurrect an expired code`() = runTest(mainDispatcher) {
        val (viewModel, gateway) = fixture()
        gateway.verifyResult = SaqzResult.Failure(PasswordResetError.CodeExpired)
        viewModel.onIntent(ResetCodeIntent.UpdateCode("1359"))
        viewModel.onIntent(ResetCodeIntent.Verify)
        runCurrent()

        viewModel.onIntent(ResetCodeIntent.UpdateCode("135"))

        // Quem expirou foi o código que o servidor mandou, não o que está sendo digitado.
        assertTrue(viewModel.state.value.expired)
    }

    @Test
    fun `verify is ignored while the code is incomplete`() = runTest(mainDispatcher) {
        val (viewModel, gateway) = fixture()
        viewModel.onIntent(ResetCodeIntent.UpdateCode("135"))

        viewModel.onIntent(ResetCodeIntent.Verify)
        runCurrent()

        assertEquals(0, gateway.verified.size)
        assertFalse(viewModel.state.value.verifying)
    }

    @Test
    fun `verify emits the ticket for the new password screen`() = runTest(mainDispatcher) {
        val (viewModel, _) = fixture()
        val effects = mutableListOf<ResetCodeEffect>()
        backgroundScope.launch { viewModel.effects.collect { effects += it } }
        viewModel.onIntent(ResetCodeIntent.UpdateCode("1359"))

        viewModel.onIntent(ResetCodeIntent.Verify)
        runCurrent()

        assertEquals(
            listOf<ResetCodeEffect>(ResetCodeEffect.OpenNewPassword("ana@exemplo.com", "ticket-de-troca")),
            effects,
        )
        assertFalse(viewModel.state.value.verifying)
    }

    @Test
    fun `invalid code keeps the digits and counts the attempts left`() = runTest(mainDispatcher) {
        val (viewModel, gateway) = fixture()
        gateway.verifyResult = SaqzResult.Failure(PasswordResetError.CodeInvalid(remainingAttempts = 2))
        viewModel.onIntent(ResetCodeIntent.UpdateCode("1359"))

        viewModel.onIntent(ResetCodeIntent.Verify)
        runCurrent()

        val state = viewModel.state.value
        assertEquals(2, state.remainingAttempts)
        assertEquals("1359", state.code)
        assertFalse(state.expired)
    }

    @Test
    fun `expired code drops the attempts line and asks for a new one`() = runTest(mainDispatcher) {
        val (viewModel, gateway) = fixture()
        gateway.verifyResult = SaqzResult.Failure(PasswordResetError.CodeExpired)
        viewModel.onIntent(ResetCodeIntent.UpdateCode("1359"))

        viewModel.onIntent(ResetCodeIntent.Verify)
        runCurrent()

        assertTrue(viewModel.state.value.expired)
        assertNull(viewModel.state.value.remainingAttempts)
    }

    @Test
    fun `attempt limit dies like an expired code`() = runTest(mainDispatcher) {
        val (viewModel, gateway) = fixture()
        gateway.verifyResult = SaqzResult.Failure(PasswordResetError.AttemptLimit)
        viewModel.onIntent(ResetCodeIntent.UpdateCode("1359"))

        viewModel.onIntent(ResetCodeIntent.Verify)
        runCurrent()

        assertTrue(viewModel.state.value.expired)
        assertNull(viewModel.state.value.remainingAttempts)
    }

    @Test
    fun `network failure surfaces a message instead of a refusal`() = runTest(mainDispatcher) {
        val (viewModel, gateway) = fixture()
        gateway.verifyResult =
            SaqzResult.Failure(PasswordResetError.DataFailure(DataError.Connectivity))
        viewModel.onIntent(ResetCodeIntent.UpdateCode("1359"))

        viewModel.onIntent(ResetCodeIntent.Verify)
        runCurrent()

        val state = viewModel.state.value
        assertFalse(state.expired)
        assertNull(state.remainingAttempts)
        assertTrue(state.failure != null)
    }

    @Test
    fun `resend after a refusal clears what the dead code had left behind`() = runTest(mainDispatcher) {
        val (viewModel, gateway) = fixture()
        gateway.verifyResult = SaqzResult.Failure(PasswordResetError.CodeExpired)
        viewModel.onIntent(ResetCodeIntent.UpdateCode("1359"))
        viewModel.onIntent(ResetCodeIntent.Verify)
        elapse(RESET_CODE_RESEND_SECONDS * 1_000L)

        viewModel.onIntent(ResetCodeIntent.Resend)
        runCurrent()

        val state = viewModel.state.value
        assertFalse(state.expired)
        assertNull(state.remainingAttempts)
        assertTrue(state.resent)
    }

    @Test
    fun `countdown is written the way the export shows it`() {
        assertEquals("1:00", formatResendCountdown(60))
        assertEquals("0:59", formatResendCountdown(59))
        assertEquals("0:42", formatResendCountdown(42))
        assertEquals("0:00", formatResendCountdown(0))
        assertEquals("0:00", formatResendCountdown(-1))
    }

    // `advanceTimeBy` roda o que está agendado **antes** do instante alvo, e o tique do
    // contador cai exatamente nele; o `runCurrent` é quem o executa.
    private fun TestScope.elapse(millis: Long) {
        advanceTimeBy(millis)
        runCurrent()
    }

    private fun TestScope.fixture(): Pair<ResetCodeViewModel, FakePasswordResetGateway> {
        val gateway = FakePasswordResetGateway()
        val viewModel = ResetCodeViewModel(
            email = "ana@exemplo.com",
            gateway = gateway,
            // O relógio do contador é o do escalonador virtual: 60 segundos passam sem
            // que o teste espere 60 segundos.
            elapsedMillis = { testScheduler.currentTime },
        )
        return viewModel to gateway
    }
}

private class FakePasswordResetGateway : PasswordResetGateway {
    val requested = mutableListOf<String>()
    val verified = mutableListOf<Pair<String, String>>()

    var requestResult: SaqzResult<Unit, PasswordResetError> = SaqzResult.Success(Unit)
    var verifyResult: SaqzResult<PasswordResetTicket, PasswordResetError> =
        SaqzResult.Success(PasswordResetTicket(token = "ticket-de-troca", expiresInSeconds = 900))

    override suspend fun requestCode(email: String): SaqzResult<Unit, PasswordResetError> {
        requested += email
        return requestResult
    }

    override suspend fun verifyCode(
        email: String,
        code: String,
    ): SaqzResult<PasswordResetTicket, PasswordResetError> {
        verified += email to code
        return verifyResult
    }

    override suspend fun confirm(
        token: String,
        newPassword: String,
    ): SaqzResult<Unit, PasswordResetError> = SaqzResult.Success(Unit)
}
