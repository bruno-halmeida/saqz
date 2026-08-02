package br.com.saqz.profile.presentation.exit

import br.com.saqz.domain.DataError
import br.com.saqz.profile.domain.ProfileError
import br.com.saqz.profile.fake.FakeProfileGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileExitViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `o primeiro toque em excluir abre a confirmacao sem chamar o gateway`() = runTest(mainDispatcher) {
        val gateway = FakeProfileGateway()
        val viewModel = viewModel(gateway)

        viewModel.onIntent(ProfileExitIntent.OpenDeleteConfirmation)
        runCurrent()

        assertEquals(ProfileExitSheet.ConfirmDelete, viewModel.state.value.sheet)
        assertEquals(0, gateway.deleteSessionCalls)
    }

    @Test
    fun `e-mail diferente nao exclui a conta e mostra erro na confirmacao`() = runTest(mainDispatcher) {
        val gateway = FakeProfileGateway()
        val viewModel = viewModel(gateway)
        viewModel.onIntent(ProfileExitIntent.OpenDeleteConfirmation)
        viewModel.onIntent(ProfileExitIntent.UpdateConfirmationEmail("outra@example.com"))
        viewModel.onIntent(ProfileExitIntent.ConfirmDelete)
        runCurrent()

        assertEquals(0, gateway.deleteSessionCalls)
        assertEquals(ProfileExitSheet.ConfirmDelete, viewModel.state.value.sheet)
        assertEquals(ProfileExitError.EmailMismatch, viewModel.state.value.error)
        assertTrue(!viewModel.state.value.isDeleting)
    }

    @Test
    fun `falha ao excluir mantem confirmacao aberta e preserva a sessao`() = runTest(mainDispatcher) {
        val gateway = FakeProfileGateway().apply {
            deleteSessionError = ProfileError.DataFailure(DataError.Connectivity)
        }
        val viewModel = viewModel(gateway)
        val effects = collectEffects(viewModel)
        viewModel.onIntent(ProfileExitIntent.OpenDeleteConfirmation)
        viewModel.onIntent(ProfileExitIntent.UpdateConfirmationEmail("person@example.com"))
        viewModel.onIntent(ProfileExitIntent.ConfirmDelete)
        runCurrent()

        assertEquals(1, gateway.deleteSessionCalls)
        assertEquals(ProfileExitSheet.ConfirmDelete, viewModel.state.value.sheet)
        assertEquals(ProfileExitError.DeleteFailed, viewModel.state.value.error)
        assertTrue(!viewModel.state.value.isDeleting)
        assertTrue(!gateway.accountDeleted)
        assertTrue(effects.isEmpty())
    }

    @Test
    fun `e-mail confirmado exclui e emite a saida`() = runTest(mainDispatcher) {
        val gateway = FakeProfileGateway()
        val viewModel = viewModel(gateway)
        val effects = collectEffects(viewModel)
        viewModel.onIntent(ProfileExitIntent.OpenDeleteConfirmation)
        viewModel.onIntent(ProfileExitIntent.UpdateConfirmationEmail("PERSON@EXAMPLE.COM"))
        viewModel.onIntent(ProfileExitIntent.ConfirmDelete)
        runCurrent()

        assertEquals(1, gateway.deleteSessionCalls)
        assertTrue(gateway.accountDeleted)
        assertEquals(listOf(ProfileExitEffect.AccountDeleted), effects)
    }

    private fun TestScope.collectEffects(viewModel: ProfileExitViewModel): List<ProfileExitEffect> {
        val received = mutableListOf<ProfileExitEffect>()
        backgroundScope.launch(mainDispatcher) { viewModel.effects.toList(received) }
        return received
    }

    private fun viewModel(gateway: FakeProfileGateway) = ProfileExitViewModel(
        gateway = gateway,
        email = "person@example.com",
    )
}
