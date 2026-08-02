package br.com.saqz.profile.presentation.edit

import br.com.saqz.domain.DataError
import br.com.saqz.domain.ValidationDetails
import br.com.saqz.profile.domain.PhoneVisibility
import br.com.saqz.profile.domain.ProfileError
import br.com.saqz.profile.fake.FakeProfileGateway
import br.com.saqz.profile.domain.UpdateField
import br.com.saqz.profile.domain.UpdateSessionProfileRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class EditProfileViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `botao fica desabilitado sem alteracao`() = runTest(mainDispatcher) {
        val gateway = FakeProfileGateway()
        val viewModel = EditProfileViewModel(gateway)
        runCurrent()

        assertFalse(viewModel.state.value.hasChanges)
        assertFalse(viewModel.state.value.canSave)
    }

    @Test
    fun `celular vazio da erro de campo e nao salva nem navega`() = runTest(mainDispatcher) {
        val gateway = FakeProfileGateway()
        val viewModel = EditProfileViewModel(gateway)
        runCurrent()
        viewModel.onIntent(EditProfileIntent.UpdatePhone(""))
        viewModel.onIntent(EditProfileIntent.Submit)
        runCurrent()

        assertTrue(EditProfileFieldError.PhoneRequired in viewModel.state.value.fieldErrors)
        assertTrue(gateway.updateRequests.isEmpty())
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun `visibilidade entra no mesmo patch completo`() = runTest(mainDispatcher) {
        val gateway = FakeProfileGateway()
        val viewModel = EditProfileViewModel(gateway)
        runCurrent()
        viewModel.onIntent(EditProfileIntent.SelectPhoneVisibility(PhoneVisibility.EVERYONE))
        viewModel.onIntent(EditProfileIntent.Submit)
        runCurrent()

        assertEquals(
            UpdateSessionProfileRequest(
                displayName = UpdateField.Set("Rafael Costa"),
                nickname = UpdateField.Set("Rafa"),
                phone = UpdateField.Set("+5511988765432"),
                city = UpdateField.Set("São Paulo, SP"),
                phoneVisibility = UpdateField.Set(PhoneVisibility.EVERYONE),
            ),
            gateway.updateRequests.single(),
        )
        assertEquals(EditProfileEffect.Saved, viewModel.effects.first())
    }

    @Test
    fun `apelido e cidade vazios limpam os campos no patch`() = runTest(mainDispatcher) {
        val gateway = FakeProfileGateway()
        val viewModel = EditProfileViewModel(gateway)
        runCurrent()

        viewModel.onIntent(EditProfileIntent.UpdateNickname(""))
        viewModel.onIntent(EditProfileIntent.UpdateCity(""))
        viewModel.onIntent(EditProfileIntent.Submit)
        runCurrent()

        val request = gateway.updateRequests.single()
        assertEquals(UpdateField.Set(null), request.nickname)
        assertEquals(UpdateField.Set(null), request.city)
        assertTrue(viewModel.state.value.fieldErrors.isEmpty())
    }

    @Test
    fun `validacao do backend marca o campo recusado`() = runTest(mainDispatcher) {
        val gateway = FakeProfileGateway()
        val viewModel = EditProfileViewModel(gateway)
        runCurrent()
        gateway.profileError = ProfileError.Validation(
            ValidationDetails(emptyList(), mapOf("nickname" to listOf("invalid"))),
        )
        viewModel.onIntent(EditProfileIntent.UpdateDisplayName("Outro nome"))
        viewModel.onIntent(EditProfileIntent.Submit)
        runCurrent()

        assertTrue(EditProfileFieldError.NicknameInvalid in viewModel.state.value.fieldErrors)
        assertFalse(viewModel.state.value.saveFailed)
    }

    @Test
    fun `falha de dados fica no estado de salvamento sem expor erro de transporte`() = runTest(mainDispatcher) {
        val gateway = FakeProfileGateway()
        val viewModel = EditProfileViewModel(gateway)
        runCurrent()
        gateway.profileError = ProfileError.DataFailure(DataError.Connectivity)
        viewModel.onIntent(EditProfileIntent.UpdateDisplayName("Outro nome"))
        viewModel.onIntent(EditProfileIntent.Submit)
        runCurrent()

        assertTrue(viewModel.state.value.saveFailed)
        assertTrue(viewModel.state.value.hasChanges)
    }
}
