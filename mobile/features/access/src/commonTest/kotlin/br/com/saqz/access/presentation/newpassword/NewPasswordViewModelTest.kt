package br.com.saqz.access.presentation.newpassword

import br.com.saqz.access.domain.passwordreset.PasswordResetError
import br.com.saqz.access.domain.passwordreset.PasswordResetGateway
import br.com.saqz.access.domain.passwordreset.PasswordResetTicket
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.auth_error_network
import br.com.saqz.access.resources.login_error_password
import br.com.saqz.access.resources.register_error_password
import br.com.saqz.designsystem.UiText
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

/**
 * As duas regras que o 1g promete: nada vai ao servidor sem senha de 8 e sem as duas
 * iguais; e ticket morto tem saída — o 1e de novo, nunca um erro sem botão.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NewPasswordViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `short password never reaches the gateway`() = runTest(mainDispatcher) {
        val (viewModel, gateway) = fixture()
        viewModel.type(password = "curta7!", confirmation = "curta7!")

        viewModel.onIntent(NewPasswordIntent.Submit)
        runCurrent()

        assertTrue(gateway.confirmations.isEmpty(), "senha curta não gasta viagem ao servidor")
        assertEquals(UiText.Res(Res.string.register_error_password), viewModel.state.value.passwordError)
        assertNull(viewModel.state.value.confirmationError)
    }

    @Test
    fun `mismatched confirmation never reaches the gateway`() = runTest(mainDispatcher) {
        val (viewModel, gateway) = fixture()
        viewModel.type(password = "senha-boa-8", confirmation = "senha-boa-9")

        viewModel.onIntent(NewPasswordIntent.Submit)
        runCurrent()

        assertTrue(gateway.confirmations.isEmpty(), "senhas diferentes não gastam viagem ao servidor")
        assertEquals(UiText.Res(Res.string.login_error_password), viewModel.state.value.confirmationError)
        assertNull(viewModel.state.value.passwordError)
    }

    // A recusa fala do que foi enviado; voltar a digitar a apaga, senão a linha vermelha
    // sobrevive à correção e vira mentira.
    @Test
    fun `typing clears the refusal of the field`() = runTest(mainDispatcher) {
        val (viewModel, _) = fixture()
        viewModel.type(password = "curta", confirmation = "curta")
        viewModel.onIntent(NewPasswordIntent.Submit)
        runCurrent()

        viewModel.onIntent(NewPasswordIntent.UpdatePassword("senha-longa-o-bastante"))
        runCurrent()

        assertNull(viewModel.state.value.passwordError)
    }

    @Test
    fun `valid pair goes to the gateway with the route token`() = runTest(mainDispatcher) {
        val (viewModel, gateway) = fixture()
        viewModel.type(password = "senha-nova-8", confirmation = "senha-nova-8")

        viewModel.onIntent(NewPasswordIntent.Submit)
        runCurrent()

        assertEquals(listOf("ticket-do-reset" to "senha-nova-8"), gateway.confirmations)
        assertEquals(NewPasswordEffect.Saved, viewModel.effects.first())
        assertEquals(false, viewModel.state.value.isSaving)
    }

    // O caso torto do ticket: o token morreu entre o 1e e o salvar. Quatro recusas dizem
    // a mesma coisa, e todas devolvem ao 1e — nenhuma deixa a pessoa num erro sem botão.
    @Test
    fun `every dead ticket asks for a new code`() = runTest(mainDispatcher) {
        listOf(
            PasswordResetError.TokenInvalid,
            PasswordResetError.CodeExpired,
            PasswordResetError.AttemptLimit,
            PasswordResetError.CodeInvalid(remainingAttempts = 0),
        ).forEach { refusal ->
            val (viewModel, _) = fixture(refusal)
            viewModel.type(password = "senha-nova-8", confirmation = "senha-nova-8")

            viewModel.onIntent(NewPasswordIntent.Submit)
            runCurrent()

            assertEquals(NewPasswordEffect.CodeRestartNeeded, viewModel.effects.first(), "$refusal")
            assertNull(viewModel.state.value.alert, "$refusal não deveria virar alerta preso na tela")
        }
    }

    @Test
    fun `connectivity failure stays on the screen as an alert`() = runTest(mainDispatcher) {
        val (viewModel, _) = fixture(PasswordResetError.DataFailure(DataError.Connectivity))
        viewModel.type(password = "senha-nova-8", confirmation = "senha-nova-8")

        viewModel.onIntent(NewPasswordIntent.Submit)
        runCurrent()

        assertEquals(UiText.Res(Res.string.auth_error_network), viewModel.state.value.alert)
        assertEquals(false, viewModel.state.value.isSaving)
    }

    private fun NewPasswordViewModel.type(password: String, confirmation: String) {
        onIntent(NewPasswordIntent.UpdatePassword(password))
        onIntent(NewPasswordIntent.UpdateConfirmation(confirmation))
    }

    private fun fixture(refusal: PasswordResetError? = null): Pair<NewPasswordViewModel, FakeGateway> {
        val gateway = FakeGateway(refusal)
        return NewPasswordViewModel("ticket-do-reset", gateway) to gateway
    }
}

private class FakeGateway(private val refusal: PasswordResetError?) : PasswordResetGateway {
    val confirmations = mutableListOf<Pair<String, String>>()

    override suspend fun requestCode(email: String) = SaqzResult.Success(Unit)

    override suspend fun verifyCode(email: String, code: String) =
        SaqzResult.Success(PasswordResetTicket(token = "ticket-do-reset", expiresInSeconds = 900))

    override suspend fun confirm(token: String, newPassword: String): SaqzResult<Unit, PasswordResetError> {
        confirmations += token to newPassword
        return refusal?.let { SaqzResult.Failure(it) } ?: SaqzResult.Success(Unit)
    }
}
