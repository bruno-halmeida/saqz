package br.com.saqz.access.presentation.forgotpassword

import br.com.saqz.access.domain.passwordreset.PasswordResetError
import br.com.saqz.access.domain.passwordreset.PasswordResetGateway
import br.com.saqz.access.domain.passwordreset.PasswordResetTicket
import br.com.saqz.access.resources.Res
import br.com.saqz.access.resources.auth_error_network
import br.com.saqz.access.resources.invite_rate_limit
import br.com.saqz.access.resources.login_error_email_invalid
import br.com.saqz.designsystem.UiText
import br.com.saqz.domain.DataError
import br.com.saqz.domain.SaqzResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
class ForgotPasswordViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `pedido aceito navega com o e-mail digitado`() = runTest(mainDispatcher) {
        val gateway = FakeGateway(SaqzResult.Success(Unit))
        val viewModel = ForgotPasswordViewModel(gateway)
        val effects = collectEffects(viewModel)

        viewModel.onIntent(ForgotPasswordIntent.UpdateEmail(" ana@exemplo.com "))
        viewModel.onIntent(ForgotPasswordIntent.Submit)
        runCurrent()

        assertEquals(listOf("ana@exemplo.com"), gateway.requested)
        assertEquals(listOf(ForgotPasswordEffect.CodeRequested("ana@exemplo.com")), effects)
        assertEquals(false, viewModel.state.value.isSubmitting)
        assertNull(viewModel.state.value.error)
    }

    /**
     * O endpoint responde 202 mesmo para e-mail sem conta, e este teste é a trava disso:
     * o mesmo `Success` do caso acima, e o resultado tem de ser indistinguível — mesmo
     * efeito, mesmo estado, nenhuma mensagem nova.
     */
    @Test
    fun `e-mail sem conta segue igual ao que tem conta`() = runTest(mainDispatcher) {
        val gateway = FakeGateway(SaqzResult.Success(Unit))
        val viewModel = ForgotPasswordViewModel(gateway)
        val effects = collectEffects(viewModel)

        viewModel.onIntent(ForgotPasswordIntent.UpdateEmail("ninguem@exemplo.com"))
        viewModel.onIntent(ForgotPasswordIntent.Submit)
        runCurrent()

        assertEquals(listOf(ForgotPasswordEffect.CodeRequested("ninguem@exemplo.com")), effects)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `falha de rede fica na tela e nao navega`() = runTest(mainDispatcher) {
        val gateway = FakeGateway(SaqzResult.Failure(PasswordResetError.DataFailure(DataError.Connectivity)))
        val viewModel = ForgotPasswordViewModel(gateway)
        val effects = collectEffects(viewModel)

        viewModel.onIntent(ForgotPasswordIntent.UpdateEmail("ana@exemplo.com"))
        viewModel.onIntent(ForgotPasswordIntent.Submit)
        runCurrent()

        assertEquals(emptyList(), effects)
        assertEquals(UiText.Res(Res.string.auth_error_network), viewModel.state.value.error)
        assertEquals(false, viewModel.state.value.isSubmitting)
        // O e-mail continua no campo: a pessoa tenta de novo sem redigitar.
        assertEquals("ana@exemplo.com", viewModel.state.value.email)
    }

    /**
     * O campo trava durante o envio, mas travar é recomposição: texto enfileirado antes
     * disso ainda chega. A resposta que volta é de um endereço que não está mais na tela,
     * e navegar com ele mandaria o código para um lugar e mostraria outro.
     */
    @Test
    fun `resposta de um e-mail que mudou no meio do envio nao navega`() = runTest(mainDispatcher) {
        val gateway = SuspendingGateway()
        val viewModel = ForgotPasswordViewModel(gateway)
        val effects = collectEffects(viewModel)

        viewModel.onIntent(ForgotPasswordIntent.UpdateEmail("ana@exemplo.com"))
        viewModel.onIntent(ForgotPasswordIntent.Submit)
        runCurrent()
        viewModel.onIntent(ForgotPasswordIntent.UpdateEmail("bruna@exemplo.com"))
        gateway.complete(SaqzResult.Success(Unit))
        runCurrent()

        assertEquals(emptyList(), effects)
        assertEquals("bruna@exemplo.com", viewModel.state.value.email)
        // Nem navega nem acusa erro: some do caminho e deixa a pessoa reenviar.
        assertNull(viewModel.state.value.error)
        assertEquals(false, viewModel.state.value.isSubmitting)
    }

    @Test
    fun `recusa por espera diz quantos segundos em vez de culpar a conexao`() = runTest(mainDispatcher) {
        val gateway = FakeGateway(SaqzResult.Failure(PasswordResetError.RateLimited(retryAfterSeconds = 45)))
        val viewModel = ForgotPasswordViewModel(gateway)

        viewModel.onIntent(ForgotPasswordIntent.UpdateEmail("ana@exemplo.com"))
        viewModel.onIntent(ForgotPasswordIntent.Submit)
        runCurrent()

        assertEquals(
            UiText.Res(Res.string.invite_rate_limit, listOf(45)),
            viewModel.state.value.error,
        )
    }

    // Sem número não há o que dizer sobre a espera; cai na mensagem genérica em vez de
    // prometer "tente de novo em 0 segundos".
    @Test
    fun `recusa por espera sem segundos cai na mensagem generica`() = runTest(mainDispatcher) {
        val gateway = FakeGateway(SaqzResult.Failure(PasswordResetError.RateLimited(retryAfterSeconds = 0)))
        val viewModel = ForgotPasswordViewModel(gateway)

        viewModel.onIntent(ForgotPasswordIntent.UpdateEmail("ana@exemplo.com"))
        viewModel.onIntent(ForgotPasswordIntent.Submit)
        runCurrent()

        assertEquals(UiText.Res(Res.string.auth_error_network), viewModel.state.value.error)
    }

    @Test
    fun `e-mail malformado nem chega ao gateway`() = runTest(mainDispatcher) {
        val gateway = FakeGateway(SaqzResult.Success(Unit))
        val viewModel = ForgotPasswordViewModel(gateway)
        val effects = collectEffects(viewModel)

        viewModel.onIntent(ForgotPasswordIntent.UpdateEmail("ana"))
        viewModel.onIntent(ForgotPasswordIntent.Submit)
        runCurrent()

        assertEquals(emptyList(), gateway.requested)
        assertEquals(emptyList(), effects)
        assertEquals(UiText.Res(Res.string.login_error_email_invalid), viewModel.state.value.error)
    }

    @Test
    fun `digitar limpa a recusa anterior`() = runTest(mainDispatcher) {
        val viewModel = ForgotPasswordViewModel(FakeGateway(SaqzResult.Success(Unit)))

        viewModel.onIntent(ForgotPasswordIntent.UpdateEmail("ana"))
        viewModel.onIntent(ForgotPasswordIntent.Submit)
        runCurrent()
        viewModel.onIntent(ForgotPasswordIntent.UpdateEmail("ana@"))

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `envio em andamento nao dispara um segundo pedido`() = runTest(mainDispatcher) {
        val gateway = FakeGateway(SaqzResult.Success(Unit))
        val viewModel = ForgotPasswordViewModel(gateway)

        viewModel.onIntent(ForgotPasswordIntent.UpdateEmail("ana@exemplo.com"))
        viewModel.onIntent(ForgotPasswordIntent.Submit)
        viewModel.onIntent(ForgotPasswordIntent.Submit)
        assertTrue(viewModel.state.value.isSubmitting)
        runCurrent()

        assertEquals(listOf("ana@exemplo.com"), gateway.requested)
    }

    private fun TestScope.collectEffects(viewModel: ForgotPasswordViewModel): List<ForgotPasswordEffect> {
        val received = mutableListOf<ForgotPasswordEffect>()
        (backgroundScope as CoroutineScope).launch(mainDispatcher) { viewModel.effects.toList(received) }
        runCurrent()
        return received
    }

    private class FakeGateway(
        private val response: SaqzResult<Unit, PasswordResetError>,
    ) : PasswordResetGateway by NotUsedGateway {
        val requested = mutableListOf<String>()

        override suspend fun requestCode(email: String): SaqzResult<Unit, PasswordResetError> {
            requested += email
            return response
        }
    }

    /** O pedido fica pendurado até o teste responder, que é quando a tela já mudou. */
    private class SuspendingGateway : PasswordResetGateway by NotUsedGateway {
        private val response = CompletableDeferred<SaqzResult<Unit, PasswordResetError>>()

        override suspend fun requestCode(email: String): SaqzResult<Unit, PasswordResetError> =
            response.await()

        fun complete(result: SaqzResult<Unit, PasswordResetError>) {
            response.complete(result)
        }
    }

    private object NotUsedGateway : PasswordResetGateway {
        override suspend fun requestCode(email: String): SaqzResult<Unit, PasswordResetError> =
            error("cada fake implementa o seu")

        override suspend fun verifyCode(
            email: String,
            code: String,
        ): SaqzResult<PasswordResetTicket, PasswordResetError> = error("fora do escopo da 1d")

        override suspend fun confirm(
            token: String,
            newPassword: String,
        ): SaqzResult<Unit, PasswordResetError> = error("fora do escopo da 1d")
    }
}
